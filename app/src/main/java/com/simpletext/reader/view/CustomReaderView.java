package com.simpletext.reader.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import androidx.annotation.Nullable;

/**
 * TekView-style custom scroll reader.
 *
 * This intentionally avoids RecyclerView for the main TXT body. The whole rendered text
 * has one fixed StaticLayout height, so page count is stable:
 *
 *   pageStep = visible screen height - overlap
 *   totalPages = floor(maxScrollY / pageStep) + 1
 *   currentPage = floor(scrollY / pageStep) + 1
 *
 * The denominator cannot grow while dragging because it is not based on RecyclerView's
 * lazy/estimated scroll range.
 */
public class CustomReaderView extends View {

    public interface ReaderListener {
        void onSingleTap(float x, float y);
        void onReaderScrollChanged();
    }

    private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG | TextPaint.SUBPIXEL_TEXT_FLAG);
    private final OverScroller scroller;
    private final int touchSlop;
    private final int minFlingVelocity;
    private final int maxFlingVelocity;

    private VelocityTracker velocityTracker;
    private ReaderListener listener;

    private String text = "";
    private StaticLayout layout;
    private int textColor = Color.rgb(224, 224, 224);
    private int backgroundColor = Color.BLACK;
    private float fontSizeSp = 18f;
    private float lineSpacingMultiplier = 1.5f;
    private int marginHorizontalPx = 24;
    private int marginVerticalPx = 16;
    private int overlapLines = 0;
    private Typeface typeface = Typeface.DEFAULT;

    private int readerScrollY = 0;
    private int maxScrollY = 0;
    private float downX;
    private float downY;
    private float lastY;
    private boolean dragging;

    public CustomReaderView(Context context) {
        this(context, null);
    }

    public CustomReaderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        scroller = new OverScroller(context);
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minFlingVelocity = vc.getScaledMinimumFlingVelocity();
        maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        setFocusable(true);
        paint.setColor(textColor);
        paint.setTextSize(spToPx(fontSizeSp));
        paint.setTypeface(typeface);
    }

    public void setReaderListener(ReaderListener listener) {
        this.listener = listener;
    }

    public void setTextContent(String value) {
        text = value != null ? value : "";
        readerScrollY = 0;
        rebuildLayout();
        invalidate();
        notifyScrollChanged();
    }

    public String getTextContent() {
        return text;
    }

    public void setReaderStyle(float fontSizeSp,
                               float lineSpacingMultiplier,
                               int textColor,
                               int backgroundColor,
                               int marginHorizontalPx,
                               int marginVerticalPx,
                               Typeface typeface) {
        this.fontSizeSp = fontSizeSp;
        this.lineSpacingMultiplier = Math.max(0.8f, lineSpacingMultiplier);
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
        this.marginHorizontalPx = Math.max(0, marginHorizontalPx);
        this.marginVerticalPx = Math.max(0, marginVerticalPx);
        this.typeface = typeface != null ? typeface : Typeface.DEFAULT;

        paint.setColor(textColor);
        paint.setTextSize(spToPx(fontSizeSp));
        paint.setTypeface(this.typeface);
        rebuildLayout();
        invalidate();
        notifyScrollChanged();
    }

    public void setOverlapLines(int overlapLines) {
        this.overlapLines = Math.max(0, Math.min(8, overlapLines));
    }

    private void rebuildLayout() {
        int width = getWidth() - getPaddingLeft() - getPaddingRight() - marginHorizontalPx * 2;
        if (width <= 0) return;

        layout = StaticLayout.Builder
                .obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, lineSpacingMultiplier)
                .setIncludePad(true)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build();

        recalcMaxScroll();
    }

    private void recalcMaxScroll() {
        int contentHeight = getContentHeight();
        int viewport = getViewportHeight();
        maxScrollY = Math.max(0, contentHeight - viewport);
        if (readerScrollY > maxScrollY) readerScrollY = maxScrollY;
        if (readerScrollY < 0) readerScrollY = 0;
    }

    private int getContentHeight() {
        return (layout != null ? layout.getHeight() : 0) + marginVerticalPx * 2;
    }

    public int getViewportHeight() {
        return Math.max(1, getHeight() - getPaddingTop() - getPaddingBottom());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildLayout();
        notifyScrollChanged();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(backgroundColor);
        if (layout == null) return;

        canvas.save();
        canvas.clipRect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        canvas.translate(getPaddingLeft() + marginHorizontalPx,
                getPaddingTop() + marginVerticalPx - readerScrollY);
        layout.draw(canvas);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scroller.abortAnimation();
                downX = event.getX();
                downY = event.getY();
                lastY = downY;
                dragging = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                float y = event.getY();
                float dy = lastY - y;
                if (!dragging && Math.abs(y - downY) > touchSlop) dragging = true;
                if (dragging) {
                    scrollByPixels((int) dy);
                    lastY = y;
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!dragging && Math.abs(event.getX() - downX) < touchSlop && Math.abs(event.getY() - downY) < touchSlop) {
                    if (listener != null) listener.onSingleTap(event.getX(), event.getY());
                } else {
                    velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                    float velocityY = velocityTracker.getYVelocity();
                    if (Math.abs(velocityY) > minFlingVelocity) {
                        scroller.fling(0, readerScrollY, 0, (int) -velocityY, 0, 0, 0, maxScrollY);
                        postInvalidateOnAnimation();
                    }
                }
                recycleVelocityTracker();
                return true;

            case MotionEvent.ACTION_CANCEL:
                recycleVelocityTracker();
                return true;
        }
        return true;
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            setReaderScrollY(scroller.getCurrY());
            postInvalidateOnAnimation();
        }
    }

    private void scrollByPixels(int dy) {
        setReaderScrollY(readerScrollY + dy);
    }

    public void setReaderScrollY(int y) {
        int clamped = Math.max(0, Math.min(maxScrollY, y));
        if (clamped != readerScrollY) {
            readerScrollY = clamped;
            invalidate();
            notifyScrollChanged();
        }
    }

    public int getReaderScrollY() {
        return readerScrollY;
    }

    public int getMaxScrollY() {
        return maxScrollY;
    }

    private void notifyScrollChanged() {
        if (listener != null) listener.onReaderScrollChanged();
    }

    public int getPageStepPx() {
        int step = getViewportHeight() - getOverlapPx();
        return Math.max(1, step);
    }

    private int getOverlapPx() {
        if (overlapLines <= 0) return 0;
        return Math.max(0, Math.round(getLineHeightPx() * overlapLines));
    }

    private float getLineHeightPx() {
        return paint.getTextSize() * lineSpacingMultiplier * 1.18f;
    }

    public int getTotalPageCount() {
        int step = getPageStepPx();
        return Math.max(1, (maxScrollY / step) + 1);
    }

    public int getCurrentPageNumber() {
        int step = getPageStepPx();
        int page = (readerScrollY / step) + 1;
        return Math.max(1, Math.min(getTotalPageCount(), page));
    }

    public void scrollToPage(int page) {
        int total = getTotalPageCount();
        page = Math.max(1, Math.min(total, page));
        setReaderScrollY((page - 1) * getPageStepPx());
    }

    public void pageBy(int direction) {
        scrollByPixels(direction * getPageStepPx());
    }

    public void scrollToPercent(float percent) {
        percent = Math.max(0f, Math.min(1f, percent));
        setReaderScrollY(Math.round(maxScrollY * percent));
    }

    public int getCurrentCharPosition() {
        if (layout == null || text.isEmpty()) return 0;
        int layoutY = Math.max(0, readerScrollY - marginVerticalPx);
        int line = layout.getLineForVertical(layoutY);
        return Math.max(0, Math.min(text.length(), layout.getLineStart(line)));
    }

    public void scrollToCharPosition(int charPosition) {
        if (layout == null || text.isEmpty()) return;
        int pos = Math.max(0, Math.min(text.length(), charPosition));
        int line = layout.getLineForOffset(pos);

        // Put the matching line slightly below the top edge so it is not hidden by
        // the status/page strip, and so search results feel like they move to the
        // actual visible location.
        int context = Math.round(getLineHeightPx() * 1.2f);
        setReaderScrollY(layout.getLineTop(line) + marginVerticalPx - context);
    }

    private float spToPx(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }
}
