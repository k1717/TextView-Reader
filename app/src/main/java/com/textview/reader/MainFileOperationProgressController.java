package com.textview.reader;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.textview.reader.util.FileOperationProgress;
import com.textview.reader.util.FileUtils;

final class MainFileOperationProgressController {
    private final MainActivity activity;
    @Nullable private android.app.Dialog dialog;
    @Nullable private FileOperationProgress activeProgress;

    MainFileOperationProgressController(@NonNull MainActivity activity) {
        this.activity = activity;
    }

    @NonNull
    FileOperationProgress show(@NonNull String titleText, @Nullable String initialDetail) {
        dismissActiveDialogOnly();
        FileOperationProgress progress = new FileOperationProgress(titleText, null);
        activeProgress = progress;
        if (initialDetail != null) progress.setDetail(initialDetail);
        showDialogFor(progress);
        activity.updateMainOverflowButtonVisibility();
        return progress;
    }

    boolean showActiveProgressDialog() {
        FileOperationProgress progress = activeProgress;
        if (progress == null || progress.isComplete()) return false;
        dismissActiveDialogOnly();
        showDialogFor(progress);
        activity.updateMainOverflowButtonVisibility();
        return true;
    }

    boolean hasBackgroundProgress() {
        return activeProgress != null
                && !activeProgress.isComplete()
                && (dialog == null || !dialog.isShowing());
    }

    void finish(@Nullable FileOperationProgress progress) {
        if (progress == null || progress == activeProgress) {
            if (progress != null) progress.markComplete();
            dismissActiveDialogOnly();
            activeProgress = null;
            activity.updateMainOverflowButtonVisibility();
        }
    }

    private void showDialogFor(@NonNull FileOperationProgress progress) {
        final boolean dark = activity.prefs == null || activity.prefs.shouldUseDarkColors(activity);
        final int bg = activity.prefs != null ? activity.prefs.getMainBgColor(activity) : (dark ? Color.rgb(33, 33, 33) : Color.rgb(255, 255, 255));
        final int panel = activity.prefs != null ? activity.prefs.getMainPanelColor(activity) : (dark ? Color.rgb(48, 48, 48) : Color.rgb(245, 245, 245));
        final int fg = activity.prefs != null ? activity.prefs.getMainTextColor(activity) : (dark ? Color.rgb(245, 245, 245) : Color.rgb(32, 33, 36));
        final int sub = activity.prefs != null ? activity.prefs.getMainSubTextColor(activity) : (dark ? Color.rgb(190, 190, 190) : Color.rgb(95, 99, 104));
        final int line = activity.prefs != null ? activity.prefs.getMainOutlineColor(activity) : (dark ? Color.rgb(92, 92, 92) : Color.rgb(210, 210, 210));
        final int accent = activity.prefs != null ? activity.prefs.getMainSelectedColor(activity) : (dark ? Color.rgb(68, 110, 190) : Color.rgb(76, 118, 180));

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(activity.dpToPx(18), activity.dpToPx(14), activity.dpToPx(18), activity.dpToPx(14));
        GradientDrawable bgShape = new GradientDrawable();
        bgShape.setColor(bg);
        bgShape.setCornerRadius(activity.dpToPx(8));
        bgShape.setStroke(Math.max(1, activity.dpToPx(1)), line);
        box.setBackground(bgShape);

        TextView title = new TextView(activity);
        title.setText(progress.snapshot().title);
        title.setTextColor(fg);
        title.setTextSize(17f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        title.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(activity.dpToPx(2), 0, activity.dpToPx(2), activity.dpToPx(8));
        box.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView detail = makeMetaText(fg, 14f, true);
        detail.setPadding(activity.dpToPx(2), 0, activity.dpToPx(2), activity.dpToPx(5));
        box.addView(detail, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView folder = makeMetaText(sub, 12.5f, true);
        folder.setPadding(activity.dpToPx(2), 0, activity.dpToPx(2), activity.dpToPx(8));
        box.addView(folder, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout progressRow = new LinearLayout(activity);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(progressRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dpToPx(38)));

        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(0);
        bar.setProgressTintList(ColorStateList.valueOf(accent));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(line));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                0,
                activity.dpToPx(6),
                1f);
        barLp.setMargins(activity.dpToPx(2), 0, activity.dpToPx(10), 0);
        progressRow.addView(bar, barLp);

        TextView pause = makeActionButton(activity.getString(R.string.operation_pause), fg, panel);
        pause.setContentDescription(activity.getString(R.string.operation_pause));
        progressRow.addView(pause, new LinearLayout.LayoutParams(
                activity.dpToPx(86),
                activity.dpToPx(34)));

        LinearLayout stats = new LinearLayout(activity);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        stats.setPadding(activity.dpToPx(2), activity.dpToPx(6), activity.dpToPx(2), activity.dpToPx(12));
        box.addView(stats, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView percent = makeMetaText(fg, 13f, false);
        percent.setTypeface(Typeface.DEFAULT_BOLD);
        stats.addView(percent, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.32f));

        TextView bytes = makeMetaText(sub, 13f, false);
        bytes.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        bytes.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        stats.addView(bytes, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.68f));

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        box.addView(actions, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                activity.dpToPx(42)));

        TextView background = makeActionButton(activity.getString(R.string.operation_background), fg, panel);
        TextView cancel = makeActionButton(activity.getString(R.string.cancel), sub, panel);
        addAction(actions, background);
        addAction(actions, cancel);

        progress.setListener(snapshot ->
                activity.fileSearchHandler.post(() -> {
                    if (activity.activityDestroyed) return;
                    updateProgressViews(snapshot, detail, folder, percent, bytes, bar, pause);
                }));

        pause.setOnClickListener(v -> progress.setPaused(!progress.isPaused()));
        background.setOnClickListener(v -> {
            dismissActiveDialogOnly();
            activity.updateMainOverflowButtonVisibility();
        });
        cancel.setOnClickListener(v -> {
            progress.cancel();
            dismissActiveDialogOnly();
            activity.updateMainOverflowButtonVisibility();
        });

        dialog = activity.createStableCenterDialog(box, 0, 0.18f);
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        activity.overrideDialogWidth(dialog, Math.max(activity.dpToPx(290), Math.min(Math.round(screenWidth * 0.88f), activity.dpToPx(540))));
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(d -> {
            dismissActiveDialogOnly();
            activity.updateMainOverflowButtonVisibility();
        });
        dialog.setOnDismissListener(d -> activity.updateMainOverflowButtonVisibility());
        dialog.show();
        updateProgressViews(progress.snapshot(), detail, folder, percent, bytes, bar, pause);
    }

    private void updateProgressViews(@NonNull FileOperationProgress.Snapshot snapshot,
                                     @NonNull TextView detail,
                                     @NonNull TextView folder,
                                     @NonNull TextView percent,
                                     @NonNull TextView bytes,
                                     @NonNull ProgressBar bar,
                                     @NonNull TextView pause) {
        String detailText = snapshot.detail == null || snapshot.detail.length() == 0
                ? snapshot.title
                : snapshot.detail;
        if (snapshot.itemTotal > 0) {
            detailText = activity.getString(R.string.operation_detail_count_format,
                    detailText,
                    snapshot.itemIndex,
                    snapshot.itemTotal);
        }
        detail.setText(detailText);
        String folderName = snapshot.folder == null || snapshot.folder.length() == 0
                ? "-"
                : snapshot.folder;
        String folderText = snapshot.folderTotal > 0
                ? activity.getString(R.string.operation_folder_count_format,
                folderName,
                snapshot.folderIndex,
                snapshot.folderTotal)
                : (snapshot.folder == null || snapshot.folder.length() == 0
                ? activity.getString(R.string.operation_folder_unknown)
                : activity.getString(R.string.operation_folder_format, snapshot.folder));
        folder.setText(folderText);
        pause.setText(activity.getString(snapshot.paused
                ? R.string.operation_resume
                : R.string.operation_pause));
        pause.setContentDescription(activity.getString(snapshot.paused
                ? R.string.operation_resume
                : R.string.operation_pause));

        if (snapshot.indeterminate) {
            bar.setIndeterminate(true);
            percent.setText(snapshot.paused
                    ? activity.getString(R.string.operation_paused)
                    : activity.getString(R.string.operation_working));
            bytes.setText("");
            return;
        }
        bar.setIndeterminate(false);
        bar.setProgress(snapshot.percent() * 10);
        percent.setText(activity.getString(snapshot.paused
                ? R.string.operation_percent_paused_format
                : R.string.operation_percent_format,
                snapshot.percent()));
        bytes.setText(activity.getString(R.string.operation_bytes_format,
                FileUtils.formatFileSize(snapshot.doneBytes),
                FileUtils.formatFileSize(snapshot.totalBytes)));
    }

    private TextView makeMetaText(int textColor, float textSize, boolean twoLines) {
        TextView view = new TextView(activity);
        view.setTextColor(textColor);
        view.setTextSize(textSize);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        view.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        view.setSingleLine(!twoLines);
        view.setMaxLines(twoLines ? 2 : 1);
        view.setEllipsize(twoLines
                ? android.text.TextUtils.TruncateAt.MIDDLE
                : android.text.TextUtils.TruncateAt.END);
        view.setIncludeFontPadding(false);
        return view;
    }

    private TextView makeActionButton(@NonNull String label, int textColor, int panelColor) {
        TextView button = new TextView(activity);
        button.setText(label);
        button.setTextColor(textColor);
        button.setTextSize(13f);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        button.setSingleLine(true);
        button.setPadding(activity.dpToPx(8), 0, activity.dpToPx(8), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(panelColor);
        bg.setCornerRadius(activity.dpToPx(6));
        button.setBackground(bg);
        return button;
    }

    private void addAction(@NonNull LinearLayout actions, @NonNull TextView button) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(activity.dpToPx(4), 0, 0, 0);
        actions.addView(button, lp);
    }

    private void dismissActiveDialogOnly() {
        if (activeProgress != null) activeProgress.setListener(null);
        if (dialog != null) {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {
            }
        }
        dialog = null;
    }
}
