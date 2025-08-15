package uz.csec.antivirus;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScannerActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private PreviewView viewFinder;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private LinearLayout scanResultDialog;
    private TextView tvScanResult;
    private String lastScannedResult = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);

        // Initialize views
        viewFinder = findViewById(R.id.viewFinder);
        scanResultDialog = findViewById(R.id.scanResultDialog);
        tvScanResult = findViewById(R.id.tvScanResult);

        // Setup buttons
        setupButtons();

        // Initialize barcode scanner
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void setupButtons() {
        // Back button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Flash button
        findViewById(R.id.btnFlash).setOnClickListener(v -> toggleFlash());

        // Result dialog buttons
        findViewById(R.id.btnCopy).setOnClickListener(v -> copyToClipboard());
        findViewById(R.id.btnShare).setOnClickListener(v -> shareResult());
        findViewById(R.id.btnScanAgain).setOnClickListener(v -> hideResultDialog());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Preview use case
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                // Image analysis use case
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new BarcodeAnalyzer());

                // Camera selector
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Unbind any bound use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Kamera ochilmadi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private class BarcodeAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

            barcodeScanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        for (Barcode barcode : barcodes) {
                            if (barcode.getRawValue() != null && !barcode.getRawValue().equals(lastScannedResult)) {
                                lastScannedResult = barcode.getRawValue();
                                showScanResult(lastScannedResult);
                                break;
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        // Handle any errors
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        }
    }

    private void showScanResult(String result) {
        runOnUiThread(() -> {
            tvScanResult.setText(result);
            scanResultDialog.setVisibility(View.VISIBLE);
        });
    }

    private void hideResultDialog() {
        scanResultDialog.setVisibility(View.GONE);
        lastScannedResult = "";
    }

    private void copyToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("QR Code Result", lastScannedResult);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Nusxalandi", Toast.LENGTH_SHORT).show();
    }

    private void shareResult() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, lastScannedResult);
        startActivity(Intent.createChooser(shareIntent, "Ulashish"));
    }

    private void toggleFlash() {
        // Flash toggle functionality can be implemented here
        Toast.makeText(this, "Flesh funksiyasi", Toast.LENGTH_SHORT).show();
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Kamera ruxsati kerak", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
    }
}