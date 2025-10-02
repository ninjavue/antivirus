package uz.csec.zirhanalizator;

import android.os.Bundle;
import android.os.Build;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Locale;

public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Status bar ranglarini sozlash
        WindowInsetsControllerCompat insetsController =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);

        setContentView(R.layout.activity_settings);

        setupBottomNav();
        setupAppInfo();
        setupLanguageSelector();
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
    }

    private void setupAppInfo() {
        ImageView iconView = findViewById(R.id.image_app_icon);
        TextView appNameView = findViewById(R.id.text_app_name);
        TextView packageNameView = findViewById(R.id.text_package_name);
        TextView versionView = findViewById(R.id.text_version);

        try {
            ApplicationInfo applicationInfo = getApplicationInfo();
            PackageManager packageManager = getPackageManager();

            Drawable appIcon = packageManager.getApplicationIcon(applicationInfo);
            CharSequence appLabel = packageManager.getApplicationLabel(applicationInfo);

            String packageName = getPackageName();

            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= 33) {
                packageInfo = packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
            } else {
                packageInfo = packageManager.getPackageInfo(packageName, 0);
            }
            String versionName = packageInfo.versionName;

            if (iconView != null) iconView.setImageDrawable(appIcon);
            if (appNameView != null) appNameView.setText(appLabel);
            if (packageNameView != null) packageNameView.setText(packageName);
            if (versionView != null) versionView.setText(getString(R.string.version_x, versionName));
        } catch (Exception ignored) { }
    }

    private void setupLanguageSelector() {
        RadioGroup group = findViewById(R.id.radio_group_language);
        RadioButton uz = findViewById(R.id.radio_uz);
        RadioButton ru = findViewById(R.id.radio_ru);
        RadioButton en = findViewById(R.id.radio_en);

        if (group == null || uz == null || ru == null || en == null) return;

        // Joriy tilni aniqlash
        LocaleListCompat current = AppCompatDelegate.getApplicationLocales();
        String tag = current.isEmpty() ? Locale.getDefault().getLanguage() : current.toLanguageTags();

        if (tag.startsWith("uz")) {
            uz.setChecked(true);
        } else if (tag.startsWith("ru")) {
            ru.setChecked(true);
        } else {
            en.setChecked(true);
        }

        // Til tanlanganda ishlovchi listener
        group.setOnCheckedChangeListener((g, checkedId) -> {
            String langTag = "en";
            if (checkedId == R.id.radio_uz) langTag = "uz";
            else if (checkedId == R.id.radio_ru) langTag = "ru";
            else if (checkedId == R.id.radio_en) langTag = "en";

            LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(langTag);
            AppCompatDelegate.setApplicationLocales(appLocale);

            // qayta ochish — animatsiyasiz
            overridePendingTransition(0, 0);
            recreate();
            overridePendingTransition(0, 0);
        });
    }
}
