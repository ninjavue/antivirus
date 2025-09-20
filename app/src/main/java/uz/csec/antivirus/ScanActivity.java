package uz.csec.antivirus;

import android.annotation.SuppressLint;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Build;
import android.os.VibrationEffect;
import android.view.Window;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Intent;
import android.content.res.AssetManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import android.os.Environment;
import android.provider.Settings;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import android.app.AlertDialog;
import org.json.JSONArray;
import org.json.JSONObject;
import android.view.LayoutInflater;
import android.content.pm.ApplicationInfo;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import android.graphics.Color;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import android.util.Log;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import androidx.core.app.ActivityCompat;
import android.Manifest;

public class ScanActivity extends AppCompatActivity {

    private static ScanActivity instance;

    private static final int DEX_SMALL_BYTES = 300 * 1024;
    private static final double ENTROPY_RAW_SUSP = 7;
    private static final double ENTROPY_NORM_SUSP = 0.95;
    private static final int MIN_MANIFEST_SIZE = 100;
    private static final String[] EXPECTED_CERT_SUBJECTS = {
            "C=US, ST=California, L=Mountain View, O=Google Inc., OU=Android, CN=Android",
            "C=US,ST=California,L=Mountain View,O=Google Inc.,OU=Android,CN=Android",
            "CN=Android,OU=Android,O=Google Inc.,L=Mountain View,ST=California,C=US",
            "CN=Android, OU=Android, O=Google Inc., L=Mountain View, ST=California, C=US",
            "L=Saint-Petersburg, O=VK, OU=VK, CN=Nikolay Kudashov",
            "L=Saint-Petersburg,O=VK,OU=VK,CN=Nikolay Kudashov",
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

    public static String getAppNameFromApk(Context context, String apkPath) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(apkPath, 0);
            if (packageInfo != null) {
                ApplicationInfo appInfo = packageInfo.applicationInfo;
                appInfo.sourceDir = apkPath;
                appInfo.publicSourceDir = apkPath;
                return (String) pm.getApplicationLabel(appInfo);
            }
        } catch (Exception e) {
            Log.e("ScanActivity", "Error getting app name for " + apkPath, e);
        }
        return new File(apkPath).getName();
    }

    public static boolean isNonPlayStoreApp(Context context, String apkPath) {
        try {
            if (apkPath == null) return true;

            PackageManager pm = context.getPackageManager();
            PackageInfo pkgInfo = pm.getPackageArchiveInfo(apkPath, 0);
            if (pkgInfo == null || pkgInfo.packageName == null) {
                return true;
            }

            String packageName = pkgInfo.packageName;

            if (context != null && packageName.equals(context.getPackageName())) {
                return false;
            }

            String installerPackageName = null;
            try {
                installerPackageName = pm.getInstallerPackageName(packageName);
            } catch (Throwable ignored) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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

            return !"com.android.vending".equals(installerPackageName);
        } catch (Exception e) {
            Log.e("ScanActivity", "Error checking Play Store status for " + apkPath, e);
            return true;
        }
    }

    private static int countFakeDexEntries(String apkPath) {
        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(apkPath))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (name.toLowerCase(Locale.US).contains("classes.dex")
                        && !name.toLowerCase(Locale.US).endsWith(".dex")) {
                    count++;
                }
            }
        } catch (IOException e) {
            Log.w("ScanActivity", "Failed to count fake dex entries for " + apkPath + ": " + e.getMessage());
        }
        return count;
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
            Log.w("ScanActivity", "Failed to read APK entry " + entryName + " for " + apkPath + ": " + e.getMessage());
        }
        return null;
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
            double p = (double) f / n;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    private ArrayList<String> getAllFiles(String dirPath) {
        ArrayList<String> fileList = new ArrayList<>();
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.canRead()) {
            return fileList;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    if (file.isDirectory()) {
                        fileList.addAll(getAllFiles(file.getAbsolutePath()));
                    } else if (file.getName().toLowerCase(Locale.US).endsWith(".apk")) {
                        fileList.add(file.getAbsolutePath());
                    }
                } catch (Exception e) {
                    Log.e("ScanActivity", "Error processing file/dir " + file.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        } else {
            Log.w("ScanActivity", "No files found in directory: " + dirPath);
        }
        return fileList;
    }

    private ArrayList<String> getAllFilesAndInstalledApks(String dirPath) {
        ArrayList<String> fileList = new ArrayList<>();
        String selfApkPath = getApplicationInfo().sourceDir;

        List<ApplicationInfo> apps = getPackageManager().getInstalledApplications(0);
        for (ApplicationInfo app : apps) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                String apkPath = app.sourceDir;
                if (apkPath != null && !fileList.contains(apkPath) && !apkPath.equals(selfApkPath)) {
                    fileList.add(apkPath);
                }
            }
        }

        // Scan common directories for uninstalled APKs
        String[] commonDirs = {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath(),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath(),
                Environment.getExternalStorageDirectory().getAbsolutePath(),
                "/sdcard/",
                "/storage/self/primary"
        };

        for (String dir : commonDirs) {
            try {
                ArrayList<String> dirFiles = getAllFiles(dir);
                for (String filePath : dirFiles) {
                    if (!fileList.contains(filePath) && !filePath.equals(selfApkPath)) {
                        fileList.add(filePath);
                        Log.d("ScanActivity", "Added uninstalled APK from " + dir + ": " + filePath);
                    }
                }
            } catch (Exception e) {
                Log.e("ScanActivity", "Error scanning directory " + dir + ": " + e.getMessage());
            }
        }

        // Scan additional storage locations
        File[] externalFilesDirs = getExternalFilesDirs(null);
        for (File dir : externalFilesDirs) {
            if (dir != null) {
                try {
                    ArrayList<String> dirFiles = getAllFiles(dir.getAbsolutePath());
                    for (String filePath : dirFiles) {
                        if (!fileList.contains(filePath) && !filePath.equals(selfApkPath)) {
                            fileList.add(filePath);
                        }
                    }
                } catch (Exception e) {
                    Log.e("ScanActivity", "Error scanning external dir " + dir.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        }

        return fileList;
    }

    private void showVirusListDialog(JSONArray virusArray) {
        if (virusArray.length() == 0) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_virus_list, null);
        TextView tvVirusCount = dialogView.findViewById(R.id.tvVirusCount);
        LinearLayout llVirusList = dialogView.findViewById(R.id.llVirusList);
        Button btnDeleteAll = dialogView.findViewById(R.id.btnDeleteAll);
        Button btnClose = dialogView.findViewById(R.id.btnClose);

        tvVirusCount.setText(virusArray.length() + " ta virus aniqlandi");

        // Add each virus to the list
        for (int i = 0; i < virusArray.length(); i++) {
            try {
                JSONObject virusObj = virusArray.getJSONObject(i);
                String filePath = virusObj.getString("path");
                File file = new File(filePath);
                String fileName = filePath.endsWith(".apk") ? getAppNameFromApk(this, filePath) : file.getName();

                View virusItemView = LayoutInflater.from(this).inflate(R.layout.item_virus, null);
                TextView tvVirusName = virusItemView.findViewById(R.id.tvVirusName);
                TextView tvVirusPath = virusItemView.findViewById(R.id.tvVirusPath);
                Button btnDeleteVirus = virusItemView.findViewById(R.id.btnDeleteVirus);

                tvVirusName.setText(fileName);
                tvVirusPath.setText("Path: " + filePath);

                btnDeleteVirus.setOnClickListener(v -> {
                    boolean deleted = file.delete();
                    if (deleted) {
                        llVirusList.removeView(virusItemView);
                        // Update count
                        int remainingCount = llVirusList.getChildCount();
                        tvVirusCount.setText(remainingCount + " ta virus aniqlandi");
                        if (remainingCount == 0) {
                            // Close dialog if no viruses left
                            ((AlertDialog) ((View) v.getParent().getParent().getParent().getParent()).getTag()).dismiss();
                        }
                    } else {
                        showSimpleDialog("Xatolik", "Faylni o'chirib bo'lmadi.");
                    }
                });

                llVirusList.addView(virusItemView);
            } catch (Exception e) {
                Log.e("ScanActivity", "Error processing virus item", e);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnDeleteAll.setOnClickListener(v -> {
            // Delete all viruses
            for (int i = 0; i < virusArray.length(); i++) {
                try {
                    JSONObject virusObj = virusArray.getJSONObject(i);
                    String filePath = virusObj.getString("path");
                    File file = new File(filePath);
                    file.delete();
                } catch (Exception e) {
                    Log.e("ScanActivity", "Error deleting virus file", e);
                }
            }
            dialog.dismiss();
            showSimpleDialog("Muvaffaqiyat", "Barcha viruslar o'chirildi.");
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showSimpleDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    @SuppressLint("NotificationPermission")
    private void showVirusNotification(String filePath) {
        File file = new File(filePath);
        String fileName = filePath.endsWith(".apk") ? getAppNameFromApk(this, filePath) : file.getName();
        String channelId = "virus_detected_channel";
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Virus Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Virus detected notifications");
            notificationManager.createNotificationChannel(channel);
        }
        Intent intent = new Intent(this, ScanActivity.class);
        intent.putExtra("virus_file_path", filePath);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_antivirus)
                .setContentTitle("Virus detected: " + fileName)
                .setContentText("Path: " + filePath)
                .setStyle(new NotificationCompat.BigTextStyle().bigText("Virus found in:\n" + filePath))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        notificationManager.notify(filePath.hashCode(), builder.build());
    }

    private int getProgressColorByVirusCount(int virusCount) {
        if (virusCount == 0) return Color.parseColor("#4CAF50");
        if (virusCount == 1) return Color.parseColor("#FF9800");
        if (virusCount == 2) return Color.parseColor("#FF7043");
        int alpha = Math.min(255, 100 + virusCount * 50);
        return Color.argb(alpha, 244, 67, 54);
    }

    private boolean isTrustedCertificate(String apkPath) {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES);
            if (packageInfo != null && packageInfo.signatures != null) {
                for (Signature signature : packageInfo.signatures) {
                    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                    ByteArrayInputStream input = new ByteArrayInputStream(signature.toByteArray());
                    X509Certificate cert = (X509Certificate) certFactory.generateCertificate(input);
                    String subjectDN = cert.getSubjectDN().getName();
                    for (String expectedSubject : EXPECTED_CERT_SUBJECTS) {
                        if (subjectDN.equals(expectedSubject)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ScanActivity", "Error checking certificate for " + apkPath, e);
        }
        return false;
    }

    public static boolean checkVirusTotal(String md5) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("http://172.174.245.45:4000/api/scan/file/" + md5)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return false;
            }
            String body = response.body().string();
            JSONObject json = new JSONObject(body);
            boolean success = json.optBoolean("success", false);
            if (success) {
                String status = json.optString("status", "");
                return "virus".equalsIgnoreCase(status);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getMd5(String filePath) {
        try (InputStream is = new FileInputStream(filePath)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] md5sum = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : md5sum) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(R.layout.activity_scan);
        
        // Initialize DexCallGraph model
        DexCallGraph.init(this);
        if (DexCallGraph.isModelInitialized()) {
            Log.d("ScanActivity", "DexCallGraph model muvaffaqiyatli yuklandi!");
        } else {
            Log.e("ScanActivity", "DexCallGraph model yuklanmadi!");
        }

        Set<String> detectedPaths = new HashSet<>();
        Window window = getWindow();
        window.setStatusBarColor(ContextCompat.getColor(this, R.color.status_bar_scan_gradient));

        ScanProgressView progressView = findViewById(R.id.scanProgressView);
        Button btnQuickScan = findViewById(R.id.btnQuickScan);
        Button btnAiScan = findViewById(R.id.btnAiScan);
        TextView tvScanStatus = findViewById(R.id.tvScanStatus);
        LinearLayout btnBack = findViewById(R.id.back);
        NativeLib nativeLib = new NativeLib();
        ImageView lottieScan = findViewById(R.id.lottieScan);
        lottieScan.setVisibility(View.VISIBLE);

        // Get AssetManager instance
        AssetManager assetManager = getAssets();

        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(ScanActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
            overridePendingTransition(0, 0);
        });

        CardView cardFullScan = findViewById(R.id.cardFullScan);
        cardFullScan.setOnClickListener(v -> {
            startActivity(new Intent(ScanActivity.this, VirusActivity.class));
            overridePendingTransition(0, 0);
        });

        // Handle virus file path from notification (if any)
        String virusFilePath = getIntent().getStringExtra("virus_file_path");
        if (virusFilePath != null) {
            // Create a single-item array for the virus list dialog
            JSONArray singleVirusArray = new JSONArray();
            try {
                JSONObject virusObj = new JSONObject();
                virusObj.put("path", virusFilePath);
                virusObj.put("hash", "");
                singleVirusArray.put(virusObj);
                showVirusListDialog(singleVirusArray);
            } catch (Exception e) {
                Log.e("ScanActivity", "Error creating virus array", e);
            }
        }

        btnQuickScan.setOnClickListener(v -> {
            if (!hasStoragePermission()) {
                showSimpleDialog("Permission Required", "Please grant storage access to scan files.");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } else {
                    ActivityCompat.requestPermissions(ScanActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
                }
                return;
            }

            btnQuickScan.setEnabled(false);
            btnQuickScan.setText(getString(R.string.checking) + "...");
            progressView.animateProgress(0f);
            tvScanStatus.setText("0% " + getString(R.string.scannery));

            new Thread(() -> {
                String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
                ArrayList<String> allFiles = getAllFilesAndInstalledApks(rootPath);
                
                // Limit number of files to prevent memory issues but allow real analysis
                int maxFiles = 20; // Process 20 files with real analysis
                if (allFiles.size() > maxFiles) {
                    allFiles = new ArrayList<>(allFiles.subList(0, maxFiles));
                }
                
                String[] fileArr = allFiles.toArray(new String[0]);
                int totalFiles = fileArr.length;
                int batchSize = 1;
                int totalBatches = (int) Math.ceil((double) totalFiles / batchSize);
                JSONArray virusArr = new JSONArray();
                AtomicInteger checkedBatches = new AtomicInteger(0);
                AntivirusDatabase db = new AntivirusDatabase(ScanActivity.this);
                String detectedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

                int THREAD_COUNT = 1; // Reduce thread count to save memory
                ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
                for (int batch = 0; batch < totalBatches; batch++) {
                    int start = batch * batchSize;
                    int end = Math.min(start + batchSize, totalFiles);
                    String[] batchFiles = Arrays.copyOfRange(fileArr, start, end);
                    executor.execute(() -> {
                        for (String apkPath : batchFiles) {
                            if (!apkPath.toLowerCase(Locale.US).contains("com.google.android")) {
                                // ML Model virus detection
                                boolean mlVirusDetected = false;
                                try {
                                    Log.d("ILova ", apkPath);
                                    mlVirusDetected = DexCallGraph.runAnalysis(new File(apkPath));
                                    if (mlVirusDetected) {
                                        Log.d("ScanActivity", "ML Model detected virus in: " + apkPath);
                                    }
                                } catch (Exception e) {
                                    Log.e("ScanActivity", "Dex analysis failed for ", e);
                                }

                                boolean detected = false;

                                String appName = getAppNameFromApk(ScanActivity.this, apkPath);

                                boolean isInstalled = false;
                                try {
                                    PackageInfo pkgInfo = getPackageManager().getPackageArchiveInfo(apkPath, 0);
                                    if (pkgInfo != null && pkgInfo.packageName != null) {
                                        try {
                                            getPackageManager().getPackageInfo(pkgInfo.packageName, 0);
                                            isInstalled = true;
                                        } catch (PackageManager.NameNotFoundException e) {
                                            isInstalled = false;
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.w("ScanActivity", "Error checking installation status for " + apkPath + ": " + e.getMessage());
                                }

                                // Show warning for non-Play Store installed APKs (but don't mark as virus)
                                if (isInstalled && isNonPlayStoreApp(ScanActivity.this, apkPath)) {
                                    try {
                                        String channelId = "non_playstore_apps";
                                        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            NotificationChannel channel = new NotificationChannel(
                                                    channelId, "Non PlayStore Apps", NotificationManager.IMPORTANCE_DEFAULT);
                                            nm.createNotificationChannel(channel);
                                        }

                                        // App info ga yuboradigan intent
                                        PackageInfo pkgInfo = getPackageManager().getPackageArchiveInfo(apkPath, 0);
                                        if (pkgInfo != null && pkgInfo.packageName != null) {
                                            Intent settingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                            settingsIntent.setData(android.net.Uri.parse("package:" + pkgInfo.packageName));
                                            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                            PendingIntent pendingIntent = PendingIntent.getActivity(
                                                    ScanActivity.this,
                                                    0,
                                                    settingsIntent,
                                                    PendingIntent.FLAG_IMMUTABLE
                                            );

                                            NotificationCompat.Builder builder = new NotificationCompat.Builder(ScanActivity.this, channelId)
                                                    .setSmallIcon(android.R.drawable.stat_notify_error)
                                                    .setContentTitle("Ogohlantirish")
                                                    .setContentText(appName + " Play Storedan o'rnatilmagan")
                                                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                                    .setAutoCancel(true)
                                                    .setContentIntent(pendingIntent);

                                            nm.notify((int) System.currentTimeMillis(), builder.build());
                                        }
                                    } catch (Exception e) {
                                        Log.e("ScanActivity", "Error showing notification for " + apkPath, e);
                                    }
                                }


                                // 1. Fake dex file check
                                try {
                                    int fakeDex = countFakeDexEntries(apkPath);
                                        if (fakeDex > 0) {
                                            if (!detectedPaths.contains(apkPath)) {
                                                detectedPaths.add(apkPath);
                                                try {
                                                    JSONObject virusObj = new JSONObject();
                                                    virusObj.put("path", apkPath);
                                                    virusObj.put("hash", "");
                                                    synchronized (virusArr) {
                                                        virusArr.put(virusObj);
                                                    }
                                                    Log.d("ScanActivity", "Detected fake dex in " + apkPath);
                                                } catch (Exception e) {
                                                    Log.e("ScanActivity", "Error creating virus object for fake dex", e);
                                                }
                                            }
                                            detected = true;
                                        }
                                } catch (Throwable t) {
                                    Log.w("ScanActivity", "Fake dex analyze failed for " + apkPath, t);
                                }

                                // 2. Classes.dex entropy check
                                try {
                                    byte[] dex = readApkEntry(apkPath, "classes.dex");
                                    if (dex != null) {
                                        if (dex.length < DEX_SMALL_BYTES) {
                                            if (!detectedPaths.contains(apkPath)) {
                                                detectedPaths.add(apkPath);
                                                try {
                                                    JSONObject virusObj = new JSONObject();
                                                    virusObj.put("path", apkPath);
                                                    virusObj.put("hash", "");
                                                    synchronized (virusArr) {
                                                        virusArr.put(virusObj);
                                                    }
                                                    Log.d("ScanActivity", "Detected small dex size in " + apkPath);
                                                } catch (Exception e) {
                                                    Log.e("ScanActivity", "Error creating virus object for small dex", e);
                                                }
                                            }
                                            detected = true;
                                        }
                                        double H = computeShannonEntropy(dex);
                                        double Hn = H / 8.0;
                                        if (H >= ENTROPY_RAW_SUSP || Hn >= ENTROPY_NORM_SUSP) {
                                            if (!detectedPaths.contains(apkPath)) {
                                                detectedPaths.add(apkPath);
                                                try {
                                                    JSONObject virusObj = new JSONObject();
                                                    virusObj.put("path", apkPath);
                                                    virusObj.put("hash", "");
                                                    synchronized (virusArr) {
                                                        virusArr.put(virusObj);
                                                    }
                                                    Log.d("ScanActivity", "Detected high entropy in " + apkPath);
                                                } catch (Exception e) {
                                                    Log.e("ScanActivity", "Error creating virus object for high entropy", e);
                                                }
                                            }
                                            detected = true;
                                        }
                                    } else {
                                        if (!detectedPaths.contains(apkPath)) {
                                            detectedPaths.add(apkPath);
                                            try {
                                                JSONObject virusObj = new JSONObject();
                                                virusObj.put("path", apkPath);
                                                virusObj.put("hash", "");
                                                synchronized (virusArr) {
                                                    virusArr.put(virusObj);
                                                }
                                                Log.d("ScanActivity", "No classes.dex found in " + apkPath);
                                            } catch (Exception e) {
                                                Log.e("ScanActivity", "Error creating virus object for missing dex", e);
                                            }
                                        }
                                        detected = true;
                                    }
                                } catch (Throwable t) {
                                    Log.w("ScanActivity", "Dex analyze failed for " + apkPath, t);
                                }

                                // 3. ML Model virus detection
                                if (!detected && mlVirusDetected) {
                                    if (!detectedPaths.contains(apkPath)) {
                                        detectedPaths.add(apkPath);
                                        try {
                                            JSONObject virusObj = new JSONObject();
                                            virusObj.put("path", apkPath);
                                            virusObj.put("hash", "");
                                            virusObj.put("detection_method", "ML Model");
                                            synchronized (virusArr) {
                                                virusArr.put(virusObj);
                                            }
                                            Log.d("ScanActivity", "ML Model detected virus in " + apkPath);
                                        } catch (Exception e) {
                                            Log.e("ScanActivity", "Error creating virus object for ML detection", e);
                                        }
                                    }
                                    detected = true;
                                }

                                // 4. Certificate and VirusTotal check
                                if (!detected) {
                                    try {
                                        if (!isTrustedCertificate(apkPath)) {
                                            String md5 = getMd5(apkPath);
                                            if (!md5.isEmpty()) {
//                                                boolean isVirus = checkVirusTotal(md5);
                                                boolean isVirus = false;
                                                if (isVirus) {
                                                    if (!detectedPaths.contains(apkPath)) {
                                                        detectedPaths.add(apkPath);
                                                        try {
                                                            JSONObject virusObj = new JSONObject();
                                                            virusObj.put("path", apkPath);
                                                            virusObj.put("hash", md5);
                                                            virusObj.put("detection_method", "VirusTotal");
                                                            synchronized (virusArr) {
                                                                virusArr.put(virusObj);
                                                            }
                                                            Log.d("ScanActivity", "VirusTotal detected virus in " + apkPath);
                                                        } catch (Exception e) {
                                                            Log.e("ScanActivity", "Error creating virus object for VirusTotal detection", e);
                                                        }
                                                    }
                                                    detected = true;
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        Log.e("ScanActivity", "Certificate/VirusTotal check failed for " + apkPath, e);
                                    }
                                }

                                // Native scan
                                try {
                                    String batchResult = nativeLib.quickScanFiles(batchFiles, assetManager);
                                    synchronized (virusArr) {
                                        JSONArray arr = new JSONArray(batchResult);
                                        for (int i = 0; i < arr.length(); i++) {
                                            virusArr.put(arr.getJSONObject(i));
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e("ScanActivity", "Native scan failed for batch", e);
                                }
                                
                                // Force garbage collection to free memory
                                System.gc();
                                
                                // Add delay to prevent overwhelming the system
                                try {
                                    Thread.sleep(200); // Increased delay for real analysis
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }

                        int done = checkedBatches.incrementAndGet();
                        float progress = (float) (done * batchSize) / totalFiles;
                        int color = getProgressColorByVirusCount(virusArr.length());
                        runOnUiThread(() -> {
                            int percent = Math.round(Math.min(progress, 1f) * 100);
                            tvScanStatus.setText(percent + "% " + getString(R.string.scannery));
                            progressView.animateProgress(Math.min(progress, 1f));
                            progressView.setProgressColor(color);
                        });
                    });
                }

                executor.shutdown();
                while (!executor.isTerminated()) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {}
                }

                runOnUiThread(() -> {
                    btnQuickScan.setEnabled(true);
                    btnQuickScan.setText(getString(R.string.check));
                    progressView.animateProgress(1f);

                    int finalColor = virusArr.length() > 0 ? Color.parseColor("#F44336") : Color.parseColor("#4CAF50");
                    progressView.setProgressColor(finalColor);

                    if (virusArr.length() > 0) {
                        // Save all viruses to database
                        for (int i = 0; i < virusArr.length(); i++) {
                            try {
                                JSONObject obj = virusArr.getJSONObject(i);
                                String filePath = obj.getString("path");
                                String hash = obj.optString("hash", "");
                                File file = new File(filePath);
                                String fileName = filePath.endsWith(".apk") ? getAppNameFromApk(ScanActivity.this, filePath) : file.getName();
                                long fileSize = file.exists() ? file.length() : 0;
                                db.insertVirusFile(fileName, filePath, fileSize, detectedAt, hash);
                            } catch (Exception e) {
                                Log.e("ScanActivity", "Error processing virus entry", e);
                            }
                        }
                        
                        // Show single dialog with all viruses
                        showVirusListDialog(virusArr);
                        tvScanStatus.setText(getString(R.string.phone_unsafe));
                    } else {
                        tvScanStatus.setText(getString(R.string.phone_safe));
                    }
                });
            }).start();
        });

        // AI Scan button click handler - xuddi btnQuickScan kabi ishlaydi
        btnAiScan.setOnClickListener(v -> {
            if (!hasStoragePermission()) {
                showSimpleDialog("Permission Required", "Please grant storage access to scan files.");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } else {
                    ActivityCompat.requestPermissions(ScanActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
                }
                return;
            }

            btnAiScan.setEnabled(false);
            btnAiScan.setText(getString(R.string.checking) + "...");
            progressView.animateProgress(0f);
            tvScanStatus.setText("0% " + getString(R.string.scannery));

            new Thread(() -> {
                String rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
                ArrayList<String> allFiles = getAllFilesAndInstalledApks(rootPath);
                
                // Limit number of files to prevent memory issues but allow real analysis
                int maxFiles = 20; // Process 20 files with real analysis
                if (allFiles.size() > maxFiles) {
                    allFiles = new ArrayList<>(allFiles.subList(0, maxFiles));
                }
                
                String[] fileArr = allFiles.toArray(new String[0]);
                int totalFiles = fileArr.length;
                int batchSize = 1;
                int totalBatches = (int) Math.ceil((double) totalFiles / batchSize);
                JSONArray virusArr = new JSONArray();
                AtomicInteger checkedBatches = new AtomicInteger(0);
                AntivirusDatabase db = new AntivirusDatabase(ScanActivity.this);
                String detectedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

                int THREAD_COUNT = 1; // Reduce thread count to save memory
                ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
                for (int batch = 0; batch < totalBatches; batch++) {
                    int start = batch * batchSize;
                    int end = Math.min(start + batchSize, totalFiles);
                    String[] batchFiles = Arrays.copyOfRange(fileArr, start, end);
                    executor.execute(() -> {
                        for (String apkPath : batchFiles) {
                            if (!apkPath.toLowerCase(Locale.US).contains("com.google.android")) {
                                // ML Model virus detection
                                boolean mlVirusDetected = false;
                                try {
                                    Log.d("ILova ", apkPath);
                                    mlVirusDetected = DexCallGraph.runAnalysis(new File(apkPath));
                                    if (mlVirusDetected) {
                                        Log.d("ScanActivity", "ML Model detected virus in: " + apkPath);
                                    }
                                } catch (Exception e) {
                                    Log.e("ScanActivity", "Dex analysis failed for ", e);
                                }

                                if (mlVirusDetected) {
                                    try {
                                        JSONObject virusObj = new JSONObject();
                                        virusObj.put("path", apkPath);
                                        virusObj.put("hash", "");
                                        virusArr.put(virusObj);
                                        detectedPaths.add(apkPath);
                                    } catch (Exception e) {
                                        Log.e("ScanActivity", "Error creating virus object", e);
                                    }
                                }
                            }
                        }

                        int checked = checkedBatches.incrementAndGet();
                        float progress = (float) checked / totalBatches;
                        int color = virusArr.length() > 0 ? Color.parseColor("#F44336") : Color.parseColor("#4CAF50");

                        runOnUiThread(() -> {
                            int percent = Math.round(Math.min(progress, 1f) * 100);
                            tvScanStatus.setText(percent + "% " + getString(R.string.scannery));
                            progressView.animateProgress(Math.min(progress, 1f));
                            progressView.setProgressColor(color);
                        });
                    });
                }

                executor.shutdown();
                while (!executor.isTerminated()) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {}
                }

                runOnUiThread(() -> {
                    btnAiScan.setEnabled(true);
                    btnAiScan.setText(getString(R.string.ai_scan));
                    progressView.animateProgress(1f);

                    int finalColor = virusArr.length() > 0 ? Color.parseColor("#F44336") : Color.parseColor("#4CAF50");
                    progressView.setProgressColor(finalColor);

                    if (virusArr.length() > 0) {
                        // Save all viruses to database
                        for (int i = 0; i < virusArr.length(); i++) {
                            try {
                                JSONObject obj = virusArr.getJSONObject(i);
                                String filePath = obj.getString("path");
                                String hash = obj.optString("hash", "");
                                // Extract file name from path
                                String fileName = new File(filePath).getName();
                                long fileSize = new File(filePath).length();
                                db.insertVirusFile(fileName, filePath, fileSize, detectedAt, hash);
                            } catch (Exception e) {
                                Log.e("ScanActivity", "Error saving virus to database", e);
                            }
                        }
                        showVirusListDialog(virusArr);
                        tvScanStatus.setText(getString(R.string.phone_unsafe));
                    } else {
                        tvScanStatus.setText(getString(R.string.phone_safe));
                    }
                });
            }).start();
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        ScanProgressView progressView = findViewById(R.id.scanProgressView);
        TextView tvScanStatus = findViewById(R.id.tvScanStatus);
        AntivirusDatabase db = new AntivirusDatabase(this);

        int virusCount = db.getVirusCount();
        progressView.setProgressColor(virusCount > 0 ? Color.parseColor("#F44336") : Color.parseColor("#4CAF50"));
        tvScanStatus.setText(getString(virusCount > 0 ? R.string.phone_unsafe : R.string.phone_safe));
    }

    public static ScanActivity getInstance() {
        return instance;
    }
}