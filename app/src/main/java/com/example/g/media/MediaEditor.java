package com.example.g.media;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import com.example.g.core.compatibility.AndroidCompatibility;

public final class MediaEditor {
    private MediaEditor() { }
    public interface Callback { void done(Uri uri, Exception error); }

    public static void createEditedCopy(Context context, Uri source, String sourceName,
                                        int rotation, float aspect, Callback callback) {
        new Thread(() -> {
            Uri output = null; Exception error = null;
            try {
                ContentResolver resolver = context.getContentResolver();
                Bitmap bitmap;
                try (InputStream in = resolver.openInputStream(source)) { bitmap = BitmapFactory.decodeStream(in); }
                if (bitmap == null) throw new IllegalArgumentException("This image format cannot be edited");
                if (aspect > 0) {
                    int width = bitmap.getWidth(), height = bitmap.getHeight();
                    float current = width / (float) height;
                    int cropW = current > aspect ? Math.round(height * aspect) : width;
                    int cropH = current > aspect ? height : Math.round(width / aspect);
                    Bitmap cropped = Bitmap.createBitmap(bitmap, (width-cropW)/2, (height-cropH)/2, cropW, cropH);
                    if (cropped != bitmap) bitmap.recycle(); bitmap = cropped;
                }
                if (rotation != 0) {
                    Matrix matrix = new Matrix(); matrix.postRotate(rotation);
                    Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    if (rotated != bitmap) bitmap.recycle(); bitmap = rotated;
                }
                String base = sourceName == null ? "image" : sourceName.replaceFirst("\\.[^.]+$", "");
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, base + "_edited_" + System.currentTimeMillis() + ".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                if (AndroidCompatibility.usesScopedMediaCollection()) { values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Luma Edited"); values.put(MediaStore.Images.Media.IS_PENDING, 1); }
                output = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (output == null) throw new IllegalStateException("Could not create edited image");
                try (OutputStream out = resolver.openOutputStream(output)) { if (out == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)) throw new IllegalStateException("Could not save image"); }
                bitmap.recycle();
                if (AndroidCompatibility.usesScopedMediaCollection()) { values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0); resolver.update(output, values, null, null); }
            } catch (Exception e) { error = e; }
            Uri result = output; Exception failure = error;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.done(result, failure));
        }).start();
    }

    public static void createManualCrop(Context context,Uri source,String name,float left,float top,float right,float bottom,Callback callback){new Thread(()->{Uri output=null;Exception error=null;try{Bitmap bitmap;try(InputStream in=context.getContentResolver().openInputStream(source)){bitmap=BitmapFactory.decodeStream(in);}if(bitmap==null)throw new IllegalArgumentException("Cannot decode image");int x=Math.max(0,Math.round(left*bitmap.getWidth())),y=Math.max(0,Math.round(top*bitmap.getHeight()));int w=Math.min(bitmap.getWidth()-x,Math.max(1,Math.round((right-left)*bitmap.getWidth()))),h=Math.min(bitmap.getHeight()-y,Math.max(1,Math.round((bottom-top)*bitmap.getHeight())));Bitmap cropped=Bitmap.createBitmap(bitmap,x,y,w,h);ContentValues values=new ContentValues();String base=(name==null?"image":name).replaceFirst("\\.[^.]+$","");values.put(MediaStore.Images.Media.DISPLAY_NAME,base+"_crop_"+System.currentTimeMillis()+".jpg");values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");if(AndroidCompatibility.usesScopedMediaCollection()){values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Luma Edited");values.put(MediaStore.Images.Media.IS_PENDING,1);}output=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(output==null)throw new IllegalStateException("Could not create crop");try(OutputStream out=context.getContentResolver().openOutputStream(output)){if(out==null||!cropped.compress(Bitmap.CompressFormat.JPEG,94,out))throw new IOException("Could not save crop");}if(AndroidCompatibility.usesScopedMediaCollection()){values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);context.getContentResolver().update(output,values,null,null);}cropped.recycle();if(cropped!=bitmap)bitmap.recycle();}catch(Exception e){error=e;}Uri result=output;Exception failure=error;new android.os.Handler(android.os.Looper.getMainLooper()).post(()->callback.done(result,failure));}).start();}
}
