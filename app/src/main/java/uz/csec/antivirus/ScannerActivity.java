package uz.csec.antivirus;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class ScannerActivity extends BaseActivity {

    private TextView tvResult;
    private Button btnVisit, btnShare, btnCopy, btnRescan;
    private LinearLayout resultLayout;

    private IntentIntegrator qrScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(ContextCompat.getColor(this, R.color.primary));
        }
        setupBottomNav();


        tvResult = findViewById(R.id.tvResult);
        btnVisit = findViewById(R.id.btnVisit);
        btnShare = findViewById(R.id.btnShare);
        btnCopy = findViewById(R.id.btnCopy);
        btnRescan = findViewById(R.id.btnRescan);

        resultLayout = findViewById(R.id.resultLayout);

        // QR Scanner init
        qrScan = new IntentIntegrator(this);
        qrScan.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        qrScan.setPrompt("QR kodni kameraga yo‘naltiring");
        qrScan.setCameraId(0);
        qrScan.setBeepEnabled(true);
        qrScan.setOrientationLocked(true);
        qrScan.setCaptureActivity(CaptureAct.class);

        qrScan.initiateScan();

        // tugmalar
        btnShare.setOnClickListener(v -> {
            String text = tvResult.getText().toString();
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(shareIntent, "Ulash"));
        });

        btnCopy.setOnClickListener(v -> {
            String text = tvResult.getText().toString();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("QR Result", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Nusxa olindi", Toast.LENGTH_SHORT).show();
        });

        btnVisit.setOnClickListener(v -> {
            String url = tvResult.getText().toString();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        });
        btnRescan.setOnClickListener(v -> {
            qrScan.initiateScan();
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            String scannedText = result.getContents();

            resultLayout.setVisibility(View.VISIBLE);
            tvResult.setText(scannedText);

            // Agar link bo‘lsa "Tashrif buyurish" tugmasi ko‘rinsin
            if (scannedText.startsWith("http://") || scannedText.startsWith("https://")) {
                btnVisit.setVisibility(View.VISIBLE);
            } else {
                btnVisit.setVisibility(View.GONE);
            }
        }
    }
}
