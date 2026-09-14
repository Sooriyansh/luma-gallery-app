package com.example.g.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Pure decision policy: reliability-weighted fusion, corroboration, and explicit uncertainty. */
public final class ImageEvidenceFusion {
    public static final class Input {
        public String sourceType = MediaSourceEvidence.OTHER;
        public int screenshotScore, cameraScore, cameraPipelineScore, visualAiScore;
        public int generatorMetadataScore, provenanceScore, watermarkScore, editingScore;
        public int noiseScore, frequencyScore, ganScore, diffusionScore, pixelScore, compressionScore;
        public int semanticScore, suspiciousRegionScore;
        public boolean visualModelAvailable, provenanceVerified, watermarkVerified;
        public int availableMlModels;
        public int likelyAiThreshold = 85;
    }

    public static final class Output {
        public final String status, mediaType, summary, confidenceLevel;
        public final int confidence, aiProbability, realProbability, forensicScore;
        public final List<String> topAiReasons, topRealReasons;
        Output(String status, String mediaType, int aiProbability, int forensicScore, String summary,
               List<String> ai, List<String> real) {
            this.status = status; this.mediaType = mediaType; this.aiProbability = clamp(aiProbability);
            this.realProbability = 100 - this.aiProbability; this.confidence = this.aiProbability;
            this.forensicScore = clamp(forensicScore); this.summary = summary;
            this.confidenceLevel = level(this.aiProbability);
            this.topAiReasons = Collections.unmodifiableList(ai);
            this.topRealReasons = Collections.unmodifiableList(real);
        }
    }

    private static final class Reason {
        final int strength; final String text;
        Reason(int strength, String text) { this.strength = strength; this.text = text; }
    }

    private ImageEvidenceFusion() { }

    public static Output fuse(Input in) {
        boolean screenshot = in.screenshotScore >= 55 || MediaSourceEvidence.SCREENSHOT.equals(in.sourceType);
        boolean cameraOrigin = MediaSourceEvidence.CAMERA.equals(in.sourceType);
        int forensic = weightedForensics(in);
        double score = 50;
        if (in.visualModelAvailable) score += (in.visualAiScore - 50) * .42;
        score += in.generatorMetadataScore * .20;
        score += in.provenanceScore * .16;
        score += in.watermarkScore * .16;
        score += Math.max(0, forensic - 20) * .16;
        score += in.semanticScore * .02;
        int realEvidence = clamp(in.cameraScore + in.cameraPipelineScore / 2);
        if (realEvidence >= 45) score -= (realEvidence - 35) * .25;
        score = Math.max(0, Math.min(100, score));

        int aiGroups = 0;
        if (in.visualModelAvailable && in.visualAiScore >= 65) aiGroups++;
        if (in.generatorMetadataScore >= 70) aiGroups++;
        if (in.provenanceScore >= 60) aiGroups++;
        if (in.watermarkScore >= 60) aiGroups++;
        if (forensic >= 55) aiGroups++;
        boolean exceptional = in.provenanceVerified && in.provenanceScore >= 90
                || in.watermarkVerified && in.watermarkScore >= 90;
        boolean conflict = realEvidence >= 70 && (aiGroups >= 2 || in.visualAiScore >= 80);

        int probability = (int) Math.round(score);
        if (!exceptional) probability = Math.min(94, probability);
        if (aiGroups < 2 && !exceptional) probability = Math.min(79, probability);
        if (conflict && !exceptional) probability = Math.max(41, Math.min(59, probability));

        String status;
        if (probability <= 40) status = AnalysisStatus.LIKELY_REAL;
        else if (probability <= 59) status = AnalysisStatus.UNKNOWN;
        else if (probability < Math.max(80, in.likelyAiThreshold)) status = AnalysisStatus.POSSIBLY_AI;
        else status = AnalysisStatus.LIKELY_AI;

        String mediaType;
        if (screenshot) mediaType = probability >= 60 ? "Screenshot containing suspected AI-generated content" : "Screenshot";
        else if (in.editingScore >= 55 && probability >= 60) mediaType = "AI Edited Photo";
        else if (in.editingScore >= 30) mediaType = "Real Photo Edited";
        else if (AnalysisStatus.LIKELY_AI.equals(status)) mediaType = "Fully AI Generated";
        else if (cameraOrigin && realEvidence >= 65 && probability <= 40) mediaType = "Real Camera Photo";
        else mediaType = "Unknown / Insufficient Evidence";

        List<Reason> ai = new ArrayList<>(), real = new ArrayList<>();
        add(ai, in.provenanceScore, "Content Credentials/provenance supports AI generation or editing");
        add(ai, in.watermarkScore, "Embedded watermark evidence supports AI generation");
        add(ai, in.generatorMetadataScore, "Creator metadata names an AI generator");
        if (in.visualModelAvailable) add(ai, in.visualAiScore, "On-device visual model AI probability is " + in.visualAiScore + "%");
        add(ai, forensic, "Combined noise, frequency, pixel and compression forensics support AI at " + forensic + "%");
        add(ai, in.suspiciousRegionScore, "A local image region is more suspicious than the global image");
        add(real, realEvidence, "Camera metadata and capture-pipeline evidence support a real photograph");
        if (in.visualModelAvailable) add(real, 100 - in.visualAiScore, "On-device visual model supports real at " + (100 - in.visualAiScore) + "%");
        if (in.generatorMetadataScore == 0) add(real, 1, "No explicit generator metadata was found (weak, neutral evidence)");
        List<String> topAi = top(ai), topReal = top(real);

        String summary;
        if (conflict) summary = "Strong AI and real-camera evidence conflict, so the result remains uncertain";
        else if (aiGroups < 2 && probability >= 60) summary = "Some AI evidence exists, but independent corroboration is insufficient for a high-confidence label";
        else if (AnalysisStatus.LIKELY_AI.equals(status)) summary = "Multiple independent evidence groups support AI generation";
        else if (AnalysisStatus.LIKELY_REAL.equals(status)) summary = "AI evidence is low and independent real-camera evidence is present";
        else summary = "Available evidence is incomplete or too ambiguous to verify origin";
        return new Output(status, mediaType, probability, forensic, summary, topAi, topReal);
    }

    private static int weightedForensics(Input in) {
        return clamp((int) Math.round(in.noiseScore * .23 + in.frequencyScore * .22 + in.ganScore * .13
                + in.diffusionScore * .18 + in.pixelScore * .16 + in.compressionScore * .08));
    }

    private static void add(List<Reason> reasons, int strength, String text) {
        if (strength > 0) reasons.add(new Reason(strength, text));
    }

    private static List<String> top(List<Reason> reasons) {
        reasons.sort(Comparator.comparingInt((Reason r) -> r.strength).reversed());
        List<String> values = new ArrayList<>();
        for (int i = 0; i < Math.min(3, reasons.size()); i++) values.add(reasons.get(i).text);
        if (values.isEmpty()) values.add("No reliable supporting evidence");
        return values;
    }

    private static String level(int probability) {
        if (probability <= 20) return "Likely Real";
        if (probability <= 40) return "Probably Real";
        if (probability <= 59) return "Uncertain";
        if (probability <= 79) return "Likely AI Generated";
        if (probability <= 94) return "Highly Likely AI Generated";
        return "Exceptionally strong verified evidence";
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
}
