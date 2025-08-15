package uz.csec.antivirus;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.view.animation.ScaleAnimation;
import android.provider.Settings;
import android.net.Uri;
import android.os.PowerManager;
import android.content.pm.ApplicationInfo;
import java.util.List;
import android.content.ClipData;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.widget.Toast;
import android.app.AlertDialog;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import uz.csec.antivirus.CircularProgressView;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<Intent> filePickerLauncher;
    private String pickedFilePath;
    private LinearLayout bottomNavigationView;
    private LinearLayout navHome, navMulti, navSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        }
        setContentView(R.layout.activity_main);
        setupBottomNavigation();

        GridLayout grid = findViewById(R.id.gridFeatures);
        LayoutInflater inflater = LayoutInflater.from(this);
        String[] titles = {"Tozalash", "Quvvat", "Malumot", "Antivirus", "Xavfsizlik", "Bloklovchi"};
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

        uz.csec.antivirus.CircularProgressView progressView = findViewById(R.id.progressGradient);
        Button btnOptimize = findViewById(R.id.btnOptimize);
        
        // Set text for progress view
        progressView.setMainText("89");
        progressView.setSubtitleText("Optimallashtirish\ntaklif etiladi");
        
        new Handler().postDelayed(() -> progressView.animateProgress(0.89f), 400);
        btnOptimize.setScaleX(0f);
        
        // Start file monitoring services
        startFileMonitoringServices();
        
        // Setup file picker (must be done in onCreate)
        setupFilePicker();
        
        btnOptimize.setScaleY(0f);
        btnOptimize.animate().scaleX(1f).scaleY(1f).setDuration(700).setStartDelay(700).start();
    }

    private void setupBottomNavigation() {
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        navHome = findViewById(R.id.nav_home);
        navMulti = findViewById(R.id.nav_multi);
        navSettings = findViewById(R.id.nav_settings);
        
        // Set initial state (home selected)
        updateNavigationSelection(navHome);
        
        navHome.setOnClickListener(v -> {
            updateNavigationSelection(navHome);
            // Already on home, do nothing
        });
        
        navMulti.setOnClickListener(v -> {
            updateNavigationSelection(navMulti);
            showMultiWindowDialog();
        });
        
        navSettings.setOnClickListener(v -> {
            updateNavigationSelection(navSettings);
            showSettingsDialog();
        });
    }

    private void updateNavigationSelection(LinearLayout selectedNav) {
        resetNavigationColors();
        
        ImageView selectedIcon = (ImageView) selectedNav.getChildAt(0);
        selectedIcon.setColorFilter(getResources().getColor(R.color.white));

    }

    private void resetNavigationColors() {
        ImageView homeIcon = (ImageView) navHome.getChildAt(0);
        homeIcon.setColorFilter(getResources().getColor(R.color.bottom_nav_unselected));

        ImageView multiIcon = (ImageView) navMulti.getChildAt(0);
        multiIcon.setColorFilter(getResources().getColor(R.color.bottom_nav_unselected));

        ImageView settingsIcon = (ImageView) navSettings.getChildAt(0);
        settingsIcon.setColorFilter(getResources().getColor(R.color.bottom_nav_unselected));
    }


    private void showMultiWindowDialog() {
        Intent intent = new Intent(this, ScannerActivity.class);
        overridePendingTransition(0, 0);
        startActivity(intent);
    }

    private void showSettingsDialog() {
        Intent intent = new Intent(this, SettingsActivity.class);
        overridePendingTransition(0, 0);
        startActivity(intent);
    }
    

    
    private void startFileMonitoringServices() {
        // Start the file monitoring services
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, FileMonitorService.class));
            startForegroundService(new Intent(this, TelegramDownloadMonitorService.class));
        } else {
            startService(new Intent(this, FileMonitorService.class));
            startService(new Intent(this, TelegramDownloadMonitorService.class));
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
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

        // TEST CODE REMOVED: Danger dialog will only show for real dangerous apps
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
        filePickerLauncher.launch(Intent.createChooser(intent, "Faylni tanlang"));
    }

    private void scanPickedFile(String filePath, Uri fileUri) {
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
                btnOptimize.setText(getString(R.string.optimize1));
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
                            btnOptimize.setText(getString(R.string.optimize1));
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (isVirus) {
            builder.setTitle("Xavfli fayl aniqlandi!")
                .setMessage("Bu fayl zararli! Asl faylni Telegram yoki fayl menejeri orqali o'chiring.\n\nFayl: " + filePath)
                .setPositiveButton("Faylni ochish", (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(fileUri, "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(this, "Faylni ochib bo'lmadi", Toast.LENGTH_SHORT).show();
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

    private void tekshirishBarchaIlovalar() {
        new Thread(() -> {
            try {
                FileScanHelper.checkAllInstalledApps(getApplicationContext());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}