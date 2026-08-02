package org.lineageos.settings.memc;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserHandle;
import androidx.preference.PreferenceManager;

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
        String cfg = mSharedPrefs.getString(MEMC_CONTROL + ":" + packageName, null);
        return cfg != null && !cfg.trim().isEmpty();
    }

    protected void executeDefaultConfig() {
        if (mDefaultConfig == null || mDefaultConfig.isEmpty()) return;
        isAppInList = false;
        applyConfigText(mDefaultConfig);
    }

    private void applyConfigText(String cfg) {
        if (cfg == null || cfg.isEmpty()) return;
        String[] lines = cfg.split("\\r?\\n");
        for (String l : lines) {
            String line = l.trim();
            if (line.isEmpty()) continue;
            setPropertyValue(line);
        }
    }

    private void setPropertyValue(String value) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method set = sp.getMethod("set", String.class, String.class);
            set.invoke(null, PROP_KEY, value);
        } catch (Exception e) {
            try {
                Runtime.getRuntime().exec(new String[]{"setprop", PROP_KEY, value});
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
        applyConfigText(cfg);
    }
}
