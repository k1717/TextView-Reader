# Keep Bookmark model for JSON serialization
-keep class com.textview.reader.model.** { *; }

# Keep ReaderState for JSON serialization
-keepclassmembers class com.textview.reader.model.** {
    public *;
}
