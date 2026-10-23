package com.example.g.activities;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.g.R;
import com.example.g.ui.SystemBarInsets;

public class PrivacyPolicyActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        TextView policy = new TextView(this);
        policy.setTextSize(16);
        // Resolve text colour from the current theme so dark mode works correctly.
        android.content.res.TypedArray typedArray = obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        policy.setTextColor(typedArray.getColor(0, 0xff202124));
        typedArray.recycle();
        policy.setPadding(dp(24), dp(20), dp(24), dp(32));
        policy.setText(content());
        scroll.addView(policy, new ViewGroup.LayoutParams(-1, -2));
        setContentView(scroll);
        setTitle("Privacy Policy");
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        SystemBarInsets.screen(getWindow(), scroll);
    }

    private SpannableStringBuilder content() {
        SpannableStringBuilder text = new SpannableStringBuilder();
        heading(text, "Luma Gallery Privacy Policy");
        text.append("Last updated: September 14, 2026\n\n");
        paragraph(text, "Luma Gallery is designed to keep your gallery data on your device. This policy explains what the app accesses, how it is used, and the limits of app-level privacy.");
        heading(text, "Media access");
        paragraph(text, "The app requests photo and video access only to display, organize, edit, analyze, share, and manage media that you choose. Android permissions and the system photo picker control which items are available. Media is not uploaded by Luma Gallery.");
        heading(text, "Private Vault");
        paragraph(text, "Private Vault media is copied into app-private storage and encrypted before the public copy is removed or moved to Android recycle bin. Secret codes are not stored as plain text; the app stores a salted verifier. Vault previews are temporary and are cleared when the vault closes.");
        heading(text, "AI and security analysis");
        paragraph(text, "AI image analysis and supported security checks run on the device using local app components. Results are stored as local metadata for your gallery. Private media and analysis results are not sent to a remote service by this app.");
        heading(text, "Sharing and editing");
        paragraph(text, "Media is shared only when you explicitly use Android sharing or an editing action. The selected Android app may receive the media according to its own privacy policy.");
        heading(text, "Data storage and deletion");
        paragraph(text, "Gallery preferences, favorites, analysis metadata, and vault metadata are stored locally. Uninstalling the app, clearing app data, or losing a device may permanently remove app-private vault media. Luma Gallery does not promise recovery without a separately implemented backup.");
        heading(text, "Notifications and logs");
        paragraph(text, "The app avoids putting private filenames or thumbnails in notifications. Secret codes are not intentionally written to search history, suggestions, analytics, or debug logs.");
        heading(text, "Security limitations");
        paragraph(text, "App-private storage and encryption improve privacy but cannot guarantee protection against rooted or compromised devices, malware, screenshots, backups, an unlocked device, or access to the device account. No app can promise 100% security.");
        heading(text, "Changes and contact");
        paragraph(text, "This policy may be updated when the app's data practices change. For support, use the app distribution channel or the developer contact supplied with your installation.");
        return text;
    }

    private void heading(SpannableStringBuilder text, String value) {
        int start = text.length();
        text.append(value).append('\n');
        text.setSpan(new StyleSpan(Typeface.BOLD), start, text.length() - 1, 0);
    }

    private void paragraph(SpannableStringBuilder text, String value) {
        text.append(value).append("\n\n");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
