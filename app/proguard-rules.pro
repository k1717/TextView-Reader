# Keep Bookmark model for JSON serialization
-keep class com.textview.reader.model.** { *; }

# Keep ReaderState for JSON serialization
-keepclassmembers class com.textview.reader.model.** {
    public *;
}

# JUniversalChardet is invoked reflectively from FileUtils.
-keep class org.mozilla.universalchardet.** { *; }
-dontwarn org.mozilla.universalchardet.**

