package com.example.g.features.aiDetection;

public final class AIDetectionResult {
    public final String imageId;
    public final AIDetectionOutcome outcome;
    public final int confidence;
    public final String explanation;

    public AIDetectionResult(String imageId, AIDetectionOutcome outcome, int confidence, String explanation) {
        this.imageId = imageId;
        this.outcome = outcome;
        this.confidence = Math.max(0, Math.min(100, confidence));
        this.explanation = explanation == null ? "" : explanation;
    }
}
