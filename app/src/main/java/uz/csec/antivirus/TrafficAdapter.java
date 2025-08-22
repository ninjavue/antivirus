package uz.csec.antivirus;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class TrafficAdapter extends RecyclerView.Adapter<TrafficAdapter.ViewHolder> {

    private List<AppTraffic> trafficList;

    public TrafficAdapter(List<AppTraffic> trafficList) {
        this.trafficList = trafficList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_traffic, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppTraffic appTraffic = trafficList.get(position);
        holder.tvAppName.setText(appTraffic.getAppName());
        holder.tvPackage.setText(appTraffic.getPackageName());
        holder.tvTraffic.setText("⬇ " + AppTraffic.formatBytes(appTraffic.getRxBytes())
                + " ⬆ " + AppTraffic.formatBytes(appTraffic.getTxBytes())
                + " | Total: " + AppTraffic.formatBytes(appTraffic.getTotalBytes()));
    }

    @Override
    public int getItemCount() {
        return trafficList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAppName, tvPackage, tvTraffic;

        ViewHolder(View itemView) {
            super(itemView);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvPackage = itemView.findViewById(R.id.tvPackage);
            tvTraffic = itemView.findViewById(R.id.tvTraffic);
        }
    }
}
