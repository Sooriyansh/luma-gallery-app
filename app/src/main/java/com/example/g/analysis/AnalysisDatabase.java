package com.example.g.analysis;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {MediaAnalysisEntity.class}, version = 4, exportSchema = false)
public abstract class AnalysisDatabase extends RoomDatabase {
    private static volatile AnalysisDatabase instance;
    public abstract MediaAnalysisDao analyses();
    public static AnalysisDatabase get(Context context) {
        if (instance == null) synchronized (AnalysisDatabase.class) {
            if (instance == null) instance = Room.databaseBuilder(context.getApplicationContext(), AnalysisDatabase.class, "authenticity.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build();
        }
        return instance;
    }
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            // ALTER TABLE requires defaults for new NOT NULL columns. Room treats those
            // defaults as a different schema, so rebuild the table instead. This also
            // preserves analysis records made with version 1 of the app.
            db.execSQL("ALTER TABLE media_analysis RENAME TO media_analysis_v1");
            db.execSQL("CREATE TABLE IF NOT EXISTS media_analysis ("
                    + "uri TEXT NOT NULL PRIMARY KEY, fileName TEXT, mimeType TEXT, "
                    + "modified INTEGER NOT NULL, status TEXT, confidence INTEGER NOT NULL, "
                    + "source TEXT, reason TEXT, metadata TEXT, scannedAt INTEGER NOT NULL, "
                    + "mediaStoreId INTEGER NOT NULL, fileHash TEXT, createdAt INTEGER NOT NULL, "
                    + "modelConfidence INTEGER NOT NULL, metadataScore INTEGER NOT NULL, "
                    + "artifactScore INTEGER NOT NULL, provenanceScore INTEGER NOT NULL, "
                    + "detectedGenerator TEXT, modelVersion TEXT)");
            db.execSQL("INSERT INTO media_analysis (uri, fileName, mimeType, modified, status, confidence, source, reason, metadata, scannedAt, mediaStoreId, fileHash, createdAt, modelConfidence, metadataScore, artifactScore, provenanceScore, detectedGenerator, modelVersion) "
                    + "SELECT uri, fileName, mimeType, modified, status, confidence, source, reason, metadata, scannedAt, 0, NULL, 0, 0, 0, 0, 0, NULL, NULL FROM media_analysis_v1");
            db.execSQL("DROP TABLE media_analysis_v1");
        }
    };
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE media_analysis RENAME TO media_analysis_v2");
            db.execSQL("CREATE TABLE IF NOT EXISTS media_analysis ("
                    + "uri TEXT NOT NULL PRIMARY KEY, fileName TEXT, mimeType TEXT, modified INTEGER NOT NULL, "
                    + "status TEXT, confidence INTEGER NOT NULL, source TEXT, reason TEXT, metadata TEXT, scannedAt INTEGER NOT NULL, "
                    + "mediaStoreId INTEGER NOT NULL, fileHash TEXT, createdAt INTEGER NOT NULL, modelConfidence INTEGER NOT NULL, "
                    + "metadataScore INTEGER NOT NULL, artifactScore INTEGER NOT NULL, provenanceScore INTEGER NOT NULL, "
                    + "detectedGenerator TEXT, modelVersion TEXT, mediaType TEXT, sourceType TEXT, cameraEvidence INTEGER NOT NULL, "
                    + "screenshotConfidence INTEGER NOT NULL, visualModelAvailable INTEGER NOT NULL, provenanceStatus TEXT)");
            db.execSQL("INSERT INTO media_analysis (uri,fileName,mimeType,modified,status,confidence,source,reason,metadata,scannedAt,"
                    + "mediaStoreId,fileHash,createdAt,modelConfidence,metadataScore,artifactScore,provenanceScore,detectedGenerator,modelVersion,"
                    + "mediaType,sourceType,cameraEvidence,screenshotConfidence,visualModelAvailable,provenanceStatus) "
                    + "SELECT uri,fileName,mimeType,modified,status,confidence,source,reason,metadata,scannedAt,mediaStoreId,fileHash,createdAt,"
                    + "modelConfidence,metadataScore,artifactScore,provenanceScore,detectedGenerator,modelVersion,'','',0,0,0,'' FROM media_analysis_v2");
            db.execSQL("DROP TABLE media_analysis_v2");
        }
    };
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE media_analysis ADD COLUMN aiProbability INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE media_analysis ADD COLUMN realProbability INTEGER NOT NULL DEFAULT 100");
            db.execSQL("ALTER TABLE media_analysis ADD COLUMN watermarkScore INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE media_analysis ADD COLUMN noiseScore INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE media_analysis ADD COLUMN frequencyScore INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE media_analysis ADD COLUMN ganScore INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE media_analysis ADD COLUMN diffusionScore INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE media_analysis ADD COLUMN pixelScore INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE media_analysis ADD COLUMN compressionScore INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE media_analysis ADD COLUMN semanticScore INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE media_analysis ADD COLUMN suspiciousRegionScore INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE media_analysis ADD COLUMN confidenceLevel TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE media_analysis ADD COLUMN watermarkStatus TEXT DEFAULT ''");
        }
    };
}
