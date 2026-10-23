package com.example.g.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.Collections;
import org.junit.Test;

public class VaultStoreTest {
    @Test public void resolvesCurrentOpaqueVaultNameFromPreviewId() throws Exception {
        File vault = java.nio.file.Files.createTempDirectory("luma-vault-test").toFile();
        File encrypted = new File(vault, "7b7d6f2a-6d1f-4bb2-84e4-d2edb4aa7a2e.luma");
        org.junit.Assert.assertTrue(encrypted.createNewFile());
        File preview = new File("cache", "7b7d6f2a-6d1f-4bb2-84e4-d2edb4aa7a2e__holiday.jpg");

        try {
            assertEquals(encrypted, VaultStore.encryptedForPreview(vault, Collections.singletonList(encrypted), preview));
        } finally {
            encrypted.delete();
            vault.delete();
        }
    }

    @Test public void refusesPreviewWithoutVaultIdentifier() {
        assertNull(VaultStore.encryptedForPreview(new File("vault"), Collections.emptyList(), new File("cache", "photo.jpg")));
    }

    @Test public void resolvesLegacyDisplayNameVaultEntry() {
        File vault = new File("vault");
        File legacy = new File(vault, "legacy-id__holiday.jpg.luma");
        File preview = new File("cache", "legacy-id__holiday.jpg");

        assertEquals(legacy, VaultStore.encryptedForPreview(vault, Collections.singletonList(legacy), preview));
    }
}
