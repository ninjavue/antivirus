package uz.csec.antivirus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class AntivirusService extends Service {
    private static final String TAG = "AntivirusService";
    private static final String CHANNEL_ID = "antivirus_channel";
    private AppInstallReceiver appInstallReceiver;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AntivirusService created");

        // BroadcastReceiver ni dinamik ro'yxatdan o'tkazish
        appInstallReceiver = new AppInstallReceiver();
        IntentFilter filter = new IntentFilter("android.intent.action.PACKAGE_ADDED");
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(appInstallReceiver, filter, RECEIVER_EXPORTED);
        } else {
            registerReceiver(appInstallReceiver, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "AntivirusService started");
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Antivirus ishlamoqda")
                .setContentText("Yangi ilovalarni virusga tekshirish")
                .setSmallIcon(R.drawable.ic_antivirus)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(false)
                .build();

        startForeground(1, notification);
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Antivirus Service",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Yangi ilovalarni virusga tekshirish uchun xizmat");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            } else {
                Log.e(TAG, "NotificationManager is null");
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "AntivirusService destroyed");
        if (appInstallReceiver != null) {
            unregisterReceiver(appInstallReceiver);
        }
    }
}