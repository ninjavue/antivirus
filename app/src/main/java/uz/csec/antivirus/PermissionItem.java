package uz.csec.antivirus;

public class PermissionItem {
    public String name;
    public boolean isDangerous;
    public boolean isGranted;

    public PermissionItem(String name, boolean isDangerous, boolean isGranted) {
        this.name = name;
        this.isDangerous = isDangerous;
        this.isGranted = isGranted;
    }
} 