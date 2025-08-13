package uz.csec.antivirus;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class FilePagerAdapter extends FragmentStateAdapter {
    
    private String largeFilesJson = "[]";
    private String suspiciousFilesJson = "[]";
    private String hiddenFilesJson = "[]";
    
    public FilePagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }
    
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return FileListFragment.newInstance("large", largeFilesJson);
            case 1:
                return FileListFragment.newInstance("suspicious", suspiciousFilesJson);
            case 2:
                return FileListFragment.newInstance("hidden", hiddenFilesJson);
            default:
                return FileListFragment.newInstance("large", largeFilesJson);
        }
    }
    
    @Override
    public int getItemCount() {
        return 3;
    }
    
    public void updateLargeFiles(String jsonData) {
        this.largeFilesJson = jsonData;
        notifyItemChanged(0);
    }
    
    public void updateSuspiciousFiles(String jsonData) {
        this.suspiciousFilesJson = jsonData;
        notifyItemChanged(1);
    }
    
    public void updateHiddenFiles(String jsonData) {
        this.hiddenFilesJson = jsonData;
        notifyItemChanged(2);
    }
    
    public FileListFragment getFragment(int position) {
        return (FileListFragment) getFragment(position);
    }
} 