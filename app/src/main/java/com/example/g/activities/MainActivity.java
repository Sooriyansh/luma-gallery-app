package com.example.g.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.example.g.R;
import com.example.g.fragments.AlbumsFragment;
import com.example.g.fragments.PhotosFragment;
import com.example.g.fragments.ToolsFragment;
import com.example.g.fragments.VaultFragment;
import com.example.g.security.EyeSession;
import com.example.g.security.LockSession;
import com.example.g.security.SecurePinStore;
import com.example.g.security.VaultSettings;
import com.example.g.ui.SystemBarInsets;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    private boolean lockOpen;
    private BottomNavigationView nav;
    private MaterialToolbar toolbar;
    private final ActivityResultLauncher<Intent> lockLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> lockOpen = false);
    private final ActivityResultLauncher<Intent> eyeLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    show(new VaultFragment());
                    nav.getMenu().findItem(R.id.nav_vault).setChecked(true);
                }
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        SystemBarInsets.gallery(getWindow(), findViewById(R.id.main_root),
                findViewById(R.id.app_bar), findViewById(R.id.container),
                findViewById(R.id.bottom_nav), findViewById(R.id.fab_stack));

        findViewById(R.id.fab).setOnClickListener(view ->
                startActivity(new Intent(this, FolderAccessActivity.class)));
        findViewById(R.id.ai_scan_fab).setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            com.example.g.analysis.AnalysisScheduler.requestRescan(this);
            Toast.makeText(this, "AI gallery scan started", Toast.LENGTH_SHORT).show();
        });
        toolbar = findViewById(R.id.toolbar);
        refreshLogo();
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_search) {
                startActivity(new Intent(this, SearchActivity.class));
            } else if (item.getItemId() == R.id.action_add_folder) {
                startActivity(new Intent(this, FolderAccessActivity.class));
            } else if (item.getItemId() == R.id.action_create_folder) {
                startActivity(new Intent(this, FolderAccessActivity.class).putExtra("create", true));
            } else if (item.getItemId() == R.id.action_refresh) {
                com.example.g.analysis.AnalysisScheduler.requestRescan(this);
                Toast.makeText(this, "AI gallery scan started", Toast.LENGTH_SHORT).show();
            } else if (item.getItemId() == R.id.action_screen_lock) {
                startActivity(new Intent(this, LockActivity.class).putExtra("manage_lock", true));
            }
            return true;
        });

        nav = findViewById(R.id.bottom_nav);
        // The vault is deliberately hidden by default; secret-code search remains available after setup.
        nav.getMenu().findItem(R.id.nav_vault).setVisible(VaultSettings.showShortcut(this));
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_photos) show(new PhotosFragment());
            else if (item.getItemId() == R.id.nav_albums) show(new AlbumsFragment());
            else if (item.getItemId() == R.id.nav_vault) {
                if (!EyeSession.isUnlocked()) {
                    eyeLauncher.launch(new Intent(this, EyeLockActivity.class));
                    return false;
                }
                show(new VaultFragment());
            } else if (item.getItemId() == R.id.nav_explore) show(ToolsFragment.make("explore"));
            else show(ToolsFragment.make("settings"));
            return true;
        });
        nav.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.nav_vault) {
                if (EyeSession.isUnlocked()) show(new VaultFragment());
                else eyeLauncher.launch(new Intent(this, EyeLockActivity.class));
            }
        });
        if (state == null) {
            if (getIntent().getBooleanExtra("open_private_vault", false) && EyeSession.isUnlocked()) show(new VaultFragment());
            else show(new PhotosFragment());
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshLogo();
        if (SecurePinStore.hasPin(this) && !LockSession.isUnlocked() && !lockOpen) {
            lockOpen = true;
            lockLauncher.launch(new Intent(this, LockActivity.class));
        }
    }

    @Override protected void onStop() {
        // "Immediately" means when private content leaves the foreground, not while its unlock UI is opening.
        if (VaultSettings.autoLockMs(this) == 0L) EyeSession.lock();
        super.onStop();
    }

    public void refreshVaultShortcut() {
        if (nav != null) {
            nav.getMenu().findItem(R.id.nav_vault).setVisible(VaultSettings.showShortcut(this));
        }
    }

    private void show(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.container, fragment).commit();
    }

    public void refreshLogo() {
        if (toolbar == null) return;
        toolbar.setLogo(com.example.g.ui.AppLogoManager.drawable(this));
        toolbar.setLogoDescription("App logo");
    }
}
