package com.example.g.fragments;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.g.R;
import com.example.g.adapters.AlbumAdapter;
import com.example.g.models.Album;
import com.example.g.models.MediaItem;
import com.example.g.repositories.MediaRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AlbumsFragment extends Fragment {
    private AlbumAdapter adapter;
    private SwipeRefreshLayout swipe;
    public AlbumsFragment() { super(R.layout.fragment_albums); }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        swipe = view.findViewById(R.id.swipe_albums);
        RecyclerView grid = view.findViewById(R.id.album_grid);
        int minimum = (int) (180 * getResources().getDisplayMetrics().density);
        int spans = Math.max(2, getResources().getDisplayMetrics().widthPixels / minimum);
        grid.setLayoutManager(new GridLayoutManager(requireContext(), spans));
        adapter = new AlbumAdapter(album -> getParentFragmentManager().beginTransaction()
                .replace(R.id.container, AlbumDetailsFragment.newInstance(album.id, album.name))
                .addToBackStack("albums").commit());
        grid.setAdapter(adapter);
        swipe.setOnRefreshListener(this::loadAlbums);
        loadAlbums();
    }

    private void loadAlbums() {
        swipe.setRefreshing(true);
        MediaRepository.get(requireContext()).load(100000, 0, (items, error) -> {
            Map<String, MutableAlbum> grouped = new LinkedHashMap<>();
            for (MediaItem item : items) {
                String key = item.bucketId;
                if (key == null || key.isEmpty()) key = item.relativePath;
                if (key == null || key.isEmpty()) key = "other";
                MutableAlbum album = grouped.get(key);
                if (album == null) {
                    String name = item.bucketName;
                    if (name == null || name.trim().isEmpty()) name = folderName(item.relativePath);
                    album = new MutableAlbum(key, name, item.relativePath, item.uri, item.dateModified);
                    grouped.put(key, album);
                }
                album.count++;
                if (item.dateModified > album.latest) { album.latest = item.dateModified; album.cover = item.uri; }
            }
            List<Album> albums = new ArrayList<>();
            for (MutableAlbum value : grouped.values()) albums.add(value.freeze());
            albums.sort(Comparator.comparingLong((Album album) -> album.latestDate).reversed());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> { adapter.submitList(albums); swipe.setRefreshing(false); });
        });
    }

    private static String folderName(String path) {
        if (path == null || path.isEmpty()) return "Other";
        String clean = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int slash = clean.lastIndexOf('/');
        return slash >= 0 ? clean.substring(slash + 1) : clean;
    }

    private static final class MutableAlbum {
        final String id, name, path; android.net.Uri cover; int count; long latest;
        MutableAlbum(String id, String name, String path, android.net.Uri cover, long latest) { this.id=id; this.name=name; this.path=path; this.cover=cover; this.latest=latest; }
        Album freeze() { return new Album(id, name, path, cover, count, latest); }
    }
}
