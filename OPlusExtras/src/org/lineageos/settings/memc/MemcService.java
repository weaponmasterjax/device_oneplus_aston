package org.lineageos.settings.memc;

import android.app.Service;
import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityTaskManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.content.Intent;

public class MemcService extends Service {

    private static final String TAG = "MemcService";

    private String mPreviousApp;
    private MemcUtils mMemcUtils;
    private IActivityTaskManager mActivityTaskManager;

    @Override
    public void onCreate() {
        try {
            mActivityTaskManager = ActivityTaskManager.getService();
            mActivityTaskManager.registerTaskStackListener(mTaskListener);
        } catch (RemoteException e) {
            // ignore
        }
        mMemcUtils = new MemcUtils(this);
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

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
