package com.example.g.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import com.example.g.R;
import com.example.g.activities.SplashActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Stores a user-selected app logo privately without modifying the original gallery image. */
public final class AppLogoManager {
    private static final String FILE_NAME = "custom_app_logo.png";
    private static final int LOGO_SIZE = 512;

    private AppLogoManager() { }

    public static boolean hasCustomLogo(Context context) { return logoFile(context).isFile(); }

    public static void save(Context context, Uri source) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            if (input == null) throw new IOException("The selected image could not be opened");
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Please choose a valid image");
        int sample = 1;
        while (bounds.outWidth / sample > 1536 || bounds.outHeight / sample > 1536) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample; options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded;
        try (InputStream input = context.getContentResolver().openInputStream(source)) {
            if (input == null) throw new IOException("The selected image could not be opened");
            decoded = BitmapFactory.decodeStream(input, null, options);
        }
        if (decoded == null) throw new IOException("Please choose a supported image");
        Bitmap output = Bitmap.createBitmap(LOGO_SIZE, LOGO_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        float scale = Math.max((float) LOGO_SIZE / decoded.getWidth(), (float) LOGO_SIZE / decoded.getHeight());
        float left = (LOGO_SIZE - decoded.getWidth() * scale) / 2f;
        float top = (LOGO_SIZE - decoded.getHeight() * scale) / 2f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(decoded, null, new android.graphics.RectF(left, top,
                left + decoded.getWidth() * scale, top + decoded.getHeight() * scale), paint);
        if (decoded != output) decoded.recycle();
        try (FileOutputStream stream = new FileOutputStream(logoFile(context))) {
            if (!output.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IOException("Could not save the logo");
        } finally { output.recycle(); }
    }

    public static void reset(Context context) {
        File file = logoFile(context);
        if (file.isFile()) file.delete();
    }

    @Nullable public static Bitmap customBitmap(Context context) {
        return hasCustomLogo(context) ? BitmapFactory.decodeFile(logoFile(context).getAbsolutePath()) : null;
    }

    public static Drawable drawable(Context context) {
        Bitmap bitmap = customBitmap(context);
        if (bitmap != null) return new BitmapDrawable(context.getResources(), bitmap);
        Drawable fallback = ContextCompat.getDrawable(context, R.mipmap.ic_launcher);
        if (fallback == null) throw new IllegalStateException("Default app icon is unavailable");
        return fallback;
    }

    public static boolean requestHomeShortcut(Context context) {
        Bitmap bitmap = customBitmap(context);
        if (bitmap == null || !ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false;
        Intent launch = new Intent(context, SplashActivity.class)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(context, "custom-luma-logo")
                .setShortLabel(context.getString(R.string.app_name))
                .setLongLabel(context.getString(R.string.app_name))
                .setIcon(IconCompat.createWithBitmap(bitmap)).setIntent(launch).build();
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null);
    }

    private static File logoFile(Context context) { return new File(context.getFilesDir(), FILE_NAME); }
}
