package uz.csec.antivirus;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import kotlin.io.LineReader;

public class BatteryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        WindowInsetsControllerCompat insetsController =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);
        setContentView(R.layout.activity_battery);

        @SuppressLint({"MissingInflatedId", "LocalSuppress"}) LinearLayout btnBack = findViewById(R.id.back);
        @SuppressLint({"MissingInflatedId", "LocalSuppress"}) TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvRemainingTime = findViewById(R.id.tvRemainingTime);
        TextView tvPowerSavingMode = findViewById(R.id.tvPowerSavingMode);
        TextView tvUltraPowerSavingMode = findViewById(R.id.tvUltraPowerSavingMode);

        View.OnClickListener backListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(BatteryActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
                overridePendingTransition(0, 0);
            }
        };
        btnBack.setOnClickListener(backListener);
        tvTitle.setOnClickListener(backListener);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        int batteryPercent = getBatteryPercent();
        uz.csec.antivirus.BatteryCircularProgressView progressView = findViewById(R.id.batteryProgressView);
        TextView tvPercent = findViewById(R.id.tvBatteryPercent);
        progressView.animateProgress(batteryPercent / 100f);
        animatePercentText(tvPercent, 0, batteryPercent, "%d%%", 1200);

        // Update remaining time and power saving modes
        tvRemainingTime.setText(String.format("%d Soat %d daqiqa", 2, 34));
        tvPowerSavingMode.setText("Faol energiya tejash rejimi");
        tvUltraPowerSavingMode.setText("Faol ultra energiya tejash rejimi");
    }

    private int getBatteryPercent() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int level = 0, scale = 100;
        if (batteryStatus != null) {
            level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        }
        if (scale == 0) return 0;
        return (int) (level * 100 / (float) scale);
    }

    private void animatePercentText(TextView textView, int start, int end, String format, int duration) {
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(start, end);
        animator.setDuration(duration);
        animator.addUpdateListener(animation -> {
            int value = (int) animation.getAnimatedValue();
            textView.setText(String.format(format, value));
        });
        animator.start();
    }
}