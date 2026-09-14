package com.example.g.analysis;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import android.provider.MediaStore;

/** Queues only gallery-visible URIs; it never accesses another app's private storage. */
public final class AnalysisScheduler {
    private static boolean watching;
    private static ContentObserver observer;
    private AnalysisScheduler() { }
    public static void scheduleLibraryWatch(Context context) {
        if (watching) return; watching = true;
        Context app = context.getApplicationContext(); ContentResolver resolver = app.getContentResolver();
        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange, Uri uri) {
                // Some providers send a row URI; others only signal the collection. Handle both.
                if (uri != null && isMediaRow(uri)) LibraryScanWorker.queueRow(app, uri);
                else scheduleChangeScan(app);
            }
        };
        resolver.registerContentObserver(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer);
        resolver.registerContentObserver(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer);
        WorkManager.getInstance(app).enqueueUniqueWork("initial-media-library-scan", ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(LibraryScanWorker.class).build());
        // A ContentObserver is near real-time while this process is alive. This persisted fallback
        // catches additions while Android has stopped the process (and survives device restarts).
        PeriodicWorkRequest scan = new PeriodicWorkRequest.Builder(LibraryScanWorker.class, 15, TimeUnit.MINUTES)
                .addTag("media-library-watch").build();
        WorkManager.getInstance(app).enqueueUniquePeriodicWork("media-library-watch", androidx.work.ExistingPeriodicWorkPolicy.KEEP, scan);
    }
    public static void enqueue(Context context, Uri uri, String name, String mime, long modified) {
        if (uri == null || mime == null || (!mime.startsWith("image/") && !mime.startsWith("video/"))) return;
        Data data = new Data.Builder().putString(AnalysisWorker.URI_KEY, uri.toString()).putString(AnalysisWorker.NAME_KEY, name)
                .putString(AnalysisWorker.MIME_KEY, mime).putLong(AnalysisWorker.MODIFIED_KEY, modified).build();
        // A newly received WhatsApp/download image should be checked as soon as Android reports
        // it to MediaStore, rather than waiting for a battery-state constraint to clear.
        // Include the detector revision so a corrected detector can replace stale classifications
        // even when the media bytes and MediaStore modification timestamp have not changed.
        String workIdentity = uri + ":" + modified + ":" + AnalysisSettings.modelVersion(context);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AnalysisWorker.class).setInputData(data)
                .addTag("media-analysis").addTag(workTag(uri.toString())).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork("media-analysis-" + Integer.toHexString(workIdentity.hashCode()), ExistingWorkPolicy.KEEP, request);
    }
    public static String workTag(String imageId) {
        return "ai-image-" + Integer.toHexString(imageId.hashCode());
    }
    public static void requestRescan(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork("initial-media-library-scan", ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(LibraryScanWorker.class).build());
    }
    private static boolean isMediaRow(Uri uri) {
        return uri.toString().matches(".*/(?:images|video)/media/\\d+");
    }
    private static void scheduleChangeScan(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(LibraryScanWorker.class)
                .setInitialDelay(750, TimeUnit.MILLISECONDS).build();
        WorkManager.getInstance(context).enqueueUniqueWork("recent-media-change-scan", ExistingWorkPolicy.KEEP, request);
    }
}
