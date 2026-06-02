package com.textview.reader;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.textview.reader.archive.ArchiveSupport;
import com.textview.reader.util.FileOperationProgress;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class MainArchiveExtractController {
    private final MainActivity activity;

    MainArchiveExtractController(@NonNull MainActivity activity) {
        this.activity = activity;
    }

    boolean isSupportedArchive(@NonNull File file) {
        return ArchiveSupport.isSupportedArchive(file);
    }

    void startArchiveExtraction(@NonNull File archive) {
        ArrayList<File> archives = new ArrayList<>();
        archives.add(archive);
        startArchiveExtractions(archives);
    }

    void startArchiveExtractions(@NonNull List<File> archives) {
        ArrayList<File> ready = new ArrayList<>();
        for (File archive : archives) {
            if (archive != null && isSupportedArchive(archive) && archive.exists() && archive.isFile() && archive.canRead()) {
                ready.add(archive);
            }
        }
        if (ready.isEmpty()) {
            ShortToast.show(activity, R.string.archive_extract_failed);
            return;
        }
        for (int i = ready.size() - 1; i >= 0; i--) {
            addPendingArchiveExtraction(ready.get(i));
        }
        activity.archiveExtractInProgress = false;
        activity.updateMainOverflowButtonVisibility();
        Toast.makeText(activity, R.string.archive_extract_select_destination, Toast.LENGTH_LONG).show();

        File parent = ready.get(0).getParentFile();
        if (parent != null && parent.exists() && parent.isDirectory() && parent.canRead()) {
            activity.resetMainBrowseFiltersAndShow(parent, null);
        }
    }

    private void addPendingArchiveExtraction(@NonNull File archive) {
        removePendingArchiveExtraction(archive);
        activity.pendingExtractArchives.add(0, archive);
        activity.pendingExtractArchive = archive;
    }

    boolean setActivePendingArchiveExtraction(@Nullable File archive) {
        if (archive == null) return false;
        for (File pending : activity.pendingExtractArchives) {
            if (pending.equals(archive)) {
                activity.pendingExtractArchive = pending;
                return true;
            }
        }
        return false;
    }

    private void removePendingArchiveExtraction(@Nullable File archive) {
        if (archive == null) return;
        for (int i = activity.pendingExtractArchives.size() - 1; i >= 0; i--) {
            File pending = activity.pendingExtractArchives.get(i);
            if (pending.equals(archive)) activity.pendingExtractArchives.remove(i);
        }
        if (activity.pendingExtractArchive != null && activity.pendingExtractArchive.equals(archive)) {
            activity.pendingExtractArchive = activity.pendingExtractArchives.isEmpty()
                    ? null
                    : activity.pendingExtractArchives.get(0);
        }
    }

    void cancelPendingArchiveExtraction(@Nullable File archive) {
        removePendingArchiveExtraction(archive);
        activity.archiveExtractInProgress = false;
        activity.updateMainOverflowButtonVisibility();
        ShortToast.show(activity, R.string.archive_extract_cancelled);
    }

    void confirmPendingArchiveExtractionToCurrentDirectory() {
        File archive = activity.pendingExtractArchive;
        if (archive == null) return;
        if (!archive.exists() || !archive.canRead() || !isSupportedArchive(archive)) {
            removePendingArchiveExtraction(archive);
            activity.updateMainOverflowButtonVisibility();
            ShortToast.show(activity, R.string.archive_extract_failed);
            return;
        }
        File destinationDir = activity.currentDirectory;
        if (destinationDir == null || !destinationDir.exists() || !destinationDir.isDirectory() || !destinationDir.canWrite()) {
            ShortToast.show(activity, R.string.file_move_destination_unavailable);
            return;
        }
        File destination = new File(destinationDir, getArchiveOutputBaseName(archive));
        showArchiveExtractConfirmDialog(archive, destinationDir, destination);
    }

    void confirmAllPendingArchiveExtractionsToCurrentDirectory() {
        if (activity.archiveExtractInProgress) return;
        ArrayList<File> archives = new ArrayList<>(activity.pendingExtractArchives);
        if (archives.isEmpty()) return;
        File destinationRoot = activity.currentDirectory;
        if (destinationRoot == null || !destinationRoot.exists()
                || !destinationRoot.isDirectory() || !destinationRoot.canWrite()) {
            ShortToast.show(activity, R.string.file_move_destination_unavailable);
            return;
        }
        activity.showSimpleConfirmDialog(
                activity.getString(R.string.extract_all_here_title),
                activity.getString(R.string.extract_all_here_message,
                        archives.size(),
                        destinationRoot.getName().length() > 0 ? destinationRoot.getName() : destinationRoot.getAbsolutePath()),
                activity.getString(R.string.extract_all_here),
                () -> continueAllArchiveExtractionsWithPasswordPreflight(archives, destinationRoot));
    }

    private String getArchiveOutputBaseName(@NonNull File archive) {
        return ArchiveSupport.getArchiveOutputBaseName(
                archive,
                activity.getString(R.string.extracted_folder_fallback));
    }

    private void showArchiveExtractConfirmDialog(@NonNull File archive,
                                                 @NonNull File destinationDir,
                                                 @NonNull File destination) {
        activity.showSimpleConfirmDialog(
                activity.getString(R.string.archive_extract_confirm_title),
                activity.getString(R.string.archive_extract_confirm_message,
                        archive.getName(),
                        destinationDir.getName().length() > 0 ? destinationDir.getName() : destinationDir.getAbsolutePath()),
                activity.getString(R.string.extract_archive),
                () -> {
                    if (activity.pendingExtractArchive == null || !activity.pendingExtractArchive.equals(archive)) return;
                    if (destination.exists()) showArchiveTargetExistsDialog(archive, destinationDir, destination);
                    else continueArchiveExtractionWithPasswordIfNeeded(archive, destinationDir, destination, false);
                });
    }

    private void showArchiveTargetExistsDialog(@NonNull File archive,
                                               @NonNull File parentDir,
                                               @NonNull File existingTarget) {
        final boolean dark = activity.prefs == null || activity.prefs.shouldUseDarkColors(activity);
        final int bg = activity.prefs != null ? activity.prefs.getMainBgColor(activity) : (dark ? Color.rgb(33, 33, 33) : Color.rgb(255, 255, 255));
        final int panel = activity.prefs != null ? activity.prefs.getMainPanelColor(activity) : (dark ? Color.rgb(48, 48, 48) : Color.rgb(245, 245, 245));
        final int fg = activity.prefs != null ? activity.prefs.getMainTextColor(activity) : (dark ? Color.rgb(245, 245, 245) : Color.rgb(32, 33, 36));
        final int sub = activity.prefs != null ? activity.prefs.getMainSubTextColor(activity) : (dark ? Color.rgb(190, 190, 190) : Color.rgb(95, 99, 104));
        final int line = activity.prefs != null ? activity.prefs.getMainOutlineColor(activity) : (dark ? Color.rgb(92, 92, 92) : Color.rgb(210, 210, 210));

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(activity.dpToPx(18), activity.dpToPx(16), activity.dpToPx(18), activity.dpToPx(10));
        GradientDrawable bgShape = new GradientDrawable();
        bgShape.setColor(bg);
        bgShape.setCornerRadius(activity.dpToPx(18));
        bgShape.setStroke(Math.max(1, activity.dpToPx(1)), line);
        box.setBackground(bgShape);

        TextView title = new TextView(activity);
        title.setText(activity.getString(R.string.archive_extract_conflict_title));
        title.setTextColor(fg);
        title.setTextSize(21f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        title.setPadding(activity.dpToPx(6), 0, activity.dpToPx(6), activity.dpToPx(8));
        box.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(activity);
        message.setText(activity.getString(R.string.archive_extract_conflict_message, existingTarget.getName()));
        message.setTextColor(sub);
        message.setTextSize(14f);
        message.setGravity(Gravity.CENTER);
        message.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        message.setLineSpacing(activity.dpToPx(2), 1.0f);
        message.setPadding(activity.dpToPx(8), 0, activity.dpToPx(8), activity.dpToPx(14));
        box.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final android.app.Dialog[] ref = new android.app.Dialog[1];
        activity.addFileOpsRow(box, activity.getString(R.string.overwrite), fg, panel, () -> {
            if (ref[0] != null) ref[0].dismiss();
            continueArchiveExtractionWithPasswordIfNeeded(archive, parentDir, existingTarget, true);
        });
        activity.addFileOpsRow(box, activity.getString(R.string.create_copy), fg, panel, () -> {
            if (ref[0] != null) ref[0].dismiss();
            File copyDestination = buildNumberedDirectoryDestination(parentDir, existingTarget.getName());
            if (copyDestination == null) {
                ShortToast.show(activity, R.string.archive_extract_failed);
                return;
            }
            continueArchiveExtractionWithPasswordIfNeeded(archive, parentDir, copyDestination, false);
        });

        TextView cancel = new TextView(activity);
        cancel.setText(activity.getString(R.string.cancel));
        cancel.setTextColor(sub);
        cancel.setTextSize(16f);
        cancel.setGravity(Gravity.CENTER);
        cancel.setTypeface(Typeface.DEFAULT_BOLD);
        cancel.setPadding(activity.dpToPx(12), 0, activity.dpToPx(12), 0);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, activity.dpToPx(50));
        cancelLp.setMargins(0, activity.dpToPx(4), 0, 0);
        box.addView(cancel, cancelLp);

        android.app.Dialog dialog = activity.createStableBottomDialog(
                box,
                activity.mainFileTypeAlignedDialogYOffsetPx(),
                0.22f);
        ref[0] = dialog;
        cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private interface ArchivePasswordCallback {
        void onPassword(@NonNull String password);
    }

    private void showArchivePasswordDialog(@NonNull ArchivePasswordCallback callback) {
        final boolean dark = activity.prefs == null || activity.prefs.shouldUseDarkColors(activity);
        final int bg = activity.prefs != null ? activity.prefs.getMainBgColor(activity) : (dark ? Color.rgb(33, 33, 33) : Color.rgb(255, 255, 255));
        final int panel = activity.prefs != null ? activity.prefs.getMainPanelColor(activity) : (dark ? Color.rgb(48, 48, 48) : Color.rgb(245, 245, 245));
        final int fg = activity.prefs != null ? activity.prefs.getMainTextColor(activity) : (dark ? Color.rgb(245, 245, 245) : Color.rgb(32, 33, 36));
        final int sub = activity.prefs != null ? activity.prefs.getMainSubTextColor(activity) : (dark ? Color.rgb(190, 190, 190) : Color.rgb(95, 99, 104));
        final int line = activity.prefs != null ? activity.prefs.getMainOutlineColor(activity) : (dark ? Color.rgb(92, 92, 92) : Color.rgb(210, 210, 210));

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(activity.dpToPx(18), activity.dpToPx(16), activity.dpToPx(18), activity.dpToPx(10));
        GradientDrawable bgShape = new GradientDrawable();
        bgShape.setColor(bg);
        bgShape.setCornerRadius(activity.dpToPx(18));
        bgShape.setStroke(Math.max(1, activity.dpToPx(1)), line);
        box.setBackground(bgShape);

        TextView title = new TextView(activity);
        title.setText(R.string.archive_password_title);
        title.setTextColor(fg);
        title.setTextSize(21f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        title.setPadding(activity.dpToPx(6), 0, activity.dpToPx(6), activity.dpToPx(8));
        box.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView message = new TextView(activity);
        message.setText(R.string.archive_password_message);
        message.setTextColor(sub);
        message.setTextSize(14f);
        message.setGravity(Gravity.CENTER);
        message.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        message.setLineSpacing(activity.dpToPx(2), 1.0f);
        message.setPadding(activity.dpToPx(8), 0, activity.dpToPx(8), activity.dpToPx(12));
        box.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.archive_password_hint);
        input.setTextColor(fg);
        input.setHintTextColor(sub);
        box.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, activity.dpToPx(52)));

        final android.app.Dialog[] ref = new android.app.Dialog[1];
        activity.addFileOpsRow(box, activity.getString(R.string.ok), fg, panel, () -> {
            if (ref[0] != null) ref[0].dismiss();
            callback.onPassword(input.getText() == null ? "" : input.getText().toString());
        });

        TextView cancel = new TextView(activity);
        cancel.setText(activity.getString(R.string.cancel));
        cancel.setTextColor(sub);
        cancel.setTextSize(16f);
        cancel.setGravity(Gravity.CENTER);
        cancel.setTypeface(Typeface.DEFAULT_BOLD);
        cancel.setPadding(activity.dpToPx(12), 0, activity.dpToPx(12), 0);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, activity.dpToPx(50));
        cancelLp.setMargins(0, activity.dpToPx(4), 0, 0);
        box.addView(cancel, cancelLp);

        android.app.Dialog dialog = activity.createStableBottomDialog(
                box,
                activity.mainFileTypeAlignedDialogYOffsetPx(),
                0.22f);
        ref[0] = dialog;
        cancel.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnShowListener(d -> input.requestFocus());
        dialog.show();
    }

    private void continueArchiveExtractionWithPasswordIfNeeded(@NonNull File archive,
                                                               @NonNull File parentDir,
                                                               @NonNull File destinationDir,
                                                               boolean overwrite) {
        if (ArchiveSupport.requiresPasswordForExtraction(archive)) {
            showArchivePasswordDialog(password -> continueArchiveExtraction(archive, parentDir, destinationDir, overwrite, password.toCharArray()));
            return;
        }
        continueArchiveExtraction(archive, parentDir, destinationDir, overwrite, null);
    }

    private void continueArchiveExtraction(@NonNull File archive,
                                           @NonNull File parentDir,
                                           @NonNull File destinationDir,
                                           boolean overwrite,
                                           char[] password) {
        activity.archiveExtractInProgress = true;
        activity.updateMainOverflowButtonVisibility();
        FileOperationProgress progress = activity.showFileOperationProgress(
                activity.getString(R.string.archive_extracting),
                archive.getName());
        progress.setFolder(destinationDir.getName().length() > 0 ? destinationDir.getName() : destinationDir.getAbsolutePath());
        activity.executeFolderBackgroundTask(() -> {
            ArchiveSupport.ExtractionResult result = ArchiveSupport.extractArchiveDetailed(
                    archive,
                    destinationDir,
                    overwrite,
                    password,
                    progress);
            activity.fileSearchHandler.post(() -> {
                activity.archiveExtractInProgress = false;
                activity.finishFileOperationProgress(progress);
                activity.updateMainOverflowButtonVisibility();
                if (activity.activityDestroyed) return;
                if (progress.isCancelled()) {
                    ShortToast.show(activity, R.string.archive_extract_cancelled);
                    return;
                }
                if (!result.success) {
                    if (result.failure == ArchiveSupport.ExtractionFailure.PASSWORD_REQUIRED
                            && (password == null || password.length == 0)
                            && ArchiveSupport.canUsePassword(archive)) {
                        showArchivePasswordDialog(nextPassword -> continueArchiveExtraction(archive, parentDir, destinationDir, overwrite, nextPassword.toCharArray()));
                    } else {
                        showArchiveExtractionFailure(archive, result);
                    }
                    return;
                }
                removePendingArchiveExtraction(archive);
                activity.updateMainOverflowButtonVisibility();
                if (activity.prefs != null) activity.prefs.addRecentFolder(parentDir.getAbsolutePath());
                activity.resetMainBrowseFiltersAndShow(parentDir, destinationDir.getAbsolutePath());
                activity.rebuildDrawerStorageEntries();
                ShortToast.show(activity, R.string.archive_extracted);
            });
        });
    }

    private void continueAllArchiveExtractions(@NonNull ArrayList<File> archives,
                                               @NonNull File destinationRoot,
                                               @Nullable char[] batchPassword) {
        activity.archiveExtractInProgress = true;
        activity.updateMainOverflowButtonVisibility();
        FileOperationProgress progress = activity.showFileOperationProgress(
                activity.getString(R.string.archive_extracting),
                activity.getString(R.string.extract_all_here));
        progress.setFolder(destinationRoot.getName().length() > 0 ? destinationRoot.getName() : destinationRoot.getAbsolutePath());

        activity.executeFolderBackgroundTask(() -> {
            int successCount = 0;
            int failedCount = 0;
            int unsupportedCount = 0;
            int passwordFailedCount = 0;
            for (File archive : archives) {
                if (progress.isCancelled()) break;
                if (archive == null || !archive.exists() || !archive.canRead() || !isSupportedArchive(archive)) {
                    failedCount++;
                    continue;
                }
                String baseName = getArchiveOutputBaseName(archive);
                File destination = new File(destinationRoot, baseName);
                if (destination.exists()) {
                    destination = buildNumberedDirectoryDestination(destinationRoot, baseName);
                }
                if (destination == null) {
                    failedCount++;
                    continue;
                }
                progress.setDetail(archive.getName());
                progress.setFolder(destination.getName());
                boolean passwordRequired = ArchiveSupport.requiresPasswordForExtraction(archive);
                char[] password = passwordRequired ? batchPassword : null;
                ArchiveSupport.ExtractionResult result = password == null && passwordRequired
                        ? ArchiveSupport.ExtractionResult.failed(ArchiveSupport.ExtractionFailure.PASSWORD_REQUIRED, null)
                        : ArchiveSupport.extractArchiveDetailed(archive, destination, false, password, progress);
                if (result.success) {
                    successCount++;
                    removePendingArchiveExtraction(archive);
                } else if (!progress.isCancelled()) {
                    failedCount++;
                    if (result.failure == ArchiveSupport.ExtractionFailure.UNSUPPORTED_FEATURE) unsupportedCount++;
                    else if (result.failure == ArchiveSupport.ExtractionFailure.PASSWORD_REQUIRED) passwordFailedCount++;
                }
            }

            final int finalSuccessCount = successCount;
            final int finalFailedCount = failedCount;
            final int finalUnsupportedCount = unsupportedCount;
            final int finalPasswordFailedCount = passwordFailedCount;
            final int totalCount = archives.size();
            activity.fileSearchHandler.post(() -> {
                activity.archiveExtractInProgress = false;
                activity.finishFileOperationProgress(progress);
                activity.updateMainOverflowButtonVisibility();
                if (activity.activityDestroyed) return;
                if (progress.isCancelled()) {
                    ShortToast.show(activity, R.string.archive_extract_cancelled);
                    return;
                }
                if (activity.prefs != null && finalSuccessCount > 0) {
                    activity.prefs.addRecentFolder(destinationRoot.getAbsolutePath());
                }
                activity.resetMainBrowseFiltersAndShow(destinationRoot, null);
                activity.rebuildDrawerStorageEntries();
                if (finalFailedCount > 0 || finalSuccessCount < totalCount) {
                    if (finalUnsupportedCount > 0) {
                        ShortToast.show(activity, activity.getString(
                                R.string.archive_extract_all_partial_unsupported,
                                finalSuccessCount,
                                totalCount,
                                finalUnsupportedCount));
                    } else if (finalPasswordFailedCount > 0) {
                        ShortToast.show(activity, activity.getString(
                                R.string.archive_extract_all_partial_password,
                                finalSuccessCount,
                                totalCount,
                                finalPasswordFailedCount));
                    } else {
                        ShortToast.show(activity, activity.getString(
                                R.string.archive_extract_all_partial,
                                finalSuccessCount,
                                totalCount));
                    }
                } else {
                    ShortToast.show(activity, activity.getString(
                            R.string.archive_extract_all_done,
                            finalSuccessCount,
                            totalCount));
                }
            });
        });
    }

    private void showArchiveExtractionFailure(@NonNull File archive,
                                              @NonNull ArchiveSupport.ExtractionResult result) {
        ShortToast.show(activity, ArchiveFailureMessages.extractionFailureMessageRes(archive, result));
    }

    private void continueAllArchiveExtractionsWithPasswordPreflight(@NonNull ArrayList<File> archives,
                                                                    @NonNull File destinationRoot) {
        if (hasPasswordProtectedArchive(archives)) {
            showArchivePasswordDialog(password -> continueAllArchiveExtractions(
                    archives,
                    destinationRoot,
                    password.toCharArray()));
            return;
        }
        continueAllArchiveExtractions(archives, destinationRoot, null);
    }

    private boolean hasPasswordProtectedArchive(@NonNull List<File> archives) {
        for (File archive : archives) {
            if (archive == null || !archive.exists() || !archive.canRead() || !isSupportedArchive(archive)) continue;
            if (ArchiveSupport.requiresPasswordForExtraction(archive)) return true;
        }
        return false;
    }

    @Nullable
    private File buildNumberedDirectoryDestination(@NonNull File parentDir, @NonNull String baseName) {
        String cleanBase = baseName.trim();
        if (cleanBase.length() == 0) cleanBase = activity.getString(R.string.extracted_folder_fallback);
        for (int i = 1; i < 10000; i++) {
            File candidate = new File(parentDir, cleanBase + " (" + i + ")");
            if (!candidate.exists()) return candidate;
        }
        return null;
    }
}
