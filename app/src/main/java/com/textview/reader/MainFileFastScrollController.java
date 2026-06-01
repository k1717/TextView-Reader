package com.textview.reader;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

final class MainFileFastScrollController {
    private static final int MIN_FAST_SCROLL_ITEMS = 40;

    private final MainActivity activity;
    private boolean dragging;

    MainFileFastScrollController(@NonNull MainActivity activity) {
        this.activity = activity;
    }

    void install() {
        if (activity.fileRecyclerView == null
                || activity.fileFastScrollRail == null
                || activity.fileFastScrollThumb == null) return;

        activity.fileFastScrollRail.bringToFront();
        activity.fileFastScrollRail.setOnTouchListener(this::onRailTouch);
        activity.fileRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateThumbFromRecycler();
            }
        });
        if (activity.fileAdapter != null) {
            activity.fileAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override public void onChanged() { scheduleThumbUpdate(); }
                @Override public void onItemRangeInserted(int positionStart, int itemCount) { scheduleThumbUpdate(); }
                @Override public void onItemRangeRemoved(int positionStart, int itemCount) { scheduleThumbUpdate(); }
                @Override public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) { scheduleThumbUpdate(); }
                @Override public void onItemRangeChanged(int positionStart, int itemCount) { scheduleThumbUpdate(); }
                @Override public void onItemRangeChanged(int positionStart, int itemCount, Object payload) { scheduleThumbUpdate(); }
            });
        }
        scheduleThumbUpdate();
    }

    private boolean onRailTouch(View view, MotionEvent event) {
        if (activity.fileRecyclerView == null || activity.fileAdapter == null) return false;
        int count = activity.fileAdapter.getItemCount();
        if (count < MIN_FAST_SCROLL_ITEMS) return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                view.getParent().requestDisallowInterceptTouchEvent(true);
                setThumbPressed(true);
                scrollToTouchY(event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    scrollToTouchY(event.getY());
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    setThumbPressed(false);
                    updateThumbFromRecycler();
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private void scrollToTouchY(float y) {
        RecyclerView recyclerView = activity.fileRecyclerView;
        if (recyclerView == null || activity.fileAdapter == null || activity.fileFastScrollRail == null
                || activity.fileFastScrollThumb == null) return;
        RecyclerView.LayoutManager rawManager = recyclerView.getLayoutManager();
        if (!(rawManager instanceof LinearLayoutManager)) return;

        int count = activity.fileAdapter.getItemCount();
        if (count <= 1) return;

        int railHeight = activity.fileFastScrollRail.getHeight();
        int thumbHeight = Math.max(1, activity.fileFastScrollThumb.getHeight());
        int travel = Math.max(1, railHeight - thumbHeight);
        float clampedTop = Math.max(0f, Math.min(travel, y - (thumbHeight / 2f)));
        float ratio = clampedTop / travel;
        int target = Math.max(0, Math.min(count - 1, Math.round(ratio * (count - 1))));
        ((LinearLayoutManager) rawManager).scrollToPositionWithOffset(target, 0);
        moveThumbTo(clampedTop);
    }

    private void scheduleThumbUpdate() {
        if (activity.fileFastScrollRail == null) return;
        activity.fileFastScrollRail.post(this::updateThumbFromRecycler);
    }

    private void updateThumbFromRecycler() {
        if (dragging
                || activity.fileRecyclerView == null
                || activity.fileAdapter == null
                || activity.fileFastScrollRail == null
                || activity.fileFastScrollThumb == null) return;

        int count = activity.fileAdapter.getItemCount();
        int range = activity.fileRecyclerView.computeVerticalScrollRange();
        int extent = activity.fileRecyclerView.computeVerticalScrollExtent();
        boolean visible = count >= MIN_FAST_SCROLL_ITEMS && range > extent;
        activity.fileFastScrollRail.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;

        int travel = Math.max(1, activity.fileFastScrollRail.getHeight() - activity.fileFastScrollThumb.getHeight());
        int offset = activity.fileRecyclerView.computeVerticalScrollOffset();
        int maxOffset = Math.max(1, range - extent);
        moveThumbTo((offset / (float) maxOffset) * travel);
    }

    private void moveThumbTo(float top) {
        if (activity.fileFastScrollThumb != null) {
            activity.fileFastScrollThumb.setTranslationY(top);
        }
    }

    private void setThumbPressed(boolean pressed) {
        if (activity.fileFastScrollRail != null) activity.fileFastScrollRail.setPressed(pressed);
        if (activity.fileFastScrollThumb != null) activity.fileFastScrollThumb.setPressed(pressed);
    }
}
