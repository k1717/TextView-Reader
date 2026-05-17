package com.textview.reader.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Represents a single bookmark in a text file.
 *
 * Bookmark JSON format (human-readable, easy to edit):
 * {
 *   "id": "uuid-string",
 *   "filePath": "<device-local-book-path>",
 *   "fileName": "novel.txt",
 *   "charPosition": 12345,
 *   "lineNumber": 230,
 *   "pcEditPosition": 230,
 *   "pcEditOriginalPosition": 230,
 *   "pcEditPositionType": "TXT logical line (1-based)",
 *   "excerpt": "...surrounding text for context...",
 *   "anchorTextBefore": "text immediately before saved spot",
 *   "anchorTextAfter": "text immediately after saved spot",
 *   "fileSizeBytes": 1234567,
 *   "quickFingerprint": "fast hash of first/middle/last file samples",
 *   "fileIdentity": { "displayName": "novel.txt", "sizeBytes": 1234567, "quickFingerprint": "..." },
 *   "label": "optional user label",
 *   "memo": "optional user label",
 *   "createdAt": 1700000000000,
 *   "updatedAt": 1700000000000
 * }
 */
public class Bookmark {
    private String id;
    private String filePath;
    private String fileName;
    private int charPosition;  // character offset in file
    private int endPosition;   // end offset of excerpt/context, TekView-style start/end range
    private int lineNumber;    // approximate line number
    private int pageNumber;    // cached displayed page number for fast large-file restore
    private int totalPages;    // cached displayed total page count when bookmark was saved
    private String excerpt;    // text excerpt for quick reference
    private String label;      // optional user-defined label
    private String anchorTextBefore; // text immediately before charPosition for robust TXT restore
    private String anchorTextAfter;  // text immediately after charPosition for robust TXT restore
    private long fileSizeBytes;      // portable identity: local path can change across devices/folders
    private String quickFingerprint; // portable identity: cheap first/middle/last sample hash
    private int pendingPcEditLine;      // delayed TXT PC edit base line when the file is not locally available during import
    private int pendingPcMoveByLines;   // delayed TXT relative movement for PC edit
    private String pendingPcFindText;   // delayed TXT search target for PC edit
    private int pendingPcFindOccurrence;
    private boolean pendingPcFindCaseSensitive;
    private long createdAt;
    private long updatedAt;

    public Bookmark() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public Bookmark(String filePath, String fileName, int charPosition, int lineNumber, String excerpt) {
        this();
        this.filePath = filePath;
        this.fileName = fileName;
        this.charPosition = charPosition;
        this.endPosition = charPosition + (excerpt != null ? excerpt.length() : 0);
        this.lineNumber = lineNumber;
        this.excerpt = excerpt;
    }

    // --- JSON serialization ---

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("filePath", filePath);
        obj.put("fileName", fileName);
        obj.put("charPosition", charPosition);
        obj.put("endPosition", endPosition);
        obj.put("lineNumber", lineNumber);
        obj.put("pageNumber", pageNumber);
        obj.put("totalPages", totalPages);

        // PC-edit helper fields.  They intentionally duplicate the internal
        // position/memo in a friendlier form for exported JSON files:
        // - Edit pcEditPosition only, then import the JSON back.
        // - Leave pcEditOriginalPosition unchanged so the importer can detect
        //   that the human-readable position was intentionally changed.
        // - Edit memo instead of label if using a spreadsheet/text editor.
        int pcEditPosition = getPcEditPosition();
        obj.put("pcEditPosition", pcEditPosition);
        obj.put("pcEditOriginalPosition", pcEditPosition);
        obj.put("pcEditPositionType", getPcEditPositionType());

        obj.put("excerpt", excerpt != null ? excerpt : "");
        obj.put("anchorTextBefore", anchorTextBefore != null ? anchorTextBefore : "");
        obj.put("anchorTextAfter", anchorTextAfter != null ? anchorTextAfter : "");
        obj.put("fileSizeBytes", fileSizeBytes);
        obj.put("quickFingerprint", quickFingerprint != null ? quickFingerprint : "");

        // Portable identity is duplicated in a grouped object so backup files are
        // easier to understand on PC.  The app still treats filePath as a local,
        // device-specific binding and uses this identity only when rebinding is needed.
        JSONObject identity = new JSONObject();
        identity.put("displayName", fileName != null ? fileName : "");
        identity.put("type", getFileTypeLabel());
        identity.put("sizeBytes", fileSizeBytes);
        identity.put("quickFingerprint", quickFingerprint != null ? quickFingerprint : "");
        obj.put("fileIdentity", identity);
        obj.put("localBindingPath", filePath != null ? filePath : "");

        if (hasPendingPcTextEdit()) {
            JSONObject pending = new JSONObject();
            pending.put("line", pendingPcEditLine);
            pending.put("moveByLines", pendingPcMoveByLines);
            pending.put("findText", pendingPcFindText != null ? pendingPcFindText : "");
            pending.put("findOccurrence", pendingPcFindOccurrence > 0 ? pendingPcFindOccurrence : 1);
            pending.put("findTextCaseSensitive", pendingPcFindCaseSensitive);
            obj.put("pendingPcTextEdit", pending);
        }

        obj.put("label", label != null ? label : "");
        obj.put("memo", label != null ? label : "");
        obj.put("createdAt", createdAt);
        obj.put("updatedAt", updatedAt);
        return obj;
    }

    /**
     * Compact, beginner-facing bookmark edit row for exported backups.
     *
     * The full bookmarks[] array remains the authoritative machine backup.
     * This row is intentionally duplicated so users editing on a PC can ignore
     * internal fields such as charPosition, anchors, fingerprints, and paths.
     */
    public JSONObject toBeginnerEditJson() throws JSONException {
        JSONObject obj = new JSONObject();
        int position = getPcEditPosition();
        String type = getFileTypeLabel();

        obj.put("bookmarkId", id != null ? id : "");
        obj.put("fileName", fileName != null ? fileName : "");
        obj.put("fileType", type);
        obj.put("help_EN", getBeginnerHowToEditEn(type));
        obj.put("help_KO", getBeginnerHowToEditKo(type));

        JSONObject current = new JSONObject();
        current.put("lineOrPage", position);
        current.put("originalLineOrPage", position);
        current.put("preview", excerpt != null ? excerpt : "");
        current.put("memo", label != null ? label : "");
        if ("TXT".equals(type)) {
            current.put("line", position);
            current.put("meaning", "TXT logical line number, starting from 1");
        } else if ("EPUB".equals(type)) {
            current.put("pageOrSection", position);
            current.put("meaning", "EPUB app page/section number, starting from 1");
        } else {
            current.put("page", position);
            current.put("meaning", type + " page number, starting from 1");
        }
        obj.put("current", current);

        JSONObject edit = new JSONObject();
        edit.put("memo", label != null ? label : "");
        edit.put("lineOrPage", position);
        edit.put("moveBy", 0);
        if ("TXT".equals(type)) {
            edit.put("setLine", position);
            edit.put("moveByLines", 0);
            edit.put("findText", "");
            edit.put("findOccurrence", 1);
            edit.put("findTextCaseSensitive", false);
        } else if ("EPUB".equals(type)) {
            edit.put("setPageOrSection", position);
            edit.put("moveByPages", 0);
        } else {
            edit.put("setPage", position);
            edit.put("moveByPages", 0);
        }
        obj.put("edit", edit);
        obj.put("editableFields", getBeginnerEditableFields(type));
        obj.put("tip_EN", "Change only the edit object unless you are sure. current is only there to help you recognize this bookmark.");
        obj.put("tip_KO", "확실한 경우가 아니라면 edit 안의 값만 수정하세요. current는 이 북마크를 알아보기 위한 참고 정보입니다.");
        return obj;
    }

    /**
     * Developer-facing edit row for exported backups.
     *
     * This is still safer than editing the machine bookmarks[] array directly:
     * the importer can detect line/page changes, search-based TXT corrections,
     * and optional direct charPosition changes from this compact row.
     */
    public JSONObject toDeveloperEditJson() throws JSONException {
        JSONObject obj = new JSONObject();
        int position = getPcEditPosition();
        String type = getFileTypeLabel();

        obj.put("bookmarkId", id != null ? id : "");
        obj.put("fileName", fileName != null ? fileName : "");
        obj.put("fileType", type);
        obj.put("help_EN", "For developer repair, edit edit.lineOrPage first. Edit edit.charPosition only when you intentionally want raw internal positioning.");
        obj.put("help_KO", "개발자 복구용입니다. 먼저 edit.lineOrPage 수정을 권장합니다. raw 내부 위치를 직접 지정해야 할 때만 edit.charPosition을 수정하세요.");

        JSONObject current = new JSONObject();
        current.put("lineOrPage", position);
        current.put("originalLineOrPage", position);
        current.put("charPosition", charPosition);
        current.put("endPosition", endPosition);
        current.put("lineNumber", lineNumber);
        current.put("pageNumber", pageNumber);
        current.put("totalPages", totalPages);
        current.put("preview", excerpt != null ? excerpt : "");
        current.put("memo", label != null ? label : "");
        if ("TXT".equals(type)) {
            current.put("line", position);
        } else if ("EPUB".equals(type)) {
            current.put("pageOrSection", position);
        } else {
            current.put("page", position);
        }
        obj.put("current", current);

        JSONObject edit = new JSONObject();
        edit.put("memo", label != null ? label : "");
        edit.put("lineOrPage", position);
        edit.put("moveBy", 0);
        edit.put("charPosition", charPosition);
        edit.put("originalCharPosition", charPosition);
        if ("TXT".equals(type)) {
            edit.put("setLine", position);
            edit.put("moveByLines", 0);
            edit.put("findText", "");
            edit.put("findOccurrence", 1);
            edit.put("findTextCaseSensitive", false);
        } else if ("EPUB".equals(type)) {
            edit.put("setPageOrSection", position);
            edit.put("moveByPages", 0);
        } else {
            edit.put("setPage", position);
            edit.put("moveByPages", 0);
        }
        obj.put("edit", edit);

        JSONObject identity = new JSONObject();
        identity.put("localBindingPath", filePath != null ? filePath : "");
        identity.put("fileSizeBytes", fileSizeBytes);
        identity.put("quickFingerprint", quickFingerprint != null ? quickFingerprint : "");
        obj.put("fileIdentity", identity);

        JSONObject anchors = new JSONObject();
        anchors.put("anchorTextBefore", anchorTextBefore != null ? anchorTextBefore : "");
        anchors.put("anchorTextAfter", anchorTextAfter != null ? anchorTextAfter : "");
        obj.put("anchors", anchors);

        JSONObject internal = new JSONObject();
        internal.put("id", id != null ? id : "");
        internal.put("filePath", filePath != null ? filePath : "");
        internal.put("lineNumber", lineNumber);
        internal.put("pageNumber", pageNumber);
        internal.put("totalPages", totalPages);
        internal.put("charPosition", charPosition);
        internal.put("endPosition", endPosition);
        internal.put("createdAt", createdAt);
        internal.put("updatedAt", updatedAt);
        obj.put("internal", internal);
        obj.put("tip_EN", "Developer rows are safer than editing bookmarks[] directly, but beginner rows are still recommended for normal edits.");
        obj.put("tip_KO", "developer 행은 bookmarks[]를 직접 수정하는 것보다 안전하지만, 일반 수정에는 beginner 행을 권장합니다.");
        return obj;
    }

    public static Bookmark fromJson(JSONObject obj) throws JSONException {
        Bookmark b = new Bookmark();
        b.id = obj.optString("id", UUID.randomUUID().toString());
        b.filePath = obj.getString("filePath");
        b.fileName = obj.optString("fileName", "");
        b.charPosition = obj.optInt("charPosition", 0);
        b.endPosition = obj.optInt("endPosition", b.charPosition);
        b.lineNumber = obj.optInt("lineNumber", 0);
        b.pageNumber = obj.optInt("pageNumber", 0);
        b.totalPages = obj.optInt("totalPages", 0);
        b.excerpt = obj.optString("excerpt", "");
        b.anchorTextBefore = obj.optString("anchorTextBefore", "");
        b.anchorTextAfter = obj.optString("anchorTextAfter", "");
        b.fileSizeBytes = obj.optLong("fileSizeBytes", 0L);
        b.quickFingerprint = obj.optString("quickFingerprint", "");
        JSONObject identity = obj.optJSONObject("fileIdentity");
        if (identity != null) {
            if (b.fileSizeBytes <= 0L) b.fileSizeBytes = identity.optLong("sizeBytes", 0L);
            if (b.quickFingerprint == null || b.quickFingerprint.isEmpty()) {
                b.quickFingerprint = identity.optString("quickFingerprint", "");
            }
            if ((b.fileName == null || b.fileName.isEmpty()) && identity.has("displayName")) {
                b.fileName = identity.optString("displayName", "");
            }
        }
        JSONObject pending = obj.optJSONObject("pendingPcTextEdit");
        if (pending != null) {
            b.pendingPcEditLine = pending.optInt("line", 0);
            b.pendingPcMoveByLines = pending.optInt("moveByLines", 0);
            b.pendingPcFindText = pending.optString("findText", "");
            b.pendingPcFindOccurrence = pending.optInt("findOccurrence", 1);
            b.pendingPcFindCaseSensitive = pending.optBoolean("findTextCaseSensitive", false);
        }
        b.label = obj.has("memo") ? obj.optString("memo", "") : obj.optString("label", "");
        b.createdAt = obj.optLong("createdAt", System.currentTimeMillis());
        b.updatedAt = obj.optLong("updatedAt", b.createdAt);
        return b;
    }


    private String getFileTypeLabel() {
        String name = fileName != null && !fileName.isEmpty() ? fileName : filePath;
        if (name == null) return "TXT";
        String lower = name.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".epub")) return "EPUB";
        if (lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".rtf")) return "WORD";
        return "TXT";
    }

    private int getPcEditPosition() {
        if (lineNumber > 0) return lineNumber;
        return Math.max(1, charPosition + 1);
    }

    public int getPcEditPositionForManager() {
        return getPcEditPosition();
    }

    private String getPcEditPositionType() {
        String name = fileName != null && !fileName.isEmpty() ? fileName : filePath;
        if (name == null) return "TXT logical line (1-based)";
        String lower = name.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF page (1-based)";
        if (lower.endsWith(".epub")) return "EPUB section/page (1-based)";
        if (lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".rtf")) {
            return "Word page (1-based)";
        }
        return "TXT logical line (1-based)";
    }

    private String getBeginnerPositionMeaning(String type) {
        if ("PDF".equals(type)) return "PDF page number, starting from 1";
        if ("EPUB".equals(type)) return "EPUB page/section number, starting from 1";
        if ("WORD".equals(type)) return "Word page number, starting from 1";
        return "TXT logical line number, starting from 1";
    }

    private String getBeginnerEditableFields(String type) {
        if ("TXT".equals(type)) {
            return "memo, setLine, moveByLines, findText, findOccurrence, findTextCaseSensitive";
        }
        if ("EPUB".equals(type)) {
            return "memo, setPageOrSection, moveByPages";
        }
        return "memo, setPage, moveByPages";
    }


    private String getBeginnerHowToEditEn(String type) {
        if ("TXT".equals(type)) {
            return "TXT: change setLine if you know the exact line. Use moveByLines for a small correction, or findText when you know a sentence instead of the line number. memo is free text.";
        }
        if ("EPUB".equals(type)) {
            return "EPUB: change setPageOrSection if you know the app page/section. Use moveByPages for a small correction. memo is free text.";
        }
        return "PDF/Word: change setPage if you know the exact page. Use moveByPages for a small correction. memo is free text.";
    }

    private String getBeginnerHowToEditKo(String type) {
        if ("TXT".equals(type)) {
            return "TXT: 정확한 줄을 알면 setLine을 바꾸세요. 조금 보정하려면 moveByLines를 쓰고, 줄 번호 대신 문장을 알고 있으면 findText를 쓰면 됩니다. memo는 자유 메모입니다.";
        }
        if ("EPUB".equals(type)) {
            return "EPUB: 정확한 앱 페이지/섹션을 알면 setPageOrSection을 바꾸세요. 조금 보정하려면 moveByPages를 쓰면 됩니다. memo는 자유 메모입니다.";
        }
        return "PDF/Word: 정확한 페이지를 알면 setPage를 바꾸세요. 조금 보정하려면 moveByPages를 쓰면 됩니다. memo는 자유 메모입니다.";
    }

    private String getBeginnerSampleEditEn(String type, int position) {
        int base = Math.max(1, position);
        if ("TXT".equals(type)) {
            return "Examples: setLine=" + (base + 10) + ", moveByLines=0 -> move to line " + (base + 10) + "; keep setLine=" + base + " and set moveByLines=2 -> move to line " + (base + 2) + "; set findText=\"target phrase\", findOccurrence=1 -> move to that phrase.";
        }
        if ("EPUB".equals(type)) {
            return "Examples: setPageOrSection=" + (base + 5) + ", moveByPages=0 -> move to app page/section " + (base + 5) + "; keep setPageOrSection=" + base + " and set moveByPages=-1 -> move one page/section earlier.";
        }
        return "Examples: setPage=" + (base + 5) + ", moveByPages=0 -> move to page " + (base + 5) + "; keep setPage=" + base + " and set moveByPages=-1 -> move to page " + Math.max(1, base - 1) + ".";
    }

    private String getBeginnerSampleEditKo(String type, int position) {
        int base = Math.max(1, position);
        if ("TXT".equals(type)) {
            return "예시: setLine=" + (base + 10) + ", moveByLines=0 -> " + (base + 10) + "번째 줄로 이동; setLine=" + base + " 그대로 두고 moveByLines=2 -> " + (base + 2) + "번째 줄로 이동; findText=\"찾을 문장\", findOccurrence=1 -> 그 문장 위치로 이동.";
        }
        if ("EPUB".equals(type)) {
            return "예시: setPageOrSection=" + (base + 5) + ", moveByPages=0 -> 앱 기준 " + (base + 5) + "번째 페이지/섹션으로 이동; setPageOrSection=" + base + " 그대로 두고 moveByPages=-1 -> 한 페이지/섹션 앞쪽으로 이동.";
        }
        return "예시: setPage=" + (base + 5) + ", moveByPages=0 -> " + (base + 5) + "페이지로 이동; setPage=" + base + " 그대로 두고 moveByPages=-1 -> " + Math.max(1, base - 1) + "페이지로 이동.";
    }

    public boolean hasPendingPcTextEdit() {
        return pendingPcEditLine > 0
                || pendingPcMoveByLines != 0
                || (pendingPcFindText != null && !pendingPcFindText.isEmpty());
    }

    public void clearPendingPcTextEdit() {
        pendingPcEditLine = 0;
        pendingPcMoveByLines = 0;
        pendingPcFindText = "";
        pendingPcFindOccurrence = 1;
        pendingPcFindCaseSensitive = false;
    }

    // --- Getters / Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public int getCharPosition() { return charPosition; }
    public void setCharPosition(int charPosition) { this.charPosition = charPosition; }

    public int getEndPosition() { return endPosition; }
    public void setEndPosition(int endPosition) { this.endPosition = endPosition; }

    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getAnchorTextBefore() { return anchorTextBefore; }
    public void setAnchorTextBefore(String anchorTextBefore) { this.anchorTextBefore = anchorTextBefore; }

    public String getAnchorTextAfter() { return anchorTextAfter; }
    public void setAnchorTextAfter(String anchorTextAfter) { this.anchorTextAfter = anchorTextAfter; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getQuickFingerprint() { return quickFingerprint; }
    public void setQuickFingerprint(String quickFingerprint) { this.quickFingerprint = quickFingerprint; }


    public int getPendingPcEditLine() { return pendingPcEditLine; }
    public void setPendingPcEditLine(int pendingPcEditLine) { this.pendingPcEditLine = pendingPcEditLine; }

    public int getPendingPcMoveByLines() { return pendingPcMoveByLines; }
    public void setPendingPcMoveByLines(int pendingPcMoveByLines) { this.pendingPcMoveByLines = pendingPcMoveByLines; }

    public String getPendingPcFindText() { return pendingPcFindText; }
    public void setPendingPcFindText(String pendingPcFindText) { this.pendingPcFindText = pendingPcFindText; }

    public int getPendingPcFindOccurrence() { return pendingPcFindOccurrence; }
    public void setPendingPcFindOccurrence(int pendingPcFindOccurrence) { this.pendingPcFindOccurrence = pendingPcFindOccurrence; }

    public boolean isPendingPcFindCaseSensitive() { return pendingPcFindCaseSensitive; }
    public void setPendingPcFindCaseSensitive(boolean pendingPcFindCaseSensitive) { this.pendingPcFindCaseSensitive = pendingPcFindCaseSensitive; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Returns display text: label if set, otherwise excerpt
     */
    public String getDisplayText() {
        if (label != null && !label.isEmpty()) {
            return label;
        }
        if (excerpt != null && !excerpt.isEmpty()) {
            return excerpt;
        }
        return "Position " + charPosition;
    }
}
