package com.simpletext.reader;

import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.content.res.Configuration;
import android.view.MenuItem;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.graphics.Insets;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.simpletext.reader.adapter.BookmarkAdapter;
import com.simpletext.reader.model.Bookmark;
import com.simpletext.reader.util.BookmarkManager;
import com.simpletext.reader.util.PrefsManager;

import java.io.File;
import java.util.List;

public class BookmarkListActivity extends AppCompatActivity
        implements BookmarkAdapter.OnBookmarkClickListener {

    private RecyclerView recyclerView;
    private BookmarkAdapter adapter;
    private TextView emptyText;
    private BookmarkManager bookmarkManager;


    private boolean isDarkUi() {
        int mask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyBookmarkPageThemeAndInsets() {
        boolean dark = isDarkUi();

        int bg = dark ? Color.rgb(0, 0, 0) : Color.rgb(255, 255, 255);
        int bar = dark ? Color.rgb(0, 0, 0) : Color.rgb(32, 33, 36);
        int text = dark ? Color.rgb(232, 234, 237) : Color.rgb(32, 33, 36);
        int sub = dark ? Color.rgb(176, 176, 176) : Color.rgb(95, 99, 104);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(bar);
        getWindow().setNavigationBarColor(bg);

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(!dark);

        View root = findViewById(R.id.bookmark_root);
        View appbar = findViewById(R.id.bookmark_appbar);
        View content = findViewById(R.id.bookmark_content);

        if (root != null) root.setBackgroundColor(bg);
        if (appbar != null) appbar.setBackgroundColor(bar);
        if (content != null) content.setBackgroundColor(bg);

        if (emptyText != null) emptyText.setTextColor(sub);

        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                View appBarView = findViewById(R.id.bookmark_appbar);
                View contentView = findViewById(R.id.bookmark_content);

                if (appBarView != null) {
                    appBarView.setPadding(
                            appBarView.getPaddingLeft(),
                            bars.top,
                            appBarView.getPaddingRight(),
                            appBarView.getPaddingBottom()
                    );
                }

                if (contentView != null) {
                    contentView.setPadding(
                            contentView.getPaddingLeft(),
                            contentView.getPaddingTop(),
                            contentView.getPaddingRight(),
                            bars.bottom
                    );
                }

                return insets;
            });
            ViewCompat.requestApplyInsets(root);
        }
    }



    private int blendColors(int bottomColor, int topColor, float topAlpha) {
        topAlpha = Math.max(0f, Math.min(1f, topAlpha));
        float bottomAlpha = 1f - topAlpha;
        int r = Math.round(Color.red(topColor) * topAlpha + Color.red(bottomColor) * bottomAlpha);
        int g = Math.round(Color.green(topColor) * topAlpha + Color.green(bottomColor) * bottomAlpha);
        int b = Math.round(Color.blue(topColor) * topAlpha + Color.blue(bottomColor) * bottomAlpha);
        return Color.rgb(r, g, b);
    }

    private boolean isLightColor(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b);
        return luminance > 160;
    }

    private int readableTextColorForBackground(int backgroundColor) {
        return isLightColor(backgroundColor) ? Color.rgb(32, 32, 32) : Color.rgb(232, 234, 237);
    }



    private Drawable actionPanelBackground(int fillColor, int lineColor) {
        return new Drawable() {
            private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            {
                linePaint.setColor(lineColor);
                linePaint.setStyle(Paint.Style.FILL);
            }

            @Override
            public void draw(Canvas canvas) {
                canvas.drawColor(fillColor);
                float h = Math.max(1f, getResources().getDisplayMetrics().density);
                canvas.drawRect(0, 0, getBounds().width(), h, linePaint);
            }

            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(ColorFilter colorFilter) { linePaint.setColorFilter(colorFilter); }
            @Override public int getOpacity() { return PixelFormat.OPAQUE; }
        };
    }

    private void forceDialogButtonPanelBackground(AlertDialog dialog, int bgColor) {
        if (dialog == null) return;

        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);

        Button first = positive != null ? positive : (negative != null ? negative : neutral);
        if (first == null) return;

        View panel = first.getParent() instanceof View ? (View) first.getParent() : null;
        if (panel == null) return;

        int lineColor = blendColors(bgColor, readableTextColorForBackground(bgColor), 0.34f);
        int panelFill = blendColors(bgColor, readableTextColorForBackground(bgColor),
                isLightColor(bgColor) ? 0.025f : 0.040f);

        panel.setBackground(actionPanelBackground(panelFill, lineColor));
        panel.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        panel.setMinimumHeight(dpToPx(50));
        panel.setClipToOutline(false);

        if (panel instanceof ViewGroup) {
            ((ViewGroup) panel).setClipChildren(false);
            ((ViewGroup) panel).setClipToPadding(false);
        }
    }

    private void styleDialogButton(Button button, int textColor, int bgColor) {
        if (button == null) return;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            button.setBackgroundTintList(null);
        }
        button.setStateListAnimator(null);
        button.setTextColor(textColor);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setAllCaps(false);
        button.setMinWidth(dpToPx(72));
        button.setMinimumWidth(dpToPx(72));
        button.setMinHeight(dpToPx(40));
        button.setMinimumHeight(dpToPx(40));
        button.setPadding(dpToPx(14), 0, dpToPx(14), 0);

        android.view.ViewGroup.LayoutParams rawLp = button.getLayoutParams();
        if (rawLp instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rawLp;
            lp.setMargins(dpToPx(2), 0, dpToPx(2), 0);
            button.setLayoutParams(lp);
        }
    }

    private void showBookmarkDeleteConfirm(Bookmark bookmark) {
        boolean dark = isDarkUi();

        int baseBg = dark ? Color.rgb(0, 0, 0) : Color.rgb(255, 255, 255);
        int bg = blendColors(baseBg, dark ? Color.WHITE : Color.BLACK, dark ? 0.12f : 0.06f);
        int fg = readableTextColorForBackground(bg);
        int sub = dark ? Color.rgb(190, 190, 190) : Color.rgb(75, 75, 75);
        int border = blendColors(bg, fg, 0.48f);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(bg);
        box.setPadding(dpToPx(22), dpToPx(12), dpToPx(22), dpToPx(8));

        TextView message = new TextView(this);
        message.setText(bookmark.getFileName() + "\n\n" + bookmark.getDisplayText());
        message.setTextColor(fg);
        message.setTextSize(14f);
        message.setLineSpacing(0f, 1.15f);
        message.setPadding(0, dpToPx(4), 0, dpToPx(8));
        box.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView warning = new TextView(this);
        warning.setText(getString(R.string.delete_this_bookmark));
        warning.setTextColor(sub);
        warning.setTextSize(13f);
        box.addView(warning, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText(getString(R.string.delete_bookmark));
        title.setTextColor(fg);
        title.setTextSize(20f);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dpToPx(22), dpToPx(18), dpToPx(22), dpToPx(8));
        title.setBackgroundColor(bg);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(title)
                .setView(box)
                .setPositiveButton(getString(R.string.delete), (d, w) -> {
                    bookmarkManager.deleteBookmark(bookmark.getId());
                    loadBookmarks();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                GradientDrawable drawable = new GradientDrawable();
                drawable.setColor(bg);
                drawable.setCornerRadius(dpToPx(4));
                drawable.setStroke(Math.max(1, dpToPx(1)), border);
                dialog.getWindow().setBackgroundDrawable(drawable);
                dialog.getWindow().getDecorView().setBackground(drawable);

                android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                lp.dimAmount = 0.14f;
                dialog.getWindow().setAttributes(lp);
                dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
            forceDialogButtonPanelBackground(dialog, bg);
            int deleteColor = isLightColor(bg) ? Color.rgb(95, 35, 35) : Color.rgb(255, 170, 170);
            styleDialogButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), deleteColor, bg);
            styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), sub, bg);
        });
        dialog.show();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        PrefsManager.getInstance(this).applyLanguage(
                PrefsManager.getInstance(this).getLanguageMode());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmark_list);
        applyBookmarkPageThemeAndInsets();

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(isDarkUi() ? Color.rgb(0, 0, 0) : Color.rgb(32, 33, 36));
        toolbar.setTitleTextColor(Color.WHITE);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.all_bookmarks));
        }

        recyclerView = findViewById(R.id.bookmark_recycler);
        emptyText = findViewById(R.id.empty_text);
        applyBookmarkPageThemeAndInsets();

        bookmarkManager = BookmarkManager.getInstance(this);

        adapter = new BookmarkAdapter();
        adapter.setShowFileName(true);
        adapter.setListener(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadBookmarks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBookmarks();
    }

    private void loadBookmarks() {
        List<Bookmark> bookmarks = bookmarkManager.getAllBookmarks();

        if (bookmarks.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.setBookmarks(bookmarks);
        }
    }

    @Override
    public void onBookmarkClick(Bookmark bookmark) {
        File file = new File(bookmark.getFilePath());
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.file_not_found_prefix) + bookmark.getFilePath(), Toast.LENGTH_LONG).show();
            return;
        }

        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_FILE_PATH, bookmark.getFilePath());
        intent.putExtra(ReaderActivity.EXTRA_JUMP_TO_POSITION, bookmark.getCharPosition());
        startActivity(intent);
    }

    @Override
    public void onBookmarkDelete(Bookmark bookmark) {
        showBookmarkDeleteConfirm(bookmark);
    }

    @Override
    public void onBookmarkEdit(Bookmark bookmark) {
        EditText input = new EditText(this);
        input.setText(bookmark.getLabel());
        input.setHint(getString(R.string.bookmark_label));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.edit_bookmark))
                .setView(input)
                .setPositiveButton(getString(R.string.save), (d, w) -> {
                    bookmark.setLabel(input.getText().toString().trim());
                    bookmarkManager.updateBookmark(bookmark);
                    loadBookmarks();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
