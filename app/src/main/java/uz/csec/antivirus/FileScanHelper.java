package uz.csec.antivirus;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
import android.content.pm.ApplicationInfo;
import org.json.JSONObject;
import android.app.AlertDialog;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.List;
import android.app.Activity;
import android.provider.MediaStore;
import android.content.ContentResolver;
import android.database.Cursor;
import java.io.StringWriter;
import java.io.PrintWriter;
import android.content.res.AXMLResource; // Import the AXMLPrinter library
import java.util.HashMap;

public class FileScanHelper {
    private static final long NOTIFICATION_DEDUP_WINDOW_MS = 60_000L; // 1 minute
    private static final Map<String, Long> LAST_NOTIFICATION_TIME_BY_KEY = new ConcurrentHashMap<>();

    // Xavfli permissionlar va ularning o'zbekcha tavsiflari
    private static final HashMap<String, String> PERMISSION_DESCRIPTIONS = new HashMap<>();
    static {
        PERMISSION_DESCRIPTIONS.put("android.permission.SYSTEM_ALERT_WINDOW", "Tizim ustida oynalar ochishga ruxsati bor (ekran ustida ko'rsatish)");
        PERMISSION_DESCRIPTIONS.put("android.permission.ACCESSIBILITY_SERVICE", "Qulaylik xizmatlariga ruxsati bor (ekran o'qish va boshqarish)");
        PERMISSION_DESCRIPTIONS.put("android.permission.BIND_ACCESSIBILITY_SERVICE", "Qulaylik xizmatiga ulanishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.WRITE_SECURE_SETTINGS", "Xavfsiz sozlamalarni o'zgartirishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.WRITE_SETTINGS", "Tizim sozlamalarini o'zgartirishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.READ_PHONE_STATE", "Telefon holatini o'qishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.READ_SMS", "SMS xabarlarni o'qishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.SEND_SMS", "SMS xabarlarni yuborishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.RECEIVE_SMS", "SMS xabarlarni qabul qilishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.READ_CONTACTS", "Kontaktlarni o'qishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.WRITE_CONTACTS", "Kontaktlarni o'zgartirishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.READ_CALL_LOG", "Qo'ng'iroq jurnalini o'qishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.WRITE_CALL_LOG", "Qo'ng'iroq jurnalini o'zgartirishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.CAMERA", "Kameraga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.RECORD_AUDIO", "Ovozni yozib olishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.ACCESS_FINE_LOCATION", "Aniq joylashuvni olishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.ACCESS_COARSE_LOCATION", "Taxminiy joylashuvni olishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.READ_EXTERNAL_STORAGE", "Tashqi xotirani o'qishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.WRITE_EXTERNAL_STORAGE", "Tashqi xotiraga yozishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.MANAGE_EXTERNAL_STORAGE", "Tashqi xotirani boshqarishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "Batareya optimizatsiyasini o'tkazib yuborishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.PACKAGE_USAGE_STATS", "Ilovalar foydalanish statistikasini o'qishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.QUERY_ALL_PACKAGES", "Barcha paketlarni so'rashga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.INSTALL_PACKAGES", "Paketlarni o'rnatishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.DELETE_PACKAGES", "Paketlarni o'chirishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.MODIFY_PHONE_STATE", "Telefon holatini o'zgartirishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.INTERNET", "Internetga ulanishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.ACCESS_NETWORK_STATE", "Tarmoq holatini o'qishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.ACCESS_WIFI_STATE", "Wi-Fi holatini o'qishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.CHANGE_WIFI_STATE", "Wi-Fi holatini o'zgartirishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.WAKE_LOCK", "Qurilmani uyg'oq holda saqlashga ruxsati bor (fon rejimida ishlash)");
        PERMISSION_DESCRIPTIONS.put("android.permission.DISABLE_KEYGUARD", "Klavyatura qulfini ochishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.SYSTEM_OVERLAY_WINDOW", "Tizim ustida oynalar ko'rsatishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND", "Fonda ishlashga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.REQUEST_DELETE_PACKAGES", "Paketlarni o'chirishni so'rashga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.REQUEST_INSTALL_PACKAGES", "Paketlarni o'rnatishni so'rashga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.RECEIVE_BOOT_COMPLETED", "Qurilma yuklanganda ishga tushishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", "Notificationlarni tinglashga ruxsati bor (notificationlarni o'qish)");
        PERMISSION_DESCRIPTIONS.put("android.permission.BIND_DEVICE_ADMIN", "Qurilma admini bo'lishga ruxsati bor");
        PERMISSION_DESCRIPTIONS.put("android.permission.MASTER_CLEAR", "Qurilmani tozalashga (factory reset) ruxsati bor");
    }

    // Xavflilik darajalari
    private static final HashMap<String, String> PERMISSION_DANGER_LEVELS = new HashMap<>();
    static {
        // Yuqori xavf
        PERMISSION_DANGER_LEVELS.put("android.permission.SYSTEM_ALERT_WINDOW", "Yuqori xavf: Bu ruxsat viruslar tomonidan ekran ustida yolg'on oynalar ochish uchun ishlatiladi.");
        PERMISSION_DANGER_LEVELS.put("android.permission.ACCESSIBILITY_SERVICE", "Yuqori xavf: Ekranni to'liq boshqarish va ma'lumotlarni o'g'irlash imkonini beradi.");
        PERMISSION_DANGER_LEVELS.put("android.permission.BIND_ACCESSIBILITY_SERVICE", "Yuqori xavf: Qulaylik xizmatlarini virus sifatida ishlatishga imkon beradi.");
        PERMISSION_DANGER_LEVELS.put("android.permission.WRITE_SECURE_SETTINGS", "Yuqori xavf: Tizim xavfsiz sozlamalarini o'zgartirib, qurilmani buzishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.WRITE_SETTINGS", "O'rta xavf: Tizim sozlamalarini o'zgartirish imkonini beradi.");
        PERMISSION_DANGER_LEVELS.put("android.permission.READ_PHONE_STATE", "O'rta xavf: Telefon raqami va holatini o'qib, shaxsiy ma'lumotlarni o'g'irlashi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.READ_SMS", "Yuqori xavf: SMS xabarlarni o'qib, bank kodlari va shaxsiy ma'lumotlarni o'g'irlashi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.SEND_SMS", "Yuqori xavf: Pul yo'qotishga olib keladigan SMS yuborishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.RECEIVE_SMS", "Yuqori xavf: Kelgan SMSlarni o'qib, muhim ma'lumotlarni o'g'irlashi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.READ_CONTACTS", "O'rta xavf: Kontaktlarni o'qib, spam yuborish yoki o'g'irlash uchun ishlatiladi.");
        PERMISSION_DANGER_LEVELS.put("android.permission.WRITE_CONTACTS", "O'rta xavf: Kontaktlarni o'zgartirib, zararli o'zgarishlar kiritishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.READ_CALL_LOG", "O'rta xavf: Qo'ng'iroq tarixini o'qib, shaxsiy ma'lumotlarni o'g'irlashi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.WRITE_CALL_LOG", "O'rta xavf: Qo'ng'iroq tarixini o'zgartirishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.CAMERA", "Yuqori xavf: Kamerani yashirin ishlatib, surat yoki video olishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.RECORD_AUDIO", "Yuqori xavf: Mikrofonni yashirin ishlatib, suhbatlarni yozib olishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.ACCESS_FINE_LOCATION", "Yuqori xavf: Joylashuvni aniq kuzatib, shaxsiy hayotga tajovuz qilishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.ACCESS_COARSE_LOCATION", "O'rta xavf: Taxminiy joylashuvni kuzatishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.READ_EXTERNAL_STORAGE", "O'rta xavf: Fayllarni o'qib, shaxsiy ma'lumotlarni o'g'irlashi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.WRITE_EXTERNAL_STORAGE", "O'rta xavf: Fayllarni o'zgartirib, zararli fayllar joylashtirishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.MANAGE_EXTERNAL_STORAGE", "Yuqori xavf: Butun xotirani boshqarib, fayllarni o'chirishi yoki o'zgartirishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "O'rta xavf: Batareya tejamkorligini o'tkazib, fonda doimiy ishlashi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.PACKAGE_USAGE_STATS", "O'rta xavf: Boshqa ilovalar foydalanishini kuzatishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.QUERY_ALL_PACKAGES", "O'rta xavf: Barcha ilovalarni ko'rishi va boshqarishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.INSTALL_PACKAGES", "Yuqori xavf: Boshqa ilovalarni o'rnatib, virus tarqatishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.DELETE_PACKAGES", "Yuqori xavf: Boshqa ilovalarni o'chirib, tizimni buzishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.MODIFY_PHONE_STATE", "Yuqori xavf: Telefon holatini o'zgartirib, qurilmani bloklashi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.INTERNET", "Past xavf: Internetga ulanishi mumkin, ammo boshqa ruxsatlar bilan birgalikda xavfli.");
        PERMISSION_DANGER_LEVELS.put("android.permission.ACCESS_NETWORK_STATE", "Past xavf: Tarmoq holatini tekshirishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.ACCESS_WIFI_STATE", "Past xavf: Wi-Fi holatini tekshirishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.CHANGE_WIFI_STATE", "O'rta xavf: Wi-Fi ni o'zgartirishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.WAKE_LOCK", "O'rta xavf: Qurilmani uyg'oq holda saqlab, fonda ishlashi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.DISABLE_KEYGUARD", "Yuqori xavf: Ekran qulfini o'chirib, qurilmaga kirishni osonlashtirishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.SYSTEM_OVERLAY_WINDOW", "Yuqori xavf: Ekran ustida yolg'on oynalar ochishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND", "O'rta xavf: Fonda ishlash imkonini beradi.");
        PERMISSION_DANGER_LEVELS.put("android.permission.REQUEST_DELETE_PACKAGES", "Yuqori xavf: Paketlarni o'chirishni so'rashi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.REQUEST_INSTALL_PACKAGES", "Yuqori xavf: Paketlarni o'rnatishni so'rashi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.RECEIVE_BOOT_COMPLETED", "O'rta xavf: Qurilma yuklanganda avtomatik ishga tushishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", "Yuqori xavf: Notificationlarni o'qib, shaxsiy ma'lumotlarni o'g'irlashi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.BIND_DEVICE_ADMIN", "Yuqori xavf: Qurilma admini bo'lib, qurilmani bloklashi yoki ma'lumotlarni o'chirishi mumkin.");
        PERMISSION_DANGER_LEVELS.put("android.permission.MASTER_CLEAR", "Yuqori xavf: Qurilmani factory reset qilishi mumkin.");
    }

    // Virusga xos permissionlar (fon rejimi, notification o'qish va boshqalar)
    private static final Set<String> VIRUS_LIKE_PERMISSIONS = new HashSet<>();
    static {
        VIRUS_LIKE_PERMISSIONS.add("android.permission.WAKE_LOCK");
        VIRUS_LIKE_PERMISSIONS.add("android.permission.RECEIVE_BOOT_COMPLETED");
        VIRUS_LIKE_PERMISSIONS.add("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE");
        VIRUS_LIKE_PERMISSIONS.add("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
        VIRUS_LIKE_PERMISSIONS.add("android.permission.SYSTEM_ALERT_WINDOW");
        VIRUS_LIKE_PERMISSIONS.add("android.permission.ACCESSIBILITY_SERVICE");
        VIRUS_LIKE_PERMISSIONS.add("android.permission.BIND_ACCESSIBILITY_SERVICE");
        // Qo'shimcha virusga xos ruxsatlarni qo'shishingiz mumkin
    }

    public static void handleNewFile(Context context, String filePath) {
        File file = new File(filePath);
        String fileName = file.getName().toLowerCase();

        Log.d("FileScanHelper", "Processing file: " + filePath + ", exists=" + file.exists() + ", writable=" + file.canWrite());

        if (filePath.contains("/sdcard/Telegram/Telegram Files/")) {
            sendNotification(context, "Telegramdan fayl yuklanmoqda", "Telegram: " + fileName);
        } else {
            Log.d("FileScanHelper", "Yangi fayl: " + fileName);
        }

        // Relaxed check for APK files (handles cases without .apk extension)
        boolean isApk = fileName.endsWith(".apk") || fileName.equals("app");
        if (isApk) {
            Log.d("FileScanHelper", "APK fayl aniqlanmoqda: " + fileName);
            // Display AndroidManifest.xml content
            String manifestContent = getManifestContent(filePath);
            if (manifestContent != null) {
                Log.d("FileScanHelper", "AndroidManifest.xml content:\n" + manifestContent);
                // Optionally show in a dialog
                if (context instanceof Activity) {
                    new AlertDialog.Builder(context)
                            .setTitle("AndroidManifest.xml Content")
                            .setMessage(manifestContent)
                            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                            .show();
                }
            } else {
                Log.e("FileScanHelper", "Failed to extract AndroidManifest.xml content for: " + filePath);
                // Fallback to PackageManager for permissions
                Set<String> permissions = extractPermissionsFromApk(filePath, context);
                if (!permissions.isEmpty()) {
                    Log.d("FileScanHelper", "Permissions extracted via PackageManager: " + permissions);
                }
            }

            boolean hasSuspiciousPermissions = analyzeApkManifest(filePath, context);
            if (hasSuspiciousPermissions) {
                sendNotification(context, "Shubhali APK", "APK faylida xavfli ruxsatlar topildi! " + fileName);
            }

            // Yangi qism: Permissionlarni o'zbekcha ko'rsatish va xavflilik haqida bildirishnoma yuborish
            Set<String> permissions = extractPermissionsFromApk(filePath, context);
            showDangerousPermissions(context, permissions, fileName);
        }

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
            Log.d("FileScanHelper", "Attempting to delete virus file: " + filePath + ", exists=" + file.exists() + ", writable=" + file.canWrite());
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

        try {
            String downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            if (file.getAbsolutePath().startsWith(downloadsDir)) {
                boolean shouldDelete = false;
                if (isApk) {
                    boolean hasSuspiciousPermissions = analyzeApkManifest(filePath, context);
                    if (hasSuspiciousPermissions) shouldDelete = true;
                    if (md5 != null && (checkVirusTotal(md5) || checkLocalVirus(context, md5))) {
                        shouldDelete = true;
                    }
                } else {
                    String md5Other = getMD5(file);
                    if (md5Other != null && (checkVirusTotal(md5Other) || checkLocalVirus(context, md5Other))) {
                        shouldDelete = true;
                    }
                }

                if (shouldDelete) {
                    sendNotification(context, "Xavfli fayl", "Download papkasida zararli fayl topildi – o'chirilmoqda: " + fileName);
                    Log.d("FileScanHelper", "Attempting to delete file from Download: " + filePath + ", exists=" + file.exists() + ", writable=" + file.canWrite());
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

    // Yangi metod: Xavfli permissionlarni o'zbekcha ko'rsatish va xavflilik haqida bildirishnoma
    private static void showDangerousPermissions(Context context, Set<String> permissions, String fileName) {
        StringBuilder message = new StringBuilder("APK fayli (" + fileName + ") uchun xavfli ruxsatlar:\n\n");
        StringBuilder dangerLevels = new StringBuilder("\n\nBu ruxsatlarning xavfliligi:\n\n");
        boolean hasVirusLike = false;

        for (String perm : permissions) {
            if (PERMISSION_DESCRIPTIONS.containsKey(perm)) {
                message.append("• ").append(PERMISSION_DESCRIPTIONS.get(perm)).append("\n");
                dangerLevels.append("• ").append(PERMISSION_DESCRIPTIONS.get(perm)).append(": ").append(PERMISSION_DANGER_LEVELS.getOrDefault(perm, "Noma'lum xavf")).append("\n");

                if (VIRUS_LIKE_PERMISSIONS.contains(perm)) {
                    hasVirusLike = true;
                }
            }
        }

        if (message.length() > "APK fayli (".length() + fileName.length() + ") uchun xavfli ruxsatlar:\n\n".length()) {
            // Bildirishnoma yuborish
            sendNotification(context, "Xavfli ruxsatlar topildi", message.toString() + dangerLevels.toString());

            // Agar virusga xos ruxsatlar bo'lsa, o'chirish
            if (hasVirusLike) {
                sendNotification(context, "Virus aniqlandi", "Virusga xos ruxsatlar topildi (fon rejimi, notification o'qish va b.k.)! Fayl o'chirilmoqda: " + fileName);
                File file = new File(context.getPackageManager().getPackageArchiveInfo(fileName, 0).applicationInfo.sourceDir); // APK yo'lini olish
                deleteFile(context, file);
            }
        } else {
            Log.d("FileScanHelper", "Hech qanday xavfli ruxsat topilmadi: " + fileName);
        }
    }

    // Method to extract and decode AndroidManifest.xml content using AXMLResource
    public static String getManifestContent(String apkPath) {
        FileInputStream fileInputStream = null;
        ByteArrayOutputStream baos = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(apkPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("AndroidManifest.xml")) {
                    // Read binary XML into a byte array
                    ByteArrayOutputStream tempBaos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        tempBaos.write(buffer, 0, len);
                    }
                    byte[] manifestBytes = tempBaos.toByteArray();
                    // Log raw bytes length for debugging
                    Log.d("FileScanHelper", "Raw AndroidManifest.xml bytes length: " + manifestBytes.length);
                    if (manifestBytes.length == 0) {
                        Log.e("FileScanHelper", "AndroidManifest.xml is empty for: " + apkPath);
                        return null;
                    }
                    // Use AXMLResource to decode binary XML
                    AXMLResource axmlResource = new AXMLResource();
                    fileInputStream = new FileInputStream(new File(apkPath)) {
                        private final byte[] data = manifestBytes;
                        private int position = 0;

                        @Override
                        public int read(byte[] b, int off, int len) {
                            int count = Math.min(len, data.length - position);
                            if (count <= 0) return -1;
                            System.arraycopy(data, position, b, off, count);
                            position += count;
                            return count;
                        }
                    };
                    axmlResource.read(fileInputStream);
                    // Capture decoded XML
                    baos = new ByteArrayOutputStream();
                    axmlResource.write(baos);
                    String decodedXml = baos.toString("UTF-8");
                    if (decodedXml.isEmpty()) {
                        Log.e("FileScanHelper", "AXMLResource returned empty XML for: " + apkPath);
                        return null;
                    }
                    return decodedXml;
                }
            }
            Log.e("FileScanHelper", "AndroidManifest.xml not found in APK: " + apkPath);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            Log.e("FileScanHelper", "Failed to read AndroidManifest.xml from APK: " + apkPath + "\n" + sw.toString());
        } finally {
            try {
                if (fileInputStream != null) fileInputStream.close();
                if (baos != null) baos.close();
            } catch (IOException e) {
                Log.e("FileScanHelper", "Error closing streams for: " + apkPath, e);
            }
        }
        return null;
    }

    public static boolean deleteFile(Context context, File file) {
        return FileDeletionHelp.deleteFileSimple(context, file.getAbsolutePath());
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
            return sb.toString();
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

    private static boolean analyzeApkManifest(String apkPath, Context context) {
        Set<String> suspiciousPermissions = new HashSet<>(PERMISSION_DESCRIPTIONS.keySet());

        try {
            Set<String> foundPermissions = extractPermissionsFromApk(apkPath, context);
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
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            Log.e("FileScanHelper", "APK manifestini tahlil qilishda xatolik: " + sw.toString());
            return false;
        }
    }

    private static Set<String> extractPermissionsFromApk(String apkPath, Context context) {
        Set<String> permissions = new HashSet<>();
        FileInputStream fileInputStream = null;
        ByteArrayOutputStream baos = null;
        try {
            // Try AXMLResource first
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(apkPath))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals("AndroidManifest.xml")) {
                        // Read binary XML into a byte array
                        ByteArrayOutputStream tempBaos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            tempBaos.write(buffer, 0, len);
                        }
                        byte[] manifestBytes = tempBaos.toByteArray();
                        Log.d("FileScanHelper", "Raw AndroidManifest.xml bytes length: " + manifestBytes.length);
                        if (manifestBytes.length == 0) {
                            Log.e("FileScanHelper", "AndroidManifest.xml is empty for: " + apkPath);
                            throw new IOException("Empty AndroidManifest.xml");
                        }
                        // Use AXMLResource to decode binary XML
                        AXMLResource axmlResource = new AXMLResource();
                        fileInputStream = new FileInputStream(new File(apkPath)) {
                            private final byte[] data = manifestBytes;
                            private int position = 0;

                            @Override
                            public int read(byte[] b, int off, int len) {
                                int count = Math.min(len, data.length - position);
                                if (count <= 0) return -1;
                                System.arraycopy(data, position, b, off, count);
                                position += count;
                                return count;
                            }
                        };
                        axmlResource.read(fileInputStream);
                        // Capture decoded XML
                        baos = new ByteArrayOutputStream();
                        axmlResource.write(baos);
                        String manifestContent = baos.toString("UTF-8");
                        if (manifestContent.isEmpty()) {
                            Log.e("FileScanHelper", "AXMLResource returned empty XML for: " + apkPath);
                            throw new IOException("Empty decoded XML");
                        }
                        extractPermissionsFromManifest(manifestContent, permissions);
                        if (!permissions.isEmpty()) {
                            return permissions; // Return if permissions were found
                        }
                    }
                }
                Log.w("FileScanHelper", "AndroidManifest.xml not found or no permissions extracted via AXMLResource for: " + apkPath);
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            Log.e("FileScanHelper", "AXMLResource failed for: " + apkPath + "\n" + sw.toString());
        } finally {
            try {
                if (fileInputStream != null) fileInputStream.close();
                if (baos != null) baos.close();
            } catch (IOException e) {
                Log.e("FileScanHelper", "Error closing streams for: " + apkPath, e);
            }
        }

        // Fallback to PackageManager
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_PERMISSIONS);
            if (packageInfo != null && packageInfo.requestedPermissions != null) {
                for (String perm : packageInfo.requestedPermissions) {
                    permissions.add(perm);
                    Log.d("FileScanHelper", "Ruxsat topildi (PackageManager): " + perm);
                }
            } else {
                Log.w("FileScanHelper", "PackageManager found no permissions for: " + apkPath);
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            Log.e("FileScanHelper", "PackageManager failed to extract permissions for: " + apkPath + "\n" + sw.toString());
        }
        return permissions;
    }

    private static void extractPermissionsFromManifest(String manifestContent, Set<String> permissions) {
        // Parse the decoded XML for <uses-permission> tags
        String[] lines = manifestContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.contains("<uses-permission") && line.contains("android:name=")) {
                int startIndex = line.indexOf("android:name=\"");
                if (startIndex != -1) {
                    startIndex += "android:name=\"".length();
                    int endIndex = line.indexOf("\"", startIndex);
                    if (endIndex != -1) {
                        String permission = line.substring(startIndex, endIndex);
                        permissions.add(permission);
                        Log.d("FileScanHelper", "Ruxsat topildi (AXMLResource): " + permission);
                    }
                }
            }
        }
    }

    public static boolean isNonPlayStoreApp(Context context, String packageName) {
        try {
            if (packageName == null) return false;
            if (context != null && packageName.equals(context.getPackageName())) {
                return false;
            }
            PackageManager pm = context.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);

            String installerPackageName = null;
            try {
                installerPackageName = pm.getInstallerPackageName(packageName);
            } catch (Throwable ignored) {}

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    android.content.pm.InstallSourceInfo src = pm.getInstallSourceInfo(packageName);
                    if (src != null) {
                        String installing = src.getInstallingPackageName();
                        String initiating = src.getInitiatingPackageName();
                        String originating = src.getOriginatingPackageName();
                        boolean play = "com.android.vending".equals(installing)
                                || "com.android.vending".equals(initiating)
                                || "com.android.vending".equals(originating);
                        if (play) return false;
                    }
                } catch (Throwable ignored) {}
            }

            if (!"com.android.vending".equals(installerPackageName)) {
                Log.d("FileScanHelper", "Play Store dan o'rnatilmagan ilova: " + packageName +
                        " (installer: " + String.valueOf(installerPackageName) + ")");
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
            String key = "last_full_app_scan_ts";
            android.content.SharedPreferences prefs = context.getSharedPreferences("antivirus_prefs", Context.MODE_PRIVATE);
            long last = prefs.getLong(key, 0L);
            long now = System.currentTimeMillis();
            if (now - last < 6 * 60 * 60 * 1000L) {
                Log.d("FileScanHelper", "Skipping full installed-apps scan (recently scanned)");
                return;
            }

            PackageManager pm = context.getPackageManager();
            List<PackageInfo> packages = null;
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

    private static String getPackageNameFromApk(String apkPath, Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(apkPath, 0);
            if (packageInfo != null) {
                return packageInfo.packageName;
            }
            String fileName = new File(apkPath).getName();
            if (fileName.endsWith(".apk")) {
                return fileName.substring(0, fileName.length() - 4);
            } else if (fileName.equals("app")) {
                return "com.example.app"; // Fallback
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
        try {
            if (isNonPlayStoreApp(context, packageName)) {
                info.reasons.add("Ilova Play Marketdan o'rnatilmagan (no Play Store)");
            }
        } catch (Throwable ignored) {}
        Set<String> dangerous = getDangerousPermissionsFromApk(apkPath, context);
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
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            info.appName = pm.getApplicationLabel(appInfo).toString();
        } catch (Exception e) {
            info.appName = packageName;
        }
        return info;
    }

    public static Set<String> getDangerousPermissionsFromApk(String apkPath, Context context) {
        Set<String> all = extractPermissionsFromApk(apkPath, context);
        Set<String> dangerous = new HashSet<>();
        for (String perm : PERMISSION_DESCRIPTIONS.keySet()) {
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

class FileDeletionHelp {
    public static boolean deleteFileSimple(Context context, String filePath) {
        File file = new File(filePath);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ContentResolver resolver = context.getContentResolver();
                Uri uri = getUriForFile(context, file);
                if (uri != null) {
                    int rowsDeleted = resolver.delete(uri, null, null);
                    Log.d("FileDeletionHelp", "Deleted rows via MediaStore: " + rowsDeleted);
                    return rowsDeleted > 0;
                } else {
                    Log.e("FileDeletionHelp", "Could not find URI for file: " + filePath);
                    return false;
                }
            } else {
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (!deleted) {
                        Log.e("FileDeletionHelp", "Failed to delete file: " + filePath);
                    }
                    return deleted;
                } else {
                    Log.e("FileDeletionHelp", "File does not exist: " + filePath);
                    return false;
                }
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            Log.e("FileDeletionHelp", "Error deleting file: " + filePath + "\n" + sw.toString());
            return false;
        }
    }

    private static Uri getUriForFile(Context context, File file) {
        try {
            String selection = MediaStore.Files.FileColumns.DATA + "=?";
            String[] selectionArgs = new String[]{file.getAbsolutePath()};
            Uri queryUri = MediaStore.Files.getContentUri("external");

            Cursor cursor = context.getContentResolver().query(
                    queryUri,
                    new String[]{MediaStore.Files.FileColumns._ID},
                    selection,
                    selectionArgs,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID));
                cursor.close();
                return Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), String.valueOf(id));
            }
            if (cursor != null) {
                cursor.close();
            }
            return null;
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            Log.e("FileDeletionHelp", "Error finding URI for file: " + file.getAbsolutePath() + "\n" + sw.toString());
            return null;
        }
    }
}