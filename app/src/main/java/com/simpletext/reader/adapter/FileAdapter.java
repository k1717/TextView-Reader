package com.simpletext.reader.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.simpletext.reader.R;
import com.simpletext.reader.util.FileUtils;
import com.simpletext.reader.util.PrefsManager;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    public interface OnFileClickListener {
        void onFileClick(File file);
        void onFileLongClick(File file);
    }

    private List<File> files = new ArrayList<>();
    private OnFileClickListener listener;
    private int sortMode = PrefsManager.SORT_NAME_ASC;
    private boolean sortEnabled = true;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public void setListener(OnFileClickListener listener) { this.listener = listener; }

    public void setFiles(List<File> fileList) {
        this.files = new ArrayList<>(fileList);
        sortFiles();
        notifyDataSetChanged();
    }

    public void setSortMode(int mode) {
        this.sortMode = mode;
        sortFiles();
        notifyDataSetChanged();
    }

    public void setSortEnabled(boolean enabled) {
        if (this.sortEnabled == enabled) return;
        this.sortEnabled = enabled;
        sortFiles();
        notifyDataSetChanged();
    }

    private void sortFiles() {
        if (!sortEnabled) return;
        Collections.sort(files, (a, b) -> {
            // Directories always first
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;

            switch (sortMode) {
                case PrefsManager.SORT_NAME_DESC:
                    return b.getName().compareToIgnoreCase(a.getName());
                case PrefsManager.SORT_DATE_NEW:
                    return Long.compare(b.lastModified(), a.lastModified());
                case PrefsManager.SORT_DATE_OLD:
                    return Long.compare(a.lastModified(), b.lastModified());
                case PrefsManager.SORT_SIZE_LARGE:
                    return Long.compare(b.length(), a.length());
                case PrefsManager.SORT_SIZE_SMALL:
                    return Long.compare(a.length(), b.length());
                case PrefsManager.SORT_TYPE:
                    String extA = getExtension(a.getName());
                    String extB = getExtension(b.getName());
                    int cmp = extA.compareToIgnoreCase(extB);
                    return cmp != 0 ? cmp : a.getName().compareToIgnoreCase(b.getName());
                default: // SORT_NAME_ASC
                    return a.getName().compareToIgnoreCase(b.getName());
            }
        });
    }

    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1) : "";
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) { holder.bind(files.get(position)); }

    @Override
    public int getItemCount() { return files.size(); }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name, info;

        private final Handler touchHandler = new Handler(Looper.getMainLooper());
        private float downX;
        private float downY;
        private boolean tapCancelled;
        private boolean longPressed;
        private final int tapSlop;
        private Runnable pendingLongPress;

        ViewHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.file_icon);
            name = v.findViewById(R.id.file_name);
            info = v.findViewById(R.id.file_info);
            tapSlop = Math.max(10, ViewConfiguration.get(v.getContext()).getScaledTouchSlop());

            v.setOnTouchListener((view, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        tapCancelled = false;
                        longPressed = false;
                        view.setPressed(true);
                        if (pendingLongPress != null) touchHandler.removeCallbacks(pendingLongPress);
                        pendingLongPress = () -> {
                            if (!tapCancelled && listener != null) {
                                int pos = getAdapterPosition();
                                if (pos != RecyclerView.NO_POSITION) {
                                    longPressed = true;
                                    view.setPressed(false);
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                                    listener.onFileLongClick(files.get(pos));
                                }
                            }
                        };
                        touchHandler.postDelayed(pendingLongPress, ViewConfiguration.getLongPressTimeout());
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getX() - downX;
                        float dy = event.getY() - downY;
                        if (Math.hypot(dx, dy) > tapSlop) {
                            tapCancelled = true;
                            view.setPressed(false);
                            if (pendingLongPress != null) touchHandler.removeCallbacks(pendingLongPress);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        view.setPressed(false);
                        if (pendingLongPress != null) touchHandler.removeCallbacks(pendingLongPress);
                        if (!tapCancelled && !longPressed && listener != null) {
                            int pos = getAdapterPosition();
                            if (pos != RecyclerView.NO_POSITION) {
                                view.performClick();
                                listener.onFileClick(files.get(pos));
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        view.setPressed(false);
                        tapCancelled = true;
                        if (pendingLongPress != null) touchHandler.removeCallbacks(pendingLongPress);
                        return false;

                    default:
                        return false;
                }
            });
        }

        void bind(File file) {
            boolean dark = PrefsManager.getInstance(itemView.getContext())
                    .shouldUseDarkColors(itemView.getContext());
            int primaryText = dark ? Color.rgb(232, 234, 237) : Color.rgb(32, 33, 36);
            int secondaryText = dark ? Color.rgb(176, 176, 176) : Color.rgb(95, 99, 104);
            int iconTint = dark ? Color.rgb(232, 234, 237) : Color.rgb(72, 76, 82);

            name.setText(file.getName());
            name.setTextColor(primaryText);
            info.setTextColor(secondaryText);

            if (file.isDirectory()) {
                icon.setImageResource(R.drawable.ic_folder);
                // Avoid file.listFiles() here: it is synchronous disk I/O and was
                // running on every scroll for every visible directory row, which
                // hung the UI thread on big roots like /storage/emulated/0.
                info.setText(R.string.folder);
            } else {
                icon.setImageResource(R.drawable.ic_text_file);
                String size = FileUtils.formatFileSize(file.length());
                String date = dateFormat.format(new Date(file.lastModified()));
                String type = FileUtils.getReadableFileType(file.getName());
                info.setText(type + "  •  " + size + "  •  " + date);
            }
            icon.setImageTintList(ColorStateList.valueOf(iconTint));
        }
    }
}
