package uz.csec.antivirus;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class VirusTableAdapter extends RecyclerView.Adapter<VirusTableAdapter.VirusViewHolder> {
    public interface OnDeleteClickListener {
        void onDelete(VirusRecord record);
    }
    public static class VirusRecord {
        public int id;
        public String fileName;
        public String filePath;
        public String detectedAt;
        public VirusRecord(int id, String fileName, String filePath, String detectedAt) {
            this.id = id;
            this.fileName = fileName;
            this.filePath = filePath;
            this.detectedAt = detectedAt;
        }
    }
    private List<VirusRecord> records;
    private OnDeleteClickListener deleteListener;
    private OnItemClickListener itemClickListener;
    public interface OnItemClickListener {
        void onItemClick(VirusRecord record);
    }
    public VirusTableAdapter(List<VirusRecord> records, OnDeleteClickListener deleteListener) {
        this.records = records;
        this.deleteListener = deleteListener;
    }
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }
    @NonNull
    @Override
    public VirusViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_virus_table_row, parent, false);
        return new VirusViewHolder(v);
    }
    @Override
    public void onBindViewHolder(@NonNull VirusViewHolder holder, int position) {
        VirusRecord rec = records.get(position);
        holder.tvFileName.setText(rec.fileName);
        holder.tvFilePath.setText(rec.filePath);
        holder.tvDetectedAt.setText("Aniqlangan vaqti: " + rec.detectedAt);
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onDelete(rec);
        });
        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) itemClickListener.onItemClick(rec);
        });
    }
    @Override
    public int getItemCount() { return records.size(); }
    public void setRecords(List<VirusRecord> newRecords) {
        this.records = newRecords;
        notifyDataSetChanged();
    }
    static class VirusViewHolder extends RecyclerView.ViewHolder {
        TextView tvFileName, tvFilePath, tvDetectedAt;
        ImageButton btnDelete;
        VirusViewHolder(View v) {
            super(v);
            tvFileName = v.findViewById(R.id.tvVirusFileName);
            tvFilePath = v.findViewById(R.id.tvVirusFilePath);
            tvDetectedAt = v.findViewById(R.id.tvVirusDetectedAt);
            btnDelete = v.findViewById(R.id.btnDeleteVirus);
        }
    }
} 