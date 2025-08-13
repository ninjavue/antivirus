package uz.csec.antivirus;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class PermissionsAdapter extends RecyclerView.Adapter<PermissionsAdapter.ViewHolder> {
    private List<PermissionApp> items = new ArrayList<>();

    public void submitList(List<PermissionApp> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.permission_app_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PermissionApp app = items.get(position);
        holder.ivAppIcon.setImageDrawable(app.icon);
        holder.tvAppName.setText(app.appName);
        holder.tvAppPackage.setText(app.packageName);
        holder.tvPermissions.setText(android.text.TextUtils.join(", ", app.permissions));
        holder.btnOpenSettings.setOnClickListener(v -> {
            Context context = v.getContext();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + app.packageName));
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName, tvAppPackage, tvPermissions;
        Button btnOpenSettings;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.iv_app_icon);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvAppPackage = itemView.findViewById(R.id.tv_app_package);
            tvPermissions = itemView.findViewById(R.id.tv_permissions);
            btnOpenSettings = itemView.findViewById(R.id.btn_open_settings);
        }
    }
} 