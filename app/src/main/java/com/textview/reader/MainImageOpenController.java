package com.textview.reader;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.textview.reader.util.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MainImageOpenController {
    private final MainActivity activity;
    private Dialog imageOpenLoadingDialog;

    MainImageOpenController(@NonNull MainActivity activity) {
        this.activity = activity;
    }

    @NonNull
    ArrayList<String> buildImageSiblingPaths(@NonNull File selected) {
        ArrayList<String> paths = new ArrayList<>();
        File parent = selected.getParentFile();
        if (parent == null || !parent.exists() || !parent.isDirectory() || !parent.canRead()) {
            paths.add(selected.getAbsolutePath());
            return paths;
        }
        File[] children = parent.listFiles();
        if (children == null) {
            paths.add(selected.getAbsolutePath());
            return paths;
        }
        List<File> images = new ArrayList<>();
        for (File child : children) {
            if (child != null && child.isFile() && FileUtils.isImageFile(child.getName())) {
                images.add(child);
            }
        }
        Collections.sort(images, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File image : images) paths.add(image.getAbsolutePath());
        if (!paths.contains(selected.getAbsolutePath())) paths.add(selected.getAbsolutePath());
        return paths;
    }

    void startWithLoading(@NonNull Intent intent) {
        showImageOpenLoadingWindow();
        activity.fileSearchHandler.postDelayed(() -> {
            if (activity.activityDestroyed || activity.isFinishing()) {
                hideImageOpenLoadingWindow();
                return;
            }
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);
            hideImageOpenLoadingWindow();
            activity.finishIfReturnToViewerMode();
        }, 90L);
    }

    private void showImageOpenLoadingWindow() {
        hideImageOpenLoadingWindow();
        final boolean dark = activity.prefs == null || activity.prefs.shouldUseDarkColors(activity);
        final int bg = activity.prefs != null ? activity.prefs.getMainBgColor(activity) : (dark ? Color.rgb(33, 33, 33) : Color.WHITE);
        final int fg = activity.prefs != null ? activity.prefs.getMainTextColor(activity) : (dark ? Color.rgb(245, 245, 245) : Color.rgb(32, 33, 36));
        final int line = activity.prefs != null ? activity.prefs.getMainOutlineColor(activity) : (dark ? Color.rgb(92, 92, 92) : Color.rgb(210, 210, 210));
        final int panel = UiColorUtils.blendColors(bg, fg, UiColorUtils.isLightColor(bg) ? 0.05f : 0.08f);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setMinimumWidth(activity.dpToPx(116));
        box.setMinimumHeight(activity.dpToPx(112));
        box.setPadding(activity.dpToPx(20), activity.dpToPx(22), activity.dpToPx(20), activity.dpToPx(20));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(panel);
        shape.setCornerRadius(activity.dpToPx(24));
        shape.setStroke(Math.max(1, activity.dpToPx(1)), line);
        box.setBackground(shape);

        ProgressBar spinner = new ProgressBar(activity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            spinner.setIndeterminateTintList(ColorStateList.valueOf(fg));
        }
        box.addView(spinner, new LinearLayout.LayoutParams(activity.dpToPx(54), activity.dpToPx(54)));

        TextView label = new TextView(activity);
        label.setText(R.string.loading);
        label.setTextColor(fg);
        label.setTextSize(15f);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelLp.setMargins(0, activity.dpToPx(10), 0, 0);
        box.addView(label, labelLp);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(box);
        dialog.setCancelable(false);
        imageOpenLoadingDialog = dialog;
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    void hideImageOpenLoadingWindow() {
        if (imageOpenLoadingDialog != null) {
            try {
                if (imageOpenLoadingDialog.isShowing()) imageOpenLoadingDialog.dismiss();
            } catch (Exception ignored) {}
            imageOpenLoadingDialog = null;
        }
    }
}
