package com.example.g.analysis;

import java.util.Collections;
import java.util.List;

/** Structured forensic report. Scores describe measured properties, not unsupported AI claims. */
public final class VideoDetectionResult {
    public final String classification, evidenceStrength, provenanceStatus;
    public final int confidence, reliability, qualityScore, analyzedFrames, analyzedScenes;
    public final Integer visualScore, temporalScore, deepfakeScore, audioScore, metadataScore;
    public final List<ModelResult> modelResults, signals, limitations;
    public VideoDetectionResult(String classification, int confidence, int reliability, String evidenceStrength,
                                Integer visualScore, Integer temporalScore, Integer deepfakeScore, Integer audioScore,
                                Integer metadataScore, String provenanceStatus, int qualityScore, int analyzedFrames,
                                int analyzedScenes, List<ModelResult> modelResults, List<ModelResult> signals, List<ModelResult> limitations) {
        this.classification=classification; this.confidence=confidence; this.reliability=reliability; this.evidenceStrength=evidenceStrength;
        this.visualScore=visualScore; this.temporalScore=temporalScore; this.deepfakeScore=deepfakeScore; this.audioScore=audioScore; this.metadataScore=metadataScore;
        this.provenanceStatus=provenanceStatus; this.qualityScore=qualityScore; this.analyzedFrames=analyzedFrames; this.analyzedScenes=analyzedScenes;
        this.modelResults=Collections.unmodifiableList(modelResults); this.signals=Collections.unmodifiableList(signals); this.limitations=Collections.unmodifiableList(limitations);
    }
    public static final class ModelResult { public final String name, detail; public final Integer score; public ModelResult(String name,String detail,Integer score){this.name=name;this.detail=detail;this.score=score;} }
}
