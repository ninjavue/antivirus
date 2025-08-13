package uz.csec.antivirus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

public class DangerDialogActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("DangerDialogActivity", "onCreate: started");

        String appName = getIntent().getStringExtra("appName");
        String reasons = getIntent().getStringExtra("reasons");
        String pkg = getIntent().getStringExtra("packageName");
        if (appName == null) appName = "Noma'lum ilova";
        if (reasons == null) reasons = "Sabablar topilmadi";
        if (pkg == null) pkg = "";
        final String packageName = pkg;

        Log.d("DangerDialogActivity", "appName=" + appName + ", reasons=" + reasons + ", packageName=" + packageName);

        new AlertDialog.Builder(this)
            .setTitle(appName + " xavfli ilova!")
            .setMessage(reasons)
            .setCancelable(false)
            .setPositiveButton("O‘chirish", (dialog, which) -> {
                Intent intent = new Intent(Intent.ACTION_DELETE);
                intent.setData(android.net.Uri.parse("package:" + packageName));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            })
            .setNegativeButton("Yopish", (dialog, which) -> {
                dialog.dismiss();
                finish();
            })
            .show();
    }
}