package uz.csec.antivirus;

import android.content.Context;
import android.text.TextUtils;
import android.os.BatteryManager;
import android.content.Intent;
import android.content.IntentFilter;

public class NativeLib {
    static {
        System.loadLibrary("antivirus");
    }
    public native String getRunningApps(Context context);
    public native String getUnusedApps(Context context);
    public native String getAppCpuUsage();
    public native String getDeviceUptime(Context context);
    public native String getAppBatteryUsage(Context context);
    
    public native String getLargeFiles(String rootPath, long minSize);
    public native String getSuspiciousFiles(String rootPath);
    public native String getHiddenFiles(String rootPath);
    public native String scanFileWithAntivirus(String filePath);
    public native String getFileStatistics(String rootPath);
    public native String quickScan(String rootPath, android.content.res.AssetManager assetManager);
    public native String quickScanFiles(String[] filePaths, android.content.res.AssetManager assetManager);

    public static String getRunningAppsJava(Context context) {
        android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        android.content.pm.PackageManager pm = context.getPackageManager();
        java.util.Set<String> uniqueLabels = new java.util.LinkedHashSet<>();
        if (am != null) {
            java.util.List<android.app.ActivityManager.RunningAppProcessInfo> runningApps = am.getRunningAppProcesses();
            if (runningApps != null) {
                for (android.app.ActivityManager.RunningAppProcessInfo proc : runningApps) {
                    boolean addedAnyForProc = false;
                    if (proc.pkgList != null) {
                        for (String pkg : proc.pkgList) {
                            try {
                                android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                                // Skip system and updated system apps
                                if (isSystemOrUpdatedSystemApp(appInfo)) {
                                    continue;
                                }
                                String label = pm.getApplicationLabel(appInfo).toString();
                                if (shouldExcludeLabel(label)) {
                                    continue;
                                }
                                uniqueLabels.add(label);
                                addedAnyForProc = true;
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    if (!addedAnyForProc) {
                        // Fallback: try by process name and still filter system if possible
                        try {
                            android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(proc.processName, 0);
                            if (!isSystemOrUpdatedSystemApp(appInfo)) {
                                String label = pm.getApplicationLabel(appInfo).toString();
                                if (!shouldExcludeLabel(label)) {
                                    uniqueLabels.add(label);
                                }
                            }
                        } catch (Exception ignored) {
                            // If we cannot resolve to a package, skip to avoid showing system/internal processes
                        }
                    }
                }
            }
        }
        return uniqueLabels.isEmpty() ? "Faol ilovalar topilmadi" : android.text.TextUtils.join("\n", uniqueLabels);
    }

    public static String getUnusedAppsJava(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
            android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            android.content.pm.PackageManager pm = context.getPackageManager();
            long now = System.currentTimeMillis();
            java.util.Set<String> unused = new java.util.LinkedHashSet<>();
            java.util.List<android.content.pm.ApplicationInfo> allApps = pm.getInstalledApplications(0);
            java.util.Map<String, Long> lastUsedMap = new java.util.HashMap<>();
            java.util.List<android.app.usage.UsageStats> stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 1000L * 60 * 60 * 24 * 30, now);
            if (stats != null) {
                for (android.app.usage.UsageStats stat : stats) {
                    lastUsedMap.put(stat.getPackageName(), stat.getLastTimeUsed());
                }
            }
            for (android.content.pm.ApplicationInfo appInfo : allApps) {
                // Faqat user ilovalarini ko'rsatamiz
                if (isSystemOrUpdatedSystemApp(appInfo)) continue;
                long lastUsed = lastUsedMap.containsKey(appInfo.packageName) ? lastUsedMap.get(appInfo.packageName) : 0L;
                if (lastUsed < now - 1000L * 60 * 60 * 24 * 15) {
                    try {
                        String label = pm.getApplicationLabel(appInfo).toString();
                        if (!shouldExcludeLabel(label)) {
                            unused.add(label);
                        }
                    } catch (Exception e) {
                        unused.add(appInfo.packageName);
                    }
                }
            }
            return unused.isEmpty() ? "Bunday ilovalar mavjud emas" : android.text.TextUtils.join("\n", unused);
        }
        return "API past";
    }

    private static boolean isSystemOrUpdatedSystemApp(android.content.pm.ApplicationInfo appInfo) {
        int flags = appInfo.flags;
        return (flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                || (flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    private static boolean shouldExcludeLabel(String label) {
        if (label == null) return false;
        String lower = label.toLowerCase(java.util.Locale.ROOT);
        // Exclude common system-like labels reported by users
        if (lower.contains("android system")) return true;
        if (lower.contains("key verifier")) return true;
        if (lower.contains("safetycore") || lower.contains("safety core")) return true;
        return false;
    }

    public static String getAppBatteryUsageJava(Context context) {
        // Faqat umumiy batareya foizi uchun
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        int level = 0, scale = 100;
        if (batteryStatus != null) {
            level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
        }
        return "Qurulma quvvati " + (int)(level * 100f / scale) + "%";
    }

    public static String getDeviceUptimeJava(Context context) {
        long uptimeMillis = android.os.SystemClock.elapsedRealtime();
        long bootTime = System.currentTimeMillis() - uptimeMillis;
        java.util.Date bootDate = new java.util.Date(bootTime);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
        return sdf.format(bootDate);
    }
}
