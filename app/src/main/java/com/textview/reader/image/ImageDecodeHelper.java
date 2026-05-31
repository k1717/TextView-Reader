package com.textview.reader.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.InputStream;
import java.util.Locale;

/** Heavy image decoding code kept outside ImageReaderActivity. */
public final class ImageDecodeHelper {
    private ImageDecodeHelper() {}

    @Nullable
    public static LoadedImage decode(@NonNull Context context,
                                     @Nullable String filePath,
                                     @Nullable String fileUri,
                                     @Nullable String displayName) throws Exception {
        if (isAnimatedImageCandidateName(displayName) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Drawable drawable = decodeAnimatedDrawable(context, filePath, fileUri);
            if (drawable != null) return LoadedImage.forDrawable(drawable);
        }
        Bitmap bitmap = decodeBitmap(context, filePath, fileUri);
        return bitmap == null ? null : LoadedImage.forBitmap(bitmap);
    }

    @Nullable
    private static Drawable decodeAnimatedDrawable(@NonNull Context context,
                                                   @Nullable String filePath,
                                                   @Nullable String fileUri) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null;
        ImageDecoder.Source source;
        if (filePath != null && filePath.trim().length() > 0) {
            source = ImageDecoder.createSource(new File(filePath));
        } else if (fileUri != null && fileUri.trim().length() > 0) {
            source = ImageDecoder.createSource(context.getContentResolver(), Uri.parse(fileUri));
        } else {
            return null;
        }
        int reqW = Math.max(context.getResources().getDisplayMetrics().widthPixels * 2, 1);
        int reqH = Math.max(context.getResources().getDisplayMetrics().heightPixels * 2, 1);
        Drawable drawable = ImageDecoder.decodeDrawable(source, (decoder, info, imageSource) -> {
            int sample = calculateInSampleSize(info.getSize().getWidth(), info.getSize().getHeight(), reqW, reqH);
            if (sample > 1) decoder.setTargetSampleSize(sample);
        });
        if (drawable instanceof AnimatedImageDrawable) {
            ((AnimatedImageDrawable) drawable).setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
        }
        return drawable;
    }

    @Nullable
    private static Bitmap decodeBitmap(@NonNull Context context,
                                       @Nullable String filePath,
                                       @Nullable String fileUri) throws Exception {
        int reqW = Math.max(context.getResources().getDisplayMetrics().widthPixels * 2, 1);
        int reqH = Math.max(context.getResources().getDisplayMetrics().heightPixels * 2, 1);
        if (filePath != null && filePath.trim().length() > 0) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = calculateInSampleSize(bounds, reqW, reqH);
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(filePath, opts);
        }
        if (fileUri == null || fileUri.trim().isEmpty()) return null;
        Uri uri = Uri.parse(fileUri);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, bounds);
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = calculateInSampleSize(bounds, reqW, reqH);
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(is, null, opts);
        }
    }

    private static boolean isAnimatedImageCandidateName(@Nullable String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".gif") || lower.endsWith(".webp");
    }

    private static int calculateInSampleSize(@NonNull BitmapFactory.Options options, int reqWidth, int reqHeight) {
        return calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight);
    }

    private static int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
        if (width <= 0 || height <= 0) return 1;
        int inSampleSize = 1;
        while ((height / inSampleSize) > reqHeight || (width / inSampleSize) > reqWidth) inSampleSize *= 2;
        return Math.max(1, inSampleSize);
    }
}
