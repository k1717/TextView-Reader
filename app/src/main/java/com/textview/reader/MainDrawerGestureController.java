package com.textview.reader;

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Method;

final class MainDrawerGestureController {
    private final MainActivity activity;

    MainDrawerGestureController(@NonNull MainActivity activity) {
        this.activity = activity;
    }

    boolean handleProportionalDrawerEdgeDrag(@NonNull MotionEvent event) {
        if (activity.drawerLayout == null || activity.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            resetDrawerSwipeState();
            return false;
        }

        View drawerView = activity.findViewById(R.id.nav_drawer);
        if (drawerView == null || drawerView.getWidth() <= 0) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activity.drawerSwipeStartX = event.getX();
                activity.drawerSwipeStartY = event.getY();
                activity.drawerSwipeOpened = false;
                activity.drawerManualDragging = false;
                // No edge-only border: drawer gestures may start anywhere on the main screen,
                // except controls that need horizontal/typing gestures themselves.
                activity.drawerSwipeTracking = !isTouchInsideView(activity.fileSearchInput, event)
                        && !isTouchInsideView(activity.fileSearchBar, event)
                        && !isTouchInsideView(activity.fileTypeFilterScroll, event);
                return false;

            case MotionEvent.ACTION_MOVE:
                if (!activity.drawerSwipeTracking) return false;

                float dx = event.getX() - activity.drawerSwipeStartX;
                float dy = event.getY() - activity.drawerSwipeStartY;
                float absDx = Math.abs(dx);
                float absDy = Math.abs(dy);

                if (!activity.drawerManualDragging) {
                    if (absDy > activity.drawerSwipeTouchSlop * 2.4f && absDy > absDx * 1.30f) {
                        resetDrawerSwipeState();
                        return false;
                    }
                    float startThreshold = Math.max(activity.dpToPx(2), activity.drawerSwipeTouchSlop * 0.45f);
                    if (dx <= startThreshold || absDx <= absDy * 1.00f) {
                        return false;
                    }
                    activity.drawerManualDragging = true;
                    cancelMainListPendingPresses();
                    activity.drawerForceSettling = false;
                    activity.drawerClosePartialOnRelease = false;
                }

                final float dragGain = 1.18f;
                float offset = Math.max(0f, Math.min(1f,
                        (dx * dragGain) / Math.max(1f, drawerView.getWidth())));
                applyDrawerManualOffset(drawerView, offset);
                return true;

            case MotionEvent.ACTION_UP:
                if (!activity.drawerManualDragging) {
                    resetDrawerSwipeState();
                    return false;
                }

                boolean fullyPulled = activity.drawerSlideOffset >= 0.30f;
                resetDrawerSwipeState();
                if (fullyPulled) {
                    drawerView.setTranslationX(0f);
                    drawerView.setVisibility(View.VISIBLE);
                    activity.drawerLayout.openDrawer(GravityCompat.START);
                } else {
                    forceDrawerClosedVisualState(drawerView);
                    activity.drawerLayout.closeDrawer(GravityCompat.START);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (activity.drawerManualDragging) {
                    forceDrawerClosedVisualState(drawerView);
                    activity.drawerLayout.closeDrawer(GravityCompat.START);
                    resetDrawerSwipeState();
                    return true;
                }
                resetDrawerSwipeState();
                return false;

            default:
                return false;
        }
    }

    private void applyDrawerManualOffset(@NonNull View drawerView, float offset) {
        activity.drawerSlideOffset = offset;

        if (offset > 0f) {
            drawerView.setVisibility(View.VISIBLE);
            activity.drawerLayout.bringChildToFront(drawerView);
        }

        try {
            Method method = activity.drawerMoveToOffsetMethod;
            if (method == null) {
                method = DrawerLayout.class.getDeclaredMethod("moveDrawerToOffset", View.class, float.class);
                method.setAccessible(true);
                activity.drawerMoveToOffsetMethod = method;
            }
            method.invoke(activity.drawerLayout, drawerView, offset);
            drawerView.setVisibility(offset > 0f ? View.VISIBLE : View.INVISIBLE);
            activity.drawerLayout.invalidate();
        } catch (Throwable ignored) {
            // Fallback path for AndroidX internals changes. DrawerLayout normally
            // lays a closed left drawer at -drawerWidth; therefore positive
            // translationX must be width * offset. The old negative translation
            // pushed the drawer farther offscreen, leaving only the dark scrim.
            int width = Math.max(1, drawerView.getWidth());
            drawerView.setTranslationX(width * offset);
            drawerView.setVisibility(offset > 0f ? View.VISIBLE : View.INVISIBLE);
            activity.drawerLayout.invalidate();
        }
    }

    private void forceDrawerClosedVisualState(@NonNull View drawerView) {
        activity.drawerSlideOffset = 0f;
        activity.drawerClosePartialOnRelease = false;
        activity.drawerForceSettling = false;
        applyDrawerManualOffset(drawerView, 0f);
        drawerView.setTranslationX(0f);
        drawerView.setVisibility(View.INVISIBLE);
        activity.drawerLayout.postInvalidateOnAnimation();
    }

    private boolean isTouchInsideView(View view, @NonNull MotionEvent event) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        return rawX >= loc[0] && rawX <= loc[0] + view.getWidth()
                && rawY >= loc[1] && rawY <= loc[1] + view.getHeight();
    }

    void closeDrawerAfterSelection() {
        if (activity.drawerLayout == null) {
            resetDrawerSwipeState();
            return;
        }

        View drawerView = activity.findViewById(R.id.nav_drawer);
        activity.drawerClosePartialOnRelease = false;
        activity.drawerForceSettling = false;
        resetDrawerSwipeState();

        try {
            // Do not gate this behind isDrawerOpen(). On some large-screen/tablet
            // paths the drawer can be visible/interactive while DrawerLayout has not
            // yet promoted it to the fully-open flag. Gating on isDrawerOpen() then
            // leaves the drawer on screen after a shortcut/recent-folder tap.
            activity.drawerLayout.closeDrawer(GravityCompat.START);
        } catch (IllegalArgumentException ignored) {
            if (drawerView != null) {
                forceDrawerClosedVisualState(drawerView);
            }
            return;
        }

        if (drawerView != null) {
            drawerView.postDelayed(() -> {
                if (activity.drawerLayout == null) return;
                if (activity.drawerLayout.isDrawerVisible(GravityCompat.START)
                        && !activity.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    forceDrawerClosedVisualState(drawerView);
                }
            }, 280L);
        }
    }

    void resetDrawerSwipeState() {
        activity.drawerSwipeTracking = false;
        activity.drawerManualDragging = false;
        activity.drawerSwipeOpened = false;
    }

    private void cancelMainListPendingPresses() {
        if (activity.fileAdapter != null) activity.fileAdapter.cancelPendingPresses();
        if (activity.recentAdapter != null) activity.recentAdapter.cancelPendingPresses();
        cancelRecyclerTouchState(activity.fileRecyclerView);
        cancelRecyclerTouchState(activity.recentRecyclerView);
    }

    private void cancelRecyclerTouchState(RecyclerView recyclerView) {
        if (recyclerView == null) return;
        recyclerView.cancelLongPress();
        recyclerView.setPressed(false);
        recyclerView.clearFocus();
        clearPressedStateRecursive(recyclerView);
        recyclerView.post(() -> clearPressedStateRecursive(recyclerView));
    }

    private void clearPressedStateRecursive(View view) {
        if (view == null) return;
        view.setPressed(false);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                clearPressedStateRecursive(group.getChildAt(i));
            }
        }
    }

    void settleHalfOpenedDrawer() {
        if (activity.drawerLayout == null || activity.drawerForceSettling) return;

        // Manual edge-drag release already decides open vs close. This remains as
        // a safety net for any partial state left by the underlying DrawerLayout.
        if (!activity.drawerLayout.isDrawerOpen(GravityCompat.START)
                && activity.drawerLayout.isDrawerVisible(GravityCompat.START)
                && activity.drawerSlideOffset < 0.995f) {
            View drawerView = activity.findViewById(R.id.nav_drawer);
            activity.drawerForceSettling = true;
            if (drawerView != null) {
                forceDrawerClosedVisualState(drawerView);
            }
            activity.drawerLayout.closeDrawer(GravityCompat.START);
        }
    }
}
