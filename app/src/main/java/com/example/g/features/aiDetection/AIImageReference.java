package com.example.g.features.aiDetection;

import android.net.Uri;
import com.example.g.models.MediaItem;

/** Immutable media identity passed across the Gallery -> AI Detection boundary. */
public final class AIImageReference {
    public final String imageId;
    public final Uri uri;
    public final String name;
    public final String mimeType;
    public final long size;
    public final long modified;

    public AIImageReference(String imageId, Uri uri, String name, String mimeType, long size, long modified) {
        this.imageId = imageId;
        this.uri = uri;
        this.name = name;
        this.mimeType = mimeType;
        this.size = size;
        this.modified = modified;
    }

    public static AIImageReference from(MediaItem item) {
        return new AIImageReference(item.uri.toString(), item.uri, item.name, item.mime,
                item.size, item.dateModified);
    }
}
