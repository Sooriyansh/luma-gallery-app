package com.example.g.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.g.R;
import com.example.g.adapters.MediaAdapter;
import com.example.g.models.MediaItem;
import com.example.g.repositories.MediaRepository;
import com.example.g.ui.SystemBarInsets;
import com.example.g.analysis.AnalysisStatus;
import com.example.g.analysis.MediaAnalysisEntity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SearchActivity extends AppCompatActivity {
    private final List<MediaItem> all = new ArrayList<>();
    private final MediaAdapter adapter = new MediaAdapter();
    private final Map<String, MediaAnalysisEntity> analyses = new HashMap<>();
    private String queryText = "";
    private int activeFilter = R.id.filter_all;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_search);
        SystemBarInsets.screen(getWindow(), findViewById(android.R.id.content));
        MaterialToolbar toolbar=findViewById(R.id.search_toolbar);toolbar.setNavigationIcon(R.drawable.ic_arrow_back);toolbar.setNavigationContentDescription("Back to gallery");toolbar.setNavigationOnClickListener(v->finish());
        RecyclerView results = findViewById(R.id.results);
        int minimum = (int) (124 * getResources().getDisplayMetrics().density);
        results.setLayoutManager(new GridLayoutManager(this,
                Math.max(2, getResources().getDisplayMetrics().widthPixels / minimum)));
        results.setAdapter(adapter);
        MediaRepository.get(this).load(1000, 0, (items, error) -> runOnUiThread(() -> {
            all.addAll(items);
            applyFilter();
        }));
        com.example.g.analysis.AnalysisRepository.observeAll(this).observe(this,entries->{analyses.clear();for(MediaAnalysisEntity entry:entries)analyses.put(entry.uri,entry);adapter.setAnalyses(analyses);applyFilter();});
        ((ChipGroup)findViewById(R.id.search_filters)).setOnCheckedStateChangeListener((group,ids)->{if(!ids.isEmpty()){activeFilter=ids.get(0);applyFilter();}});
        ((EditText) findViewById(R.id.query)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                queryText = value.toString().toLowerCase(Locale.ROOT); applyFilter();
            }
            @Override public void afterTextChanged(Editable value) { }
        });
    }

    private void applyFilter(){List<MediaItem> filtered=new ArrayList<>();for(MediaItem item:all){String searchable=((item.name==null?"":item.name)+" "+(item.bucketName==null?"":item.bucketName)+" "+(item.relativePath==null?"":item.relativePath)+" "+(item.mime==null?"":item.mime)+" "+DateFormat.getDateInstance().format(new Date(Math.max(item.dateTaken,item.dateModified*1000)))).toLowerCase(Locale.ROOT);if(!searchable.contains(queryText))continue;MediaAnalysisEntity analysis=analyses.get(item.uri.toString());boolean include=activeFilter==R.id.filter_all||(activeFilter==R.id.filter_screenshots&&searchable.contains("screenshot"))||(activeFilter==R.id.filter_downloads&&searchable.contains("download"))||(activeFilter==R.id.filter_ai&&analysis!=null&&AnalysisStatus.LIKELY_AI.equals(analysis.status))||(activeFilter==R.id.filter_edited&&analysis!=null&&AnalysisStatus.POSSIBLY_EDITED.equals(analysis.status))||(activeFilter==R.id.filter_favorites&&getSharedPreferences("favorites",0).getBoolean(item.uri.toString(),false));if(include)filtered.add(item);}adapter.submitList(filtered);}
}
