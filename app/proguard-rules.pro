# Keep Bookmark model for JSON serialization
-keep class com.simpletext.reader.model.** { *; }

# Keep ReaderState for JSON serialization
-keepclassmembers class com.simpletext.reader.model.** {
    public *;
}
