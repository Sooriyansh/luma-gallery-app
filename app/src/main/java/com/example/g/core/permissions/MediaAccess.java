package com.example.g.core.permissions;

public enum MediaAccess {
    FULL,
    IMAGES_ONLY,
    VIDEOS_ONLY,
    SELECTED,
    DENIED;

    public boolean canReadAnything() { return this != DENIED; }
    public boolean isPartial() { return this == IMAGES_ONLY || this == VIDEOS_ONLY || this == SELECTED; }
}
