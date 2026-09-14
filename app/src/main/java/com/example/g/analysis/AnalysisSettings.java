package com.example.g.analysis;

import android.content.Context;

/** Shared configuration for on-device image-authenticity analysis. */
public final class AnalysisSettings {
    private static final String PREFS = "analysis_settings";
    public static final String AI_THRESHOLD = "ai_detection_threshold";
    public static final int DEFAULT_AI_THRESHOLD = 85;
    public static final String ALGORITHM_VERSION = "multi-layer-fusion-v9";

    private AnalysisSettings() { }

    /** Returns a percentage in the inclusive range 0..100. */
    public static int confidenceThreshold(Context context) {
        int value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(AI_THRESHOLD, DEFAULT_AI_THRESHOLD);
        return Math.max(0, Math.min(100, value));
    }

    public static void setConfidenceThreshold(Context context, int value) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(AI_THRESHOLD, Math.max(0, Math.min(100, value))).apply();
    }

    /** Cache identity includes the threshold because changing it can change the final label. */
    public static String modelVersion(Context context) {
        return VisualAiModel.MODEL_VERSION + "-" + ALGORITHM_VERSION + "-" + VisualAiModel.PREPROCESSING_VERSION
                + "-threshold-" + confidenceThreshold(context);
    }
}
