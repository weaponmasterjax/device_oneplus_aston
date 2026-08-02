package org.lineageos.settings.memc;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserHandle;
import androidx.preference.PreferenceManager;

import java.lang.reflect.Method;

public final class MemcUtils {

    private static final String MEMC_CONTROL = "memc_control";
    private static final String PROP_KEY = "sys.oplus.iris.cmd";

    private SharedPreferences mSharedPrefs;
    private Context mContext;
    protected static boolean isAppInList = false;
    private String mDefaultConfig = null;

    protected MemcUtils(Context context) {
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mContext = context;
        loadDefaultConfig();
    }

    public static void startService(Context context) {
        context.startServiceAsUser(new android.content.Intent(context, MemcService.class),
                UserHandle.CURRENT);
    }

    protected void writePackageConfig(String packageName, String config) {
        mSharedPrefs.edit().putString(MEMC_CONTROL + ":" + packageName, config).apply();
    }

    protected String getConfigForPackage(String packageName) {
        return mSharedPrefs.getString(MEMC_CONTROL + ":" + packageName, null);
    }

    protected boolean hasPackageConfig(String packageName) {
        return mSharedPrefs.contains(MEMC_CONTROL + ":" + packageName);
    }

    protected void executeDefaultConfig() {
        if (mDefaultConfig == null || mDefaultConfig.isEmpty()) return;
        isAppInList = false;
        String[] lines = mDefaultConfig.split("\\r?\\n");
        for (String l : lines) {
            String line = l.trim();
            if (line.isEmpty()) continue;
            setPropertyLine(line);
        }
    }

    private void setPropertyLine(String line) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method set = sp.getMethod("set", String.class, String.class);
            set.invoke(null, PROP_KEY, line);
        } catch (Exception e) {
            try {
                Runtime.getRuntime().exec(new String[]{"setprop", PROP_KEY, line});
            } catch (Exception ex) {
                // ignore
            }
        }
    }

    private void loadDefaultConfig() {
        try {
            mDefaultConfig = mContext.getString(org.lineageos.settings.R.string.memc_default_config);
            if (mDefaultConfig != null) {
                mDefaultConfig = mDefaultConfig.trim();
            }
        } catch (Exception e) {
            mDefaultConfig = null;
        }
    }

    protected void executeConfig(String packageName) {
        String cfg = getConfigForPackage(packageName);
        if (cfg == null || cfg.isEmpty()) return;
        isAppInList = true;
        String[] lines = cfg.split("\\r?\\n");
        for (String l : lines) {
            String line = l.trim();
            if (line.isEmpty()) continue;
            setPropertyLine(line);
        }
    }
}
