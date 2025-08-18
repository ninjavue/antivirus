package uz.csec.antivirus;

import android.graphics.drawable.Drawable;
import java.util.List;

public class AppCardItem {
    public Drawable appIcon;
    public String appName;
    public String packageName;
    public List<PermissionItem> grantedPermissions;
    public List<PermissionItem> requestedPermissions;
    public boolean isProtected;

    public AppCardItem(Drawable appIcon, String appName, String packageName, 
                      List<PermissionItem> grantedPermissions, 
                      List<PermissionItem> requestedPermissions, 
                      boolean isProtected) {
        this.appIcon = appIcon;
        this.appName = appName;
        this.packageName = packageName;
        this.grantedPermissions = grantedPermissions;
        this.requestedPermissions = requestedPermissions;
        this.isProtected = isProtected;
    }
} 