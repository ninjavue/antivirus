package uz.csec.antivirus;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;

public class DataActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1001;
    NativeLib nativeLib = new NativeLib();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_data);

        @SuppressLint({"MissingInflatedId", "LocalSuppress"}) LinearLayout backBtn = findViewById(R.id.backBtnContainer);

        backBtn.setOnClickListener(v -> finish());

        @SuppressLint({"MissingInflatedId", "LocalSuppress"}) LottieAnimationView lottieScan = findViewById(R.id.lottieData);
        lottieScan.setVisibility(View.VISIBLE);
        lottieScan.setAnimation("data.json");
        lottieScan.setRepeatCount(100);
        lottieScan.playAnimation();

        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission();
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PERMISSION_REQUEST_CODE);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BATTERY_STATS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BATTERY_STATS}, PERMISSION_REQUEST_CODE);
        }

        renderRunningApps(NativeLib.getRunningAppsJava(this));
        renderUnusedApps(NativeLib.getUnusedAppsJava(this));
        ((TextView)findViewById(R.id.tv_cpu_usage)).setText(nativeLib.getAppCpuUsage());
        try {
            ((TextView)findViewById(R.id.tv_uptime)).setText(nativeLib.getDeviceUptime(this));
        } catch (Exception e) {
            ((TextView)findViewById(R.id.tv_uptime)).setText("Uptime xatolik: " + e.getMessage());
            e.printStackTrace();
        }
        ((TextView)findViewById(R.id.tv_battery_usage)).setText(nativeLib.getAppBatteryUsage(this));
    }

    private void renderUnusedApps(String raw) {
        LinearLayout container = findViewById(R.id.ll_unused_apps);
        TextView placeholder = findViewById(R.id.tv_unused_apps);
        if (container == null || placeholder == null) return;

        if (raw == null) raw = "";
        String trimmed = raw.trim();

        // If native layer returned a message instead of a list
        if (trimmed.isEmpty() ||
                "API past".equalsIgnoreCase(trimmed) ||
                "Bunday ilovalar mavjud emas".equalsIgnoreCase(trimmed)) {
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
                "Faol ilovalar topilmadi".equalsIgnoreCase(trimmed)) {
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}