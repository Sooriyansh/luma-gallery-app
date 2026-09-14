package com.example.g.core.compatibility;

import android.os.Build;
import androidx.annotation.ChecksSdkIntAtLeast;

/** The single source of truth for Android-version feature gates. */
public final class AndroidCompatibility {
    private AndroidCompatibility() { }

    @ChecksSdkIntAtLeast(api = 29)
    public static boolean usesScopedMediaCollection() {
        return CompatibilityPolicy.usesScopedMediaCollection(Build.VERSION.SDK_INT);
    }

    @ChecksSdkIntAtLeast(api = 30)
    public static boolean supportsMediaTrash() {
        return CompatibilityPolicy.supportsMediaTrash(Build.VERSION.SDK_INT);
    }

    @ChecksSdkIntAtLeast(api = 33)
    public static boolean usesGranularMediaPermissions() {
        return CompatibilityPolicy.usesGranularMediaPermissions(Build.VERSION.SDK_INT);
    }

    @ChecksSdkIntAtLeast(api = 33)
    public static boolean supportsNotificationPermission() {
        return CompatibilityPolicy.supportsNotificationPermission(Build.VERSION.SDK_INT);
    }

    @ChecksSdkIntAtLeast(api = 34)
    public static boolean supportsSelectedVisualMedia() {
        return CompatibilityPolicy.supportsSelectedVisualMedia(Build.VERSION.SDK_INT);
    }

    @ChecksSdkIntAtLeast(api = 26)
    public static boolean supportsNotificationChannels() {
        return CompatibilityPolicy.supportsNotificationChannels(Build.VERSION.SDK_INT);
    }

    @ChecksSdkIntAtLeast(api = 30)
    public static boolean supportsMediaOwnerMetadata() {
        return CompatibilityPolicy.supportsMediaOwnerMetadata(Build.VERSION.SDK_INT);
    }
}
