package com.example.g.activities;

import android.app.PendingIntent;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import com.example.g.R;
import com.example.g.adapters.MediaAdapter;
import com.example.g.models.MediaItem;
import com.example.g.ui.SystemBarInsets;
import com.example.g.core.compatibility.AndroidCompatibility;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecycleBinActivity extends AppCompatActivity {
    private MediaAdapter adapter; private View empty;
    private final ActivityResultLauncher<IntentSenderRequest> restore=registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),r->load());
    @Override protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_recycle_bin);SystemBarInsets.screen(getWindow(),findViewById(android.R.id.content));MaterialToolbar t=findViewById(R.id.toolbar);t.setNavigationIcon(R.drawable.ic_arrow_back);t.setNavigationContentDescription("Back to gallery");t.setNavigationOnClickListener(v->finish());empty=findViewById(R.id.bin_empty);androidx.recyclerview.widget.RecyclerView grid=findViewById(R.id.bin_grid);grid.setLayoutManager(new GridLayoutManager(this,3));adapter=new MediaAdapter(item->{if(AndroidCompatibility.supportsMediaTrash())restoreItem(item);});grid.setAdapter(adapter);load();}
    private void load(){if(!AndroidCompatibility.supportsMediaTrash()){empty.setVisibility(View.VISIBLE);return;}new Thread(()->{List<MediaItem> out=new ArrayList<>();query(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),out);query(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),out);runOnUiThread(()->{adapter.submitList(out);empty.setVisibility(out.isEmpty()?View.VISIBLE:View.GONE);});}).start();}
    @RequiresApi(30) private void query(Uri collection,List<MediaItem> out){String[] p={MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.MIME_TYPE,MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.WIDTH,MediaStore.MediaColumns.HEIGHT,MediaStore.MediaColumns.DATE_MODIFIED};Bundle args=new Bundle();args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED,MediaStore.MATCH_ONLY);args.putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER,MediaStore.MediaColumns.DATE_MODIFIED+" DESC");try(Cursor c=getContentResolver().query(collection,p,args,null)){if(c==null)return;while(c.moveToNext()){long id=c.getLong(0);out.add(new MediaItem(id,ContentUris.withAppendedId(collection,id),c.getString(1),c.getString(2),c.getLong(3),c.getInt(4),c.getInt(5),0,0,0,c.getLong(6),"","","Recycle Bin",0));}}catch(Exception ignored){}}
    @RequiresApi(30) private void restoreItem(MediaItem item){new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(item.name).setItems(new String[]{"Restore","Delete permanently"},(d,w)->{if(w==0)restoreNow(item);else confirmPermanent(item);}).show();}
    @RequiresApi(30) private void restoreNow(MediaItem item){try{PendingIntent p=MediaStore.createTrashRequest(getContentResolver(),Collections.singletonList(item.uri),false);restore.launch(new IntentSenderRequest.Builder(p.getIntentSender()).build());}catch(Exception e){Toast.makeText(this,"Could not restore this item",Toast.LENGTH_LONG).show();}}
    @RequiresApi(30) private void confirmPermanent(MediaItem item){new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Delete permanently?").setMessage("This cannot be undone.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{try{PendingIntent p=MediaStore.createDeleteRequest(getContentResolver(),Collections.singletonList(item.uri));restore.launch(new IntentSenderRequest.Builder(p.getIntentSender()).build());}catch(Exception e){Toast.makeText(this,"Could not permanently delete this item",Toast.LENGTH_LONG).show();}}).show();}
}
