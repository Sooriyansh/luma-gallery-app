package com.example.g.features.aiDetection;

import java.util.List;

/** Stable public contract; detector internals and Gallery UI must remain independent. */
public interface AIImageDetector {
    interface ResultCallback { void onResult(AIDetectionResult result); }

    void scanImage(AIImageReference image);
    void scanImages(List<AIImageReference> images);
    void getCachedResult(String imageId, ResultCallback callback);
    void cancelScan(String imageId);
}
