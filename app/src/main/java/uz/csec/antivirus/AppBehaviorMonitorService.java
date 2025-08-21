package uz.csec.antivirus;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Ilovalarning xulq-atvorini fon rejimida tahlil qiluvchi servis.
 * O'rnatilgan ilovalarni davriy skaner qiladi va shubhali xususiyatlar aniqlansa
 * qizil bildirishnoma va tafsilotlar bilan ogohlantiradi.
 */
public class AppBehaviorMonitorService extends Service {
    private static final String TAG = "AppBehaviorMonitor";
    private static final String CHANNEL_ID_MONITOR = "app_behavior_monitor";
    private static final String CHANNEL_ID_ALERT = "virus_alerts";
    private static final String CHANNEL_ID_WARNING = "app_warnings"; // Yangi kanal qo'shildi
    private static final long SCAN_INTERVAL_MIN = 15;

    // ====== Heuristic thresholds ======
    private static final int DEX_SMALL_BYTES = 300 * 1024; // <300KB -> shubhali
    private static final double ENTROPY_RAW_SUSP = 7;     // 7.6/8 ≈ 0.95
    private static final double ENTROPY_NORM_SUSP = 0.95;
    private static final int MIN_MANIFEST_SIZE = 100;      // Minimal AndroidManifest.xml hajmi (baytlarda)

    private ScheduledExecutorService scheduler;

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannels();
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                scanInstalledApps(getApplicationContext());
            } catch (Throwable t) {
                Log.e(TAG, "scan error", t);
            }
        }, 0, SCAN_INTERVAL_MIN, TimeUnit.MINUTES);
    }

    private void ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel monitor = new NotificationChannel(
                    CHANNEL_ID_MONITOR, "App Monitor", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(monitor);

            NotificationChannel alerts = new NotificationChannel(
                    CHANNEL_ID_ALERT, "Virus Alerts", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(alerts);

            // Yangi kanal qo'shildi: Oddiy ogohlantirishlar uchun
            NotificationChannel warnings = new NotificationChannel(
                    CHANNEL_ID_WARNING, "App Warnings", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(warnings);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID_MONITOR)
                .setContentTitle("Antivirus")
                .setContentText("Ilovalar monitoringi faol")
                .setColorized(true)
                .setSmallIcon(R.drawable.ic_antivirus)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .build();
        try {
            startForeground(1002, notification);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void scanInstalledApps(Context context) {
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> packages;
        try {
            packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        } catch (Throwable t) {
            Log.e(TAG, "getInstalledPackages failed", t);
            return;
        }

        for (PackageInfo pi : packages) {
            if (pi == null || pi.packageName == null) continue;
            if (pi.packageName.equals(getPackageName())) continue;
            ApplicationInfo ai = pi.applicationInfo;
            if (ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;

            try {
                String apkPath = (ai != null) ? ai.sourceDir : null;
                if (apkPath == null) continue;

                List<String> virusReasons = new ArrayList<>();
                List<String> warningReasons = new ArrayList<>();
                analyzeApp(context, pi, apkPath, virusReasons, warningReasons);

                if (!virusReasons.isEmpty()) {
                    maybeNotifyVirus(context, pi.packageName, getAppLabel(pm, ai), virusReasons, apkPath);
                }
                if (!warningReasons.isEmpty()) {
                    maybeNotifyWarning(context, pi.packageName, getAppLabel(pm, ai), warningReasons);
                }
            } catch (Throwable t) {
                Log.e(TAG, "analyze error for " + pi.packageName, t);
            }
        }
    }

    private void maybeNotifyVirus(Context context, String packageName, String appName, List<String> reasons, String apkPath) {
        // Har safar bildirishnoma chiqarish uchun SharedPreferences check olib tashlandi
        String message = android.text.TextUtils.join("\n", reasons);
        Intent dialogIntent = new Intent(context, DangerDialogActivity.class)
                .putExtra("appName", appName)
                .putExtra("reasons", message)
                .putExtra("packageName", packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, packageName.hashCode(), dialogIntent,
                (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0) | PendingIntent.FLAG_UPDATE_CURRENT
        );
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
                .setSmallIcon(R.drawable.ic_antivirus)
                .setContentTitle("Virus aniqlandi: " + appName)
                .setContentText(reasons.get(0))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(Color.RED)
                .setColorized(true)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(Math.abs(packageName.hashCode()), b.build());
    }

    private void maybeNotifyWarning(Context context, String packageName, String appName, List<String> reasons) {
        // Har safar bildirishnoma chiqarish uchun SharedPreferences check olib tashlandi
        String message = android.text.TextUtils.join("\n", reasons);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID_WARNING)
                .setSmallIcon(R.drawable.ic_antivirus)
                .setContentTitle("Ogohlantirish: " + appName)
                .setContentText(reasons.get(0))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(Math.abs(packageName.hashCode() + 1), b.build()); // Boshqa ID ishlatish uchun +1
    }

    private String getAppLabel(PackageManager pm, ApplicationInfo ai) {
        try { return pm.getApplicationLabel(ai).toString(); } catch (Throwable t) { return ai.packageName; }
    }

    private void analyzeApp(Context context, PackageInfo pi, String apkPath, List<String> virusReasons, List<String> warningReasons) {
        String packageName = pi.packageName;

        // 1) PackageManager orqali ilova ma'lumotlarini qayta tekshirish
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            virusReasons.add("Paket topilmadi (PackageManager.NameNotFoundException) — ilova noto'g'ri o'rnatilgan yoki shubhali.");
        }

        // 2) Invalid CEN header yoki encrypted entry
        try {
            new ZipFile(apkPath);
        } catch (java.util.zip.ZipException e) {
            String msg = e.getMessage().toLowerCase(java.util.Locale.US);
            if (msg.contains("invalid cen header") || msg.contains("encrypted entry")) {
                virusReasons.add("Invalid CEN header yoki encrypted entry aniqlandi — shubhali APK struktura, virus ehtimoli yuqori.");
            }
        } catch (IOException e) {
            virusReasons.add("APK faylini ochishda I/O xatosi — fayl buzilgan yoki shifrlangan bo'lishi mumkin.");
            Log.w(TAG, "ZIP open failed for " + packageName, e);
        }

        // 3) Play Store'dan o'rnatilganligini tekshirish — endi alohida warningReasons ga qo'shiladi
        try {
            boolean isNonPlay = FileScanHelper.isNonPlayStoreApp(context, packageName);
            if (isNonPlay) {
                warningReasons.add("Bu ilova Play Store'dan o'rnatilmagan; xavf yuqori bo'lishi mumkin.");
            }
        } catch (Throwable ignored) {}

        // 4) Manifestdagi actionlar
        try {
            Set<String> actions = extractActionsFromApk(apkPath);
            Set<String> suspicious = new HashSet<>();
            for (String action : actions) {
                if (!action.startsWith("android.intent.action.")) suspicious.add(action);
                if (action.contains("BOOT") || action.contains("OVERLAY") || action.contains("USER_PRESENT")) {
                    suspicious.add(action);
                }
            }
            if (!suspicious.isEmpty()) {
                virusReasons.add("Maxsus action(lar): " + android.text.TextUtils.join(", ", suspicious));
            }
        } catch (Throwable t) {
            Log.w(TAG, "action analyze failed", t);
        }

        // 5) Manifest holati
        try {
            byte[] manBytes = readApkEntry(apkPath, "AndroidManifest.xml");
            if (isManifestSuspicious(manBytes)) {
                virusReasons.add("AndroidManifest.xml g'ayritabiiy (bo'sh yoki juda kichik) — packer/anti-analysis ehtimoli yuqori.");
            }
        } catch (Throwable t) {
            Log.w(TAG, "manifest analyze failed", t);
        }

        // 6) Fake dex fayllar mavjudligini tekshirish
        try {
            int fakeDex = countFakeDexEntries(apkPath);
            if (fakeDex > 0) {
                virusReasons.add("APK ichida 'classes.dex' ni yashirishga urinishlar: " + fakeDex + " ta soxta fayl (masalan classes.dex.png/xml/webp…).");
            }
        } catch (Throwable t) {
            Log.w(TAG, "fake dex analyze failed", t);
        }

        // 7) classes.dex hajmi va entropiyasi
        try {
            byte[] dex = readApkEntry(apkPath, "classes.dex");
            if (dex != null) {
                if (dex.length < DEX_SMALL_BYTES) {
                    virusReasons.add("classes.dex juda kichik (" + formatSize(dex.length) + ") — loader/dropper ehtimoli.");
                }

                double H = computeShannonEntropy(dex);
                double Hn = H / 8.0;
                if (H >= ENTROPY_RAW_SUSP || Hn >= ENTROPY_NORM_SUSP) {
                    virusReasons.add("Yuqori entropiya (H=" + String.format(java.util.Locale.US, "%.2f", H)
                            + ", norm=" + String.format(java.util.Locale.US, "%.2f", Hn)
                            + ") — shifrlangan/paketlangan payload ehtimoli.");
                }
            } else {
                virusReasons.add("classes.dex topilmadi yoki o'qib bo'lmadi — juda g'alati holat.");
            }
        } catch (Throwable t) {
            Log.w(TAG, "dex analyze failed", t);
        }
    }

    private static boolean isManifestSuspicious(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            Log.d(TAG, "Manifest is null or empty");
            return true;
        }

        // AndroidManifest.xml AXML formatining magic raqami (0x03 0x00 0x08 0x00)
        if (bytes.length < 4 || bytes[0] != 0x03 || bytes[1] != 0x00 || bytes[2] != 0x08 || bytes[3] != 0x00) {
            Log.d(TAG, "Invalid AXML magic number: " + Arrays.toString(Arrays.copyOf(bytes, Math.min(bytes.length, 4))));
            return true;
        }

        // Manifest hajmi juda kichik bo'lsa (masalan, <100 bayt)
        if (bytes.length < MIN_MANIFEST_SIZE) {
            Log.d(TAG, "Manifest too small: " + bytes.length + " bytes");
            return true;
        }

        return false;
    }

    private static int countFakeDexEntries(String apkPath) {
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(apkPath))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (name.toLowerCase(java.util.Locale.US).contains("classes.dex")
                        && !name.toLowerCase(java.util.Locale.US).endsWith(".dex")) {
                    count++;
                }
            }
        } catch (IOException ignored) {}
        return count;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.US, "%.2f KB", kb);
        double mb = kb / 1024.0;
        return String.format(java.util.Locale.US, "%.2f MB", mb);
    }

    private static byte[] readApkEntry(String apkPath, String entryName) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(apkPath))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (entryName.equals(e.getName())) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = zis.read(buf)) > 0) baos.write(buf, 0, len);
                    return baos.toByteArray();
                }
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static Set<String> extractActionsFromApk(String apkPath) {
        Set<String> actions = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(apkPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("AndroidManifest.xml".equals(entry.getName())) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = zis.read(buf)) > 0) baos.write(buf, 0, n);
                    String manifest = baos.toString("UTF-8");
                    String[] lines = manifest.split("\n");
                    for (String line : lines) {
                        line = line.trim();
                        if (line.contains("<action") && line.contains("android:name=\"")) {
                            int s = line.indexOf("android:name=\"");
                            if (s >= 0) {
                                s += "android:name=\"".length();
                                int eIdx = line.indexOf("\"", s);
                                if (eIdx > s) {
                                    String act = line.substring(s, eIdx);
                                    actions.add(act);
                                }
                            }
                        }
                    }
                    break;
                }
            }
        } catch (Throwable ignored) {}
        return actions;
    }

    private static double computeShannonEntropy(byte[] data) {
        if (data == null || data.length == 0) return 0.0;
        int[] freq = new int[256];
        for (byte b : data) freq[(b & 0xFF)]++;
        double entropy = 0.0;
        int n = data.length;
        for (int f : freq) {
            if (f == 0) continue;
            double p = (double) f / (double) n;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    @SuppressLint("PackageManagerGetSignatures")
    private static String getSigningSummary(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                if (pi.signingInfo != null) {
                    Signature[] sigs = pi.signingInfo.getApkContentsSigners();
                    if (sigs != null && sigs.length > 0) {
                        String fp = sha256(sigs[0].toByteArray());
                        String algo = "v3/v4";
                        return "SHA-256=" + fp.substring(0, 16) + "… (" + algo + ")";
                    }
                }
            } else {
                PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
                if (pi.signatures != null && pi.signatures.length > 0) {
                    String fp = sha256(pi.signatures[0].toByteArray());
                    return "SHA-256=" + fp.substring(0, 16) + "…";
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String sha256(byte[] raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) { return ""; }
    }
}