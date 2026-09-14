package com.example.g.filesecurity;

import com.example.g.filesecurity.engine.ScanResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Iterator;

/** Bounded engine results can cross the isolated-process boundary without parceling parsers. */
public final class ScanResultJson {
    private ScanResultJson() { }
    public static String encode(ScanResult result) {
        JSONObject json = new JSONObject();
        try {
            json.put("fileName", result.fileName).put("actualType", result.actualType).put("actualMime", result.actualMime)
                    .put("sha256", result.sha256).put("threatLevel", result.threatLevel).put("confidence", result.confidence)
                    .put("signatureVersion", result.signatureVersion).put("scannerVersion", result.scannerVersion)
                    .put("complete", result.complete).put("bytesScanned", result.bytesScanned).put("elapsedMs", result.elapsedMs)
                    .put("filesScanned", result.filesScanned).put("reasons", new JSONArray(result.reasons))
                    .put("limitations", new JSONArray(result.limitations)).put("scores", new JSONObject(result.scores));
        } catch (JSONException invalid) { throw new IllegalStateException("Invalid scanner result", invalid); }
        return json.toString();
    }
    public static ScanResult decode(String encoded) throws JSONException {
        JSONObject json = new JSONObject(encoded);
        ScanResult result = new ScanResult();
        result.fileName = json.optString("fileName"); result.actualType = json.optString("actualType", "Unknown");
        result.actualMime = json.optString("actualMime", "application/octet-stream"); result.sha256 = json.optString("sha256");
        result.threatLevel = json.optString("threatLevel", "SCAN_FAILED"); result.confidence = json.optString("confidence", "Unavailable");
        result.signatureVersion = json.optString("signatureVersion"); result.scannerVersion = json.optString("scannerVersion");
        result.complete = json.optBoolean("complete", false); result.bytesScanned = json.optLong("bytesScanned");
        result.elapsedMs = json.optLong("elapsedMs"); result.filesScanned = json.optInt("filesScanned", 1);
        JSONArray reasons = json.optJSONArray("reasons"), limitations = json.optJSONArray("limitations");
        if (reasons != null) for (int i = 0; i < reasons.length(); i++) result.reasons.add(reasons.optString(i));
        if (limitations != null) for (int i = 0; i < limitations.length(); i++) result.limitations.add(limitations.optString(i));
        JSONObject scores = json.optJSONObject("scores");
        if (scores != null) for (Iterator<String> iterator = scores.keys(); iterator.hasNext();) {
            String key = iterator.next(); result.scores.put(key, scores.optInt(key));
        }
        return result;
    }
    public static ScanResult failure(String name, String message) {
        ScanResult result = new ScanResult(); result.fileName = name; result.threatLevel = "SCAN_FAILED";
        result.confidence = "Unavailable"; result.complete = false; result.filesScanned = 0;
        result.reasons.add(message); result.limitations.add("No safety conclusion is available for this file.");
        return result;
    }
}
