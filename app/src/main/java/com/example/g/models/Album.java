package com.example.g.models;

import android.net.Uri;
import java.util.Objects;

public final class Album {
    public final String id;
    public final String name;
    public final String path;
    public final Uri coverUri;
    public final int count;
    public final long latestDate;

    public Album(String id, String name, String path, Uri coverUri, int count, long latestDate) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.coverUri = coverUri;
        this.count = count;
        this.latestDate = latestDate;
    }

    @Override public boolean equals(Object object) {
        if (!(object instanceof Album)) return false;
        Album other = (Album) object;
        return count == other.count && latestDate == other.latestDate
                && Objects.equals(id, other.id) && Objects.equals(coverUri, other.coverUri);
    }

    @Override public int hashCode() { return Objects.hash(id, count, latestDate); }
}
