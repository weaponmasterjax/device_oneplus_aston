package org.lineageos.settings.memc;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.os.UserHandle;
import androidx.preference.PreferenceManager;

public final class MemcUtils {

    private static final String MEMC_CONTROL = "memc_control";
    private static final String PROP_KEY = "sys.oplus.iris.cmd";
    private static final long DELAY_MS = 80L;
    private static final String[] DEFAULT_CONFIG_LINES = {
            "258 1 0",
            "273 1 0",
            "267 2 3 0",
            "56 1 1"
    };

    private SharedPreferences mSharedPrefs;
    protected static boolean isAppInList = false;

    protected MemcUtils(Context context) {
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
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
        isAppInList = false;
        applyConfigLines(DEFAULT_CONFIG_LINES);
    }

    private void applyConfigText(String cfg) {
        if (cfg == null || cfg.isEmpty()) return;
        String[] lines = cfg.split("\\r?\\n");
        applyConfigLines(lines);
    }

    private void applyConfigLines(String[] lines) {
        if (lines == null) return;
        for (String line : lines) {
            String value = line == null ? "" : line.trim();
            if (value.isEmpty()) continue;
            setPropertyValue(value);
            SystemClock.sleep(DELAY_MS);
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

    protected void executeConfig(String packageName) {
        String cfg = getConfigForPackage(packageName);
        if (cfg == null || cfg.isEmpty()) return;
        isAppInList = true;
        applyConfigText(cfg);
    }
}
