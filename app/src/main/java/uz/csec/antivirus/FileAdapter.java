package uz.csec.antivirus;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {
    
    private List<FileItem> files = new ArrayList<>();
    private OnFileScanListener scanListener;
    
    public interface OnFileScanListener {
        void onFileScan(FileItem file);
    }
    
    public static class FileItem {
        public String name;
        public String path;
        public long size;
        public String formattedSize;
        public boolean isHidden;
        public boolean isSuspicious;
        public String fileType;
        public String riskLevel;
        
        public FileItem(String name, String path, long size, String formattedSize, 
                       boolean isHidden, boolean isSuspicious, String fileType, String riskLevel) {
            this.name = name;
            this.path = path;
            this.size = size;
            this.formattedSize = formattedSize;
            this.isHidden = isHidden;
            this.isSuspicious = isSuspicious;
            this.fileType = fileType;
            this.riskLevel = riskLevel;
        }
    }
    
    public static class FileViewHolder extends RecyclerView.ViewHolder {
        ImageView ivFileIcon;
        TextView tvFileName;
        TextView tvFilePath;
        TextView tvFileSize;
        TextView tvFileType;
        TextView tvHidden;
        TextView tvSuspicious;
        ImageButton btnScan;
        
        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFileIcon = itemView.findViewById(R.id.ivFileIcon);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFilePath = itemView.findViewById(R.id.tvFilePath);
            tvFileSize = itemView.findViewById(R.id.tvFileSize);
            tvFileType = itemView.findViewById(R.id.tvFileType);
            tvHidden = itemView.findViewById(R.id.tvHidden);
            tvSuspicious = itemView.findViewById(R.id.tvSuspicious);
            btnScan = itemView.findViewById(R.id.btnScan);
        }
    }
    
    public void setOnFileScanListener(OnFileScanListener listener) {
        this.scanListener = listener;
    }
    
    public void setFiles(String jsonData) {
        files.clear();
        try {
            JSONArray jsonArray = new JSONArray(jsonData);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                FileItem file = new FileItem(
                    jsonObject.getString("name"),
                    jsonObject.getString("path"),
                    jsonObject.getLong("size"),
                    jsonObject.getString("formattedSize"),
                    jsonObject.optBoolean("isHidden", false),
                    jsonObject.optBoolean("isSuspicious", false),
                    jsonObject.optString("fileType", ""),
                    jsonObject.optString("riskLevel", "")
                );
                files.add(file);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.file_item, parent, false);
        return new FileViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileItem file = files.get(position);
        
        holder.tvFileName.setText(file.name);
        holder.tvFilePath.setText(file.path);
        holder.tvFileSize.setText(file.formattedSize);
        holder.tvFileType.setText(file.fileType);
        
        if (file.fileType.equals(".apk")) {
            holder.ivFileIcon.setImageResource(R.drawable.ic_apps);
        } else if (file.fileType.equals(".exe") || file.fileType.equals(".jar")) {
            holder.ivFileIcon.setImageResource(R.drawable.ic_security);
        } else {
            holder.ivFileIcon.setImageResource(R.drawable.ic_files);
        }
        
        if (file.isHidden) {
            holder.tvHidden.setVisibility(View.VISIBLE);
        } else {
            holder.tvHidden.setVisibility(View.GONE);
        }
        
        if (file.isSuspicious) {
            holder.tvSuspicious.setVisibility(View.VISIBLE);
        } else {
            holder.tvSuspicious.setVisibility(View.GONE);
        }
        
        holder.btnScan.setOnClickListener(v -> {
            if (scanListener != null) {
                scanListener.onFileScan(file);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return files.size();
    }
} 