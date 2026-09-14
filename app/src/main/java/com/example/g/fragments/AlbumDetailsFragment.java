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
import com.example.g.adapters.MediaAdapter;
import com.example.g.models.MediaItem;
import com.example.g.repositories.MediaRepository;
import java.util.ArrayList;
import java.util.List;

public final class AlbumDetailsFragment extends Fragment {
    public AlbumDetailsFragment() { super(R.layout.fragment_photos); }
    public static AlbumDetailsFragment newInstance(String id, String name) {
        AlbumDetailsFragment fragment = new AlbumDetailsFragment();
        Bundle args = new Bundle(); args.putString("id", id); args.putString("name", name); fragment.setArguments(args); return fragment;
    }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MediaAdapter adapter = new MediaAdapter();
        RecyclerView grid = view.findViewById(R.id.grid);
        int minimum = (int) (124 * getResources().getDisplayMetrics().density);
        grid.setLayoutManager(new GridLayoutManager(requireContext(), Math.max(2, getResources().getDisplayMetrics().widthPixels / minimum)));
        grid.setAdapter(adapter);
        SwipeRefreshLayout swipe = view.findViewById(R.id.swipe);
        Runnable load = () -> { swipe.setRefreshing(true); MediaRepository.get(requireContext()).load(100000, 0, (items, error) -> {
            List<MediaItem> filtered = new ArrayList<>(); String id = requireArguments().getString("id");
            for (MediaItem item : items) { String key = item.bucketId; if (key == null || key.isEmpty()) key = item.relativePath; if (key == null || key.isEmpty()) key = "other"; if (key.equals(id)) filtered.add(item); }
            if (isAdded()) requireActivity().runOnUiThread(() -> { adapter.submitList(filtered); swipe.setRefreshing(false); });
        }); };
        swipe.setOnRefreshListener(load::run); load.run();
    }
}
