package com.example.g.analysis;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;

/** Executes the bundled classifier using its documented tensor contract and verified label order. */
public final class VisualAiModel {
    public static final String MODEL_ASSET="ai_image_detector.tflite";
    public static final String MODEL_VERSION="juandaram-resnet50-tflite-sha256-056505d0";
    public static final String PREPROCESSING_VERSION="rgb128-stretch-and-multicrop-f32-div255-v1";
    private static final int SIZE=128;
    private static final Object LOCK=new Object();
    private static Interpreter interpreter;
    private static String loadError="";

    public static final class Result {
        public boolean modelLoaded,inferenceStarted,valid;public int width,height;public String inputShape="",rawOutput="",predictedClass="",error="";
        public float aiProbability,realProbability,inputMin,inputMax;
    }
    private VisualAiModel(){ }

    public static Result analyze(Context context,Uri uri){
        synchronized(LOCK){
            Result result=new Result();
            try{
                ensureLoaded(context);result.modelLoaded=interpreter!=null;result.inputShape="[1, 128, 128, 3] FLOAT32";
                if(interpreter==null){result.error=loadError.isEmpty()?"Model could not be loaded":loadError;return result;}
                Bitmap bitmap=decodeOriented(context,uri);if(bitmap==null){result.error="Image decode failed";return result;}
                result.width=bitmap.getWidth();result.height=bitmap.getHeight();result.inferenceStarted=true;
                List<Rect> crops=crops(bitmap.getWidth(),bitmap.getHeight());ArrayList<Float> aiScores=new ArrayList<>();ArrayList<String> raw=new ArrayList<>();
                float min=1f,max=0f;
                for(Rect crop:crops){TensorInput prepared=prepare(bitmap,crop);min=Math.min(min,prepared.min);max=Math.max(max,prepared.max);
                    ByteBuffer output=ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());interpreter.run(prepared.buffer,output);output.rewind();
                    float real=output.getFloat();if(!Float.isFinite(real)||real<0f||real>1f)throw new IllegalStateException("Invalid sigmoid output: "+real);
                    aiScores.add(1f-real);raw.add(String.format(Locale.US,"%.6f",real));
                }
                bitmap.recycle();Collections.sort(aiScores);float ai=aiScores.get(aiScores.size()/2);
                result.inputMin=min;result.inputMax=max;result.rawOutput=raw.toString();result.aiProbability=ai;result.realProbability=1f-ai;
                result.predictedClass=ai>=.5f?"AI/FAKE (class 0)":"REAL (class 1)";result.valid=true;return result;
            }catch(Exception failure){result.error=failure.getClass().getSimpleName()+": "+(failure.getMessage()==null?"Inference failed":failure.getMessage());return result;}
        }
    }

    private static void ensureLoaded(Context context){
        if(interpreter!=null)return;
        try(AssetFileDescriptor asset=context.getAssets().openFd(MODEL_ASSET);FileInputStream input=new FileInputStream(asset.getFileDescriptor())){
            MappedByteBuffer model=input.getChannel().map(FileChannel.MapMode.READ_ONLY,asset.getStartOffset(),asset.getDeclaredLength());
            Interpreter.Options options=new Interpreter.Options().setNumThreads(Math.max(2,Math.min(4,Runtime.getRuntime().availableProcessors())));
            Interpreter candidate=new Interpreter(model,options);int[] inputShape=candidate.getInputTensor(0).shape();int[] outputShape=candidate.getOutputTensor(0).shape();
            if(candidate.getInputTensor(0).dataType()!=DataType.FLOAT32||!Arrays.equals(inputShape,new int[]{1,SIZE,SIZE,3}))throw new IllegalStateException("Unexpected input tensor "+Arrays.toString(inputShape)+" "+candidate.getInputTensor(0).dataType());
            if(candidate.getOutputTensor(0).dataType()!=DataType.FLOAT32||candidate.getOutputTensor(0).numElements()!=1)throw new IllegalStateException("Unexpected output tensor "+Arrays.toString(outputShape));
            interpreter=candidate;loadError="";
        }catch(Exception error){loadError=error.getClass().getSimpleName()+": "+(error.getMessage()==null?"Model load failed":error.getMessage());}
    }

    private static Bitmap decodeOriented(Context context,Uri uri)throws Exception{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream input=context.getContentResolver().openInputStream(uri)){if(input==null)return null;BitmapFactory.decodeStream(input,null,bounds);}
        int sample=1;while(bounds.outWidth/sample>2048||bounds.outHeight/sample>2048)sample*=2;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap decoded;try(InputStream input=context.getContentResolver().openInputStream(uri)){if(input==null)return null;decoded=BitmapFactory.decodeStream(input,null,options);}
        if(decoded==null)return null;int orientation=ExifInterface.ORIENTATION_NORMAL;
        try(InputStream input=context.getContentResolver().openInputStream(uri)){if(input!=null)orientation=new ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(Exception ignored){ }
        Matrix matrix=new Matrix();if(orientation==ExifInterface.ORIENTATION_ROTATE_90)matrix.postRotate(90);else if(orientation==ExifInterface.ORIENTATION_ROTATE_180)matrix.postRotate(180);else if(orientation==ExifInterface.ORIENTATION_ROTATE_270)matrix.postRotate(270);else if(orientation==ExifInterface.ORIENTATION_FLIP_HORIZONTAL)matrix.postScale(-1,1);else if(orientation==ExifInterface.ORIENTATION_FLIP_VERTICAL)matrix.postScale(1,-1);
        if(matrix.isIdentity())return decoded;Bitmap oriented=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),matrix,true);if(oriented!=decoded)decoded.recycle();return oriented;
    }

    private static List<Rect> crops(int width,int height){int side=Math.min(width,height),maxX=width-side,maxY=height-side;return Arrays.asList(
            new Rect(0,0,width,height),new Rect(maxX/2,maxY/2,maxX/2+side,maxY/2+side),
            new Rect(0,0,side,side),new Rect(maxX,maxY,maxX+side,maxY+side),new Rect(maxX,0,maxX+side,side));}
    private static final class TensorInput{final ByteBuffer buffer;final float min,max;TensorInput(ByteBuffer b,float min,float max){buffer=b;this.min=min;this.max=max;}}
    private static TensorInput prepare(Bitmap source,Rect crop){Bitmap scaled=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(scaled);canvas.drawColor(android.graphics.Color.WHITE);canvas.drawBitmap(source,crop,new Rect(0,0,SIZE,SIZE),new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG|android.graphics.Paint.FILTER_BITMAP_FLAG));int[] pixels=new int[SIZE*SIZE];scaled.getPixels(pixels,0,SIZE,0,0,SIZE,SIZE);scaled.recycle();ByteBuffer input=ByteBuffer.allocateDirect(SIZE*SIZE*3*4).order(ByteOrder.nativeOrder());float min=1,max=0;for(int pixel:pixels){float r=((pixel>>16)&255)/255f,g=((pixel>>8)&255)/255f,b=(pixel&255)/255f;input.putFloat(r);input.putFloat(g);input.putFloat(b);min=Math.min(min,Math.min(r,Math.min(g,b)));max=Math.max(max,Math.max(r,Math.max(g,b)));}input.rewind();return new TensorInput(input,min,max);}
}
