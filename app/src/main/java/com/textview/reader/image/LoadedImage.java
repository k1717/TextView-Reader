package com.textview.reader.image;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Result holder for ImageReaderActivity decoding paths. */
public final class LoadedImage {
    public final Bitmap bitmap;
    public final Drawable drawable;

    private LoadedImage(@Nullable Bitmap bitmap, @Nullable Drawable drawable) {
        this.bitmap = bitmap;
        this.drawable = drawable;
    }

    @NonNull
    public static LoadedImage forBitmap(@NonNull Bitmap bitmap) {
        return new LoadedImage(bitmap, null);
    }

    @NonNull
    public static LoadedImage forDrawable(@NonNull Drawable drawable) {
        return new LoadedImage(null, drawable);
    }
}
