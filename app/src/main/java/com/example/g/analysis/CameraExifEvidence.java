package com.example.g.analysis;

import android.content.Context;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Scores multiple internally related camera fields; EXIF presence alone never proves authenticity. */
public final class CameraExifEvidence {
    public final int score; public final List<String> signals; public final String software;
    private CameraExifEvidence(int score,List<String> signals,String software){this.score=Math.min(100,score);this.signals=signals;this.software=software;}
    public static CameraExifEvidence analyze(Context context,Uri uri,MediaSourceEvidence source){
        int score=MediaSourceEvidence.CAMERA.equals(source.type)?20:0;ArrayList<String> signals=new ArrayList<>();String software="";
        try(InputStream stream=context.getContentResolver().openInputStream(uri)){
            if(stream==null)return new CameraExifEvidence(score,signals,software);ExifInterface exif=new ExifInterface(stream);
            String make=value(exif,ExifInterface.TAG_MAKE),model=value(exif,ExifInterface.TAG_MODEL);
            String original=value(exif,ExifInterface.TAG_DATETIME_ORIGINAL),digitized=value(exif,ExifInterface.TAG_DATETIME_DIGITIZED);
            software=value(exif,ExifInterface.TAG_SOFTWARE);
            if(!make.isEmpty()&&!model.isEmpty()){score+=18;signals.add("Camera make and model are present");}
            if(!original.isEmpty()){score+=15;signals.add("Original capture time is present");}
            if(!digitized.isEmpty()){score+=5;}
            int exposure=0;
            if(!value(exif,ExifInterface.TAG_EXPOSURE_TIME).isEmpty())exposure++;
            if(!value(exif,ExifInterface.TAG_F_NUMBER).isEmpty())exposure++;
            if(!value(exif,ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY).isEmpty())exposure++;
            if(!value(exif,ExifInterface.TAG_FOCAL_LENGTH).isEmpty())exposure++;
            score+=exposure*8;if(exposure>=3)signals.add("Exposure, ISO and lens capture fields are consistent");
            if(!value(exif,ExifInterface.TAG_LENS_MODEL).isEmpty())score+=5;
            int exifWidth=exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH,0),exifHeight=exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH,0);
            if(exifWidth>0&&exifHeight>0&&dimensionsMatch(exifWidth,exifHeight,source.width,source.height)){score+=10;signals.add("EXIF dimensions match MediaStore");}
            if(MediaSourceEvidence.CAMERA.equals(source.type))signals.add("Stored in the camera capture location");
        }catch(Exception ignored){ }
        return new CameraExifEvidence(score,signals,software);
    }
    private static boolean dimensionsMatch(int a,int b,int x,int y){return (a==x&&b==y)||(a==y&&b==x);}
    private static String value(ExifInterface exif,String tag){String value=exif.getAttribute(tag);return value==null?"":value.trim();}
}
