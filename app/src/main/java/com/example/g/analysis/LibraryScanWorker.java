package com.example.g.analysis;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.g.core.compatibility.MediaStoreCompatibility;

/** Periodically finds gallery-visible additions and queues only new or changed rows. */
public final class LibraryScanWorker extends Worker {
    private static final ExecutorService ROW_IO = Executors.newSingleThreadExecutor();
    public LibraryScanWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }
    @NonNull @Override public Result doWork() {
        try {
            boolean scanned = false;
            try { scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false); scanned = true; } catch (SecurityException ignored) { }
            try { scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true); scanned = true; } catch (SecurityException ignored) { }
            return scanned ? Result.success() : Result.failure();
        } catch (Exception retryable) { return Result.retry(); }
    }
    private void scan(Uri fallback, boolean video) {
        Context app = getApplicationContext(); ContentResolver resolver = app.getContentResolver();
        Uri collection = video ? MediaStoreCompatibility.videosCollection() : MediaStoreCompatibility.imagesCollection();
        String[] projection = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATE_MODIFIED};
        // Most collection notifications do not identify the row. Looking only at the newest rows
        // makes that fallback cheap while the database prevents duplicate API/model calls.
        try (Cursor cursor = resolver.query(collection, projection, null, null, MediaStore.MediaColumns.DATE_ADDED + " DESC LIMIT 250")) {
            if (cursor == null) return;
            int id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID), name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME), mime = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE), changed = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED);
            MediaAnalysisDao dao = AnalysisDatabase.get(app).analyses();
            while (cursor.moveToNext() && !isStopped()) {
                Uri uri = ContentUris.withAppendedId(collection, cursor.getLong(id)); long modified = cursor.getLong(changed);
                MediaAnalysisEntity existing = dao.get(uri.toString());
                if (existing == null || existing.modified != modified || !AnalysisSettings.modelVersion(app).equals(existing.modelVersion)) AnalysisScheduler.enqueue(app, uri, cursor.getString(name), cursor.getString(mime), modified);
            }
        }
    }
    static void queueRow(Context context, Uri uri) {
        ROW_IO.execute(() -> queueRowOnIo(context.getApplicationContext(), uri));
    }
    private static void queueRowOnIo(Context context, Uri uri) {
        String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATE_MODIFIED};
        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return; // deleted between notification and query
            String mime = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE));
            if (TextUtils.isEmpty(mime) || (!mime.startsWith("image/") && !mime.startsWith("video/"))) return;
            String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME));
            long modified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED));
            MediaAnalysisEntity existing = AnalysisDatabase.get(context).analyses().get(uri.toString());
            if (existing == null || existing.modified != modified || !AnalysisSettings.modelVersion(context).equals(existing.modelVersion)) AnalysisScheduler.enqueue(context, uri, name, mime, modified);
        } catch (SecurityException ignored) {
            // Permission may be temporarily unavailable; periodic work will retry after it is restored.
        } catch (Exception ignored) {
            // A partially-written, corrupt, or deleted row must not crash the observer process.
        }
    }
}
