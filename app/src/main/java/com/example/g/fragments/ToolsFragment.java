package com.example.g.fragments;

import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import com.example.g.R;
import com.example.g.activities.FolderAccessActivity;
import com.example.g.activities.MainActivity;
import com.example.g.activities.RecycleBinActivity;
import com.example.g.activities.SearchActivity;
import com.example.g.ui.AppLogoManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ToolsFragment extends Fragment {
    private ImageView logoPreview;
    private final ActivityResultLauncher<String> logoPicker = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                Context app = requireContext().getApplicationContext();
                new Thread(() -> {
                    try {
                        AppLogoManager.save(app, uri);
                        if (getActivity() != null) requireActivity().runOnUiThread(() -> {
                            refreshLogoPreview();
                            if (requireActivity() instanceof MainActivity) ((MainActivity) requireActivity()).refreshLogo();
                            Toast.makeText(requireContext(), "Custom app logo saved", Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception error) {
                        if (getActivity() != null) requireActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }).start();
            });

    public ToolsFragment(){super(R.layout.fragment_message);}
    public static ToolsFragment make(String type){ToolsFragment fragment=new ToolsFragment();Bundle args=new Bundle();args.putString("type",type);fragment.setArguments(args);return fragment;}

    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state){
        String type=requireArguments().getString("type","explore");
        TextView title=view.findViewById(R.id.title),message=view.findViewById(R.id.message);
        ImageView icon=view.findViewById(R.id.icon);
        MaterialButton action=view.findViewById(R.id.action);action.setVisibility(View.VISIBLE);
        if("settings".equals(type)){
            icon.setImageResource(R.drawable.ic_settings);title.setText("Gallery Settings");
            message.setText("Manage media folders, AI confidence and Android recycle bin. Luma keeps edits non-destructive.");
            action.setText("Open Recycle Bin");action.setIconResource(R.drawable.ic_delete);
            action.setOnClickListener(x->startActivity(new Intent(requireContext(),RecycleBinActivity.class)));
            addLogoSettings(view);addButton(view,"Add media folder",FolderAccessActivity.class);addAiDetectionSettings(view);
        }else{
            icon.setImageResource(R.drawable.ic_ai_scan);title.setText("AI Gallery Scan");
            message.setText("Check new and existing media with private on-device metadata, provenance and forensic analysis.");
            action.setText("Scan gallery now");action.setIconResource(R.drawable.ic_ai_scan);
            action.setOnClickListener(x->{x.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);com.example.g.analysis.AnalysisScheduler.requestRescan(requireContext());Toast.makeText(requireContext(),"AI gallery scan started",Toast.LENGTH_SHORT).show();});
            addButton(view,"Search library",SearchActivity.class);addButton(view,"Recycle Bin",RecycleBinActivity.class);
        }
    }

    private void addButton(View root,String label,Class<?> target){
        LinearLayout box=root.findViewById(R.id.tools_content);
        MaterialButton button=new MaterialButton(requireContext(),null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);button.setIconResource(target==SearchActivity.class?R.drawable.ic_search:target==FolderAccessActivity.class?R.drawable.ic_add_folder:R.drawable.ic_delete);
        button.setOnClickListener(view->startActivity(new Intent(requireContext(),target)));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(10);box.addView(button,params);
    }

    private void addAiDetectionSettings(View root){
        LinearLayout box=root.findViewById(R.id.tools_content);
        MaterialButton button=new MaterialButton(requireContext(),null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText("AI detection confidence");button.setIconResource(R.drawable.ic_ai_scan);
        button.setOnClickListener(view->{final int[] values={80,85,90,95};String[] labels={"80% - more results","85% - recommended","90% - fewer false positives","95% - strict"};int current=com.example.g.analysis.AnalysisSettings.confidenceThreshold(requireContext());int checked=1;for(int i=0;i<values.length;i++)if(values[i]==current)checked=i;new MaterialAlertDialogBuilder(requireContext()).setTitle("AI detection confidence").setSingleChoiceItems(labels,checked,(dialog,which)->{com.example.g.analysis.AnalysisSettings.setConfidenceThreshold(requireContext(),values[which]);com.example.g.analysis.AnalysisScheduler.requestRescan(requireContext());dialog.dismiss();}).setNegativeButton("Cancel",null).show();});
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(10);box.addView(button,params);
    }

    private void addLogoSettings(View root) {
        LinearLayout box = root.findViewById(R.id.tools_content);
        View card = getLayoutInflater().inflate(R.layout.view_logo_settings, box, false);
        logoPreview = card.findViewById(R.id.logo_preview);
        refreshLogoPreview();
        card.findViewById(R.id.choose_logo).setOnClickListener(view -> logoPicker.launch("image/*"));
        card.findViewById(R.id.add_logo_shortcut).setOnClickListener(view -> {
            if (!AppLogoManager.hasCustomLogo(requireContext())) {
                Toast.makeText(requireContext(), "Choose a logo image first", Toast.LENGTH_SHORT).show();
            } else if (AppLogoManager.requestHomeShortcut(requireContext())) {
                Toast.makeText(requireContext(), "Confirm the Home-screen shortcut", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "This launcher does not support pinned shortcuts", Toast.LENGTH_LONG).show();
            }
        });
        card.findViewById(R.id.reset_logo).setOnClickListener(view -> {
            AppLogoManager.reset(requireContext()); refreshLogoPreview();
            if (requireActivity() instanceof MainActivity) ((MainActivity) requireActivity()).refreshLogo();
            Toast.makeText(requireContext(), "Default logo restored", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(12);
        box.addView(card, params);
    }

    private void refreshLogoPreview() {
        if (logoPreview != null && isAdded()) logoPreview.setImageDrawable(AppLogoManager.drawable(requireContext()));
    }

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
