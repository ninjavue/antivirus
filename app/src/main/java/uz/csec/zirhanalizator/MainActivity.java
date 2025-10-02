package uz.csec.zirhanalizator;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;

import android.provider.Settings;
import android.net.Uri;
import android.os.PowerManager;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.widget.Toast;
import android.app.AlertDialog;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.WindowInsetsControllerCompat;

// import uz.zirh.zirhlib.ZirhMilliy;

public class MainActivity extends BaseActivity {

    // ZirhMilliy zirh = new ZirhMilliy();

    private ActivityResultLauncher<Intent> filePickerLauncher;
    private String pickedFilePath;
    private LinearLayout bottomNavigationView;
    private LinearLayout navHome, navMulti, navSettings;
    private static final int RC_POST_NOTIFICATIONS = 1001;
    private static final int REQUEST_STORAGE = 1001;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        }
        WindowInsetsControllerCompat insetsController =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);

//        Intent serviceIntent = new Intent(this, AppBehaviorMonitorService.class);
//        serviceIntent.setAction("scan_now");
//        startService(serviceIntent);

        setContentView(R.layout.activity_main);
        setupBottomNav();

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, RC_POST_NOTIFICATIONS);
            }
        }

        if (!hasStoragePermission()) {
            requestStoragePermission();
        }
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission();
        }

        Intent serviceInten = new Intent(this, AntivirusService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceInten);
        }

        GridLayout grid = findViewById(R.id.gridFeatures);
        LayoutInflater inflater = LayoutInflater.from(this);
        String[] titles = {getString(R.string.cleaner), getString(R.string.battery), getString(R.string.data), getString(R.string.antivirus), getString(R.string.security), getString(R.string.files)};
        int[] icons = {R.drawable.ic_chart, R.drawable.ic_battery, R.drawable.ic_drop, R.drawable.ic_antivirus, R.drawable.ic_qulf, R.drawable.ic_umbrella};
        for (int i = 0; i < 6; i++) {
            View item = inflater.inflate(R.layout.item_main_button, grid, false);
            ImageView iconView = item.findViewById(R.id.icon);
            TextView titleView = item.findViewById(R.id.title);
            iconView.setImageResource(icons[i]);
            titleView.setText(titles[i]);
            int finalI = i;
            item.setOnClickListener(v -> {
                if (finalI == 0) {
                    startActivity(new Intent(this, CleanerActivity.class));
                    overridePendingTransition(0, 0);
                }
                if (finalI == 1) {
                    startActivity(new Intent(this, BatteryActivity.class));
                    overridePendingTransition(0, 0);
                }
                if (finalI == 2) {
                    startActivity(new Intent(this, DataActivity.class));
                    overridePendingTransition(0, 0);
                }
                if (finalI == 3) {
                    startActivity(new Intent(this, ScanActivity.class));
                    overridePendingTransition(0, 0);
                }
                if (finalI == 4) {
                    startActivity(new Intent(this, SecurityActivity.class));
                    overridePendingTransition(0, 0);
                }
                if (finalI == 5) {
                    startActivity(new Intent(this, FilesActivity.class));
                    overridePendingTransition(0, 0);
                }
            });

            item.setBackgroundResource(R.drawable.item_main_button_bg);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = (int) (120 * getResources().getDisplayMetrics().density);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(8, 8, 8, 8);
            item.setLayoutParams(params);
            grid.addView(item);
        }
        uz.csec.zirhanalizator.CircularProgressView progressView = findViewById(R.id.progressGradient);
        Button btnOptimize = findViewById(R.id.btnOptimize);

        new Handler().postDelayed(() -> progressView.animateProgress(0.89f), 400);
        btnOptimize.setScaleX(0f);
        
        startFileMonitoringServices();
        
        setupFilePicker();
        
        btnOptimize.setScaleY(0f);
        btnOptimize.animate().scaleX(1f).scaleY(1f).setDuration(700).setStartDelay(700).start();
    }


    private void startFileMonitoringServices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, FileMonitorService.class));
        } else {
            startService(new Intent(this, FileMonitorService.class));
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();


        // boolean isEmulyator = zirh.emulyatorniAniqlash(this);
        // boolean isRoot = zirh.rootniAniqlash();
        // boolean isPlayStore = zirh.playMarketniAniqlash(this);

        // if (!isEmulyator || isRoot || isPlayStore) {
        //     finishAffinity();
        //     System.exit(0);
        // }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }
        handleIncomingShare();
        findViewById(R.id.btnOptimize).setOnClickListener(v -> openFilePicker());
    }
    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        String filePath = getFilePathFromUri(uri);
                        if (filePath != null) {
                            pickedFilePath = filePath;
                            scanPickedFile(filePath, uri);
                        } else {
                            showSimpleDialog("Xatolik", "Faylni ochib bo'lmadi");
                        }
                    }
                }
            }
        );
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.select_file)));
    }
    private void scanPickedFile(String filePath, Uri fileUri) {
        CircularProgressView progressView = findViewById(R.id.progressGradient);
        Button btnOptimize = findViewById(R.id.btnOptimize);
        
        progressView.setProgress(0.0f);
        progressView.animateProgress(1.0f, 5000);
        
        btnOptimize.setEnabled(false);
        btnOptimize.setText(getString(R.string.checking) + "...");
        btnOptimize.setBackgroundResource(R.drawable.btn_optimize_disabled_bg);
        
        new Thread(() -> {
            boolean isVirus = FileScanHelper.isVirus(this, filePath);
            runOnUiThread(() -> {
                btnOptimize.setEnabled(true);
                btnOptimize.setText(getString(R.string.select_file));
                btnOptimize.setBackgroundResource(R.drawable.btn_optimize_bg);
                
                progressView.animateProgress(1f);
                
                showScanResultDialog(isVirus, filePath, fileUri);
            });
        }).start();
    }

    private void showSimpleDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }
    private void handleIncomingShare() {
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            Uri fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (fileUri != null) {
                String filePath = getFilePathFromUri(fileUri);
                if (filePath != null) {
                    CircularProgressView progressView = findViewById(R.id.progressGradient);
                    Button btnOptimize = findViewById(R.id.btnOptimize);
                    
                    progressView.setProgress(0.0f);
                    progressView.animateProgress(1.0f, 5000);
                    
                    btnOptimize.setEnabled(false);
                    btnOptimize.setText("Tekshirilmoqda...");
                    btnOptimize.setBackgroundResource(R.drawable.btn_optimize_disabled_bg);
                    
                    new Thread(() -> {
                        boolean isVirus = FileScanHelper.isVirus(this, filePath);
                        runOnUiThread(() -> {
                            btnOptimize.setEnabled(true);
                            btnOptimize.setText(getString(R.string.select_file));
                            btnOptimize.setBackgroundResource(R.drawable.btn_optimize_bg);

                            progressView.animateProgress(1f);

                            showScanResultDialog(isVirus, filePath, fileUri);
                        });
                    }).start();
                } else {
                    Toast.makeText(this, "Faylni ochib bo'lmadi", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void showScanResultDialog(boolean isVirus, String filePath, Uri fileUri) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.MyAlertDialogTheme);

        if (isVirus) {
            builder.setTitle("Xavfli fayl aniqlandi!")
                    .setMessage("Bu fayl zararli!\n\nFayl: " + filePath)
                    .setPositiveButton("Faylni ko‘rish", (d, w) -> {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(fileUri, "*/*");
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(Intent.createChooser(intent, "Faylni ochish"));
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(this, "Faylni ochib bo‘lmadi", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Yopish", null)
                    .show();
        } else {
            builder.setTitle("Fayl xavfsiz")
                    .setMessage("Fayl xavfsiz: " + filePath)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }



    private String getFilePathFromUri(Uri uri) {
        if (uri == null) return null;
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    String name = (idx >= 0) ? cursor.getString(idx) : "file";
                    java.io.File cacheFile = new java.io.File(getCacheDir(), name);
                    try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                         java.io.OutputStream out = new java.io.FileOutputStream(cacheFile)) {
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    } catch (Exception e) { return null; }
                    return cacheFile.getAbsolutePath();
                }
            } catch (Exception e) {
                return null;
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        return null;
    }


    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_IMAGES
            }, REQUEST_STORAGE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }

    private boolean hasUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        }
        return true;
    }

    private void requestUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            if (hasStoragePermission()) recreate();
        }
    }
}