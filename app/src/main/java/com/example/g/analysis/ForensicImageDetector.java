package com.example.g.analysis;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.example.g.BuildConfig;
import java.util.ArrayList;

/** Multi-layer pipeline. Every available check runs before reliability-weighted fusion. */
public final class ForensicImageDetector {
    private static final String TAG="LumaImageAnalysis";
    private final Context context;
    public ForensicImageDetector(Context context){this.context=context.getApplicationContext();}

    public DetectionResult analyze(Uri uri)throws Exception{
        MediaSourceEvidence source=MediaSourceEvidence.detect(context,uri);
        CameraExifEvidence camera=CameraExifEvidence.analyze(context,uri,source);
        EmbeddedAuthenticityEvidence embedded=EmbeddedAuthenticityEvidence.analyze(context,uri);
        ImageForensicEvidence forensic=ImageForensicEvidence.analyze(context,uri);
        VisualAiModel.Result visual=VisualAiModel.analyze(context,uri);
        ImageEvidenceFusion.Input input=new ImageEvidenceFusion.Input();
        input.sourceType=source.type;input.screenshotScore=source.screenshotConfidence;input.cameraScore=camera.score;
        input.cameraPipelineScore=forensic.cameraPipelineScore;
        input.visualModelAvailable=visual.valid;input.visualAiScore=Math.round(visual.aiProbability*100);input.availableMlModels=visual.valid?1:0;
        input.generatorMetadataScore=embedded.metadataScore;input.provenanceScore=embedded.provenanceScore;
        input.watermarkScore=embedded.watermarkScore;input.editingScore=embedded.editingScore;
        input.noiseScore=forensic.noiseScore;input.frequencyScore=forensic.frequencyScore;input.ganScore=forensic.ganScore;
        input.diffusionScore=forensic.diffusionScore;input.pixelScore=forensic.pixelScore;
        input.compressionScore=forensic.compressionScore;input.suspiciousRegionScore=forensic.suspiciousRegionScore;
        input.likelyAiThreshold=AnalysisSettings.confidenceThreshold(context);
        ImageEvidenceFusion.Output fused=ImageEvidenceFusion.fuse(input);
        ArrayList<String> signals=new ArrayList<>();
        signals.add("Classification: "+fused.confidenceLevel);
        signals.add("AI Probability: "+fused.aiProbability+"%");
        signals.add("Real Probability: "+fused.realProbability+"%");
        signals.add("Confidence Level: "+fused.confidenceLevel);
        signals.add("Media Type: "+fused.mediaType);
        signals.add("");
        signals.add("C2PA Evidence: "+embedded.provenanceStatus);
        signals.add("Watermark Evidence: "+embedded.watermarkStatus);
        signals.add("Metadata Evidence: "+embedded.metadataScore+"% supporting AI");
        signals.add("Camera Sensor Evidence: "+Math.min(100,camera.score+forensic.cameraPipelineScore/2)+"% supporting real (EXIF and statistical pipeline proxies)");
        signals.add("Noise Evidence: "+forensic.noiseScore+"% supporting AI");
        signals.add("Frequency Evidence: "+forensic.frequencyScore+"% supporting AI");
        signals.add("GAN Evidence: "+forensic.ganScore+"% supporting AI");
        signals.add("Diffusion Evidence: "+forensic.diffusionScore+"% supporting AI");
        signals.add("Compression Evidence: "+forensic.compressionScore+"% supporting AI");
        signals.add("Pixel Forensics: "+forensic.pixelScore+"% supporting AI");
        signals.add("Semantic Evidence: unavailable (no anatomy/OCR/physics model bundled)");
        signals.add("ML Ensemble Evidence: "+(visual.valid?"1 detector available; score "+Math.round(visual.aiProbability*100)+"%. A single model cannot determine the label.":"No validated visual model result available"));
        signals.add("Suspicious-region probability: "+forensic.suspiciousRegionScore+"%");
        signals.add("");
        signals.add("Top reasons supporting AI:");
        for(String reason:fused.topAiReasons)signals.add("• "+reason);
        signals.add("Top reasons supporting Real:");
        for(String reason:fused.topRealReasons)signals.add("• "+reason);
        signals.add("");
        signals.add("Internal detection details:");
        signals.add("Media source: "+source.type);
        if(source.screenshotConfidence>0)signals.add("Screenshot evidence: "+source.screenshotConfidence+"%");
        if(camera.score>0)signals.add("Camera metadata evidence: "+camera.score+"% (supporting evidence only)");
        signals.addAll(camera.signals);signals.addAll(embedded.signals);signals.addAll(forensic.signals);
        signals.add(visual.valid?"Visual AI probability: "+Math.round(visual.aiProbability*100)+"% (multi-crop median)":"Visual AI classifier unavailable: "+visual.error);
        signals.add("Final assessment: "+fused.summary);
        signals.add("Limitations: scores are an on-device probabilistic assessment, not proof. The bundled model has not been calibrated on this user's device or every unseen generator.");
        String provenanceStatus=embedded.provenanceStatus;
        if(BuildConfig.DEBUG)Log.d(TAG,"file="+source.displayName+" source="+source.type+" screenshot="+source.screenshotConfidence+
                " cameraExif="+camera.score+" modelLoaded="+visual.modelLoaded+" inferenceStarted="+visual.inferenceStarted+
                " decoded="+visual.width+"x"+visual.height+" inputShape="+visual.inputShape+" inputRange="+visual.inputMin+".."+visual.inputMax+
                " rawRealOutputs="+visual.rawOutput+" aiProbability="+visual.aiProbability+" realProbability="+visual.realProbability+
                " predictedClass="+visual.predictedClass+" provenance="+provenanceStatus+" final="+fused.status+
                " finalConfidence="+fused.confidence+" reason="+fused.summary+" error="+visual.error);
        return new DetectionResult(fused.status,fused.aiProbability,"On-device multi-layer evidence fusion",signals,
                Math.round(visual.aiProbability*100),embedded.metadataScore,fused.forensicScore,embedded.provenanceScore,
                embedded.watermarkScore,embedded.generator,fused.mediaType,source.type,
                Math.min(100,camera.score+forensic.cameraPipelineScore/2),source.screenshotConfidence,visual.valid,
                provenanceStatus,embedded.watermarkStatus,fused.confidenceLevel,forensic.noiseScore,forensic.frequencyScore,
                forensic.ganScore,forensic.diffusionScore,forensic.pixelScore,forensic.compressionScore,0,
                forensic.suspiciousRegionScore,fused.topAiReasons,fused.topRealReasons);
    }
}
