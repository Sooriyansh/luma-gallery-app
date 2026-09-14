package com.example.g.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class FolderAccessActivity extends AppCompatActivity {
    private final ActivityResultLauncher<Intent> picker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    android.net.Uri tree = result.getData().getData();
                    if (tree != null) {
                        int flags = result.getData().getFlags()
                                & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        try { getContentResolver().takePersistableUriPermission(tree, flags); }
                        catch (Exception ignored) { }
                        getSharedPreferences("folders", 0).edit()
                                .putString(tree.toString(), tree.toString()).apply();
                        if (getIntent().getBooleanExtra("create", false)) {
                            askName(tree);
                            return;
                        }
                    }
                }
                finish();
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        picker.launch(intent);
    }

    private void askName(android.net.Uri tree) {
        EditText name = new EditText(this);
        name.setHint("New folder name");
        name.setSingleLine(true);
        name.setMinHeight(dp(48));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setCancelable(false)
                .setTitle("Create new folder")
                .setView(name)
                .setNegativeButton("Cancel", (ignored, which) -> finish())
                .setPositiveButton("Create", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String value = name.getText().toString().trim();
                if (value.isEmpty()) {
                    name.setError("Enter a folder name");
                    name.requestFocus();
                    return;
                }
                DocumentFile root = DocumentFile.fromTreeUri(this, tree);
                DocumentFile made = root == null ? null : root.createDirectory(value);
                Toast.makeText(this, made == null ? "Folder could not be created" : "Folder created",
                        Toast.LENGTH_SHORT).show();
                finish();
            });
            name.requestFocus();
            if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
