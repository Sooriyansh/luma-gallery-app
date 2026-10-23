package com.example.g.analysis;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.example.g.R;
import com.example.g.activities.ImageViewerActivity;
import com.example.g.activities.VideoPlayerActivity;
import com.example.g.core.compatibility.AndroidCompatibility;
import com.example.g.core.permissions.PermissionManager;
import com.example.g.core.permissions.PermissionState;

final class AnalysisNotifier {
    private static final String CHANNEL = "ai_analysis_alerts";
    static void show(Context context, MediaAnalysisEntity analysis) {
        if (!context.getSharedPreferences("analysis_settings", Context.MODE_PRIVATE).getBoolean("alerts_enabled", true)) return;
        int threshold = context.getSharedPreferences("analysis_settings", Context.MODE_PRIVATE).getInt("alert_threshold", 80);
        if (analysis.confidence < threshold || (AndroidCompatibility.supportsNotificationPermission()
                && PermissionManager.notificationPermissionState(context) != PermissionState.GRANTED)) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (AndroidCompatibility.supportsNotificationChannels()) manager.createNotificationChannel(new NotificationChannel(CHANNEL, "AI media analysis", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(context, analysis.mimeType.startsWith("video/") ? VideoPlayerActivity.class : ImageViewerActivity.class).setData(android.net.Uri.parse(analysis.uri)).putExtra("name", analysis.fileName).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        PendingIntent pending = PendingIntent.getActivity(context, analysis.uri.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL)
                // Use the monochrome vector drawable — mipmap/ic_launcher is not valid for notification icons.
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle("AI-generated image detected").setContentText("AI-generation indicators found (" + analysis.confidence + "% confidence).")
                .setStyle(new NotificationCompat.BigTextStyle().bigText("This is a probabilistic result, not definitive proof. Tap to review the evidence."))
                .setContentIntent(pending).setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify(analysis.uri.hashCode(), builder.build());
        } catch (SecurityException permissionRevoked) {
            // Permission can be revoked between the check and notify; analysis remains persisted.
        }
    }
}
