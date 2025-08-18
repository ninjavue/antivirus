package uz.csec.antivirus;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class AppCardAdapter extends RecyclerView.Adapter<AppCardAdapter.ViewHolder> {
    private List<AppCardItem> items = new ArrayList<>();
    private Context context;

    public AppCardAdapter(Context context) {
        this.context = context;
    }

    public void submitList(List<AppCardItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.app_card_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppCardItem item = items.get(position);
        
        // Set app info
        holder.ivAppIcon.setImageDrawable(item.appIcon);
        holder.tvAppName.setText(item.appName);
        holder.tvAppPackage.setText(item.packageName);
        
        // Set up expand/collapse functionality
        holder.appHeader.setOnClickListener(v -> {
            boolean isExpanded = holder.expandedContent.getVisibility() == View.VISIBLE;
            if (isExpanded) {
                holder.expandedContent.setVisibility(View.GONE);
                holder.ivArrow.setRotation(0);
            } else {
                holder.expandedContent.setVisibility(View.VISIBLE);
                holder.ivArrow.setRotation(90);
            }
        });
        
        // Set up permission adapters
        if (item.grantedPermissions.isEmpty() && item.requestedPermissions.isEmpty()) {
            // No permissions at all - show only one "Ruxsati yo'q" message in the center
            showSingleNoPermissionsMessage(holder.rvGrantedPermissions, "Ruxsati yo'q");
            
            // Hide the requested permissions section and manage app button when no permissions
            holder.rvRequestedPermissions.setVisibility(View.GONE);
            holder.btnManageApp.setVisibility(View.GONE);
            
            // Hide the section titles when no permissions
            hidePermissionSectionTitles(holder);
        } else {
            // Has some permissions - show normal permission lists and manage button
            holder.rvRequestedPermissions.setVisibility(View.VISIBLE);
            holder.btnManageApp.setVisibility(View.VISIBLE);
            
            // Show the section titles when permissions exist
            showPermissionSectionTitles(holder);
            
            // Handle granted permissions
            if (item.grantedPermissions.isEmpty()) {
                // No granted permissions - show "Ruxsati yo'q" in granted section
                showNoPermissionsMessage(holder.rvGrantedPermissions, "Ruxsati yo'q");
            } else {
                // Has granted permissions - show normal list
                PermissionAdapter grantedAdapter = new PermissionAdapter(item.grantedPermissions);
                holder.rvGrantedPermissions.setLayoutManager(new LinearLayoutManager(context));
                holder.rvGrantedPermissions.setAdapter(grantedAdapter);
            }
            
            // Handle requested permissions
            if (item.requestedPermissions.isEmpty()) {
                // No requested permissions - show "Ruxsati yo'q" in requested section
                showNoPermissionsMessage(holder.rvRequestedPermissions, "Ruxsati yo'q");
            } else {
                // Has requested permissions - show normal list
                PermissionAdapter requestedAdapter = new PermissionAdapter(item.requestedPermissions);
                holder.rvRequestedPermissions.setLayoutManager(new LinearLayoutManager(context));
                holder.rvRequestedPermissions.setAdapter(requestedAdapter);
            }
            
            // Set up manage app button
            holder.btnManageApp.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.fromParts("package", item.packageName, null));
                context.startActivity(intent);
            });
        }
    }

    private void showSingleNoPermissionsMessage(RecyclerView recyclerView, String message) {
        // Create a simple adapter that shows the message
        RecyclerView.Adapter<RecyclerView.ViewHolder> messageAdapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                TextView textView = new TextView(context);
                textView.setText(message);
                textView.setTextSize(16);
                textView.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
                textView.setGravity(android.view.Gravity.CENTER);
                textView.setPadding(32, 32, 32, 32);
                
                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                );
                textView.setLayoutParams(params);
                
                return new RecyclerView.ViewHolder(textView) {};
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                // Text is already set in onCreateViewHolder
            }

            @Override
            public int getItemCount() {
                return 1;
            }
        };
        
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(messageAdapter);
    }

    private void showNoPermissionsMessage(RecyclerView recyclerView, String message) {
        // Create a simple adapter that shows the message for individual sections
        RecyclerView.Adapter<RecyclerView.ViewHolder> messageAdapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                TextView textView = new TextView(context);
                textView.setText(message);
                textView.setTextSize(14);
                textView.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
                textView.setGravity(android.view.Gravity.CENTER);
                textView.setPadding(16, 16, 16, 16);
                
                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                );
                textView.setLayoutParams(params);
                
                return new RecyclerView.ViewHolder(textView) {};
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                // Text is already set in onCreateViewHolder
            }

            @Override
            public int getItemCount() {
                return 1;
            }
        };
        
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(messageAdapter);
    }

    private void hidePermissionSectionTitles(ViewHolder holder) {
        // Find and hide the section title TextViews in the expanded content
        View expandedContent = holder.expandedContent;
        if (expandedContent instanceof ViewGroup) {
            hideSectionTitlesRecursively((ViewGroup) expandedContent);
        }
    }

    private void showPermissionSectionTitles(ViewHolder holder) {
        // Find and show the section title TextViews in the expanded content
        View expandedContent = holder.expandedContent;
        if (expandedContent instanceof ViewGroup) {
            showSectionTitlesRecursively((ViewGroup) expandedContent);
        }
    }

    private void hideSectionTitlesRecursively(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof TextView) {
                TextView textView = (TextView) child;
                String text = textView.getText().toString();
                if (text.contains("Berilgan ruxsatlar") || text.contains("Berilmagan ruxsatlar")) {
                    textView.setVisibility(View.GONE);
                }
            } else if (child instanceof ViewGroup) {
                hideSectionTitlesRecursively((ViewGroup) child);
            }
        }
    }

    private void showSectionTitlesRecursively(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof TextView) {
                TextView textView = (TextView) child;
                String text = textView.getText().toString();
                if (text.contains("Berilgan ruxsatlar") || text.contains("Berilmagan ruxsatlar")) {
                    textView.setVisibility(View.VISIBLE);
                }
            } else if (child instanceof ViewGroup) {
                showSectionTitlesRecursively((ViewGroup) child);
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon, ivArrow;
        TextView tvAppName, tvAppPackage;
        LinearLayout appHeader, expandedContent;
        RecyclerView rvGrantedPermissions, rvRequestedPermissions;
        Button btnManageApp;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.iv_app_icon);
            ivArrow = itemView.findViewById(R.id.iv_arrow);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvAppPackage = itemView.findViewById(R.id.tv_app_package);
            appHeader = itemView.findViewById(R.id.app_header);
            expandedContent = itemView.findViewById(R.id.expanded_content);
            rvGrantedPermissions = itemView.findViewById(R.id.rv_granted_permissions);
            rvRequestedPermissions = itemView.findViewById(R.id.rv_requested_permissions);
            btnManageApp = itemView.findViewById(R.id.btn_manage_app);
        }
    }

    // Inner adapter for permissions
    private static class PermissionAdapter extends RecyclerView.Adapter<PermissionAdapter.PermissionViewHolder> {
        private List<PermissionItem> permissions;

        public PermissionAdapter(List<PermissionItem> permissions) {
            this.permissions = permissions != null ? permissions : new ArrayList<>();
        }

        @NonNull
        @Override
        public PermissionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.permission_item, parent, false);
            return new PermissionViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PermissionViewHolder holder, int position) {
            PermissionItem permission = permissions.get(position);
            holder.tvPermissionName.setText(permission.name);
            holder.tvPermissionStatus.setText(permission.isDangerous ? "XAVFLI" : "ODDIY");
            holder.tvPermissionStatus.setBackgroundResource(
                permission.isDangerous ? R.drawable.dot_red : R.drawable.dot_blue
            );
        }

        @Override
        public int getItemCount() {
            return permissions.size();
        }

        static class PermissionViewHolder extends RecyclerView.ViewHolder {
            ImageView ivPermissionIcon;
            TextView tvPermissionName, tvPermissionStatus;

            PermissionViewHolder(@NonNull View itemView) {
                super(itemView);
                ivPermissionIcon = itemView.findViewById(R.id.iv_permission_icon);
                tvPermissionName = itemView.findViewById(R.id.tv_permission_name);
                tvPermissionStatus = itemView.findViewById(R.id.tv_permission_status);
            }
        }
    }
} 