package com.example.g.ui;

import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Applies display-cutout and system-gesture insets without hard-coding a device offset. */
public final class SystemBarInsets {
    private SystemBarInsets() { }

    /** Insets a normal screen on every edge, including the software keyboard when visible. */
    public static void screen(Window window, View root) {
        WindowCompat.setDecorFitsSystemWindows(window, false);
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = contentInsets(insets, true);
            view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /** Keeps the gallery header, bottom navigation and floating action clear of all system UI. */
    public static void gallery(Window window, View root, View header, View content, View navigation, View fab) {
        WindowCompat.setDecorFitsSystemWindows(window, false);
        final int navigationHeight = navigation.getLayoutParams().height;
        final int navigationStart = navigation.getPaddingStart();
        final int navigationTop = navigation.getPaddingTop();
        final int navigationEnd = navigation.getPaddingEnd();
        final int navigationBottom = navigation.getPaddingBottom();
        final int contentBottom = bottomMargin(content);
        final int contentStart = content.getPaddingStart();
        final int contentTop = content.getPaddingTop();
        final int contentEnd = content.getPaddingEnd();
        final int contentPaddingBottom = content.getPaddingBottom();
        final int fabEnd = endMargin(fab);
        final int fabBottom = bottomMargin(fab);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = contentInsets(insets, false);
            setPaddingRelative(header, safe.left, safe.top, safe.right, 0);
            // Keep Material's full 80dp content area and add the gesture/navigation inset below
            // it. Padding a fixed-height bar was squeezing and clipping its icons and labels.
            ViewGroup.LayoutParams navigationParams = navigation.getLayoutParams();
            int requiredHeight = navigationHeight + safe.bottom;
            if (navigationParams.height != requiredHeight) {
                navigationParams.height = requiredHeight;
                navigation.setLayoutParams(navigationParams);
            }
            setPaddingRelative(navigation, navigationStart + safe.left, navigationTop,
                    navigationEnd + safe.right, navigationBottom + safe.bottom);
            content.setPaddingRelative(contentStart + safe.left, contentTop,
                    contentEnd + safe.right, contentPaddingBottom);
            setBottomMargin(content, contentBottom + safe.bottom);
            setEndAndBottomMargins(fab, fabEnd + safe.right, fabBottom + safe.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    public static void imageViewer(Window window, View root, View toolbar, View pager, View actions) {
        WindowCompat.setDecorFitsSystemWindows(window, false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = contentInsets(insets, false);
            resizeWithTopInset(toolbar, safe);
            resizeWithBottomInset(actions, safe);
            pager.setPadding(safe.left, safe.top + dp(pager, 56), safe.right, safe.bottom + dp(pager, 64));
            if (pager instanceof ViewGroup) ((ViewGroup) pager).setClipToPadding(false);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    public static void bottomActions(Window window, View root, View actions) {
        WindowCompat.setDecorFitsSystemWindows(window, false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = contentInsets(insets, false);
            resizeWithBottomInset(actions, safe);
            root.setPadding(0, safe.top, 0, 0);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    public static void cropEditor(Window window, View root, View actionBar) {
        WindowCompat.setDecorFitsSystemWindows(window, false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safe = contentInsets(insets, true);
            root.setPadding(safe.left, safe.top, safe.right, 0);
            actionBar.setPadding(0, 0, 0, safe.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private static void resizeWithTopInset(View target, Insets safe) {
        ViewGroup.LayoutParams params = target.getLayoutParams(); int height = dp(target, 56) + safe.top;
        if (params.height != height) { params.height = height; target.setLayoutParams(params); }
        target.setPadding(safe.left, safe.top, safe.right, 0);
    }

    private static void resizeWithBottomInset(View target, Insets safe) {
        ViewGroup.LayoutParams params = target.getLayoutParams(); int height = dp(target, 64) + safe.bottom;
        if (params.height != height) { params.height = height; target.setLayoutParams(params); }
        target.setPadding(safe.left, 0, safe.right, safe.bottom);
    }

    @NonNull private static Insets contentInsets(WindowInsetsCompat insets, boolean includeIme) {
        int types = WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout()
                | WindowInsetsCompat.Type.mandatorySystemGestures();
        if (includeIme) types |= WindowInsetsCompat.Type.ime();
        return insets.getInsets(types);
    }

    private static void setPaddingRelative(View target, int start, int top, int end, int bottom) {
        target.setPaddingRelative(start, top, end, bottom);
    }

    private static int bottomMargin(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params instanceof ViewGroup.MarginLayoutParams ? ((ViewGroup.MarginLayoutParams) params).bottomMargin : 0;
    }

    private static int endMargin(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        return params instanceof ViewGroup.MarginLayoutParams ? ((ViewGroup.MarginLayoutParams) params).getMarginEnd() : 0;
    }

    private static void setBottomMargin(View view, int bottom) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        if (margins.bottomMargin != bottom) { margins.bottomMargin = bottom; view.setLayoutParams(margins); }
    }

    private static void setEndAndBottomMargins(View view, int end, int bottom) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        if (margins.getMarginEnd() != end || margins.bottomMargin != bottom) {
            margins.setMarginEnd(end); margins.bottomMargin = bottom; view.setLayoutParams(margins);
        }
    }

    private static int dp(View view, int value) { return Math.round(value * view.getResources().getDisplayMetrics().density); }
}
