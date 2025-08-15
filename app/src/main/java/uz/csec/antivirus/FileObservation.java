package uz.csec.antivirus;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.os.Environment;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class FileObservation {

    /**
     * Checks common Telegram download locations and shows a notification for found files.
     * - /Android/data/org.telegram.messenger/files/Telegram/Telegram Files
     * - /Download/Telegram
     * - MediaStore queries for Telegram files
     * - Alternative storage paths
     * - Root access attempts
     * - Package manager queries
     */
    public void observeTelegramFiles(Context context) {
        // Method 1: Direct file access (may not work on newer Android)
        checkDirectFileAccess(context);
        
        // Method 2: MediaStore queries (works on all Android versions)
        checkMediaStoreForTelegramFiles(context);
        
        // Method 3: Multiple storage paths
        checkMultipleStoragePaths(context);
        
        // Method 4: Package-specific paths
        checkPackageSpecificPaths(context);
        
        // Method 5: Root access attempts
        checkWithRootAccess(context);
        
        // Method 6: Alternative MediaStore queries
        checkAlternativeMediaStoreQueries(context);
        
        // Method 7: File system scanning with different permissions
        checkFileSystemWithDifferentPermissions(context);
        
        // Method 8: Package manager based detection
        checkPackageManagerBasedDetection(context);
    }

    private void checkDirectFileAccess(Context context) {
        // Telegram app private external directory (not always accessible on newer Android versions)
        File appDataTelegram = new File(Environment.getExternalStorageDirectory(),
                "Android/data/org.telegram.messenger/files/Telegram/Telegram Files");

        // Public Downloads/Telegram folder
        File publicDownloadsTelegram = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Telegram");

        checkDirAndNotify(context, appDataTelegram);
        checkDirAndNotify(context, publicDownloadsTelegram);
    }

    private void checkMediaStoreForTelegramFiles(Context context) {
        try {
            ContentResolver contentResolver = context.getContentResolver();
            
            // Query for files in Telegram app data path
            Uri filesUri = MediaStore.Files.getContentUri("external");
            String[] projection = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_ADDED
            };
            
            // Look for files in Telegram app data directory
            String selection = MediaStore.Files.FileColumns.DATA + " LIKE ?";
            String[] selectionArgs = {"%/Android/data/org.telegram.messenger/files/Telegram/%"};
            
            try (Cursor cursor = contentResolver.query(filesUri, projection, selection, selectionArgs, 
                    MediaStore.Files.FileColumns.DATE_ADDED + " DESC")) {
                if (cursor != null) {
                    Log.d("FileObservation", "Found " + cursor.getCount() + " Telegram files via MediaStore");
                    while (cursor.moveToNext()) {
                        @SuppressLint("Range") String fileName = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME));
                        @SuppressLint("Range") String filePath = cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA));
                        @SuppressLint("Range") long fileSize = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE));
                        @SuppressLint("Range") long dateAdded = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED));
                        
                        Log.d("FileObservation", "Telegram file via MediaStore: " + fileName + " at " + filePath + " size: " + fileSize);
                        
                        // Check if file was added recently (last 10 minutes)
                        long now = System.currentTimeMillis() / 1000;
                        if (dateAdded > now - 600) {
                            notifyTelegramFile(context, new File(filePath));
                        }
                    }
                }
            }
            
            // Also check Downloads for Telegram files
            Uri downloadsUri = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                downloadsUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL);
            }
            String[] downloadsProjection = {
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.DATA,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_ADDED
            };
            
            String downloadsSelection = MediaStore.Downloads.DATA + " LIKE ?";
            String[] downloadsSelectionArgs = {"%/Telegram/%"};
            
            try (Cursor downloadsCursor = contentResolver.query(downloadsUri, downloadsProjection, downloadsSelection, downloadsSelectionArgs,
                    MediaStore.Downloads.DATE_ADDED + " DESC")) {
                if (downloadsCursor != null) {
                    Log.d("FileObservation", "Found " + downloadsCursor.getCount() + " Telegram files in Downloads via MediaStore");
                    while (downloadsCursor.moveToNext()) {
                        @SuppressLint("Range") String fileName = downloadsCursor.getString(downloadsCursor.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME));
                        @SuppressLint("Range") String filePath = downloadsCursor.getString(downloadsCursor.getColumnIndex(MediaStore.Downloads.DATA));
                        @SuppressLint("Range") long dateAdded = downloadsCursor.getLong(downloadsCursor.getColumnIndex(MediaStore.Downloads.DATE_ADDED));
                        
                        Log.d("FileObservation", "Telegram file in Downloads: " + fileName + " at " + filePath);
                        
                        // Check if file was added recently (last 10 minutes)
                        long now = System.currentTimeMillis() / 1000;
                        if (dateAdded > now - 600) {
                            notifyTelegramFile(context, new File(filePath));
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e("FileObservation", "MediaStore query error: " + e.getMessage(), e);
        }
    }

    private void checkMultipleStoragePaths(Context context) {
        String[] storageRoots = {
            "/storage/emulated/0",
            "/sdcard",
            "/storage/sdcard0",
            "/storage/sdcard1",
            "/mnt/sdcard",
            "/mnt/sdcard/external_sdcard",
            "/mnt/extSdCard"
        };
        
        String[] telegramPaths = {
            "/Android/data/org.telegram.messenger/files/Telegram/Telegram Files",
            "/Android/data/org.telegram.messenger/files/Telegram/Telegram Documents",
            "/Android/data/org.telegram.messenger/files/Telegram/Telegram Video",
            "/Android/data/org.telegram.messenger/files/Telegram/Telegram Audio",
            "/Android/data/org.telegram.messenger/files/Telegram/Telegram Images",
            "/Download/Telegram",
            "/Telegram",
            "/DCIM/Telegram",
            "/Pictures/Telegram",
            "/Documents/Telegram"
        };
        
        for (String storageRoot : storageRoots) {
            for (String telegramPath : telegramPaths) {
                File telegramDir = new File(storageRoot + telegramPath);
                if (telegramDir.exists() && telegramDir.isDirectory()) {
                    Log.d("FileObservation", "Found Telegram directory: " + telegramDir.getAbsolutePath());
                    checkDirAndNotify(context, telegramDir);
                }
            }
        }
    }

    private void checkPackageSpecificPaths(Context context) {
        try {
            // Try to get Telegram app's external files directory
            String[] telegramPackages = {
                "org.telegram.messenger",
                "org.telegram.plus",
                "org.telegram.beta"
            };
            
            for (String packageName : telegramPackages) {
                try {
                    ApplicationInfo appInfo = context.getPackageManager()
                        .getApplicationInfo(packageName, 0);
                    
                    if (appInfo != null) {
                        // Try to access the app's external files directory
                        File externalDir = new File(appInfo.dataDir + "/files/Telegram");
                        if (externalDir.exists() && externalDir.isDirectory()) {
                            Log.d("FileObservation", "Found Telegram external dir: " + externalDir.getAbsolutePath());
                            checkDirAndNotify(context, externalDir);
                        }
                        
                        // Also try the standard external files directory
                        File standardExternalDir = new File(Environment.getExternalStorageDirectory() + 
                            "/Android/data/" + packageName + "/files/Telegram");
                        if (standardExternalDir.exists() && standardExternalDir.isDirectory()) {
                            Log.d("FileObservation", "Found Telegram standard external dir: " + standardExternalDir.getAbsolutePath());
                            checkDirAndNotify(context, standardExternalDir);
                        }
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    // Package not installed, skip
                } catch (Exception e) {
                    Log.e("FileObservation", "Error checking package " + packageName + ": " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            Log.e("FileObservation", "Package-specific path check error: " + e.getMessage(), e);
        }
    }

    private void checkWithRootAccess(Context context) {
        try {
            // Try to use root access to find Telegram files
            String[] rootCommands = {
                "find /data/data/org.telegram.messenger/files/Telegram -type f -name '*' 2>/dev/null",
                "find /storage/emulated/0/Android/data/org.telegram.messenger/files/Telegram -type f -name '*' 2>/dev/null",
                "find /sdcard/Android/data/org.telegram.messenger/files/Telegram -type f -name '*' 2>/dev/null",
                "ls -la /data/data/org.telegram.messenger/files/Telegram/ 2>/dev/null",
                "ls -la /storage/emulated/0/Android/data/org.telegram.messenger/files/Telegram/ 2>/dev/null"
            };
            
            for (String command : rootCommands) {
                try {
                    Process process = Runtime.getRuntime().exec("su -c '" + command + "'");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("/") && !line.startsWith("d")) {
                            // This is a file path
                            String filePath = line.trim();
                            if (filePath.contains("Telegram") && new File(filePath).exists()) {
                                Log.d("FileObservation", "Found Telegram file via root: " + filePath);
                                notifyTelegramFile(context, new File(filePath));
                            }
                        }
                    }
                    reader.close();
                    process.waitFor();
                } catch (Exception e) {
                    // Root access not available or command failed
                    Log.d("FileObservation", "Root command failed: " + command + " - " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e("FileObservation", "Root access check error: " + e.getMessage(), e);
        }
    }

    private void checkAlternativeMediaStoreQueries(Context context) {
        try {
            ContentResolver contentResolver = context.getContentResolver();
            
            // Try different MediaStore queries
            String[] queries = {
                // Query for recent files that might be from Telegram
                "SELECT _data, _display_name, date_added FROM files WHERE _data LIKE '%Telegram%' AND date_added > " + (System.currentTimeMillis() / 1000 - 3600),
                
                // Query for files in app data directories
                "SELECT _data, _display_name, date_added FROM files WHERE _data LIKE '%/Android/data/org.telegram%' AND date_added > " + (System.currentTimeMillis() / 1000 - 3600),
                
                // Query for files with common Telegram extensions
                "SELECT _data, _display_name, date_added FROM files WHERE _data LIKE '%.apk' AND _data LIKE '%Telegram%' AND date_added > " + (System.currentTimeMillis() / 1000 - 3600)
            };
            
            for (String query : queries) {
                try (Cursor cursor = contentResolver.query(Uri.parse("content://media/external/file"), null, null, null, null)) {
                    if (cursor != null) {
                        Log.d("FileObservation", "Alternative MediaStore query found " + cursor.getCount() + " files");
                        while (cursor.moveToNext()) {
                            @SuppressLint("Range") String filePath = cursor.getString(cursor.getColumnIndex("_data"));
                            if (filePath != null && filePath.contains("Telegram")) {
                                Log.d("FileObservation", "Alternative MediaStore found: " + filePath);
                                notifyTelegramFile(context, new File(filePath));
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.d("FileObservation", "Alternative MediaStore query failed: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e("FileObservation", "Alternative MediaStore queries error: " + e.getMessage(), e);
        }
    }

    private void checkFileSystemWithDifferentPermissions(Context context) {
        try {
            // Try to access files with different permission approaches
            String[] alternativePaths = {
                "/data/data/org.telegram.messenger/files/Telegram",
                "/data/user/0/org.telegram.messenger/files/Telegram",
                "/data/app/org.telegram.messenger-*/files/Telegram",
                "/storage/emulated/0/Android/media/org.telegram.messenger/Telegram",
                "/storage/emulated/0/Android/data/org.telegram.messenger/files/Telegram",
                "/sdcard/Android/data/org.telegram.messenger/files/Telegram",
                "/mnt/sdcard/Android/data/org.telegram.messenger/files/Telegram"
            };
            
            for (String path : alternativePaths) {
                try {
                    File dir = new File(path);
                    if (dir.exists() && dir.isDirectory()) {
                        Log.d("FileObservation", "Found alternative Telegram path: " + path);
                        checkDirAndNotify(context, dir);
                    }
                } catch (Exception e) {
                    Log.d("FileObservation", "Alternative path failed: " + path + " - " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e("FileObservation", "File system with different permissions error: " + e.getMessage(), e);
        }
    }

    private void checkPackageManagerBasedDetection(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            
            // Get all installed packages
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            
            for (ApplicationInfo appInfo : packages) {
                if (appInfo.packageName.contains("telegram")) {
                    Log.d("FileObservation", "Found Telegram-related package: " + appInfo.packageName);
                    
                    // Try to access the app's files directory
                    String[] possiblePaths = {
                        appInfo.dataDir + "/files/Telegram",
                        appInfo.dataDir + "/files/Telegram/Telegram Files",
                        appInfo.dataDir + "/files/Telegram/Telegram Documents",
                        Environment.getExternalStorageDirectory() + "/Android/data/" + appInfo.packageName + "/files/Telegram"
                    };
                    
                    for (String path : possiblePaths) {
                        File telegramDir = new File(path);
                        if (telegramDir.exists() && telegramDir.isDirectory()) {
                            Log.d("FileObservation", "Found Telegram dir via package manager: " + path);
                            checkDirAndNotify(context, telegramDir);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e("FileObservation", "Package manager based detection error: " + e.getMessage(), e);
        }
    }

    private void checkDirAndNotify(Context context, File directory) {
        if (directory == null) return;
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files == null) return;
            Log.d("FileObservation", "Checking directory: " + directory.getAbsolutePath() + " with " + files.length + " files");
            for (File file : files) {
                if (file == null) continue;
                if (file.isDirectory()) {
                    // Optionally go one level deep
                    File[] inner = file.listFiles();
                    if (inner == null) continue;
                    for (File innerFile : inner) {
                        if (innerFile != null && innerFile.isFile()) {
                            notifyTelegramFile(context, innerFile);
                        }
                    }
                } else if (file.isFile()) {
                    notifyTelegramFile(context, file);
                }
            }
        }
    }

    private void notifyTelegramFile(Context context, File file) {
        String fileName = file.getName();
        Log.d("FileObservation", "Notifying about Telegram file: " + fileName + " at " + file.getAbsolutePath());
        FileScanHelper.sendNotification(context, "Telegram yuklandi", fileName);
        // Optionally trigger scan/handling logic
        FileScanHelper.handleNewFile(context, file.getAbsolutePath());
    }
}


