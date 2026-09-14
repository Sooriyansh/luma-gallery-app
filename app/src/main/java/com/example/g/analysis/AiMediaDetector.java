package com.example.g.analysis;

import android.net.Uri;

/**
 * Add an on-device model or a consent-gated cloud implementation here. Implementations must
 * report their actual evidence and must never turn an unavailable model into a positive result.
 */
public interface AiMediaDetector {
    DetectionResult analyzeImage(Uri uri) throws Exception;
    DetectionResult analyzeVideo(Uri uri) throws Exception;
}
