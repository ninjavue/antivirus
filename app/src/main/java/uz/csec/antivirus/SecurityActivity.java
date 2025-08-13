package uz.csec.antivirus;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import android.location.LocationManager;
import android.provider.Settings;
import android.widget.Toast;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class SecurityActivity extends AppCompatActivity {
    static {
        System.loadLibrary("antivirus");
    }

    private RecyclerView rvDangerousApps, rvPermissions;
    private TextView tvRootStatus, tvWifiSecurity;
    private Button btnCheckSecurity;
    private DangerousAppsAdapter dangerousAppsAdapter;
    private PermissionsAdapter permissionsAdapter;
    private static final int REQ_CODE_LOCATION = 101;
    private boolean pendingWifiCheck = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security);

        rvDangerousApps = findViewById(R.id.rv_dangerous_apps);
        rvPermissions = findViewById(R.id.rv_permissions);
        tvRootStatus = findViewById(R.id.tv_root_status);
        tvWifiSecurity = findViewById(R.id.tv_wifi_security);
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        dangerousAppsAdapter = new DangerousAppsAdapter();
        permissionsAdapter = new PermissionsAdapter();
        rvDangerousApps.setLayoutManager(new LinearLayoutManager(this));
        rvDangerousApps.setAdapter(dangerousAppsAdapter);
        rvPermissions.setLayoutManager(new LinearLayoutManager(this));
        rvPermissions.setAdapter(permissionsAdapter);

        // Show all info immediately
        updateSecurityInfo();
    }

    private void updateSecurityInfo() {
        dangerousAppsAdapter.submitList(getDangerousAppsListModel(SecurityActivity.this));
        permissionsAdapter.submitList(getPermissionAppsListModel(SecurityActivity.this));
        tvRootStatus.setText(getRootStatus());
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingWifiCheck = true;
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQ_CODE_LOCATION);
        } else {
            showWifiInfo();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (pendingWifiCheck) {
                    showWifiInfo();
                    pendingWifiCheck = false;
                }
            } else {
                tvWifiSecurity.setText("Joylashuv ruxsati berilmadi, Wi-Fi ma'lumotlari olinmaydi");
            }
        }
    }

    private void showWifiInfo() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!isLocationEnabled) {
            Toast.makeText(this, "Wi-Fi ma'lumotlari uchun joylashuvni yoqing!", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            tvWifiSecurity.setText("Joylashuv yoqilmagan!");
        } else {
            tvWifiSecurity.setText(getWifiSecurity());
        }
    }

    public native String getDangerousApps();
    public native String getPermissionsControl();
    public native String getRootStatus();
    public native String getWifiSecurity();

    public static java.util.List<String> getUserInstalledPackages(android.content.Context context) {
        android.content.pm.PackageManager pm = context.getPackageManager();
        java.util.List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(0);
        java.util.List<String> userPackages = new java.util.ArrayList<>();
        for (android.content.pm.ApplicationInfo app : apps) {
            if ((app.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 && (app.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) {
                userPackages.add(app.packageName);
            }
        }
        return userPackages;
    }

    public static String getDangerousAppsList(android.content.Context context) {
        StringBuilder sb = new StringBuilder();
        android.content.pm.PackageManager pm = context.getPackageManager();
        String[] dangerousPermissions = new String[] {
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.WRITE_CONTACTS,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.SYSTEM_ALERT_WINDOW
        };
        java.util.List<String> userPackages = getUserInstalledPackages(context);
        for (String packageName : userPackages) {
            try {
                android.content.pm.PackageInfo pkgInfo = pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_PERMISSIONS);
                String[] perms = pkgInfo.requestedPermissions;
                if (perms != null) {
                    for (String perm : dangerousPermissions) {
                        for (String p : perms) {
                            if (perm.equals(p)) {
                                sb.append(pm.getApplicationLabel(pkgInfo.applicationInfo)).append(" (" + packageName + ")\n");
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return sb.length() == 0 ? "Virus yoki shubhali ilovalar topilmadi" : sb.toString();
    }

    public static String getAppsWithSensitivePermissions(android.content.Context context) {
        StringBuilder sb = new StringBuilder();
        android.content.pm.PackageManager pm = context.getPackageManager();
        String[] sensitive = new String[] {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        };
        java.util.List<String> userPackages = getUserInstalledPackages(context);
        for (String packageName : userPackages) {
            try {
                android.content.pm.ApplicationInfo app = pm.getApplicationInfo(packageName, 0);
                boolean found = false;
                for (String perm : sensitive) {
                    int granted = pm.checkPermission(perm, packageName);
                    if (granted == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        sb.append(pm.getApplicationLabel(app)).append(" (" + packageName + ")\n");
                        found = true;
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
        return sb.length() == 0 ? "Kamera, mikrofon yoki joylashuvga ruxsat so‘ragan ilovalar topilmadi" : sb.toString();
    }

    public static String getRootStatusJava() {
        String[] paths = {"/system/xbin/su", "/system/bin/su", "/system/app/Superuser.apk"};
        for (String path : paths) {
            if (new java.io.File(path).exists()) return "Qurilma root qilingan";
        }
        String buildTags = android.os.Build.TAGS;
        if (buildTags != null && buildTags.contains("test-keys")) return "Qurilma root qilingan";
        return "Qurilma root qilinmagan";
    }

    public static String getWifiSecurityJava(android.content.Context context) {
        try {
            android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager) context.getSystemService(android.content.Context.WIFI_SERVICE);
            if (wifiManager == null || !wifiManager.isWifiEnabled()) return "Wi-Fi o'chirilgan";
            android.net.wifi.WifiInfo info = wifiManager.getConnectionInfo();
            String ssid = info.getSSID();
            int networkId = info.getNetworkId();
            if (networkId == -1 || ssid == null || ssid.equals("<unknown ssid>")) {
                return "Wi-Fi ulanmagan yoki joylashuv ruxsati yo'q";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("SSID: ").append(ssid).append("\n");
            sb.append("BSSID: ").append(info.getBSSID()).append("\n");
            sb.append("IP: ").append(android.text.format.Formatter.formatIpAddress(info.getIpAddress())).append("\n");
            return sb.toString();
        } catch (Exception e) {
            return "Wi-Fi ma'lumotlarini olishda xatolik";
        }
    }

    public static List<DangerousApp> getDangerousAppsListModel(android.content.Context context) {
        List<DangerousApp> result = new ArrayList<>();
        android.content.pm.PackageManager pm = context.getPackageManager();
        String[] dangerousPermissions = new String[] {
            android.Manifest.permission.READ_SMS,
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.WRITE_CONTACTS,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.SYSTEM_ALERT_WINDOW
        };
        List<String> userPackages = getUserInstalledPackages(context);
        String myPackage = context.getPackageName();
        Set<String> added = new HashSet<>();
        for (String packageName : userPackages) {
            if (packageName.equals(myPackage)) continue;
            try {
                android.content.pm.PackageInfo pkgInfo = pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_PERMISSIONS);
                String[] perms = pkgInfo.requestedPermissions;
                if (perms != null) {
                    for (String perm : dangerousPermissions) {
                        for (String p : perms) {
                            if (perm.equals(p)) {
                                if (!added.contains(packageName)) {
                                    String reason = getPermissionLabel(context, perm);
                                    result.add(new DangerousApp(
                                            pm.getApplicationLabel(pkgInfo.applicationInfo).toString(),
                                            packageName,
                                            pm.getApplicationIcon(pkgInfo.applicationInfo),
                                            reason + " ruxsat so‘ralgan"
                                    ));
                                    added.add(packageName);
                                }
                                break;
                            }
                        }
                        if (added.contains(packageName)) break;
                    }
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    public static List<PermissionApp> getPermissionAppsListModel(android.content.Context context) {
        List<PermissionApp> result = new ArrayList<>();
        android.content.pm.PackageManager pm = context.getPackageManager();
        String[] sensitive = new String[] {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        };
        List<String> userPackages = getUserInstalledPackages(context);
        String myPackage = context.getPackageName();
        for (String packageName : userPackages) {
            if (packageName.equals(myPackage)) continue;
            try {
                android.content.pm.PackageInfo pkgInfo = pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_PERMISSIONS);
                String[] perms = pkgInfo.requestedPermissions;
                List<String> granted = new ArrayList<>();
                if (perms != null) {
                    for (String perm : sensitive) {
                        for (String p : perms) {
                            if (perm.equals(p)) {
                                granted.add(getPermissionLabel(context, perm));
                                break;
                            }
                        }
                    }
                }
                if (!granted.isEmpty()) {
                    result.add(new PermissionApp(
                            pm.getApplicationLabel(pkgInfo.applicationInfo).toString(),
                            packageName,
                            pm.getApplicationIcon(pkgInfo.applicationInfo),
                            granted
                    ));
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    public static String getPermissionLabel(android.content.Context context, String permission) {
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.pm.PermissionInfo info = pm.getPermissionInfo(permission, 0);
            CharSequence label = info.loadLabel(pm);
            return label != null ? label.toString() : permission;
        } catch (Exception e) {
            return permission;
        }
    }
}