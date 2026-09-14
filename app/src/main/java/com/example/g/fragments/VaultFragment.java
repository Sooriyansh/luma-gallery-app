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
import com.example.g.security.VaultStore;
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
        ((TextView) view.findViewById(R.id.eye_status)).setText(
                "Eye unlocked - locks again in " + Math.max(1, remaining / 1000) + " seconds");
        handler.postDelayed(expire, remaining);
        new Thread(() -> {
            List<File> files = VaultStore.decryptPreviews(requireContext());
            if (isAdded()) requireActivity().runOnUiThread(() -> {
                grid.setAdapter(new EyeAdapter(files, empty));
                empty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
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
                    .setPositiveButton("Restore to gallery", (dialog, which) -> restore(preview)).show();
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
                    .setNegativeButton("Restore to gallery", (ignored, which) -> restore(preview))
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
                    if (!encrypted.delete()) throw new java.io.IOException("Restored, but could not remove encrypted copy");
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

        @Override public int getItemCount() { return files.size(); }
    }
}
