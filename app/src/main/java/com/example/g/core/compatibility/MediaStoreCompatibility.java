package com.example.g.core.compatibility;

import android.net.Uri;
import android.provider.MediaStore;

/** Version-safe MediaStore collections and columns used by repositories and workers. */
public final class MediaStoreCompatibility {
    private MediaStoreCompatibility() { }

    public static Uri imagesCollection() {
        return AndroidCompatibility.usesScopedMediaCollection()
                ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    }

    public static Uri videosCollection() {
        return AndroidCompatibility.usesScopedMediaCollection()
                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
    }

    public static boolean hasRelativePath() {
        return AndroidCompatibility.usesScopedMediaCollection();
    }
}
