package com.simpletext.reader.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
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
    private final Paint searchHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeSearchHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path searchHighlightPath = new Path();
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
    private int topTextZoneOffsetPx = 0;
    private int bottomTextZoneOffsetPx = 0;
    private int leftTextInsetPx = 0;
    private int rightTextInsetPx = 0;
    private Typeface typeface = Typeface.DEFAULT;
    private String searchQuery = "";
    private int activeSearchIndex = -1;

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
        searchHighlightPaint.setStyle(Paint.Style.FILL);
        activeSearchHighlightPaint.setStyle(Paint.Style.FILL);
        updateSearchHighlightColors();
    }

    public void setReaderListener(ReaderListener listener) {
        this.listener = listener;
    }

    public void releaseTextResources() {
        if (!scroller.isFinished()) {
            scroller.abortAnimation();
        }
        recycleVelocityTracker();

        listener = null;
        text = "";
        layout = null;
        searchQuery = "";
        activeSearchIndex = -1;
        readerScrollY = 0;
        maxScrollY = 0;
        invalidate();
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
        float nextLineSpacing = Math.max(0.8f, lineSpacingMultiplier);
        int nextMarginHorizontal = Math.max(0, marginHorizontalPx);
        int nextMarginVertical = Math.max(0, marginVerticalPx);
        Typeface nextTypeface = typeface != null ? typeface : Typeface.DEFAULT;

        boolean layoutAffectingChange = Float.compare(this.fontSizeSp, fontSizeSp) != 0
                || Float.compare(this.lineSpacingMultiplier, nextLineSpacing) != 0
                || this.marginHorizontalPx != nextMarginHorizontal
                || this.marginVerticalPx != nextMarginVertical
                || this.typeface != nextTypeface;

        boolean colorChange = this.textColor != textColor || this.backgroundColor != backgroundColor;

        this.fontSizeSp = fontSizeSp;
        this.lineSpacingMultiplier = nextLineSpacing;
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
        this.marginHorizontalPx = nextMarginHorizontal;
        this.marginVerticalPx = nextMarginVertical;
        this.typeface = nextTypeface;

        paint.setColor(textColor);
        paint.setTextSize(spToPx(fontSizeSp));
        paint.setTypeface(this.typeface);
        updateSearchHighlightColors();

        if (layoutAffectingChange) {
            rebuildLayout();
            notifyScrollChanged();
        }

        if (layoutAffectingChange || colorChange) {
            invalidate();
        }
    }

    public void setOverlapLines(int overlapLines) {
        this.overlapLines = Math.max(0, Math.min(8, overlapLines));
    }

    public void setTextZoneAdjustments(int topOffsetPx, int bottomOffsetPx, int leftInsetPx, int rightInsetPx) {
        int nextTopOffset = Math.max(0, Math.min(240, topOffsetPx));
        int nextBottomOffset = Math.max(0, Math.min(240, bottomOffsetPx));
        int nextLeftInset = Math.max(0, Math.min(240, leftInsetPx));
        int nextRightInset = Math.max(0, Math.min(240, rightInsetPx));

        boolean widthChanged = this.leftTextInsetPx != nextLeftInset
                || this.rightTextInsetPx != nextRightInset;
        boolean viewportChanged = this.topTextZoneOffsetPx != nextTopOffset
                || this.bottomTextZoneOffsetPx != nextBottomOffset;

        if (!widthChanged && !viewportChanged) return;

        this.topTextZoneOffsetPx = nextTopOffset;
        this.bottomTextZoneOffsetPx = nextBottomOffset;
        this.leftTextInsetPx = nextLeftInset;
        this.rightTextInsetPx = nextRightInset;

        if (widthChanged) {
            rebuildLayout();
        } else {
            recalcMaxScroll();
        }

        invalidate();
        notifyScrollChanged();
    }

    public void setSearchHighlight(String query, int activeSearchIndex) {
        this.searchQuery = query != null ? query : "";
        this.activeSearchIndex = this.searchQuery.isEmpty() ? -1 : activeSearchIndex;
        invalidate();
    }

    private void updateSearchHighlightColors() {
        boolean light = isLightColor(backgroundColor);
        if (light) {
            searchHighlightPaint.setColor(Color.argb(88, 255, 216, 96));
            activeSearchHighlightPaint.setColor(Color.argb(150, 255, 184, 56));
        } else {
            searchHighlightPaint.setColor(Color.argb(108, 255, 221, 105));
            activeSearchHighlightPaint.setColor(Color.argb(170, 255, 195, 75));
        }
    }

    private boolean isLightColor(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b);
        return luminance > 160;
    }

    private void rebuildLayout() {
        int width = getWidth() - getPaddingLeft() - getPaddingRight()
                - marginHorizontalPx * 2 - leftTextInsetPx - rightTextInsetPx;
        if (width <= 0) return;

        layout = StaticLayout.Builder
                .obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, lineSpacingMultiplier)
                .setIncludePad(true)
                .setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE)
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
        int top = getTextViewportTopY();
        int bottom = getTextViewportBottomY();
        return Math.max(1, bottom - top);
    }

    private int getTextViewportTopY() {
        int physicalTop = getPaddingTop();
        int physicalBottom = getHeight() - getPaddingBottom();
        int shiftedTop = physicalTop + topTextZoneOffsetPx;
        return Math.max(physicalTop, Math.min(physicalBottom - 1, shiftedTop));
    }

    private int getTextViewportBottomY() {
        int physicalTop = getTextViewportTopY();
        int physicalBottom = getHeight() - getPaddingBottom();
        int shiftedBottom = physicalBottom - bottomTextZoneOffsetPx;
        return Math.max(physicalTop + 1, Math.min(physicalBottom, shiftedBottom));
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
        int viewportTop = getTextViewportTopY();
        canvas.clipRect(getPaddingLeft() + marginHorizontalPx + leftTextInsetPx,
                viewportTop,
                getWidth() - getPaddingRight() - marginHorizontalPx - rightTextInsetPx,
                getFullLineClipBottom());
        canvas.translate(getPaddingLeft() + marginHorizontalPx + leftTextInsetPx,
                viewportTop + marginVerticalPx - readerScrollY);
        drawSearchHighlights(canvas);
        layout.draw(canvas);
        canvas.restore();
    }

    private void drawSearchHighlights(Canvas canvas) {
        if (layout == null || text.isEmpty() || searchQuery == null || searchQuery.isEmpty()) return;

        int lineCount = layout.getLineCount();
        if (lineCount <= 0) return;

        int layoutTopY = Math.max(0, readerScrollY - marginVerticalPx);
        int viewportHeight = getViewportHeight();
        int layoutBottomY = Math.min(layout.getHeight(), layoutTopY + viewportHeight);

        int startLine = Math.max(0, layout.getLineForVertical(layoutTopY) - 1);
        int endLine = Math.min(lineCount - 1, layout.getLineForVertical(Math.max(0, layoutBottomY - 1)) + 1);

        int startChar = Math.max(0, layout.getLineStart(startLine) - Math.max(0, searchQuery.length() - 1));
        int endChar = Math.min(text.length(), layout.getLineEnd(endLine) + Math.max(0, searchQuery.length() - 1));
        int step = Math.max(1, searchQuery.length());

        int index = text.indexOf(searchQuery, startChar);
        while (index >= 0 && index < endChar) {
            int matchEnd = Math.min(text.length(), index + searchQuery.length());
            if (matchEnd > startChar) {
                searchHighlightPath.reset();
                layout.getSelectionPath(index, matchEnd, searchHighlightPath);
                canvas.drawPath(searchHighlightPath,
                        index == activeSearchIndex ? activeSearchHighlightPaint : searchHighlightPaint);
            }
            int nextStart = Math.max(index + step, index + 1);
            index = text.indexOf(searchQuery, nextStart);
        }
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
        int clamped = clampScrollY(y);
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

    private int clampScrollY(int y) {
        return Math.max(0, Math.min(maxScrollY, y));
    }

    private int snapScrollYToLineTop(int y) {
        if (layout == null || text.isEmpty()) return clampScrollY(y);

        int clamped = clampScrollY(y);
        if (clamped <= 0) return 0;
        if (clamped >= maxScrollY) return maxScrollY;

        int layoutY = Math.max(0, clamped - marginVerticalPx);
        int line = layout.getLineForVertical(layoutY);

        // Align the visible top edge to a real StaticLayout line boundary.
        // This prevents the top line from being clipped in half after tap paging.
        return clampScrollY(layout.getLineTop(line) + marginVerticalPx);
    }

    private int getFullLineClipBottom() {
        if (layout == null) {
            return getTextViewportBottomY();
        }

        int viewTop = getTextViewportTopY();
        int viewBottom = getTextViewportBottomY();
        int viewportHeight = Math.max(1, viewBottom - viewTop);

        int layoutTopY = Math.max(0, readerScrollY - marginVerticalPx);
        int visibleBottomInLayout = Math.min(layout.getHeight(), layoutTopY + viewportHeight);

        int line = layout.getLineForVertical(Math.max(0, visibleBottomInLayout));
        int lineBottom = layout.getLineBottom(line);

        if (lineBottom > visibleBottomInLayout) {
            int fullBottomInLayout = Math.max(0, layout.getLineTop(line));
            int screenBottom = viewTop + marginVerticalPx - readerScrollY + fullBottomInLayout;
            return Math.max(viewTop, Math.min(viewBottom, screenBottom));
        }

        return viewBottom;
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
        int total = getTotalPageCount();
        int page = Math.round(readerScrollY / (float) step) + 1;
        return Math.max(1, Math.min(total, page));
    }

    private int getPageAnchorScrollY(int page) {
        int total = getTotalPageCount();
        int clampedPage = Math.max(1, Math.min(total, page));

        // Page 1 must be the absolute top. Snapping 0 to a line top can otherwise
        // become marginVerticalPx, which makes the first page drift down by one row
        // after paging back to the beginning.
        if (clampedPage <= 1) {
            return 0;
        }

        // Keep counted pages on normal page-step anchors. Do not force the last
        // counted page directly to maxScrollY; otherwise the content between the
        // last full page anchor and the physical end can be skipped.
        return snapScrollYToLineTop((clampedPage - 1) * getPageStepPx());
    }

    public void scrollToPage(int page) {
        setReaderScrollY(getPageAnchorScrollY(page));
    }

    public void pageBy(int direction) {
        if (direction == 0) return;

        int total = getTotalPageCount();
        int current = getCurrentPageNumber();

        if (direction > 0 && current >= total) {
            setReaderScrollY(maxScrollY);
            return;
        }

        if (direction < 0 && current <= 1) {
            setReaderScrollY(0);
            return;
        }

        scrollToPage(current + direction);
    }

    public void scrollToPercent(float percent) {
        percent = Math.max(0f, Math.min(1f, percent));
        setReaderScrollY(snapScrollYToLineTop(Math.round(maxScrollY * percent)));
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
        setReaderScrollY(snapScrollYToLineTop(layout.getLineTop(line) + marginVerticalPx - context));
    }

    private float spToPx(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }
}
