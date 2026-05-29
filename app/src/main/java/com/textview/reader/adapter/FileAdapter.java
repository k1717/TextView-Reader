package com.textview.reader.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.text.TextUtils;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.textview.reader.R;
import com.textview.reader.model.ReaderState;
import com.textview.reader.util.FileUtils;
import com.textview.reader.util.PrefsManager;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    public interface OnFileClickListener {
        void onFileClick(File file);
        void onFileLongClick(File file);
    }

    private final List<File> files = new ArrayList<>();
    private OnFileClickListener listener;
    private int sortMode = PrefsManager.SORT_NAME_ASC;
    private boolean sortEnabled = true;
    private int touchCancelGeneration = 0;
    private boolean showReadingProgress = false;
    private final Map<String, Integer> readingProgressByPath = new HashMap<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public void setListener(OnFileClickListener listener) { this.listener = listener; }

    public void setShowReadingProgress(boolean enabled) {
        if (this.showReadingProgress == enabled) return;
        this.showReadingProgress = enabled;
        if (!enabled) readingProgressByPath.clear();
        if (getItemCount() > 0) notifyItemRangeChanged(0, getItemCount());
    }

    public void setReadingProgressStates(List<ReaderState> states) {
        readingProgressByPath.clear();
        if (states != null) {
            for (ReaderState state : states) {
                if (state == null || state.getFilePath() == null) continue;
                int pct = calculateReadingProgressPercent(state);
                if (pct >= 0) readingProgressByPath.put(state.getFilePath(), pct);
            }
        }
    }

    public void refreshReadingProgress() {
        if (showReadingProgress && getItemCount() > 0) {
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    public void cancelPendingPresses() {
        touchCancelGeneration++;
    }

    public void setFiles(List<File> fileList) {
        replaceFiles(new ArrayList<>(fileList));
    }

    /**
     * Fast replacement used when navigating to a different folder. Directory
     * switches do not need item-by-item DiffUtil animation, and very large
     * folders can make DiffUtil noticeably block the UI thread.
     */
    public void setFilesFastPresorted(List<File> fileList) {
        files.clear();
        files.addAll(fileList);
        notifyDataSetChanged();
    }

    /** Updates the stored sort mode without re-sorting the currently visible list. */
    public void setSortModeSilently(int mode) {
        this.sortMode = mode;
    }

    public void setSortMode(int mode) {
        if (this.sortMode == mode) return;
        this.sortMode = mode;
        replaceFiles(new ArrayList<>(files));
    }

    public void setSortEnabled(boolean enabled) {
        if (this.sortEnabled == enabled) return;
        this.sortEnabled = enabled;
        replaceFiles(new ArrayList<>(files));
    }

    private void replaceFiles(@NonNull List<File> next) {
        if (sortEnabled) sortFiles(next);
        if (files.equals(next)) return;

        List<File> old = new ArrayList<>(files);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return old.size(); }
            @Override public int getNewListSize() { return next.size(); }

            @Override public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return old.get(oldItemPosition).getAbsolutePath()
                        .equals(next.get(newItemPosition).getAbsolutePath());
            }

            @Override public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                File a = old.get(oldItemPosition);
                File b = next.get(newItemPosition);
                return a.getName().equals(b.getName())
                        && a.isDirectory() == b.isDirectory()
                        && a.length() == b.length()
                        && a.lastModified() == b.lastModified();
            }
        });

        files.clear();
        files.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    private void sortFiles(@NonNull List<File> target) {
        target.sort((a, b) -> {
            // Directories always first
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;

            switch (sortMode) {
                case PrefsManager.SORT_NAME_DESC:
                    return compareNames(b, a);
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
                    return cmp != 0 ? cmp : compareNames(a, b);
                default: // SORT_NAME_ASC
                    return compareNames(a, b);
            }
        });
    }

    private int compareNames(@NonNull File a, @NonNull File b) {
        return a.getName().compareToIgnoreCase(b.getName());
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

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        holder.cancelPendingPress();
        super.onViewRecycled(holder);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
        holder.cancelPendingPress();
        super.onViewDetachedFromWindow(holder);
    }

    public void refreshTheme() {
        if (getItemCount() > 0) {
            notifyItemRangeChanged(0, getItemCount());
        }
    }

    public void release() {
        listener = null;
        files.clear();
    }

    private static int calculateReadingProgressPercent(@NonNull ReaderState state) {
        int current = state.getPageNumber();
        int total = state.getTotalPages();
        if (total > 1 && current > 0) {
            int clampedCurrent = Math.max(1, Math.min(total, current));
            return Math.max(0, Math.min(100, Math.round((clampedCurrent * 100f) / total)));
        }

        long length = state.getFileLength();
        int charPosition = state.getCharPosition();
        if (length > 0 && charPosition > 0) {
            return Math.max(0, Math.min(100, Math.round((charPosition * 100f) / length)));
        }
        return -1;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        LinearLayout textContainer;
        TextView name, info, progress;

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
            textContainer = v.findViewById(R.id.file_text_container);
            name = v.findViewById(R.id.file_name);
            info = v.findViewById(R.id.file_info);
            progress = v.findViewById(R.id.file_progress);
            tapSlop = Math.max(10, ViewConfiguration.get(v.getContext()).getScaledTouchSlop());

            v.setOnTouchListener((view, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        tapCancelled = false;
                        longPressed = false;
                        view.setPressed(true);
                        if (pendingLongPress != null) {
                            touchHandler.removeCallbacks(pendingLongPress);
                            pendingLongPress = null;
                        }
                        final int longPressGeneration = touchCancelGeneration;
                        pendingLongPress = () -> {
                            if (longPressGeneration == touchCancelGeneration
                                    && !tapCancelled
                                    && listener != null) {
                                int pos = getBindingAdapterPosition();
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
                            if (pendingLongPress != null) {
                            touchHandler.removeCallbacks(pendingLongPress);
                            pendingLongPress = null;
                        }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        view.setPressed(false);
                        if (pendingLongPress != null) {
                            touchHandler.removeCallbacks(pendingLongPress);
                            pendingLongPress = null;
                        }
                        if (!tapCancelled && !longPressed && listener != null) {
                            int pos = getBindingAdapterPosition();
                            if (pos != RecyclerView.NO_POSITION) {
                                view.performClick();
                                listener.onFileClick(files.get(pos));
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        view.setPressed(false);
                        tapCancelled = true;
                        if (pendingLongPress != null) {
                            touchHandler.removeCallbacks(pendingLongPress);
                            pendingLongPress = null;
                        }
                        return false;

                    default:
                        return false;
                }
            });
        }

        void cancelPendingPress() {
            tapCancelled = true;
            longPressed = false;
            itemView.setPressed(false);
            if (pendingLongPress != null) {
                touchHandler.removeCallbacks(pendingLongPress);
                pendingLongPress = null;
            }
        }

        void bind(File file) {
            cancelPendingPress();
            PrefsManager prefs = PrefsManager.getInstance(itemView.getContext());
            boolean dark = prefs.shouldUseDarkColors(itemView.getContext());
            int primaryText = prefs.getMainTextColor(itemView.getContext());
            int secondaryText = prefs.getMainSubTextColor(itemView.getContext());
            int iconTint = dark ? prefs.getMainTextColor(itemView.getContext()) : Color.rgb(72, 76, 82);

            name.setText(file.getName());
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            name.setHorizontallyScrolling(false);
            name.setTextColor(primaryText);
            info.setSingleLine(true);
            info.setEllipsize(TextUtils.TruncateAt.END);
            info.setTextColor(secondaryText);
            if (progress != null) {
                progress.setTextColor(secondaryText);
                progress.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
                progress.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
            }

            if (file.isDirectory()) {
                icon.setImageResource(R.drawable.ic_folder);
                // Avoid file.listFiles() here: it is synchronous disk I/O and was
                // running on every scroll for every visible directory row, which
                // hung the UI thread on large storage roots.
                info.setText(R.string.folder);
            } else {
                icon.setImageResource(R.drawable.ic_text_file);
                String size = FileUtils.formatFileSize(file.length());
                String date = dateFormat.format(new Date(file.lastModified()));
                String type = FileUtils.getReadableFileType(file.getName());
                info.setText(String.format(Locale.getDefault(), "%s  •  %s  •  %s", type, size, date));
            }
            updateReadingProgressBadge(file);
            icon.setImageTintList(ColorStateList.valueOf(iconTint));
            itemView.setPressed(false);
            itemView.setSelected(false);
            itemView.setActivated(false);
            itemView.setBackground(makeFileRowBackground(prefs));
        }

        private void updateReadingProgressBadge(@NonNull File file) {
            if (progress == null) return;
            Integer pct = showReadingProgress && !file.isDirectory()
                    ? readingProgressByPath.get(file.getAbsolutePath())
                    : null;
            if (pct == null) {
                setTextReserveEnd(0);
                progress.setVisibility(View.GONE);
                progress.setText("");
                progress.setBackgroundColor(Color.TRANSPARENT);
                return;
            }

            progress.setBackgroundColor(Color.TRANSPARENT);
            progress.setText(String.format(Locale.getDefault(), "%d%%", Math.max(0, Math.min(100, pct))));
            progress.setVisibility(View.VISIBLE);

            int badgeWidth = getMaxProgressBadgeWidth();
            ViewGroup.LayoutParams layoutParams = progress.getLayoutParams();
            if (layoutParams != null && layoutParams.width != badgeWidth) {
                layoutParams.width = badgeWidth;
                progress.setLayoutParams(layoutParams);
            }

            int marginEnd = 0;
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                marginEnd = ((ViewGroup.MarginLayoutParams) layoutParams).getMarginEnd();
            }
            int progressReserve = badgeWidth + marginEnd + dpToPx(5);
            setTextReserveEnd(progressReserve);
        }

        private void setTextReserveEnd(int reservePx) {
            if (textContainer != null) {
                textContainer.setPadding(
                        textContainer.getPaddingLeft(),
                        textContainer.getPaddingTop(),
                        reservePx,
                        textContainer.getPaddingBottom());
            }
            name.setPadding(name.getPaddingLeft(), name.getPaddingTop(), 0, name.getPaddingBottom());
            info.setPadding(info.getPaddingLeft(), info.getPaddingTop(), 0, info.getPaddingBottom());
        }

        private int getMaxProgressBadgeWidth() {
            int textWidth = (int) Math.ceil(progress.getPaint().measureText("100%"));
            return textWidth + progress.getPaddingLeft() + progress.getPaddingRight();
        }

        private int dpToPx(int dp) {
            return Math.round(dp * itemView.getResources().getDisplayMetrics().density);
        }

        private StateListDrawable makeFileRowBackground(@NonNull PrefsManager prefs) {
            int pressed = prefs.getMainFileLongHoldColor(itemView.getContext());
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(pressed));
            states.addState(new int[]{android.R.attr.state_focused}, new ColorDrawable(pressed));
            states.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(pressed));
            states.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
            return states;
        }
    }
}
