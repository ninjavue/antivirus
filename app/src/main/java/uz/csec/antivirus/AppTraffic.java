package uz.csec.antivirus;

public class AppTraffic {
    private String appName;
    private String packageName;
    private long rxBytes; // Qabul qilingan baytlar
    private long txBytes; // Yuborilgan baytlar

    public AppTraffic(String appName, String packageName, long rxBytes, long txBytes) {
        this.appName = appName;
        this.packageName = packageName;
        this.rxBytes = rxBytes;
        this.txBytes = txBytes;
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public long getRxBytes() {
        return rxBytes;
    }

    public long getTxBytes() {
        return txBytes;
    }

    // Umumiy trafik
    public long getTotalBytes() {
        return rxBytes + txBytes;
    }

    // Baytlarni MB/KB ga aylantirish
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
