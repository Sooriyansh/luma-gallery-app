package com.example.g.filesecurity.engine;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashScanner {
    private HashScanner() { }
    public static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    public static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            out[2 * i] = alphabet[(bytes[i] >>> 4) & 15];
            out[2 * i + 1] = alphabet[bytes[i] & 15];
        }
        return new String(out);
    }
}
