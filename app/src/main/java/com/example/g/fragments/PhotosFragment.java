package com.example.g.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.database.ContentObserver;
import android.provider.MediaStore;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.HapticFeedbackConstants;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.app.PendingIntent;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.g.R;
import com.example.g.adapters.MediaAdapter;
import com.example.g.viewmodels.PhotosViewModel;
import com.example.g.core.compatibility.AndroidCompatibility;
import com.example.g.core.permissions.PermissionManager;
import com.example.g.core.permissions.PermissionState;
import com.example.g.features.aiDetection.AIImageDetector;
import com.example.g.features.aiDetection.AIImageReference;
import com.example.g.features.aiDetection.WorkManagerAIImageDetector;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.io.File;

public class PhotosFragment extends Fragment {
    private PhotosViewModel viewModel;
    private boolean accessMessageShown;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipe;
    private View statePanel;
    private TextView stateTitle;
    private TextView stateMessage;
    private com.google.android.material.button.MaterialButton stateAction;
    private MediaAdapter adapter;
    private List<com.example.g.models.MediaItem> pendingFiles = new ArrayList<>();
    private boolean pendingMove;
    private final List<File> pendingEyeFiles = new ArrayList<>();
    private List<com.example.g.models.MediaItem> lastDeletedItems = new ArrayList<>();
    private boolean pendingUserDelete;
    private AIImageDetector aiImageDetector;
    private final ActivityResultLauncher<Intent> folderLauncher=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),r->{if(r.getResultCode()==android.app.Activity.RESULT_OK&&r.getData()!=null&&r.getData().getData()!=null)copyTo(r.getData().getData());});
    private final ActivityResultLauncher<IntentSenderRequest> deleteLauncher=registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),r->{boolean success=r.getResultCode()==android.app.Activity.RESULT_OK;if(!success)rollbackPendingEye();else pendingEyeFiles.clear();if(success&&pendingUserDelete)showUndo();pendingUserDelete=false;adapter.clearSelection();if(viewModel!=null)viewModel.refresh();});
    private final ActivityResultLauncher<IntentSenderRequest> undoLauncher=registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),r->{if(viewModel!=null)viewModel.refresh();});
    private final ContentObserver mediaObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange, Uri uri) { if (viewModel != null && hasAnyAccess()) viewModel.refresh(); }
    };
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (hasAnyAccess()) viewModel.refresh();
                else showAccessDialog();
            });

    public PhotosFragment() { super(R.layout.fragment_photos); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        aiImageDetector = new WorkManagerAIImageDetector(requireContext());
        adapter = new MediaAdapter();
        swipe = view.findViewById(R.id.swipe);
        statePanel = view.findViewById(R.id.gallery_state);
        stateTitle = view.findViewById(R.id.state_title);
        stateMessage = view.findViewById(R.id.state_message);
        stateAction = view.findViewById(R.id.state_action);
        RecyclerView grid = view.findViewById(R.id.grid);
        int minimum = (int) (124 * getResources().getDisplayMetrics().density);
        grid.setLayoutManager(new GridLayoutManager(requireContext(), Math.max(2,
                getResources().getDisplayMetrics().widthPixels / minimum)));
        grid.setAdapter(adapter);
        grid.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener(){private boolean dragging;@Override public boolean onInterceptTouchEvent(@NonNull RecyclerView rv,@NonNull android.view.MotionEvent e){if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)dragging=adapter.isSelecting();if(e.getActionMasked()==android.view.MotionEvent.ACTION_MOVE&&adapter.isSelecting())dragging=true;if(dragging&&(e.getActionMasked()==android.view.MotionEvent.ACTION_MOVE||e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)){View child=rv.findChildViewUnder(e.getX(),e.getY());if(child!=null)adapter.selectPosition(rv.getChildAdapterPosition(child));}if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP||e.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL)dragging=false;return false;}});
        View selectionBar=view.findViewById(R.id.selection_bar); TextView selectionCount=view.findViewById(R.id.selection_count);
        adapter.setSelectionListener(selected->{selectionBar.setVisibility(selected.isEmpty()?View.GONE:View.VISIBLE);selectionCount.setText(selected.size()+" Selected");});
        view.findViewById(R.id.select_all).setOnClickListener(v->adapter.selectAll());
        view.findViewById(R.id.cancel_selection).setOnClickListener(v->adapter.clearSelection());
        view.findViewById(R.id.delete_selected).setOnClickListener(v->deleteSelected());
        view.findViewById(R.id.share_selected).setOnClickListener(v->shareSelected());
        view.findViewById(R.id.ai_selected).setOnClickListener(v->scanSelected(v));
        view.findViewById(R.id.favorite_selected).setOnClickListener(v->favoriteSelected(v));
        view.findViewById(R.id.eye_selected).setOnClickListener(v->moveSelectedToEye());
        view.findViewById(R.id.copy_selected).setOnClickListener(v->chooseFolder(false));
        view.findViewById(R.id.move_selected).setOnClickListener(v->chooseFolder(true));
        viewModel = new ViewModelProvider(this).get(PhotosViewModel.class);
        viewModel.media().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
            for (com.example.g.models.MediaItem item : items) {
                aiImageDetector.scanImage(AIImageReference.from(item));
            }
            if (!Boolean.TRUE.equals(viewModel.loading().getValue()) && viewModel.error().getValue() == null) {
                if (items.isEmpty()) showState(hasPartialAccess() ? "Selected access is empty" : "No images found",
                        hasPartialAccess() ? "Select more photos or allow full access in Settings." : "Add a folder or refresh after adding media.",
                        hasPartialAccess() ? "Select photos" : "Add folder",
                        hasPartialAccess() ? this::requestMediaAccess : () -> startActivity(new Intent(requireContext(), com.example.g.activities.FolderAccessActivity.class)));
                else statePanel.setVisibility(View.GONE);
            }
        });
        // Worker writes are observable, so badges change without pulling to refresh or reopening.
        com.example.g.analysis.AnalysisRepository.observeAll(requireContext()).observe(getViewLifecycleOwner(), entries -> {
            Map<String, com.example.g.analysis.MediaAnalysisEntity> values = new HashMap<>();
            for (com.example.g.analysis.MediaAnalysisEntity entry : entries) values.put(entry.uri, entry);
            adapter.setAnalyses(values);
        });
        viewModel.loading().observe(getViewLifecycleOwner(), loading ->
                swipe.setRefreshing(loading));
        viewModel.error().observe(getViewLifecycleOwner(), error -> {
            if (error != null) showState("Could not load images", error, "Retry", viewModel::refresh);
        });
        swipe.setOnRefreshListener(viewModel::refresh);
        requireContext().getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
        requireContext().getContentResolver().registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
        if (hasAnyAccess()) viewModel.refresh(); else requestMediaAccess();
    }

    @Override public void onResume() {
        super.onResume();
        if (viewModel != null && hasAnyAccess()) viewModel.refresh();
    }

    @Override public void onDestroyView() {
        try { requireContext().getContentResolver().unregisterContentObserver(mediaObserver); } catch (Exception ignored) { }
        super.onDestroyView();
    }

    private void showState(String title, String message, String button, Runnable action) {
        statePanel.setVisibility(View.VISIBLE); stateTitle.setText(title); stateMessage.setText(message); stateAction.setText(button);
        stateAction.setOnClickListener(view -> action.run());
    }

    private boolean hasAnyAccess() {
        return PermissionManager.hasAnyMediaAccess(requireContext());
    }

    private boolean hasPartialAccess() {
        return PermissionManager.mediaAccess(requireContext()).isPartial();
    }

    private void requestMediaAccess() {
        PermissionManager.markMediaPermissionRequested(requireContext());
        permissionLauncher.launch(PermissionManager.mediaPermissions());
    }

    private void showAccessDialog() {
        if (!isAdded() || accessMessageShown) return;
        accessMessageShown = true;
        boolean permanentlyDenied = PermissionManager.mediaPermissionState(requireActivity())
                == PermissionState.PERMANENTLY_DENIED;
        String message = hasPartialAccess()
                ? "Android has allowed only some media. Choose more photos, or allow full access in Settings to show every folder."
                : permanentlyDenied
                ? "Media access is disabled. Open Android Settings to allow photos and videos, or add a folder manually."
                : "Allow photos and videos so Luma can show your gallery. You can also add a folder manually.";
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Photos access required").setMessage(message)
                .setNegativeButton("Cancel", (ignored, which) -> accessMessageShown = false);
        if (!permanentlyDenied) dialog.setPositiveButton("Choose photos", (ignored, which) -> {
            accessMessageShown = false;
            requestMediaAccess();
        });
        dialog.setNeutralButton("Open Settings", (ignored, which) -> {
                    accessMessageShown = false;
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + requireContext().getPackageName()));
                    startActivity(intent);
                }).show();
    }

    private void chooseFolder(boolean move){pendingFiles=new ArrayList<>(adapter.selectedItems());if(pendingFiles.isEmpty())return;pendingMove=move;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);folderLauncher.launch(i);}
    private void copyTo(Uri folder){com.example.g.media.BatchFileOperations.copyToTree(requireContext(),pendingFiles,folder,(count,error)->{if(error!=null)Toast.makeText(requireContext(),error.getMessage(),Toast.LENGTH_LONG).show();else if(pendingMove)trashUris(pendingFiles);else{Toast.makeText(requireContext(),count+" item(s) copied",Toast.LENGTH_SHORT).show();adapter.clearSelection();}});}
    private void moveSelectedToEye(){List<com.example.g.models.MediaItem> items=new ArrayList<>(adapter.selectedItems());if(items.isEmpty())return;new MaterialAlertDialogBuilder(requireContext()).setTitle("Move "+items.size()+" item(s) to Eye?").setMessage("Photos and videos will be encrypted, then originals will move to Recycle Bin.").setNegativeButton("Cancel",null).setPositiveButton("Move",(d,w)->new Thread(()->{List<File> imported=new ArrayList<>();try{for(com.example.g.models.MediaItem m:items)imported.add(com.example.g.security.VaultStore.importMedia(requireContext(),m.uri,m.name));requireActivity().runOnUiThread(()->{pendingEyeFiles.clear();pendingEyeFiles.addAll(imported);trashUris(items);});}catch(Exception e){for(File file:imported)file.delete();if(isAdded())requireActivity().runOnUiThread(()->Toast.makeText(requireContext(),"Eye move failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}}).start()).show();}
    private void shareSelected(){List<com.example.g.models.MediaItem> items=adapter.selectedItems();if(items.isEmpty())return;ArrayList<Uri> uris=new ArrayList<>();boolean mixed=false;String first=null;for(com.example.g.models.MediaItem m:items){uris.add(m.uri);String type=m.isVideo()?"video":"image";if(first==null)first=type;else if(!first.equals(type))mixed=true;}Intent share=new Intent(items.size()==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE).setType(mixed?"*/*":first+"/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);if(items.size()==1)share.putExtra(Intent.EXTRA_STREAM,uris.get(0));else share.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);startActivity(Intent.createChooser(share,"Share "+items.size()+" item(s)"));}
    private void scanSelected(View button){List<com.example.g.models.MediaItem> items=adapter.selectedItems();if(items.isEmpty())return;button.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);button.setEnabled(false);if(button instanceof TextView)((TextView)button).setText("Scanning…");List<AIImageReference> references=new ArrayList<>();for(com.example.g.models.MediaItem item:items)references.add(new AIImageReference(item.uri.toString(),item.uri,item.name,item.mime,item.size,System.currentTimeMillis()));aiImageDetector.scanImages(references);new Handler(Looper.getMainLooper()).postDelayed(()->{if(!isAdded())return;button.setEnabled(true);if(button instanceof TextView)((TextView)button).setText("AI Scan");Toast.makeText(requireContext(),items.size()+" item(s) queued for AI analysis",Toast.LENGTH_SHORT).show();adapter.clearSelection();},900);}
    private void favoriteSelected(View button){List<com.example.g.models.MediaItem> items=adapter.selectedItems();if(items.isEmpty())return;button.performHapticFeedback(HapticFeedbackConstants.CONFIRM);android.content.SharedPreferences.Editor edit=requireContext().getSharedPreferences("favorites",0).edit();for(com.example.g.models.MediaItem item:items)edit.putBoolean(item.uri.toString(),true);edit.apply();Toast.makeText(requireContext(),items.size()+" item(s) added to Favorites",Toast.LENGTH_SHORT).show();adapter.clearSelection();}
    private void deleteSelected(){List<com.example.g.models.MediaItem> items=new ArrayList<>(adapter.selectedItems());if(items.isEmpty())return;BottomSheetDialog dialog=new BottomSheetDialog(requireContext());LinearLayout box=new LinearLayout(requireContext());box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(18),dp(24),dp(24));TextView title=new TextView(requireContext());title.setText("Delete "+items.size()+" item(s)?");title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);TextView message=new TextView(requireContext());message.setText("Selected media will move to Android's Recycle Bin.");message.setPadding(0,dp(8),0,dp(18));MaterialButton move=new MaterialButton(requireContext());move.setText("Move to Trash");move.setIconResource(R.drawable.ic_delete);move.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);pendingUserDelete=true;lastDeletedItems=items;dialog.dismiss();trashUris(items);});MaterialButton cancel=new MaterialButton(requireContext(),null,com.google.android.material.R.attr.materialButtonOutlinedStyle);cancel.setText("Cancel");cancel.setOnClickListener(v->dialog.dismiss());box.addView(title);box.addView(message);box.addView(move,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(8);box.addView(cancel,params);dialog.setContentView(box);dialog.show();}
    private void trashUris(List<com.example.g.models.MediaItem> items){try{if(AndroidCompatibility.supportsMediaTrash()){List<Uri> uris=new ArrayList<>();for(com.example.g.models.MediaItem m:items)uris.add(m.uri);PendingIntent request=MediaStore.createTrashRequest(requireContext().getContentResolver(),uris,true);deleteLauncher.launch(new IntentSenderRequest.Builder(request.getIntentSender()).build());}else{for(com.example.g.models.MediaItem m:items)requireContext().getContentResolver().delete(m.uri,null,null);pendingEyeFiles.clear();pendingUserDelete=false;adapter.clearSelection();viewModel.refresh();}}catch(Exception e){pendingUserDelete=false;rollbackPendingEye();Toast.makeText(requireContext(),"Android could not modify one or more files",Toast.LENGTH_LONG).show();}}
    private void rollbackPendingEye(){for(File file:pendingEyeFiles)file.delete();pendingEyeFiles.clear();}
    private void showUndo(){if(!isAdded()||!AndroidCompatibility.supportsMediaTrash())return;com.google.android.material.snackbar.Snackbar.make(requireView(),lastDeletedItems.size()+" item(s) moved to Trash",com.google.android.material.snackbar.Snackbar.LENGTH_LONG).setAction("UNDO",v->{try{List<Uri> uris=new ArrayList<>();for(com.example.g.models.MediaItem item:lastDeletedItems)uris.add(item.uri);PendingIntent request=MediaStore.createTrashRequest(requireContext().getContentResolver(),uris,false);undoLauncher.launch(new IntentSenderRequest.Builder(request.getIntentSender()).build());}catch(Exception error){Toast.makeText(requireContext(),"Could not restore items",Toast.LENGTH_LONG).show();}}).show();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
