package com.example.g.core.compatibility;

/** Pure API-level policy kept separate so compatibility decisions can be unit tested. */
public final class CompatibilityPolicy {
    private CompatibilityPolicy() { }

    public static boolean usesScopedMediaCollection(int sdk) { return sdk >= 29; }
    public static boolean supportsMediaTrash(int sdk) { return sdk >= 30; }
    public static boolean usesGranularMediaPermissions(int sdk) { return sdk >= 33; }
    public static boolean supportsNotificationPermission(int sdk) { return sdk >= 33; }
    public static boolean supportsSelectedVisualMedia(int sdk) { return sdk >= 34; }
    public static boolean supportsNotificationChannels(int sdk) { return sdk >= 26; }
    public static boolean supportsMediaOwnerMetadata(int sdk) { return sdk >= 30; }
}
