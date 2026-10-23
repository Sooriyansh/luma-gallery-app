package com.example.g.activities;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.example.g.R;
import com.example.g.core.compatibility.AndroidCompatibility;
import com.example.g.features.aiDetection.AIImageReference;
import com.example.g.features.aiDetection.WorkManagerAIImageDetector;
import com.example.g.ui.SystemBarInsets;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.Collections;
import java.io.File;

public class VideoPlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView view;
    private int rotation;
    private File pendingHiddenFile;
    private boolean waitingForVaultSetup;
    private final ActivityResultLauncher<IntentSenderRequest> trash = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> { if (result.getResultCode() == RESULT_OK) { pendingHiddenFile = null; finish(); } else rollbackPendingHide(); });
    private final ActivityResultLauncher<Intent> vaultSetup = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (waitingForVaultSetup && result.getResultCode() == RESULT_OK) { waitingForVaultSetup = false; hideVideo(); } else waitingForVaultSetup = false;
    });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_video_player);
        SystemBarInsets.bottomActions(getWindow(), findViewById(R.id.video_root), findViewById(R.id.video_actions));
        MaterialToolbar toolbar=findViewById(R.id.video_toolbar);toolbar.setTitle(getIntent().getStringExtra("name"));toolbar.setNavigationIcon(R.drawable.ic_arrow_back);toolbar.setNavigationContentDescription("Back to gallery");toolbar.setNavigationOnClickListener(v->finish());toolbar.setOnMenuItemClickListener(item -> { if (item.getItemId() == R.id.video_menu_hide) hideVideo(); return true; });
        view = findViewById(R.id.player_view); player = new ExoPlayer.Builder(this).build(); view.setPlayer(player); player.setMediaItem(MediaItem.fromUri(getIntent().getData())); player.prepare(); player.play();
        findViewById(R.id.video_analysis).setOnClickListener(button -> showAnalysis());
        findViewById(R.id.video_hide).setOnClickListener(button -> hideVideo());
        findViewById(R.id.video_rotate).setOnClickListener(button -> { rotation = (rotation + 90) % 360; view.setRotation(rotation); });
        findViewById(R.id.video_trim).setOnClickListener(button -> { Intent edit = new Intent(Intent.ACTION_EDIT).setDataAndType(getIntent().getData(), "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); try { startActivity(Intent.createChooser(edit, "Trim or edit video")); } catch (Exception error) { Toast.makeText(this, "No video editor is installed", Toast.LENGTH_LONG).show(); } });
        findViewById(R.id.video_delete).setOnClickListener(button -> confirmDelete());
    }

    private void hideVideo() {
        if (!com.example.g.security.EyePinStore.hasPin(this)) { waitingForVaultSetup = true; vaultSetup.launch(new Intent(this, EyeLockActivity.class)); return; }
        new MaterialAlertDialogBuilder(this).setTitle("Move video to Private Vault?")
                .setMessage("The video will be encrypted, then its original will move to Android's Recycle Bin.")
                .setNegativeButton("Cancel", null).setPositiveButton("Move", (d, w) -> new Thread(() -> { try {
                    pendingHiddenFile = com.example.g.security.VaultStore.importMedia(this, getIntent().getData(), getIntent().getStringExtra("name"));
                    runOnUiThread(this::moveToBin);
                } catch (Exception error) { runOnUiThread(() -> Toast.makeText(this, "Unable to move video to Private Vault", Toast.LENGTH_LONG).show()); } }).start()).show();
    }

    private void showAnalysis() {
        String key = getIntent().getData().toString();
        com.example.g.analysis.AnalysisRepository.loadOne(this, key, found -> {
            com.example.g.analysis.MediaAnalysisEntity analysis = found.get(key);
            if (analysis == null) {
                queueAnalysis();
                Toast.makeText(this, "Video analysis queued. It continues in the background.", Toast.LENGTH_LONG).show();
                return;
            }
            if (com.example.g.analysis.AnalysisStatus.PENDING.equals(analysis.status)) {
                Toast.makeText(this, "Video analysis is in progress.", Toast.LENGTH_LONG).show();
                return;
            }
            boolean failed = com.example.g.analysis.AnalysisStatus.FAILED.equals(analysis.status);
            new MaterialAlertDialogBuilder(this).setTitle(failed ? "Video analysis could not finish" : "AI video analysis")
                    .setMessage((analysis.reason == null || analysis.reason.isEmpty() ? "No report is available yet." : analysis.reason)
                            + (failed ? "" : "\n\nThis is a probabilistic assessment, not proof."))
                    .setNegativeButton("Close", null)
                    .setPositiveButton(failed ? "Try again" : "Analyze again", (dialog, which) -> {
                        queueAnalysis();
                        Toast.makeText(this, "Video analysis queued.", Toast.LENGTH_SHORT).show();
                    }).show();
        });
    }

    private void queueAnalysis() {
        android.view.View control=findViewById(R.id.video_analysis);control.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);control.setEnabled(false);if(control instanceof android.widget.TextView)((android.widget.TextView)control).setText("Scanning…");
        android.net.Uri mediaUri=getIntent().getData();
        new WorkManagerAIImageDetector(this).scanImage(new AIImageReference(mediaUri.toString(),mediaUri,
                getIntent().getStringExtra("name"),"video/*",0,System.currentTimeMillis()));
        control.postDelayed(()->{control.setEnabled(true);if(control instanceof android.widget.TextView)((android.widget.TextView)control).setText("AI Scan");},1000);
    }
    private void confirmDelete() { new MaterialAlertDialogBuilder(this).setTitle("Move video to recycle bin?").setNegativeButton("Cancel", null).setPositiveButton("Move", (dialog, which) -> moveToBin()).show(); }
    private void moveToBin() { try { if (AndroidCompatibility.supportsMediaTrash()) { PendingIntent request = MediaStore.createTrashRequest(getContentResolver(), Collections.singletonList(getIntent().getData()), true); trash.launch(new IntentSenderRequest.Builder(request.getIntentSender()).build()); } else if (getContentResolver().delete(getIntent().getData(), null, null) > 0) { pendingHiddenFile = null; finish(); } else { rollbackPendingHide(); Toast.makeText(this, "Android did not allow deletion", Toast.LENGTH_LONG).show(); } } catch (Exception error) { rollbackPendingHide(); Toast.makeText(this, "Android did not allow deletion", Toast.LENGTH_LONG).show(); } }
    private void rollbackPendingHide() { if (pendingHiddenFile != null) { pendingHiddenFile.delete(); pendingHiddenFile = null; } }
    @Override protected void onStop() { super.onStop(); if (player != null) player.pause(); }
    @Override protected void onDestroy() { super.onDestroy(); if (player != null) { player.release(); player = null; } }
}
