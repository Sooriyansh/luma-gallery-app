package com.example.g.analysis;

import android.content.Context;
import android.net.Uri;
import androidx.exifinterface.media.ExifInterface;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Parses locally accessible EXIF/XMP/IPTC/PNG/JPEG marker text and provenance containers. */
public final class EmbeddedAuthenticityEvidence {
    private static final List<String> GENERATORS = Arrays.asList(
            "stable diffusion", "midjourney", "dall-e", "dall e", "adobe firefly", "firefly",
            "imagen", "gemini", "flux", "comfyui", "automatic1111", "invokeai", "fooocus",
            "leonardo", "canva ai", "ideogram", "generative fill", "trainedalgorithmicmedia");
    private static final List<String> EDITORS = Arrays.asList(
            "photoshop", "lightroom", "snapseed", "gimp", "picsart", "photoleap", "pixelcut",
            "photoroom", "fotor", "faceapp", "meitu", "canva", "generative fill");

    public int metadataScore, provenanceScore, watermarkScore, editingScore;
    public String generator = "", provenanceStatus = "No Content Credentials container found";
    public String watermarkStatus = "No supported embedded watermark marker found; invisible watermark detection unavailable";
    public final List<String> signals = new ArrayList<>();

    private EmbeddedAuthenticityEvidence() { }

    public static EmbeddedAuthenticityEvidence analyze(Context context, Uri uri) {
        EmbeddedAuthenticityEvidence out = new EmbeddedAuthenticityEvidence();
        StringBuilder metadata = new StringBuilder();
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input != null) {
                ExifInterface exif = new ExifInterface(input);
                add(metadata, exif, ExifInterface.TAG_MAKE, "Camera make", out);
                add(metadata, exif, ExifInterface.TAG_MODEL, "Camera model", out);
                add(metadata, exif, ExifInterface.TAG_LENS_MODEL, "Lens", out);
                add(metadata, exif, ExifInterface.TAG_FOCAL_LENGTH, "Focal length", out);
                add(metadata, exif, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "ISO", out);
                add(metadata, exif, ExifInterface.TAG_EXPOSURE_TIME, "Exposure", out);
                add(metadata, exif, ExifInterface.TAG_F_NUMBER, "Aperture", out);
                add(metadata, exif, ExifInterface.TAG_DATETIME_ORIGINAL, "Date taken", out);
                add(metadata, exif, ExifInterface.TAG_SOFTWARE, "Software", out);
                add(metadata, exif, ExifInterface.TAG_IMAGE_DESCRIPTION, "Description", out);
                add(metadata, exif, ExifInterface.TAG_USER_COMMENT, "Comment", out);
                if (exif.getLatLong() != null) out.signals.add("Metadata: GPS coordinates are present");
            }
        } catch (Exception ignored) { out.signals.add("EXIF metadata: unavailable or absent"); }

        byte[] bytes = readPrefix(context, uri, 4 * 1024 * 1024);
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);
        String lower = (metadata + "\n" + raw).toLowerCase(Locale.US);
        boolean xmp = lower.contains("xmpmeta") || lower.contains("http://ns.adobe.com/xap/");
        boolean iptc = lower.contains("photoshop 3.0") || lower.contains("iptc");
        boolean pngText = lower.contains("text") && bytes.length > 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P';
        boolean jpegComment = containsJpegComment(bytes);
        if (xmp || iptc || pngText || jpegComment) out.signals.add("Extended metadata inspected: "
                + joinTypes(xmp, iptc, pngText, jpegComment));

        for (String term : GENERATORS) if (lower.contains(term)) {
            if (out.generator.isEmpty()) out.generator = display(term);
            out.metadataScore = Math.max(out.metadataScore, term.equals("trainedalgorithmicmedia") ? 82 : 90);
        }
        if (!out.generator.isEmpty()) out.signals.add("AI generator declaration in embedded creator metadata: " + out.generator);
        for (String term : EDITORS) if (lower.contains(term)) out.editingScore = Math.max(out.editingScore,
                term.contains("generative") ? 70 : 38);
        if (out.editingScore > 0) out.signals.add("Editing application metadata is present; editing is not proof of generation");

        boolean c2pa = lower.contains("c2pa") || lower.contains("jumbf") || lower.contains("content credentials");
        boolean aiAssertion = lower.contains("trainedalgorithmicmedia") || lower.contains("compositewithtrainedalgorithmicmedia")
                || lower.contains("digital source type") && lower.contains("algorithmic");
        if (c2pa) {
            out.provenanceScore = aiAssertion ? 72 : 38;
            out.provenanceStatus = aiAssertion
                    ? "Container declares AI-created/edited content; cryptographic signature not verified"
                    : "Container found; cryptographic signature verification unavailable";
            out.signals.add("C2PA evidence: " + out.provenanceStatus);
        } else {
            out.signals.add("C2PA evidence: no container found (absence does not imply a real photo)");
        }

        boolean synthIdMarker = lower.contains("synthid");
        boolean providerMarker = lower.contains("invisible watermark") || lower.contains("ai watermark");
        if (synthIdMarker || providerMarker) {
            out.watermarkScore = 45;
            out.watermarkStatus = (synthIdMarker ? "SynthID" : "Provider") + " marker text found; invisible watermark not cryptographically verified";
        }
        out.signals.add("Watermark evidence: " + out.watermarkStatus);
        if (out.generator.isEmpty()) out.signals.add("Metadata evidence: no explicit AI generator declaration found; missing metadata is neutral");
        return out;
    }

    private static void add(StringBuilder text, ExifInterface exif, String tag, String label,
                            EmbeddedAuthenticityEvidence out) {
        String value = exif.getAttribute(tag);
        if (value != null && !value.trim().isEmpty()) {
            text.append(' ').append(value);
            out.signals.add("Metadata " + label + ": " + safeValue(value));
        }
    }

    private static byte[] readPrefix(Context context, Uri uri, int limit) {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) return new byte[0];
            byte[] buffer = new byte[32768]; int total = 0, read;
            while (total < limit && (read = input.read(buffer, 0, Math.min(buffer.length, limit - total))) > 0) {
                output.write(buffer, 0, read); total += read;
            }
            return output.toByteArray();
        } catch (Exception ignored) { return new byte[0]; }
    }

    private static boolean containsJpegComment(byte[] bytes) {
        for (int i = 0; i + 1 < bytes.length; i++) if ((bytes[i] & 255) == 0xff && (bytes[i + 1] & 255) == 0xfe) return true;
        return false;
    }

    private static String joinTypes(boolean xmp, boolean iptc, boolean png, boolean jpeg) {
        List<String> values = new ArrayList<>();
        if (xmp) values.add("XMP"); if (iptc) values.add("IPTC"); if (png) values.add("PNG text chunks"); if (jpeg) values.add("JPEG comments");
        return android.text.TextUtils.join(", ", values);
    }

    private static String display(String value) {
        if ("trainedalgorithmicmedia".equals(value)) return "C2PA trained-algorithmic-media assertion";
        return value;
    }

    private static String safeValue(String value) {
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 80 ? clean.substring(0, 80) + "…" : clean;
    }
}
