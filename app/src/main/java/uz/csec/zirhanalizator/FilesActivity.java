package uz.csec.zirhanalizator;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.provider.Settings;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.viewpager2.widget.ViewPager2;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// import uz.zirh.zirhlib.ZirhMilliy;

public class FilesActivity extends AppCompatActivity {

    // ZirhMilliy zirh = new ZirhMilliy();
    
    private NativeLib nativeLib;
    private FilePagerAdapter pagerAdapter;
    private ViewPager2 viewPager;
    private ProgressBar progressBar;
    private TextView scanStatus;
    private ImageButton btnRefresh;
    private TextView tvLargeFiles, tvSuspiciousFiles, tvHiddenFiles;
    private ExecutorService executor;
    private View indicatorLarge, indicatorSuspicious, indicatorHidden;
    private int scanSequence = 0;
    
    private static final String[] TAB_TITLES = {"Katta fayllar", "Shubhali fayllar", "Yashirin fayllar"};
    
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    scanFiles();
                } else {
                    Toast.makeText(this, "Fayllarga kirish uchun ruxsat kerak", Toast.LENGTH_LONG).show();
                }
            });
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        WindowInsetsControllerCompat insetsController =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);
        setContentView(R.layout.activity_files);
        
        initViews();
        setupListeners();
        setupViewPager();
        
        nativeLib = new NativeLib();
        executor = Executors.newSingleThreadExecutor();
        checkPermissionsAndScan();
    }


    // @Override
    // protected void onResume() {
    //     super.onResume();


    //     boolean isEmulyator = zirh.emulyatorniAniqlash(this);
    //     boolean isRoot = zirh.rootniAniqlash();
    //     boolean isPlayStore = zirh.playMarketniAniqlash(this);
    //     if (!isEmulyator || isRoot || isPlayStore) {
    //         finishAffinity();
    //         System.exit(0);
    //     }
    // }

    private void checkPermissionsAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                new AlertDialog.Builder(this)
                    .setTitle("Ruxsat kerak")
                    .setMessage("Ilova barcha fayllarga kirish uchun ruxsat so'raydi. Sozlamalarga o'ting va ruxsat bering.")
                    .setPositiveButton("Sozlamalar", (d, w) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("Bekor qilish", null)
                    .show();
                return;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] permissions = {
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES
            };
            
            boolean allGranted = true;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                scanFiles();
            } else {
                permissionLauncher.launch(permissions);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                scanFiles();
            } else {
                permissionLauncher.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE});
            }
        }
    }
    
    private void initViews() {
        viewPager = findViewById(R.id.viewPager);
        progressBar = findViewById(R.id.progressBar);
        scanStatus = findViewById(R.id.scanStatus);
        tvLargeFiles = findViewById(R.id.tvLargeFiles);
        tvSuspiciousFiles = findViewById(R.id.tvSuspiciousFiles);
        tvHiddenFiles = findViewById(R.id.tvHiddenFiles);
        indicatorLarge = findViewById(R.id.indicatorLarge);
        indicatorSuspicious = findViewById(R.id.indicatorSuspicious);
        indicatorHidden = findViewById(R.id.indicatorHidden);
        findViewById(R.id.cardLargeFiles).setOnClickListener(v -> viewPager.setCurrentItem(0, true));
        findViewById(R.id.cardSuspiciousFiles).setOnClickListener(v -> viewPager.setCurrentItem(1, true));
        findViewById(R.id.cardHiddenFiles).setOnClickListener(v -> viewPager.setCurrentItem(2, true));

    }
    
    private void setupListeners() {
        findViewById(R.id.header).setOnClickListener(v -> finish());

        if (scanStatus != null) {
            scanStatus.setText(getString(R.string.click_files));
        }
    }
    
    private void setupViewPager() {
        pagerAdapter = new FilePagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateIndicators(position);
            }
        });

        // Default state
        updateIndicators(0);
    }
    
    private void scanFiles() {
        showLoading(true);
        setStatus(getString(R.string.file_scanning));

        executor.execute(() -> {
            try {
                String rootPath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();

                String largeFilesJson = nativeLib.getLargeFiles(rootPath, 100 * 1024 * 1024);

                String suspiciousFilesJson = nativeLib.getSuspiciousFiles(rootPath);

                String hiddenFilesJson = nativeLib.getHiddenFiles(rootPath);

                String statsJson = nativeLib.getFileStatistics(rootPath);

                runOnUiThread(() -> {
                    updateUI(largeFilesJson, suspiciousFilesJson, hiddenFilesJson, statsJson);
                    showLoading(false);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    setStatus("Xatolik yuz berdi: " + e.getMessage());
                    showLoading(false);
                });
            }
        });
    }
    
    private void updateUI(String largeFilesJson, String suspiciousFilesJson, String hiddenFilesJson, String statsJson) {
        pagerAdapter.updateLargeFiles(largeFilesJson);
        pagerAdapter.updateSuspiciousFiles(suspiciousFilesJson);
        pagerAdapter.updateHiddenFiles(hiddenFilesJson);
        
        try {
            JSONObject stats = new JSONObject(statsJson);
            tvLargeFiles.setText(String.valueOf(stats.optInt("largeFiles", 0)));
            tvSuspiciousFiles.setText(String.valueOf(stats.optInt("suspiciousFiles", 0)));
            tvHiddenFiles.setText(String.valueOf(stats.optInt("hiddenFiles", 0)));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        // Ensure current fragment receives the listener immediately
        androidx.fragment.app.Fragment current = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
        if (current instanceof FileListFragment) {
            ((FileListFragment) current).setOnFileScanListener(this::scanIndividualFile);
        }
        
        setStatus(getString(R.string.click_files));
    }
    
    private void scanIndividualFile(FileAdapter.FileItem file) {
        final long startMs = SystemClock.uptimeMillis();
        final int localSeq = ++scanSequence;
        setStatus(getString(R.string.checking_file) + file.name);
        
        executor.execute(() -> {
            try {
                String scanResult = nativeLib.scanFileWithAntivirus(file.path);
                JSONObject result = new JSONObject(scanResult);
                
                runOnUiThread(() -> {
                    String message;
                    if (result.optBoolean("suspicious", false)) {
                        message = getString(R.string.sus_file) + file.name + "\nSababi: " + result.optString("reason", "");
                    } else {
                        message = getString(R.string.safe_file) + file.name;
                    }
                    long elapsed = SystemClock.uptimeMillis() - startMs;
                    long delay = Math.max(0, 1000 - elapsed);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (localSeq == scanSequence) {
                            setStatus(message);
                        }
                    }, delay);
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    long elapsed = SystemClock.uptimeMillis() - startMs;
                    long delay = Math.max(0, 1000 - elapsed);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (localSeq == scanSequence) {
                            setStatus("Fayl tekshirishda xatolik");
                        }
                    }, delay);
                });
            }
        });
    }
    
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setStatus(String text) {
        if (scanStatus != null) {
            scanStatus.setText(text);
        }
    }

    private void updateIndicators(int position) {
        if (indicatorLarge == null) return;
        indicatorLarge.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        indicatorSuspicious.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        indicatorHidden.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
    }
    
    @Override
    public void onAttachFragment(@NonNull androidx.fragment.app.Fragment fragment) {
        super.onAttachFragment(fragment);
        if (fragment instanceof FileListFragment) {
            ((FileListFragment) fragment).setOnFileScanListener(this::scanIndividualFile);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}