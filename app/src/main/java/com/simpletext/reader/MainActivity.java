package com.simpletext.reader;

import android.Manifest;
import android.graphics.Color;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.simpletext.reader.adapter.FileAdapter;
import com.simpletext.reader.model.ReaderState;
import com.simpletext.reader.util.BookmarkManager;
import com.simpletext.reader.util.EdgeToEdgeUtil;
import com.simpletext.reader.util.FileUtils;
import com.simpletext.reader.util.PrefsManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements FileAdapter.OnFileClickListener {

    public static final String EXTRA_RETURN_TO_VIEWER = "return_to_viewer";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private RecyclerView recyclerView;
    private FileAdapter fileAdapter;
    private TextView pathText, emptyText;
    private View recentSection;
    private RecyclerView recentRecyclerView;

    private File currentDirectory;
    private PrefsManager prefs;
    private BookmarkManager bookmarkManager;
    private boolean lockChecked = false;
    private boolean returnToViewerMode = false;
    private long lastBackPressedTime = 0L;

    private final ActivityResultLauncher<String[]> openFileLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    openFileFromUri(uri);
                }
            });

    private final ActivityResultLauncher<Intent> manageStorageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                    initFileBrowser();
                }
            });

    private final ActivityResultLauncher<Intent> lockLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    lockChecked = true;
                    checkPermissionsAndInit();
                } else {
                    finishAffinity();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = PrefsManager.getInstance(this);
        returnToViewerMode = getIntent().getBooleanExtra(EXTRA_RETURN_TO_VIEWER, false);
        prefs.applyLanguage(prefs.getLanguageMode());
        prefs.applyDarkMode(prefs.getDarkMode());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleMainBackPressed();
            }
        });
        EdgeToEdgeUtil.applyStandardInsets(this, findViewById(R.id.main_root),
                findViewById(R.id.main_appbar), findViewById(R.id.main_content_container));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        applyMainReadableTheme(toolbar);

        recyclerView = findViewById(R.id.file_list);
        pathText = findViewById(R.id.current_path);
        emptyText = findViewById(R.id.empty_text);
        recentSection = findViewById(R.id.recent_section);
        recentRecyclerView = findViewById(R.id.recent_list);
        applyMainReadableTheme(toolbar);

        fileAdapter = new FileAdapter();
        fileAdapter.setListener(this);
        fileAdapter.setSortMode(prefs.getSortMode());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(fileAdapter);

        bookmarkManager = BookmarkManager.getInstance(this);

        // Handle external intent
        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri != null) { openFileFromUri(uri); return; }
        }

        // Check lock
        if (prefs.isLockEnabled() && !lockChecked) {
            Intent lockIntent = new Intent(this, LockActivity.class);
            lockIntent.putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_UNLOCK);
            lockLauncher.launch(lockIntent);
        } else {
            checkPermissionsAndInit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecentFiles();
        if (currentDirectory != null) loadDirectory(currentDirectory);
    }


    private boolean isDarkUi() {
        int mask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyMainReadableTheme(Toolbar toolbar) {
        boolean dark = isDarkUi();

        int bg = dark ? Color.rgb(0, 0, 0) : Color.rgb(255, 255, 255);
        int panel = dark ? Color.rgb(17, 17, 17) : Color.rgb(248, 249, 250);
        int text = dark ? Color.rgb(232, 234, 237) : Color.rgb(32, 33, 36);
        int sub = dark ? Color.rgb(176, 176, 176) : Color.rgb(95, 99, 104);
        int bar = dark ? Color.rgb(0, 0, 0) : Color.rgb(32, 33, 36);

        View root = findViewById(R.id.main_root);
        View appbar = findViewById(R.id.main_appbar);
        View content = findViewById(R.id.main_content_container);
        View recent = findViewById(R.id.recent_section);

        if (root != null) root.setBackgroundColor(bg);
        if (content != null) content.setBackgroundColor(bg);
        if (recent != null) recent.setBackgroundColor(bg);
        if (recyclerView != null) recyclerView.setBackgroundColor(bg);
        if (recentRecyclerView != null) recentRecyclerView.setBackgroundColor(bg);
        if (pathText != null) {
            pathText.setBackgroundColor(panel);
            pathText.setTextColor(sub);
        }
        if (emptyText != null) emptyText.setTextColor(sub);

        if (appbar != null) appbar.setBackgroundColor(bar);
        if (toolbar != null) {
            toolbar.setBackgroundColor(bar);
            toolbar.setTitleTextColor(Color.WHITE);
        }

        getWindow().setStatusBarColor(bar);
        getWindow().setNavigationBarColor(bg);
        androidx.core.view.WindowInsetsControllerCompat controller =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(!dark);
    }


    private void checkPermissionsAndInit() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) { initFileBrowser(); }
            else {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    manageStorageLauncher.launch(intent);
                } catch (Exception e) {
                    manageStorageLauncher.launch(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            } else { initFileBrowser(); }
        } else { initFileBrowser(); }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initFileBrowser();
        } else {
            emptyText.setText("Tap menu → Open File to pick a text file.");
            emptyText.setVisibility(View.VISIBLE);
        }
    }

    private void initFileBrowser() {
        String lastDir = prefs.getLastDirectory();
        currentDirectory = (lastDir != null && new File(lastDir).isDirectory())
                ? new File(lastDir) : Environment.getExternalStorageDirectory();
        loadDirectory(currentDirectory);
    }

    private void loadDirectory(File dir) {
        currentDirectory = dir;
        prefs.setLastDirectory(dir.getAbsolutePath());
        pathText.setText(dir.getAbsolutePath());
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(dir.getName().isEmpty() ? "/" : dir.getName());
            getSupportActionBar().setDisplayHomeAsUpEnabled(!isRootStorage(dir));
        }

        File[] fileArray = dir.listFiles();
        List<File> fileList = new ArrayList<>();
        boolean showHidden = prefs.getShowHiddenFiles();

        if (fileArray != null) {
            for (File f : fileArray) {
                if (!showHidden && f.getName().startsWith(".")) continue;
                if (f.isDirectory() || FileUtils.isTextFile(f.getName())) fileList.add(f);
            }
        }

        fileAdapter.setFiles(fileList);
        emptyText.setVisibility(fileList.isEmpty() ? View.VISIBLE : View.GONE);
        if (fileList.isEmpty()) emptyText.setText(getString(R.string.no_text_files_in_directory));
    }

    private boolean isRootStorage(File dir) {
        String path = dir.getAbsolutePath();
        return path.equals(Environment.getExternalStorageDirectory().getAbsolutePath())
                || path.equals("/storage") || path.equals("/");
    }

    private void loadRecentFiles() {
        List<ReaderState> recent = bookmarkManager.getRecentFiles(25);
        if (recent.isEmpty()) { recentSection.setVisibility(View.GONE); return; }

        List<File> recentFiles = new ArrayList<>();
        for (ReaderState s : recent) {
            File f = new File(s.getFilePath());
            if (f.exists()) recentFiles.add(f);
        }
        if (recentFiles.isEmpty()) { recentSection.setVisibility(View.GONE); return; }

        recentSection.setVisibility(View.VISIBLE);
        FileAdapter recentAdapter = new FileAdapter();
        recentAdapter.setListener(this);
        recentAdapter.setFiles(recentFiles);
        recentRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        recentRecyclerView.setAdapter(recentAdapter);
    }

    @Override public void onFileClick(@NonNull File file) {
        if (file.isDirectory()) loadDirectory(file);
        else openFile(file);
    }

    @Override public void onFileLongClick(@NonNull File file) { showFileOpsDialog(file); }

    private void openFile(File file) {
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
        startActivity(intent);

        if (returnToViewerMode) {
            finish();
        }
    }

    private void openFileFromUri(Uri uri) {
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra(ReaderActivity.EXTRA_FILE_URI, uri.toString());
        startActivity(intent);

        if (returnToViewerMode) {
            finish();
        }
    }

    // --- File Operations ---

    private void showFileOpsDialog(File file) {
        String[] options;
        if (file.isDirectory()) {
            options = new String[]{getString(R.string.rename), getString(R.string.delete)};
        } else {
            options = new String[]{getString(R.string.open), getString(R.string.rename), getString(R.string.delete), getString(R.string.file_info)};
        }

        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(options, (d, which) -> {
                    if (file.isDirectory()) {
                        if (which == 0) showRenameDialog(file);
                        else if (which == 1) showDeleteConfirm(file);
                    } else {
                        if (which == 0) openFile(file);
                        else if (which == 1) showRenameDialog(file);
                        else if (which == 2) showDeleteConfirm(file);
                        else if (which == 3) showFileInfo(file);
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showRenameDialog(File file) {
        EditText input = new EditText(this);
        input.setText(file.getName());
        input.selectAll();

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.rename))
                .setView(input)
                .setPositiveButton(getString(R.string.rename), (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) return;
                    File newFile = new File(file.getParent(), newName);
                    if (file.renameTo(newFile)) {
                        loadDirectory(currentDirectory);
                        Toast.makeText(this, getString(R.string.renamed), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, getString(R.string.rename_failed), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showDeleteConfirm(File file) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete))
                .setMessage(getString(R.string.delete_file_confirm, file.getName()))
                .setPositiveButton(getString(R.string.delete), (d, w) -> {
                    if (deleteRecursive(file)) {
                        loadDirectory(currentDirectory);
                        Toast.makeText(this, getString(R.string.deleted), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, getString(R.string.delete_failed), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) deleteRecursive(c);
            }
        }
        return file.delete();
    }

    private void showFileInfo(File file) {
        String info = getString(R.string.file_info_name) + ": " + file.getName()
                + "\n" + getString(R.string.file_info_path) + ": " + file.getAbsolutePath()
                + "\n" + getString(R.string.file_info_size) + ": " + FileUtils.formatFileSize(file.length())
                + "\n" + getString(R.string.file_info_modified) + ": "
                + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()).format(new java.util.Date(file.lastModified()))
                + "\n" + getString(R.string.file_info_readable) + ": " + file.canRead()
                + "\n" + getString(R.string.file_info_writable) + ": " + file.canWrite();

        // Detect encoding for text files
        if (!file.isDirectory() && FileUtils.isTextFile(file.getName())) {
            info += "\n" + getString(R.string.file_info_encoding) + ": " + FileUtils.detectEncoding(file);
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.file_info))
                .setMessage(info)
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    private void showNewFolderDialog() {
        EditText input = new EditText(this);
        input.setHint(getString(R.string.folder_name));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.new_folder))
                .setView(input)
                .setPositiveButton(getString(R.string.create), (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    File newDir = new File(currentDirectory, name);
                    if (newDir.mkdirs()) {
                        loadDirectory(currentDirectory);
                        Toast.makeText(this, getString(R.string.folder_created), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, getString(R.string.folder_create_failed), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showSortDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_sort, null);
        RadioGroup group = dialogView.findViewById(R.id.sort_group);

        int[] ids = {R.id.sort_name_asc, R.id.sort_name_desc, R.id.sort_date_new,
                R.id.sort_date_old, R.id.sort_size_large, R.id.sort_size_small, R.id.sort_type};
        int current = prefs.getSortMode();
        if (current >= 0 && current < ids.length) {
            ((RadioButton) dialogView.findViewById(ids[current])).setChecked(true);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.sort_by))
                .setView(dialogView)
                .setNegativeButton(getString(R.string.cancel), null)
                .create();

        group.setOnCheckedChangeListener((g, checkedId) -> {
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == checkedId) {
                    prefs.setSortMode(i);
                    fileAdapter.setSortMode(i);
                    dialog.dismiss();
                    break;
                }
            }
        });

        dialog.show();
    }

    // --- Menu ---

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        menu.findItem(R.id.action_show_hidden).setChecked(prefs.getShowHiddenFiles());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) { navigateUp(); return true; }
        else if (id == R.id.action_open_file) { openFileLauncher.launch(new String[]{"text/plain", "text/*"}); return true; }
        else if (id == R.id.action_bookmarks) { startActivity(new Intent(this, BookmarkListActivity.class)); return true; }
        else if (id == R.id.action_sort) { showSortDialog(); return true; }
        else if (id == R.id.action_new_folder) { showNewFolderDialog(); return true; }
        else if (id == R.id.action_show_hidden) {
            boolean newVal = !item.isChecked();
            item.setChecked(newVal);
            prefs.setShowHiddenFiles(newVal);
            loadDirectory(currentDirectory);
            return true;
        }
        else if (id == R.id.action_go_to_internal) { loadDirectory(Environment.getExternalStorageDirectory()); return true; }
        else if (id == R.id.action_go_to_storage) { loadDirectory(new File("/storage")); return true; }
        else if (id == R.id.action_settings) { startActivity(new Intent(this, SettingsActivity.class)); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void navigateUp() {
        if (currentDirectory != null && !isRootStorage(currentDirectory)) {
            File parent = currentDirectory.getParentFile();
            if (parent != null && parent.canRead()) loadDirectory(parent);
        }
    }

    private void handleMainBackPressed() {
        if (currentDirectory != null && !isRootStorage(currentDirectory)) {
            navigateUp();
            return;
        }

        if (returnToViewerMode) {
            finish();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastBackPressedTime < 2000) {
            finish();
        } else {
            lastBackPressedTime = now;
            Toast.makeText(this, getString(R.string.press_back_again_exit), Toast.LENGTH_SHORT).show();
        }
    }
}
