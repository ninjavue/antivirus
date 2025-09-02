package uz.csec.antivirus;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import android.app.AppOpsManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.Manifest;

public class CleanerActivity extends AppCompatActivity {
    private static final int REQUEST_STORAGE = 1001;

    private List<CleanerItem> cleanerItems;
    private TextView tvCleanableSize, tvCleanableUnit, tvCleanableDesc;
    private long[] itemSizes;
    private String[] itemLabels;
    private int[] itemLayoutIds;
    private boolean[] itemSelected;
    private int[] segmentColors = {0xFFFF0000, 0xFF00FF00, 0xFFFFFF00, 0xFF0000FF};

    private static class CleanerItem {
        View view;
        TextView sizeText;
        long size;
        String label;
        int type;

        CleanerItem(View view, TextView sizeText, long size, String label, int type) {
            this.view = view;
            this.sizeText = sizeText;
            this.size = size;
            this.label = label;
            this.type = type;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowInsetsControllerCompat insetsController =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);
        setContentView(R.layout.activity_cleaner);

        ImageView btnBack = findViewById(R.id.btnBack);
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvCleanableSize = findViewById(R.id.tvCleanableSize);
        tvCleanableUnit = findViewById(R.id.tvCleanableUnit);
        tvCleanableDesc = findViewById(R.id.tvCleanableDesc);

        View.OnClickListener backListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CleanerActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
                overridePendingTransition(0, 0);
            }
        };
        btnBack.setOnClickListener(backListener);
        tvTitle.setOnClickListener(backListener);

        if (!hasStoragePermission()) {
            requestStoragePermission();
            return;
        }
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission();
            return;
        }

        // Hajmlarni hisoblash
        long bigFiles = getBigFilesSize(Environment.getExternalStorageDirectory(), 100 * 1024 * 1024);
        long audioVideo = getMediaFilesSize();
        long apks = getApkFilesSize(Environment.getExternalStorageDirectory());
        long junk = getAppCacheSize() + getDownloadFolderSize();

        itemSizes = new long[]{bigFiles, audioVideo, apks, junk};
        itemLabels = new String[]{getString(R.string.big_files), getString(R.string.audio_and_video), getString(R.string.apps_delete), getString(R.string.cash_memory)};
        itemLayoutIds = new int[]{R.id.item1, R.id.item2, R.id.item3, R.id.item4};
        itemSelected = new boolean[]{true, true, true, true};

        CleanerCircularProgressView mainProgress = findViewById(R.id.cleanerMainProgress);

        initializeCleanerItems();

        updateTotalDisplay();
        updateProgressSegments(mainProgress);
        mainProgress.animateBgProgress(1f);
        mainProgress.animateProgress(1f);
        updateCleanButtonState();

        tvCleanableDesc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performCleanup();
            }
        });
    }

    private void initializeCleanerItems() {
        cleanerItems = new ArrayList<>();
        int[] iconResIds = {R.drawable.ic_big_files, R.drawable.ic_disk, R.drawable.ic_phone, R.drawable.ic_cleaner};

        for (int i = 0; i < itemLayoutIds.length; i++) {
            View itemView = findViewById(itemLayoutIds[i]);
            if (itemView != null) {
                TextView sizeText = itemView.findViewById(R.id.itemSize);
                ImageView icon = itemView.findViewById(R.id.itemIcon);
                TextView label = itemView.findViewById(R.id.itemLabel);

                if (sizeText != null) sizeText.setText(formatSize(itemSizes[i]));
                if (icon != null) icon.setImageResource(iconResIds[i]);
                if (label != null) label.setText(itemLabels[i]);

                CleanerItem item = new CleanerItem(itemView, sizeText, itemSizes[i], itemLabels[i], i);
                cleanerItems.add(item);

                updateItemBackground(item, itemSelected[i]);

                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleItemSelection(item);
                    }
                });
            }
        }
    }

    private void toggleItemSelection(CleanerItem item) {
        itemSelected[item.type] = !itemSelected[item.type];
        updateItemBackground(item, itemSelected[item.type]);
        updateTotalDisplay();
        CleanerCircularProgressView mainProgress = findViewById(R.id.cleanerMainProgress);
        updateProgressSegments(mainProgress);
        mainProgress.animateBgProgress(1f);
        mainProgress.animateProgress(1f);
        updateCleanButtonState();
    }

    private void updateTotalDisplay() {
        long selectedTotal = 0;
        for (int i = 0; i < itemSizes.length; i++) {
            if (itemSelected[i]) {
                selectedTotal += itemSizes[i];
            }
        }

        String totalStr = formatSize(selectedTotal);
        tvCleanableSize.setText(totalStr.replaceAll("[^0-9.,]", "").trim());
        tvCleanableUnit.setText(getUnitFromFormattedSize(totalStr));
    }

    private void updateProgressSegments(CleanerCircularProgressView mainProgress) {
        long selectedTotal = 0;
        for (int i = 0; i < itemSizes.length; i++) {
            if (itemSelected[i]) {
                selectedTotal += itemSizes[i];
            }
        }

        ArrayList<Float> selPercent = new ArrayList<>();
        ArrayList<Integer> selColors = new ArrayList<>();

        if (selectedTotal > 0) {
            for (int i = 0; i < itemSizes.length; i++) {
                if (itemSelected[i] && itemSizes[i] > 0) {
                    float percent = (float) itemSizes[i] / selectedTotal;
                    selPercent.add(percent);
                    selColors.add(segmentColors[i]);
                }
            }
        }

        float[] percentages = new float[selPercent.size()];
        int[] colors = new int[selColors.size()];
        for (int j = 0; j < selPercent.size(); j++) {
            percentages[j] = selPercent.get(j);
            colors[j] = selColors.get(j);
        }

        mainProgress.setSegments(percentages, colors);
    }

    private void updateItemBackground(CleanerItem item, boolean isSelected) {
        if (isSelected) {
            item.view.setBackgroundResource(R.drawable.item_cleaner_selected_bg);
        } else {
            item.view.setBackgroundResource(R.drawable.item_cleaner_bg);
        }

        int paddingHorizontal = (int) (20 * getResources().getDisplayMetrics().density);
        item.view.setPadding(paddingHorizontal, item.view.getPaddingTop(), paddingHorizontal, item.view.getPaddingBottom());
    }

    private void updateCleanButtonState() {
        boolean hasSelection = false;
        for (boolean selected : itemSelected) {
            if (selected) {
                hasSelection = true;
                break;
            }
        }

        if (hasSelection) {
            tvCleanableDesc.setText(getString(R.string.cleaner));
            tvCleanableDesc.setTextColor(getResources().getColor(android.R.color.white));
            tvCleanableDesc.setBackgroundResource(R.drawable.btn_clean_bg);
            tvCleanableDesc.setClickable(true);
        } else {
            tvCleanableDesc.setText(getString(R.string.cleaner));
            tvCleanableDesc.setTextColor(getResources().getColor(android.R.color.darker_gray));
            tvCleanableDesc.setBackgroundResource(android.R.color.transparent);
            tvCleanableDesc.setClickable(false);
        }
    }

    private void performCleanup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Hamma fayllarga kirish ruxsati kerak", Toast.LENGTH_LONG).show();
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
                return;
            }
        }
        boolean hasSelection = false;
        for (boolean selected : itemSelected) {
            if (selected) {
                hasSelection = true;
                break;
            }
        }

        if (!hasSelection) {
            Toast.makeText(this, "Hech qanday element tanlanmagan", Toast.LENGTH_SHORT).show();
            return;
        }

        long cleanedSize = 0;

        for (int i = 0; i < itemSelected.length; i++) {
            if (itemSelected[i]) {
                switch (i) {
                    case 0:
                        cleanedSize += cleanBigFiles();
                        break;
                    case 1:
                        cleanedSize += cleanMediaFiles();
                        break;
                    case 2:
                        cleanedSize += cleanApkFiles();
                        break;
                    case 3:
                        cleanedSize += cleanJunkFiles();
                        break;
                }
            }
        }

        long bigFiles = getBigFilesSize(Environment.getExternalStorageDirectory(), 100 * 1024 * 1024);
        long audioVideo = getMediaFilesSize();
        long apks = getApkFilesSize(Environment.getExternalStorageDirectory());
        long junk = getAppCacheSize() + getDownloadFolderSize();

        itemSizes[0] = bigFiles;
        itemSizes[1] = audioVideo;
        itemSizes[2] = apks;
        itemSizes[3] = junk;

        updateAfterCleanup(cleanedSize);

        CleanerCircularProgressView mainProgress = findViewById(R.id.cleanerMainProgress);
        updateTotalDisplay();
        updateProgressSegments(mainProgress);

        Toast.makeText(this, formatSize(cleanedSize) + " tozalandi", Toast.LENGTH_LONG).show();
    }

    private long cleanBigFiles() {
        long[] cleanedSizeHolder = new long[]{0};
        File dir = Environment.getExternalStorageDirectory();
        cleanBigFilesRecursive(this, dir, 100 * 1024 * 1024, cleanedSizeHolder);
        long cleaned = cleanedSizeHolder[0];
        return cleaned;
    }

    private void cleanBigFilesRecursive(Context context, File dir, long minSize, long[] cleanedSizeHolder) {
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.length() >= minSize) {
                        long len = file.length();
                        if (deleteFileCompat(file)) {
                            cleanedSizeHolder[0] += len;
                        }
                    } else if (file.isDirectory()) {
                        cleanBigFilesRecursive(context, file, minSize, cleanedSizeHolder);
                    }
                }
            }
        }
    }

    private long cleanMediaFiles() {
        long cleanedSize = 0;
        File[] mediaDirs = new File[] {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        };
        for (File dir : mediaDirs) {
            cleanedSize += cleanAllFilesInDir(this, dir);
        }
        return cleanedSize;
    }

    private long cleanApkFiles() {
        long cleanedSize = 0;
        File dir = Environment.getExternalStorageDirectory();
        cleanedSize += cleanApkFilesRecursive(this, dir);
        return cleanedSize;
    }

    private long cleanAllFilesInDir(Context context, File dir) {
        long cleaned = 0;
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        long len = file.length();
                        if (deleteFileCompat(file)) {
                            cleaned += len;
                        }
                    } else if (file.isDirectory()) {
                        cleaned += cleanAllFilesInDir(context, file);
                        if (file.list() != null && file.list().length == 0) {
                            deleteFileCompat(file);
                        }
                    }
                }
            }
        }
        return cleaned;
    }

    private long cleanApkFilesRecursive(Context context, File dir) {
        long cleaned = 0;
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".apk")) {
                        long len = file.length();
                        if (deleteFileCompat(file)) {
                            cleaned += len;
                        }
                    } else if (file.isDirectory()) {
                        cleaned += cleanApkFilesRecursive(context, file);
                    }
                }
            }
        }
        return cleaned;
    }

    private long cleanJunkFiles() {
        long cleanedSize = 0;
        try {
            cleanedSize += deleteDirectoryContents(this, getCacheDir());
            cleanedSize += deleteDirectoryContents(this, getExternalCacheDir());
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            cleanedSize += deleteDirectoryContents(this, downloads);
        } catch (Exception e) {
            Log.e("CleanerActivity", "Error cleaning junk files", e);
        }
        return cleanedSize;
    }

    private long deleteDirectoryContents(Context context, File dir) {
        long cleaned = 0;
        if (dir != null && dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        long len = file.length();
                        if (deleteFileCompat(file)) {
                            cleaned += len;
                        }
                    } else if (file.isDirectory()) {
                        cleaned += deleteDirectoryContents(context, file);
                        if (file.list() != null && file.list().length == 0) {
                            deleteFileCompat(file);
                        }
                    }
                }
            }
        }
        return cleaned;
    }

    private void updateAfterCleanup(long cleanedSize) {
        for (int i = 0; i < itemSelected.length; i++) {
            itemSelected[i] = true;
        }

        for (int i = 0; i < cleanerItems.size(); i++) {
            CleanerItem item = cleanerItems.get(i);
            updateItemBackground(item, true);
            item.size = itemSizes[i];
            item.sizeText.setText(formatSize(itemSizes[i]));
        }

        updateCleanButtonState();
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_IMAGES
            }, REQUEST_STORAGE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }

    private boolean hasUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        }
        return true;
    }

    private void requestUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            if (hasStoragePermission()) recreate();
        }
    }

    private long getFolderSize(File dir) {
        long size = 0;
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        size += file.length();
                    } else {
                        size += getFolderSize(file);
                    }
                }
            }
        }
        return size;
    }

    private long getBigFilesSize(File dir, long minSize) {
        long size = 0;
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.length() >= minSize) {
                        size += file.length();
                    } else if (file.isDirectory()) {
                        size += getBigFilesSize(file, minSize);
                    }
                }
            }
        }
        return size;
    }

    private long getMediaFilesSize() {
        long size = 0;
        File music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        size += getFolderSize(music);
        size += getFolderSize(movies);
        size += getFolderSize(pictures);
        return size;
    }

    private long getApkFilesSize(File dir) {
        long size = 0;
        if (dir != null && dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".apk")) {
                        size += file.length();
                    } else if (file.isDirectory()) {
                        size += getApkFilesSize(file);
                    }
                }
            }
        }
        return size;
    }

    private long getAppCacheSize() {
        long size = 0;
        size += getFolderSize(getCacheDir());
        size += getFolderSize(getExternalCacheDir());
        return size;
    }

    private long getDownloadFolderSize() {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return getFolderSize(downloads);
    }

    private String formatSize(long size) {
        if (size == 0) return "0 B";
        float kb = size / 1024f;
        float mb = kb / 1024f;
        float gb = mb / 1024f;
        if (gb >= 1) return String.format("%.2f GB", gb);
        if (mb >= 1) return String.format("%.2f MB", mb);
        if (kb >= 1) return String.format("%.2f KB", kb);
        return size + " B";
    }

    private String getUnitFromFormattedSize(String formatted) {
        if (formatted.contains("GB")) return "GB";
        if (formatted.contains("MB")) return "MB";
        if (formatted.contains("KB")) return "KB";
        if (formatted.contains("B")) return "B";
        return "";
    }

    private boolean deleteFileCompat(File file) {
        if (file == null) return false;
        if (!file.exists()) return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentResolver cr = getContentResolver();
                String name = file.getName();
                String parentPath = file.getParent();
                String relPath = deriveRelativePath(parentPath);

                Uri[] targets = new Uri[]{
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.Files.getContentUri("external")
                };

                for (Uri baseUri : targets) {
                    int rows;
                    if (relPath != null) {
                        String sel = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?";
                        String[] args = new String[]{relPath, name};
                        rows = cr.delete(baseUri, sel, args);
                        if (rows > 0) return true;
                    }

                    String absSel = MediaStore.MediaColumns.DATA + "=?";
                    String[] absArgs = new String[]{file.getAbsolutePath()};
                    rows = cr.delete(baseUri, absSel, absArgs);
                    if (rows > 0) return true;

                    String dispSel = MediaStore.MediaColumns.DISPLAY_NAME + "=?";
                    String[] dispArgs = new String[]{name};
                    try (Cursor cursor = cr.query(baseUri, new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DATA}, dispSel, dispArgs, null)) {
                        if (cursor != null) {
                            while (cursor.moveToNext()) {
                                String cRel = getColumnString(cursor, MediaStore.MediaColumns.RELATIVE_PATH);
                                String cData = getColumnString(cursor, MediaStore.MediaColumns.DATA);
                                boolean samePath = false;
                                if (cRel != null && relPath != null) samePath = cRel.equals(relPath);
                                if (!samePath && cData != null) samePath = cData.equals(file.getAbsolutePath());
                                if (samePath) {
                                    long id = cursor.getLong(0);
                                    Uri itemUri = ContentUris.withAppendedId(baseUri, id);
                                    if (cr.delete(itemUri, null, null) > 0) return true;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("CleanerActivity", "MediaStore delete failed", e);
            }
        }
        boolean deleted = file.delete();
        return deleted || !file.exists();
    }

    private static String deriveRelativePath(String parentPath) {
        if (parentPath == null) return null;
        try {
            String downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            String dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
            String pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath();
            String movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getAbsolutePath();
            String music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath();

            if (parentPath.startsWith(downloads)) return Environment.DIRECTORY_DOWNLOADS + "/" + safeSub(parentPath, downloads) + "/";
            if (parentPath.startsWith(dcim)) return Environment.DIRECTORY_DCIM + "/" + safeSub(parentPath, dcim) + "/";
            if (parentPath.startsWith(pictures)) return Environment.DIRECTORY_PICTURES + "/" + safeSub(parentPath, pictures) + "/";
            if (parentPath.startsWith(movies)) return Environment.DIRECTORY_MOVIES + "/" + safeSub(parentPath, movies) + "/";
            if (parentPath.startsWith(music)) return Environment.DIRECTORY_MUSIC + "/" + safeSub(parentPath, music) + "/";

            String extRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
            if (parentPath.startsWith(extRoot)) {
                String rest = parentPath.substring(extRoot.length());
                if (rest.startsWith("/")) rest = rest.substring(1);
                if (!rest.isEmpty() && !rest.endsWith("/")) rest += "/";
                return rest.replace("\\", "/");
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String safeSub(String full, String prefix) {
        int start = prefix.length();
        if (full.length() <= start) return "";
        String sub = full.substring(start);
        if (sub.startsWith("/")) sub = sub.substring(1);
        return sub.replace("\\", "/");
    }

    private static String getColumnString(Cursor c, String column) {
        int idx = c.getColumnIndex(column);
        if (idx >= 0) return c.getString(idx);
        return null;
    }
}