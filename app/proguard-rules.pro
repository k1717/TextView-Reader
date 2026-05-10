# Keep app model classes and members used by JSON import/export.
-keep class com.simpletext.reader.model.** { *; }
-keepclassmembers class com.simpletext.reader.model.** {
    public *;
}
