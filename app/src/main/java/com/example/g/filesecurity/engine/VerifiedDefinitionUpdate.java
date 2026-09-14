package com.example.g.filesecurity.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies a bounded, detached SHA256withRSA signed UTF-8 package against a caller-pinned key.
 * There is deliberately no vendor key, remote endpoint or automatic network request configured.
 * Wire format (LF only): G-FILE-SECURITY-1, revision, issuedEpochMillis, version, then entries.
 * Entry: HASH&lt;TAB&gt;lowercase SHA256&lt;TAB&gt;label or BYTE&lt;TAB&gt;hex bytes&lt;TAB&gt;label.
 * Persist the accepted revision with the installed snapshot to enforce rollback protection.
 */
public final class VerifiedDefinitionUpdate {
    private VerifiedDefinitionUpdate() { }
    public static SignatureDatabase verify(byte[] payload, byte[] detachedSignature,
            PublicKey pinnedKey, long minimumRevision, long nowMillis)
            throws GeneralSecurityException, IOException {
        if (payload == null || payload.length == 0 || payload.length > 1024 * 1024
                || detachedSignature == null || detachedSignature.length > 1024)
            throw new IOException("Definition package exceeds limits");
        if (!(pinnedKey instanceof RSAKey) || ((RSAKey) pinnedKey).getModulus().bitLength() < 2048)
            throw new GeneralSecurityException("A pinned RSA key of at least 2048 bits is required");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(pinnedKey);
        verifier.update(payload);
        if (!verifier.verify(detachedSignature)) throw new GeneralSecurityException("Invalid definition signature");
        String content = new String(payload, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(payload, content.getBytes(StandardCharsets.UTF_8)) || content.indexOf('\r') >= 0)
            throw new IOException("Invalid definition encoding");
        String[] lines = content.split("\n", -1);
        if (lines.length < 5 || !"G-FILE-SECURITY-1".equals(lines[0])) throw new IOException("Unknown definitions format");
        long revision = positiveLong(lines[1]), issued = positiveLong(lines[2]);
        if (revision <= minimumRevision) throw new IOException("Definition rollback or duplicate revision rejected");
        if (issued > nowMillis + 24L * 60 * 60 * 1000 || nowMillis - issued > 366L * 24 * 60 * 60 * 1000)
            throw new IOException("Definition issuance date is stale or in the future");
        String version = lines[3];
        if (!version.matches("[A-Za-z0-9._-]{1,64}")) throw new IOException("Invalid definition version");
        Map<String, String> hashes = new LinkedHashMap<>();
        List<SignatureDatabase.ByteSignature> patterns = new ArrayList<>();
        for (int i = 4; i < lines.length; i++) {
            if (i == lines.length - 1 && lines[i].isEmpty()) continue;
            String[] entry = lines[i].split("\t", -1);
            if (entry.length != 3 || !entry[2].matches("[A-Za-z0-9 ._()/-]{1,100}"))
                throw new IOException("Invalid definition record");
            if ("HASH".equals(entry[0])) {
                if (!entry[1].matches("[a-f0-9]{64}") || hashes.put(entry[1], entry[2]) != null)
                    throw new IOException("Invalid or duplicate hash");
                if (hashes.size() > 5000) throw new IOException("Too many hash definitions");
            } else if ("BYTE".equals(entry[0])) {
                if (!entry[1].matches("[a-f0-9]{32,1024}") || (entry[1].length() & 1) != 0)
                    throw new IOException("Byte definitions must be 16 to 512 bytes");
                byte[] bytes = new byte[entry[1].length() / 2];
                for (int j = 0; j < bytes.length; j++) bytes[j] = (byte) Integer.parseInt(entry[1].substring(j * 2, j * 2 + 2), 16);
                patterns.add(new SignatureDatabase.ByteSignature(entry[2], bytes, false));
                if (patterns.size() > 128) throw new IOException("Too many byte definitions");
            } else throw new IOException("Unsupported definition record");
        }
        if (hashes.isEmpty() && patterns.isEmpty()) throw new IOException("Empty production definitions");
        return SignatureDatabase.verified(version, revision, issued, hashes, patterns);
    }
    private static long positiveLong(String input) throws IOException {
        try { long value = Long.parseLong(input); if (value > 0) return value; }
        catch (NumberFormatException ignored) { }
        throw new IOException("Invalid definition counter/date");
    }
}
