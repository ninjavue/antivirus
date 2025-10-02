package uz.csec.zirhanalizator;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.ColorRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

// import uz.zirh.zirhlib.ZirhMilliy;

public abstract class BaseActivity extends AppCompatActivity {

    private LinearLayout home, scan, settings;

    // ZirhMilliy zirh = new ZirhMilliy();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

    protected void setupBottomNav() {
        home     = findViewById(R.id.nav_home);
        scan     = findViewById(R.id.nav_multi);
        settings = findViewById(R.id.nav_settings);

        if (home == null || scan == null || settings == null) return;

        home.setOnClickListener(v -> {
            if (getClass() != MainActivity.class) openNoAnim(MainActivity.class);
        });

        scan.setOnClickListener(v -> {
            if (getClass() != ScannerActivity.class) openNoAnim(ScannerActivity.class);
        });

        settings.setOnClickListener(v -> {
            if (getClass() != SettingsActivity.class) openNoAnim(SettingsActivity.class);
        });

        highlightCurrent();
    }

    private void openNoAnim(Class<?> target) {
        Intent i = new Intent(this, target);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(i);
        overridePendingTransition(0, 0);
        finish();
    }

    private void highlightCurrent() {
        resetNavigationColors();
        if (getClass() == MainActivity.class) {
            select(home);
        } else if (getClass() == ScannerActivity.class) {
            select(scan);
        } else if (getClass() == SettingsActivity.class) {
            select(settings);
        }
    }

    private void select(LinearLayout item) {
        ImageView icon = getFirstIcon(item);
        if (icon != null) icon.setColorFilter(color(R.color.white));
    }

    private void resetNavigationColors() {
        setIconTint(home, R.color.bottom_nav_unselected);
        setIconTint(scan, R.color.bottom_nav_unselected);
        setIconTint(settings, R.color.bottom_nav_unselected);
    }

    private void setIconTint(LinearLayout item, @ColorRes int colorRes) {
        ImageView icon = getFirstIcon(item);
        if (icon != null) icon.setColorFilter(color(colorRes));
    }

    private ImageView getFirstIcon(LinearLayout item) {
        if (item == null || item.getChildCount() == 0) return null;
        View first = item.getChildAt(0);
        return (first instanceof ImageView) ? (ImageView) first : null;
    }

    private int color(@ColorRes int res) {
        return ContextCompat.getColor(this, res);
    }
}
