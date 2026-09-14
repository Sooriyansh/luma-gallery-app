package com.example.g.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.g.R;
import com.example.g.models.Album;

public final class AlbumAdapter extends ListAdapter<Album, AlbumAdapter.Holder> {
    public interface Listener { void open(Album album); }
    private final Listener listener;

    public AlbumAdapter(Listener listener) {
        super(new DiffUtil.ItemCallback<Album>() {
            @Override public boolean areItemsTheSame(@NonNull Album a, @NonNull Album b) { return a.id.equals(b.id); }
            @Override public boolean areContentsTheSame(@NonNull Album a, @NonNull Album b) { return a.equals(b); }
        });
        this.listener = listener;
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_album, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Album album = getItem(position);
        holder.name.setText(album.name);
        holder.count.setText(album.count + (album.count == 1 ? " item" : " items")
                + (album.path == null || album.path.isEmpty() ? "" : "  •  " + album.path));
        Glide.with(holder.cover).load(album.coverUri).centerCrop().thumbnail(.15f).into(holder.cover);
        holder.itemView.setOnClickListener(view -> listener.open(album));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView cover; final TextView name; final TextView count;
        Holder(View view) { super(view); cover = view.findViewById(R.id.album_cover); name = view.findViewById(R.id.album_name); count = view.findViewById(R.id.album_count); }
    }
}
