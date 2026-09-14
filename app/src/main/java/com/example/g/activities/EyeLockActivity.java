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
        ((TextView) findViewById(R.id.lock_message)).setText("Eye has its own PIN. You can also unlock it with your fingerprint.");
        ((Button) findViewById(R.id.unlock_pin)).setText("Use Eye PIN");
        findViewById(R.id.unlock_pin).setOnClickListener(v -> showPin());
        findViewById(R.id.unlock_biometric).setOnClickListener(v -> showBiometric());
        if (!EyePinStore.hasPin(this)) setupPin(); else showBiometric();
    }

    private EditText pinField(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setPadding(48, 16, 48, 16);
        return input;
    }

    private void setupPin() {
        EditText input = pinField("Create separate Eye PIN");
        new MaterialAlertDialogBuilder(this).setCancelable(false).setTitle("Set Eye lock")
                .setMessage("Use a 4–8 digit PIN. This can be different from the Gallery PIN.")
                .setView(input).setPositiveButton("Next", (dialog, which) -> {
                    String pin = input.getText().toString();
                    if (!pin.matches("\\d{4,8}")) {
                        Toast.makeText(this, "Use 4 to 8 digits", Toast.LENGTH_LONG).show();
                        setupPin();
                    } else {
                        confirmPin(pin);
                    }
                }).show();
    }

    private void confirmPin(String original) {
        EditText input = pinField("Confirm Eye PIN");
        new MaterialAlertDialogBuilder(this).setCancelable(false).setTitle("Confirm Eye PIN")
                .setView(input).setNegativeButton("Back", (dialog, which) -> setupPin())
                .setPositiveButton("Save", (dialog, which) -> {
                    if (!original.equals(input.getText().toString())) {
                        Toast.makeText(this, "PINs did not match", Toast.LENGTH_LONG).show();
                        setupPin();
                    } else {
                        EyePinStore.save(this, original.toCharArray());
                        EyeSession.unlock();
                        complete();
                    }
                }).show();
    }

    private void showPin() {
        EditText input = pinField("Eye PIN");
        new MaterialAlertDialogBuilder(this).setTitle("Unlock Eye").setView(input)
                .setNegativeButton("Fingerprint", (dialog, which) -> showBiometric())
                .setPositiveButton("Unlock", (dialog, which) -> {
                    if (EyePinStore.verify(this, input.getText().toString().toCharArray())) {
                        EyeSession.unlock();
                        complete();
                    } else {
                        Toast.makeText(this, "Incorrect Eye PIN", Toast.LENGTH_SHORT).show();
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
                        EyeSession.unlock();
                        complete();
                    }

                    @Override public void onAuthenticationError(
                            int code, @NonNull CharSequence message) {
                        showing = false;
                    }
                }).authenticate(new BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock Eye").setSubtitle("Private photos")
                        .setAllowedAuthenticators(allowed)
                        .setNegativeButtonText("Use Eye PIN").build());
    }

    private void complete() {
        setResult(RESULT_OK);
        finish();
    }
}
