package uz.csec.antivirus;

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

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FilesActivity extends AppCompatActivity {
    
    private NativeLib nativeLib;
    private FilePagerAdapter pagerAdapter;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private ProgressBar progressBar;
    private Button scanButton;
    private ImageButton btnRefresh;
    private TextView tvLargeFiles, tvSuspiciousFiles, tvHiddenFiles;
    private ExecutorService executor;
    
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
        setContentView(R.layout.activity_files);
        
        initViews();
        setupListeners();
        setupViewPager();
        
        nativeLib = new NativeLib();
        executor = Executors.newSingleThreadExecutor();
        checkPermissionsAndScan();
    }
    
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
        tabLayout = findViewById(R.id.tabLayout);
        progressBar = findViewById(R.id.progressBar);
        scanButton = findViewById(R.id.scanButton);
        tvLargeFiles = findViewById(R.id.tvLargeFiles);
        tvSuspiciousFiles = findViewById(R.id.tvSuspiciousFiles);
        tvHiddenFiles = findViewById(R.id.tvHiddenFiles);
    }
    
    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        
        scanButton.setOnClickListener(v -> {
            scanButton.setEnabled(false);
            scanButton.setText("Tekshirilmoqda...");
            checkPermissionsAndScan();
        });
    }
    
    private void setupViewPager() {
        pagerAdapter = new FilePagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(TAB_TITLES[position]);
        }).attach();
    }
    
    private void scanFiles() {
        showLoading(true);

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
                    Toast.makeText(this, "Xatolik yuz berdi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        
        for (int i = 0; i < 3; i++) {
            FileListFragment fragment = (FileListFragment) getSupportFragmentManager()
                    .findFragmentByTag("f" + pagerAdapter.getItemId(i));
            if (fragment != null) {
                fragment.setOnFileScanListener(this::scanIndividualFile);
            }
        }
        
        scanButton.setEnabled(true);
        scanButton.setText("Fayllarni tekshirish");
    }
    
    private void scanIndividualFile(FileAdapter.FileItem file) {
        Toast.makeText(this, "Fayl tekshirilmoqda: " + file.name, Toast.LENGTH_SHORT).show();
        
        executor.execute(() -> {
            try {
                String scanResult = nativeLib.scanFileWithAntivirus(file.path);
                JSONObject result = new JSONObject(scanResult);
                
                runOnUiThread(() -> {
                    String message;
                    if (result.optBoolean("suspicious", false)) {
                        message = "Shubhali fayl: " + file.name + "\nSababi: " + result.optString("reason", "");
                    } else {
                        message = "Xavfsiz fayl: " + file.name;
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Fayl tekshirishda xatolik", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        scanButton.setEnabled(!show);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}