package com.simpletext.reader.adapter;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.simpletext.reader.R;
import com.simpletext.reader.model.Bookmark;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Date;

/**
 * TekView-style bookmark list:
 * file folder header -> expandable bookmarks inside each text file.
 */
public class BookmarkFolderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_FOLDER = 0;
    private static final int TYPE_BOOKMARK = 1;

    public interface Listener {
        void onFolderClick(String filePath);
        void onBookmarkClick(Bookmark bookmark);
        void onBookmarkDelete(Bookmark bookmark);
        void onBookmarkEdit(Bookmark bookmark);
    }

    private static class Row {
        int type;
        String filePath;
        String fileName;
        int count;
        boolean expanded;
        boolean currentFile;
        Bookmark bookmark;

        static Row folder(String filePath, String fileName, int count, boolean expanded, boolean currentFile) {
            Row r = new Row();
            r.type = TYPE_FOLDER;
            r.filePath = filePath;
            r.fileName = fileName;
            r.count = count;
            r.expanded = expanded;
            r.currentFile = currentFile;
            return r;
        }

        static Row bookmark(Bookmark b) {
            Row r = new Row();
            r.type = TYPE_BOOKMARK;
            r.bookmark = b;
            r.filePath = b.getFilePath();
            r.fileName = b.getFileName();
            return r;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private Listener listener;
    private int folderCount = 0;

    private int dialogBgColor = Color.TRANSPARENT;
    private int folderBgColor = Color.TRANSPARENT;
    private int folderBorderColor = Color.LTGRAY;
    private int textColor = Color.WHITE;
    private int subTextColor = Color.LTGRAY;
    private int pathTextColor = Color.GRAY;

    public void setThemeColors(int dialogBgColor, int textColor, int subTextColor, int folderBgColor) {
        this.dialogBgColor = dialogBgColor;
        this.folderBgColor = folderBgColor;
        this.textColor = textColor;
        this.subTextColor = subTextColor;
        this.pathTextColor = blendColors(dialogBgColor, subTextColor, 0.55f);

        // Stronger folder boundary so expanded/shrunk file folders are clearly visible.
        this.folderBorderColor = blendColors(folderBgColor, textColor, 0.42f);
        notifyDataSetChanged();
    }

    private static int blendColors(int bottomColor, int topColor, float topAlpha) {
        topAlpha = Math.max(0f, Math.min(1f, topAlpha));
        float bottomAlpha = 1f - topAlpha;
        int r = Math.round(Color.red(topColor) * topAlpha + Color.red(bottomColor) * bottomAlpha);
        int g = Math.round(Color.green(topColor) * topAlpha + Color.green(bottomColor) * bottomAlpha);
        int b = Math.round(Color.blue(topColor) * topAlpha + Color.blue(bottomColor) * bottomAlpha);
        return Color.rgb(r, g, b);
    }

    private GradientDrawable rowBackground(int color, int strokeColor, float radiusDp, float strokeDp, View view) {
        float density = view.getResources().getDisplayMetrics().density;

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radiusDp * density);
        bg.setStroke(Math.max(1, Math.round(strokeDp * density)), strokeColor);
        return bg;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int getFolderCount() {
        return folderCount;
    }

    public void setBookmarks(List<Bookmark> allBookmarks, Set<String> expandedFolders, String currentFilePath) {
        rows.clear();

        if (expandedFolders == null) expandedFolders = new HashSet<>();

        Map<String, List<Bookmark>> grouped = new LinkedHashMap<>();
        List<Bookmark> sorted = new ArrayList<>(allBookmarks);

        Collections.sort(sorted, (a, b) -> {
            String af = safeFileName(a);
            String bf = safeFileName(b);
            int byName = af.compareToIgnoreCase(bf);
            if (byName != 0) return byName;
            String ap = a.getFilePath() != null ? a.getFilePath() : "";
            String bp = b.getFilePath() != null ? b.getFilePath() : "";
            int byPath = ap.compareToIgnoreCase(bp);
            if (byPath != 0) return byPath;
            return Integer.compare(a.getCharPosition(), b.getCharPosition());
        });

        for (Bookmark b : sorted) {
            String key = b.getFilePath() != null ? b.getFilePath() : "";
            if (!grouped.containsKey(key)) grouped.put(key, new ArrayList<>());
            grouped.get(key).add(b);
        }

        folderCount = grouped.size();

        for (Map.Entry<String, List<Bookmark>> entry : grouped.entrySet()) {
            String path = entry.getKey();
            List<Bookmark> bookmarks = entry.getValue();
            Collections.sort(bookmarks, Comparator.comparingInt(Bookmark::getCharPosition));

            String fileName = bookmarks.isEmpty() ? new File(path).getName() : safeFileName(bookmarks.get(0));
            boolean expanded = expandedFolders.contains(path);
            boolean current = currentFilePath != null && currentFilePath.equals(path);

            rows.add(Row.folder(path, fileName, bookmarks.size(), expanded, current));
            if (expanded) {
                for (Bookmark b : bookmarks) rows.add(Row.bookmark(b));
            }
        }

        notifyDataSetChanged();
    }

    private static String safeFileName(Bookmark b) {
        if (b.getFileName() != null && !b.getFileName().isEmpty()) return b.getFileName();
        if (b.getFilePath() != null && !b.getFilePath().isEmpty()) return new File(b.getFilePath()).getName();
        return "(unknown file)";
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_FOLDER) {
            View view = inflater.inflate(R.layout.item_bookmark_folder, parent, false);
            return new FolderHolder(view);
        }
        View view = inflater.inflate(R.layout.item_bookmark, parent, false);
        return new BookmarkHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof FolderHolder) {
            ((FolderHolder) holder).bind(row);
        } else {
            ((BookmarkHolder) holder).bind(row.bookmark);
        }
    }

    class FolderHolder extends RecyclerView.ViewHolder {
        TextView arrow, title, meta, path;

        FolderHolder(View itemView) {
            super(itemView);
            arrow = itemView.findViewById(R.id.folder_arrow);
            title = itemView.findViewById(R.id.folder_title);
            meta = itemView.findViewById(R.id.folder_meta);
            path = itemView.findViewById(R.id.folder_path);
            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onFolderClick(rows.get(pos).filePath);
                }
            });
        }

        void bind(Row row) {
            itemView.setBackground(rowBackground(folderBgColor, folderBorderColor, 4f, 1.6f, itemView));

            arrow.setText(row.expanded ? "▾" : "▸");
            arrow.setTextColor(textColor);

            String prefix = row.currentFile ? "현재 파일  •  " : "";
            title.setText(prefix + row.fileName);
            title.setTextColor(textColor);

            meta.setText(row.count + (row.count == 1 ? " bookmark" : " bookmarks"));
            meta.setTextColor(blendColors(folderBgColor, textColor, 0.78f));

            path.setText(row.filePath != null ? row.filePath : "");
            path.setTextColor(blendColors(folderBgColor, textColor, 0.55f));
        }
    }

    class BookmarkHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView excerpt;
        TextView meta;
        ImageButton btnDelete;

        BookmarkHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.bookmark_title);
            excerpt = itemView.findViewById(R.id.bookmark_excerpt);
            meta = itemView.findViewById(R.id.bookmark_meta);
            btnDelete = itemView.findViewById(R.id.bookmark_delete);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onBookmarkClick(rows.get(pos).bookmark);
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onBookmarkEdit(rows.get(pos).bookmark);
                }
                return true;
            });

            btnDelete.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onBookmarkDelete(rows.get(pos).bookmark);
                }
            });
        }

        void bind(Bookmark bookmark) {
            // Bookmark rows should blend into the bookmark dialog background, not show black boxes.
            itemView.setBackgroundColor(Color.TRANSPARENT);

            String display = bookmark.getDisplayText();
            title.setText(display != null && !display.isEmpty()
                    ? display
                    : "Position " + bookmark.getCharPosition());
            title.setTextColor(textColor);

            String excerptText = bookmark.getExcerpt();
            if (excerptText != null && !excerptText.isEmpty()
                    && !excerptText.equals(display)) {
                excerpt.setText(excerptText);
                excerpt.setTextColor(subTextColor);
                excerpt.setVisibility(View.VISIBLE);
            } else {
                excerpt.setVisibility(View.GONE);
            }

            String dateStr = dateFormat.format(new Date(bookmark.getUpdatedAt()));
            int end = bookmark.getEndPosition();
            String range = end > bookmark.getCharPosition()
                    ? bookmark.getCharPosition() + " - " + end
                    : String.valueOf(bookmark.getCharPosition());
            meta.setText("Position " + range + "  •  " + dateStr);
            meta.setTextColor(pathTextColor);

            btnDelete.setColorFilter(subTextColor);
            btnDelete.setBackgroundColor(Color.TRANSPARENT);
        }
    }
}
