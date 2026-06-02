# Keep Bookmark/ReaderState models for JSON serialization.
-keep class com.textview.reader.model.** { *; }
-keepclassmembers class com.textview.reader.model.** {
    public *;
}

# JUniversalChardet is invoked reflectively from FileUtils.
-keep class org.mozilla.universalchardet.** { *; }
-dontwarn org.mozilla.universalchardet.**

# Keep WebView bridge methods exposed through @JavascriptInterface.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Strip low-value release logs while leaving warnings/errors available.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep stack trace line numbers but anonymize source-file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Optional RAR5 decoder is loaded reflectively by Rar5LibraryFallback when the local jar is present.
-keep class be.stef.rar5.** { *; }
-dontwarn be.stef.rar5.**
