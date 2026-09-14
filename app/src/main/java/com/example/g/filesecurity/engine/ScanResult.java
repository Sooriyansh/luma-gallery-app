package com.example.g.filesecurity.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A local, static analysis report. A negative finding is never a guarantee of safety. */
public final class ScanResult {
    public String fileName = "Unknown file";
    public String actualType = "Unknown";
    public String actualMime = "application/octet-stream";
    public String sha256 = "";
    public String threatLevel = "SCAN_FAILED";
    public String confidence = "Unrated: static heuristics are not probability calibrated";
    public String signatureVersion = SignatureDatabase.BUILTIN_VERSION;
    public String scannerVersion = SignatureScanner.VERSION;
    public boolean complete;
    public long bytesScanned;
    public long elapsedMs;
    public int filesScanned;
    public final List<String> reasons = new ArrayList<>();
    public final List<String> limitations = new ArrayList<>();
    public final Map<String, Integer> scores = new LinkedHashMap<>();
    public final List<ScanResult> children = new ArrayList<>();
    public ScanResult() {
        for (String layer : new String[]{"Known Malware Signature", "Hash Reputation", "File Structure",
                "Embedded Content", "Document Macro", "Script", "Archive", "APK", "MIME Mismatch",
                "Known Threat Intelligence"}) scores.put(layer, 0);
    }
    void evidence(String layer, int score, String reason) {
        scores.put(layer, Math.max(scores.getOrDefault(layer, 0), score));
        if (!reasons.contains(reason)) reasons.add(reason);
    }
    void limitation(String reason) {
        if (!limitations.contains(reason)) limitations.add(reason);
    }
}
