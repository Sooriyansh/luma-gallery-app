package com.example.g.filesecurity.engine;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable snapshot. Production entries can only originate from verified signed definitions. */
public final class SignatureDatabase {
    public static final String BUILTIN_VERSION = "builtin-test-1";
    final Map<String, String> hashes;
    final List<ByteSignature> patterns;
    public final String version;
    public final long revision, lastUpdated;
    public final boolean hasProductionDefinitions;

    private SignatureDatabase(String version, long revision, long lastUpdated,
            Map<String, String> hashes, List<ByteSignature> patterns, boolean production) {
        this.version = version;
        this.revision = revision;
        this.lastUpdated = lastUpdated;
        this.hashes = Collections.unmodifiableMap(new LinkedHashMap<>(hashes));
        this.patterns = Collections.unmodifiableList(new ArrayList<>(patterns));
        this.hasProductionDefinitions = production;
    }
    public static SignatureDatabase builtin() {
        List<ByteSignature> tests = new ArrayList<>();
        // The standard harmless EICAR antivirus test string; this is NOT a malware definition.
        tests.add(new ByteSignature("Harmless EICAR antivirus test", ("X5O!P%@AP[4\\PZX54(P^)7CC)7}"
                + "$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*").getBytes(StandardCharsets.US_ASCII), true));
        return new SignatureDatabase(BUILTIN_VERSION, 0, 0, Collections.emptyMap(), tests, false);
    }
    static SignatureDatabase verified(String version, long revision, long updated,
            Map<String, String> hashes, List<ByteSignature> patterns) {
        List<ByteSignature> all = new ArrayList<>(patterns);
        all.addAll(builtin().patterns);
        return new SignatureDatabase(version, revision, updated, hashes, all, true);
    }
    public int numberOfSignatures() { return hashes.size() + patterns.size(); }
    public int numberOfProductionSignatures() {
        int count = hashes.size();
        for (ByteSignature s : patterns) if (!s.testOnly) count++;
        return count;
    }
    static final class ByteSignature {
        final String name;
        final byte[] bytes;
        final boolean testOnly;
        ByteSignature(String name, byte[] bytes, boolean testOnly) {
            this.name = name;
            this.bytes = bytes.clone();
            this.testOnly = testOnly;
        }
    }
}
