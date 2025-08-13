package uz.csec.antivirus;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import android.util.Log;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.MediaStore;
import android.os.Handler;
import android.os.Looper;

public class TelegramDownloadMonitorService extends Service {
    private final List<FileObserver> observers = new ArrayList<>();
    private final List<ContentObserver> contentObservers = new ArrayList<>();
    private ScheduledExecutorService scheduler;

    private static final String[] TELEGRAM_DIRS = new String[] {
        "/Android/data/org.telegram.messenger/files/Telegram/Telegram Files",
        "/Android/data/org.telegram.messenger/files/Telegram/Telegram Files",
        "/Android/media/org.telegram.messenger/Telegram/Telegram Files",
        "/Android/data/org.telegram.plus/files/Telegram/Telegram Files",
        "/Android/media/org.telegram.plus/Telegram/Telegram Files",
        "/Download/Telegram",
        "/Telegram",
        "/Telegram/Telegram Documents",
        "/Telegram/Telegram Video",
        "/Telegram/Telegram Audio",
        "/Telegram/Telegram Images",
        "/Telegram/Telegram Files",
        "/DCIM/Telegram",
        "/Pictures/Telegram",
        "/Documents/Telegram"
    };
    
    private static final String[] STORAGE_ROOTS = new String[] {
        "/storage/emulated/0",
        "/sdcard",
        "/storage/sdcard0",
        "/storage/sdcard1",
        "/mnt/sdcard",
        "/mnt/sdcard/external_sdcard",
        "/mnt/extSdCard"
    };

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        String channelId = "tg_monitor_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId, "Telegram Monitor", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
        Notification notification = new NotificationCompat.Builder(this, channelId)
            .setContentTitle("Telegram fayllari kuzatilyapti")
            .setSmallIcon(R.drawable.ic_antivirus)
            .setOngoing(true)
            .build();
        startForeground(2025, notification);

        scheduler = Executors.newSingleThreadScheduledExecutor();

        // Try multiple storage roots
        for (String storageRoot : STORAGE_ROOTS) {
            Log.d("TGMonitorService", "Trying storage root: " + storageRoot);
            File rootDir = new File(storageRoot);
            if (rootDir.exists() && rootDir.canRead()) {
                Log.d("TGMonitorService", "Found accessible storage: " + storageRoot);
                setupMonitoring(storageRoot);
                break;
            }
        }
        
        // Test storage access
        testStorageAccess();
        
        // Also check for Telegram folder directly in root
        String rootStorage = Environment.getExternalStorageDirectory().getAbsolutePath();
        File telegramRoot = new File(rootStorage + "/Telegram");
        if (telegramRoot.exists() && telegramRoot.isDirectory()) {
            Log.d("TGMonitorService", "Found Telegram root folder, setting up monitoring");
            setupMonitoring(rootStorage);
            
            // Also monitor subdirectories inside Telegram folder
            setupTelegramSubdirMonitoring(telegramRoot);
        }
        
        // Also check for Telegram folder in app data
        File telegramAppData = new File(rootStorage + "/Android/data/org.telegram.messenger/files/Telegram");
        if (telegramAppData.exists() && telegramAppData.isDirectory()) {
            Log.d("TGMonitorService", "Found Telegram app data folder, setting up monitoring");
            setupTelegramSubdirMonitoring(telegramAppData);
        }
        
        // Also check for Telegram folder in sdcard app data
        File telegramSdcardAppData = new File("/sdcard/Android/data/org.telegram.messenger/files/Telegram");
        if (telegramSdcardAppData.exists() && telegramSdcardAppData.isDirectory()) {
            Log.d("TGMonitorService", "Found Telegram sdcard app data folder, setting up monitoring");
            setupTelegramSubdirMonitoring(telegramSdcardAppData);
        }
        
        // Setup MediaStore monitoring for app data folders (works on non-rooted devices)
        setupMediaStoreMonitoring();
        
        // Periodic scanning as backup (every 10 seconds)
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                Log.d("TGMonitorService", "Periodic scan started");
                scanTelegramFolders();
            } catch (Exception e) {
                Log.e("TGMonitorService", "Periodic scan error: " + e.getMessage(), e);
            }
        }, 5, 10, TimeUnit.SECONDS);
        
        // Test notification to verify service is working
        FileScanHelper.sendNotification(getApplicationContext(), "Telegram Monitor", "Servis ishga tushdi");
    }
    
    private void setupMonitoring(String storageRoot) {
        Log.d("TGMonitorService", "Setting up monitoring with root: " + storageRoot);
        
        for (String dir : TELEGRAM_DIRS) {
            String absPath = storageRoot + dir;
            File folder = new File(absPath);
            Log.d("TGMonitorService", "Checking directory: " + absPath + " exists: " + folder.exists() + " isDir: " + folder.isDirectory());
            if (!folder.exists() || !folder.isDirectory()) {
                Log.w("TGMonitorService", "Directory not found or not accessible: " + absPath);
                continue;
            }

            // Log what's in the Telegram folder
            if (absPath.contains("/Telegram")) {
                File[] files = folder.listFiles();
                if (files != null) {
                    Log.d("TGMonitorService", "Telegram folder contains " + files.length + " items:");
                    for (File file : files) {
                        Log.d("TGMonitorService", "  - " + file.getName() + " (file: " + file.isFile() + ", dir: " + file.isDirectory() + ", size: " + file.length() + ")");
                    }
                }
            }

            FileObserver observer = new FileObserver(absPath, FileObserver.CREATE | FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE) {
                @Override
                public void onEvent(int event, @Nullable String path) {
                    Log.d("TGMonitorService", "FileObserver event: " + event + " path: " + path);
                    if (path == null) return;
                    String lower = path.toLowerCase();
                    if (lower.startsWith(".pending-") || lower.endsWith(".part") || lower.endsWith(".partial") || lower.endsWith(".tmp") || lower.startsWith(".")) {
                        Log.d("TGMonitorService", "Skipping temp file: " + path);
                        return;
                    }
                    String filePath = absPath + "/" + path;
                    Log.d("TGMonitorService", "New file detected: " + filePath);
                    
                    // Notify immediately what file was detected
                    FileScanHelper.sendNotification(getApplicationContext(), "Telegram yuklandi", path);
                    
                    scheduler.schedule(() -> {
                        try {
                            File file = new File(filePath);
                            if (!file.exists() || !file.isFile()) {
                                Log.d("TGMonitorService", "File no longer exists: " + filePath);
                                return;
                            }
                            long prev = -1L;
                            for (int i = 0; i < 3; i++) {
                                long cur = file.length();
                                if (cur > 0 && cur == prev) break;
                                prev = cur;
                                try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                            }
                            Log.d("TGMonitorService", "Processing file: " + filePath + " size: " + file.length());
                            FileScanHelper.handleNewFile(getApplicationContext(), filePath);
                        } catch (Exception e) {
                            Log.e("TGMonitorService", "handle file error: " + e.getMessage(), e);
                        }
                    }, 500, TimeUnit.MILLISECONDS);
                }
            };
            observer.startWatching();
            observers.add(observer);
            Log.d("TGMonitorService", "Started watching: " + absPath);
        }
    }
    
    private void setupTelegramSubdirMonitoring(File telegramRoot) {
        try {
            File[] subdirs = telegramRoot.listFiles();
            if (subdirs != null) {
                for (File subdir : subdirs) {
                    if (subdir.isDirectory()) {
                        Log.d("TGMonitorService", "Setting up monitoring for Telegram subdir: " + subdir.getAbsolutePath());
                        
                        FileObserver observer = new FileObserver(subdir.getAbsolutePath(), FileObserver.CREATE | FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE) {
                            @Override
                            public void onEvent(int event, @Nullable String path) {
                                Log.d("TGMonitorService", "Subdir FileObserver event: " + event + " path: " + path + " in " + subdir.getName());
                                if (path == null) return;
                                String lower = path.toLowerCase();
                                if (lower.startsWith(".pending-") || lower.endsWith(".part") || lower.endsWith(".partial") || lower.endsWith(".tmp") || lower.startsWith(".")) {
                                    Log.d("TGMonitorService", "Skipping temp file: " + path);
                                    return;
                                }
                                String filePath = subdir.getAbsolutePath() + "/" + path;
                                Log.d("TGMonitorService", "New file detected in subdir: " + filePath);
                                
                                // Notify immediately what file was detected
                                String folderName = subdir.getName();
                                String notificationText = "Telegram " + folderName.replace("Telegram ", "") + ": " + path;
                                FileScanHelper.sendNotification(getApplicationContext(), "Telegram yuklandi", notificationText);
                                
                                scheduler.schedule(() -> {
                                    try {
                                        File file = new File(filePath);
                                        if (!file.exists() || !file.isFile()) {
                                            Log.d("TGMonitorService", "File no longer exists: " + filePath);
                                            return;
                                        }
                                        long prev = -1L;
                                        for (int i = 0; i < 3; i++) {
                                            long cur = file.length();
                                            if (cur > 0 && cur == prev) break;
                                            prev = cur;
                                            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                                        }
                                        Log.d("TGMonitorService", "Processing file from subdir: " + filePath + " size: " + file.length());
                                        FileScanHelper.handleNewFile(getApplicationContext(), filePath);
                                    } catch (Exception e) {
                                        Log.e("TGMonitorService", "handle file error: " + e.getMessage(), e);
                                    }
                                }, 500, TimeUnit.MILLISECONDS);
                            }
                        };
                        observer.startWatching();
                        observers.add(observer);
                        Log.d("TGMonitorService", "Started watching subdir: " + subdir.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            Log.e("TGMonitorService", "Error setting up subdir monitoring: " + e.getMessage(), e);
        }
    }
    
    private void setupMediaStoreMonitoring() {
        try {
            Log.d("TGMonitorService", "Setting up MediaStore monitoring for Telegram app data");
            
            // Monitor MediaStore for new files that might be from Telegram
            ContentResolver contentResolver = getContentResolver();
            
            // Monitor Downloads
            Uri downloadsUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL);
            ContentObserver downloadsObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    super.onChange(selfChange, uri);
                    Log.d("TGMonitorService", "Downloads MediaStore changed: " + uri);
                    checkForNewTelegramFiles();
                }
            };
            contentResolver.registerContentObserver(downloadsUri, true, downloadsObserver);
            contentObservers.add(downloadsObserver);
            
            // Monitor Files
            Uri filesUri = MediaStore.Files.getContentUri("external");
            ContentObserver filesObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    super.onChange(selfChange, uri);
                    Log.d("TGMonitorService", "Files MediaStore changed: " + uri);
                    checkForNewTelegramFiles();
                }
            };
            contentResolver.registerContentObserver(filesUri, true, filesObserver);
            contentObservers.add(filesObserver);
            
            Log.d("TGMonitorService", "MediaStore monitoring setup complete");
            
        } catch (Exception e) {
            Log.e("TGMonitorService", "Error setting up MediaStore monitoring: " + e.getMessage(), e);
        }
    }
    
    private void checkForNewTelegramFiles() {
        try {
            Log.d("TGMonitorService", "Checking for new Telegram files via MediaStore");
            
            ContentResolver contentResolver = getContentResolver();
            
            // Check Downloads
            Uri downloadsUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL);
            String[] projection = {MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.DATA, MediaStore.Downloads.DATE_ADDED};
            String selection = MediaStore.Downloads.DATE_ADDED + " > ?";
            String[] selectionArgs = {String.valueOf((System.currentTimeMillis() / 1000) - 60)}; // Last 60 seconds
            
            try (android.database.Cursor cursor = contentResolver.query(downloadsUri, projection, selection, selectionArgs, MediaStore.Downloads.DATE_ADDED + " DESC")) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String fileName = cursor.getString(cursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME));
                        String filePath = cursor.getString(cursor.getColumnIndex(MediaStore.Downloads.DATA));
                        long dateAdded = cursor.getLong(cursor.getColumnIndex(MediaStore.Downloads.DATE_ADDED));
                        
                        if (filePath != null && filePath.contains("Telegram")) {
                            Log.d("TGMonitorService", "Found new Telegram file via MediaStore: " + fileName + " at " + filePath);
                            FileScanHelper.sendNotification(getApplicationContext(), "Telegram yuklandi", fileName);
                            
                            // Process the file
                            scheduler.schedule(() -> {
                                try {
                                    File file = new File(filePath);
                                    if (file.exists() && file.isFile()) {
                                        Log.d("TGMonitorService", "Processing MediaStore detected file: " + filePath + " size: " + file.length());
                                        FileScanHelper.handleNewFile(getApplicationContext(), filePath);
                                    }
                                } catch (Exception e) {
                                    Log.e("TGMonitorService", "Error processing MediaStore file: " + e.getMessage(), e);
                                }
                            }, 1000, TimeUnit.MILLISECONDS);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e("TGMonitorService", "Error checking MediaStore: " + e.getMessage(), e);
        }
    }
    
    private void testStorageAccess() {
        try {
            Log.d("TGMonitorService", "Testing all storage locations...");
            
            for (String storageRoot : STORAGE_ROOTS) {
                File rootDir = new File(storageRoot);
                Log.d("TGMonitorService", "Testing: " + storageRoot + " exists: " + rootDir.exists() + " canRead: " + rootDir.canRead() + " canWrite: " + rootDir.canWrite());
                
                if (rootDir.exists() && rootDir.canRead()) {
                    File[] rootFiles = rootDir.listFiles();
                    if (rootFiles != null) {
                        Log.d("TGMonitorService", storageRoot + " contains " + rootFiles.length + " items");
                        for (File item : rootFiles) {
                            if (item.isDirectory() && (item.getName().equals("Android") || item.getName().equals("Download") || item.getName().equals("DCIM"))) {
                                Log.d("TGMonitorService", "Found important dir: " + item.getAbsolutePath() + " canRead: " + item.canRead());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("TGMonitorService", "Storage access test failed: " + e.getMessage(), e);
        }
    }
    
    private void scanTelegramFolders() {
        Log.d("TGMonitorService", "Periodic scan using MediaStore...");
        
        try {
            ContentResolver contentResolver = getContentResolver();
            
            // Only look for files in the specific Telegram app data folder
            Log.d("TGMonitorService", "Looking specifically for Telegram app data files...");
            
            Uri filesUri = MediaStore.Files.getContentUri("external");
            String[] filesProjection = {MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DATE_ADDED, MediaStore.Files.FileColumns.SIZE};
            
            // Query specifically for files in the Telegram app data path
            String telegramSelection = MediaStore.Files.FileColumns.DATA + " LIKE ?";
            String[] telegramSelectionArgs = {"%/Android/data/org.telegram.messenger/files/Telegram/%"};
            
            try (android.database.Cursor cursor = contentResolver.query(filesUri, filesProjection, telegramSelection, telegramSelectionArgs, MediaStore.Files.FileColumns.DATE_ADDED + " DESC")) {
                if (cursor != null) {
                    Log.d("TGMonitorService", "Found " + cursor.getCount() + " files in Telegram app data folder");
                    while (cursor.moveToNext()) {
                        String fileName = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME));
                        String filePath = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA));
                        long dateAdded = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED));
                        long fileSize = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE));
                        
                        Log.d("TGMonitorService", "Telegram app data file: " + fileName + " at " + filePath + " size: " + fileSize + " added: " + dateAdded);
                        
                        // Check if file was added recently (last 5 minutes)
                        long now = System.currentTimeMillis() / 1000;
                        if (dateAdded > now - 300) {
                            Log.d("TGMonitorService", "Recent Telegram app data file found: " + fileName);
                            FileScanHelper.sendNotification(getApplicationContext(), "Telegram fayl topildi", fileName);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e("TGMonitorService", "Error in periodic MediaStore scan: " + e.getMessage(), e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (FileObserver observer : observers) observer.stopWatching();
        for (ContentObserver observer : contentObservers) {
            try {
                getContentResolver().unregisterContentObserver(observer);
            } catch (Exception e) {
                Log.e("TGMonitorService", "Error unregistering content observer", e);
            }
        }
        if (scheduler != null) scheduler.shutdownNow();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}