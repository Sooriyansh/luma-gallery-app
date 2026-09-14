package com.example.g.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import com.example.g.R;
import com.example.g.security.LockSession;
import com.example.g.security.SecurePinStore;
import com.example.g.ui.SystemBarInsets;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class LockActivity extends AppCompatActivity {
    private boolean promptShowing;
    @Override protected void onCreate(Bundle state){super.onCreate(state);getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){@Override public void handleOnBackPressed(){moveTaskToBack(true);}});if(LockSession.isUnlocked()){complete();return;}setContentView(R.layout.activity_lock);SystemBarInsets.screen(getWindow(),findViewById(android.R.id.content));((android.widget.ImageView)findViewById(R.id.app_logo)).setImageDrawable(com.example.g.ui.AppLogoManager.drawable(this));findViewById(R.id.unlock_biometric).setOnClickListener(v->showBiometric());findViewById(R.id.unlock_pin).setOnClickListener(v->showPin());if(!SecurePinStore.hasPin(this)) setupPin();else showBiometric();}
    private void setupPin(){EditText first=pinField("Create a 4–8 digit PIN");new MaterialAlertDialogBuilder(this).setCancelable(false).setTitle("Create Gallery PIN").setMessage("This PIN protects Luma whenever it is opened.").setView(first).setPositiveButton("Next",(d,w)->{String pin=first.getText().toString();if(!pin.matches("\\d{4,8}")){Toast.makeText(this,"Use 4 to 8 digits",Toast.LENGTH_LONG).show();setupPin();}else confirmPin(pin);}).show();}
    private void confirmPin(String original){EditText confirm=pinField("Enter PIN again");new MaterialAlertDialogBuilder(this).setCancelable(false).setTitle("Confirm Gallery PIN").setView(confirm).setNegativeButton("Back",(d,w)->setupPin()).setPositiveButton("Save",(d,w)->{if(!original.equals(confirm.getText().toString())){Toast.makeText(this,"PINs did not match",Toast.LENGTH_LONG).show();setupPin();return;}SecurePinStore.save(this,original.toCharArray());LockSession.unlock();complete();}).show();}
    private EditText pinField(String hint){EditText input=new EditText(this);input.setHint(hint);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);input.setSingleLine(true);input.setPadding(48,16,48,16);return input;}
    private void showPin(){EditText input=pinField("Gallery PIN");new MaterialAlertDialogBuilder(this).setTitle("Enter Gallery PIN").setView(input).setNegativeButton("Fingerprint",(d,w)->showBiometric()).setPositiveButton("Unlock",(d,w)->{if(SecurePinStore.verify(this,input.getText().toString().toCharArray())){LockSession.unlock();complete();}else{Toast.makeText(this,"Incorrect PIN",Toast.LENGTH_SHORT).show();showPin();}}).show();}
    private void showBiometric(){if(promptShowing||!SecurePinStore.hasPin(this))return;int allowed=BiometricManager.Authenticators.BIOMETRIC_STRONG|BiometricManager.Authenticators.BIOMETRIC_WEAK;if(BiometricManager.from(this).canAuthenticate(allowed)!=BiometricManager.BIOMETRIC_SUCCESS){showPin();return;}promptShowing=true;BiometricPrompt prompt=new BiometricPrompt(this,ContextCompat.getMainExecutor(this),new BiometricPrompt.AuthenticationCallback(){@Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result){promptShowing=false;LockSession.unlock();complete();}@Override public void onAuthenticationError(int code,@NonNull CharSequence message){promptShowing=false;if(code!=BiometricPrompt.ERROR_NEGATIVE_BUTTON&&code!=BiometricPrompt.ERROR_USER_CANCELED)Toast.makeText(LockActivity.this,message,Toast.LENGTH_SHORT).show();}});prompt.authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Luma Gallery").setSubtitle("No lock will be requested again for 3 minutes").setAllowedAuthenticators(allowed).setNegativeButtonText("Use PIN").build());}
    private void complete(){setResult(RESULT_OK);if(getIntent().getBooleanExtra("start_main",false))startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();}
}
