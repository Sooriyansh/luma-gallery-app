package com.example.g.analysis;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import androidx.exifinterface.media.ExifInterface;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import com.example.g.core.compatibility.AndroidCompatibility;

/** Local, explainable evidence detector. It does not pretend to visually classify AI content. */
public final class LocalMetadataDetector implements AiMediaDetector {
    private static final List<String> AI_TERMS = Arrays.asList(
            "stable diffusion", "midjourney", "dall-e", "dall e", "openai", "chatgpt", "firefly",
            "generative fill", "generative expand", "comfyui", "automatic1111", "invokeai", "leonardo.ai",
            "google ai", "gemini", "imagen", "magic editor", "magic eraser", "flux", "ideogram", "fooocus",
            "remini", "pixelcut ai", "photoleap ai", "picsart ai", "fotor ai", "canva ai", "dreamina",
            "photoroom ai", "clipdrop", "faceapp ai", "meitu ai", "galaxy ai", "copilot designer",
            "grok imagine", "remove.bg", "ai enhancer", "ai retouch", "ai background", "ai replace");
    private static final List<String> EDIT_TERMS = Arrays.asList(
            "photoshop", "lightroom", "snapseed", "canva", "capcut", "premiere", "after effects", "gimp",
            "picsart", "photoleap", "pixelcut", "photoroom", "fotor", "faceapp", "meitu", "b612", "prisma");
    private final ContentResolver resolver;
    public LocalMetadataDetector(Context context) { resolver = context.getApplicationContext().getContentResolver(); }
    @Override public DetectionResult analyzeImage(Uri uri) throws Exception {
        ArrayList<String> creatorMetadata = new ArrayList<>();
        ArrayList<String> originMetadata = new ArrayList<>();
        addMediaStoreValues(originMetadata, uri);
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream != null) {
                ExifInterface exif = new ExifInterface(stream);
                add(creatorMetadata, exif.getAttribute(ExifInterface.TAG_SOFTWARE));
                add(creatorMetadata, exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION));
                add(creatorMetadata, exif.getAttribute(ExifInterface.TAG_USER_COMMENT));
                add(creatorMetadata, exif.getAttribute(ExifInterface.TAG_MAKE));
            }
        } catch (Exception ignored) { /* EXIF is optional; byte signatures are still checked. */ }
        return classify(creatorMetadata, originMetadata, readSignatures(uri), "Local metadata and embedded-signature analysis");
    }
    @Override public DetectionResult analyzeVideo(Uri uri) throws Exception {
        ArrayList<String> creatorMetadata = new ArrayList<>();
        ArrayList<String> originMetadata = new ArrayList<>();
        addMediaStoreValues(originMetadata, uri);
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(resolver.openFileDescriptor(uri, "r").getFileDescriptor());
            add(creatorMetadata, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR));
        } catch (Exception ignored) { } finally { try { retriever.release(); } catch (Exception ignored) { } }
        return classify(creatorMetadata, originMetadata, readSignatures(uri), "Local video metadata and embedded-signature analysis");
    }
    private DetectionResult classify(List<String> creatorMetadata, List<String> originMetadata, String signature, String source) {
        String creatorText = android.text.TextUtils.join(" ", creatorMetadata).toLowerCase(Locale.US);
        String originText = android.text.TextUtils.join(" ", originMetadata).toLowerCase(Locale.US);
        String provenanceText = (creatorText + " " + signature).toLowerCase(Locale.US);
        ArrayList<String> signals = new ArrayList<>();
        // File names, folder names and arbitrary compressed bytes are not creator evidence. A
        // screenshot named after an AI app, or a coincidental byte sequence, must stay Unknown.
        String generator = ""; for (String term : AI_TERMS) if (creatorText.contains(term)) { signals.add("AI-related creator metadata: " + term); if (generator.isEmpty()) generator = term; }
        boolean credentials = provenanceText.contains("c2pa") || provenanceText.contains("jumbf") || provenanceText.contains("content credentials");
        if (credentials) signals.add("Content Credentials container found (signature validation is unavailable locally)");
        boolean edited = false;
        for (String term : EDIT_TERMS) if (creatorText.contains(term)) { signals.add("Editing software metadata: " + term); edited = true; }
        String incomingSource = incomingSource(originText);
        if (!incomingSource.isEmpty()) signals.add("Incoming source: " + incomingSource);
        int metadataScore = generator.isEmpty() ? 0 : 90;
        // Presence of a container is useful, but without cryptographic validation it is not proof.
        int provenanceScore = credentials ? 45 : 0;
        if (!generator.isEmpty() || credentials) return new DetectionResult(AnalysisStatus.UNKNOWN, Math.max(metadataScore, provenanceScore), source, signals, 0, metadataScore, 0, provenanceScore, generator);
        if (edited) return new DetectionResult(AnalysisStatus.POSSIBLY_EDITED, 55, source, signals, 0, 35, 0, 0, "");
        signals.add("No strong local AI-generation metadata or embedded provenance indicator was found");
        signals.add("Absence of metadata is not proof that media is original");
        return new DetectionResult(AnalysisStatus.UNKNOWN, 0, source, signals, 0, 0, 0, 0, "");
    }
    private static String incomingSource(String value) {
        if (value.contains("whatsapp")) return "WhatsApp";
        if (value.contains("telegram")) return "Telegram";
        if (value.contains("screenshot")) return "Screenshots";
        if (value.contains("download") || value.contains("chrome") || value.contains("firefox") || value.contains("brave") || value.contains("opera")) return "Browser/Downloads";
        if (value.contains("bluetooth") || value.contains("nearby") || value.contains("shareit")) return "Bluetooth/Shared";
        return "";
    }
    private String readSignatures(Uri uri) {
        byte[] bytes = new byte[256 * 1024]; int total = 0;
        try (InputStream input = resolver.openInputStream(uri)) { if (input != null) while (total < bytes.length) { int read = input.read(bytes, total, bytes.length-total); if (read < 0) break; total += read; } } catch (Exception ignored) { }
        return new String(bytes, 0, total, StandardCharsets.ISO_8859_1);
    }
    private void addMediaStoreValues(List<String> out, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) add(out, cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)));
        } catch (Exception ignored) { }
        try (Cursor cursor = resolver.query(uri, new String[]{MediaStore.MediaColumns.RELATIVE_PATH}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) add(out, cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)));
        } catch (Exception ignored) { }
        if (AndroidCompatibility.supportsMediaOwnerMetadata()) {
            try (Cursor cursor = resolver.query(uri, new String[]{MediaStore.MediaColumns.OWNER_PACKAGE_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) add(out, cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)));
            } catch (Exception ignored) { }
        }
    }
    private static void add(List<String> out, String value) { if (value != null && !value.trim().isEmpty()) out.add(value); }
}
