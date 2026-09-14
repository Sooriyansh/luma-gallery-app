package com.example.g.analysis;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.lifecycle.LiveData;

public final class AnalysisRepository {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private AnalysisRepository() { }
    public interface Callback { void done(Map<String, MediaAnalysisEntity> values); }
    public static void loadAll(Context context, Callback callback) {
        IO.execute(() -> { List<MediaAnalysisEntity> entries = AnalysisDatabase.get(context).analyses().all(); Map<String, MediaAnalysisEntity> out = new HashMap<>(); for (MediaAnalysisEntity e : entries) out.put(e.uri, e); new Handler(Looper.getMainLooper()).post(() -> callback.done(out)); });
    }
    public static void loadOne(Context context, String uri, Callback callback) {
        IO.execute(() -> { MediaAnalysisEntity entry = AnalysisDatabase.get(context).analyses().get(uri); Map<String, MediaAnalysisEntity> out = new HashMap<>(); if (entry != null) out.put(uri, entry); new Handler(Looper.getMainLooper()).post(() -> callback.done(out)); });
    }
    /** Room emits whenever a worker changes PENDING, completed, or failure state. */
    public static LiveData<List<MediaAnalysisEntity>> observeAll(Context context) {
        return AnalysisDatabase.get(context).analyses().observeAll();
    }
}
