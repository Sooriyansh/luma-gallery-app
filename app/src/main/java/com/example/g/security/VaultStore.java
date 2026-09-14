package com.example.g.security;

import android.content.Context;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import com.example.g.core.compatibility.AndroidCompatibility;

public final class VaultStore {
    private VaultStore() { }
    private static File dir(Context c) { File d=new File(c.getFilesDir(),"vault"); if(!d.exists()) d.mkdirs(); return d; }
    public static File importMedia(Context c, Uri uri, String name) throws Exception {
        String safe=(name==null?"hidden":name).replaceAll("[^a-zA-Z0-9._-]","_");
        String id=java.util.UUID.randomUUID().toString(); File target=new File(dir(c),id+"__"+safe+".luma"); File partial=new File(dir(c),id+".part");
        try(InputStream in=c.getContentResolver().openInputStream(uri); OutputStream out=new java.io.FileOutputStream(partial)) {
            if(in==null) throw new java.io.IOException("Cannot read media"); VaultCipher.encrypt(in,out);
        } catch(Exception e){partial.delete();throw e;} if(!partial.renameTo(target)){partial.delete();throw new java.io.IOException("Could not finish encrypted Eye copy");}
        return target;
    }
    public static List<File> files(Context c) { File[] all=dir(c).listFiles((d,n)->n.endsWith(".luma")); List<File> r=new ArrayList<>(); if(all!=null) java.util.Collections.addAll(r,all); return r; }
    public static String displayName(File f) { String n=f.getName(); int p=n.indexOf("__"); return (p>=0?n.substring(p+2):n).replaceFirst("\\.luma$",""); }
    public static List<File> decryptPreviews(Context c) { clearPreviews(c);File d=new File(c.getCacheDir(),"eye_previews");d.mkdirs();List<File> out=new ArrayList<>();for(File encrypted:files(c)){String id=encrypted.getName().split("__",2)[0];File preview=new File(d,id+"__"+displayName(encrypted));try(InputStream in=new java.io.FileInputStream(encrypted);OutputStream stream=new java.io.FileOutputStream(preview)){VaultCipher.decrypt(in,stream);out.add(preview);}catch(Exception damaged){preview.delete();}}return out; }
    public static File encryptedForPreview(Context c, File preview) {
        String name=preview.getName(); int split=name.indexOf("__");
        if(split<=0)return null; String id=name.substring(0,split);
        for(File encrypted:files(c))if(encrypted.getName().startsWith(id+"__"))return encrypted;
        return null;
    }
    public static Uri restoreToGallery(Context c, File encrypted) throws Exception {
        String name=displayName(encrypted);
        String extension=MimeTypeMap.getFileExtensionFromUrl(name);
        String mime=extension==null?null:MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(java.util.Locale.ROOT));
        if(mime==null)mime="application/octet-stream";
        boolean video=mime.startsWith("video/");
        Uri collection=video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,mime);
        if(AndroidCompatibility.usesScopedMediaCollection()){values.put(MediaStore.MediaColumns.RELATIVE_PATH,(video?"Movies":"Pictures")+"/Luma Restored");values.put(MediaStore.MediaColumns.IS_PENDING,1);}
        Uri output=c.getContentResolver().insert(collection,values);
        if(output==null)throw new java.io.IOException("Could not create restored gallery item");
        try(InputStream in=new java.io.FileInputStream(encrypted);OutputStream out=c.getContentResolver().openOutputStream(output)){
            if(out==null)throw new java.io.IOException("Could not write restored gallery item");
            VaultCipher.decrypt(in,out);
        }catch(Exception error){c.getContentResolver().delete(output,null,null);throw error;}
        if(AndroidCompatibility.usesScopedMediaCollection()){ContentValues ready=new ContentValues();ready.put(MediaStore.MediaColumns.IS_PENDING,0);c.getContentResolver().update(output,ready,null,null);}
        return output;
    }
    public static void clearPreviews(Context c){File d=new File(c.getCacheDir(),"eye_previews");File[] all=d.listFiles();if(all!=null)for(File f:all)f.delete();}
}
