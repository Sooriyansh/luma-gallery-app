package com.example.g.analysis;

/** Labels deliberately describe evidence, never a fact about a person or image. */
public final class AnalysisStatus {
    private AnalysisStatus() { }
    public static final String PENDING = "PENDING";
    public static final String UNKNOWN = "UNKNOWN";
    /** Only this status is eligible for a visible badge or notification. */
    public static final String LIKELY_AI = "AI";
    public static final String POSSIBLY_AI = "POSSIBLY_AI";
    public static final String POSSIBLY_EDITED = "POSSIBLY_AI_EDITED";
    public static final String LIKELY_REAL = "REAL";
    public static final String FAILED = "FAILED";
}
