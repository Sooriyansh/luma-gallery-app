package com.example.g.analysis;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "media_analysis")
public class MediaAnalysisEntity {
    @PrimaryKey @NonNull public String uri = "";
    public String fileName = "";
    public String mimeType = "";
    public long modified;
    public String status = AnalysisStatus.PENDING;
    public int confidence;
    public String source = "";
    public String reason = "";
    public String metadata = "";
    public long scannedAt;
    /** Cache identity and the independent evidence scores (all 0..100). */
    public long mediaStoreId;
    public String fileHash = "";
    public long createdAt;
    public int modelConfidence;
    public int metadataScore;
    public int artifactScore;
    public int provenanceScore;
    public String detectedGenerator = "";
    public String modelVersion = "local-forensics-1";
    /** Capture/source evidence is intentionally separate from the AI classification. */
    public String mediaType = "";
    public String sourceType = "";
    public int cameraEvidence;
    public int screenshotConfidence;
    public boolean visualModelAvailable;
    public String provenanceStatus = "";
    @ColumnInfo(defaultValue = "0") public int aiProbability;
    @ColumnInfo(defaultValue = "100") public int realProbability = 100;
    @ColumnInfo(defaultValue = "0") public int watermarkScore;
    @ColumnInfo(defaultValue = "0") public int noiseScore;
    @ColumnInfo(defaultValue = "0") public int frequencyScore;
    @ColumnInfo(defaultValue = "0") public int ganScore;
    @ColumnInfo(defaultValue = "0") public int diffusionScore;
    @ColumnInfo(defaultValue = "0") public int pixelScore;
    @ColumnInfo(defaultValue = "0") public int compressionScore;
    @ColumnInfo(defaultValue = "0") public int semanticScore;
    @ColumnInfo(defaultValue = "0") public int suspiciousRegionScore;
    @ColumnInfo(defaultValue = "''") public String confidenceLevel = "";
    @ColumnInfo(defaultValue = "''") public String watermarkStatus = "";
}
