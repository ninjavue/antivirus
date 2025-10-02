package uz.csec.zirhanalizator;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.app.usage.NetworkStats.Bucket;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class TrafficActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private TrafficAdapter adapter;
    private List<AppTraffic> trafficList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupBottomNav();
        setContentView(R.layout.activity_traffic);

        recyclerView = findViewById(R.id.recyclerViewTraffic);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TrafficAdapter(trafficList);
        recyclerView.setAdapter(adapter);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            loadAppTrafficData();
        } else {
            Toast.makeText(this, "Traffic info requires Android 6.0+", Toast.LENGTH_LONG).show();
        }
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
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void loadAppTrafficData() {
        try {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);

            NetworkStatsManager networkStatsManager =
                    (NetworkStatsManager) getSystemService(Context.NETWORK_STATS_SERVICE);

            for (ApplicationInfo app : apps) {
                int uid = app.uid;
                long rxBytes = 0, txBytes = 0;

                NetworkStats mobileStats = networkStatsManager.queryDetailsForUid(
                        ConnectivityManager.TYPE_MOBILE,
                        null,
                        0,
                        System.currentTimeMillis(),
                        uid
                );
                Bucket bucket = new Bucket();
                while (mobileStats.hasNextBucket()) {
                    mobileStats.getNextBucket(bucket);
                    rxBytes += bucket.getRxBytes();
                    txBytes += bucket.getTxBytes();
                }
                mobileStats.close();

                NetworkStats wifiStats = networkStatsManager.queryDetailsForUid(
                        ConnectivityManager.TYPE_WIFI,
                        null,
                        0,
                        System.currentTimeMillis(),
                        uid
                );
                while (wifiStats.hasNextBucket()) {
                    wifiStats.getNextBucket(bucket);
                    rxBytes += bucket.getRxBytes();
                    txBytes += bucket.getTxBytes();
                }
                wifiStats.close();

                if (rxBytes > 0 || txBytes > 0) {
                    AppTraffic appTraffic = new AppTraffic(
                            app.loadLabel(pm).toString(),
                            app.packageName,
                            rxBytes,
                            txBytes
                    );
                    trafficList.add(appTraffic);
                }
            }

            adapter.notifyDataSetChanged();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
