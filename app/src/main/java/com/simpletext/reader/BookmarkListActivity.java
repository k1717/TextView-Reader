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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.graphics.Insets;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.simpletext.reader.adapter.BookmarkFolderAdapter;
import com.simpletext.reader.model.Bookmark;
import com.simpletext.reader.util.BookmarkManager;
import com.simpletext.reader.util.PrefsManager;
import com.simpletext.reader.util.FileUtils;

import java.io.File;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class BookmarkListActivity extends AppCompatActivity
        implements BookmarkFolderAdapter.Listener {

    private RecyclerView recyclerView;
    private BookmarkFolderAdapter adapter;
    private final Set<String> expandedFolders = new HashSet<>();
    private boolean didInitialBookmarkExpansion = false;
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

    private void showBookmarkDeleteConfirm(Bookmark bookmark) {
        boolean dark = isDarkUi();

        int baseBg = dark ? Color.rgb(0, 0, 0) : Color.rgb(255, 255, 255);
        int bg = blendColors(baseBg, dark ? Color.WHITE : Color.BLACK, dark ? 0.12f : 0.06f);
        int fg = readableTextColorForBackground(bg);
        int sub = dark ? Color.rgb(190, 190, 190) : Color.rgb(75, 75, 75);
        int deleteColor = isLightColor(bg) ? Color.rgb(125, 45, 45) : Color.rgb(255, 175, 175);

        LinearLayout dialogPanel = new LinearLayout(this);
        dialogPanel.setOrientation(LinearLayout.VERTICAL);
        dialogPanel.setBackgroundColor(Color.TRANSPARENT);

        dialogPanel.addView(makeBookmarkDialogTitle(getString(R.string.delete_bookmark), fg),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.TRANSPARENT);
        box.setPadding(dpToPx(22), dpToPx(12), dpToPx(22), dpToPx(10));

        TextView message = new TextView(this);
        message.setText(bookmark.getFileName() + "\n\n" + bookmark.getDisplayText());
        message.setTextColor(fg);
        message.setTextSize(14f);
        message.setLineSpacing(0f, 1.15f);
        message.setPadding(0, dpToPx(4), 0, dpToPx(10));
        box.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView warning = new TextView(this);
        warning.setText(getString(R.string.delete_this_bookmark));
        warning.setTextColor(sub);
        warning.setTextSize(13f);
        warning.setPadding(0, 0, 0, dpToPx(2));
        box.addView(warning, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        dialogPanel.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = makeBookmarkDialogActionRow(bg, fg);
        TextView cancel = makeBookmarkDialogActionText(getString(R.string.cancel), sub, Gravity.CENTER);
        TextView delete = makeBookmarkDialogActionText(getString(R.string.delete), deleteColor, Gravity.CENTER);
        actions.addView(cancel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        actions.addView(delete, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        dialogPanel.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        android.app.Dialog dialog = createBookmarkDialog(dialogPanel, bg);
        cancel.setOnClickListener(v -> dialog.dismiss());
        delete.setOnClickListener(v -> {
            bookmarkManager.deleteBookmark(bookmark.getId());
            loadBookmarks();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showBookmarkFolderDeleteConfirm(String folderFilePath,
                                                 String expansionKey,
                                                 String folderName,
                                                 int bookmarkCount) {
        boolean dark = isDarkUi();

        int baseBg = dark ? Color.rgb(0, 0, 0) : Color.rgb(255, 255, 255);
        int bg = blendColors(baseBg, dark ? Color.WHITE : Color.BLACK, dark ? 0.12f : 0.06f);
        int fg = readableTextColorForBackground(bg);
        int sub = dark ? Color.rgb(190, 190, 190) : Color.rgb(75, 75, 75);
        int deleteColor = isLightColor(bg) ? Color.rgb(125, 45, 45) : Color.rgb(255, 175, 175);

        LinearLayout dialogPanel = new LinearLayout(this);
        dialogPanel.setOrientation(LinearLayout.VERTICAL);
        dialogPanel.setBackgroundColor(Color.TRANSPARENT);

        dialogPanel.addView(makeBookmarkDialogTitle(getString(R.string.delete_bookmark_folder), fg),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.TRANSPARENT);
        box.setPadding(dpToPx(22), dpToPx(12), dpToPx(22), dpToPx(10));

        TextView message = new TextView(this);
        String displayName = folderName != null && !folderName.trim().isEmpty()
                ? folderName.trim()
                : getString(R.string.bookmark);
        message.setText(displayName + "\n\n"
                + getString(R.string.delete_bookmark_folder_message, bookmarkCount)
                + "\n" + getString(R.string.delete_bookmark_folder_note));
        message.setTextColor(fg);
        message.setTextSize(14f);
        message.setLineSpacing(0f, 1.15f);
        message.setPadding(0, dpToPx(4), 0, dpToPx(8));
        box.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        dialogPanel.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = makeBookmarkDialogActionRow(bg, fg);
        TextView cancel = makeBookmarkDialogActionText(getString(R.string.cancel), sub, Gravity.CENTER);
        TextView delete = makeBookmarkDialogActionText(getString(R.string.delete), deleteColor, Gravity.CENTER);
        actions.addView(cancel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        actions.addView(delete, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        dialogPanel.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        android.app.Dialog dialog = createBookmarkDialog(dialogPanel, bg);
        cancel.setOnClickListener(v -> dialog.dismiss());
        delete.setOnClickListener(v -> {
            bookmarkManager.deleteBookmarksForFile(folderFilePath);
            expandedFolders.remove(folderFilePath);
            expandedFolders.remove(expansionKey);
            loadBookmarks();
            dialog.dismiss();
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

        // Keep the all-bookmarks page expansion/collapse as responsive as the
        // in-viewer bookmark windows. The default item animator made folder
        // open/close feel sluggish and visually jumpy.
        recyclerView.setItemAnimator(null);
        recyclerView.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(8));
        recyclerView.setClipToPadding(false);

        applyBookmarkPageThemeAndInsets();

        bookmarkManager = BookmarkManager.getInstance(this);

        adapter = new BookmarkFolderAdapter();
        boolean dark = isDarkUi();
        int bg = dark ? Color.rgb(0, 0, 0) : Color.rgb(255, 255, 255);
        int fg = dark ? Color.rgb(232, 234, 237) : Color.rgb(32, 33, 36);
        int sub = dark ? Color.rgb(176, 176, 176) : Color.rgb(95, 99, 104);
        int panel = dark ? Color.rgb(24, 24, 24) : Color.rgb(245, 245, 245);
        adapter.setThemeColors(bg, fg, sub, panel);
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
            // Keep main bookmark folders collapsed by default. Users can expand only
            // the folders they need, which keeps the all-bookmarks page compact.
            if (!didInitialBookmarkExpansion) {
                didInitialBookmarkExpansion = true;
                expandedFolders.clear();
            }
            adapter.setBookmarks(bookmarks, expandedFolders, null);
        }
    }

    @Override
    public void onFolderClick(String folderFilePath) {
        if (expandedFolders.contains(folderFilePath)) expandedFolders.remove(folderFilePath);
        else expandedFolders.add(folderFilePath);
        loadBookmarks();
    }

    @Override
    public void onFolderDelete(String folderFilePath, String expansionKey, String folderName, int bookmarkCount) {
        showBookmarkFolderDeleteConfirm(folderFilePath, expansionKey, folderName, bookmarkCount);
    }

    @Override
    public void onBookmarkClick(Bookmark bookmark) {
        openBookmarkTarget(bookmark);
    }

    @Override
    public void onBookmarkDelete(Bookmark bookmark) {
        showBookmarkDeleteConfirm(bookmark);
    }

    private int bookmarkDialogCornerRadiusPx() {
        return dpToPx(24);
    }

    private int bookmarkDialogBorderColor(int bgColor) {
        int fg = readableTextColorForBackground(bgColor);
        return blendColors(bgColor, fg, isLightColor(bgColor) ? 0.58f : 0.78f);
    }

    private GradientDrawable bookmarkDialogFillBackground(int bgColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(bgColor);
        drawable.setCornerRadius(bookmarkDialogCornerRadiusPx());
        return drawable;
    }

    private Drawable bookmarkDialogBorderOverlay(int bgColor) {
        final int borderColor = bookmarkDialogBorderColor(bgColor);
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final android.graphics.RectF rect = new android.graphics.RectF();

            {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dpToPx(2));
                paint.setColor(borderColor);
            }

            @Override
            public void draw(Canvas canvas) {
                android.graphics.Rect bounds = getBounds();
                float half = paint.getStrokeWidth() / 2f;
                rect.set(bounds.left + half, bounds.top + half,
                        bounds.right - half, bounds.bottom - half);
                float radius = Math.max(0f, bookmarkDialogCornerRadiusPx() - half);
                canvas.drawRoundRect(rect, radius, radius, paint);
            }

            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
    }

    private TextView makeBookmarkDialogTitle(String text, int fgColor) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(fgColor);
        title.setTextSize(20f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dpToPx(22), dpToPx(18), dpToPx(22), dpToPx(8));
        title.setBackgroundColor(Color.TRANSPARENT);
        return title;
    }

    private TextView makeBookmarkDialogActionText(String label, int textColor, int gravity) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(textColor);
        button.setTextSize(16f);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setGravity(gravity);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dpToPx(14), 0, dpToPx(14), 0);
        return button;
    }

    private LinearLayout makeBookmarkDialogActionRow(int bgColor, int fgColor) {
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        actions.setBackground(actionPanelBackground(
                blendColors(bgColor, fgColor, isLightColor(bgColor) ? 0.025f : 0.040f),
                blendColors(bgColor, fgColor, 0.34f)));
        actions.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        return actions;
    }

    private android.app.Dialog createBookmarkDialog(@NonNull View content, int bgColor) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        android.widget.FrameLayout outerFrame = new android.widget.FrameLayout(this);
        outerFrame.setBackground(bookmarkDialogFillBackground(bgColor));
        outerFrame.setForeground(bookmarkDialogBorderOverlay(bgColor));
        outerFrame.setClipToOutline(true);
        outerFrame.setClipChildren(true);
        outerFrame.setClipToPadding(true);

        content.setBackgroundColor(Color.TRANSPARENT);
        if (content instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) content;
            group.setClipChildren(true);
            group.setClipToPadding(true);
        }

        outerFrame.addView(content, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(outerFrame);
        configureBookmarkDialogWindow(dialog);
        dialog.setOnShowListener(d -> configureBookmarkDialogWindow(dialog));
        return dialog;
    }

    private void configureBookmarkDialogWindow(@NonNull android.app.Dialog dialog) {
        android.view.Window window = dialog.getWindow();
        if (window == null) return;

        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
        lp.copyFrom(window.getAttributes());
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        lp.width = Math.min(screenWidth - dpToPx(28), dpToPx(460));
        lp.height = android.view.WindowManager.LayoutParams.WRAP_CONTENT;
        lp.y = dpToPx(74);
        lp.dimAmount = 0.16f;
        window.setAttributes(lp);
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setWindowAnimations(0);
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    @Override
    public void onBookmarkEdit(Bookmark bookmark) {
        boolean dark = isDarkUi();

        int baseBg = dark ? Color.rgb(0, 0, 0) : Color.rgb(255, 255, 255);
        int bg = blendColors(baseBg, dark ? Color.WHITE : Color.BLACK, dark ? 0.12f : 0.06f);
        int fg = readableTextColorForBackground(bg);
        int sub = dark ? Color.rgb(190, 190, 190) : Color.rgb(75, 75, 75);
        int border = blendColors(bg, fg, 0.48f);
        int panel = blendColors(bg, fg, dark ? 0.12f : 0.06f);

        LinearLayout dialogPanel = new LinearLayout(this);
        dialogPanel.setOrientation(LinearLayout.VERTICAL);
        dialogPanel.setBackgroundColor(Color.TRANSPARENT);

        dialogPanel.addView(makeBookmarkDialogTitle(getString(R.string.edit_bookmark_memo), fg),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.TRANSPARENT);
        box.setPadding(dpToPx(22), dpToPx(12), dpToPx(22), dpToPx(10));

        TextView message = new TextView(this);
        message.setText(bookmark.getFileName() + "\n\n" + bookmark.getDisplayText());
        message.setTextColor(sub);
        message.setTextSize(13f);
        message.setLineSpacing(0f, 1.15f);
        message.setPadding(0, 0, 0, dpToPx(10));
        box.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(bookmark.getLabel());
        input.setHint(getString(R.string.optional_memo));
        input.setSelectAllOnFocus(true);
        input.setTextColor(fg);
        input.setHintTextColor(sub);
        input.setPadding(dpToPx(14), 0, dpToPx(14), 0);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(panel);
        inputBg.setCornerRadius(dpToPx(12));
        inputBg.setStroke(Math.max(1, dpToPx(1)), border);
        input.setBackground(inputBg);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(52)));
        dialogPanel.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = makeBookmarkDialogActionRow(bg, fg);

        TextView cancel = makeBookmarkDialogActionText(getString(R.string.cancel), sub, Gravity.CENTER);
        TextView clear = makeBookmarkDialogActionText(getString(R.string.clear_memo), sub, Gravity.CENTER);
        TextView save = makeBookmarkDialogActionText(getString(R.string.save), fg, Gravity.CENTER);
        actions.addView(cancel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        actions.addView(clear, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        actions.addView(save, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(46)));
        dialogPanel.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        android.app.Dialog dialog = createBookmarkDialog(dialogPanel, bg);
        cancel.setOnClickListener(v -> dialog.dismiss());
        clear.setOnClickListener(v -> {
            bookmark.setLabel("");
            bookmarkManager.updateBookmark(bookmark);
            loadBookmarks();
            dialog.dismiss();
        });
        save.setOnClickListener(v -> {
            bookmark.setLabel(input.getText().toString().trim());
            bookmarkManager.updateBookmark(bookmark);
            loadBookmarks();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void openBookmarkTarget(@NonNull Bookmark bookmark) {
        String path = bookmark.getFilePath();
        if (path == null || path.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.file_not_found_prefix) + "(missing path)", Toast.LENGTH_SHORT).show();
            return;
        }

        File target = new File(path.trim());
        if (!target.exists()) {
            Toast.makeText(this, getString(R.string.file_not_found_prefix) + path, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent;
        String targetPath = target.getAbsolutePath();
        String name = target.getName();
        if (FileUtils.isPdfFile(name)) {
            intent = new Intent(this, PdfReaderActivity.class);
            intent.putExtra(PdfReaderActivity.EXTRA_FILE_PATH, targetPath);
            intent.putExtra(PdfReaderActivity.EXTRA_JUMP_TO_PAGE, bookmark.getCharPosition());
        } else if (FileUtils.isEpubFile(name) || FileUtils.isWordFile(name)) {
            intent = new Intent(this, DocumentPageActivity.class);
            intent.putExtra(DocumentPageActivity.EXTRA_FILE_PATH, targetPath);
            intent.putExtra(DocumentPageActivity.EXTRA_JUMP_TO_PAGE, bookmark.getCharPosition());
        } else {
            intent = new Intent(this, ReaderActivity.class);
            intent.putExtra(ReaderActivity.EXTRA_FILE_PATH, targetPath);
            intent.putExtra(ReaderActivity.EXTRA_JUMP_TO_POSITION, bookmark.getCharPosition());
            intent.putExtra(ReaderActivity.EXTRA_JUMP_DISPLAY_PAGE, bookmark.getPageNumber());
            intent.putExtra(ReaderActivity.EXTRA_JUMP_TOTAL_PAGES, bookmark.getTotalPages());
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
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
