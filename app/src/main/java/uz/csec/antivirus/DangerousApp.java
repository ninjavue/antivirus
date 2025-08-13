package uz.csec.antivirus;
import android.graphics.drawable.Drawable;

public class DangerousApp {
    public String appName;
    public String packageName;
    public Drawable icon;
    public String reason;

    public DangerousApp(String appName, String packageName, Drawable icon, String reason) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.reason = reason;
    }
} 