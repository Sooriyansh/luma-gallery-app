package com.example.g.security;

import android.content.Context;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.example.g.core.compatibility.AndroidCompatibility;

public final class VaultStore {
    private VaultStore() { }
    private static File dir(Context c) { File d=new File(c.getFilesDir(),"vault"); if(!d.exists()) d.mkdirs(); return d; }
    private static File bin(Context c) { File d=new File(c.getFilesDir(),"vault_bin"); if(!d.exists()) d.mkdirs(); return d; }
    public static File importMedia(Context c, Uri uri, String name) throws Exception {
        String display=(name==null||name.trim().isEmpty()?"Private item":name);
        String id=java.util.UUID.randomUUID().toString(); File target=new File(dir(c),id+".luma"); File partial=new File(dir(c),id+".part");
        try(InputStream in=c.getContentResolver().openInputStream(uri); OutputStream out=new java.io.FileOutputStream(partial)) {
            if(in==null) throw new java.io.IOException("Cannot read media"); VaultCipher.encrypt(in,out);
        } catch(Exception e){partial.delete();throw e;} if(!partial.renameTo(target)){partial.delete();throw new java.io.IOException("Could not finish encrypted vault copy");}
        try { writeMeta(c, target, display, "", 0L); } catch (Exception error) { target.delete(); throw error; }
        return target;
    }
    public static List<File> files(Context c) { File[] all=dir(c).listFiles((d,n)->n.endsWith(".luma")); List<File> r=new ArrayList<>(); if(all!=null) java.util.Collections.addAll(r,all); return r; }
    public static List<File> recycleBin(Context c) { File[] all=bin(c).listFiles((d,n)->n.endsWith(".luma")); List<File> r=new ArrayList<>(); if(all!=null) java.util.Collections.addAll(r,all); return r; }
    /** New vault files use opaque names. Old Eye files still work for existing users. */
    public static String displayName(Context c, File file) { try { String value=readMeta(c,file)[0]; if(value!=null&&!value.isEmpty()) return value; } catch(Exception ignored) { } return displayName(file); }
    public static String displayName(File f) { String n=f.getName(); int p=n.indexOf("__"); return (p>=0?n.substring(p+2):n).replaceFirst("\\.luma$",""); }
    public static List<File> decryptPreviews(Context c) { clearPreviews(c);File d=new File(c.getCacheDir(),"eye_previews");d.mkdirs();List<File> out=new ArrayList<>();for(File encrypted:files(c)){String id=encrypted.getName().replaceFirst("\\.luma$","").split("__",2)[0];File preview=new File(d,id+"__"+displayName(c,encrypted));try(InputStream in=new java.io.FileInputStream(encrypted);OutputStream stream=new java.io.FileOutputStream(preview)){VaultCipher.decrypt(in,stream);out.add(preview);}catch(Exception damaged){preview.delete();}}return out; }
    public static File encryptedForPreview(Context c, File preview) {
        return encryptedForPreview(dir(c), files(c), preview);
    }

    /** Visible for unit tests: resolves both current opaque and legacy vault item names. */
    static File encryptedForPreview(File vaultDirectory, List<File> encryptedFiles, File preview) {
        String name = preview.getName();
        int split = name.indexOf("__");
        if (split <= 0) return null;
        String id = name.substring(0, split);

        // Current vault entries have opaque UUID filenames.  Check that exact name first;
        // the loop retains compatibility with the pre-migration "id__display-name.luma"
        // format without ever deriving a path from the user-controlled display name.
        File currentFormat = new File(vaultDirectory, id + ".luma");
        if (currentFormat.isFile()) return currentFormat;
        for (File encrypted : encryptedFiles) {
            if (encrypted.getName().startsWith(id + "__")) return encrypted;
        }
        return null;
    }
    public static Uri restoreToGallery(Context c, File encrypted) throws Exception {
        String name=displayName(c, encrypted);
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
    public static void moveToRecycleBin(Context c, File encrypted) throws Exception { move(c, encrypted, bin(c), System.currentTimeMillis()); }
    public static void restoreFromRecycleBin(Context c, File encrypted) throws Exception { move(c, encrypted, dir(c), 0L); }
    public static void permanentlyDelete(Context c, File encrypted) { metaFile(encrypted).delete(); encrypted.delete(); }
    public static String folder(Context c, File encrypted) { try { return readMeta(c, encrypted)[1]; } catch (Exception ignored) { return ""; } }
    public static void setFolder(Context c, File encrypted, String folder) throws Exception { String[] meta=readMeta(c,encrypted); writeMeta(c,encrypted,meta[0],folder,Long.parseLong(meta[2])); }
    public static long deletedAt(Context c, File encrypted) { try { return Long.parseLong(readMeta(c, encrypted)[2]); } catch (Exception ignored) { return 0L; } }

    private static void move(Context c, File source, File destination, long deletedAt) throws Exception {
        String[] meta=readMeta(c, source); File target=new File(destination, source.getName());
        if (!source.renameTo(target)) throw new java.io.IOException("Could not move private item");
        File oldMeta=metaFile(source), newMeta=metaFile(target); if(oldMeta.exists()&&!oldMeta.renameTo(newMeta)){target.renameTo(source);throw new java.io.IOException("Could not move vault metadata");}
        writeMeta(c,target,meta[0],meta[1],deletedAt);
    }
    private static File metaFile(File media) { return new File(media.getParentFile(), media.getName()+".meta"); }
    private static void writeMeta(Context c, File media, String display, String folder, long deletedAt) throws Exception {
        String safeDisplay=display.replace("\n"," ").replace("\r"," "); String safeFolder=folder.replace("\n"," ").replace("\r"," ");
        byte[] source=(safeDisplay+"\n"+safeFolder+"\n"+deletedAt).getBytes(StandardCharsets.UTF_8);
        File partial=new File(media.getParentFile(), media.getName()+".meta.part");
        try(InputStream in=new ByteArrayInputStream(source);OutputStream out=new java.io.FileOutputStream(partial)){VaultCipher.encrypt(in,out);}
        File finalMeta=metaFile(media); if(finalMeta.exists()&&!finalMeta.delete()){partial.delete();throw new java.io.IOException("Could not replace vault metadata");}
        if(!partial.renameTo(finalMeta)){partial.delete();throw new java.io.IOException("Could not store vault metadata");}
    }
    private static String[] readMeta(Context c, File media) throws Exception {
        File meta=metaFile(media); if(!meta.exists()) return new String[]{displayName(media),"","0"};
        ByteArrayOutputStream output=new ByteArrayOutputStream();try(InputStream in=new java.io.FileInputStream(meta)){VaultCipher.decrypt(in,output);} String[] parts=new String(output.toByteArray(),StandardCharsets.UTF_8).split("\n",-1);
        return new String[]{parts.length>0?parts[0]:"Private item",parts.length>1?parts[1]:"",parts.length>2?parts[2]:"0"};
    }
}
