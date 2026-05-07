package com.simpletext.reader.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Represents a single bookmark in a text file.
 *
 * Bookmark JSON format (human-readable, easy to edit):
 * {
 *   "id": "uuid-string",
 *   "filePath": "/storage/emulated/0/Books/novel.txt",
 *   "fileName": "novel.txt",
 *   "charPosition": 12345,
 *   "lineNumber": 230,
 *   "excerpt": "...surrounding text for context...",
 *   "label": "optional user label",
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
    private String excerpt;    // text excerpt for quick reference
    private String label;      // optional user-defined label
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
        obj.put("excerpt", excerpt != null ? excerpt : "");
        obj.put("label", label != null ? label : "");
        obj.put("createdAt", createdAt);
        obj.put("updatedAt", updatedAt);
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
        b.excerpt = obj.optString("excerpt", "");
        b.label = obj.optString("label", "");
        b.createdAt = obj.optLong("createdAt", System.currentTimeMillis());
        b.updatedAt = obj.optLong("updatedAt", b.createdAt);
        return b;
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

    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

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
