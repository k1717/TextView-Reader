package com.simpletext.reader.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.simpletext.reader.model.TextChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for long text files.
 * Uses smaller chunks and cheap text layout settings to reduce drag lag.
 */
public class TextChunkAdapter extends RecyclerView.Adapter<TextChunkAdapter.ChunkViewHolder> {
    private final List<TextChunk> chunks = new ArrayList<>();

    private float textSizeSp = 18f;
    private float lineSpacingMultiplier = 1.5f;
    private int textColor = 0xFFE0E0E0;
    private int marginHorizontalPx = 24;
    private int marginVerticalPx = 8;
    private Typeface typeface = Typeface.DEFAULT;

    public TextChunkAdapter() {
        setHasStableIds(true);
    }

    public void setChunks(List<TextChunk> newChunks) {
        chunks.clear();
        if (newChunks != null) chunks.addAll(newChunks);
        notifyDataSetChanged();
    }

    public List<TextChunk> getChunks() { return chunks; }

    public TextChunk getChunk(int position) {
        if (position < 0 || position >= chunks.size()) return null;
        return chunks.get(position);
    }

    public void setTextStyle(float textSizeSp,
                             float lineSpacingMultiplier,
                             int textColor,
                             int marginHorizontalPx,
                             int marginVerticalPx,
                             Typeface typeface) {
        this.textSizeSp = textSizeSp;
        this.lineSpacingMultiplier = lineSpacingMultiplier;
        this.textColor = textColor;
        this.marginHorizontalPx = marginHorizontalPx;
        this.marginVerticalPx = Math.max(0, marginVerticalPx / 2);
        this.typeface = typeface != null ? typeface : Typeface.DEFAULT;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChunkViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        TextView tv = new TextView(context);
        tv.setId(android.R.id.text1);
        tv.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setIncludeFontPadding(true);
        tv.setTextIsSelectable(false);
        tv.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        tv.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tv.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
            tv.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        }
        return new ChunkViewHolder(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull ChunkViewHolder holder, int position) {
        TextChunk chunk = chunks.get(position);
        TextView tv = holder.textView;
        tv.setText(chunk.getText());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        tv.setLineSpacing(0, lineSpacingMultiplier);
        tv.setTextColor(textColor);
        tv.setTypeface(typeface);
        tv.setPadding(marginHorizontalPx, marginVerticalPx, marginHorizontalPx, marginVerticalPx);
    }

    @Override public long getItemId(int position) {
        TextChunk c = getChunk(position);
        return c != null ? c.getStartChar() : RecyclerView.NO_ID;
    }

    @Override public int getItemCount() { return chunks.size(); }

    public static class ChunkViewHolder extends RecyclerView.ViewHolder {
        public final TextView textView;
        public ChunkViewHolder(@NonNull TextView itemView) {
            super(itemView);
            this.textView = itemView;
        }
    }
}
