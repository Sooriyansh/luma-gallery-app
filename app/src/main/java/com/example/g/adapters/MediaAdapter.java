package com.example.g.adapters;

import android.content.Intent;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.g.R;
import com.example.g.activities.ImageViewerActivity;
import com.example.g.activities.VideoPlayerActivity;
import com.example.g.analysis.AnalysisStatus;
import com.example.g.analysis.MediaAnalysisEntity;
import com.example.g.models.MediaItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MediaAdapter extends androidx.recyclerview.widget.ListAdapter<MediaItem, MediaAdapter.Holder> {
    public interface ClickListener { void click(MediaItem item); }
    public interface SelectionListener { void changed(List<MediaItem> selected); }

    private final ClickListener clickListener;
    private SelectionListener selectionListener;
    private final Set<String> selected = new HashSet<>();
    private final Map<String, MediaAnalysisEntity> analyses = new HashMap<>();

    public MediaAdapter() { this(null); }

    public MediaAdapter(ClickListener listener) {
        super(new DiffUtil.ItemCallback<MediaItem>() {
            @Override public boolean areItemsTheSame(@NonNull MediaItem a, @NonNull MediaItem b) { return a.id == b.id; }
            @Override public boolean areContentsTheSame(@NonNull MediaItem a, @NonNull MediaItem b) { return a.equals(b); }
        });
        clickListener = listener;
        setHasStableIds(true);
    }

    public void setAnalyses(Map<String, MediaAnalysisEntity> values) { analyses.clear(); analyses.putAll(values); notifyDataSetChanged(); }
    public void setSelectionListener(SelectionListener listener) { selectionListener = listener; }
    public boolean isSelecting() { return !selected.isEmpty(); }
    public List<MediaItem> selectedItems() { List<MediaItem> out = new ArrayList<>(); for (MediaItem item : getCurrentList()) if (selected.contains(item.uri.toString())) out.add(item); return out; }
    public void selectAll() { for (MediaItem item : getCurrentList()) selected.add(item.uri.toString()); notifyDataSetChanged(); dispatch(); }
    public void selectPosition(int position) { if (position < 0 || position >= getItemCount()) return; MediaItem item = getItem(position); if (selected.add(item.uri.toString())) { notifyItemChanged(position); dispatch(); } }
    public void clearSelection() { selected.clear(); notifyDataSetChanged(); dispatch(); }
    private void toggle(MediaItem item) { String key = item.uri.toString(); if (!selected.add(key)) selected.remove(key); notifyDataSetChanged(); dispatch(); }
    private void dispatch() { if (selectionListener != null) selectionListener.changed(selectedItems()); }

    @Override public long getItemId(int position) { return getItem(position).id; }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_media, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        MediaItem item = getItem(position);
        Glide.with(holder.itemView).load(item.uri).thumbnail(.15f).centerCrop()
                .placeholder(R.drawable.image_placeholder).error(R.drawable.image_error).into(holder.image);
        MediaAnalysisEntity analysis = analyses.get(item.uri.toString());
        if (analysis != null && AnalysisStatus.LIKELY_AI.equals(analysis.status) && analysis.confidence >= 85) {
            showAnalysisBadge(holder, item.isVideo() ? "AI Video" : "AI Generated");
        } else if (analysis != null && AnalysisStatus.POSSIBLY_AI.equals(analysis.status)) {
            showPossibleBadge(holder, "Possible AI");
        } else if (analysis != null && AnalysisStatus.POSSIBLY_EDITED.equals(analysis.status)) {
            showEditedBadge(holder, "Edited");
        } else if (analysis != null && "Screenshot".equals(analysis.mediaType)) {
            showNeutralBadge(holder, "Screenshot");
        } else if (analysis != null && AnalysisStatus.LIKELY_REAL.equals(analysis.status)) {
            showVerifiedBadge(holder, "Camera");
        } else if (analysis != null && AnalysisStatus.PENDING.equals(analysis.status)) {
            showNeutralBadge(holder, item.isVideo() ? "Checking video…" : "Checking AI…");
        } else if (item.isGif()) {
            showNeutralBadge(holder, "GIF");
        } else if (item.isVideo()) {
            showNeutralBadge(holder, DateUtils.formatElapsedTime(item.duration / 1000));
        } else holder.badge.setVisibility(View.GONE);

        boolean checked = selected.contains(item.uri.toString());
        holder.itemView.setAlpha(checked ? .72f : 1f);
        holder.indicator.setVisibility(checked ? View.VISIBLE : View.GONE);
        holder.tick.setVisibility(checked ? View.VISIBLE : View.GONE);
        holder.card.setStrokeWidth(checked ? 5 : 0);
        String analysisDescription = analysis == null ? "AI check queued"
                : AnalysisStatus.PENDING.equals(analysis.status) ? "AI check in progress"
                : AnalysisStatus.LIKELY_AI.equals(analysis.status) ? "AI-generation indicators found"
                : AnalysisStatus.POSSIBLY_AI.equals(analysis.status) ? "possible AI indicators found"
                : AnalysisStatus.LIKELY_REAL.equals(analysis.status) ? "camera capture evidence found"
                : "no confirmed AI-generation indicators";
        holder.itemView.setContentDescription(item.name + ", " + analysisDescription + (checked ? ", selected" : ""));
        holder.itemView.setOnLongClickListener(view -> { if (clickListener == null) { view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); toggle(item); return true; } return false; });
        holder.itemView.setOnClickListener(view -> {
            if (clickListener != null) { clickListener.click(item); return; }
            if (isSelecting()) { toggle(item); return; }
            Intent intent = new Intent(view.getContext(), item.isVideo() ? VideoPlayerActivity.class : ImageViewerActivity.class);
            intent.setData(item.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra("name", item.name);
            view.getContext().startActivity(intent);
        });
    }

    private void showAnalysisBadge(Holder holder, String label) {
        holder.badge.setVisibility(View.VISIBLE);
        holder.badge.setText(label);
        androidx.core.view.ViewCompat.setBackgroundTintList(holder.badge,
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#C5221F")));
    }

    private void showEditedBadge(Holder holder, String label) {
        holder.badge.setVisibility(View.VISIBLE);
        holder.badge.setText(label);
        androidx.core.view.ViewCompat.setBackgroundTintList(holder.badge,
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#6D4C41")));
    }

    private void showPossibleBadge(Holder holder, String label) {
        holder.badge.setVisibility(View.VISIBLE); holder.badge.setText(label);
        androidx.core.view.ViewCompat.setBackgroundTintList(holder.badge,
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#B06000")));
    }

    private void showVerifiedBadge(Holder holder, String label) {
        holder.badge.setVisibility(View.VISIBLE); holder.badge.setText(label);
        androidx.core.view.ViewCompat.setBackgroundTintList(holder.badge,
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#176B45")));
    }

    private void showNeutralBadge(Holder holder, String label) {
        holder.badge.setVisibility(View.VISIBLE);
        holder.badge.setText(label);
        androidx.core.view.ViewCompat.setBackgroundTintList(holder.badge,
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#B3000000")));
    }

    @Override public void onViewRecycled(@NonNull Holder holder) {
        Glide.with(holder.itemView).clear(holder.image);
        super.onViewRecycled(holder);
    }

    public static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView badge, tick;
        final View indicator;
        final com.google.android.material.card.MaterialCardView card;
        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.thumbnail);
            badge = view.findViewById(R.id.badge);
            tick = view.findViewById(R.id.selection_tick);
            indicator = view.findViewById(R.id.selection_indicator);
            card = view.findViewById(R.id.media_card);
        }
    }
}
