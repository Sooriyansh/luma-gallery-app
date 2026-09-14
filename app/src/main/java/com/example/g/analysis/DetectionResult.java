package com.example.g.analysis;

import java.util.Collections;
import java.util.List;

public final class DetectionResult {
    public final String status, source, detectedGenerator, mediaType, sourceType, provenanceStatus;
    public final String watermarkStatus, confidenceLevel;
    public final int confidence;
    public final int modelConfidence, metadataScore, artifactScore, provenanceScore, cameraEvidence, screenshotConfidence;
    public final int aiProbability, realProbability, watermarkScore, noiseScore, frequencyScore, ganScore;
    public final int diffusionScore, pixelScore, compressionScore, semanticScore, suspiciousRegionScore;
    public final boolean visualModelAvailable;
    public final List<String> signals, topAiReasons, topRealReasons;
    public DetectionResult(String status, int confidence, String source, List<String> signals) {
        this(status, confidence, source, signals, 0, 0, 0, 0, "", MediaSourceEvidence.OTHER,
                MediaSourceEvidence.OTHER, 0, 0, false, "Not available");
    }
    public DetectionResult(String status, int confidence, String source, List<String> signals, int model, int metadata, int artifact, int provenance, String generator) {
        this(status,confidence,source,signals,model,metadata,artifact,provenance,generator,
                MediaSourceEvidence.OTHER,MediaSourceEvidence.OTHER,0,0,false,"Not available");
    }
    public DetectionResult(String status, int confidence, String source, List<String> signals, int model, int metadata, int artifact,
                           int provenance, String generator, String mediaType, String sourceType, int cameraEvidence,
                           int screenshotConfidence, boolean visualModelAvailable, String provenanceStatus) {
        this.status = status; this.confidence = Math.max(0, Math.min(100, confidence)); this.source = source;
        this.modelConfidence = clamp(model); this.metadataScore = clamp(metadata); this.artifactScore = clamp(artifact); this.provenanceScore = clamp(provenance);
        this.detectedGenerator = generator == null ? "" : generator; this.signals = Collections.unmodifiableList(signals);
        this.mediaType=mediaType;this.sourceType=sourceType;this.cameraEvidence=clamp(cameraEvidence);
        this.screenshotConfidence=clamp(screenshotConfidence);this.visualModelAvailable=visualModelAvailable;this.provenanceStatus=provenanceStatus;
        this.aiProbability=this.confidence;this.realProbability=100-this.confidence;this.watermarkScore=0;
        this.noiseScore=0;this.frequencyScore=0;this.ganScore=0;this.diffusionScore=0;this.pixelScore=0;
        this.compressionScore=0;this.semanticScore=0;this.suspiciousRegionScore=0;
        this.watermarkStatus="Unavailable";this.confidenceLevel=confidence>=80?"Highly Likely AI Generated":confidence>=60?"Likely AI Generated":confidence>=41?"Uncertain":confidence>=21?"Probably Real":"Likely Real";
        this.topAiReasons=Collections.emptyList();this.topRealReasons=Collections.emptyList();
    }

    public DetectionResult(String status, int aiProbability, String source, List<String> signals,
                           int model, int metadata, int artifact, int provenance, int watermark,
                           String generator, String mediaType, String sourceType, int cameraEvidence,
                           int screenshotConfidence, boolean visualModelAvailable, String provenanceStatus,
                           String watermarkStatus, String confidenceLevel, int noise, int frequency,
                           int gan, int diffusion, int pixel, int compression, int semantic, int region,
                           List<String> topAiReasons, List<String> topRealReasons) {
        this.status=status;this.confidence=clamp(aiProbability);this.aiProbability=this.confidence;
        this.realProbability=100-this.aiProbability;this.source=source;this.signals=Collections.unmodifiableList(new java.util.ArrayList<>(signals));
        this.modelConfidence=clamp(model);this.metadataScore=clamp(metadata);this.artifactScore=clamp(artifact);
        this.provenanceScore=clamp(provenance);this.watermarkScore=clamp(watermark);
        this.detectedGenerator=generator==null?"":generator;this.mediaType=mediaType;this.sourceType=sourceType;
        this.cameraEvidence=clamp(cameraEvidence);this.screenshotConfidence=clamp(screenshotConfidence);
        this.visualModelAvailable=visualModelAvailable;this.provenanceStatus=provenanceStatus;this.watermarkStatus=watermarkStatus;
        this.confidenceLevel=confidenceLevel;this.noiseScore=clamp(noise);this.frequencyScore=clamp(frequency);
        this.ganScore=clamp(gan);this.diffusionScore=clamp(diffusion);this.pixelScore=clamp(pixel);
        this.compressionScore=clamp(compression);this.semanticScore=clamp(semantic);this.suspiciousRegionScore=clamp(region);
        this.topAiReasons=Collections.unmodifiableList(new java.util.ArrayList<>(topAiReasons));
        this.topRealReasons=Collections.unmodifiableList(new java.util.ArrayList<>(topRealReasons));
    }
    private static int clamp(int v) { return Math.max(0, Math.min(100, v)); }
    public String reasonText() { return android.text.TextUtils.join("\n", signals); }
}
