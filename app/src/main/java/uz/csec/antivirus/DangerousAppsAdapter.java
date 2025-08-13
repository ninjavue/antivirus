package uz.csec.antivirus;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class DangerousAppsAdapter extends RecyclerView.Adapter<DangerousAppsAdapter.ViewHolder> {
    private List<DangerousApp> items = new ArrayList<>();

    public void submitList(List<DangerousApp> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.dangerous_app_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DangerousApp app = items.get(position);
        holder.ivAppIcon.setImageDrawable(app.icon);
        holder.tvAppName.setText(app.appName);
        holder.tvAppPackage.setText(app.packageName);
        holder.tvReason.setText(app.reason);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName, tvAppPackage, tvReason;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.iv_app_icon);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvAppPackage = itemView.findViewById(R.id.tv_app_package);
            tvReason = itemView.findViewById(R.id.tv_reason);
        }
    }
} 