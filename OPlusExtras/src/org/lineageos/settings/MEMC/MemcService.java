package org.lineageos.settings.memc;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityTaskManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.view.Display;

import org.lineageos.settings.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MemcService extends Service {

    private static final String TAG = "MemcService";
    private static final String CHANNEL_ID = "memc_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    private String mPreviousApp = "";
    private MemcUtils mMemcUtils;
    private IActivityTaskManager mActivityTaskManager;
    private DisplayManager mDisplayManager;
    private NotificationManager mNotificationManager;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private Future<?> mCurrentConfigTask;

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mPreviousApp = "";
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel();

        mMemcUtils = new MemcUtils(this);
        startForeground(NOTIFICATION_ID, createServiceNotification(""));

        try {
            mActivityTaskManager = ActivityTaskManager.getService();
            mActivityTaskManager.registerTaskStackListener(mTaskListener);
        } catch (RemoteException e) {
            // ignore
        }

        mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (mDisplayManager != null) {
            mDisplayManager.registerDisplayListener(mDisplayListener, null);
        }

        registerReceiver();
        applyConfig("");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(mIntentReceiver);
        } catch (Exception e) {
            // ignore
        }
        if (mDisplayManager != null) {
            mDisplayManager.unregisterDisplayListener(mDisplayListener);
        }
        if (mCurrentConfigTask != null) {
            mCurrentConfigTask.cancel(false);
        }
        mExecutor.shutdownNow();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mIntentReceiver, filter);
    }

    private synchronized void applyConfig(final String packageName) {
        if (mCurrentConfigTask != null && !mCurrentConfigTask.isDone()) {
            mCurrentConfigTask.cancel(false);
        }

        updateNotification(packageName);

        if (mMemcUtils.hasPackageConfig(packageName)) {
            mCurrentConfigTask = mExecutor.submit(() -> mMemcUtils.executeConfig(packageName));
        } else {
            mCurrentConfigTask = mExecutor.submit(() -> mMemcUtils.executeDefaultConfig());
        }
    }

    private void createNotificationChannel() {
        if (mNotificationManager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.memc_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.memc_notification_channel_desc));
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createServiceNotification(String packageName) {
        CharSequence appLabel = packageName;
        if (packageName != null && !packageName.isEmpty()) {
            try {
                PackageManager pm = getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                appLabel = pm.getApplicationLabel(ai);
            } catch (PackageManager.NameNotFoundException e) {
                // ignore fallback to packageName
            }
        }

        Intent intent = new Intent(this, MemcActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_oplus_extras)
                .setOngoing(true)
                .setContentIntent(pendingIntent);

        if (packageName != null && !packageName.isEmpty() && mMemcUtils.hasPackageConfig(packageName)) {
            builder.setContentTitle(getString(R.string.memc_notification_title))
                    .setContentText(getString(R.string.memc_notification_content, appLabel));
        } else {
            builder.setContentTitle(getString(R.string.memc_notification_channel_name))
                    .setContentText(getString(R.string.memc_summary));
        }

        return builder.build();
    }

    private void updateNotification(String packageName) {
        if (mNotificationManager == null) return;
        mNotificationManager.notify(NOTIFICATION_ID, createServiceNotification(packageName));
    }

    private final DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
        }

        @Override
        public void onDisplayRemoved(int displayId) {
        }

        @Override
        public void onDisplayChanged(int displayId) {
            Display display = mDisplayManager != null ? mDisplayManager.getDisplay(displayId) : null;
            if (display == null || displayId != Display.DEFAULT_DISPLAY) {
                return;
            }

            int state = display.getState();
            if (state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND) {
                SystemProperties.set("sys.oplus.iris.cmd", "56 1 1");
            }
        }
    };

    private final android.app.TaskStackListener mTaskListener = new android.app.TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            try {
                final RootTaskInfo info = mActivityTaskManager.getFocusedRootTaskInfo();
                if (info == null || info.topActivity == null) {
                    return;
                }
                String foregroundApp = info.topActivity.getPackageName();
                if (foregroundApp != null && !foregroundApp.equals(mPreviousApp)) {
                    applyConfig(foregroundApp);
                    mPreviousApp = foregroundApp;
                }
            } catch (Exception e) {}
        }
    };
}
