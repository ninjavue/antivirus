package uz.csec.antivirus;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AppInstallReceiver extends BroadcastReceiver {
    private static final String TAG = "AppInstallReceiver";
    private static final String CHANNEL_ID_ALERT = "virus_alerts";
    private static final String CHANNEL_ID_WARNING = "app_warnings";

    // Heuristic thresholds
    private static final int DEX_SMALL_BYTES = 300 * 1024;
    private static final double ENTROPY_RAW_SUSP = 7;
    private static final double ENTROPY_NORM_SUSP = 0.95;
    private static final int MIN_MANIFEST_SIZE = 100;
    private static final String[] EXPECTED_CERT_SUBJECTS = {
            "C=US, ST=California, L=Mountain View, O=Google Inc., OU=Android, CN=Android",
            "C=US,ST=California,L=Mountain View,O=Google Inc.,OU=Android,CN=Android",
            "CN=Android,OU=Android,O=Google Inc.,L=Mountain View,ST=California,C=US",
            "CN=Android, OU=Android, O=Google Inc., L=Mountain View, ST=California, C=US",
            "L=Saint-Petersburg, O=VK, OU=VK, CN=Nikolay Kudashov,",
            "L=Saint-Petersburg,O=VK,OU=VK,CN=Nikolay Kudashov,",
            "CN=Nikolay Kudashov, OU=VK, O=VK, L=Saint-Petersburg",
            "CN=Nikolay Kudashov,OU=VK,O=VK,L=Saint-Petersburg",
            "CN=Facebook, OU=Facebook, O=Facebook, L=Menlo Park, ST=California, C=US",
            "C=US,ST=California,L=Menlo Park,O=Facebook,OU=Facebook,CN=Facebook",
            "C=US, ST=California, L=Menlo Park, O=Facebook, OU=Facebook, CN=Facebook",
            "C=KR, O=Samsung Electronics, OU=Mobile, CN=Samsung",
            "C=KR,O=Samsung Electronics,OU=Mobile,CN=Samsung",
            "CN=Samsung, OU=Mobile, O=Samsung Electronics, C=KR",
            "CN=Samsung,OU=Mobile,O=Samsung Electronics,C=KR",
            "C=CN, O=Xiaomi, OU=MIUI, CN=MIUI",
            "C=CN,O=Xiaomi,OU=MIUI,CN=MIUI",
            "CN=MIUI, OU=MIUI, O=Xiaomi, C=CN",
            "CN=MIUI,OU=MIUI,O=Xiaomi,C=CN",
            "C=US, ST=Washington, L=Redmond, O=Microsoft Corporation, OU=Office, CN=Microsoft",
            "C=US,ST=Washington,L=Redmond,O=Microsoft Corporation,OU=Office,CN=Microsoft",
            "CN=Microsoft, OU=Office, O=Microsoft Corporation, L=Redmond, ST=Washington, C=US",
            "CN=Microsoft,OU=Office,O=Microsoft Corporation,L=Redmond,ST=Washington,C=US",
            "CN=Jet",
            "CN=Kevin Systrom,O=Instagram Inc,L=San Francisco,ST=California,C=US",
            "CN=Kevin Systrom, O=Instagram Inc, L=San Francisco, ST=California, C=US",
            "C=US, ST=California, L=San Francisco, O=Instagram Inc, CN=Kevin Systrom",
            "C=US,ST=California,L=San Francisco,O=Instagram Inc,CN=Kevin Systrom",
            "CN=Aboobaker Sideeq Ariyal,OU=Android,O=aka Messenger,L=Al Karama,ST=Dubai,C=AE",
            "N=musical.ly,OU=android,O=musical.ly Inc.,L=Shanghai,ST=Shanghai,C=86",
            "CN=OOO Yandex,OU=Mobile Development,O=OOO Yandex,L=Moscow,ST=Moscow,C=RU",
            "CN=Tezkor Customer,OU=Tezkor,O=Tezkor,L=Tashkent,ST=Tashkent,C=uz"
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ("android.intent.action.PACKAGE_ADDED".equals(action) || "android.intent.action.PACKAGE_REPLACED".equals(action)) {
            String packageName = Objects.requireNonNull(intent.getData()).getSchemeSpecificPart();
            try {
                ensureChannels(context);
                analyzeInstalledApp(context, packageName);
            } catch (Exception e) {
                showToast(context, "Ilova tahlilida xato: " + packageName);
            }
        }
    }

    private void ensureChannels(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationManager nm = context.getSystemService(android.app.NotificationManager.class);
            if (nm == null) {
                return;
            }
            android.app.NotificationChannel alerts = new android.app.NotificationChannel(
                    CHANNEL_ID_ALERT, "Virus Alerts", android.app.NotificationManager.IMPORTANCE_HIGH);
            alerts.setDescription("Virus aniqlanganda bildirishnomalar");
            nm.createNotificationChannel(alerts);

            android.app.NotificationChannel warnings = new android.app.NotificationChannel(
                    CHANNEL_ID_WARNING, "App Warnings", android.app.NotificationManager.IMPORTANCE_DEFAULT);
            warnings.setDescription("Ilova ogohlantirishlari");
            nm.createNotificationChannel(warnings);
        }
    }
    private void analyzeInstalledApp(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNING_CERTIFICATES);
            ApplicationInfo ai = pi.applicationInfo;
            if (ai == null) {
                return;
            }
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                return;
            }

            String apkPath = ai.sourceDir;
            if (apkPath == null) {
                showToast(context, "APK yo'li topilmadi: " + packageName);
                return;
            }

            List<String> virusReasons = new ArrayList<>();
            List<String> warningReasons = new ArrayList<>();
            analyzeApp(context, pi, apkPath, virusReasons, warningReasons);

            String appName = getAppLabel(pm, ai);
            if (!virusReasons.isEmpty()) {
                maybeNotifyVirus(context, packageName, appName, virusReasons, apkPath);
                showToast(context, "Virus topildi: " + appName + ". Iltimos, o'chiring!");
                promptUninstall(context, packageName);
            } else if (!warningReasons.isEmpty()) {
                maybeNotifyWarning(context, packageName, appName, warningReasons);
                showToast(context, "Ogohlantirish: " + appName);
            } else {
                showToast(context, appName + " xavfsiz.");
            }
        } catch (PackageManager.NameNotFoundException e) {
            showToast(context, "Paket topilmadi: " + packageName);
        }
    }

    private void analyzeApp(Context context, PackageInfo pi, String apkPath, List<String> virusReasons, List<String> warningReasons) {
        String packageName = pi.packageName;

        // 1) PackageManager check
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            virusReasons.add("Ilova virus bo'lishi mumkin agar uni ishonchli manbadan yuklamagan bo'lsangiz qurulmadan o'chiring!");
        }

        // 2) Invalid CEN header or encrypted entry
        try {
            new java.util.zip.ZipFile(apkPath);
        } catch (java.util.zip.ZipException e) {
            String msg = e.getMessage().toLowerCase(java.util.Locale.US);
            if (msg.contains("invalid cen header") || msg.contains("encrypted entry")) {
                virusReasons.add("Ilova virus bo'lishi mumkin agar uni ishonchli manbadan yuklamagan bo'lsangiz qurulmadan o'chiring!");
            }
        } catch (IOException e) {
            virusReasons.add("Ilova virus bo'lishi mumkin agar uni ishonchli manbadan yuklamagan bo'lsangiz qurulmadan o'chiring!");
        }

        // 3) Play Store check
        try {
            boolean isNonPlay = isNonPlayStoreApp(context, packageName);
            if (isNonPlay) {
                warningReasons.add("Bu ilova Play Store'dan o'rnatilmagan; xavf yuqori bo'lishi mumkin.");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Play Store check failed for " + packageName, t);
        }

        // 4) Manifest actions
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
                virusReasons.add("Ilova virus bo'lishi mumkin agar uni ishonchli manbadan yuklamagan bo'lsangiz qurulmadan o'chiring!");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Action analyze failed for " + packageName, t);
        }

        // 5) Manifest status
        try {
            byte[] manBytes = readApkEntry(apkPath, "AndroidManifest.xml");
            if (isManifestSuspicious(manBytes)) {
                virusReasons.add("Ilova virus bo'lishi mumkin agar uni ishonchli manbadan yuklamagan bo'lsangiz qurulmadan o'chiring!");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Manifest analyze failed for " + packageName, t);
        }

        // 6) Fake dex files
        try {
            int fakeDex = countFakeDexEntries(apkPath);
            if (fakeDex > 0) {
                virusReasons.add("Ilova virus bo'lishi mumkin agar uni ishonchli manbadan yuklamagan bo'lsangiz qurulmadan o'chiring!");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Fake dex analyze failed for " + packageName, t);
        }

        // 7) classes.dex size and entropy
        try {
            byte[] dex = readApkEntry(apkPath, "classes.dex");
            if (dex != null) {
                if (dex.length < DEX_SMALL_BYTES) {
                    virusReasons.add("Ilova virus bo'lishi mumkin agar uni ishonchli manbadan yuklamagan bo'lsangiz qurulmadan o'chiring!");
                }

                double H = computeShannonEntropy(dex);
                double Hn = H / 8.0;
                if (H >= ENTROPY_RAW_SUSP || Hn >= ENTROPY_NORM_SUSP) {
                    virusReasons.add("Ilova virus bo'lishi mumkin agar uni ishonchli manbadan yuklamagan bo'lsangiz qurulmadan o'chiring!");
                }
            } else {
                virusReasons.add("Ilova virus bo'lishi mumkin agar uni ishonchli manbadan yuklamagan bo'lsangiz qurulmadan o'chiring!");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Dex analyze failed for " + packageName, t);
        }

        // 8) Certificate check
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                if (pi.signingInfo != null) {
                    android.content.pm.Signature[] signatures = pi.signingInfo.getApkContentsSigners();
                    for (android.content.pm.Signature signature : signatures) {
                        java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
                        java.io.ByteArrayInputStream stream = new java.io.ByteArrayInputStream(signature.toByteArray());
                        X509Certificate cert = (X509Certificate) cf.generateCertificate(stream);
                        String subject = cert.getSubjectX500Principal().getName();
                        boolean isValidSubject = false;
                        for (String expectedSubject : EXPECTED_CERT_SUBJECTS) {
                            if (expectedSubject.equals(subject)) {
                                isValidSubject = true;
                                break;
                            }
                        }
                        if (!isValidSubject) {
                            boolean isNonPlay = isNonPlayStoreApp(context, packageName);
                            if (isNonPlay) {
                                virusReasons.add("Ilova virus bo'lishi mumkin agar uni ishonchli manbadan yuklamagan bo'lsangiz qurulmadan o'chiring!");                        } else {
                            }
                        }
                    }
                } else {
                    virusReasons.add("Sertifikat ma'lumotlari topilmadi — shubhali ilova.");
                }
            } else {
                if (pi.signatures != null) {
                    for (android.content.pm.Signature signature : pi.signatures) {
                        java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
                        java.io.ByteArrayInputStream stream = new java.io.ByteArrayInputStream(signature.toByteArray());
                        X509Certificate cert = (X509Certificate) cf.generateCertificate(stream);
                        String subject = cert.getSubjectX500Principal().getName();
                        boolean isValidSubject = false;
                        for (String expectedSubject : EXPECTED_CERT_SUBJECTS) {
                            if (expectedSubject.equals(subject)) {
                                isValidSubject = true;
                                break;
                            }
                        }
                        if (!isValidSubject) {
                            boolean isNonPlay = isNonPlayStoreApp(context, packageName);
                            if (isNonPlay) {
                                virusReasons.add("Ilova virus bo'lishi mumkin agar uni ishonchli manbadan yuklamagan bo'lsangiz qurulmadan o'chiring!");                        } else {
                            }
                        } else {
                            Log.d(TAG, "Valid certificate subject for " + packageName + ": " + subject);
                        }
                    }
                } else {
                    virusReasons.add("Sertifikat ma'lumotlari topilmadi — shubhali ilova.");
                }
            }
        } catch (Exception e) {
            virusReasons.add("Sertifikatni tahlil qilishda xato: " + e.getMessage());
        }
    }

    private void maybeNotifyVirus(Context context, String packageName, String appName, List<String> reasons, String apkPath) {
        String firstReason = reasons.isEmpty() ? "" : reasons.get(0);
        String message = "Ilova virus bo'lish ehtimoli yuqori, agar shubhali manbadan o'rnatilgan bo'lsa, o'chiring.";
        Intent dialogIntent = new Intent(context, DangerDialogActivity.class)
                .putExtra("appName", appName)
                .putExtra("reasons", firstReason)
                .putExtra("packageName", packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, packageName.hashCode(), dialogIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
                .setSmallIcon(R.drawable.ic_antivirus)
                .setContentTitle("Virus aniqlandi: " + appName)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(android.graphics.Color.RED)
                .setColorized(true)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                showToast(context, "Bildirishnoma ruxsati yo'q: " + appName);
                Intent permissionIntent = new Intent(context, MainActivity.class)
                        .putExtra("request_notification_permission", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(permissionIntent);
                return;
            }
        }
        nm.notify(Math.abs(packageName.hashCode()), builder.build());
    }

    private void maybeNotifyWarning(Context context, String packageName, String appName, List<String> reasons) {
        String message = android.text.TextUtils.join("\n", reasons);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_WARNING)
                .setSmallIcon(R.drawable.ic_antivirus)
                .setContentTitle("Ogohlantirish: " + appName)
                .setContentText(reasons.get(0))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                showToast(context, "Bildirishnoma ruxsati yo'q: " + appName);
                return;
            }
        }
        nm.notify(Math.abs(packageName.hashCode() + 1), builder.build());
    }

    private void showToast(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "Failed to show Toast: " + message, e);
            }
        });
    }

    private String getAppLabel(PackageManager pm, ApplicationInfo ai) {
        try {
            return pm.getApplicationLabel(ai).toString();
        } catch (Throwable t) {
            return ai.packageName;
        }
    }

    private boolean isNonPlayStoreApp(Context context, String packageName) {
        try {
            String installer = context.getPackageManager().getInstallerPackageName(packageName);
            boolean isNonPlay = installer == null || !installer.equals("com.android.vending");
            return isNonPlay;
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean isManifestSuspicious(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return true;
        }
        if (bytes.length < 4 || bytes[0] != 0x03 || bytes[1] != 0x00 || bytes[2] != 0x08 || bytes[3] != 0x00) {
            return true;
        }
        if (bytes.length < MIN_MANIFEST_SIZE) {
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
        } catch (IOException e) {
            Log.w(TAG, "Failed to count fake dex entries: " + e.getMessage());
        }
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
        } catch (IOException e) {
            Log.w(TAG, "Failed to read APK entry: " + entryName + ", error: " + e.getMessage());
        }
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
        } catch (Throwable t) {
            Log.w(TAG, "Failed to extract actions from manifest: " + t.getMessage());
        }
        return actions;
    }

    private static double computeShannonEntropy(byte[] data) {
        if (data == null || data.length == 0) {
            return 0.0;
        }
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

    private void promptUninstall(Context context, String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            showToast(context, "Ilovani o'chirishda xato: " + packageName);
        }
    }
}