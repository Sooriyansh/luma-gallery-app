package com.example.g.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.g.media.MediaEditor;
import com.example.g.ui.SystemBarInsets;
import com.google.android.material.button.MaterialButton;
import java.io.InputStream;

/** Touch crop editor with controls kept above cutouts, gesture navigation and the IME. */
public class ManualCropActivity extends AppCompatActivity {
    private CropView crop;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        crop = new CropView();
        root.addView(crop, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xE6000000);

        MaterialButton cancel = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        cancel.setText("Cancel");
        cancel.setContentDescription("Cancel crop");
        cancel.setMinHeight(dp(48));
        cancel.setOnClickListener(view -> finish());

        MaterialButton save = new MaterialButton(this);
        save.setText("Save crop");
        save.setContentDescription("Save cropped image");
        save.setMinHeight(dp(48));
        save.setOnClickListener(view -> {view.setEnabled(false);save.setText("Saving…");saveCrop(save);});

        bar.addView(cancel, new LinearLayout.LayoutParams(0, dp(64), 1));
        bar.addView(save, new LinearLayout.LayoutParams(0, dp(64), 1));
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        SystemBarInsets.cropEditor(getWindow(), root, bar);

        try (InputStream input = getContentResolver().openInputStream(getIntent().getData())) {
            crop.setBitmap(BitmapFactory.decodeStream(input));
        } catch (Exception error) {
            Toast.makeText(this, "Cannot open image", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void saveCrop(MaterialButton save) {
        float[] rect = crop.normalized();
        MediaEditor.createManualCrop(this, getIntent().getData(), getIntent().getStringExtra("name"),
                rect[0], rect[1], rect[2], rect[3], (uri, error) -> {
                    Toast.makeText(this, error == null ? "Manual crop saved" : error.getMessage(),
                            Toast.LENGTH_LONG).show();
                    if (error == null) finish(); else {save.setEnabled(true);save.setText("Save crop");}
                });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class CropView extends View {
        private final Paint imagePaint = new Paint(3);
        private final Paint shade = new Paint();
        private final Paint line = new Paint(3);
        private final RectF imageRect = new RectF();
        private final RectF box = new RectF();
        private Bitmap bitmap;
        private float lastX;
        private float lastY;
        private int mode;

        CropView() {
            super(ManualCropActivity.this);
            setContentDescription("Crop area. Drag inside to move; drag an edge to resize.");
            shade.setColor(0x99000000);
            line.setColor(Color.WHITE);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(dp(3));
        }

        void setBitmap(Bitmap value) {
            bitmap = value;
            invalidate();
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null) return;
            float scale = Math.min(getWidth() / (float) bitmap.getWidth(),
                    getHeight() / (float) bitmap.getHeight());
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            imageRect.set((getWidth() - width) / 2, (getHeight() - height) / 2,
                    (getWidth() + width) / 2, (getHeight() + height) / 2);
            if (box.isEmpty()) {
                box.set(imageRect.left + width * .12f, imageRect.top + height * .12f,
                        imageRect.right - width * .12f, imageRect.bottom - height * .12f);
            }
            canvas.drawBitmap(bitmap, null, imageRect, imagePaint);
            canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, box.top, shade);
            canvas.drawRect(imageRect.left, box.bottom, imageRect.right, imageRect.bottom, shade);
            canvas.drawRect(imageRect.left, box.top, box.left, box.bottom, shade);
            canvas.drawRect(box.right, box.top, imageRect.right, box.bottom, shade);
            canvas.drawRect(box, line);
            for (int index = 1; index < 3; index++) {
                canvas.drawLine(box.left + box.width() * index / 3, box.top,
                        box.left + box.width() * index / 3, box.bottom, line);
                canvas.drawLine(box.left, box.top + box.height() * index / 3,
                        box.right, box.top + box.height() * index / 3, line);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            float y = event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
                lastX = x;
                lastY = y;
                float target = dp(48);
                mode = Math.abs(x - box.left) < target ? 1
                        : Math.abs(x - box.right) < target ? 2
                        : Math.abs(y - box.top) < target ? 3
                        : Math.abs(y - box.bottom) < target ? 4 : 5;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float deltaX = x - lastX;
                float deltaY = y - lastY;
                float minimum = dp(80);
                if (mode == 1) box.left = Math.min(box.right - minimum,
                        Math.max(imageRect.left, box.left + deltaX));
                else if (mode == 2) box.right = Math.max(box.left + minimum,
                        Math.min(imageRect.right, box.right + deltaX));
                else if (mode == 3) box.top = Math.min(box.bottom - minimum,
                        Math.max(imageRect.top, box.top + deltaY));
                else if (mode == 4) box.bottom = Math.max(box.top + minimum,
                        Math.min(imageRect.bottom, box.bottom + deltaY));
                else {
                    deltaX = Math.max(imageRect.left - box.left,
                            Math.min(imageRect.right - box.right, deltaX));
                    deltaY = Math.max(imageRect.top - box.top,
                            Math.min(imageRect.bottom - box.bottom, deltaY));
                    box.offset(deltaX, deltaY);
                }
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }

        float[] normalized() {
            return new float[]{(box.left - imageRect.left) / imageRect.width(),
                    (box.top - imageRect.top) / imageRect.height(),
                    (box.right - imageRect.left) / imageRect.width(),
                    (box.bottom - imageRect.top) / imageRect.height()};
        }
    }
}
