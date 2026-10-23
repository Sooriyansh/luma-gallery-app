package com.example.g.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.g.R;
import com.example.g.security.EyeSession;
import com.example.g.security.EyePinStore;
import com.example.g.security.VaultSettings;
import com.example.g.security.VaultStore;
import com.example.g.activities.MainActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.util.List;
import java.util.Locale;

public class VaultFragment extends Fragment {
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static String previewName(File file) {
        String name = file.getName();
        int split = name.indexOf("__");
        return split >= 0 ? name.substring(split + 2) : name;
    }

    private final Runnable expire = () -> {
        if (isAdded()) {
            EyeSession.lock();
            VaultStore.clearPreviews(requireContext());
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.container, MessageFragment.make("Eye locked", "Tap Eye and unlock again to view private media."))
                    .commit();
        }
    };

    public VaultFragment() { super(R.layout.fragment_eye); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        RecyclerView grid = view.findViewById(R.id.eye_grid);
        grid.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        TextView empty = view.findViewById(R.id.eye_empty);
        long remaining = EyeSession.remaining();
        ((TextView) view.findViewById(R.id.eye_status)).setText("Private Vault unlocked");
        view.findViewById(R.id.vault_lock_now).setOnClickListener(v -> expire.run());
        view.findViewById(R.id.vault_recycle).setOnClickListener(v -> showRecycleBin());
        view.findViewById(R.id.vault_settings).setOnClickListener(v -> showVaultSettings());
        handler.postDelayed(expire, remaining);
        new Thread(() -> {
            List<File> files = VaultStore.decryptPreviews(requireContext());
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                grid.setAdapter(new EyeAdapter(files, empty));
                empty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    private void showVaultSettings() {
        String shortcut = VaultSettings.showShortcut(requireContext())
                ? "Hide Private Vault shortcut" : "Show Private Vault shortcut";
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Private Vault settings")
                .setItems(new String[]{shortcut, "Auto-lock", "Change secret code", "Security information"},
                        (dialog, which) -> {
                            if (which == 0) {
                                boolean enabled = !VaultSettings.showShortcut(requireContext());
                                VaultSettings.setShowShortcut(requireContext(), enabled);
                                if (requireActivity() instanceof MainActivity) {
                                    ((MainActivity) requireActivity()).refreshVaultShortcut();
                                }
                                Toast.makeText(requireContext(),
                                        enabled ? "Private Vault shortcut shown" : "Private Vault shortcut hidden",
                                        Toast.LENGTH_SHORT).show();
                            } else if (which == 1) {
                                chooseAutoLock();
                            } else if (which == 2) {
                                changeVaultCode();
                            } else {
                                new MaterialAlertDialogBuilder(requireContext())
                                        .setTitle("Private Vault security")
                                        .setMessage("Private media is encrypted in app-private storage with an Android Keystore key. Your secret code is saved only as a salted verifier. Rooted or compromised devices and an unlocked device can reduce privacy.")
                                        .setPositiveButton("Close", null).show();
                            }
                        }).show();
    }

    private void chooseAutoLock() {
        long[] values = {0L, 30_000L, 60_000L, 300_000L};
        String[] labels = {"Immediately", "After 30 seconds", "After 1 minute", "After 5 minutes"};
        long current = VaultSettings.autoLockMs(requireContext());
        int checked = 1;
        for (int i = 0; i < values.length; i++) if (values[i] == current) checked = i;
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Auto-Lock Private Vault")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    VaultSettings.setAutoLockMs(requireContext(), values[which]);
                    dialog.dismiss();
                }).setNegativeButton("Cancel", null).show();
    }

    private void changeVaultCode() {
        EditText current = codeField("Current secret code");
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Change secret code").setView(current)
                .setNegativeButton("Cancel", null).setPositiveButton("Continue", (dialog, which) -> {
                    if (!EyePinStore.verify(requireContext(), current.getText().toString().toCharArray())) {
                        Toast.makeText(requireContext(), "Unable to verify secret code", Toast.LENGTH_SHORT).show();
                    } else {
                        enterNewVaultCode();
                    }
                }).show();
    }

    private void enterNewVaultCode() {
        EditText code = codeField("New secret code");
        new MaterialAlertDialogBuilder(requireContext()).setTitle("New secret code").setView(code)
                .setNegativeButton("Cancel", null).setPositiveButton("Continue", (dialog, which) -> {
                    String value = code.getText().toString();
                    if (value.codePointCount(0, value.length()) < 4) {
                        Toast.makeText(requireContext(), "Use at least 4 characters", Toast.LENGTH_SHORT).show();
                    } else {
                        confirmNewVaultCode(value);
                    }
                }).show();
    }

    private void confirmNewVaultCode(String original) {
        EditText confirm = codeField("Confirm new secret code");
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Confirm new secret code").setView(confirm)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", (dialog, which) -> {
                    if (!original.equals(confirm.getText().toString())) {
                        Toast.makeText(requireContext(), "Codes did not match", Toast.LENGTH_SHORT).show();
                    } else {
                        EyePinStore.save(requireContext(), original.toCharArray());
                        Toast.makeText(requireContext(), "Private Vault secret code changed", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private EditText codeField(String hint) {
        EditText field = new EditText(requireContext());
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setPadding(48, 16, 48, 16);
        return field;
    }

    @Override public void onDestroyView() {
        handler.removeCallbacks(expire);
        VaultStore.clearPreviews(requireContext());
        super.onDestroyView();
    }

    private class EyeAdapter extends RecyclerView.Adapter<EyeAdapter.Holder> {
        private final List<File> files;
        private final TextView empty;

        EyeAdapter(List<File> files, TextView empty) { this.files = files; this.empty = empty; }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            Holder(ImageView image) { super(image); this.image = image; }
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            ImageView image = new ImageView(parent.getContext());
            int height = (int) (124 * getResources().getDisplayMetrics().density);
            image.setLayoutParams(new ViewGroup.LayoutParams(-1, height));
            image.setPadding(4, 4, 4, 4);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return new Holder(image);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            File preview = files.get(position);
            Glide.with(holder.image).load(preview).centerCrop().placeholder(R.drawable.image_placeholder).into(holder.image);
            String display = previewName(preview);
            holder.image.setContentDescription(display);
            holder.image.setOnClickListener(view -> {
                int current = holder.getBindingAdapterPosition();
                if (current == RecyclerView.NO_POSITION) return;
                if (display.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mkv|webm|3gp|mov)$"))
                    showVideo(preview, display, current);
                else showImage(preview, display, current);
            });
        }

        private void showImage(File preview, String display, int position) {
            ImageView full = new ImageView(requireContext());
            full.setAdjustViewBounds(true);
            full.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Glide.with(full).load(preview).into(full);
            new MaterialAlertDialogBuilder(requireContext()).setTitle(display).setView(full)
                    .setNegativeButton("Close", null)
                    .setNeutralButton("Move to Recycle Bin", (dialog, which) -> moveToBin(preview))
                    .setPositiveButton("Move to Normal Gallery", (dialog, which) -> restore(preview)).show();
        }

        private void showVideo(File preview, String display, int position) {
            PlayerView playerView = new PlayerView(requireContext());
            ExoPlayer player = new ExoPlayer.Builder(requireContext()).build();
            playerView.setPlayer(player);
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(preview)));
            player.prepare();
            player.play();
            androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(display).setView(playerView)
                    .setNegativeButton("Move to Recycle Bin", (ignored, which) -> moveToBin(preview))
                    .setPositiveButton("Close", null).create();
            dialog.setOnDismissListener(ignored -> player.release());
            dialog.show();
        }

        private void restore(File preview) {
            File encrypted = VaultStore.encryptedForPreview(requireContext(), preview);
            if (encrypted == null) {
                Toast.makeText(requireContext(), "Encrypted Eye item was not found", Toast.LENGTH_LONG).show();
                return;
            }
            new Thread(() -> {
                try {
                    VaultStore.restoreToGallery(requireContext(), encrypted);
                    VaultStore.permanentlyDelete(requireContext(), encrypted);
                    if (encrypted.exists()) throw new java.io.IOException("Restored, but could not remove encrypted copy");
                    preview.delete();
                    if (isAdded()) requireActivity().runOnUiThread(() -> {
                        int current = files.indexOf(preview);
                        if (current >= 0) { files.remove(current); notifyItemRemoved(current); }
                        empty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
                        Toast.makeText(requireContext(), "Restored to gallery", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception error) {
                    if (isAdded()) requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "Restore failed: " + error.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        }

        private void moveToBin(File preview) {
            File encrypted = VaultStore.encryptedForPreview(requireContext(), preview);
            if (encrypted == null) { Toast.makeText(requireContext(), "Private item was not found", Toast.LENGTH_LONG).show(); return; }
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Move to Private Recycle Bin?")
                    .setMessage("The encrypted item can be restored from Private Recycle Bin.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Move", (d, w) -> new Thread(() -> {
                        try {
                            VaultStore.moveToRecycleBin(requireContext(), encrypted); preview.delete();
                            if (isAdded()) requireActivity().runOnUiThread(() -> {
                                int current = files.indexOf(preview); if (current >= 0) { files.remove(current); notifyItemRemoved(current); }
                                empty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
                                Toast.makeText(requireContext(), "Item moved to Private Recycle Bin", Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception error) { if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Unable to move private item", Toast.LENGTH_LONG).show()); }
                    }).start()).show();
        }

        @Override public int getItemCount() { return files.size(); }
    }

    private void showRecycleBin() {
        new Thread(() -> {
            List<File> files = VaultStore.recycleBin(requireContext());
            String[] labels = new String[files.size()];
            for (int i = 0; i < files.size(); i++) labels[i] = VaultStore.displayName(requireContext(), files.get(i));
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (labels.length == 0) { Toast.makeText(requireContext(), "Private Recycle Bin is empty", Toast.LENGTH_SHORT).show(); return; }
                new MaterialAlertDialogBuilder(requireContext()).setTitle("Private Recycle Bin")
                        .setItems(labels, (d, which) -> recycleAction(files.get(which), labels[which]))
                        .setNegativeButton("Close", null).show();
            });
        }).start();
    }

    private void recycleAction(File item, String name) {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(name)
                .setItems(new String[]{"Restore to Private Vault", "Permanently Delete"}, (d, which) -> {
                    if (which == 0) new Thread(() -> { try {
                        VaultStore.restoreFromRecycleBin(requireContext(), item);
                        if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Restored to Private Vault", Toast.LENGTH_SHORT).show());
                    } catch (Exception error) { if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Restore failed", Toast.LENGTH_LONG).show()); } }).start();
                    else new MaterialAlertDialogBuilder(requireContext()).setTitle("Permanently delete this item?")
                            .setMessage("This action cannot be undone.").setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", (x, y) -> { VaultStore.permanentlyDelete(requireContext(), item); Toast.makeText(requireContext(), "Private item deleted", Toast.LENGTH_SHORT).show(); }).show();
                }).show();
    }
}
