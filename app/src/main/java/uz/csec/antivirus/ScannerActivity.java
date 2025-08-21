package uz.csec.antivirus;

import android.os.Bundle;


public class ScannerActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);
        setupBottomNav();
        

    }

}