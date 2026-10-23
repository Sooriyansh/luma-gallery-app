package com.example.g.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Stores a verifier only. Despite its legacy name this accepts Unicode secret codes, not just PINs. */
public final class EyePinStore {
    private static final String PREF = "eye_security", SALT = "salt", HASH = "hash";
    private static final String FAILURES = "failures", RETRY_AFTER = "retry_after";
    private EyePinStore() { }

    public static boolean hasPin(Context context) { return prefs(context).contains(HASH); }

    public static void save(Context context, char[] code) {
        byte[] salt = new byte[32];
        byte[] hash = null;
        try {
            new SecureRandom().nextBytes(salt);
            hash = derive(code, salt);
            prefs(context).edit().putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                    .remove(FAILURES).remove(RETRY_AFTER).apply();
        } finally {
            Arrays.fill(code, '\0');
            Arrays.fill(salt, (byte) 0);
            if (hash != null) Arrays.fill(hash, (byte) 0);
        }
    }

    public static boolean verify(Context context, char[] code) {
        SharedPreferences prefs = prefs(context);
        String salt = prefs.getString(SALT, null), hash = prefs.getString(HASH, null);
        byte[] expected = null;
        byte[] saltBytes = null;
        byte[] actual = null;
        try {
            if (salt == null || hash == null) return false;
            saltBytes = Base64.decode(salt, Base64.NO_WRAP);
            expected = Base64.decode(hash, Base64.NO_WRAP);
            actual = derive(code, saltBytes);
            return java.security.MessageDigest.isEqual(expected, actual);
        } finally {
            Arrays.fill(code, '\0');
            if (saltBytes != null) Arrays.fill(saltBytes, (byte) 0);
            if (expected != null) Arrays.fill(expected, (byte) 0);
            if (actual != null) Arrays.fill(actual, (byte) 0);
        }
    }

    public static boolean canAttempt(Context context) { return System.currentTimeMillis() >= prefs(context).getLong(RETRY_AFTER, 0); }
    public static long retryAfterMillis(Context context) { return Math.max(0, prefs(context).getLong(RETRY_AFTER, 0) - System.currentTimeMillis()); }
    public static void recordSuccess(Context context) { prefs(context).edit().remove(FAILURES).remove(RETRY_AFTER).apply(); }
    public static void recordFailure(Context context) {
        SharedPreferences prefs = prefs(context); int failures = prefs.getInt(FAILURES, 0) + 1;
        SharedPreferences.Editor edit = prefs.edit().putInt(FAILURES, failures);
        if (failures >= 5) edit.putLong(RETRY_AFTER, System.currentTimeMillis() + Math.min(5 * 60_000L, (failures - 4L) * 30_000L));
        edit.apply();
    }
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    private static byte[] derive(char[] code, byte[] salt) { try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(code, salt, 210000, 256)).getEncoded(); } catch (Exception e) { throw new IllegalStateException(e); } }
}
