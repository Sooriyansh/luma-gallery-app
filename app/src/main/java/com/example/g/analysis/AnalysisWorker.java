package com.example.g.analysis;

import android.content.Context;
import android.net.Uri;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class AnalysisWorker extends Worker {
    public static final String URI_KEY = "media_uri", NAME_KEY = "media_name", MIME_KEY = "media_mime", MODIFIED_KEY = "media_modified";
    public AnalysisWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }
    @NonNull @Override public Result doWork() {
        String raw = getInputData().getString(URI_KEY); if (raw == null) return Result.failure();
        MediaAnalysisEntity saved = new MediaAnalysisEntity(); saved.uri = raw;
        saved.fileName = valueOrEmpty(getInputData().getString(NAME_KEY)); saved.mimeType = valueOrEmpty(getInputData().getString(MIME_KEY));
        saved.modified = getInputData().getLong(MODIFIED_KEY, 0); saved.scannedAt = System.currentTimeMillis(); saved.createdAt = saved.scannedAt; saved.modelVersion = AnalysisSettings.modelVersion(getApplicationContext());
        try { saved.mediaStoreId = android.content.ContentUris.parseId(Uri.parse(raw)); } catch (Exception ignored) { }
        try { saved.fileHash = sha256(Uri.parse(raw)); } catch (Exception ignored) { }
        MediaAnalysisEntity cached = AnalysisDatabase.get(getApplicationContext()).analyses().get(raw);
        // Re-run results made with an earlier detector revision or a different threshold.
        if (cached != null && saved.modelVersion.equals(cached.modelVersion) && cached.modified == saved.modified && !saved.fileHash.isEmpty() && saved.fileHash.equals(cached.fileHash) && !AnalysisStatus.PENDING.equals(cached.status) && !AnalysisStatus.FAILED.equals(cached.status)) return Result.success();
        // Persist this first so open galleries immediately render “Analyzing…” via Room observation.
        saved.status = AnalysisStatus.PENDING;
        AnalysisDatabase.get(getApplicationContext()).analyses().upsert(saved);
        try {
            String finalStatus;
            int finalConfidence;
            // Fail explicitly when MediaStore has already removed the row instead of treating it as real.
            try (InputStream input = getApplicationContext().getContentResolver().openInputStream(Uri.parse(raw))) {
                if (input == null) throw new FileNotFoundException(raw);
            }
            if (saved.mimeType.startsWith("video/")) {
                VideoDetectionResult result = new ForensicVideoDetector(getApplicationContext()).analyze(Uri.parse(raw), (stage, percent) ->
                        setProgressAsync(new androidx.work.Data.Builder().putString("stage", stage).putInt("progress", percent).build()));
                saved.status = result.classification; saved.confidence = result.confidence;
                saved.source = "Multi-stage local video forensic analysis";
                saved.reason = formatVideoReport(result); saved.metadata = "quality=" + result.qualityScore + "; frames=" + result.analyzedFrames + "; scenes=" + result.analyzedScenes + "; provenance=" + result.provenanceStatus;
                finalStatus = result.classification; finalConfidence = result.confidence;
            } else {
                DetectionResult result = new ForensicImageDetector(getApplicationContext()).analyze(Uri.parse(raw));
                saved.status = result.status; saved.confidence = result.confidence; saved.source = result.source; saved.reason = result.reasonText();
                saved.modelConfidence = result.modelConfidence; saved.metadataScore = result.metadataScore; saved.artifactScore = result.artifactScore; saved.provenanceScore = result.provenanceScore; saved.detectedGenerator = result.detectedGenerator;
                saved.mediaType=result.mediaType;saved.sourceType=result.sourceType;saved.cameraEvidence=result.cameraEvidence;
                saved.screenshotConfidence=result.screenshotConfidence;saved.visualModelAvailable=result.visualModelAvailable;saved.provenanceStatus=result.provenanceStatus;
                saved.aiProbability=result.aiProbability;saved.realProbability=result.realProbability;saved.watermarkScore=result.watermarkScore;
                saved.noiseScore=result.noiseScore;saved.frequencyScore=result.frequencyScore;saved.ganScore=result.ganScore;
                saved.diffusionScore=result.diffusionScore;saved.pixelScore=result.pixelScore;saved.compressionScore=result.compressionScore;
                saved.semanticScore=result.semanticScore;saved.suspiciousRegionScore=result.suspiciousRegionScore;
                saved.confidenceLevel=result.confidenceLevel;saved.watermarkStatus=result.watermarkStatus;
                saved.metadata = "visualModel=" + (result.visualModelAvailable?result.modelConfidence+"%":"unavailable")
                        + "; generatorMetadata=" + result.metadataScore + "; camera=" + result.cameraEvidence
                        + "; screenshot=" + result.screenshotConfidence + "; provenance=" + result.provenanceStatus
                        + "; watermark=" + result.watermarkStatus + "; forensic=" + result.artifactScore;
                finalStatus = result.status; finalConfidence = result.confidence;
            }
            AnalysisDatabase.get(getApplicationContext()).analyses().upsert(saved);
            if (AnalysisStatus.LIKELY_AI.equals(finalStatus) && finalConfidence >= 85) AnalysisNotifier.show(getApplicationContext(), saved);
            return Result.success();
        } catch (FileNotFoundException missing) { saved.status = AnalysisStatus.FAILED; saved.reason = "Image is no longer available in MediaStore."; AnalysisDatabase.get(getApplicationContext()).analyses().upsert(saved); return Result.failure();
        } catch (SecurityException permission) { saved.status = AnalysisStatus.FAILED; saved.reason = "Media permission is unavailable."; AnalysisDatabase.get(getApplicationContext()).analyses().upsert(saved); return Result.failure();
        } catch (Exception failure) { saved.status = AnalysisStatus.FAILED; saved.reason = "Analysis could not finish: " + failure.getClass().getSimpleName(); AnalysisDatabase.get(getApplicationContext()).analyses().upsert(saved); return getRunAttemptCount() < 2 ? Result.retry() : Result.failure(); }
    }
    private static String formatVideoReport(VideoDetectionResult report) {
        StringBuilder text = new StringBuilder("Result: ").append(report.classification).append("\nConfidence: ").append(report.confidence).append("%\nReliability: ").append(report.reliability).append("%\nEvidence strength: ").append(report.evidenceStrength).append("\nVideo quality: ").append(report.qualityScore).append("%\nAdaptive frames: ").append(report.analyzedFrames).append("\nScenes: ").append(report.analyzedScenes).append("\nTemporal consistency: ").append(report.temporalScore == null ? "Not available" : report.temporalScore + "%").append("\nProvenance: ").append(report.provenanceStatus).append("\n\nSignals:");
        for (VideoDetectionResult.ModelResult signal : report.signals) text.append("\n• ").append(signal.name).append(": ").append(signal.detail);
        text.append("\n\nLimitations:"); for (VideoDetectionResult.ModelResult limitation : report.limitations) text.append("\n• ").append(limitation.detail);
        return text.toString();
    }
    private static String valueOrEmpty(String value) { return value == null ? "" : value; }
    private String sha256(Uri uri) throws Exception { MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[32768]; int read; try (InputStream input = getApplicationContext().getContentResolver().openInputStream(uri)) { if (input == null) throw new FileNotFoundException(uri.toString()); while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read); } StringBuilder hash = new StringBuilder(); for (byte b : digest.digest()) hash.append(String.format(Locale.US, "%02x", b)); return hash.toString(); }
}
