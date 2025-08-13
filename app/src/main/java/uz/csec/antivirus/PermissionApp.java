package uz.csec.antivirus;
import android.graphics.drawable.Drawable;
import java.util.List;

public class PermissionApp {
    public String appName;
    public String packageName;
    public Drawable icon;
    public List<String> permissions;

    public PermissionApp(String appName, String packageName, Drawable icon, List<String> permissions) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.permissions = permissions;
    }
} 