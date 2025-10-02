package uz.csec.zirhanalizator;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.airbnb.lottie.LottieAnimationView;
// import uz.zirh.zirhlib.ZirhMilliy;

public class DataActivity extends AppCompatActivity {

    // private ZirhMilliy zirh = new ZirhMilliy();
    private NativeLib nativeLib = new NativeLib();
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private LinearLayout loadingContainer;
    private ConstraintLayout mainContent;
    private TextView tvRunningApps, tvUnusedApps, tvCpuUsage, tvUptime, tvBatteryUsage;
    private LottieAnimationView lottieData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_data);

        // Initialize UI components
        loadingContainer = findViewById(R.id.loading_container);
        mainContent = findViewById(R.id.main_content);
        @SuppressLint({"MissingInflatedId", "LocalSuppress"}) LinearLayout backBtn = findViewById(R.id.backBtnContainer);
        @SuppressLint({"MissingInflatedId", "LocalSuppress"}) TextView tvTitle = findViewById(R.id.tvTitle);
        tvRunningApps = findViewById(R.id.tv_running_apps);
        tvUnusedApps = findViewById(R.id.tv_unused_apps);
        tvCpuUsage = findViewById(R.id.tv_cpu_usage);
        tvUptime = findViewById(R.id.tv_uptime);
        tvBatteryUsage = findViewById(R.id.tv_battery_usage);
        lottieData = findViewById(R.id.lottieData);

        // Set click listener for back button
        View.OnClickListener backListener = v -> {
            Intent intent = new Intent(DataActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
            overridePendingTransition(0, 0);
        };
        backBtn.setOnClickListener(backListener);
        tvTitle.setOnClickListener(backListener);

        // Ensure loading state is shown initially
        loadingContainer.setVisibility(View.VISIBLE);
        mainContent.setVisibility(View.GONE);

        // Check permissions and load data
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission();
        } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BATTERY_STATS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BATTERY_STATS}, PERMISSION_REQUEST_CODE);
        } else {
            loadData();
        }
    }

    // @Override
    // protected void onResume() {
    //     super.onResume();
    //     // Check security conditions
    //     boolean isEmulyator = zirh.emulyatorniAniqlash(this);
    //     boolean isRoot = zirh.rootniAniqlash();
    //     boolean isPlayStore = zirh.playMarketniAniqlash(this);
    //     if (!isEmulyator || isRoot || isPlayStore) {
    //         finishAffinity();
    //         System.exit(0);
    //     }
    // }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BATTERY_STATS) == PackageManager.PERMISSION_GRANTED) {
                if (hasUsageStatsPermission()) {
                    loadData();
                } else {
                    requestUsageStatsPermission();
                }
            } else {
                // Permission denied, show error and keep loading UI
                loadingContainer.setVisibility(View.GONE);
                mainContent.setVisibility(View.VISIBLE);
                tvBatteryUsage.setText("Batareya ma'lumotlari uchun ruxsat berilmadi");
                renderRunningApps(""); // Show placeholder
                renderUnusedApps(""); // Show placeholder
                tvCpuUsage.setText("N/A");
                tvUptime.setText("N/A");
                lottieData.setVisibility(View.VISIBLE);
                lottieData.setAnimation("data.json");
                lottieData.setRepeatCount(100);
                lottieData.playAnimation();
            }
        }
    }

    private void loadData() {
        // Execute data loading in background
        new LoadDataTask().execute();
    }

    // AsyncTask to load data
    private class LoadDataTask extends AsyncTask<Void, Void, DataResult> {
        @Override
        protected void onPreExecute() {
            // Show loading container and hide main content
            loadingContainer.setVisibility(View.VISIBLE);
            mainContent.setVisibility(View.GONE);
        }

        @Override
        protected DataResult doInBackground(Void... voids) {
            String runningApps = "";
            String unusedApps = "";
            String cpuUsage = "N/A";
            String uptime = "N/A";
            String batteryUsage = "N/A";

            try {
                runningApps = nativeLib.getRunningAppsJava(DataActivity.this);
            } catch (Exception e) {
                runningApps = "Xatolik: " + e.getMessage();
            }

            try {
                unusedApps = nativeLib.getUnusedAppsJava(DataActivity.this);
            } catch (Exception e) {
                unusedApps = "Xatolik: " + e.getMessage();
            }

            try {
                cpuUsage = nativeLib.getAppCpuUsage();
            } catch (Exception e) {
                cpuUsage = "Xatolik: " + e.getMessage();
            }

            try {
                uptime = nativeLib.getDeviceUptime(DataActivity.this);
            } catch (Exception e) {
                uptime = "Uptime xatolik: " + e.getMessage();
            }

            try {
                batteryUsage = nativeLib.getAppBatteryUsage(DataActivity.this);
            } catch (Exception e) {
                batteryUsage = "Xatolik: " + e.getMessage();
            }

            return new DataResult(runningApps, unusedApps, cpuUsage, uptime, batteryUsage);
        }

        @Override
        protected void onPostExecute(DataResult result) {
            // Update UI with loaded data
            renderRunningApps(result.runningApps);
            renderUnusedApps(result.unusedApps);
            tvCpuUsage.setText(result.cpuUsage);
            tvUptime.setText(result.uptime);
            tvBatteryUsage.setText(result.batteryUsage);

            // Play Lottie animation
            lottieData.setVisibility(View.VISIBLE);
            lottieData.setAnimation("data.json");
            lottieData.setRepeatCount(100);
            lottieData.playAnimation();

            // Hide loading container and show main content
            loadingContainer.setVisibility(View.GONE);
            mainContent.setVisibility(View.VISIBLE);
        }
    }

    // Data class to hold results
    private static class DataResult {
        String runningApps;
        String unusedApps;
        String cpuUsage;
        String uptime;
        String batteryUsage;

        DataResult(String runningApps, String unusedApps, String cpuUsage, String uptime, String batteryUsage) {
            this.runningApps = runningApps;
            this.unusedApps = unusedApps;
            this.cpuUsage = cpuUsage;
            this.uptime = uptime;
            this.batteryUsage = batteryUsage;
        }
    }

    private void renderUnusedApps(String raw) {
        LinearLayout container = findViewById(R.id.ll_unused_apps);
        TextView placeholder = findViewById(R.id.tv_unused_apps);
        if (container == null || placeholder == null) return;

        if (raw == null) raw = "";
        String trimmed = raw.trim();

        if (trimmed.isEmpty() ||
                "API past".equalsIgnoreCase(trimmed) ||
                "Bunday ilovalar mavjud emas".equalsIgnoreCase(trimmed) ||
                trimmed.startsWith("Xatolik:")) {
            container.setVisibility(View.GONE);
            placeholder.setVisibility(View.VISIBLE);
            placeholder.setText(trimmed.isEmpty() ? "Bunday ilovalar mavjud emas" : trimmed);
            return;
        }

        container.setVisibility(View.VISIBLE);
        placeholder.setVisibility(View.GONE);
        container.removeAllViews();

        int iconSize = dp(20);
        int rowPaddingV = dp(6);
        int iconMarginEnd = dp(8);
        int textColor = ContextCompat.getColor(this, R.color.antivirus_text);

        String[] lines = trimmed.split("\n");
        for (String line : lines) {
            String appName = line == null ? "" : line.trim();
            if (appName.isEmpty()) continue;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, rowPaddingV, 0, rowPaddingV);

            ImageView icon = new ImageView(this);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.setMarginEnd(iconMarginEnd);
            icon.setLayoutParams(iconParams);
            icon.setImageResource(R.drawable.android_24);

            TextView name = new TextView(this);
            name.setText(appName);
            name.setTextColor(textColor);

            row.addView(icon);
            row.addView(name);
            container.addView(row);
        }
    }

    private void renderRunningApps(String raw) {
        LinearLayout container = findViewById(R.id.ll_running_apps);
        TextView placeholder = findViewById(R.id.tv_running_apps);
        if (container == null || placeholder == null) return;

        if (raw == null) raw = "";
        String trimmed = raw.trim();

        if (trimmed.isEmpty() ||
                "Faol ilovalar topilmadi".equalsIgnoreCase(trimmed) ||
                trimmed.startsWith("Xatolik:")) {
            container.setVisibility(View.GONE);
            placeholder.setVisibility(View.VISIBLE);
            placeholder.setText(trimmed.isEmpty() ? "Faol ilovalar topilmadi" : trimmed);
            return;
        }

        container.setVisibility(View.VISIBLE);
        placeholder.setVisibility(View.GONE);
        container.removeAllViews();

        int iconSize = dp(20);
        int rowPaddingV = dp(6);
        int iconMarginEnd = dp(8);
        int textColor = ContextCompat.getColor(this, R.color.antivirus_text);

        String[] lines = trimmed.split("\n");
        for (String line : lines) {
            String appName = line == null ? "" : line.trim();
            if (appName.isEmpty()) continue;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, rowPaddingV, 0, rowPaddingV);

            ImageView icon = new ImageView(this);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.setMarginEnd(iconMarginEnd);
            icon.setLayoutParams(iconParams);
            icon.setImageResource(R.drawable.android_24);

            TextView name = new TextView(this);
            name.setText(appName);
            name.setTextColor(textColor);

            row.addView(icon);
            row.addView(name);
            container.addView(row);
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void requestUsageStatsPermission() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivity(intent);
    }
}