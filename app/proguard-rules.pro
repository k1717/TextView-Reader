# Keep Bookmark model for JSON serialization
-keep class com.simpletext.reader.model.** { *; }

# Keep ReaderState for JSON serialization
-keepclassmembers class com.simpletext.reader.model.** {
    public *;
}

# Keep PdfBox-Android classes used for PDF text extraction
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
