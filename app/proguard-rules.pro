# ─── Application data models (Room entities, MediaItem, etc.) ────────────────
-keep class com.example.g.models.** { *; }
-keep class com.example.g.analysis.MediaAnalysisEntity { *; }
-keepattributes Signature,*Annotation*

# ─── Room / SQLite ────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# ─── WorkManager workers ─────────────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ─── TensorFlow Lite / LiteRT ────────────────────────────────────────────────
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.lite.**
-dontwarn com.google.ai.edge.litert.**

# ─── Android Keystore / Crypto ───────────────────────────────────────────────
-keep class android.security.keystore.** { *; }

# ─── Glide generated API ─────────────────────────────────────────────────────
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-dontwarn com.bumptech.glide.**

# ─── ExoPlayer / Media3 ──────────────────────────────────────────────────────
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }

# ─── Biometric ───────────────────────────────────────────────────────────────
-dontwarn androidx.biometric.**

# ─── Material / Compose (unused but referenced by transitive deps) ────────────
-dontwarn androidx.compose.**
-dontwarn com.google.android.material.**

# ─── Firebase / Google Services (conditional) ────────────────────────────────
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ─── General Android safety ──────────────────────────────────────────────────
# Keep all View subclasses used in XML layouts (prevents removal of custom views)
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
# Keep all Activity, Service, etc. entry points
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ─── Preserve line numbers in stack traces for crash reports ─────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
