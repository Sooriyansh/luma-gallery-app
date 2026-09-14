package com.example.g.analysis;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.lifecycle.LiveData;
import java.util.List;

@Dao public interface MediaAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) void upsert(MediaAnalysisEntity value);
    @Query("SELECT * FROM media_analysis WHERE uri = :uri LIMIT 1") MediaAnalysisEntity get(String uri);
    @Query("DELETE FROM media_analysis") void clear();
    @Query("SELECT * FROM media_analysis ORDER BY scannedAt DESC") List<MediaAnalysisEntity> all();
    @Query("SELECT * FROM media_analysis ORDER BY scannedAt DESC") LiveData<List<MediaAnalysisEntity>> observeAll();
}
