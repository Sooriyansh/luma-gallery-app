package com.example.g.security;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM vault cipher backed by the Android Keystore.
 *
 * <p>Format: [1-byte IV length][IV bytes][GCM-encrypted ciphertext + 16-byte tag]
 *
 * <p>The caller retains ownership of both {@code in} and {@code out} streams.
 * This class does NOT close the caller's streams — it only closes the internal
 * {@link CipherInputStream} wrapper it creates internally.
 */
public final class VaultCipher {
    private static final String ALIAS = "luma_vault_aes";

    private VaultCipher() { }

    private static SecretKey key() throws Exception {
        KeyStore k = KeyStore.getInstance("AndroidKeyStore");
        k.load(null);
        if (!k.containsAlias(ALIAS)) {
            KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            g.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) k.getEntry(ALIAS, null)).getSecretKey();
    }

    public static void encrypt(InputStream in, OutputStream out) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV();
        out.write(iv.length);
        out.write(iv);
        // Wrap only the input stream; out is the caller's stream and must not be closed here.
        try (CipherInputStream cis = new CipherInputStream(in, c)) {
            pipe(cis, out);
        }
    }

    public static void decrypt(InputStream in, OutputStream out) throws Exception {
        int n = in.read();
        if (n < 12 || n > 32) throw new IOException("Invalid vault file");
        byte[] iv = new byte[n];
        readFully(in, iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        // Wrap only the input stream; out is the caller's stream and must not be closed here.
        try (CipherInputStream cis = new CipherInputStream(in, c)) {
            pipe(cis, out);
        }
    }

    /**
     * Copies all bytes from {@code in} to {@code out}.
     * Neither stream is closed by this method.
     */
    private static void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[65536];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    /** InputStream.read is allowed to return fewer bytes than requested. */
    private static void readFully(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = in.read(buffer, offset, buffer.length - offset);
            if (read < 0) throw new EOFException();
            offset += read;
        }
    }
}
