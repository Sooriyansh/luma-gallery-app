package com.example.g.activities;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.format.Formatter;
import android.view.View;
import android.view.ViewGroup;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.example.g.R;
import com.example.g.core.compatibility.AndroidCompatibility;
import com.example.g.features.aiDetection.AIImageReference;
import com.example.g.features.aiDetection.WorkManagerAIImageDetector;
import com.example.g.media.MediaEditor;
import com.example.g.models.MediaItem;
import com.example.g.repositories.MediaRepository;
import com.example.g.security.VaultStore;
import com.example.g.ui.SystemBarInsets;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** Full-screen viewer keeps its toolbar and edit actions clear of cutouts and gesture navigation. */
public class ImageViewerActivity extends AppCompatActivity {
    private Uri uri;
    private String name;
    private ViewPager2 pager;
    private final List<MediaItem> images = new ArrayList<>();
    private MaterialToolbar toolbar;
    private View actions;
    private MaterialButton aiButton;
    private MaterialButton hideButton;
    private boolean controlsVisible = true;
    private boolean slideshow;
    private File pendingHiddenFile;
    private boolean waitingForVaultSetup;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable next = new Runnable() { @Override public void run() {
        if (slideshow && !images.isEmpty()) { pager.setCurrentItem((pager.getCurrentItem() + 1) % images.size(), true); handler.postDelayed(this, 3000); }
    }};
    private final ActivityResultLauncher<IntentSenderRequest> trashLauncher = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            boolean wasHide=pendingHiddenFile!=null;
            pendingHiddenFile = null;
            if(wasHide)            Toast.makeText(this, "Photo moved to Private Vault", Toast.LENGTH_SHORT).show();
            if(hideButton!=null){hideButton.setEnabled(true);hideButton.setText("Hide");}
            int position = pager.getCurrentItem(); if (position < images.size()) images.remove(position); pager.getAdapter().notifyDataSetChanged(); if (images.isEmpty()) finish(); else setCurrent(Math.min(position, images.size() - 1));
        } else if (pendingHiddenFile != null) {
            pendingHiddenFile.delete(); pendingHiddenFile = null;
            if(hideButton!=null){hideButton.setEnabled(true);hideButton.setText("Hide");}
            Toast.makeText(this, "Hide cancelled; photo was not moved to Eye", Toast.LENGTH_LONG).show();
        }
    });
    private final ActivityResultLauncher<Intent> vaultSetupLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (waitingForVaultSetup && result.getResultCode() == RESULT_OK) { waitingForVaultSetup = false; hide(); }
                else waitingForVaultSetup = false;
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_image_viewer);
        uri = getIntent().getData(); name = getIntent().getStringExtra("name"); pager = findViewById(R.id.image_pager); toolbar = findViewById(R.id.toolbar); actions = findViewById(R.id.image_actions); aiButton = findViewById(R.id.action_ai); hideButton=findViewById(R.id.action_hide);
        SystemBarInsets.imageViewer(getWindow(), findViewById(android.R.id.content), toolbar, pager, actions);
        toolbar.setTitle(name); toolbar.setNavigationIcon(R.drawable.ic_arrow_back); toolbar.setNavigationContentDescription("Back to gallery"); toolbar.setNavigationOnClickListener(view -> finish()); updateFavoriteAction();
        toolbar.setOnMenuItemClickListener(item -> { if (item.getItemId() == R.id.view_share) share(); else if (item.getItemId() == R.id.view_favorite) favorite(); else if (item.getItemId() == R.id.view_slideshow) toggleSlideshow(); else if (item.getItemId() == R.id.view_info) info(); else if (item.getItemId() == R.id.view_hide) hide(); return true; });
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() { @Override public void onPageSelected(int position) { setCurrent(position); }});
        pager.setAdapter(new Pages()); loadPages();
        findViewById(R.id.action_rotate).setOnClickListener(view -> edit(90, 0));
        findViewById(R.id.action_crop).setOnClickListener(view -> new MaterialAlertDialogBuilder(this).setTitle("Crop image").setItems(new String[]{"Manual crop", "Square 1:1", "Portrait 4:5", "Landscape 16:9"}, (dialog, which) -> { if (which == 0) startActivity(new Intent(this, ManualCropActivity.class).setData(uri).putExtra("name", name)); else edit(0, new float[]{0, 1f, 4f / 5f, 16f / 9f}[which]); }).show());
        findViewById(R.id.action_delete).setOnClickListener(view -> confirmDeleteSheet());
        findViewById(R.id.action_hide).setOnClickListener(view -> hide());
        findViewById(R.id.action_share).setOnClickListener(view -> share());
        findViewById(R.id.action_favorite).setOnClickListener(view -> favorite());
        aiButton.setOnClickListener(view -> scanCurrent());
    }

    private MediaItem current() { int position = pager.getCurrentItem(); return position >= 0 && position < images.size() ? images.get(position) : null; }
    private void loadPages() { MediaRepository.get(this).load(10000, 0, (items, error) -> { for (MediaItem item : items) if (!item.isVideo()) images.add(item); int target = 0; for (int i = 0; i < images.size(); i++) if (images.get(i).uri.equals(uri)) { target = i; break; } pager.getAdapter().notifyDataSetChanged(); pager.setCurrentItem(target, false); setCurrent(target); }); }
    private void setCurrent(int position) { if (position >= 0 && position < images.size()) { MediaItem item = images.get(position); uri = item.uri; name = item.name; toolbar.setTitle(name); updateFavoriteAction(); } }
    private void share() { Intent share = new Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(share, "Share photo")); }
    private void favorite() { android.content.SharedPreferences preferences = getSharedPreferences("favorites", 0); String key = uri.toString(); boolean add = !preferences.getBoolean(key, false); preferences.edit().putBoolean(key, add).apply(); com.example.g.ui.HapticFeedback.confirm(findViewById(R.id.action_favorite)); findViewById(R.id.action_favorite).animate().scaleX(.86f).scaleY(.86f).setDuration(90).withEndAction(()->findViewById(R.id.action_favorite).animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start(); updateFavoriteAction(); Toast.makeText(this, add ? "Added to Favorites" : "Removed from Favorites", Toast.LENGTH_SHORT).show(); }
    private void updateFavoriteAction() { if (toolbar == null || uri == null) return; boolean active = getSharedPreferences("favorites", 0).getBoolean(uri.toString(), false); android.view.MenuItem item = toolbar.getMenu().findItem(R.id.view_favorite); if (item != null) { item.setIcon(R.drawable.ic_favorite); item.setChecked(active); item.setTitle(active ? "Remove from favorites" : "Add to favorites"); androidx.core.view.MenuItemCompat.setContentDescription(item, active ? "Remove image from favorites" : "Add image to favorites"); } MaterialButton bottom=findViewById(R.id.action_favorite);if(bottom!=null){bottom.setText(active?"Favorited":"Favorite");bottom.setAlpha(active?1f:.82f);} }
    private void toggleSlideshow() { slideshow = !slideshow; handler.removeCallbacks(next); if (slideshow) { handler.postDelayed(next, 1000); Toast.makeText(this, "Slideshow started · 3 seconds", Toast.LENGTH_SHORT).show(); } else Toast.makeText(this, "Slideshow stopped", Toast.LENGTH_SHORT).show(); }
    private void info() {
        MediaItem item=current();if(item==null)return;
        Uri detailsUri = item.uri;
        long when=Math.max(item.dateTaken,Math.max(item.dateAdded*1000,item.dateModified*1000));
        String base="Resolution: "+item.width+" × "+item.height+"\nSize: "+Formatter.formatFileSize(this,item.size)
                +"\nType: "+item.mime+"\nAlbum: "+item.bucketName+"\nDate: "+DateFormat.getDateTimeInstance().format(new Date(when))+"\nLocation: "+item.relativePath;
        com.example.g.analysis.AnalysisRepository.loadOne(this,detailsUri.toString(),entries->{
            com.example.g.analysis.MediaAnalysisEntity analysis=entries.get(detailsUri.toString());String text=base;
            if(analysis!=null)text+="\n\nMedia source: "+safe(analysis.sourceType)+"\nMedia type: "+safe(analysis.mediaType)
                    +"\nCamera evidence: "+analysis.cameraEvidence+"%\nScreenshot evidence: "+analysis.screenshotConfidence+"%"
                    +"\nVisual AI model: "+(analysis.visualModelAvailable?analysis.modelConfidence+"%":"Unavailable")
                    +"\nContent Credentials: "+safe(analysis.provenanceStatus)
                    +"\nWatermark: "+safe(analysis.watermarkStatus)+"\nFinal result: "+resultTitle(analysis)
                    +"\nAI probability: "+analysis.aiProbability+"%\nReal probability: "+analysis.realProbability+"%"
                    +"\nConfidence level: "+safe(analysis.confidenceLevel)
                    +(analysis.reason==null||analysis.reason.isEmpty()?"":"\n\nDetails:\n"+analysis.reason);
            else text += "\n\nAI analysis: Not scanned. Tap AI Scan to check this image.";
            new MaterialAlertDialogBuilder(this).setTitle(item.name).setMessage(text).setPositiveButton("Close",null).show();
        });
    }
    private void edit(int rotation, float ratio) { MediaEditor.createEditedCopy(this, uri, name, rotation, ratio, (output, error) -> Toast.makeText(this, error == null ? "Edited copy saved" : error.getMessage(), Toast.LENGTH_LONG).show()); }
    private void trash() { try { if (AndroidCompatibility.supportsMediaTrash()) { PendingIntent request = MediaStore.createTrashRequest(getContentResolver(), Collections.singletonList(uri), true); trashLauncher.launch(new IntentSenderRequest.Builder(request.getIntentSender()).build()); } else if (getContentResolver().delete(uri, null, null) > 0) { pendingHiddenFile = null; finish(); } else { rollbackPendingHide(); Toast.makeText(this, "Android did not allow deletion", Toast.LENGTH_LONG).show(); } } catch (Exception error) { rollbackPendingHide(); Toast.makeText(this, "Android did not allow deletion", Toast.LENGTH_LONG).show(); } }
    private void rollbackPendingHide() { if (pendingHiddenFile != null) { pendingHiddenFile.delete(); pendingHiddenFile = null; } if(hideButton!=null){hideButton.setEnabled(true);hideButton.setText("Hide");} }
    private void hide() {
        if (!com.example.g.security.EyePinStore.hasPin(this)) {
            waitingForVaultSetup = true;
            vaultSetupLauncher.launch(new Intent(this, EyeLockActivity.class));
            return;
        }
        new MaterialAlertDialogBuilder(this).setTitle("Move photo to Private Vault?")
                .setMessage("The photo will be encrypted, then its original will move to Android's Recycle Bin.")
                .setNegativeButton("Cancel", null).setPositiveButton("Move", (dialog, which) ->
                new Thread(() -> { try {
                    runOnUiThread(()->{hideButton.setEnabled(false);hideButton.setText("Hiding…");});
                    pendingHiddenFile = VaultStore.importMedia(this, uri, name);
                    runOnUiThread(this::trash);
                } catch (Exception error) {
                    runOnUiThread(() -> {hideButton.setEnabled(true);hideButton.setText("Hide");Toast.makeText(this, "Could not move photo to Private Vault: " + error.getMessage(), Toast.LENGTH_LONG).show();});
                } }).start()).show();
    }

    private void confirmDeleteSheet(){
        BottomSheetDialog dialog=new BottomSheetDialog(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(18),dp(24),dp(24));
        TextView title=new TextView(this);title.setText("Delete this image?");title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);
        TextView message=new TextView(this);message.setText("The image will move to Android's Recycle Bin.");message.setPadding(0,dp(8),0,dp(18));
        MaterialButton move=new MaterialButton(this);move.setText("Move to Trash");move.setIconResource(R.drawable.ic_delete);move.setOnClickListener(v->{com.example.g.ui.HapticFeedback.confirm(v);dialog.dismiss();trash();});
        MaterialButton cancel=new MaterialButton(this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);cancel.setText("Cancel");cancel.setOnClickListener(v->dialog.dismiss());
        box.addView(title);box.addView(message);box.addView(move,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams cancelParams=new LinearLayout.LayoutParams(-1,-2);cancelParams.topMargin=dp(8);box.addView(cancel,cancelParams);dialog.setContentView(box);dialog.show();
    }

    private void scanCurrent() {
        long started = System.currentTimeMillis();
        aiButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        aiButton.setEnabled(false); aiButton.setText("Scanning…");
        Uri scanUri=uri; String scanName=name;
        new WorkManagerAIImageDetector(this).scanImage(
                new AIImageReference(scanUri.toString(), scanUri, scanName, "image/*", 0, started));
        handler.postDelayed(() -> pollAnalysis(scanUri.toString(), started, 0), 500);
    }

    private void pollAnalysis(String key, long started, int attempt) {
        com.example.g.analysis.AnalysisRepository.loadOne(this, key, found -> {
            if(isFinishing()||isDestroyed())return;
            com.example.g.analysis.MediaAnalysisEntity result = found.get(key);
            boolean finished = result != null && result.scannedAt >= started
                    && !com.example.g.analysis.AnalysisStatus.PENDING.equals(result.status);
            if (finished) {
                aiButton.setEnabled(true); aiButton.setText("AI Scan");
                com.example.g.ui.HapticFeedback.confirm(aiButton);
                showAnalysisResult(result);
            } else if (attempt < 45) handler.postDelayed(() -> pollAnalysis(key, started, attempt + 1), 700);
            else { aiButton.setEnabled(true); aiButton.setText("AI Scan"); Toast.makeText(this, "Analysis is continuing in the background", Toast.LENGTH_LONG).show(); }
        });
    }

    private void showAnalysisResult(com.example.g.analysis.MediaAnalysisEntity result) {
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(22),dp(18),dp(22),dp(18));
        TextView heading = new TextView(this); heading.setText("AI Analysis"); heading.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);
        TextView type = new TextView(this); type.setText("Media type · "+safe(result.mediaType)); type.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge); type.setPadding(0,dp(12),0,0);
        TextView status = new TextView(this); status.setText(resultTitle(result)); status.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge); status.setPadding(0,dp(6),0,dp(8));
        content.addView(heading);content.addView(type);content.addView(status);
        if(result.confidence>0){LinearProgressIndicator progress=new LinearProgressIndicator(this);progress.setMax(100);progress.setProgressCompat(result.aiProbability,true);content.addView(progress,new LinearLayout.LayoutParams(-1,dp(8)));TextView score=new TextView(this);score.setText("AI probability "+result.aiProbability+"%  •  Real probability "+result.realProbability+"%\n"+safe(result.confidenceLevel));score.setPadding(0,dp(8),0,dp(8));content.addView(score);}
        else {TextView uncertain=new TextView(this);uncertain.setText("Confidence unavailable — insufficient reliable evidence");uncertain.setPadding(0,dp(4),0,dp(10));content.addView(uncertain);}
        TextView evidence=new TextView(this);evidence.setText("Media source: "+safe(result.sourceType)
                +"\nCamera evidence: "+result.cameraEvidence+"%\nScreenshot: "+result.screenshotConfidence+"%"
                +"\nVisual model: "+(result.visualModelAvailable?result.modelConfidence+"%":"Unavailable")
                +"\nContent Credentials: "+safe(result.provenanceStatus)+"\nWatermark: "+safe(result.watermarkStatus)
                +"\nNoise / Frequency: "+result.noiseScore+"% / "+result.frequencyScore+"%"
                +"\nGAN / Diffusion: "+result.ganScore+"% / "+result.diffusionScore+"%"
                +"\nPixel / Compression: "+result.pixelScore+"% / "+result.compressionScore+"%"
                +"\nSuspicious region: "+result.suspiciousRegionScore+"%");evidence.setPadding(0,dp(4),0,dp(10));content.addView(evidence);
        TextView explanation = new TextView(this); explanation.setText(result.reason == null || result.reason.isEmpty()?"There is not enough reliable evidence to determine whether this image is AI generated.":result.reason); explanation.setMaxLines(14);content.addView(explanation);
        MaterialCardView card = new MaterialCardView(this); card.setRadius(dp(24)); card.addView(content);
        new MaterialAlertDialogBuilder(this).setView(card).setNegativeButton("Close",null).setNeutralButton("Metadata",(d,w)->info())
                .setPositiveButton("Share report",(d,w)->shareReport(result)).show();
    }

    private void shareReport(com.example.g.analysis.MediaAnalysisEntity result) {
        String report="AI Analysis - "+result.fileName+"\nMedia type: "+safe(result.mediaType)+"\nMedia source: "+safe(result.sourceType)
                +"\nResult: "+resultTitle(result)+"\nAI probability: "+result.aiProbability+"%\nReal probability: "+result.realProbability+"%"
                +"\nConfidence level: "+safe(result.confidenceLevel)
                +"\nVisual model: "+(result.visualModelAvailable?result.modelConfidence+"%":"Unavailable")
                +"\nCamera evidence: "+result.cameraEvidence+"%\nScreenshot evidence: "+result.screenshotConfidence+"%"
                +"\nContent Credentials: "+safe(result.provenanceStatus)+"\nWatermark: "+safe(result.watermarkStatus)
                +"\nNoise: "+result.noiseScore+"%\nFrequency: "+result.frequencyScore+"%\nGAN: "+result.ganScore+"%"
                +"\nDiffusion: "+result.diffusionScore+"%\nPixel: "+result.pixelScore+"%\nCompression: "+result.compressionScore+"%"
                +"\nSuspicious region: "+result.suspiciousRegionScore+"%\n\n"+result.reason+"\n\nProbabilistic assessment, not definitive proof.";
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,report),"Share AI report"));
    }

    private String resultTitle(com.example.g.analysis.MediaAnalysisEntity result){
        if(com.example.g.analysis.AnalysisStatus.LIKELY_AI.equals(result.status))return "Likely AI Generated";
        if(com.example.g.analysis.AnalysisStatus.POSSIBLY_AI.equals(result.status))return "AI indicators detected — result is uncertain";
        if(com.example.g.analysis.AnalysisStatus.POSSIBLY_EDITED.equals(result.status))return "AI Edited / Editing Indicators";
        if(com.example.g.analysis.AnalysisStatus.LIKELY_REAL.equals(result.status))return "Likely Not AI Generated";
        if(com.example.g.analysis.AnalysisStatus.FAILED.equals(result.status))return "Analysis Failed";
        return "Unable to Verify";
    }
    private String safe(String value){return value==null||value.isEmpty()?"Unknown":value;}

    private void toggleControls() {
        controlsVisible=!controlsVisible; float target=controlsVisible?1f:0f;
        if(controlsVisible){toolbar.setVisibility(View.VISIBLE);actions.setVisibility(View.VISIBLE);}
        toolbar.animate().alpha(target).setDuration(200).withEndAction(()->{if(!controlsVisible)toolbar.setVisibility(View.GONE);}).start();
        actions.animate().alpha(target).setDuration(200).withEndAction(()->{if(!controlsVisible)actions.setVisibility(View.GONE);}).start();
    }

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy() { handler.removeCallbacks(next); super.onDestroy(); }

    private class Pages extends RecyclerView.Adapter<Pages.Holder> {
        class Holder extends RecyclerView.ViewHolder { final ImageView image; Holder(ImageView view) { super(view); image = view; } }
        @Override public Holder onCreateViewHolder(ViewGroup parent, int type) { ZoomImageView view = new ZoomImageView(); view.setLayoutParams(new ViewGroup.LayoutParams(-1, -1)); view.setScaleType(ImageView.ScaleType.FIT_CENTER); return new Holder(view); }
        @Override public void onBindViewHolder(Holder holder, int position) { Glide.with(holder.image).load(images.get(position).uri).fitCenter().into(holder.image); }
        @Override public int getItemCount() { return images.size(); }
    }

    private final class ZoomImageView extends androidx.appcompat.widget.AppCompatImageView {
        private float zoom=1f;
        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector gestureDetector;
        ZoomImageView(){super(ImageViewerActivity.this);setClickable(true);scaleDetector=new ScaleGestureDetector(ImageViewerActivity.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector detector){zoom=Math.max(1f,Math.min(4f,zoom*detector.getScaleFactor()));setPivotX(detector.getFocusX());setPivotY(detector.getFocusY());setScaleX(zoom);setScaleY(zoom);getParent().requestDisallowInterceptTouchEvent(zoom>1f);return true;}});gestureDetector=new GestureDetector(ImageViewerActivity.this,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){return true;}@Override public boolean onSingleTapConfirmed(MotionEvent e){toggleControls();return true;}@Override public boolean onDoubleTap(MotionEvent e){zoom=zoom>1f?1f:2.5f;setPivotX(e.getX());setPivotY(e.getY());animate().scaleX(zoom).scaleY(zoom).setDuration(220).start();performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);return true;}});}
        @Override public boolean onTouchEvent(MotionEvent event){scaleDetector.onTouchEvent(event);gestureDetector.onTouchEvent(event);if(event.getActionMasked()==MotionEvent.ACTION_UP&&zoom<=1f)getParent().requestDisallowInterceptTouchEvent(false);return true;}
    }
}
