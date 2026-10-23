package com.example.g.activities;

import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import com.example.g.R;
import com.example.g.security.EyePinStore;
import com.example.g.security.EyeSession;
import com.example.g.ui.SystemBarInsets;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class EyeLockActivity extends AppCompatActivity {
    private boolean showing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                setResult(RESULT_CANCELED);
                finish();
            }
        });
        if (EyeSession.isUnlocked()) {
            complete();
            return;
        }
        setContentView(R.layout.activity_lock);
        SystemBarInsets.screen(getWindow(), findViewById(android.R.id.content));
        ((TextView) findViewById(R.id.lock_message)).setText("Private Vault is protected by your secret code. Fingerprint unlock is available when supported.");
        ((Button) findViewById(R.id.unlock_pin)).setText("Use secret code");
        findViewById(R.id.unlock_pin).setOnClickListener(v -> showPin());
        findViewById(R.id.unlock_biometric).setOnClickListener(v -> showBiometric());
        if (!EyePinStore.hasPin(this)) setupPin(); else showBiometric();
    }

    private EditText pinField(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setPadding(48, 16, 48, 16);
        return input;
    }

    private void setupPin() {
        EditText input = pinField("Enter secret code");
        new MaterialAlertDialogBuilder(this).setCancelable(false).setTitle("Set Up Private Vault")
                .setMessage("Use a 4–8 digit PIN. This can be different from the Gallery PIN.")
                .setView(input).setPositiveButton("Next", (dialog, which) -> {
                    String pin = input.getText().toString();
                    if (pin.codePointCount(0, pin.length()) < 4) {
                        Toast.makeText(this, "Use at least 4 characters", Toast.LENGTH_LONG).show();
                        setupPin();
                    } else {
                        confirmPin(pin);
                    }
                }).show();
    }

    private void confirmPin(String original) {
        EditText input = pinField("Confirm secret code");
        new MaterialAlertDialogBuilder(this).setCancelable(false).setTitle("Confirm secret code")
                .setView(input).setNegativeButton("Back", (dialog, which) -> setupPin())
                .setPositiveButton("Save", (dialog, which) -> {
                    if (!original.equals(input.getText().toString())) {
                        Toast.makeText(this, "Codes did not match", Toast.LENGTH_LONG).show();
                        setupPin();
                    } else {
                        EyePinStore.save(this, original.toCharArray());
                        EyeSession.unlock(this);
                        complete();
                    }
                }).show();
    }

    private void showPin() {
        if (!EyePinStore.canAttempt(this)) { Toast.makeText(this, "Please wait before trying again", Toast.LENGTH_LONG).show(); return; }
        EditText input = pinField("Secret code");
        new MaterialAlertDialogBuilder(this).setTitle("Unlock Private Vault").setView(input)
                .setNegativeButton("Fingerprint", (dialog, which) -> showBiometric())
                .setPositiveButton("Unlock", (dialog, which) -> {
                    if (EyePinStore.verify(this, input.getText().toString().toCharArray())) {
                        EyePinStore.recordSuccess(this);
                        EyeSession.unlock(this);
                        complete();
                    } else {
                        EyePinStore.recordFailure(this);
                        Toast.makeText(this, "Unable to unlock Private Vault", Toast.LENGTH_SHORT).show();
                        showPin();
                    }
                }).show();
    }

    private void showBiometric() {
        if (showing || !EyePinStore.hasPin(this)) return;
        int allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.BIOMETRIC_WEAK;
        if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            showPin();
            return;
        }
        showing = true;
        new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        showing = false;
                        EyePinStore.recordSuccess(EyeLockActivity.this);
                        EyeSession.unlock(EyeLockActivity.this);
                        complete();
                    }

                    @Override public void onAuthenticationError(
                            int code, @NonNull CharSequence message) {
                        showing = false;
                    }
                }).authenticate(new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock Private Vault").setSubtitle("Private photos and videos")
                        .setAllowedAuthenticators(allowed)
                        .setNegativeButtonText("Use secret code").build());
    }

    private void complete() {
        setResult(RESULT_OK);
        finish();
    }
}
