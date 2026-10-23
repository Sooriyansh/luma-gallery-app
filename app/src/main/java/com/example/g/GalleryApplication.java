package com.example.g;
import android.app.Application;
import com.google.android.material.color.DynamicColors;
import com.example.g.analysis.AnalysisScheduler;
public class GalleryApplication extends Application { @Override public void onCreate(){super.onCreate(); DynamicColors.applyToActivitiesIfAvailable(this); AnalysisScheduler.stopLibraryWatch(this);} }
