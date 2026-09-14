package com.example.g.analysis;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Measurements in this class are real, local, and independently inspectable. They intentionally
 * stop short of labeling normal motion or compression as AI without a validated model provider.
 */
public final class ForensicVideoDetector implements VideoAiDetector {
    private static final List<String> AI_TERMS=Arrays.asList("stable diffusion","midjourney","dall-e","dall e","openai","firefly","runway","sora","pika","kling","luma","dream machine","veo","hailuo","minimax","gen-2","gen-3","gen-4","seedance","wan2","cogvideo","hunyuanvideo");
    private static final List<String> EDIT_TERMS=Arrays.asList("photoshop","premiere","after effects","capcut","davinci","final cut","canva");
    private final Context context; private final ContentResolver resolver;
    public ForensicVideoDetector(Context context) { this.context=context.getApplicationContext(); this.resolver=this.context.getContentResolver(); }
    @Override public VideoDetectionResult analyze(Uri uri, Progress progress) throws Exception {
        progress.update("Reading video metadata",5); MediaMetadataRetriever r=new MediaMetadataRetriever(); r.setDataSource(context,uri);
        long duration=number(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        int width=(int)number(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)), height=(int)number(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        long bitrate=number(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
        String title=value(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)), author=value(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)), date=value(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE));
        ArrayList<VideoDetectionResult.ModelResult> signals=new ArrayList<>(), models=new ArrayList<>(), limits=new ArrayList<>();
        String metadata=(displayName(uri)+" "+title+" "+author+" "+date+" "+readHeader(uri)).toLowerCase(Locale.US);
        boolean ai=false, edited=false; for(String term:AI_TERMS)if(metadata.contains(term)){signals.add(row("AI-related metadata",term,null));ai=true;} for(String term:EDIT_TERMS)if(metadata.contains(term)){signals.add(row("Editing metadata",term,null));edited=true;}
        boolean c2pa=metadata.contains("c2pa")||metadata.contains("jumbf")||metadata.contains("content credentials");
        if(c2pa) signals.add(row("Provenance container","C2PA/JUMBF marker present; signature verification provider unavailable",null));
        progress.update("Assessing quality",15); int quality=quality(width,height,bitrate,duration); signals.add(row("Video quality",width+"×"+height+", "+bitrate+" bps",quality));
        if(quality<45) limits.add(row("Quality limitation","Heavy compression or low resolution reduces forensic reliability",null));
        progress.update("Adaptive scene and frame extraction",25); FrameReport frames=sample(r,duration,progress); r.release();
        signals.add(row("Adaptive sampling",frames.count+" unique representative frames across "+frames.scenes+" visual scenes",null));
        if(frames.count<20) limits.add(row("Sampling limitation","Video duration or decode availability yielded fewer than 20 unique frames",null));
        progress.update("Recording unavailable model providers",88);
        models.add(row("Image AI classifier","Detector unavailable: install a validated model provider",null));
        models.add(row("Deepfake / face tracker","Detector unavailable: install a validated face/deepfake provider",null));
        models.add(row("Audio / lip-sync analyzer","Detector unavailable: install a validated audio model provider",null));
        models.add(row("C2PA verifier","Detector unavailable: marker presence is not cryptographic verification",null));
        limits.add(row("Detection coverage","No validated image, deepfake, audio, or C2PA verification model is configured",null));
        String classification=ai?AnalysisStatus.LIKELY_AI:(edited?AnalysisStatus.POSSIBLY_EDITED:AnalysisStatus.UNKNOWN);
        int confidence=ai?(c2pa?88:85):(edited?55:0); int reliability=Math.min(85,Math.max(20,quality));
        String strength=ai?"STRONG_METADATA_EVIDENCE":(edited?"EDITING_METADATA_ONLY":"INSUFFICIENT_EVIDENCE");
        progress.update("Calibrating evidence",100);
        return new VideoDetectionResult(classification,confidence,reliability,strength,null,frames.consistency,null,null,ai?85:(edited?55:0),c2pa?"UNVERIFIED_CONTAINER_PRESENT":"NOT_AVAILABLE",quality,frames.count,frames.scenes,models,signals,limits);
    }
    private FrameReport sample(MediaMetadataRetriever r,long duration,Progress progress) {
        int targets=duration<=30_000?20:duration<=120_000?40:duration<=600_000?80:100; ArrayList<int[]> fingerprints=new ArrayList<>(); int scenes=0; long previous=-1; int consistencySum=0, comparisons=0;
        for(int i=0;i<targets;i++){long time=targets==1?0:(duration*i/(targets-1)); Bitmap b=null;try{b=r.getFrameAtTime(time*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);if(b==null)continue;int[] fp=fingerprint(b);b.recycle();int difference=previous<0?100:distance(fingerprints.get(fingerprints.size()-1),fp);if(previous<0||difference>7){fingerprints.add(fp);if(difference>22)scenes++;if(previous>=0){consistencySum+=Math.max(0,100-difference);comparisons++;}previous=time;}}catch(Exception ignored){}progress.update("Adaptive frame extraction",25+(int)(55d*(i+1)/targets));}
        return new FrameReport(fingerprints.size(),Math.max(1,scenes),comparisons==0?null:consistencySum/comparisons);
    }
    private static int[] fingerprint(Bitmap b){int[] result=new int[16];int w=Math.max(1,b.getWidth()/4),h=Math.max(1,b.getHeight()/4);for(int y=0;y<4;y++)for(int x=0;x<4;x++){int p=b.getPixel(Math.min(b.getWidth()-1,x*w),Math.min(b.getHeight()-1,y*h));result[y*4+x]=((p>>16)&255)+((p>>8)&255)+(p&255);}return result;}
    private static int distance(int[] a,int[] b){int total=0;for(int i=0;i<a.length;i++)total+=Math.abs(a[i]-b[i]);return Math.min(100,total/(a.length*6));}
    private int quality(int w,int h,long bitrate,long duration){int resolution=w*h>=1280*720?45:w*h>=640*360?30:15;int rate=bitrate>=2_000_000?35:bitrate>=600_000?25:10;return Math.min(100,resolution+rate+(duration>0?20:0));}
    private String readHeader(Uri uri){byte[] bytes=new byte[256*1024];int n=0;try(InputStream in=resolver.openInputStream(uri)){if(in!=null)while(n<bytes.length){int read=in.read(bytes,n,bytes.length-n);if(read<0)break;n+=read;}}catch(Exception ignored){}return new String(bytes,0,n,StandardCharsets.ISO_8859_1);}
    private String displayName(Uri uri){try(Cursor cursor=resolver.query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst()){int column=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(column>=0)return value(cursor.getString(column));}}catch(Exception ignored){}return "";}
    private static long number(String s){try{return Long.parseLong(s);}catch(Exception ignored){return 0;}} private static String value(String s){return s==null?"":s;} private static VideoDetectionResult.ModelResult row(String a,String b,Integer c){return new VideoDetectionResult.ModelResult(a,b,c);}
    private static final class FrameReport {final int count,scenes;final Integer consistency;FrameReport(int count,int scenes,Integer consistency){this.count=count;this.scenes=scenes;this.consistency=consistency;}}
}
