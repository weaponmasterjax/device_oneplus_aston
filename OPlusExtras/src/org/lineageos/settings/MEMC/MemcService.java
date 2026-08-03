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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private Future<?> mCurrentConfigTask;

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mPreviousApp = "";
            checkForegroundApp();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = getSystemService(NotificationManager.class);
        createNotificationChannel();

        mMemcUtils = new MemcUtils(this);

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
        mHandler.removeCallbacks(mCheckTaskRunnable);
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
        hideNotification();
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

        if (packageName != null && mMemcUtils.hasPackageConfig(packageName)) {
            showNotification(packageName);
            mCurrentConfigTask = mExecutor.submit(() -> mMemcUtils.executeConfig(packageName));
        } else {
            hideNotification();
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

    private void showNotification(String packageName) {
        if (mNotificationManager == null) return;

        CharSequence appLabel = packageName;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            appLabel = pm.getApplicationLabel(ai);
        } catch (PackageManager.NameNotFoundException e) {
            // ignore fallback to packageName
        }

        Intent intent = new Intent(this, MemcActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.memc_notification_title))
                .setContentText(getString(R.string.memc_notification_content, appLabel))
                .setSmallIcon(R.drawable.ic_motion_settings)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();

        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void hideNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_ID);
        }
    }

    private void checkForegroundApp() {
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
            checkForegroundApp();
            mHandler.removeCallbacks(mCheckTaskRunnable);
            mHandler.postDelayed(mCheckTaskRunnable, 250);
        }
    };

    private final Runnable mCheckTaskRunnable = this::checkForegroundApp;
}
