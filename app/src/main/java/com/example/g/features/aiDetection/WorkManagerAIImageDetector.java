package com.example.g.features.aiDetection;

import android.content.Context;
import androidx.work.WorkManager;
import com.example.g.analysis.AnalysisRepository;
import com.example.g.analysis.AnalysisScheduler;
import com.example.g.analysis.AnalysisStatus;
import com.example.g.analysis.MediaAnalysisEntity;
import java.util.List;

/** Adapter that preserves the existing detector, Room cache, and controlled WorkManager queue. */
public final class WorkManagerAIImageDetector implements AIImageDetector {
    private final Context app;

    public WorkManagerAIImageDetector(Context context) { app = context.getApplicationContext(); }

    @Override public void scanImage(AIImageReference image) {
        if (image == null) return;
        AnalysisScheduler.enqueue(app, image.uri, image.name, image.mimeType, image.modified);
    }

    @Override public void scanImages(List<AIImageReference> images) {
        if (images == null) return;
        for (AIImageReference image : images) scanImage(image);
    }

    @Override public void getCachedResult(String imageId, ResultCallback callback) {
        if (callback == null) return;
        AnalysisRepository.loadOne(app, imageId, values -> {
            MediaAnalysisEntity entity = values.get(imageId);
            callback.onResult(entity == null ? null : map(entity));
        });
    }

    @Override public void cancelScan(String imageId) {
        if (imageId == null) return;
        WorkManager.getInstance(app).cancelAllWorkByTag(AnalysisScheduler.workTag(imageId));
    }

    private static AIDetectionResult map(MediaAnalysisEntity entity) {
        AIDetectionOutcome outcome;
        if (AnalysisStatus.LIKELY_AI.equals(entity.status)) outcome = AIDetectionOutcome.AI_GENERATED;
        else if (AnalysisStatus.POSSIBLY_AI.equals(entity.status)
                || AnalysisStatus.POSSIBLY_EDITED.equals(entity.status)) outcome = AIDetectionOutcome.LIKELY_AI;
        else if (AnalysisStatus.LIKELY_REAL.equals(entity.status)) outcome = AIDetectionOutcome.LIKELY_REAL;
        else if (AnalysisStatus.FAILED.equals(entity.status)) outcome = AIDetectionOutcome.SCAN_FAILED;
        else outcome = AIDetectionOutcome.UNCERTAIN;
        return new AIDetectionResult(entity.uri, outcome, entity.confidence, entity.reason);
    }
}
