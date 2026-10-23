package com.example.g.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Persists only a salted PBKDF2 verifier for the app lock. */
public final class SecurePinStore {
    private static final String PREF = "security", SALT = "pin_salt", HASH = "pin_hash";
    private static final int ITERATIONS = 210_000, KEY_BITS = 256;

    private SecurePinStore() { }

    public static boolean hasPin(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).contains(HASH);
    }

    public static void save(Context context, char[] pin) {
        byte[] salt = new byte[32];
        byte[] hash = null;
        try {
            new SecureRandom().nextBytes(salt);
            hash = derive(pin, salt);
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                    .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                    .apply();
        } finally {
            Arrays.fill(pin, '\0');
            Arrays.fill(salt, (byte) 0);
            if (hash != null) Arrays.fill(hash, (byte) 0);
        }
    }

    public static boolean verify(Context context, char[] pin) {
        byte[] expected = null;
        byte[] actual = null;
        byte[] salt = null;
        try {
            SharedPreferences preferences = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String encodedSalt = preferences.getString(SALT, null);
            String encodedHash = preferences.getString(HASH, null);
            if (encodedSalt == null || encodedHash == null) return false;
            salt = Base64.decode(encodedSalt, Base64.NO_WRAP);
            expected = Base64.decode(encodedHash, Base64.NO_WRAP);
            actual = derive(pin, salt);
            return java.security.MessageDigest.isEqual(expected, actual);
        } finally {
            Arrays.fill(pin, '\0');
            if (salt != null) Arrays.fill(salt, (byte) 0);
            if (expected != null) Arrays.fill(expected, (byte) 0);
            if (actual != null) Arrays.fill(actual, (byte) 0);
        }
    }

    private static byte[] derive(char[] pin, byte[] salt) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(pin, salt, ITERATIONS, KEY_BITS)).getEncoded();
        } catch (Exception error) {
            throw new IllegalStateException("Could not derive lock verifier", error);
        }
    }
}
