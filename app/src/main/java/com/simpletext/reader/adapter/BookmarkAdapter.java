package com.simpletext.reader.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.simpletext.reader.R;
import com.simpletext.reader.model.Bookmark;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TekView-style bookmark row:
 * - excerpt/context is the main visible item
 * - position range and line are visible
 * - optional user memo appears below the excerpt
 */
public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.ViewHolder> {

    public interface OnBookmarkClickListener {
        void onBookmarkClick(Bookmark bookmark);
        void onBookmarkDelete(Bookmark bookmark);
        void onBookmarkEdit(Bookmark bookmark);
    }

    private List<Bookmark> bookmarks = new ArrayList<>();
    private OnBookmarkClickListener listener;
    private boolean showFileName = false;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public void setListener(OnBookmarkClickListener listener) {
        this.listener = listener;
    }

    public void setShowFileName(boolean show) {
        this.showFileName = show;
    }

    public void setBookmarks(List<Bookmark> list) {
        this.bookmarks = new ArrayList<>(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bookmark, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(bookmarks.get(position));
    }

    @Override
    public int getItemCount() {
        return bookmarks.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView excerpt;
        TextView meta;
        ImageButton btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.bookmark_title);
            excerpt = itemView.findViewById(R.id.bookmark_excerpt);
            meta = itemView.findViewById(R.id.bookmark_meta);
            btnDelete = itemView.findViewById(R.id.bookmark_delete);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onBookmarkClick(bookmarks.get(pos));
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onBookmarkEdit(bookmarks.get(pos));
                }
                return true;
            });

            btnDelete.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onBookmarkDelete(bookmarks.get(pos));
                }
            });
        }

        void bind(Bookmark bookmark) {
            String main = bookmark.getExcerpt();
            if (main == null || main.trim().isEmpty()) {
                main = bookmark.getDisplayText();
            }
            if (main == null || main.trim().isEmpty()) {
                main = "Position " + bookmark.getCharPosition();
            }

            // Original-style: excerpt/context is the main visible line.
            title.setText(main.trim());

            String memo = bookmark.getLabel();
            if (showFileName) {
                String file = bookmark.getFileName() != null ? bookmark.getFileName() : "";
                if (memo != null && !memo.isEmpty()) {
                    excerpt.setText(file + "  •  Memo: " + memo);
                } else {
                    excerpt.setText(file);
                }
                excerpt.setVisibility(file.isEmpty() && (memo == null || memo.isEmpty()) ? View.GONE : View.VISIBLE);
            } else if (memo != null && !memo.isEmpty()) {
                excerpt.setText("Memo: " + memo);
                excerpt.setVisibility(View.VISIBLE);
            } else {
                excerpt.setVisibility(View.GONE);
            }

            int start = Math.max(0, bookmark.getCharPosition());
            int end = Math.max(start, bookmark.getEndPosition());
            String dateStr = dateFormat.format(new Date(bookmark.getUpdatedAt()));

            meta.setText("Line " + bookmark.getLineNumber()
                    + "  •  Pos " + start + "-" + end
                    + "  •  " + dateStr);

            title.setTextColor(Color.rgb(232, 232, 232));
            excerpt.setTextColor(Color.rgb(190, 190, 190));
            meta.setTextColor(Color.rgb(150, 150, 150));
            btnDelete.setColorFilter(Color.rgb(180, 180, 180));
        }
    }
}
