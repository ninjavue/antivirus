package uz.csec.antivirus;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

public class DataActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1001;
    NativeLib nativeLib = new NativeLib();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_data);

        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission();
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, PERMISSION_REQUEST_CODE);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BATTERY_STATS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BATTERY_STATS}, PERMISSION_REQUEST_CODE);
        }

        ((TextView)findViewById(R.id.tv_running_apps)).setText(nativeLib.getRunningApps(this));
        ((TextView)findViewById(R.id.tv_unused_apps)).setText(nativeLib.getUnusedApps(this));
        ((TextView)findViewById(R.id.tv_cpu_usage)).setText(nativeLib.getAppCpuUsage());
        try {
            ((TextView)findViewById(R.id.tv_uptime)).setText(nativeLib.getDeviceUptime(this));
        } catch (Exception e) {
            ((TextView)findViewById(R.id.tv_uptime)).setText("Uptime xatolik: " + e.getMessage());
            e.printStackTrace();
        }
        ((TextView)findViewById(R.id.tv_battery_usage)).setText(nativeLib.getAppBatteryUsage(this));
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