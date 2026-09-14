package com.example.g.repositories;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import androidx.documentfile.provider.DocumentFile;
import com.example.g.models.MediaItem;
import com.example.g.core.compatibility.MediaStoreCompatibility;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MediaRepository {
    private static volatile MediaRepository instance;
    private final Context context;
    private final ContentResolver resolver;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private MediaRepository(Context context) {
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
    }

    public static MediaRepository get(Context context) {
        if (instance == null) synchronized (MediaRepository.class) {
            if (instance == null) instance = new MediaRepository(context);
        }
        return instance;
    }

    public interface Callback { void done(List<MediaItem> items, Exception error); }

    public void load(int limit, int offset, Callback callback) {
        io.execute(() -> {
            List<MediaItem> result = new ArrayList<>();
            Exception failure = null;
            try {
                queryImages(result);
                queryVideos(result);
                queryGrantedFolders(result);
                result.sort(Comparator.comparingLong(MediaRepository::sortDate).reversed());
                int from = Math.min(Math.max(0, offset), result.size());
                int to = Math.min(result.size(), from + Math.max(1, limit));
                result = new ArrayList<>(result.subList(from, to));
            } catch (SecurityException exception) {
                failure = new SecurityException("Photo permission is missing or was revoked", exception);
            } catch (Exception exception) {
                failure = exception;
            }
            List<MediaItem> delivered = Collections.unmodifiableList(result);
            Exception deliveredFailure = failure;
            main.post(() -> callback.done(delivered, deliveredFailure));
        });
    }

    private void queryImages(List<MediaItem> output) {
        Uri collection = MediaStoreCompatibility.imagesCollection();
        String[] projection = imageProjection();
        try (Cursor cursor = resolver.query(collection, projection, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cursor == null) return;
            ColumnReader columns = new ColumnReader(cursor);
            while (cursor.moveToNext()) {
                long id = columns.longValue(MediaStore.Images.Media._ID);
                Uri uri = ContentUris.withAppendedId(collection, id);
                output.add(new MediaItem(id, uri,
                        columns.text(MediaStore.Images.Media.DISPLAY_NAME),
                        columns.text(MediaStore.Images.Media.MIME_TYPE),
                        columns.longValue(MediaStore.Images.Media.SIZE),
                        columns.intValue(MediaStore.Images.Media.WIDTH),
                        columns.intValue(MediaStore.Images.Media.HEIGHT), 0,
                        columns.longValue(MediaStore.Images.Media.DATE_TAKEN),
                        columns.longValue(MediaStore.Images.Media.DATE_ADDED),
                        columns.longValue(MediaStore.Images.Media.DATE_MODIFIED),
                        MediaStoreCompatibility.hasRelativePath() ? columns.text(MediaStore.Images.Media.RELATIVE_PATH) : "",
                        columns.text(MediaStore.Images.Media.BUCKET_ID),
                        columns.text(MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
                        columns.intValue(MediaStore.Images.Media.ORIENTATION)));
            }
        }
    }

    private void queryVideos(List<MediaItem> output) {
        Uri collection = MediaStoreCompatibility.videosCollection();
        String[] projection = videoProjection();
        try (Cursor cursor = resolver.query(collection, projection, null, null,
                MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (cursor == null) return;
            ColumnReader columns = new ColumnReader(cursor);
            while (cursor.moveToNext()) {
                long id = columns.longValue(MediaStore.Video.Media._ID);
                output.add(new MediaItem(id, ContentUris.withAppendedId(collection, id),
                        columns.text(MediaStore.Video.Media.DISPLAY_NAME),
                        columns.text(MediaStore.Video.Media.MIME_TYPE),
                        columns.longValue(MediaStore.Video.Media.SIZE),
                        columns.intValue(MediaStore.Video.Media.WIDTH),
                        columns.intValue(MediaStore.Video.Media.HEIGHT),
                        columns.longValue(MediaStore.Video.Media.DURATION),
                        columns.longValue(MediaStore.Video.Media.DATE_TAKEN),
                        columns.longValue(MediaStore.Video.Media.DATE_ADDED),
                        columns.longValue(MediaStore.Video.Media.DATE_MODIFIED),
                        MediaStoreCompatibility.hasRelativePath() ? columns.text(MediaStore.Video.Media.RELATIVE_PATH) : "",
                        columns.text(MediaStore.Video.Media.BUCKET_ID),
                        columns.text(MediaStore.Video.Media.BUCKET_DISPLAY_NAME), 0));
            }
        } catch (SecurityException videoDenied) {
            // Image-only access is valid; lack of video access must not hide images.
        }
    }

    private String[] imageProjection() {
        List<String> columns = new ArrayList<>();
        Collections.addAll(columns, MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.ORIENTATION);
        if (MediaStoreCompatibility.hasRelativePath()) columns.add(MediaStore.Images.Media.RELATIVE_PATH);
        return columns.toArray(new String[0]);
    }

    private String[] videoProjection() {
        List<String> columns = new ArrayList<>();
        Collections.addAll(columns, MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.MIME_TYPE, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.DATE_TAKEN,
                MediaStore.Video.Media.DATE_ADDED, MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.BUCKET_ID, MediaStore.Video.Media.BUCKET_DISPLAY_NAME);
        if (MediaStoreCompatibility.hasRelativePath()) columns.add(MediaStore.Video.Media.RELATIVE_PATH);
        return columns.toArray(new String[0]);
    }

    private void queryGrantedFolders(List<MediaItem> output) {
        SharedPreferences folders = context.getSharedPreferences("folders", Context.MODE_PRIVATE);
        Set<String> existing = new HashSet<>();
        for (MediaItem item : output) existing.add(item.uri.toString());
        for (Map.Entry<String, ?> entry : folders.getAll().entrySet()) {
            Uri treeUri = Uri.parse(String.valueOf(entry.getValue()));
            DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
            if (root != null && root.canRead()) scanDocuments(root, root.getName(), output, existing, 0);
        }
    }

    private void scanDocuments(DocumentFile folder, String folderName, List<MediaItem> output,
                               Set<String> existing, int depth) {
        if (depth > 12) return;
        DocumentFile[] children;
        try { children = folder.listFiles(); } catch (Exception revoked) { return; }
        for (DocumentFile file : children) {
            if (file.isDirectory()) scanDocuments(file, file.getName(), output, existing, depth + 1);
            else {
                String mime = file.getType();
                if (mime != null && (mime.startsWith("image/") || mime.startsWith("video/"))
                        && existing.add(file.getUri().toString())) {
                    long modified = file.lastModified();
                    output.add(new MediaItem(-Math.abs(file.getUri().hashCode()), file.getUri(), file.getName(), mime,
                            file.length(), 0, 0, 0, 0, modified / 1000, modified / 1000,
                            folderName, file.getUri().toString(), folderName, 0));
                }
            }
        }
    }

    private static long sortDate(MediaItem item) {
        return Math.max(item.dateTaken, Math.max(item.dateModified * 1000, item.dateAdded * 1000));
    }

    private static final class ColumnReader {
        private final Cursor cursor;
        ColumnReader(Cursor cursor) { this.cursor = cursor; }
        int index(String name) { return cursor.getColumnIndex(name); }
        String text(String name) { int index=index(name); return index < 0 || cursor.isNull(index) ? "" : cursor.getString(index); }
        long longValue(String name) { int index=index(name); return index < 0 || cursor.isNull(index) ? 0 : cursor.getLong(index); }
        int intValue(String name) { int index=index(name); return index < 0 || cursor.isNull(index) ? 0 : cursor.getInt(index); }
    }
}
