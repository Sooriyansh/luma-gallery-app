package com.example.g.analysis;

import android.net.Uri;

/** Provider boundary for a validated ML/deepfake model. No model means no ML conclusion. */
public interface VideoAiDetector {
    VideoDetectionResult analyze(Uri video, Progress progress) throws Exception;
    interface Progress { void update(String stage, int percent); }
}
