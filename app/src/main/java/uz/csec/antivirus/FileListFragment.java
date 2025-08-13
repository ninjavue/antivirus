package uz.csec.antivirus;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class FileListFragment extends Fragment {
    
    private static final String ARG_FILE_TYPE = "file_type";
    private static final String ARG_JSON_DATA = "json_data";
    
    private String fileType;
    private String jsonData;
    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private TextView tvEmpty;
    
    public static FileListFragment newInstance(String fileType, String jsonData) {
        FileListFragment fragment = new FileListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_FILE_TYPE, fileType);
        args.putString(ARG_JSON_DATA, jsonData);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            fileType = getArguments().getString(ARG_FILE_TYPE);
            jsonData = getArguments().getString(ARG_JSON_DATA);
        }
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_list, container, false);
        
        recyclerView = view.findViewById(R.id.recyclerView);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FileAdapter();
        recyclerView.setAdapter(adapter);
        
        if (jsonData != null) {
            adapter.setFiles(jsonData);
            updateEmptyState();
        }
        
        return view;
    }
    
    public void updateData(String newJsonData) {
        this.jsonData = newJsonData;
        if (adapter != null) {
            adapter.setFiles(newJsonData);
            updateEmptyState();
        }
    }
    
    private void updateEmptyState() {
        if (adapter.getItemCount() == 0) {
            recyclerView.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            
            switch (fileType) {
                case "large":
                    tvEmpty.setText("100MB+ bo'lgan fayllar topilmadi");
                    break;
                case "suspicious":
                    tvEmpty.setText("Shubhali fayllar topilmadi");
                    break;
                case "hidden":
                    tvEmpty.setText("Yashirin fayllar topilmadi");
                    break;
                default:
                    tvEmpty.setText("Fayllar topilmadi");
                    break;
            }
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
        }
    }
    
    public void setOnFileScanListener(FileAdapter.OnFileScanListener listener) {
        if (adapter != null) {
            adapter.setOnFileScanListener(listener);
        }
    }
} 