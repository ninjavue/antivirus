package uz.csec.antivirus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.util.Log;

public class AppInstallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
            String packageName = intent.getData().getSchemeSpecificPart();
            try {
                ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
                String apkPath = info.sourceDir;
        
                FileScanHelper.scanAndHandleApp(context, packageName, apkPath);
            } catch (Exception e) {
                Log.e("AppInstallReceiver", "Xatolik: ", e);
            }
        }
    }
} 