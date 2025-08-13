package uz.csec.antivirus;

import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Build;
import android.view.Window;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.Intent;
import android.widget.Toast;
import android.content.res.AssetManager;
import java.io.File;
import java.util.ArrayList;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.content.Intent;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.os.Vibrator;
import android.app.AlertDialog;
import android.content.DialogInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import android.view.LayoutInflater;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.util.List;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import android.graphics.Color;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import java.util.Arrays;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.database.Cursor;

public class ScanActivity extends AppCompatActivity {

    private ArrayList<String> getAllFiles(String dirPath) {
        ArrayList<String> fileList = new ArrayList<>();
        File dir = new File(dirPath);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    fileList.addAll(getAllFiles(file.getAbsolutePath()));
                } else {
                    fileList.add(file.getAbsolutePath());
                }
            }
        }
        return fileList;
    }

    private ArrayList<String> getAllFilesAndInstalledApks(String dirPath) {
        ArrayList<String> fileList = getAllFiles(dirPath);
        List<ApplicationInfo> apps = getPackageManager().getInstalledApplications(0);
        for (ApplicationInfo app : apps) {
            String apkPath = app.sourceDir;
            if (apkPath != null && !fileList.contains(apkPath)) {
                fileList.add(apkPath);
            }
        }
        return fileList;
    }

    private void showVirusDialog(String filePath) {
        File file = new File(filePath);
        String fileName = file.getName();
        String message = "Zararli dastur aniqlandi!\n\nFayl: " + fileName + "\nJoylashgan joyi: " + filePath;
        new AlertDialog.Builder(this)
            .setTitle("Zararli fayl aniqlandi!")
            .setMessage(message)
            .setPositiveButton("O'chirish", (dialog, which) -> {
                boolean deleted = file.delete();
                if (deleted) {
                    showSimpleDialog("File deleted", "The infected file was deleted.");
                } else {
                    showSimpleDialog("Delete failed", "Could not delete the file.");
                }
            })
            .setNegativeButton("Bekor qilish", null)
            .show();
    }

    private void showModernVirusDialog(String filePath) {
        File file = new File(filePath);
        String fileName = file.getName();
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_virus_found, null);
        TextView tvFile = dialogView.findViewById(R.id.tvVirusFile);
        TextView tvPath = dialogView.findViewById(R.id.tvVirusPath);
        Button btnDelete = dialogView.findViewById(R.id.btnDelete);
        Button btnClose = dialogView.findViewById(R.id.btnClose);
        tvFile.setText("Fayl: " + fileName);
        tvPath.setText("Fayl joylashgan joyi: " + filePath);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();
        btnDelete.setOnClickListener(v -> {
            boolean deleted = file.delete();
            dialog.dismiss();
            if (deleted) {
                showSimpleDialog("File deleted", "The infected file was deleted.");
            } else {
                showSimpleDialog("Delete failed", "Could not delete the file.");
            }
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
        String fileName = file.getName();
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

    private void showVirusAnimation() {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            View virusOverlay = findViewById(R.id.virusOverlay);
            if (virusOverlay == null) return;
            virusOverlay.setVisibility(View.VISIBLE);
            // Fade in animation
            AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
            fadeIn.setDuration(300);
            fadeIn.setFillAfter(true);
            virusOverlay.startAnimation(fadeIn);
            // Vibrate for effect
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(android.os.VibrationEffect.createOneShot(400, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else if (v != null) {
                v.vibrate(400);
            }
            // After a short delay, fade out
            virusOverlay.postDelayed(() -> {
                AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
                fadeOut.setDuration(500);
                fadeOut.setFillAfter(true);
                virusOverlay.startAnimation(fadeOut);
                virusOverlay.postDelayed(() -> virusOverlay.setVisibility(View.GONE), 500);
            }, 1200);
        });
    }

    private int getProgressColorByVirusCount(int virusCount) {
        if (virusCount == 0) return Color.parseColor("#4CAF50"); // green
        if (virusCount == 1) return Color.parseColor("#FF9800"); // orange
        if (virusCount == 2) return Color.parseColor("#FF7043"); // deep orange
        int alpha = Math.min(255, 100 + virusCount * 50);
        return Color.argb(alpha, 244, 67, 54); // #F44336 qizil, alpha kuchayadi
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.status_bar_scan_gradient));
        }

        ScanProgressView progressView = findViewById(R.id.scanProgressView);
        Button btnQuickScan = findViewById(R.id.btnQuickScan);
        TextView tvScanStatus = findViewById(R.id.tvScanStatus);
        ImageView btnBack = findViewById(R.id.btnBack);
        TextView tvTitle = findViewById(R.id.tvTitle);
        NativeLib nativeLib = new NativeLib();
        LottieAnimationView lottieScan = findViewById(R.id.lottieScan);
        lottieScan.setVisibility(View.VISIBLE);
        lottieScan.setAnimation("success.json");
        lottieScan.setRepeatCount(0);
        lottieScan.playAnimation();

        View.OnClickListener backListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ScanActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
                overridePendingTransition(0, 0); // No animation
            }
        };
        btnBack.setOnClickListener(backListener);
        tvTitle.setOnClickListener(backListener);


        CardView cardFullScan = findViewById(R.id.cardFullScan);
        cardFullScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ScanActivity.this, VirusActivity.class));
                overridePendingTransition(0, 0);
            }
        });
        String virusFilePath = getIntent().getStringExtra("virus_file_path");
        if (virusFilePath != null) {
            showModernVirusDialog(virusFilePath);
        }

        AntivirusDatabase db = new AntivirusDatabase(this);
        int virusCount = db.getVirusCount();
        if (virusCount > 0) {
            progressView.setProgressColor(android.graphics.Color.parseColor("#F44336"));
            tvScanStatus.setText("Zararli fayl(lar) mavjud");
        } else {
            progressView.setProgressColor(android.graphics.Color.parseColor("#4CAF50"));
            tvScanStatus.setText("Telefoningiz xavfsiz holatda");
        }

        btnQuickScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnQuickScan.setEnabled(false);
                btnQuickScan.setText("Tekshirilmoqda...");
                progressView.animateProgress(0f);
                tvScanStatus.setText("0% skanerlandi");
                lottieScan.setVisibility(View.VISIBLE);
                lottieScan.setAnimation("scan.json");
                lottieScan.setRepeatCount(LottieDrawable.INFINITE);
                lottieScan.playAnimation();

                new Thread(() -> {
                    String rootPath = "/storage/emulated/0";
                    AssetManager assetManager = getAssets();
                    ArrayList<String> allFiles = getAllFilesAndInstalledApks(rootPath);
                    String[] fileArr = allFiles.toArray(new String[0]);
                    int totalFiles = fileArr.length;
                    int batchSize = 10;
                    int totalBatches = (int) Math.ceil((double) totalFiles / batchSize);
                    JSONArray virusArr = new JSONArray();
                    AtomicInteger checkedBatches = new AtomicInteger(0);

                    int THREAD_COUNT = 2;
                    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

                    for (int batch = 0; batch < totalBatches; batch++) {
                        int start = batch * batchSize;
                        int end = Math.min(start + batchSize, totalFiles);
                        String[] batchFiles = Arrays.copyOfRange(fileArr, start, end);
                        executor.execute(() -> {
                            String batchResult = nativeLib.quickScanFiles(batchFiles, assetManager);
                            synchronized (virusArr) {
                                try {
                                    JSONArray arr = new JSONArray(batchResult);
                                    for (int i = 0; i < arr.length(); i++) {
                                        virusArr.put(arr.getJSONObject(i));
                                    }
                                } catch (Exception ignored) {}
                            }
                            int done = checkedBatches.incrementAndGet();
                            float progress = (float) (done * batchSize) / totalFiles;
                            int color = getProgressColorByVirusCount(virusArr.length());
                            runOnUiThread(() -> {
                                int percent = Math.round(Math.min(progress, 1f) * 100);
                                tvScanStatus.setText(percent + "% skanerlandi");
                                progressView.animateProgress(Math.min(progress, 1f));
                                progressView.setProgressColor(color);
                            });
                        });
                    }

                    executor.shutdown();
                    while (!executor.isTerminated()) {
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                    }

                    runOnUiThread(() -> {
                        btnQuickScan.setEnabled(true);
                        btnQuickScan.setText("Tekshirish");
                        progressView.animateProgress(1f);
                        int finalColor;
                        if (virusArr.length() > 0) {
                            finalColor = Color.parseColor("#F44336");
                        } else {
                            finalColor = Color.parseColor("#4CAF50");
                        }
                        progressView.setProgressColor(finalColor);
                        if (virusArr.length() > 0) {
                            AntivirusDatabase db = new AntivirusDatabase(ScanActivity.this);
                            String detectedAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                            StringBuilder status = new StringBuilder();
                            for (int i = 0; i < virusArr.length(); i++) {
                                try {
                                    JSONObject obj = virusArr.getJSONObject(i);
                                    String filePath = obj.getString("path");
                                    String hash = obj.optString("hash", "");
                                    java.io.File file = new java.io.File(filePath);
                                    String fileName = file.getName();
                                    long fileSize = file.exists() ? file.length() : 0;
                                    db.insertVirusFile(fileName, filePath, fileSize, detectedAt, hash);
                                    showVirusNotification(filePath);
                                    showModernVirusDialog(filePath);
                                    status.append(filePath).append("\n");
                                } catch (Exception ignored) {}
                            }
                            tvScanStatus.setText("Zararli dastur aniqlandi");
                            lottieScan.setAnimation("found_virus.json");
                            lottieScan.setRepeatCount(LottieDrawable.INFINITE);
                            lottieScan.playAnimation();
                        } else {
                            tvScanStatus.setText("Qurulmada zararli dastur aniqlanmadi");
                            lottieScan.setAnimation("success.json");
                            lottieScan.setRepeatCount(0);
                            lottieScan.playAnimation();
                        }
                    });
                }).start();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        }
    }
} 