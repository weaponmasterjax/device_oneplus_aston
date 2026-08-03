package org.lineageos.settings.memc;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
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

    private static final String KEY_PEAK_REFRESH_RATE = Settings.System.PEAK_REFRESH_RATE;
    private static final String KEY_MIN_REFRESH_RATE = Settings.System.MIN_REFRESH_RATE;
    private static final float MEMC_REFRESH_RATE = 120f;

    private static final Object sLock = new Object();
    private static float sSavedMinRefreshRate = 0f;
    private static float sSavedPeakRefreshRate = 120f;
    private static boolean sSavedRefreshRate = false;

    private final Context mContext;
    private SharedPreferences mSharedPrefs;
    protected static volatile boolean isAppInList = false;

    protected MemcUtils(Context context) {
        mContext = context;
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

    public static boolean hasPackageConfig(Context context, String packageName) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String cfg = sharedPrefs.getString(MEMC_CONTROL + ":" + packageName, null);
        return cfg != null && !cfg.trim().isEmpty();
    }

    protected void executeDefaultConfig() {
        isAppInList = false;
        restoreOriginalRefreshRate();
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
            try {
                Thread.sleep(DELAY_MS);
            } catch (InterruptedException e) {
                // Ignore interrupt during delay to ensure full sequence completion
            }
        }
    }

    private void setPropertyValue(String value) {
        try {
            SystemProperties.set(PROP_KEY, value);
        } catch (Exception e) {
            // ignore
        }
    }

    protected void executeConfig(String packageName) {
        String cfg = getConfigForPackage(packageName);
        if (cfg == null || cfg.isEmpty()) return;
        synchronized (sLock) {
            if (!sSavedRefreshRate) {
                saveOriginalRefreshRate();
            }
        }
        isAppInList = true;
        applyConfigText(cfg);
        setRefreshRateFixed();
    }

    private void saveOriginalRefreshRate() {
        try {
            float min = Settings.System.getFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, 0f);
            float peak = Settings.System.getFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, 120f);
            if (min != MEMC_REFRESH_RATE) {
                sSavedMinRefreshRate = min;
            }
            if (peak != MEMC_REFRESH_RATE) {
                sSavedPeakRefreshRate = peak;
            }
            sSavedRefreshRate = true;
        } catch (Exception e) {
            sSavedRefreshRate = false;
        }
    }

    private void restoreOriginalRefreshRate() {
        synchronized (sLock) {
            float curMin = Settings.System.getFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, 0f);
            if (!sSavedRefreshRate && curMin != MEMC_REFRESH_RATE) {
                return;
            }
            try {
                Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, sSavedMinRefreshRate);
                Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, sSavedPeakRefreshRate);
            } catch (Exception e) {
                // ignore
            } finally {
                sSavedRefreshRate = false;
            }
        }
    }

    private void setRefreshRateFixed() {
        try {
            Settings.System.putFloat(mContext.getContentResolver(), KEY_MIN_REFRESH_RATE, MEMC_REFRESH_RATE);
            Settings.System.putFloat(mContext.getContentResolver(), KEY_PEAK_REFRESH_RATE, MEMC_REFRESH_RATE);
        } catch (Exception e) {
            // ignore
        }
    }
}
