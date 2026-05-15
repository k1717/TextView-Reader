package com.textview.reader.util;

import android.content.Context;
import android.util.Log;

import com.textview.reader.model.Bookmark;
import com.textview.reader.model.ReaderState;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Manages bookmarks and reading states.
 *
 * Storage format: plain JSON files (no compression, easy to edit manually).
 *
 * bookmarks.json:
 * {
 *   "version": 1,
 *   "bookmarks": [ ... ]
 * }
 *
 * reading_states.json:
 * {
 *   "version": 1,
 *   "states": { "filePath": { ... }, ... }
 * }
 */
public class BookmarkManager {
    private static final String TAG = "BookmarkManager";
    private static final String BOOKMARKS_FILE = "bookmarks.json";
    private static final String STATES_FILE = "reading_states.json";
    private static final int FORMAT_VERSION = 7;
    private static final int QUICK_FINGERPRINT_SAMPLE_BYTES = 4096;

    private static BookmarkManager instance;
    private final Context context;
    private List<Bookmark> bookmarks;
    private Map<String, ReaderState> readingStates;

    private BookmarkManager(Context context) {
        this.context = context.getApplicationContext();
        loadBookmarks();
        loadReadingStates();
    }

    public static synchronized BookmarkManager getInstance(Context context) {
        if (instance == null) {
            instance = new BookmarkManager(context);
        }
        return instance;
    }

    // ========== Bookmark Operations ==========

    public List<Bookmark> getAllBookmarks() {
        return new ArrayList<>(bookmarks);
    }

    public List<Bookmark> getBookmarksForFile(String filePath) {
        List<Bookmark> result = collectBookmarksForExactPath(filePath);

        // Fast path above avoids any file I/O during normal use.  Portable matching
        // only runs when the local path/URI did not match, e.g., after importing a
        // backup on another device or moving the same file to a different folder.
        if (result.isEmpty() && bindPortableBookmarksToFile(filePath) > 0) {
            result = collectBookmarksForExactPath(filePath);
        }

        resolvePendingPcTextEditsForFile(filePath, result);

        // Sort by position
        Collections.sort(result, Comparator.comparingInt(Bookmark::getCharPosition));
        return result;
    }

    private List<Bookmark> collectBookmarksForExactPath(String filePath) {
        List<Bookmark> result = new ArrayList<>();
        if (filePath == null) return result;
        for (Bookmark b : bookmarks) {
            String path = b.getFilePath();
            if (filePath.equals(path)) {
                result.add(b);
            }
        }
        return result;
    }

    public void addBookmark(Bookmark bookmark) {
        enrichPortableIdentity(bookmark);
        bookmarks.add(bookmark);
        saveBookmarks();
    }

    public void updateBookmark(Bookmark bookmark) {
        enrichPortableIdentity(bookmark);
        for (int i = 0; i < bookmarks.size(); i++) {
            if (bookmarks.get(i).getId().equals(bookmark.getId())) {
                bookmark.setUpdatedAt(System.currentTimeMillis());
                bookmarks.set(i, bookmark);
                saveBookmarks();
                return;
            }
        }
    }

    public void deleteBookmark(String bookmarkId) {
        Iterator<Bookmark> it = bookmarks.iterator();
        while (it.hasNext()) {
            if (it.next().getId().equals(bookmarkId)) {
                it.remove();
                saveBookmarks();
                return;
            }
        }
    }

    public void deleteBookmarksForFile(String filePath) {
        Iterator<Bookmark> it = bookmarks.iterator();
        while (it.hasNext()) {
            Bookmark bookmark = it.next();
            String path = bookmark.getFilePath();
            if (filePath == null ? path == null : filePath.equals(path)) {
                it.remove();
            }
        }
        saveBookmarks();
    }

    // ========== Reading State Operations ==========

    public ReaderState getReadingState(String filePath) {
        return readingStates.get(filePath);
    }

    public void saveReadingState(ReaderState state) {
        state.setLastReadAt(System.currentTimeMillis());
        readingStates.put(state.getFilePath(), state);
        saveReadingStates();
    }

    public void deleteReadingState(String filePath) {
        readingStates.remove(filePath);
        saveReadingStates();
    }

    /**
     * Clear all recent-file entries / saved reading states without touching bookmarks.
     */
    public void clearReadingStates() {
        readingStates.clear();
        saveReadingStates();
    }

    /**
     * Get recently read files, sorted by last read time (most recent first).
     */
    public List<ReaderState> getRecentFiles(int limit) {
        List<ReaderState> states = new ArrayList<>(readingStates.values());
        Collections.sort(states, (a, b) -> Long.compare(b.getLastReadAt(), a.getLastReadAt()));
        if (states.size() > limit) {
            states = states.subList(0, limit);
        }
        return states;
    }

    /**
     * Fast existence check for UI visibility / clear-all actions.
     * Avoids sorting the whole recent-file map when only emptiness matters.
     */
    public boolean hasRecentFiles() {
        return !readingStates.isEmpty();
    }

    // ========== Export / Import ==========

    /**
     * Export all bookmarks and states to a single JSON string.
     * User can save this to a file for backup.
     */
    public String exportAll() {
        ensurePortableIdentitiesForExistingBookmarks();
        try {
            JSONObject root = new JSONObject();
            root.put("appName", "TextView Reader");
            root.put("backupType", "textview_reader_full_backup");
            root.put("schema", "textview-full-backup-v9");
            root.put("version", FORMAT_VERSION);
            root.put("exportedAt", System.currentTimeMillis());
            root.put("_READ_ME_FIRST_EN", "Beginner editing rule: scroll down to beginnerEditableBookmarks and edit only the clearly editable fields there. Do not edit fields ending with DoNotEdit. Do not edit bookmarks, readingStates, settings, fileIdentity, quickFingerprint, charPosition, anchorTextBefore, or anchorTextAfter unless you intentionally want advanced internal editing.");
            root.put("_읽으세요_KO", "초보자 수정 규칙: beginnerEditableBookmarks 섹션으로 내려가서 명확히 수정 가능한 필드만 고치세요. DoNotEdit으로 끝나는 필드는 수정하지 마세요. 고급 내부 수정 목적이 아니라면 bookmarks, readingStates, settings, fileIdentity, quickFingerprint, charPosition, anchorTextBefore, anchorTextAfter는 건드리지 마세요.");
            root.put("beginnerEditInstructions", "Use beginnerEditableBookmarks. TXT bookmarks can be edited by setLine, moveByLines, or findText. PDF/Word bookmarks use setPage and moveByPages. EPUB bookmarks use setPageOrSection and moveByPages. memo can always be edited. currentPreview_DoNotEdit is old context only; the app refreshes preview/anchors after import.");
            root.put("pcEditInstructions", "Preferred: edit beginnerEditableBookmarks only. Legacy/advanced option: edit bookmarks[].pcEditPosition and/or bookmarks[].memo only. Paths are device-local shortcuts; same files can rebind by file identity after opening them on the device.");
            root.put("SECTION_1_BOOKMARK_TUTORIAL_READ_ONLY_START", "==================== BOOKMARK TUTORIAL / 북마크 튜토리얼 ==================== This section is explanation only. Do not edit values in this tutorial area. 이 영역은 설명서입니다. 여기 값은 수정하지 마세요.");

            JSONObject beginnerEditHeader = new JSONObject();
            beginnerEditHeader.put("01_EN_WHERE_TO_EDIT", "Edit only beginnerEditableBookmarks. Each item in that array is one bookmark. The internal bookmarks[] section is for the app, not for beginners.");
            beginnerEditHeader.put("01_KO_어디를_수정하나", "beginnerEditableBookmarks만 수정하세요. 그 배열의 항목 하나가 북마크 하나입니다. 내부 bookmarks[] 섹션은 앱이 쓰는 영역이며 초보자 수정용이 아닙니다.");
            beginnerEditHeader.put("02_EN_SAFE_FIELDS", "TXT safe fields: memo, setLine, moveByLines, findText, findOccurrence, findTextCaseSensitive. PDF/Word safe fields: memo, setPage, moveByPages. EPUB safe fields: memo, setPageOrSection, moveByPages.");
            beginnerEditHeader.put("02_KO_수정가능_필드", "TXT 수정 가능 필드: memo, setLine, moveByLines, findText, findOccurrence, findTextCaseSensitive. PDF/Word 수정 가능 필드: memo, setPage, moveByPages. EPUB 수정 가능 필드: memo, setPageOrSection, moveByPages.");
            beginnerEditHeader.put("03_EN_DO_NOT_EDIT", "Do not edit bookmarkId_DoNotEdit, fileName_DoNotEdit, fileType_DoNotEdit, currentPreview_DoNotEdit, currentLine_DoNotEdit, currentPage_DoNotEdit, currentPageOrSection_DoNotEdit, or originalPosition_DoNotEdit. They are labels/context used to identify the bookmark.");
            beginnerEditHeader.put("03_KO_수정금지", "bookmarkId_DoNotEdit, fileName_DoNotEdit, fileType_DoNotEdit, currentPreview_DoNotEdit, currentLine_DoNotEdit, currentPage_DoNotEdit, currentPageOrSection_DoNotEdit, originalPosition_DoNotEdit은 수정하지 마세요. 북마크 식별/참고용 값입니다.");
            beginnerEditHeader.put("04_EN_PREVIEW_RULE", "currentPreview_DoNotEdit is only the old saved context. It will not change while you edit this file on PC. After import, TextView Reader recalculates the real preview and TXT anchor text from the new target location.");
            beginnerEditHeader.put("04_KO_프리뷰_규칙", "currentPreview_DoNotEdit은 기존 저장 위치의 참고 문장일 뿐입니다. PC에서 이 파일을 수정하는 동안 자동으로 바뀌지 않습니다. 가져오기 후 TextView Reader가 새 위치 기준으로 실제 미리보기와 TXT 앵커를 다시 계산합니다.");
            beginnerEditHeader.put("05_EN_TXT_SETLINE", "TXT exact line move: setLine is the final line number you want. Example: setLine=120 and moveByLines=0 moves the bookmark to logical line 120.");
            beginnerEditHeader.put("05_KO_TXT_SETLINE", "TXT 정확한 줄 이동: setLine은 원하는 최종 줄 번호입니다. 예: setLine=120, moveByLines=0이면 북마크를 논리 줄 번호 120번으로 이동합니다.");
            beginnerEditHeader.put("06_EN_TXT_MOVEBYLINES", "TXT relative correction: moveByLines moves from the chosen base line. Positive numbers move down/later; negative numbers move up/earlier. Example: currentLine_DoNotEdit=93, setLine=93, moveByLines=2 -> line 95. setLine=93, moveByLines=-3 -> line 90.");
            beginnerEditHeader.put("06_KO_TXT_MOVEBYLINES", "TXT 상대 보정: moveByLines는 기준 줄에서 위/아래로 이동합니다. 양수는 아래/뒤쪽, 음수는 위/앞쪽입니다. 예: currentLine_DoNotEdit=93, setLine=93, moveByLines=2 -> 95번째 줄. setLine=93, moveByLines=-3 -> 90번째 줄.");
            beginnerEditHeader.put("07_EN_TXT_FINDTEXT", "TXT phrase move: use findText when you do not know the line number. Example: findText=\"important sentence\", findOccurrence=2, moveByLines=-1 means find the 2nd matching phrase and place the bookmark one line above it. Leave findText empty if you do not want search-based movement.");
            beginnerEditHeader.put("07_KO_TXT_FINDTEXT", "TXT 문장 검색 이동: 줄 번호를 모르면 findText를 쓰세요. 예: findText=\"중요한 문장\", findOccurrence=2, moveByLines=-1이면 두 번째로 일치하는 문장을 찾고 그보다 한 줄 위에 북마크를 둡니다. 검색 이동을 쓰지 않으면 findText는 빈칸으로 두세요.");
            beginnerEditHeader.put("08_EN_TXT_FIND_CASE", "findTextCaseSensitive=false ignores uppercase/lowercase differences for English text. Set true only when exact capitalization must match.");
            beginnerEditHeader.put("08_KO_TXT_대소문자", "findTextCaseSensitive=false는 영어 대문자/소문자 차이를 무시합니다. 대소문자까지 정확히 맞아야 할 때만 true로 바꾸세요.");
            beginnerEditHeader.put("09_EN_PDF_WORD", "PDF/Word page move: setPage is the exact page number. moveByPages is an extra correction. Example: setPage=10, moveByPages=0 -> page 10. setPage=10, moveByPages=-2 -> page 8. setPage=10, moveByPages=3 -> page 13.");
            beginnerEditHeader.put("09_KO_PDF_WORD", "PDF/Word 페이지 이동: setPage는 정확한 페이지 번호입니다. moveByPages는 추가 보정입니다. 예: setPage=10, moveByPages=0 -> 10페이지. setPage=10, moveByPages=-2 -> 8페이지. setPage=10, moveByPages=3 -> 13페이지.");
            beginnerEditHeader.put("10_EN_EPUB", "EPUB uses setPageOrSection because EPUB layout can depend on the app's page/section calculation. Example: setPageOrSection=25, moveByPages=0 moves to app page/section 25.");
            beginnerEditHeader.put("10_KO_EPUB", "EPUB은 앱의 페이지/섹션 계산에 따라 위치가 달라질 수 있어서 setPageOrSection을 씁니다. 예: setPageOrSection=25, moveByPages=0이면 앱 기준 25번째 페이지/섹션으로 이동합니다.");
            beginnerEditHeader.put("11_EN_MEMO", "memo is free text. You may write any note in memo. Empty memo is also valid.");
            beginnerEditHeader.put("11_KO_MEMO", "memo는 자유 입력 메모입니다. 원하는 내용을 적어도 되고 빈칸으로 두어도 됩니다.");
            beginnerEditHeader.put("12_EN_IMPORT_RESULT", "On import, TextView Reader applies beginnerEditableBookmarks edits to the internal bookmarks. For TXT, it also regenerates excerpt, anchorTextBefore, and anchorTextAfter from the new location.");
            beginnerEditHeader.put("12_KO_IMPORT_RESULT", "가져오기 시 TextView Reader가 beginnerEditableBookmarks의 수정값을 내부 북마크에 반영합니다. TXT는 새 위치 기준으로 excerpt, anchorTextBefore, anchorTextAfter도 다시 생성합니다.");
            beginnerEditHeader.put("13_EN_COMMON_MISTAKE", "Common mistake: changing setLine from 93 to 120 but expecting currentPreview_DoNotEdit to update inside this PC file. It will not. Import first, then export again to see refreshed previews.");
            beginnerEditHeader.put("13_KO_COMMON_MISTAKE", "흔한 실수: setLine을 93에서 120으로 바꾼 뒤 PC 파일 안에서 currentPreview_DoNotEdit도 같이 바뀌길 기대하는 것입니다. 이 값은 자동으로 바뀌지 않습니다. 먼저 앱에서 가져오기한 뒤 다시 내보내야 갱신된 미리보기를 볼 수 있습니다.");

            JSONObject beginnerExamples = new JSONObject();
            beginnerExamples.put("TXT_example_1_exact_line_EN", "Before: currentLine_DoNotEdit=93, setLine=93, moveByLines=0. Edit to: setLine=120, moveByLines=0. Result: bookmark moves to line 120.");
            beginnerExamples.put("TXT_example_1_exact_line_KO", "수정 전: currentLine_DoNotEdit=93, setLine=93, moveByLines=0. 수정: setLine=120, moveByLines=0. 결과: 북마크가 120번째 줄로 이동합니다.");
            beginnerExamples.put("TXT_example_2_small_fix_EN", "Before: bookmark is almost correct but should be 2 lines lower. Leave setLine as-is and set moveByLines=2. To move 2 lines higher, use moveByLines=-2.");
            beginnerExamples.put("TXT_example_2_small_fix_KO", "북마크가 거의 맞지만 2줄 아래가 맞는 경우 setLine은 그대로 두고 moveByLines=2로 바꾸세요. 2줄 위로 올리려면 moveByLines=-2를 쓰세요.");
            beginnerExamples.put("TXT_example_3_phrase_EN", "To move by phrase instead of line number: set findText=\"chapter begins here\", findOccurrence=1, moveByLines=0. If there are many matches, increase findOccurrence to 2, 3, 4, ...");
            beginnerExamples.put("TXT_example_3_phrase_KO", "줄 번호 대신 문장으로 이동하려면 findText=\"이 장면이 시작되는 문장\", findOccurrence=1, moveByLines=0으로 두세요. 같은 문장이 여러 번 나오면 findOccurrence를 2, 3, 4...로 올리세요.");
            beginnerExamples.put("TXT_example_4_phrase_plus_correction_EN", "findText=\"important sentence\", findOccurrence=2, moveByLines=-1 means: find the second match, then save one line above it.");
            beginnerExamples.put("TXT_example_4_phrase_plus_correction_KO", "findText=\"중요한 문장\", findOccurrence=2, moveByLines=-1의 의미: 두 번째 일치 문장을 찾은 뒤 그보다 한 줄 위에 저장합니다.");
            beginnerExamples.put("PDF_example_EN", "PDF page edit: setPage=42, moveByPages=0 -> page 42. setPage=42, moveByPages=1 -> page 43. setPage=42, moveByPages=-5 -> page 37.");
            beginnerExamples.put("PDF_example_KO", "PDF 페이지 수정: setPage=42, moveByPages=0 -> 42페이지. setPage=42, moveByPages=1 -> 43페이지. setPage=42, moveByPages=-5 -> 37페이지.");
            beginnerExamples.put("EPUB_example_EN", "EPUB edit: setPageOrSection=25, moveByPages=0 -> app page/section 25. Use moveByPages only for small correction.");
            beginnerExamples.put("EPUB_example_KO", "EPUB 수정: setPageOrSection=25, moveByPages=0 -> 앱 기준 25번째 페이지/섹션. 작은 보정만 필요할 때 moveByPages를 쓰세요.");
            beginnerExamples.put("Memo_example_EN", "memo=\"Return here later\" changes only the bookmark note. It does not move the bookmark unless position fields are also changed.");
            beginnerExamples.put("Memo_example_KO", "memo=\"나중에 여기 다시 보기\"는 북마크 메모만 바꿉니다. 위치 관련 필드를 바꾸지 않으면 북마크 위치는 이동하지 않습니다.");
            beginnerExamples.put("Import_export_refresh_EN", "To verify edited previews: import this JSON in Settings, open/relink the file if needed, then export backup again. The new backup will contain refreshed preview/anchor fields.");
            beginnerExamples.put("Import_export_refresh_KO", "수정된 미리보기를 확인하려면 설정에서 이 JSON을 가져오기하고, 필요하면 파일을 열어/다시 연결한 뒤 백업을 다시 내보내세요. 새 백업에는 갱신된 미리보기/앵커가 들어갑니다.");
            beginnerEditHeader.put("14_DETAILED_EXAMPLES_READ_THIS", beginnerExamples);
            root.put("BEGINNER_EDIT_HEADER_READ_FIRST", beginnerEditHeader);

            JSONObject beginnerGuide = new JSONObject();
            beginnerGuide.put("EN_step_1", "Open this JSON file on a PC with a plain text editor. Search for beginnerEditableBookmarks. That is the beginner editing area.");
            beginnerGuide.put("KO_1단계", "PC에서 이 JSON 파일을 일반 텍스트 편집기로 여세요. beginnerEditableBookmarks를 검색하세요. 그 부분이 초보자 수정 영역입니다.");
            beginnerGuide.put("EN_step_2", "Find the bookmark by fileName_DoNotEdit and currentPreview_DoNotEdit. Do not change those fields; they only help you identify the bookmark.");
            beginnerGuide.put("KO_2단계", "fileName_DoNotEdit과 currentPreview_DoNotEdit을 보고 수정할 북마크를 찾으세요. 이 필드들은 식별용이므로 바꾸지 마세요.");
            beginnerGuide.put("EN_step_3", "Edit only memo and the position fields for that file type. TXT uses setLine/moveByLines/findText. PDF/Word uses setPage/moveByPages. EPUB uses setPageOrSection/moveByPages.");
            beginnerGuide.put("KO_3단계", "memo와 해당 파일 형식의 위치 필드만 수정하세요. TXT는 setLine/moveByLines/findText, PDF/Word는 setPage/moveByPages, EPUB은 setPageOrSection/moveByPages를 씁니다.");
            beginnerGuide.put("EN_step_4", "Save the JSON file without changing its extension, then import it back from TextView Reader Settings. Choose Merge if you want to keep current app data, or Replace if this backup should overwrite current app data.");
            beginnerGuide.put("KO_4단계", "확장자를 바꾸지 말고 JSON 파일을 저장한 뒤 TextView Reader 설정에서 다시 가져오세요. 현재 앱 데이터를 유지하려면 합치기, 이 백업으로 덮어쓰려면 바꾸기를 선택하세요.");
            beginnerGuide.put("safeFields", "TXT: memo, setLine, moveByLines, findText, findOccurrence, findTextCaseSensitive. PDF/Word: memo, setPage, moveByPages. EPUB: memo, setPageOrSection, moveByPages.");
            beginnerGuide.put("doNotEdit", "Any field whose name includes DoNotEdit, plus the internal bookmarks/readingStates/settings/customThemes sections unless you intentionally want advanced machine-level editing.");
            beginnerGuide.put("positionRule_EN", "All visible positions in beginnerEditableBookmarks start from 1. TXT line 1 is the first logical line. PDF page 1 is the first page. EPUB page/section 1 is the first app page/section value.");
            beginnerGuide.put("positionRule_KO", "beginnerEditableBookmarks의 보이는 위치값은 모두 1부터 시작합니다. TXT 1줄은 첫 번째 논리 줄, PDF 1페이지는 첫 페이지, EPUB 1페이지/섹션은 앱 기준 첫 번째 페이지/섹션입니다.");
            beginnerGuide.put("setVsMove_EN", "setLine/setPage/setPageOrSection chooses the absolute target. moveByLines/moveByPages then adds a relative correction. Usually edit only one of them: exact target -> change setLine/setPage and leave moveBy=0; small correction -> leave setLine/setPage as-is and change moveBy.");
            beginnerGuide.put("setVsMove_KO", "setLine/setPage/setPageOrSection은 절대 목표 위치입니다. moveByLines/moveByPages는 거기에 추가로 더하는 상대 보정입니다. 보통 둘 중 하나만 고치세요. 정확한 목표를 알면 setLine/setPage만 바꾸고 moveBy=0으로 두세요. 조금만 보정하려면 setLine/setPage는 그대로 두고 moveBy만 바꾸세요.");
            beginnerGuide.put("txtSearchRule_EN", "For TXT bookmarks, findText can move the bookmark to a phrase without knowing the line number. findOccurrence selects which match to use. moveByLines is applied after findText or setLine.");
            beginnerGuide.put("txtSearchRule_KO", "TXT 북마크는 줄 번호를 몰라도 findText로 문장 위치를 찾을 수 있습니다. findOccurrence는 몇 번째 일치 문장을 쓸지 정합니다. moveByLines는 findText 또는 setLine 적용 후 추가로 반영됩니다.");
            beginnerGuide.put("previewRule_EN", "currentPreview_DoNotEdit is reference-only. It is not a live preview. It updates only after you import the JSON and export again.");
            beginnerGuide.put("previewRule_KO", "currentPreview_DoNotEdit은 참고용입니다. 실시간 미리보기가 아닙니다. JSON을 가져오기한 뒤 다시 내보내야 갱신됩니다.");
            beginnerGuide.put("portableRule_EN", "Paths are local shortcuts. If the same file is moved or restored on another device, open that file once in TextView Reader so the app can rebind bookmarks by file identity.");
            beginnerGuide.put("portableRule_KO", "경로는 기기별 로컬 바로가기일 뿐입니다. 같은 파일을 다른 폴더나 다른 기기에 복원했다면 TextView Reader에서 그 파일을 한 번 열어 북마크가 파일 식별값으로 다시 연결되게 하세요.");

            JSONArray beginnerWalkthroughs = new JSONArray();
            JSONObject walkthroughTxtExact = new JSONObject();
            walkthroughTxtExact.put("case_EN", "TXT exact line move");
            walkthroughTxtExact.put("case_KO", "TXT 정확한 줄 이동");
            walkthroughTxtExact.put("before", "currentLine_DoNotEdit=93, setLine=93, moveByLines=0");
            walkthroughTxtExact.put("edit", "setLine=120, moveByLines=0");
            walkthroughTxtExact.put("result_EN", "Bookmark moves to line 120.");
            walkthroughTxtExact.put("result_KO", "북마크가 120번째 줄로 이동합니다.");
            beginnerWalkthroughs.put(walkthroughTxtExact);

            JSONObject walkthroughTxtRelative = new JSONObject();
            walkthroughTxtRelative.put("case_EN", "TXT small correction");
            walkthroughTxtRelative.put("case_KO", "TXT 작은 위치 보정");
            walkthroughTxtRelative.put("before", "currentLine_DoNotEdit=93, setLine=93, moveByLines=0");
            walkthroughTxtRelative.put("edit", "moveByLines=2");
            walkthroughTxtRelative.put("result_EN", "Bookmark moves to line 95. Use moveByLines=-2 to move to line 91.");
            walkthroughTxtRelative.put("result_KO", "북마크가 95번째 줄로 이동합니다. moveByLines=-2를 쓰면 91번째 줄로 이동합니다.");
            beginnerWalkthroughs.put(walkthroughTxtRelative);

            JSONObject walkthroughTxtFind = new JSONObject();
            walkthroughTxtFind.put("case_EN", "TXT phrase search");
            walkthroughTxtFind.put("case_KO", "TXT 문장 검색 이동");
            walkthroughTxtFind.put("before", "findText=\"\", findOccurrence=1, moveByLines=0");
            walkthroughTxtFind.put("edit", "findText=\"the sentence I want\", findOccurrence=1, moveByLines=0");
            walkthroughTxtFind.put("result_EN", "Bookmark moves to the first matching phrase. If the phrase appears multiple times, use findOccurrence=2 or 3.");
            walkthroughTxtFind.put("result_KO", "북마크가 첫 번째 일치 문장으로 이동합니다. 같은 문장이 여러 번 나오면 findOccurrence=2 또는 3을 쓰세요.");
            beginnerWalkthroughs.put(walkthroughTxtFind);

            JSONObject walkthroughPdf = new JSONObject();
            walkthroughPdf.put("case_EN", "PDF/Word page move");
            walkthroughPdf.put("case_KO", "PDF/Word 페이지 이동");
            walkthroughPdf.put("before", "currentPage_DoNotEdit=5, setPage=5, moveByPages=0");
            walkthroughPdf.put("edit", "setPage=10, moveByPages=0");
            walkthroughPdf.put("result_EN", "Bookmark moves to page 10. setPage=10 and moveByPages=-2 moves to page 8.");
            walkthroughPdf.put("result_KO", "북마크가 10페이지로 이동합니다. setPage=10, moveByPages=-2이면 8페이지로 이동합니다.");
            beginnerWalkthroughs.put(walkthroughPdf);

            beginnerGuide.put("sampleWalkthroughs", beginnerWalkthroughs);
            root.put("beginnerEditGuide", beginnerGuide);
            root.put("SECTION_1_BOOKMARK_TUTORIAL_READ_ONLY_END", "==================== END OF TUTORIAL / 튜토리얼 끝 ==================== Stop reading examples here. The actual editable bookmark list starts below the next section marker. 설명은 여기까지입니다. 실제 수정 영역은 다음 구분선 아래에서 시작합니다.");
            root.put("SECTION_2_ACTUAL_BOOKMARK_EDIT_AREA_START", "==================== ACTUAL BOOKMARK EDIT AREA / 실제 북마크 수정 영역 ==================== Edit the objects inside beginnerEditableBookmarks below. 아래 beginnerEditableBookmarks 안의 항목만 수정하세요.");

            JSONArray beginnerArr = new JSONArray();
            for (Bookmark b : bookmarks) {
                beginnerArr.put(b.toBeginnerEditJson());
            }
            root.put("beginnerEditableBookmarks", beginnerArr);
            root.put("SECTION_2_ACTUAL_BOOKMARK_EDIT_AREA_END", "==================== END OF ACTUAL BOOKMARK EDIT AREA / 실제 북마크 수정 영역 끝 ==================== Fields below are internal backup/help sections. 아래 영역은 내부 백업/도움말 영역입니다.");

            JSONObject pcEditGuide = new JSONObject();
            JSONArray safeFields = new JSONArray();
            safeFields.put("beginnerEditableBookmarks[].memo");
            safeFields.put("beginnerEditableBookmarks[].setLine");
            safeFields.put("beginnerEditableBookmarks[].moveByLines");
            safeFields.put("beginnerEditableBookmarks[].findText");
            safeFields.put("beginnerEditableBookmarks[].findOccurrence");
            safeFields.put("beginnerEditableBookmarks[].findTextCaseSensitive");
            safeFields.put("beginnerEditableBookmarks[].setPage");
            safeFields.put("beginnerEditableBookmarks[].setPageOrSection");
            safeFields.put("beginnerEditableBookmarks[].moveByPages");
            safeFields.put("beginnerEditableBookmarks[].position");
            safeFields.put("bookmarks[].pcEditPosition");
            safeFields.put("bookmarks[].memo");
            pcEditGuide.put("safeFields", safeFields);
            pcEditGuide.put("positionRule", "TXT setLine uses 1-based logical line numbers; PDF/Word setPage and EPUB setPageOrSection use 1-based page/section numbers.");
            pcEditGuide.put("doNotEdit", "Fields containing DoNotEdit, plus id, filePath, localBindingPath, fileIdentity, quickFingerprint, charPosition, anchorTextBefore, anchorTextAfter, pendingPcTextEdit, createdAt, updatedAt");
            pcEditGuide.put("portableRule", "filePath/localBindingPath are local shortcuts. If the same file is opened from another folder/device, fileIdentity is used for rebinding.");
            root.put("pcEditGuide", pcEditGuide);
            root.put("internalSectionsNote", "The sections below are the full machine backup. For normal PC editing, use beginnerEditableBookmarks above; the app maps those edits back into the internal backup on import.");

            // Bookmarks
            JSONArray arr = new JSONArray();
            for (Bookmark b : bookmarks) {
                arr.put(b.toJson());
            }
            root.put("bookmarks", arr);

            // Reading states
            JSONObject statesObj = new JSONObject();
            for (Map.Entry<String, ReaderState> entry : readingStates.entrySet()) {
                statesObj.put(entry.getKey(), entry.getValue().toJson());
            }
            root.put("readingStates", statesObj);

            // App settings, including layout, theme selection, behavior, sorting,
            // brightness, TXT/EPUB boundaries, font family, and recent folder prefs.
            // Security PIN data is skipped inside PrefsManager.
            root.put("settings", PrefsManager.getInstance(context).exportSettingsToJson());

            // Custom reading themes are stored outside SharedPreferences, so keep
            // them in the same backup JSON as the active_theme_id setting.
            root.put("customThemes", ThemeManager.getInstance(context).exportCustomThemesToJson());

            return root.toString(2); // pretty-printed for readability
        } catch (JSONException e) {
            Log.e(TAG, "Export failed", e);
            return "{}";
        }
    }

    /**
     * Import bookmarks and states from a JSON string.
     * @param merge if true, merge with existing; if false, replace all
     */
    public void importAll(String jsonString, boolean merge) {
        try {
            JSONObject root = new JSONObject(jsonString);

            // Import bookmarks
            Map<String, JSONObject> beginnerEditMap = readBeginnerEditableBookmarkMap(root);
            JSONArray arr = root.optJSONArray("bookmarks");
            if (arr != null) {
                if (!merge) {
                    bookmarks.clear();
                }
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject bookmarkObj = arr.getJSONObject(i);
                    Bookmark b = Bookmark.fromJson(bookmarkObj);
                    applyPcEditableBookmarkFields(bookmarkObj, b);
                    applyBeginnerEditableBookmarkFields(beginnerEditMap.get(b.getId()), b);
                    enrichPortableIdentity(b);
                    if (merge) {
                        // If this is the same bookmark id, replace it so PC edits
                        // from the backup are not lost during a merge import.
                        int sameIdIndex = -1;
                        for (int j = 0; j < bookmarks.size(); j++) {
                            if (safeEquals(bookmarks.get(j).getId(), b.getId())) {
                                sameIdIndex = j;
                                break;
                            }
                        }
                        if (sameIdIndex >= 0) {
                            bookmarks.set(sameIdIndex, b);
                        } else {
                            boolean duplicate = false;
                            for (Bookmark existing : bookmarks) {
                                if (isSameBookmarkLocation(existing, b)) {
                                    duplicate = true;
                                    break;
                                }
                            }
                            if (!duplicate) {
                                bookmarks.add(b);
                            }
                        }
                    } else {
                        bookmarks.add(b);
                    }
                }
                saveBookmarks();
            }

            // Import reading states
            JSONObject statesObj = root.optJSONObject("readingStates");
            if (statesObj != null) {
                if (!merge) {
                    readingStates.clear();
                }
                Iterator<String> keys = statesObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    ReaderState state = ReaderState.fromJson(statesObj.getJSONObject(key));
                    readingStates.put(key, state);
                }
                saveReadingStates();
            }

            // Import settings and custom themes when present. Older backups that
            // only contain bookmarks/reading states remain valid.
            JSONObject settingsObj = root.optJSONObject("settings");
            if (settingsObj != null) {
                PrefsManager.getInstance(context).importSettingsFromJson(settingsObj, merge);
            }

            JSONArray themesArr = root.optJSONArray("customThemes");
            if (themesArr != null) {
                ThemeManager.getInstance(context).importCustomThemesFromJson(themesArr, merge);
            }
        } catch (Exception e) {
            Log.e(TAG, "Import failed", e);
        }
    }


    private Map<String, JSONObject> readBeginnerEditableBookmarkMap(JSONObject root) {
        Map<String, JSONObject> result = new HashMap<>();
        if (root == null) return result;
        JSONArray arr = root.optJSONArray("beginnerEditableBookmarks");
        if (arr == null) return result;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject editObj = arr.optJSONObject(i);
            if (editObj == null) continue;
            String id = editObj.optString("bookmarkId_DoNotEdit", "");
            if (id == null || id.trim().isEmpty()) {
                id = editObj.optString("bookmarkId", "");
            }
            if (id != null && !id.trim().isEmpty()) {
                result.put(id, editObj);
            }
        }
        return result;
    }

    /**
     * Apply the beginner-facing edit row when it exists.
     *
     * Unlike the first beginner JSON shape, this does not limit users to one
     * absolute position field.  TXT can be edited by exact line, relative line
     * movement, or search text; document bookmarks can be edited by exact page
     * and/or relative page movement.
     */
    private void applyBeginnerEditableBookmarkFields(JSONObject editObj, Bookmark bookmark) {
        if (editObj == null || bookmark == null) return;

        if (editObj.has("memo")) {
            bookmark.setLabel(editObj.optString("memo", ""));
        }

        if (isPdfBookmark(bookmark) || isEpubBookmark(bookmark) || isWordBookmark(bookmark)) {
            applyBeginnerDocumentEdit(editObj, bookmark);
        } else {
            applyBeginnerTextEdit(editObj, bookmark);
        }
    }

    private void applyBeginnerDocumentEdit(JSONObject editObj, Bookmark bookmark) {
        String type = bookmarkFileType(bookmark);
        int current;
        int setPosition;
        int moveBy;

        if ("EPUB".equals(type)) {
            current = readPositiveInt(editObj, "currentPageOrSection_DoNotEdit", Integer.MIN_VALUE);
            setPosition = readPositiveInt(editObj, "setPageOrSection", -1);
        } else {
            current = readPositiveInt(editObj, "currentPage_DoNotEdit", Integer.MIN_VALUE);
            setPosition = readPositiveInt(editObj, "setPage", -1);
        }

        int originalPosition = readPositiveInt(editObj, "originalPosition_DoNotEdit", Integer.MIN_VALUE);
        if (current == Integer.MIN_VALUE) {
            current = originalPosition;
        }
        int legacyPosition = readPositiveInt(editObj, "position", -1);
        boolean legacyPositionEdited = legacyPosition > 0
                && (originalPosition == Integer.MIN_VALUE || legacyPosition != originalPosition);
        if (legacyPositionEdited && (setPosition <= 0 || setPosition == current)) {
            setPosition = legacyPosition;
        } else if (setPosition <= 0) {
            setPosition = legacyPosition;
        }
        moveBy = readSignedInt(editObj, "moveByPages", 0);
        if (moveBy == 0) {
            moveBy = readSignedInt(editObj, "moveBySections", 0);
        }
        if (moveBy == 0) {
            moveBy = readSignedInt(editObj, "moveBy", 0);
        }

        boolean changedAbsolute = setPosition > 0
                && (current == Integer.MIN_VALUE || setPosition != current);
        boolean changedRelative = moveBy != 0;
        if (!changedAbsolute && !changedRelative) return;

        int base = setPosition > 0 ? setPosition : (current != Integer.MIN_VALUE ? current : bookmark.getPcEditPositionForManager());
        int target = Math.max(1, base + moveBy);
        applyPcDocumentPosition(bookmark, target);
    }

    private void applyBeginnerTextEdit(JSONObject editObj, Bookmark bookmark) {
        int currentLine = readPositiveInt(editObj, "currentLine_DoNotEdit", Integer.MIN_VALUE);
        int originalPosition = readPositiveInt(editObj, "originalPosition_DoNotEdit", Integer.MIN_VALUE);
        if (currentLine == Integer.MIN_VALUE) {
            currentLine = originalPosition;
        }

        int setLine = readPositiveInt(editObj, "setLine", -1);
        int legacyPosition = readPositiveInt(editObj, "position", -1);
        boolean legacyPositionEdited = legacyPosition > 0
                && (originalPosition == Integer.MIN_VALUE || legacyPosition != originalPosition);
        if (legacyPositionEdited && (setLine <= 0 || setLine == currentLine)) {
            setLine = legacyPosition;
        } else if (setLine <= 0) {
            // Backward-compatible alias from the earlier beginner JSON shape.
            setLine = legacyPosition;
        }

        int moveByLines = readSignedInt(editObj, "moveByLines", 0);
        if (moveByLines == 0) {
            moveByLines = readSignedInt(editObj, "moveBy", 0);
        }

        String findText = readTrimmedString(editObj, "findText");
        int findOccurrence = Math.max(1, readPositiveInt(editObj, "findOccurrence", 1));
        boolean findCaseSensitive = readBoolean(editObj, "findTextCaseSensitive", false);

        boolean changedByFind = !findText.isEmpty();
        boolean changedAbsolute = setLine > 0
                && (currentLine == Integer.MIN_VALUE || setLine != currentLine);
        boolean changedRelative = moveByLines != 0;
        if (!changedByFind && !changedAbsolute && !changedRelative) return;

        File file = fileFromPath(bookmark.getFilePath());
        if (file != null && file.exists() && file.isFile()) {
            try {
                String text = FileUtils.readTextFile(file);
                boolean applied = applyTextEditWithContent(
                        bookmark,
                        text,
                        findText,
                        findOccurrence,
                        findCaseSensitive,
                        setLine,
                        currentLine,
                        moveByLines);
                if (applied) {
                    bookmark.clearPendingPcTextEdit();
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply beginner TXT bookmark edit immediately for " + file, e);
            }
        }

        // The file may not exist on this device yet.  Keep the edit as a pending
        // portable action and resolve it the first time the matching local file is
        // opened/rebound.  This prevents cross-device backup edits from silently
        // falling back to the old charPosition.
        int fallbackLine = setLine > 0 ? setLine : (currentLine != Integer.MIN_VALUE ? currentLine : bookmark.getLineNumber());
        bookmark.setPendingPcEditLine(Math.max(1, fallbackLine));
        bookmark.setPendingPcMoveByLines(moveByLines);
        bookmark.setPendingPcFindText(findText);
        bookmark.setPendingPcFindOccurrence(findOccurrence);
        bookmark.setPendingPcFindCaseSensitive(findCaseSensitive);
        if (setLine > 0 || moveByLines != 0) {
            bookmark.setLineNumber(Math.max(1, fallbackLine + moveByLines));
        }
        bookmark.setPageNumber(0);
        bookmark.setTotalPages(0);
    }

    private int readPositiveInt(JSONObject obj, String key, int fallback) {
        if (obj == null || key == null || !obj.has(key)) return fallback;
        try {
            Object raw = obj.opt(key);
            if (raw instanceof Number) {
                int value = ((Number) raw).intValue();
                return value > 0 ? value : fallback;
            }
            String text = obj.optString(key, "").trim();
            if (text.isEmpty()) return fallback;
            int value = Integer.parseInt(text);
            return value > 0 ? value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int readSignedInt(JSONObject obj, String key, int fallback) {
        if (obj == null || key == null || !obj.has(key)) return fallback;
        try {
            Object raw = obj.opt(key);
            if (raw instanceof Number) return ((Number) raw).intValue();
            String text = obj.optString(key, "").trim();
            if (text.isEmpty()) return fallback;
            return Integer.parseInt(text);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String readTrimmedString(JSONObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) return "";
        String value = obj.optString(key, "");
        return value != null ? value.trim() : "";
    }

    private boolean readBoolean(JSONObject obj, String key, boolean fallback) {
        if (obj == null || key == null || !obj.has(key)) return fallback;
        Object raw = obj.opt(key);
        if (raw instanceof Boolean) return (Boolean) raw;
        String text = obj.optString(key, "").trim().toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "yes".equals(text) || "1".equals(text)) return true;
        if ("false".equals(text) || "no".equals(text) || "0".equals(text)) return false;
        return fallback;
    }

    /**
     * Apply fields intended for PC-side editing of exported JSON backups.
     *
     * Internal bookmarks still use charPosition for fast/exact restore.  For a
     * human-editable backup, charPosition is annoying to calculate manually, so
     * exported bookmarks also include pcEditPosition plus pcEditOriginalPosition.
     * The importer only treats pcEditPosition as authoritative when it differs
     * from pcEditOriginalPosition, which means an unedited backup imports without
     * losing the exact original character offset inside long TXT lines.
     */
    private void applyPcEditableBookmarkFields(JSONObject obj, Bookmark bookmark) {
        if (obj == null || bookmark == null || !obj.has("pcEditPosition")) return;

        int pcPosition = obj.optInt("pcEditPosition", -1);
        if (pcPosition <= 0) return;

        int original = obj.has("pcEditOriginalPosition")
                ? obj.optInt("pcEditOriginalPosition", pcPosition)
                : Integer.MIN_VALUE;
        boolean editedOnPc = !obj.has("pcEditOriginalPosition") || pcPosition != original;
        if (!editedOnPc) return;

        if (isPdfBookmark(bookmark) || isEpubBookmark(bookmark) || isWordBookmark(bookmark)) {
            applyPcDocumentPosition(bookmark, pcPosition);
        } else {
            applyPcTextLinePosition(bookmark, pcPosition);
        }
    }

    private void applyPcDocumentPosition(Bookmark bookmark, int oneBasedPosition) {
        int zeroBased = Math.max(0, oneBasedPosition - 1);
        bookmark.setCharPosition(zeroBased);
        bookmark.setEndPosition(zeroBased + 1);
        bookmark.setLineNumber(oneBasedPosition);
        bookmark.setPageNumber(oneBasedPosition);
        // Keep totalPages when it was known. The viewer will still clamp invalid
        // document page jumps against the actual opened file.
    }

    private void applyPcTextLinePosition(Bookmark bookmark, int oneBasedLine) {
        bookmark.setLineNumber(Math.max(1, oneBasedLine));
        bookmark.setPageNumber(0);
        bookmark.setTotalPages(0);

        String path = bookmark.getFilePath();
        if (path == null || path.trim().isEmpty()) return;

        File file = new File(path);
        if (!file.exists() || !file.isFile()) return;

        try {
            String text = FileUtils.readTextFile(file);
            applyPcTextLinePositionFromText(bookmark, text, oneBasedLine);
            bookmark.clearPendingPcTextEdit();
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply PC-edited bookmark position for " + path, e);
        }
    }

    private void applyPcTextLinePositionFromText(Bookmark bookmark, String text, int oneBasedLine) {
        int safeLine = Math.max(1, oneBasedLine);
        int charPosition = findCharPositionForOneBasedLine(text, safeLine);
        applyPcTextCharPositionFromText(bookmark, text, charPosition);
        bookmark.setLineNumber(safeLine);
    }

    private void applyPcTextCharPositionFromText(Bookmark bookmark, String text, int charPosition) {
        if (bookmark == null) return;
        int safePosition = Math.max(0, Math.min(text != null ? text.length() : 0, charPosition));
        String excerpt = makeBookmarkExcerpt(text, safePosition);
        bookmark.setCharPosition(safePosition);
        bookmark.setLineNumber(countOneBasedLineForChar(text, safePosition));
        bookmark.setPageNumber(0);
        bookmark.setTotalPages(0);
        bookmark.setExcerpt(excerpt);
        bookmark.setAnchorTextBefore(makeAnchorTextBefore(text, safePosition));
        bookmark.setAnchorTextAfter(makeAnchorTextAfter(text, safePosition));
        bookmark.setEndPosition(Math.min(
                text != null ? text.length() : safePosition,
                safePosition + Math.max(1, excerpt.length())));
    }

    private boolean applyTextEditWithContent(Bookmark bookmark, String text, String findText,
                                             int findOccurrence, boolean caseSensitive,
                                             int setLine, int currentLine, int moveByLines) {
        if (bookmark == null || text == null) return false;

        int baseChar = -1;
        if (findText != null && !findText.isEmpty()) {
            baseChar = findTextPosition(text, findText, findOccurrence, caseSensitive);
        }

        if (baseChar >= 0) {
            if (moveByLines != 0) {
                int line = countOneBasedLineForChar(text, baseChar);
                applyPcTextLinePositionFromText(bookmark, text, Math.max(1, line + moveByLines));
            } else {
                applyPcTextCharPositionFromText(bookmark, text, baseChar);
            }
            return true;
        }

        int baseLine = setLine > 0 ? setLine : currentLine;
        if (baseLine == Integer.MIN_VALUE || baseLine <= 0) {
            baseLine = Math.max(1, bookmark.getLineNumber());
        }
        if (setLine > 0 || moveByLines != 0) {
            applyPcTextLinePositionFromText(bookmark, text, Math.max(1, baseLine + moveByLines));
            return true;
        }

        return false;
    }

    private int findTextPosition(String text, String needle, int occurrence, boolean caseSensitive) {
        if (text == null || needle == null || needle.isEmpty()) return -1;
        int wanted = Math.max(1, occurrence);
        String haystack = caseSensitive ? text : text.toLowerCase(Locale.ROOT);
        String target = caseSensitive ? needle : needle.toLowerCase(Locale.ROOT);
        int from = 0;
        int found = -1;
        for (int i = 0; i < wanted; i++) {
            found = haystack.indexOf(target, from);
            if (found < 0) return -1;
            from = Math.min(haystack.length(), found + Math.max(1, target.length()));
        }
        return Math.max(0, Math.min(text.length(), found));
    }

    private int countOneBasedLineForChar(String text, int charPosition) {
        if (text == null || text.isEmpty()) return 1;
        int safePosition = Math.max(0, Math.min(text.length(), charPosition));
        int line = 1;
        for (int i = 0; i < safePosition; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    private void resolvePendingPcTextEditsForFile(String filePath, List<Bookmark> result) {
        if (filePath == null || result == null || result.isEmpty()) return;
        boolean hasPending = false;
        for (Bookmark bookmark : result) {
            if (bookmark != null && bookmark.hasPendingPcTextEdit()
                    && !isPdfBookmark(bookmark) && !isEpubBookmark(bookmark) && !isWordBookmark(bookmark)) {
                hasPending = true;
                break;
            }
        }
        if (!hasPending) return;

        File file = fileFromPath(filePath);
        if (file == null || !file.exists() || !file.isFile()) return;

        try {
            String text = FileUtils.readTextFile(file);
            boolean changed = false;
            for (Bookmark bookmark : result) {
                if (bookmark == null || !bookmark.hasPendingPcTextEdit()) continue;
                boolean applied = applyTextEditWithContent(
                        bookmark,
                        text,
                        bookmark.getPendingPcFindText(),
                        bookmark.getPendingPcFindOccurrence(),
                        bookmark.isPendingPcFindCaseSensitive(),
                        bookmark.getPendingPcEditLine(),
                        bookmark.getLineNumber(),
                        bookmark.getPendingPcMoveByLines());
                if (applied) {
                    bookmark.clearPendingPcTextEdit();
                    bookmark.setUpdatedAt(System.currentTimeMillis());
                    changed = true;
                }
            }
            if (changed) saveBookmarks();
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve pending PC TXT bookmark edits for " + filePath, e);
        }
    }

    private int findCharPositionForOneBasedLine(String text, int targetLine) {
        if (text == null || text.isEmpty() || targetLine <= 1) return 0;
        int line = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                if (line >= targetLine) return Math.min(i + 1, text.length());
            }
        }
        return text.length();
    }

    private String makeBookmarkExcerpt(String text, int charPosition) {
        if (text == null || text.isEmpty()) return "";
        int start = Math.max(0, Math.min(text.length(), charPosition));
        int end = Math.min(text.length(), start + 90);
        return text.substring(start, end).trim().replaceAll("[\r\n]+", " ");
    }

    private String makeAnchorTextBefore(String text, int charPosition) {
        if (text == null || text.isEmpty()) return "";
        int pos = Math.max(0, Math.min(text.length(), charPosition));
        int start = Math.max(0, pos - 80);
        return text.substring(start, pos);
    }

    private String makeAnchorTextAfter(String text, int charPosition) {
        if (text == null || text.isEmpty()) return "";
        int pos = Math.max(0, Math.min(text.length(), charPosition));
        int end = Math.min(text.length(), pos + 120);
        return text.substring(pos, end);
    }

    private boolean isPdfBookmark(Bookmark bookmark) {
        return FileUtils.isPdfFile(bookmarkFileName(bookmark));
    }

    private boolean isEpubBookmark(Bookmark bookmark) {
        return FileUtils.isEpubFile(bookmarkFileName(bookmark));
    }

    private boolean isWordBookmark(Bookmark bookmark) {
        return FileUtils.isWordFile(bookmarkFileName(bookmark));
    }

    private String bookmarkFileName(Bookmark bookmark) {
        if (bookmark == null) return "";
        if (bookmark.getFileName() != null && !bookmark.getFileName().isEmpty()) {
            return bookmark.getFileName();
        }
        String path = bookmark.getFilePath();
        if (path == null || path.isEmpty()) return "";
        return new File(path).getName();
    }

    private String bookmarkFileType(Bookmark bookmark) {
        String name = bookmarkFileName(bookmark).toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return "PDF";
        if (name.endsWith(".epub")) return "EPUB";
        if (name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".rtf")) return "WORD";
        return "TXT";
    }


    // ========== Portable bookmark identity ==========

    /**
     * Attach cheap portable identity fields to a bookmark when the target local
     * file is available.  This reads at most three 4KB samples, not the whole
     * file, so save/export stays responsive even for large TXT files.
     */
    private void enrichPortableIdentity(Bookmark bookmark) {
        if (bookmark == null) return;
        File file = fileFromPath(bookmark.getFilePath());
        if (file == null || !file.exists() || !file.isFile()) return;

        if (bookmark.getFileName() == null || bookmark.getFileName().trim().isEmpty()) {
            bookmark.setFileName(file.getName());
        }

        long size = file.length();
        boolean needsFingerprint = bookmark.getQuickFingerprint() == null
                || bookmark.getQuickFingerprint().trim().isEmpty()
                || bookmark.getFileSizeBytes() != size;
        bookmark.setFileSizeBytes(size);

        if (needsFingerprint) {
            String fp = quickFingerprint(file);
            if (fp != null && !fp.isEmpty()) {
                bookmark.setQuickFingerprint(fp);
            }
        }
    }

    private void ensurePortableIdentitiesForExistingBookmarks() {
        boolean changed = false;
        for (Bookmark bookmark : bookmarks) {
            long oldSize = bookmark.getFileSizeBytes();
            String oldFp = bookmark.getQuickFingerprint();
            enrichPortableIdentity(bookmark);
            if (oldSize != bookmark.getFileSizeBytes()
                    || !safeEquals(oldFp, bookmark.getQuickFingerprint())) {
                changed = true;
            }
        }
        if (changed) saveBookmarks();
    }

    /**
     * Rebind imported/moved bookmarks to the currently opened file only when the
     * exact path lookup failed.  No phone-wide scan is performed: the user opens
     * or selects a file, then we compare that single file against stored portable
     * identity fields.
     */
    private int bindPortableBookmarksToFile(String filePath) {
        if (filePath == null || bookmarks.isEmpty()) return 0;
        File file = fileFromPath(filePath);
        if (file == null || !file.exists() || !file.isFile()) return 0;

        String displayName = file.getName();
        boolean hasCandidate = false;
        for (Bookmark bookmark : bookmarks) {
            String existingPath = bookmark.getFilePath();
            if (filePath.equals(existingPath)) continue;
            if (bookmarkFileName(bookmark).equalsIgnoreCase(displayName)) {
                hasCandidate = true;
                break;
            }
        }
        if (!hasCandidate) return 0;

        PortableFileIdentity target = buildPortableFileIdentity(file);
        if (target == null || target.displayName == null || target.displayName.isEmpty()) return 0;

        int bound = 0;
        for (Bookmark bookmark : bookmarks) {
            String existingPath = bookmark.getFilePath();
            if (filePath.equals(existingPath)) continue;
            if (!portableIdentityMatches(bookmark, target)) continue;

            bookmark.setFilePath(filePath);
            bookmark.setFileName(target.displayName);
            bookmark.setFileSizeBytes(target.sizeBytes);
            bookmark.setQuickFingerprint(target.quickFingerprint);
            bookmark.setUpdatedAt(System.currentTimeMillis());
            bound++;
        }

        if (bound > 0) saveBookmarks();
        return bound;
    }

    private boolean portableIdentityMatches(Bookmark bookmark, PortableFileIdentity target) {
        if (bookmark == null || target == null) return false;

        String bookmarkName = bookmarkFileName(bookmark);
        if (bookmarkName == null || bookmarkName.isEmpty()) return false;
        if (!bookmarkName.equalsIgnoreCase(target.displayName)) return false;

        long bookmarkSize = bookmark.getFileSizeBytes();
        String bookmarkFp = bookmark.getQuickFingerprint();

        if (bookmarkFp != null && !bookmarkFp.trim().isEmpty()
                && target.quickFingerprint != null && !target.quickFingerprint.trim().isEmpty()) {
            return bookmarkFp.equals(target.quickFingerprint)
                    && (bookmarkSize <= 0L || bookmarkSize == target.sizeBytes);
        }

        // Conservative fallback for older backups made before quickFingerprint
        // existed: same display name + same byte length.  Do not bind by name only.
        return bookmarkSize > 0L && bookmarkSize == target.sizeBytes;
    }

    private PortableFileIdentity buildPortableFileIdentity(File file) {
        if (file == null || !file.exists() || !file.isFile()) return null;
        PortableFileIdentity identity = new PortableFileIdentity();
        identity.displayName = file.getName();
        identity.sizeBytes = file.length();
        identity.quickFingerprint = quickFingerprint(file);
        return identity;
    }

    private String quickFingerprint(File file) {
        if (file == null || !file.exists() || !file.isFile()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long length = file.length();
            updateDigestWithLong(digest, length);
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                if (length <= QUICK_FINGERPRINT_SAMPLE_BYTES * 3L) {
                    updateDigestWithRange(digest, raf, 0L, length);
                } else {
                    long sample = QUICK_FINGERPRINT_SAMPLE_BYTES;
                    updateDigestWithRange(digest, raf, 0L, sample);
                    long middle = Math.max(0L, (length / 2L) - (sample / 2L));
                    updateDigestWithRange(digest, raf, middle, sample);
                    updateDigestWithRange(digest, raf, Math.max(0L, length - sample), sample);
                }
            }
            return bytesToHex(digest.digest());
        } catch (Exception e) {
            Log.e(TAG, "Failed to compute quick bookmark fingerprint for " + file, e);
            return "";
        }
    }

    private void updateDigestWithRange(MessageDigest digest, RandomAccessFile raf, long start, long count) throws Exception {
        if (digest == null || raf == null || count <= 0L) return;
        long safeStart = Math.max(0L, Math.min(start, raf.length()));
        long remaining = Math.min(count, raf.length() - safeStart);
        raf.seek(safeStart);
        updateDigestWithLong(digest, safeStart);
        updateDigestWithLong(digest, remaining);

        byte[] buffer = new byte[8192];
        while (remaining > 0L) {
            int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read <= 0) break;
            digest.update(buffer, 0, read);
            remaining -= read;
        }
    }

    private void updateDigestWithLong(MessageDigest digest, long value) {
        for (int i = 7; i >= 0; i--) {
            digest.update((byte) ((value >> (i * 8)) & 0xff));
        }
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        char[] hex = new char[bytes.length * 2];
        final char[] table = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xff;
            hex[i * 2] = table[v >>> 4];
            hex[i * 2 + 1] = table[v & 0x0f];
        }
        return new String(hex);
    }

    private File fileFromPath(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        // content:// paths are local Android bindings; this manager only hashes
        // actual local files.  Current readers usually pass resolved local paths.
        if (path.startsWith("content://")) return null;
        try {
            return new File(path);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isSameBookmarkLocation(Bookmark a, Bookmark b) {
        if (a == null || b == null) return false;
        if (safeEquals(a.getFilePath(), b.getFilePath())
                && a.getCharPosition() == b.getCharPosition()) {
            return true;
        }
        if (!bookmarkFileName(a).equalsIgnoreCase(bookmarkFileName(b))) return false;
        if (a.getCharPosition() != b.getCharPosition()) return false;
        String fpA = a.getQuickFingerprint();
        String fpB = b.getQuickFingerprint();
        return fpA != null && !fpA.isEmpty() && fpA.equals(fpB);
    }

    private boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static class PortableFileIdentity {
        String displayName;
        long sizeBytes;
        String quickFingerprint;
    }

    /**
     * Batch replace path prefix in all bookmarks and reading states.
     * Useful when moving files to a new SD card or device.
     * e.g., replacePathPrefix("/storage/AAAA-BBBB", "/storage/CCCC-DDDD")
     */
    public int replacePathPrefix(String oldPrefix, String newPrefix) {
        int count = 0;

        // Update bookmarks
        for (Bookmark b : bookmarks) {
            String bookmarkPath = b.getFilePath();
            if (bookmarkPath != null && bookmarkPath.startsWith(oldPrefix)) {
                b.setFilePath(bookmarkPath.replace(oldPrefix, newPrefix));
                b.setUpdatedAt(System.currentTimeMillis());
                count++;
            }
        }
        saveBookmarks();

        // Update reading states
        Map<String, ReaderState> updated = new HashMap<>();
        for (Map.Entry<String, ReaderState> entry : readingStates.entrySet()) {
            String path = entry.getKey();
            ReaderState state = entry.getValue();
            if (path.startsWith(oldPrefix)) {
                String newPath = path.replace(oldPrefix, newPrefix);
                state.setFilePath(newPath);
                updated.put(newPath, state);
                count++;
            } else {
                updated.put(path, state);
            }
        }
        readingStates = updated;
        saveReadingStates();

        return count;
    }

    // ========== Private I/O ==========

    private void loadBookmarks() {
        bookmarks = new ArrayList<>();
        String json = readFile(BOOKMARKS_FILE);
        if (json == null) return;

        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("bookmarks");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    bookmarks.add(Bookmark.fromJson(arr.getJSONObject(i)));
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load bookmarks", e);
        }
    }

    private void saveBookmarks() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", FORMAT_VERSION);
            JSONArray arr = new JSONArray();
            for (Bookmark b : bookmarks) {
                arr.put(b.toJson());
            }
            root.put("bookmarks", arr);
            writeFile(BOOKMARKS_FILE, root.toString(2));
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save bookmarks", e);
        }
    }

    private void loadReadingStates() {
        readingStates = new HashMap<>();
        String json = readFile(STATES_FILE);
        if (json == null) return;

        try {
            JSONObject root = new JSONObject(json);
            JSONObject statesObj = root.optJSONObject("states");
            if (statesObj != null) {
                Iterator<String> keys = statesObj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    readingStates.put(key, ReaderState.fromJson(statesObj.getJSONObject(key)));
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load reading states", e);
        }
    }

    private void saveReadingStates() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", FORMAT_VERSION);
            JSONObject statesObj = new JSONObject();
            for (Map.Entry<String, ReaderState> entry : readingStates.entrySet()) {
                statesObj.put(entry.getKey(), entry.getValue().toJson());
            }
            root.put("states", statesObj);
            writeFile(STATES_FILE, root.toString(2));
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save reading states", e);
        }
    }

    private String readFile(String fileName) {
        File file = new File(context.getFilesDir(), fileName);
        if (!file.exists()) return null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read " + fileName, e);
            return null;
        }
    }

    private void writeFile(String fileName, String content) {
        File file = new File(context.getFilesDir(), fileName);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        } catch (Exception e) {
            Log.e(TAG, "Failed to write " + fileName, e);
        }
    }
}
