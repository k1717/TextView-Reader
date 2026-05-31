package com.textview.reader.view;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/** Lightweight pan/zoom/fling image surface for ImageReaderActivity. */
public class ZoomImageView extends AppCompatImageView {
    public interface Callbacks {
        void onSingleTap();
        void onSwipeLeft();
        void onSwipeRight();
    }

    private final Matrix matrix = new Matrix();
    private final RectF imageRect = new RectF();
    private final float[] values = new float[9];
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private final OverScroller scroller;
    private final int touchSlop;
    private float minScale = 1f;
    private float maxScale = 5f;
    private float lastX;
    private float lastY;
    private int lastScrollerX;
    private int lastScrollerY;
    private boolean dragging;
    private boolean panGestureStarted;
    private int imageWidth;
    private int imageHeight;
    private Callbacks callbacks;

    public ZoomImageView(@NonNull android.content.Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);
        setAdjustViewBounds(false);
        scroller = new OverScroller(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                stopImageFling();
                disallowParentIntercept(true);
                return true;
            }

            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                float current = getCurrentScale();
                if (current <= 0f) return false;
                float requested = detector.getScaleFactor();
                float target = current * requested;
                if (target < minScale) requested = minScale / current;
                if (target > maxScale) requested = maxScale / current;
                matrix.postScale(requested, requested, detector.getFocusX(), detector.getFocusY());
                applyBounds();
                return true;
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                applyBounds();
                if (!isZoomed()) disallowParentIntercept(false);
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (callbacks != null) callbacks.onSingleTap();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                stopImageFling();
                toggleZoom(e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (isZoomed()) {
                    return startImageFling(velocityX, velocityY);
                }
                if (e1 == null || callbacks == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                float threshold = Math.max(72f, getResources().getDisplayMetrics().density * 56f);
                if (Math.abs(dx) < threshold || Math.abs(dx) < Math.abs(dy) * 1.25f) return false;
                if (dx < 0) callbacks.onSwipeLeft(); else callbacks.onSwipeRight();
                return true;
            }
        });
    }

    public void setCallbacks(@Nullable Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void setImageBitmapReady(@NonNull Bitmap nextBitmap) {
        stopImageFling();
        imageWidth = nextBitmap.getWidth();
        imageHeight = nextBitmap.getHeight();
        setImageBitmap(nextBitmap);
        post(this::configureBaseMatrix);
    }

    public void setImageDrawableReady(@NonNull Drawable nextDrawable) {
        stopImageFling();
        imageWidth = Math.max(1, nextDrawable.getIntrinsicWidth());
        imageHeight = Math.max(1, nextDrawable.getIntrinsicHeight());
        setImageDrawable(nextDrawable);
        if (nextDrawable instanceof AnimatedImageDrawable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ((AnimatedImageDrawable) nextDrawable).start();
        }
        post(this::configureBaseMatrix);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        configureBaseMatrix();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopImageFling();
                lastX = event.getX();
                lastY = event.getY();
                dragging = false;
                panGestureStarted = false;
                disallowParentIntercept(isZoomed());
                return true;
            case MotionEvent.ACTION_MOVE:
                handleMove(event);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                stopImageFling();
                dragging = false;
                panGestureStarted = false;
                disallowParentIntercept(true);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                updateLastTouchFromRemainingPointer(event);
                dragging = false;
                panGestureStarted = false;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                panGestureStarted = false;
                if (!isZoomed()) disallowParentIntercept(false);
                return true;
            default:
                return true;
        }
    }

    @Override
    public void computeScroll() {
        if (!scroller.computeScrollOffset()) return;
        int currentX = scroller.getCurrX();
        int currentY = scroller.getCurrY();
        matrix.postTranslate(lastScrollerX - currentX, lastScrollerY - currentY);
        lastScrollerX = currentX;
        lastScrollerY = currentY;
        applyBounds();
        postInvalidateOnAnimation();
    }

    public void configureBaseMatrix() {
        stopImageFling();
        if (imageWidth <= 0 || imageHeight <= 0 || getWidth() <= 0 || getHeight() <= 0) return;
        int contentW = getContentWidth();
        int contentH = getContentHeight();
        if (contentW <= 0 || contentH <= 0) return;
        float bw = imageWidth;
        float bh = imageHeight;
        if (bw <= 0 || bh <= 0) return;
        matrix.reset();
        minScale = Math.min(contentW / bw, contentH / bh);
        if (minScale <= 0f) minScale = 1f;
        maxScale = Math.max(minScale * 5f, 5f);
        float dx = getPaddingLeft() + (contentW - bw * minScale) * 0.5f;
        float dy = getPaddingTop() + (contentH - bh * minScale) * 0.5f;
        matrix.postScale(minScale, minScale);
        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
    }

    private void handleMove(@NonNull MotionEvent event) {
        if (scaleDetector.isInProgress() || event.getPointerCount() != 1) return;
        float x = event.getX();
        float y = event.getY();
        float dx = x - lastX;
        float dy = y - lastY;
        if (!panGestureStarted) {
            float distanceSquared = dx * dx + dy * dy;
            if (distanceSquared < touchSlop * touchSlop) return;
            panGestureStarted = true;
            dragging = isZoomed();
        }
        if (dragging && isZoomed()) {
            disallowParentIntercept(true);
            matrix.postTranslate(dx, dy);
            applyBounds();
        }
        lastX = x;
        lastY = y;
    }

    private void updateLastTouchFromRemainingPointer(@NonNull MotionEvent event) {
        int pointerIndex = event.getActionIndex() == 0 ? 1 : 0;
        if (pointerIndex >= event.getPointerCount()) pointerIndex = 0;
        lastX = event.getX(pointerIndex);
        lastY = event.getY(pointerIndex);
    }

    private void toggleZoom(float focusX, float focusY) {
        if (imageWidth <= 0 || imageHeight <= 0) return;
        float current = getCurrentScale();
        if (current <= 0f) return;
        float target = current > minScale * 1.2f ? minScale : Math.min(maxScale, minScale * 2.5f);
        float factor = target / current;
        matrix.postScale(factor, factor, focusX, focusY);
        applyBounds();
    }

    private boolean startImageFling(float velocityX, float velocityY) {
        if (imageWidth <= 0 || imageHeight <= 0 || !isZoomed()) return false;
        mapImageRect();
        int contentW = getContentWidth();
        int contentH = getContentHeight();
        if (contentW <= 0 || contentH <= 0) return false;

        float leftBound = getPaddingLeft();
        float topBound = getPaddingTop();
        float rightBound = leftBound + contentW;
        float bottomBound = topBound + contentH;

        boolean canPanX = imageRect.width() > contentW + 1f;
        boolean canPanY = imageRect.height() > contentH + 1f;
        if (!canPanX && !canPanY) return false;

        int startX = Math.round(-imageRect.left);
        int startY = Math.round(-imageRect.top);
        int minX = canPanX ? Math.round(-leftBound) : startX;
        int maxX = canPanX ? Math.round(-(rightBound - imageRect.width())) : startX;
        int minY = canPanY ? Math.round(-topBound) : startY;
        int maxY = canPanY ? Math.round(-(bottomBound - imageRect.height())) : startY;
        startX = clamp(startX, minX, maxX);
        startY = clamp(startY, minY, maxY);

        lastScrollerX = startX;
        lastScrollerY = startY;
        scroller.fling(
                startX,
                startY,
                canPanX ? Math.round(-velocityX) : 0,
                canPanY ? Math.round(-velocityY) : 0,
                minX,
                maxX,
                minY,
                maxY);
        postInvalidateOnAnimation();
        return true;
    }

    private float getCurrentScale() {
        matrix.getValues(values);
        float scale = values[Matrix.MSCALE_X];
        return scale == 0f ? 1f : scale;
    }

    private boolean isZoomed() {
        return getCurrentScale() > minScale * 1.03f;
    }

    private void applyBounds() {
        if (imageWidth <= 0 || imageHeight <= 0) return;
        int contentW = getContentWidth();
        int contentH = getContentHeight();
        if (contentW <= 0 || contentH <= 0) return;
        mapImageRect();

        float leftBound = getPaddingLeft();
        float topBound = getPaddingTop();
        float rightBound = leftBound + contentW;
        float bottomBound = topBound + contentH;
        float dx = 0f;
        float dy = 0f;

        if (imageRect.width() <= contentW) {
            dx = leftBound + (contentW - imageRect.width()) * 0.5f - imageRect.left;
        } else if (imageRect.left > leftBound) {
            dx = leftBound - imageRect.left;
        } else if (imageRect.right < rightBound) {
            dx = rightBound - imageRect.right;
        }

        if (imageRect.height() <= contentH) {
            dy = topBound + (contentH - imageRect.height()) * 0.5f - imageRect.top;
        } else if (imageRect.top > topBound) {
            dy = topBound - imageRect.top;
        } else if (imageRect.bottom < bottomBound) {
            dy = bottomBound - imageRect.bottom;
        }

        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
    }

    private void mapImageRect() {
        imageRect.set(0, 0, imageWidth, imageHeight);
        matrix.mapRect(imageRect);
    }

    private int getContentWidth() {
        return getWidth() - getPaddingLeft() - getPaddingRight();
    }

    private int getContentHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    private void stopImageFling() {
        if (!scroller.isFinished()) scroller.forceFinished(true);
    }

    private void disallowParentIntercept(boolean disallow) {
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(disallow);
    }

    private int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
