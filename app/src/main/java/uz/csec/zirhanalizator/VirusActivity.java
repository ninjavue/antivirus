package uz.csec.zirhanalizator;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import android.widget.ImageView;

// import uz.zirh.zirhlib.ZirhMilliy;

public class VirusActivity extends AppCompatActivity {

    // ZirhMilliy zirh = new ZirhMilliy();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_virus);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.virus_status_bar));
        }
        WindowInsetsControllerCompat insetsController =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);


        RecyclerView rvVirusTable = findViewById(R.id.rvVirusTable);
        rvVirusTable.setLayoutManager(new LinearLayoutManager(this));
        showVirusTable();
        @SuppressLint({"MissingInflatedId", "LocalSuppress"}) LinearLayout btnBack = findViewById(R.id.backBtnContainer);
        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, 0);
        });
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
    private void showVirusTable() {
        RecyclerView rvVirusTable = findViewById(R.id.rvVirusTable);
        TextView tvTitle = findViewById(R.id.tvVirusTableTitle);
        AntivirusDatabase db = new AntivirusDatabase(this);
        List<VirusTableAdapter.VirusRecord> records = new ArrayList<>();
        android.database.Cursor cursor = db.getReadableDatabase().rawQuery("SELECT id, file_name, file_path, detected_at FROM virus_files ORDER BY detected_at DESC", null);
        while (cursor.moveToNext()) {
            int id = cursor.getInt(0);
            String fileName = cursor.getString(1);
            String filePath = cursor.getString(2);
            String detectedAt = cursor.getString(3);
            records.add(new VirusTableAdapter.VirusRecord(id, fileName, filePath, detectedAt));
        }
        cursor.close();
        if (records.isEmpty()) {
            rvVirusTable.setVisibility(View.GONE);
            tvTitle.setText(getString(R.string.malcuse_files_not_found));
        } else {
            rvVirusTable.setVisibility(View.VISIBLE);
            tvTitle.setText(getString(R.string.malicious_files_detected));
            VirusTableAdapter adapter = new VirusTableAdapter(records, record -> {
                db.getWritableDatabase().delete("virus_files", "id=?", new String[]{String.valueOf(record.id)});
                java.io.File file = new java.io.File(record.filePath);
                if (file.exists()) file.delete();
                showVirusTable();
            });
            rvVirusTable.setAdapter(adapter);
        }
    }
}