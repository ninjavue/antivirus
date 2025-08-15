package uz.csec.antivirus;

import android.annotation.SuppressLint;
import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FileMonitorService extends Service {
    private final List<FileObserver> observers = new ArrayList<>();
    private static final String[] MONITOR_DIRS = new String[] {
        "/Download",
        "/Android/data/org.telegram.messenger/files/Telegram/Telegram Files"
    };
    private ScheduledExecutorService scheduler;
    private final Set<String> scannedApks = new HashSet<>();

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        String channelId = "monitor_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId, "File Monitor", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        String storageRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        for (String dir : MONITOR_DIRS) {
            String absPath = storageRoot + dir;
            File folder = new File(absPath);
            if (!folder.exists() || !folder.isDirectory()) {
                continue;
            }
            FileObserver observer = new FileObserver(absPath, FileObserver.CREATE | FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE) {
                @Override
                public void onEvent(int event, @Nullable String path) {
                    if (path != null) {
                        String lower = path.toLowerCase();
                        if (lower.startsWith(".pending-") || lower.endsWith(".part") || lower.endsWith(".partial") || lower.endsWith(".tmp") || lower.endsWith(".crdownload") || lower.startsWith(".")) {
                            return;
                        }

                        String filePath = absPath + "/" + path;
                        ScheduledExecutorService exec = scheduler != null ? scheduler : Executors.newSingleThreadScheduledExecutor();
                        exec.schedule(() -> {
                            try {
                                File file = new File(filePath);
                                if (!file.exists() || !file.isFile()) {
                                    Log.d("FileMonitorService", "Fayl mavjud emas yoki file emas: " + filePath);
                                    return;
                                }

                                // O'lcham barqarorlashishini kutish (3 marotaba tekshiruv)
                                long previous = -1L;
                                for (int i = 0; i < 3; i++) {
                                    long current = file.length();
                                    if (current > 0 && current == previous) break;
                                    previous = current;
                                    try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                                }

                                FileScanHelper.handleNewFile(getApplicationContext(), filePath);
                            } catch (Exception e) {
                                Log.e("FileMonitorService", "Faylni qayta ishlashda xatolik: " + e.getMessage(), e);
                            }
                        }, 1500, TimeUnit.MILLISECONDS);
                    }
                }
            };
            observer.startWatching();
            observers.add(observer);
        }
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                scanTelegramApks();
            } catch (Exception e) {
                Log.e("FileMonitorService", "scanTelegramApks ERROR: " + e.getMessage(), e);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void scanTelegramApks() {
        String telegramDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/org.telegram.messenger/files/Telegram/Telegram Files";
        File folder = new File(telegramDir);
        if (!folder.exists() || !folder.isDirectory()) {
            return;
        }
        long now = System.currentTimeMillis();
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".apk")) {
                long lastModified = file.lastModified();
                if (now - lastModified <= 20_000 && !scannedApks.contains(file.getAbsolutePath())) {
                    scannedApks.add(file.getAbsolutePath());
                    try {
                        FileScanHelper.scanAndHandleApp(getApplicationContext(), null, file.getAbsolutePath());
                    } catch (Exception e) {
                        Log.e("FileMonitorService", "scanTelegramApks: scanAndHandleApp ERROR: " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (FileObserver observer : observers) observer.stopWatching();
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String channelId = "monitor_channel";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Antivirus")
                .setContentText("Fayl monitoring faol")
                .setSmallIcon(R.drawable.ic_antivirus)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);

        Notification notification = builder.build();
        
        startForeground(1, notification);
        
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 