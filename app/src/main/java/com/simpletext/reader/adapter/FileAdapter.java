package com.simpletext.reader.adapter;

import android.view.LayoutInflater;
import android.view.View;
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

    private void sortFiles() {
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

        ViewHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.file_icon);
            name = v.findViewById(R.id.file_name);
            info = v.findViewById(R.id.file_info);
            v.setOnClickListener(x -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) listener.onFileClick(files.get(pos));
            });
            v.setOnLongClickListener(x -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) listener.onFileLongClick(files.get(pos));
                return true;
            });
        }

        void bind(File file) {
            name.setText(file.getName());
            if (file.isDirectory()) {
                icon.setImageResource(R.drawable.ic_folder);
                File[] children = file.listFiles();
                info.setText((children != null ? children.length : 0) + " items");
            } else {
                icon.setImageResource(R.drawable.ic_text_file);
                String size = FileUtils.formatFileSize(file.length());
                String date = dateFormat.format(new Date(file.lastModified()));
                info.setText(size + "  •  " + date);
            }
        }
    }
}
