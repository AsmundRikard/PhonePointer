package me.ambassator.phonepointer.data;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import me.ambassator.phonepointer.R;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    private final List<AppInfo> apps;
    private final OnAppClickListener listener;
    private OnReorderListener reorderListener;

    // Snap colors for indicators (matching AppsOverlayController.COLORS)
    private static final int[] COLORS = {
            0xFFE57373, 0xFF64B5F6, 0xFF81C784, 0xFFFFD54F, 0xFFBA68C8, 0xFF4DB6AC
    };

    public interface OnAppClickListener {
        void onAppClick(AppInfo app);
    }

    public interface OnReorderListener {
        void onMoveUp(int position);

        void onMoveDown(int position);
    }

    public AppAdapter(List<AppInfo> apps, OnAppClickListener listener) {
        this.apps = apps;
        this.listener = listener;
    }

    public void setReorderListener(OnReorderListener listener) {
        this.reorderListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = apps.get(position);
        holder.icon.setImageDrawable(app.icon);
        holder.label.setText(app.label);

        // Visual feedback for excluded apps - darker appearance
        float alpha = app.excluded ? 0.4f : 1.0f;
        holder.icon.setAlpha(alpha);
        holder.label.setAlpha(alpha);
        holder.itemView.setAlpha(alpha);

        // Show color indicator and reorder buttons if colorIndex is set (running apps)
        if (app.colorIndex >= 0 && holder.colorIndicator != null) {
            holder.colorIndicator.setVisibility(View.VISIBLE);
            int color = COLORS[app.colorIndex % COLORS.length];
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            holder.colorIndicator.setBackground(drawable);

            // Show reorder buttons for running apps
            if (holder.reorderButtons != null) {
                holder.reorderButtons.setVisibility(View.VISIBLE);

                int pos = position;
                holder.btnMoveUp.setOnClickListener(v -> {
                    if (reorderListener != null && pos > 0) {
                        reorderListener.onMoveUp(pos);
                    }
                });
                holder.btnMoveDown.setOnClickListener(v -> {
                    if (reorderListener != null && pos < apps.size() - 1) {
                        reorderListener.onMoveDown(pos);
                    }
                });

                // Dim arrows that can't be used
                holder.btnMoveUp.setAlpha(pos > 0 ? 1.0f : 0.3f);
                holder.btnMoveDown.setAlpha(pos < apps.size() - 1 ? 1.0f : 0.3f);
            }
        } else {
            if (holder.colorIndicator != null) {
                holder.colorIndicator.setVisibility(View.GONE);
            }
            if (holder.reorderButtons != null) {
                holder.reorderButtons.setVisibility(View.GONE);
            }
        }

        holder.itemView.setOnClickListener(v -> listener.onAppClick(app));
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final View colorIndicator;
        final View reorderButtons;
        final View btnMoveUp;
        final View btnMoveDown;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
            colorIndicator = itemView.findViewById(R.id.color_indicator);
            reorderButtons = itemView.findViewById(R.id.reorder_buttons);
            btnMoveUp = itemView.findViewById(R.id.btn_move_up);
            btnMoveDown = itemView.findViewById(R.id.btn_move_down);
        }
    }
}
