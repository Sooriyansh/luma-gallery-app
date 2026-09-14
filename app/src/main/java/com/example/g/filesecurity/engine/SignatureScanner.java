package com.example.g.filesecurity.engine;

import java.util.ArrayList;
import java.util.List;

/** Streaming KMP matching carries state across chunk boundaries without retaining the file. */
final class SignatureScanner {
    static final String VERSION = "1.0";
    private final List<Matcher> matchers = new ArrayList<>();
    SignatureScanner(SignatureDatabase db) {
        for (SignatureDatabase.ByteSignature s : db.patterns) matchers.add(new Matcher(s));
    }
    void update(byte[] buffer, int length, ScanResult result) {
        for (Matcher m : matchers) {
            if (m.found) continue;
            for (int i = 0; i < length; i++) {
                while (m.state > 0 && buffer[i] != m.signature.bytes[m.state]) m.state = m.prefix[m.state - 1];
                if (buffer[i] == m.signature.bytes[m.state]) m.state++;
                if (m.state == m.signature.bytes.length) {
                    m.found = true;
                    if (m.signature.testOnly) result.evidence("Embedded Content", 60,
                            "Harmless EICAR antivirus test signature detected; this test file is not a real virus");
                    else result.evidence("Known Malware Signature", 100, "Trusted byte signature match: " + m.signature.name);
                    break;
                }
            }
        }
    }
    private static final class Matcher {
        final SignatureDatabase.ByteSignature signature;
        final int[] prefix;
        int state;
        boolean found;
        Matcher(SignatureDatabase.ByteSignature signature) {
            this.signature = signature;
            prefix = new int[signature.bytes.length];
            for (int i = 1, j = 0; i < prefix.length; i++) {
                while (j > 0 && signature.bytes[i] != signature.bytes[j]) j = prefix[j - 1];
                if (signature.bytes[i] == signature.bytes[j]) j++;
                prefix[i] = j;
            }
        }
    }
}
