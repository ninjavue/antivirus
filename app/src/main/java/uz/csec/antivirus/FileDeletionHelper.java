package uz.csec.antivirus;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class FileDeletionHelper {
    private static final String TAG = "FileDeletionHelper";
    
    public interface DeletionCallback {
        void onSuccess(String filePath);
        void onFailure(String filePath, String error);
    }
    
    /**
     * Main method to delete a file using the best available method
     */
    public static void deleteFile(Context context, String filePath, DeletionCallback callback) {
        File file = new File(filePath);
        
        // First try traditional deletion
        if (deleteWithRetries(file, 3, 100)) {
            callback.onSuccess(filePath);
            return;
        }
        
        // For Android 11+ restricted directories, try MediaStore deletion
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (isInRestrictedDirectory(filePath)) {
                if (deleteWithMediaStore(context, filePath)) {
                    callback.onSuccess(filePath);
                    return;
                }
            }
        }
        
        // Try to move to accessible location and delete
        if (moveAndDelete(context, filePath)) {
            callback.onSuccess(filePath);
            return;
        }
        
        callback.onFailure(filePath, "All deletion methods failed");
    }
    
    /**
     * Check if file is in a restricted directory (Android 11+)
     */
    private static boolean isInRestrictedDirectory(String filePath) {
        String lowerPath = filePath.toLowerCase();
        return lowerPath.contains("/android/data/") || 
               lowerPath.contains("/android/media/") ||
               lowerPath.contains("/android/obb/") ||
               lowerPath.contains("/storage/emulated/0/android/");
    }
    
    /**
     * Delete using MediaStore (works for Android 11+ restricted directories)
     */
    private static boolean deleteWithMediaStore(Context context, String filePath) {
        try {
            ContentResolver contentResolver = context.getContentResolver();
            
            // Try Files provider first
            Uri filesUri = MediaStore.Files.getContentUri("external");
            String selection = MediaStore.Files.FileColumns.DATA + "=?";
            String[] selectionArgs = {filePath};
            
            int deletedRows = contentResolver.delete(filesUri, selection, selectionArgs);
            if (deletedRows > 0) {
                Log.d(TAG, "MediaStore Files deletion successful: " + filePath);
                return true;
            }
            
            // Try Downloads provider
            Uri downloadsUri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL);
            deletedRows = contentResolver.delete(downloadsUri, selection, selectionArgs);
            if (deletedRows > 0) {
                Log.d(TAG, "MediaStore Downloads deletion successful: " + filePath);
                return true;
            }
            
            // Try Images provider
            Uri imagesUri = MediaStore.Images.Media.getContentUri("external");
            deletedRows = contentResolver.delete(imagesUri, selection, selectionArgs);
            if (deletedRows > 0) {
                Log.d(TAG, "MediaStore Images deletion successful: " + filePath);
                return true;
            }
            
            // Try Video provider
            Uri videoUri = MediaStore.Video.Media.getContentUri("external");
            deletedRows = contentResolver.delete(videoUri, selection, selectionArgs);
            if (deletedRows > 0) {
                Log.d(TAG, "MediaStore Video deletion successful: " + filePath);
                return true;
            }
            
            // Try Audio provider
            Uri audioUri = MediaStore.Audio.Media.getContentUri("external");
            deletedRows = contentResolver.delete(audioUri, selection, selectionArgs);
            if (deletedRows > 0) {
                Log.d(TAG, "MediaStore Audio deletion successful: " + filePath);
                return true;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "MediaStore deletion failed: " + e.getMessage(), e);
        }
        
        return false;
    }
    
    /**
     * Move file to accessible location and then delete
     */
    private static boolean moveAndDelete(Context context, String filePath) {
        try {
            File sourceFile = new File(filePath);
            if (!sourceFile.exists()) {
                return true; // Already deleted
            }
            
            // Try to move to Downloads folder
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }
            
            File tempFile = new File(downloadsDir, "temp_delete_" + System.currentTimeMillis() + "_" + sourceFile.getName());
            
            if (sourceFile.renameTo(tempFile)) {
                // Now delete from accessible location
                if (deleteWithRetries(tempFile, 3, 100)) {
                    Log.d(TAG, "Move and delete successful: " + filePath);
                    return true;
                } else {
                    Log.w(TAG, "Failed to delete moved file: " + tempFile.getAbsolutePath());
                }
            } else {
                Log.w(TAG, "Failed to move file to accessible location: " + filePath);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Move and delete failed: " + e.getMessage(), e);
        }
        
        return false;
    }
    
    /**
     * Traditional file deletion with retries
     */
    private static boolean deleteWithRetries(File file, int maxRetries, long delayMs) {
        for (int i = 0; i < maxRetries; i++) {
            if (file.delete() || !file.exists()) {
                return true;
            }
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }
    
    /**
     * Simple deletion method for backward compatibility
     */
    public static boolean deleteFileSimple(Context context, String filePath) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        deleteFile(context, filePath, new DeletionCallback() {
            @Override
            public void onSuccess(String path) {
                future.complete(true);
            }
            
            @Override
            public void onFailure(String path, String error) {
                Log.w(TAG, "Deletion failed: " + error);
                future.complete(false);
            }
        });
        
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.e(TAG, "Simple deletion timeout: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Enhanced deletion specifically for Telegram and restricted directories
     */
    public static boolean deleteTelegramFile(Context context, String filePath) {
        try {
            // First try MediaStore deletion (works for app data directories)
            if (deleteWithMediaStore(context, filePath)) {
                return true;
            }
            
            // Try traditional deletion
            File file = new File(filePath);
            if (deleteWithRetries(file, 5, 200)) {
                return true;
            }
            
            // Try move and delete as last resort
            return moveAndDelete(context, filePath);
            
        } catch (Exception e) {
            Log.e(TAG, "Telegram file deletion failed: " + e.getMessage(), e);
            return false;
        }
    }
} 