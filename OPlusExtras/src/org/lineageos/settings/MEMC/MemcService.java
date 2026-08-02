package org.lineageos.settings.memc;

import android.app.Service;
import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityTaskManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.view.Display;

public class MemcService extends Service {

    private static final String TAG = "MemcService";

    private String mPreviousApp = "";
    private MemcUtils mMemcUtils;
    private IActivityTaskManager mActivityTaskManager;
    private DisplayManager mDisplayManager;

    @Override
    public void onCreate() {
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

        mMemcUtils = new MemcUtils(this);
        mMemcUtils.executeDefaultConfig();
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mDisplayManager != null) {
            mDisplayManager.unregisterDisplayListener(mDisplayListener);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
                if (!foregroundApp.equals(mPreviousApp)) {
                    if (mMemcUtils.hasPackageConfig(foregroundApp)) {
                        mMemcUtils.executeConfig(foregroundApp);
                    } else {
                        mMemcUtils.executeDefaultConfig();
                    }
                    mPreviousApp = foregroundApp;
                }
            } catch (Exception e) {}
        }
    };
}
