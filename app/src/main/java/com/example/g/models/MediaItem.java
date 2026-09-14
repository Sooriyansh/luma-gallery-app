package com.example.g.models;
import android.net.Uri;
import java.util.Objects;
public final class MediaItem {
 public final long id,size,dateAdded,dateModified,dateTaken,duration; public final int width,height,orientation; public final Uri uri; public final String name,mime,relativePath,bucketId,bucketName;
 public MediaItem(long id,Uri uri,String name,String mime,long size,int width,int height,long duration,long dateTaken,long dateAdded,long dateModified,String path,String bucketId,String bucketName,int orientation){this.id=id;this.uri=uri;this.name=name;this.mime=mime;this.size=size;this.width=width;this.height=height;this.duration=duration;this.dateTaken=dateTaken;this.dateAdded=dateAdded;this.dateModified=dateModified;this.relativePath=path;this.bucketId=bucketId;this.bucketName=bucketName;this.orientation=orientation;}
 public boolean isVideo(){return mime!=null&&mime.startsWith("video/");} public boolean isGif(){return "image/gif".equals(mime);} @Override public boolean equals(Object o){return o instanceof MediaItem&&id==((MediaItem)o).id&&Objects.equals(uri,((MediaItem)o).uri)&&dateModified==((MediaItem)o).dateModified;} @Override public int hashCode(){return Objects.hash(id,uri,dateModified);}
}
