package com.simpletext.reader.adapter;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.simpletext.reader.R;
import com.simpletext.reader.model.DrawerEntry;
import com.simpletext.reader.util.PrefsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for the drawer's storage shortcut list (Recent, storage roots, recent folders, ...).
 */
public class DrawerEntryAdapter extends RecyclerView.Adapter<DrawerEntryAdapter.ViewHolder> {

    public interface OnEntryClickListener {
        void onEntryClick(@NonNull DrawerEntry entry);
    }

    private final List<DrawerEntry> entries = new ArrayList<>();
    private OnEntryClickListener listener;

    public void setListener(OnEntryClickListener listener) { this.listener = listener; }

    public void setEntries(@NonNull List<DrawerEntry> newEntries) {
        if (entries.equals(newEntries)) return;
        entries.clear();
        entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_drawer_entry, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(entries.get(position));
    }

    @Override
    public int getItemCount() { return entries.size(); }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title;
        TextView subtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.drawer_entry_icon);
            title = itemView.findViewById(R.id.drawer_entry_title);
            subtitle = itemView.findViewById(R.id.drawer_entry_subtitle);
            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION || listener == null) return;
                DrawerEntry entry = entries.get(pos);
                if (!entry.isHeader() && !entry.isDivider()) listener.onEntryClick(entry);
            });
        }

        void bind(@NonNull DrawerEntry entry) {
            boolean dark = PrefsManager.getInstance(itemView.getContext())
                    .shouldUseDarkColors(itemView.getContext());
            int titleColor = dark ? Color.rgb(232, 234, 237) : Color.rgb(32, 33, 36);
            int subColor = dark ? Color.rgb(189, 193, 198) : Color.rgb(95, 99, 104);
            int headerColor = dark ? Color.rgb(154, 160, 166) : Color.rgb(95, 99, 104);
            int iconColor = Color.WHITE;

            ViewGroup.LayoutParams lp = itemView.getLayoutParams();
            if (entry.isDivider()) {
                lp.height = dpToPx(8);
                itemView.setLayoutParams(lp);
                itemView.setClickable(false);
                itemView.setFocusable(false);
                itemView.setBackgroundColor(Color.TRANSPARENT);
                icon.setVisibility(View.GONE);
                subtitle.setVisibility(View.GONE);
                title.setText("");
                title.setTextSize(0);
                title.setLetterSpacing(0f);
                title.setBackgroundColor(dark ? Color.rgb(92, 92, 92) : Color.rgb(188, 188, 188));
                ViewGroup.LayoutParams titleLp = title.getLayoutParams();
                titleLp.height = Math.max(1, dpToPx(1.5f));
                title.setLayoutParams(titleLp);
                return;
            }

            ViewGroup.LayoutParams titleLp = title.getLayoutParams();
            titleLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            title.setLayoutParams(titleLp);
            title.setBackgroundColor(Color.TRANSPARENT);

            if (entry.isHeader()) {
                lp.height = dpToPx(28);
                itemView.setLayoutParams(lp);
                itemView.setClickable(false);
                itemView.setFocusable(false);
                itemView.setBackgroundColor(Color.TRANSPARENT);
                icon.setVisibility(View.GONE);
                subtitle.setVisibility(View.GONE);
                title.setText(entry.getTitle().toUpperCase(Locale.getDefault()));
                title.setTextColor(headerColor);
                title.setTextSize(11);
                title.setLetterSpacing(0.08f);
                return;
            }

            lp.height = dpToPx(48);
            itemView.setLayoutParams(lp);
            itemView.setClickable(true);
            itemView.setFocusable(true);
            itemView.setBackgroundColor(Color.TRANSPARENT);
            icon.setVisibility(View.VISIBLE);
            title.setTextSize(14);
            title.setLetterSpacing(0f);
            icon.setImageResource(entry.getIconRes());
            icon.setImageTintList(ColorStateList.valueOf(iconColor));
            title.setText(entry.getTitle());
            title.setTextColor(titleColor);
            subtitle.setTextColor(subColor);
            if (entry.getSubtitle() != null && !entry.getSubtitle().isEmpty()) {
                subtitle.setVisibility(View.VISIBLE);
                subtitle.setText(entry.getSubtitle());
            } else {
                subtitle.setVisibility(View.GONE);
            }
        }

        private int dpToPx(float dp) {
            return Math.round(dp * itemView.getResources().getDisplayMetrics().density);
        }
    }
}
