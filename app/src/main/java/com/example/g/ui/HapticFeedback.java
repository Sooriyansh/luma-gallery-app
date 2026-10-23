package com.example.g.ui;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** Applies modern confirmation haptics where supported and a safe legacy fallback elsewhere. */
public final class HapticFeedback {
    private HapticFeedback() { }

    public static void confirm(View view) {
        view.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.LONG_PRESS);
    }
}
