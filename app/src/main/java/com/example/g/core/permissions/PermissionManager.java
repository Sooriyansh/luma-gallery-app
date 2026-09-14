package com.example.g.core.permissions;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import com.example.g.core.compatibility.AndroidCompatibility;

/** Central permission policy. Notifications are intentionally not coupled to gallery access. */
public final class PermissionManager {
    private static final String PREFS = "permission_state";
    private static final String MEDIA_REQUESTED = "media_requested";

    private PermissionManager() { }

    public static String[] mediaPermissions() {
        if (AndroidCompatibility.usesGranularMediaPermissions()) {
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    public static MediaAccess mediaAccess(Context context) {
        if (!AndroidCompatibility.usesGranularMediaPermissions()) {
            return granted(context, Manifest.permission.READ_EXTERNAL_STORAGE) ? MediaAccess.FULL : MediaAccess.DENIED;
        }
        boolean images = granted(context, Manifest.permission.READ_MEDIA_IMAGES);
        boolean videos = granted(context, Manifest.permission.READ_MEDIA_VIDEO);
        if (images && videos) return MediaAccess.FULL;
        if (images) return MediaAccess.IMAGES_ONLY;
        if (videos) return MediaAccess.VIDEOS_ONLY;
        if (AndroidCompatibility.supportsSelectedVisualMedia()
                && granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) return MediaAccess.SELECTED;
        return MediaAccess.DENIED;
    }

    public static boolean hasAnyMediaAccess(Context context) {
        return mediaAccess(context).canReadAnything();
    }

    public static void markMediaPermissionRequested(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(MEDIA_REQUESTED, true).apply();
    }

    public static PermissionState mediaPermissionState(Activity activity) {
        if (hasAnyMediaAccess(activity)) return PermissionState.GRANTED;
        boolean requested = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(MEDIA_REQUESTED, false);
        if (!requested) return PermissionState.DENIED;
        for (String permission : mediaPermissions()) {
            if (activity.shouldShowRequestPermissionRationale(permission)) return PermissionState.DENIED;
        }
        return PermissionState.PERMANENTLY_DENIED;
    }

    public static PermissionState notificationPermissionState(Context context) {
        if (!AndroidCompatibility.supportsNotificationPermission()) return PermissionState.UNAVAILABLE;
        return granted(context, Manifest.permission.POST_NOTIFICATIONS)
                ? PermissionState.GRANTED : PermissionState.DENIED;
    }

    private static boolean granted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }
}
