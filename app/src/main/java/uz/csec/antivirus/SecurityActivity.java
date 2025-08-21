package uz.csec.antivirus;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.util.ArrayList;
import java.util.List;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.drawable.Drawable;

import kotlin.io.LineReader;

public class SecurityActivity extends AppCompatActivity {
    static {
        System.loadLibrary("antivirus");
    }

    private RecyclerView rvDangerousApps, rvSafeApps;
    private AppCardAdapter dangerousAppsAdapter;
    private AppCardAdapter safeAppsAdapter;
    private LinearLayout dangerousAppsSection, safeAppsSection;
    private Button btnDangerousApps, btnSafeApps;
    private List<AppCardItem> dangerousAppsList, safeAppsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowInsetsControllerCompat insetsController =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);
        setContentView(R.layout.activity_security);

        rvDangerousApps = findViewById(R.id.rv_dangerous_apps);
        rvSafeApps = findViewById(R.id.rv_safe_apps);
        dangerousAppsSection = findViewById(R.id.dangerous_apps_section);
        safeAppsSection = findViewById(R.id.safe_apps_section);
        btnDangerousApps = findViewById(R.id.btn_dangerous_apps);
        btnSafeApps = findViewById(R.id.btn_safe_apps);
        LinearLayout btnBack = findViewById(R.id.security_header);
        TextView tv_security_title = findViewById(R.id.tv_security_title);
        btnBack.setOnClickListener(v -> finish());
        tv_security_title.setOnClickListener(v -> finish());

        dangerousAppsAdapter = new AppCardAdapter(this);
        safeAppsAdapter = new AppCardAdapter(this);
        
        rvDangerousApps.setLayoutManager(new LinearLayoutManager(this));
        rvDangerousApps.setAdapter(dangerousAppsAdapter);
        rvSafeApps.setLayoutManager(new LinearLayoutManager(this));
        rvSafeApps.setAdapter(safeAppsAdapter);

        // Set up button click listeners
        btnDangerousApps.setOnClickListener(v -> showDangerousApps());
        btnSafeApps.setOnClickListener(v -> showSafeApps());

        // Load app data
        loadAppData();
        
        // Show dangerous apps by default
        showDangerousApps();
        
        // Set initial button colors
        btnDangerousApps.setTextColor(getResources().getColor(android.R.color.white));
        btnSafeApps.setTextColor(getResources().getColor(android.R.color.black));
    }

    private void showDangerousApps() {
        dangerousAppsSection.setVisibility(View.VISIBLE);
        safeAppsSection.setVisibility(View.GONE);
        
        // Update button styles
        btnDangerousApps.setBackgroundResource(R.drawable.bg_security_btn);
        btnDangerousApps.setTextColor(getResources().getColor(android.R.color.white));
        btnSafeApps.setBackgroundResource(R.drawable.btn_optimize_disabled_bg);
        btnSafeApps.setTextColor(getResources().getColor(android.R.color.black));
        
        // Show message if no dangerous apps
        if (dangerousAppsList.isEmpty()) {
            showNoAppsMessage(dangerousAppsSection, "Xavfli ilovalar topilmadi");
        }
    }

    private void showSafeApps() {
        dangerousAppsSection.setVisibility(View.GONE);
        safeAppsSection.setVisibility(View.VISIBLE);
        
        // Update button styles
        btnDangerousApps.setBackgroundResource(R.drawable.btn_optimize_disabled_bg);
        btnDangerousApps.setTextColor(getResources().getColor(android.R.color.black));
        btnSafeApps.setBackgroundResource(R.drawable.bg_security_btn);
        btnSafeApps.setTextColor(getResources().getColor(android.R.color.white));
        
        // Show message if no safe apps
        if (safeAppsList.isEmpty()) {
            showNoAppsMessage(safeAppsSection, "Xavfsiz ilovalar topilmadi");
        }
    }

    private void showNoAppsMessage(LinearLayout container, String message) {
        // Remove existing views
        container.removeAllViews();
        
        // Add message TextView
        TextView messageView = new TextView(this);
        messageView.setText(message);
        messageView.setTextSize(16); // Changed from 16sp to 16
        messageView.setTextColor(getResources().getColor(android.R.color.darker_gray));
        messageView.setGravity(android.view.Gravity.CENTER);
        messageView.setPadding(32, 64, 32, 64);
        
        container.addView(messageView);
    }

    private void loadAppData() {
        List<AppCardItem> userApps = getUserInstalledApps();
        dangerousAppsList = new ArrayList<>();
        safeAppsList = new ArrayList<>();
        
        for (AppCardItem app : userApps) {
            if (hasVirusLikePermissions(app)) {
                dangerousAppsList.add(app);
            } else {
                safeAppsList.add(app);
            }
        }
        
        dangerousAppsAdapter.submitList(dangerousAppsList);
        safeAppsAdapter.submitList(safeAppsList);
    }

    private boolean hasVirusLikePermissions(AppCardItem app) {
        // Check if app has dangerous permissions that are typical for malware
        for (PermissionItem permission : app.grantedPermissions) {
            if (isVirusLikePermission(permission.name)) {
                return true;
            }
        }
        return false;
    }

    private boolean isVirusLikePermission(String permissionName) {
        String[] virusLikePermissions = {
            "SMS o'qish",
            "SMS yuborish", 
            "Telefon qilish",
            "Qo'ng'iroqlar tarixini o'qish",
            "Qo'ng'iroqlar tarixini yozish",
            "Oynalar ustida ko'rsatish",
            "Tana sensorlari",
            "Media joylashuv"
        };
        
        for (String virusPerm : virusLikePermissions) {
            if (permissionName.equals(virusPerm)) {
                return true;
            }
        }
        return false;
    }

    private List<AppCardItem> getUserInstalledApps() {
        List<AppCardItem> apps = new ArrayList<>();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        
        String myPackage = getPackageName();
        
        for (ApplicationInfo appInfo : installedApps) {
            // Skip system apps, updated system apps, and our own app
            if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || 
                (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 ||
                appInfo.packageName.equals(myPackage) ||
                appInfo.packageName.startsWith("com.android.") ||
                appInfo.packageName.startsWith("android.") ||
                appInfo.packageName.startsWith("com.google.android.") ||
                appInfo.packageName.startsWith("com.samsung.") ||
                appInfo.packageName.startsWith("com.sec.") ||
                appInfo.packageName.startsWith("com.wssyncmldm") ||
                appInfo.packageName.startsWith("com.sec.android.") ||
                appInfo.packageName.equals("com.android.settings") ||
                appInfo.packageName.equals("com.android.vending") ||
                appInfo.packageName.equals("com.google.android.gms") ||
                appInfo.packageName.equals("com.google.android.gsf")) {
                continue;
            }
            
            AppCardItem app = createAppCardItem(appInfo.packageName);
            if (app != null) {
                apps.add(app);
            }
        }
        
        return apps;
    }

    private AppCardItem createAppCardItem(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            
            Drawable icon = pm.getApplicationIcon(appInfo);
            String appName = pm.getApplicationLabel(appInfo).toString();
            
            List<PermissionItem> grantedPermissions = getGrantedPermissions(packageInfo, pm);
            List<PermissionItem> nonGrantedPermissions = getNonGrantedPermissions(packageInfo, pm);
            
            return new AppCardItem(icon, appName, packageName, grantedPermissions, nonGrantedPermissions, false);
            
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private List<PermissionItem> getGrantedPermissions(PackageInfo packageInfo, PackageManager pm) {
        List<PermissionItem> permissions = new ArrayList<>();
        
        if (packageInfo.requestedPermissions != null) {
            for (String permission : packageInfo.requestedPermissions) {
                int permissionStatus = pm.checkPermission(permission, packageInfo.packageName);
                if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
                    // Only add dangerous permissions, skip normal permissions
                    if (isDangerousPermission(permission)) {
                        permissions.add(new PermissionItem(getPermissionDisplayName(permission), true, true));
                    }
                }
            }
        }
        
        return permissions;
    }

    private List<PermissionItem> getNonGrantedPermissions(PackageInfo packageInfo, PackageManager pm) {
        List<PermissionItem> permissions = new ArrayList<>();
        
        if (packageInfo.requestedPermissions != null) {
            for (String permission : packageInfo.requestedPermissions) {
                int permissionStatus = pm.checkPermission(permission, packageInfo.packageName);
                if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                    // Only add dangerous permissions, skip normal permissions
                    if (isDangerousPermission(permission)) {
                        permissions.add(new PermissionItem(getPermissionDisplayName(permission), true, false));
                    }
                }
            }
        }
        
        return permissions;
    }

    private boolean isDangerousPermission(String permission) {
        String[] dangerousPermissions = {
            "android.permission.CAMERA",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS", 
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.READ_PHONE_STATE",
            "android.permission.CALL_PHONE",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
            "android.permission.BODY_SENSORS",
            "android.permission.ACCESS_MEDIA_LOCATION"
        };
        
        for (String dangerous : dangerousPermissions) {
            if (dangerous.equals(permission)) {
                return true;
            }
        }
        return false;
    }

    private String getPermissionDisplayName(String permission) {
        switch (permission) {
            case "android.permission.CAMERA": return "Kamera";
            case "android.permission.READ_CONTACTS": return "Kontaktlarni o'qish";
            case "android.permission.WRITE_CONTACTS": return "Kontaktlarni yozish";
            case "android.permission.ACCESS_FINE_LOCATION": return "Aniq joylashuv";
            case "android.permission.ACCESS_COARSE_LOCATION": return "Taxminiy joylashuv";
            case "android.permission.RECORD_AUDIO": return "Ovozni yozish";
            case "android.permission.READ_SMS": return "SMS o'qish";
            case "android.permission.SEND_SMS": return "SMS yuborish";
            case "android.permission.READ_PHONE_STATE": return "Telefon holatini o'qish";
            case "android.permission.CALL_PHONE": return "Telefon qilish";
            case "android.permission.READ_CALL_LOG": return "Qo'ng'iroqlar tarixini o'qish";
            case "android.permission.WRITE_CALL_LOG": return "Qo'ng'iroqlar tarixini yozish";
            case "android.permission.READ_EXTERNAL_STORAGE": return "Xotira o'qish";
            case "android.permission.WRITE_EXTERNAL_STORAGE": return "Xotira yozish";
            case "android.permission.SYSTEM_ALERT_WINDOW": return "Oynalar ustida ko'rsatish";
            case "android.permission.READ_CALENDAR": return "Kalendarni o'qish";
            case "android.permission.WRITE_CALENDAR": return "Kalendarni yozish";
            case "android.permission.BODY_SENSORS": return "Tana sensorlari";
            case "android.permission.ACCESS_MEDIA_LOCATION": return "Media joylashuv";
            default: return permission;
        }
    }
}