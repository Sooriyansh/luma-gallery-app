package com.example.g.filesecurity.engine;

/** Per-request limits shared by every member of an archive tree. No extraction to disk occurs. */
public final class ScanLimits {
    public final long maxInputBytes, maxExpandedBytes, maxEntryBytes, maxElapsedMs;
    public final int maxEntries, maxDepth, sampleBytes, maxCompressionRatio;
    public ScanLimits() {
        this(512L * 1024 * 1024, 64L * 1024 * 1024, 16L * 1024 * 1024,
                30_000, 300, 3, 2 * 1024 * 1024, 200);
    }
    public ScanLimits(long maxInputBytes, long maxExpandedBytes, long maxEntryBytes,
            long maxElapsedMs, int maxEntries, int maxDepth, int sampleBytes, int maxCompressionRatio) {
        if (maxInputBytes <= 0 || maxExpandedBytes <= 0 || maxEntryBytes <= 0 || maxElapsedMs <= 0
                || maxEntries <= 0 || maxDepth < 0 || sampleBytes < 512 || maxCompressionRatio < 1
                || sampleBytes > 8 * 1024 * 1024 || maxEntryBytes > 32L * 1024 * 1024
                || maxEntries > 1000 || maxDepth > 5)
            throw new IllegalArgumentException("Unsafe scanner resource limits");
        this.maxInputBytes = maxInputBytes;
        this.maxExpandedBytes = maxExpandedBytes;
        this.maxEntryBytes = maxEntryBytes;
        this.maxElapsedMs = maxElapsedMs;
        this.maxEntries = maxEntries;
        this.maxDepth = maxDepth;
        this.sampleBytes = sampleBytes;
        this.maxCompressionRatio = maxCompressionRatio;
    }
}
