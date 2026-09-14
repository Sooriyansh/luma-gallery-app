package com.example.g.core.compatibility;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompatibilityPolicyTest {
    @Test public void scopedStorageStartsAtApi29() {
        assertFalse(CompatibilityPolicy.usesScopedMediaCollection(28));
        assertTrue(CompatibilityPolicy.usesScopedMediaCollection(29));
    }

    @Test public void mediaTrashStartsAtApi30() {
        assertFalse(CompatibilityPolicy.supportsMediaTrash(29));
        assertTrue(CompatibilityPolicy.supportsMediaTrash(30));
    }

    @Test public void granularAndSelectedPermissionsUseCorrectApiLevels() {
        assertFalse(CompatibilityPolicy.usesGranularMediaPermissions(32));
        assertTrue(CompatibilityPolicy.usesGranularMediaPermissions(33));
        assertFalse(CompatibilityPolicy.supportsSelectedVisualMedia(33));
        assertTrue(CompatibilityPolicy.supportsSelectedVisualMedia(34));
    }

    @Test public void notificationAndOwnerMetadataGatesMatchPlatform() {
        assertFalse(CompatibilityPolicy.supportsNotificationChannels(25));
        assertTrue(CompatibilityPolicy.supportsNotificationChannels(26));
        assertFalse(CompatibilityPolicy.supportsNotificationPermission(32));
        assertTrue(CompatibilityPolicy.supportsNotificationPermission(33));
        assertFalse(CompatibilityPolicy.supportsMediaOwnerMetadata(29));
        assertTrue(CompatibilityPolicy.supportsMediaOwnerMetadata(30));
    }
}
