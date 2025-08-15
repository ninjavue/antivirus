package uz.csec.antivirus;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import android.util.Log;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.os.PowerManager;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import org.json.JSONObject;
import android.app.AlertDialog;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.List;
import android.app.Activity;
import android.provider.MediaStore;
import android.content.ContentResolver;

public class FileScanHelper {
	// Notification deduplication: avoid spamming same message repeatedly
	private static final long NOTIFICATION_DEDUP_WINDOW_MS = 60_000L; // 1 minute
	private static final Map<String, Long> LAST_NOTIFICATION_TIME_BY_KEY = new ConcurrentHashMap<>();
    public static void handleNewFile(Context context, String filePath) {
        File file = new File(filePath);
        String fileName = file.getName().toLowerCase();
        
        if (filePath.contains("/Android/data/org.telegram.messenger/files/Telegram/Telegram Files/")) {
            sendNotification(context, "Telegramdan fayl yuklanmoqda", "Telegram: " + fileName);
        } else {
            sendNotification(context, "Yangi fayl", "Yangi yuklangan fayl: " + fileName);
        }
        
        boolean isApk = fileName.endsWith(".apk");
        if (isApk) {
            Log.d("FileScanHelper", "APK fayl aniqlanmoqda: " + fileName);
            boolean hasSuspiciousPermissions = analyzeApkManifest(filePath);
            if (hasSuspiciousPermissions) {
                sendNotification(context, "Shubhali APK", "APK faylida xavfli ruxsatlar topildi! " + fileName);
            }
        }
        
        // Fayl to'liq yozib bo'linganini kutish uchun kichik prob tekshiruv (hash hisoblashda EOF xatolarini kamaytirish)
        long lastLen = -1L;
        for (int i = 0; i < 3; i++) {
            long cur = file.length();
            if (cur > 0 && cur == lastLen) break;
            lastLen = cur;
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }

        String md5 = getMD5(file);
        if (md5 == null) {
            return;
        }
        
        boolean isVirus = checkVirusTotal(md5);
        if (!isVirus) {
            isVirus = checkLocalVirus(context, md5);
        }
        
        if (isVirus) {
            sendNotification(context, "Xavfli fayl", "Fayl zararli! O'chirilmoqda: " + fileName);
            if (deleteFile(context, file)) {
                Log.d("FileScanHelper", "Xavfli fayl o'chirildi: " + filePath);
                if (!file.exists()) {
                    Log.d("FileScanHelper", "Fayl haqiqatan ham o‘chirildi");
                } else {
                    Log.e("FileScanHelper", "Fayl hali ham mavjud: " + file.getAbsolutePath());
                }
                return;
            } else {
                Log.w("FileScanHelper", "Xavfli faylni o'chirish muvaffaqiyatsiz: " + filePath);
            }
        }

        // Download papkasida APK va boshqa fayllarni shubhali bo'lsa darhol o'chirish
        try {
            String downloadsDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download";
            if (file.getAbsolutePath().startsWith(downloadsDir)) {
                boolean shouldDelete = false;
                if (isApk) {
                    // APK uchun: manifestdagi xavfli ruxsatlar yoki virus bazasida bo'lsa
                    boolean hasSuspiciousPermissions = analyzeApkManifest(filePath);
                    if (hasSuspiciousPermissions) shouldDelete = true;
                    if (md5 != null && (checkVirusTotal(md5) || checkLocalVirus(context, md5))) {
                        shouldDelete = true;
                    }
                } else {
                    // APK bo'lmagan fayllar: faqat hash bo'yicha tekshiruvga asoslanib o'chirish
                    String md5Other = getMD5(file);
                    if (md5Other != null && (checkVirusTotal(md5Other) || checkLocalVirus(context, md5Other))) {
                        shouldDelete = true;
                    }
                }

                if (shouldDelete) {
                    sendNotification(context, "Xavfli fayl", "Download papkasida zararli fayl topildi – o'chirilmoqda: " + fileName);
                    if (deleteFile(context, file)) {
                        Log.d("FileScanHelper", "Download'dan xavfli fayl o'chirildi: " + filePath);
                    } else {
                        Log.w("FileScanHelper", "Faylni o'chirish muvaffaqiyatsiz: " + filePath);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("FileScanHelper", "Download papkasini tekshirishda xatolik", e);
        }
    }

    // Universal file delete for all Android versions
    public static boolean deleteFile(Context context, File file) {
        return FileDeletionHelper.deleteFileSimple(context, file.getAbsolutePath());
    }

    public static boolean isVirus(Context context, String filePath) {
        String md5 = getMD5(new File(filePath));
        if (md5 == null) {
            return false;
        }
        boolean isVirus = checkVirusTotal(md5);
        if (!isVirus) {
            isVirus = checkLocalVirus(context, md5);
        }
        return isVirus;
    }

    public static void sendNotification(Context context, String title, String text) {
		// Create a stable key per message
		String key = title + "|" + text;
		long now = System.currentTimeMillis();
		Long lastTs = LAST_NOTIFICATION_TIME_BY_KEY.get(key);
		if (lastTs != null && (now - lastTs) < NOTIFICATION_DEDUP_WINDOW_MS) {
			Log.d("FileScanHelper", "Skipping duplicate notification within window: " + key);
			return;
		}
		LAST_NOTIFICATION_TIME_BY_KEY.put(key, now);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "virus_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Virus Alerts", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_antivirus)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOnlyAlertOnce(true);

		// Use stable ID so same message updates instead of stacking
		int notificationId = key.hashCode();
        manager.notify(notificationId, builder.build());
    }

    public static String getMD5(File file) {
        Log.d("FileScanHelper", "getMD5: file=" + file.getAbsolutePath() + ", exists=" + file.exists() + ", length=" + file.length());
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1024];
            int n;
            while ((n = fis.read(buffer)) > 0) {
                md.update(buffer, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            String hash = sb.toString();
            return hash;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean checkVirusTotal(String md5) {
        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                .url("http://172.174.245.45:4000/api/scan/file/" + md5)
                .build();
            Response response = client.newCall(request).execute();
            String body = response.body().string();
            JSONObject json = new JSONObject(body);
            boolean success = json.optBoolean("success", false);
            if (success) {
                String status = json.optString("status", "");
                return "virus".equals(status);
            }
            return false;
        } catch (Exception e) {
            Log.e("FileScanHelper", "API xatoligi", e);
            return false;
        }
    }

    public static boolean checkLocalVirus(Context context, String md5) {
        try (Scanner scanner = new Scanner(context.getAssets().open("virus.txt"))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.equalsIgnoreCase(md5)) return true;
            }
        } catch (Exception e) {
            Log.e("FileScanHelper", "Xatolik yuz berdi", e);
        }
        return false;
    }
    
    private static boolean analyzeApkManifest(String apkPath) {
        Set<String> suspiciousPermissions = new HashSet<>();
        suspiciousPermissions.add("android.permission.SYSTEM_ALERT_WINDOW");
        suspiciousPermissions.add("android.permission.ACCESSIBILITY_SERVICE");
        suspiciousPermissions.add("android.permission.BIND_ACCESSIBILITY_SERVICE");
        suspiciousPermissions.add("android.permission.WRITE_SECURE_SETTINGS");
        suspiciousPermissions.add("android.permission.WRITE_SETTINGS");
        suspiciousPermissions.add("android.permission.READ_PHONE_STATE");
        suspiciousPermissions.add("android.permission.READ_SMS");
        suspiciousPermissions.add("android.permission.SEND_SMS");
        suspiciousPermissions.add("android.permission.RECEIVE_SMS");
        suspiciousPermissions.add("android.permission.READ_CONTACTS");
        suspiciousPermissions.add("android.permission.WRITE_CONTACTS");
        suspiciousPermissions.add("android.permission.READ_CALL_LOG");
        suspiciousPermissions.add("android.permission.WRITE_CALL_LOG");
        suspiciousPermissions.add("android.permission.CAMERA");
        suspiciousPermissions.add("android.permission.RECORD_AUDIO");
        suspiciousPermissions.add("android.permission.ACCESS_FINE_LOCATION");
        suspiciousPermissions.add("android.permission.ACCESS_COARSE_LOCATION");
        suspiciousPermissions.add("android.permission.READ_EXTERNAL_STORAGE");
        suspiciousPermissions.add("android.permission.WRITE_EXTERNAL_STORAGE");
        suspiciousPermissions.add("android.permission.MANAGE_EXTERNAL_STORAGE");
        suspiciousPermissions.add("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
        suspiciousPermissions.add("android.permission.PACKAGE_USAGE_STATS");
        suspiciousPermissions.add("android.permission.QUERY_ALL_PACKAGES");
        suspiciousPermissions.add("android.permission.INSTALL_PACKAGES");
        suspiciousPermissions.add("android.permission.DELETE_PACKAGES");
        suspiciousPermissions.add("android.permission.MODIFY_PHONE_STATE");
        suspiciousPermissions.add("android.permission.INTERNET");
        suspiciousPermissions.add("android.permission.ACCESS_NETWORK_STATE");
        suspiciousPermissions.add("android.permission.ACCESS_WIFI_STATE");
        suspiciousPermissions.add("android.permission.CHANGE_WIFI_STATE");
        suspiciousPermissions.add("android.permission.WAKE_LOCK");
        suspiciousPermissions.add("android.permission.DISABLE_KEYGUARD");
        suspiciousPermissions.add("android.permission.SYSTEM_OVERLAY_WINDOW");
        suspiciousPermissions.add("android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND");
        suspiciousPermissions.add("android.permission.REQUEST_DELETE_PACKAGES");
        suspiciousPermissions.add("android.permission.REQUEST_INSTALL_PACKAGES");
        
        try {
            Set<String> foundPermissions = extractPermissionsFromApk(apkPath);
            int suspiciousCount = 0;
            
            for (String permission : foundPermissions) {
                if (suspiciousPermissions.contains(permission)) {
                    suspiciousCount++;
                    Log.d("FileScanHelper", "Shubhali ruxsat topildi: " + permission);
                }
            }
            
            Log.d("FileScanHelper", "APK tahlili: " + suspiciousCount + " ta shubhali ruxsat topildi");
            return suspiciousCount >= 3;
            
        } catch (Exception e) {
            Log.e("FileScanHelper", "APK manifestini tahlil qilishda xatolik", e);
            return false;
        }
    }
    
    private static Set<String> extractPermissionsFromApk(String apkPath) {
        Set<String> permissions = new HashSet<>();
        
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(apkPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("AndroidManifest.xml")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    
                    String manifestContent = baos.toString("UTF-8");
                    extractPermissionsFromManifest(manifestContent, permissions);
                    break;
                }
            }
        } catch (IOException e) {
            Log.e("FileScanHelper", "APK faylini o'qishda xatolik", e);
        }
        
        return permissions;
    }
    
    private static void extractPermissionsFromManifest(String manifestContent, Set<String> permissions) {
        String[] lines = manifestContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.contains("android:name=") && line.contains("permission")) {
                int startIndex = line.indexOf("android:name=\"");
                if (startIndex != -1) {
                    startIndex += "android:name=\"".length();
                    int endIndex = line.indexOf("\"", startIndex);
                    if (endIndex != -1) {
                        String permission = line.substring(startIndex, endIndex);
                        permissions.add(permission);
                        Log.d("FileScanHelper", "Ruxsat topildi: " + permission);
                    }
                }
            }
        }
    }
    
    public static boolean isNonPlayStoreApp(Context context, String packageName) {
        try {
            // Never flag our own app
            if (packageName == null) return false;
            if (context != null && packageName.equals(context.getPackageName())) {
                return false;
            }
            PackageManager pm = context.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            
            String installerPackageName = pm.getInstallerPackageName(packageName);

            // Play Store package names (different devices may use different ones)
            String[] playStorePackages = {
                "com.android.vending",  // Standard Google Play Store
                "com.google.android.packageinstaller", // Google Package Installer
                "com.android.packageinstaller", // Android Package Installer
                "com.google.android.apps.nbu.files", // Google Files (some devices)
                "com.miui.packageinstaller", // Xiaomi Package Installer
                "com.samsung.android.packageinstaller", // Samsung Package Installer
                "com.oneplus.packageinstaller", // OnePlus Package Installer
                "com.huawei.packageinstaller", // Huawei Package Installer
                "com.oppo.packageinstaller", // OPPO Package Installer
                "com.vivo.packageinstaller" // VIVO Package Installer
            };

            // If no installer package name, it's likely not from Play Store
            if (installerPackageName == null) {
                Log.d("FileScanHelper", "Play Store dan o'rnatilmagan ilova (no installer): " + packageName);
                return true;
            }

            // Check if installer is from Play Store
            boolean isFromPlayStore = false;
            for (String playStorePackage : playStorePackages) {
                if (installerPackageName.equals(playStorePackage)) {
                    isFromPlayStore = true;
                    break;
                }
            }

            if (!isFromPlayStore) {
                Log.d("FileScanHelper", "Play Store dan o'rnatilmagan ilova: " + packageName + " (installer: " + installerPackageName + ")");
                return true;
            }
            
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("FileScanHelper", "Ilova topilmadi: " + packageName, e);
            return true;
        } catch (Exception e) {
            Log.e("FileScanHelper", "Ilova tekshirishda xatolik: " + packageName, e);
            return true;
        }
    }
    
    public static void checkAllInstalledApps(Context context) {
        try {
            // Rate-limit full scan: only once per 6 hours
            String key = "last_full_app_scan_ts";
            android.content.SharedPreferences prefs = context.getSharedPreferences("antivirus_prefs", Context.MODE_PRIVATE);
            long last = prefs.getLong(key, 0L);
            long now = System.currentTimeMillis();
            if (now - last < 6 * 60 * 60 * 1000L) {
                Log.d("FileScanHelper", "Skipping full installed-apps scan (recently scanned)");
                return;
            }

            PackageManager pm = context.getPackageManager();
            java.util.List<PackageInfo> packages = null;
            try {
                packages = pm.getInstalledPackages(0);
            } catch (Throwable t) {
                Log.e("FileScanHelper", "getInstalledPackages failed, retrying once", t);
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                try {
                    packages = pm.getInstalledPackages(0);
                } catch (Throwable t2) {
                    Log.e("FileScanHelper", "getInstalledPackages failed again, aborting", t2);
                    return;
                }
            }
            
            for (PackageInfo packageInfo : packages) {
                String packageName = packageInfo.packageName;
                
                if (isSystemApp(packageInfo)) {
                    continue;
                }
                // Skip our own package
                if (packageName != null && packageName.equals(context.getPackageName())) {
                    continue;
                }
                
                if (isNonPlayStoreApp(context, packageName)) {
                    Log.d("FileScanHelper", "Xavfli ilova topildi: " + packageName);
                    sendNotification(context, "Xavfli ilova", "Play Store dan o'rnatilmagan ilova: " + packageName);
                }
            }

            prefs.edit().putLong(key, now).apply();
        } catch (Exception e) {
            Log.e("FileScanHelper", "Ilovalarni tekshirishda xatolik", e);
        }
    }
    
    private static boolean isSystemApp(PackageInfo packageInfo) {
        return (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }
    
    private static String getPackageNameFromApk(String apkPath) {
        try {
            Set<String> permissions = extractPermissionsFromApk(apkPath);
            String fileName = new File(apkPath).getName();
            if (fileName.endsWith(".apk")) {
                return fileName.substring(0, fileName.length() - 4);
            }
        } catch (Exception e) {
            Log.e("FileScanHelper", "APK dan package name olishda xatolik", e);
        }
        return null;
    }

    public static class DangerInfo {
        public String appName;
        public List<String> reasons = new ArrayList<>();
        public boolean isDangerous() { return !reasons.isEmpty(); }
    }

    public static DangerInfo analyzeAppDanger(Context context, String packageName, String apkPath) {
        DangerInfo info = new DangerInfo();
        PackageManager pm = context.getPackageManager();
        String installer = null;
        try { installer = pm.getInstallerPackageName(packageName); } catch (Exception ignored) {}
        if (installer == null || !installer.equals("com.android.vending")) {
            info.reasons.add("Ilova Play Marketdan o'rnatilmagan (no Play Store)");
        }
        Set<String> dangerous = getDangerousPermissionsFromApk(apkPath);
        if (!dangerous.isEmpty()) {
            info.reasons.add("Xavfli ruxsatlar so'ralgan: " + TextUtils.join(", ", dangerous));
        }
        if (dangerous.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")) {
            info.reasons.add("Accessibility servisi ruxsati so'ralgan (ekranni boshqarish va o'qish)");
        }
        if (dangerous.contains("android.permission.BIND_DEVICE_ADMIN")) {
            info.reasons.add("Device Admin ruxsati so'ralgan (tizim boshqaruvi)");
        }
        if (dangerous.contains("android.permission.REQUEST_INSTALL_PACKAGES")) {
            info.reasons.add("Boshqa ilovalarni o'rnatish ruxsati so'ralgan");
        }
        if (dangerous.contains("android.permission.WAKE_LOCK") || dangerous.contains("android.permission.RECEIVE_BOOT_COMPLETED")) {
            info.reasons.add("Ilova doimiy ishlashga yoki avtomatik ishga tushishga ruxsat so'ramoqda (background)");
        }
        if (dangerous.contains("android.permission.MASTER_CLEAR")) {
            info.reasons.add("Qurilmani zavod sozlamalariga qaytarish (factory reset) ruxsati so'ralgan");
        }
        if (isVirus(context, apkPath)) {
            info.reasons.add("Virus bazasidan topildi");
        }
        // App name
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            info.appName = pm.getApplicationLabel(appInfo).toString();
        } catch (Exception e) {
            info.appName = packageName;
        }
        return info;
    }

    public static Set<String> getDangerousPermissionsFromApk(String apkPath) {
        Set<String> all = extractPermissionsFromApk(apkPath);
        Set<String> dangerous = new HashSet<>();
        String[] dangerList = {
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.permission.BIND_DEVICE_ADMIN",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.WRITE_SETTINGS",
            "android.permission.WRITE_SECURE_SETTINGS",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.INSTALL_PACKAGES",
            "android.permission.DELETE_PACKAGES",
            "android.permission.MODIFY_PHONE_STATE",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.WAKE_LOCK",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.MASTER_CLEAR"
        };
        for (String perm : dangerList) {
            if (all.contains(perm)) dangerous.add(perm);
        }
        return dangerous;
    }

    public static void showDangerDialog(Activity activity, DangerInfo info, String packageName) {
        StringBuilder message = new StringBuilder();
        for (String reason : info.reasons) {
            message.append("• ").append(reason).append("\n");
        }
        new AlertDialog.Builder(activity)
            .setTitle(info.appName + " xavfli ilova!")
            .setMessage(message.toString())
            .setCancelable(false)
            .setPositiveButton("O'chirish", (dialog, which) -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_DELETE);
                    intent.setData(Uri.parse("package:" + packageName));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + packageName));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                }
            })
            .setNegativeButton("Yopish", (dialog, which) -> dialog.dismiss())
            .show();
    }

    // Show dialog via Activity from any context
    public static void showDangerDialogViaActivity(Context context, DangerInfo info, String packageName) {
        Intent intent = new Intent(context, DangerDialogActivity.class);
        intent.putExtra("appName", info.appName);
        intent.putExtra("reasons", TextUtils.join("\n", info.reasons));
        intent.putExtra("packageName", packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void scanAndHandleApp(Context context, String packageName, String apkPath) {
        DangerInfo info = analyzeAppDanger(context, packageName, apkPath);
        if (info.isDangerous()) {
            Intent intent = new Intent(context, DangerDialogActivity.class);
            intent.putExtra("appName", info.appName);
            intent.putExtra("reasons", TextUtils.join("\n", info.reasons));
            intent.putExtra("packageName", packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            String md5 = getMD5(new File(apkPath));
            if (md5 != null) {
                boolean isVirus = checkVirusTotal(md5);
                if (isVirus) {
                    sendNotification(context, "Virus aniqlandi", info.appName + " zararli fayl! O'chirilmoqda.");
                    Intent uninstallIntent = new Intent(Intent.ACTION_DELETE);
                    uninstallIntent.setData(Uri.parse("package:" + packageName));
                    uninstallIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(uninstallIntent);
                }
            }
        }
    }
} 