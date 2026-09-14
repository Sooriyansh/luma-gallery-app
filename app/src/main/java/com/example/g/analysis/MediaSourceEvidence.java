package com.example.g.analysis;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.example.g.core.compatibility.MediaStoreCompatibility;

/** MediaStore-based capture/source classification. Source is never treated as AI evidence. */
public final class MediaSourceEvidence {
    public static final String CAMERA = "Camera", SCREENSHOT = "Screenshot", SHARED = "Shared / Compressed",
            DOWNLOADED = "Downloaded", OTHER = "Other / Unknown";
    public final String type, displayName, relativePath, bucket, mime;
    public final long dateAdded, dateTaken;
    public final int width, height, screenshotConfidence;

    private MediaSourceEvidence(String type, String name, String path, String bucket, String mime,
                                long added, long taken, int width, int height, int screenshotConfidence) {
        this.type=type;this.displayName=name;this.relativePath=path;this.bucket=bucket;this.mime=mime;
        this.dateAdded=added;this.dateTaken=taken;this.width=width;this.height=height;
        this.screenshotConfidence=Math.max(0,Math.min(100,screenshotConfidence));
    }

    public static MediaSourceEvidence detect(Context context, Uri uri) {
        List<String> projection = new ArrayList<>();
        projection.add(MediaStore.MediaColumns.DISPLAY_NAME); projection.add(MediaStore.MediaColumns.MIME_TYPE);
        projection.add(MediaStore.MediaColumns.DATE_ADDED); projection.add(MediaStore.Images.ImageColumns.DATE_TAKEN);
        projection.add(MediaStore.MediaColumns.WIDTH); projection.add(MediaStore.MediaColumns.HEIGHT);
        projection.add(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME);
        if (MediaStoreCompatibility.hasRelativePath()) projection.add(MediaStore.MediaColumns.RELATIVE_PATH);
        String name="",path="",bucket="",mime="";long added=0,taken=0;int width=0,height=0;
        try (Cursor cursor=context.getContentResolver().query(uri,projection.toArray(new String[0]),null,null,null)) {
            if(cursor!=null&&cursor.moveToFirst()){
                name=text(cursor,MediaStore.MediaColumns.DISPLAY_NAME);mime=text(cursor,MediaStore.MediaColumns.MIME_TYPE);
                bucket=text(cursor,MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME);
                if(MediaStoreCompatibility.hasRelativePath())path=text(cursor,MediaStore.MediaColumns.RELATIVE_PATH);
                added=number(cursor,MediaStore.MediaColumns.DATE_ADDED);taken=number(cursor,MediaStore.Images.ImageColumns.DATE_TAKEN);
                width=(int)number(cursor,MediaStore.MediaColumns.WIDTH);height=(int)number(cursor,MediaStore.MediaColumns.HEIGHT);
            }
        } catch(Exception ignored) { }
        String combined=(name+" "+path+" "+bucket).toLowerCase(Locale.US);
        boolean screenshotFolder=combined.contains("screenshot")||combined.contains("screen capture")||combined.contains("screenrecord");
        String lowerName=name.toLowerCase(Locale.US);
        boolean screenshotName=lowerName.startsWith("screenshot_")||lowerName.startsWith("screenshot-")
                ||lowerName.startsWith("screen_")||lowerName.contains("smartselect")||lowerName.startsWith("screenshot");
        int screenshot=(screenshotFolder?55:0)+(screenshotName?30:0)+(matchesDisplay(context,width,height)?15:0);
        boolean camera=combined.contains("dcim/camera")||"camera".equals(bucket.toLowerCase(Locale.US));
        boolean shared=combined.contains("whatsapp")||combined.contains("telegram")||combined.contains("messenger")
                ||combined.contains("instagram")||combined.contains("facebook");
        boolean downloaded=combined.contains("download")||combined.contains("browser");
        String type=screenshot>=55?SCREENSHOT:camera?CAMERA:shared?SHARED:downloaded?DOWNLOADED:OTHER;
        return new MediaSourceEvidence(type,name,path,bucket,mime,added,taken,width,height,screenshot);
    }

    private static boolean matchesDisplay(Context context,int width,int height){
        if(width<=0||height<=0)return false;android.util.DisplayMetrics d=context.getResources().getDisplayMetrics();
        return close(width,d.widthPixels)&&close(height,d.heightPixels)||close(height,d.widthPixels)&&close(width,d.heightPixels);
    }
    private static boolean close(int a,int b){return Math.abs(a-b)<=Math.max(80,Math.round(b*.16f));}
    private static String text(Cursor cursor,String column){int i=cursor.getColumnIndex(column);return i<0||cursor.isNull(i)?"":cursor.getString(i);}
    private static long number(Cursor cursor,String column){int i=cursor.getColumnIndex(column);return i<0||cursor.isNull(i)?0:cursor.getLong(i);}
}
