package uz.csec.antivirus;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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
import android.content.res.AXMLResource;
import java.util.HashMap;

public class FileScanHelper {
    private static final long NOTIFICATION_DEDUP_WINDOW_MS = 60_000L;
    private static final Map<String, Long> LAST_NOTIFICATION_TIME_BY_KEY = new ConcurrentHashMap<>();

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

    private static final HashMap<String, String> PERMISSION_DANGER_LEVELS = new HashMap<>();
    static {
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
    private static final Set<String> VIRUS_LIKE_PERMISSIONS = new HashSet<>();
    static {
        VIRUS_LIKE_PERMISSIONS.add("android.permission.WAKE_LOCK");
        VIRUS_LIKE_PERMISSIONS.add("android.permission.RECEIVE_BOOT_COMPLETED");
        VIRUS_LIKE_PERMISSIONS.add("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE");
        VIRUS_LIKE_PERMISSIONS.add("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
        VIRUS_LIKE_PERMISSIONS.add("android.permission.SYSTEM_ALERT_WINDOW");
        VIRUS_LIKE_PERMISSIONS.add("android.permission.ACCESSIBILITY_SERVICE");
        VIRUS_LIKE_PERMISSIONS.add("android.permission.BIND_ACCESSIBILITY_SERVICE");
    }

    public static void handleNewFile(Context context, String filePath) {
        File file = new File(filePath);
        String fileName = file.getName().toLowerCase();

        if (filePath.contains("/sdcard/Telegram/Telegram Files/")) {
            sendNotification(context, "Telegramdan fayl yuklanmoqda", "Telegram: " + fileName);
        } else {
            Log.d("FileScanHelper", "Yangi fayl: " + fileName);
        }

        boolean isApk = fileName.endsWith(".apk") || fileName.equals("app");
        if (isApk) {
            String manifestContent = getManifestContent(filePath);
            if (manifestContent != null) {
                if (context instanceof Activity) {
                    new AlertDialog.Builder(context)
                            .setTitle("AndroidManifest.xml Content")
                            .setMessage(manifestContent)
                            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                            .show();
                }
            } else {
                Set<String> permissions = extractPermissionsFromApk(filePath, context);
                if (!permissions.isEmpty()) {
                    Log.d("FileScanHelper", "Permissions extracted via PackageManager: " + permissions);
                }
            }

            Set<String> permissions = extractPermissionsFromApk(filePath, context);
            showPermissionsNotification(context, permissions, fileName, filePath);
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
            if (deleteFile(context, file)) {
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
            }
        } catch (Exception e) {
            Log.e("FileScanHelper", "Download papkasini tekshirishda xatolik", e);
        }
    }

    private static void showPermissionsNotification(Context context, Set<String> permissions, String fileName, String filePath) {
        StringBuilder message = new StringBuilder("");
        for (String perm : permissions) {
            String description = PERMISSION_DESCRIPTIONS.getOrDefault(perm, perm);
            message.append("• ").append(description).append("\n");
        }
        String notificationText = message.toString();

        Intent intent = new Intent(context, PermissionDialogActivity.class);
        intent.putExtra("permissions", notificationText);
        intent.putExtra("fileName", fileName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                fileName.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        String key = "Permissions|" + fileName;
        long now = System.currentTimeMillis();
        Long lastTs = LAST_NOTIFICATION_TIME_BY_KEY.get(key);
        if (lastTs != null && (now - lastTs) < NOTIFICATION_DEDUP_WINDOW_MS) {
            return;
        }
        LAST_NOTIFICATION_TIME_BY_KEY.put(key, now);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "permissions_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Permissions Alerts", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(fileName + " ilova ruxsatlari\n\n" )
                .setContentText(notificationText.length() > 100 ? notificationText.substring(0, 100) + "..." : notificationText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationText))
                .setSmallIcon(R.drawable.ic_antivirus)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        int notificationId = key.hashCode();
        manager.notify(notificationId, builder.build());
    }

    public static String getManifestContent(String apkPath) {
        FileInputStream fileInputStream = null;
        ByteArrayOutputStream baos = null;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(apkPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("AndroidManifest.xml")) {
                    ByteArrayOutputStream tempBaos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        tempBaos.write(buffer, 0, len);
                    }
                    byte[] manifestBytes = tempBaos.toByteArray();
                    if (manifestBytes.length == 0) {
                        return null;
                    }
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
                    baos = new ByteArrayOutputStream();
                    axmlResource.write(baos);
                    String decodedXml = baos.toString("UTF-8");
                    if (decodedXml.isEmpty()) {
                        return null;
                    }
                    return decodedXml;
                }
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
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
                }
            }
            return suspiciousCount >= 3;

        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            return false;
        }
    }

    private static Set<String> extractPermissionsFromApk(String apkPath, Context context) {
        Set<String> permissions = new HashSet<>();
        FileInputStream fileInputStream = null;
        ByteArrayOutputStream baos = null;
        try {
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(apkPath))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().equals("AndroidManifest.xml")) {
                        ByteArrayOutputStream tempBaos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            tempBaos.write(buffer, 0, len);
                        }
                        byte[] manifestBytes = tempBaos.toByteArray();
                        if (manifestBytes.length == 0) {
                            throw new IOException("Empty AndroidManifest.xml");
                        }
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
                        baos = new ByteArrayOutputStream();
                        axmlResource.write(baos);
                        String manifestContent = baos.toString("UTF-8");
                        if (manifestContent.isEmpty()) {
                            throw new IOException("Empty decoded XML");
                        }
                        extractPermissionsFromManifest(manifestContent, permissions);
                        if (!permissions.isEmpty()) {
                            return permissions;
                        }
                    }
                }
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
        } finally {
            try {
                if (fileInputStream != null) fileInputStream.close();
                if (baos != null) baos.close();
            } catch (IOException e) {
                Log.e("FileScanHelper", "Error closing streams for: " + apkPath, e);
            }
        }

        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_PERMISSIONS);
            if (packageInfo != null && packageInfo.requestedPermissions != null) {
                for (String perm : packageInfo.requestedPermissions) {
                    permissions.add(perm);
                }
            } else {
                Log.w("FileScanHelper", "PackageManager found no permissions for: " + apkPath);
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
        }
        return permissions;
    }
    private static void extractPermissionsFromManifest(String manifestContent, Set<String> permissions) {
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
                return true;
            }

            return false;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        } catch (Exception e) {
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
                return;
            }

            PackageManager pm = context.getPackageManager();
            List<PackageInfo> packages = null;
            try {
                packages = pm.getInstalledPackages(0);
            } catch (Throwable t) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                try {
                    packages = pm.getInstalledPackages(0);
                } catch (Throwable t2) {
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
                    return rowsDeleted > 0;
                } else {
                    return false;
                }
            } else {
                if (file.exists()) {
                    boolean deleted = file.delete();
                    return deleted;
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
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
            return null;
        }
    }
}

class PermissionDialogActivity extends android.app.Activity {
    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String permissions = getIntent().getStringExtra("permissions");
        String fileName = getIntent().getStringExtra("fileName");

        new AlertDialog.Builder(this)
                .setTitle("APK Ruxsatlari: " + fileName)
                .setMessage(permissions)
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .setCancelable(false)
                .show();
    }
}