package com.example.g.analysis;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Lightweight, explainable statistics which are never used as a stand-alone AI verdict. */
public final class ImageForensicEvidence {
    public int noiseScore, frequencyScore, ganScore, diffusionScore, pixelScore, compressionScore;
    public int cameraPipelineScore, suspiciousRegionScore;
    public String regionSummary = "No strongly anomalous region found";
    public final List<String> signals = new ArrayList<>();

    private ImageForensicEvidence() { }

    public static ImageForensicEvidence analyze(Context context, Uri uri) {
        ImageForensicEvidence out = new ImageForensicEvidence();
        Bitmap bitmap = decode(context, uri);
        if (bitmap == null || bitmap.getWidth() < 24 || bitmap.getHeight() < 24) {
            out.signals.add("Pixel forensics: unavailable (image could not be decoded at a useful size)");
            return out;
        }
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        bitmap.recycle();
        float[] gray = new float[pixels.length];
        int[] histogram = new int[256];
        double saturation = 0, channelDelta = 0;
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i], r = (p >>> 16) & 255, g = (p >>> 8) & 255, b = p & 255;
            gray[i] = (77 * r + 150 * g + 29 * b) / 256f;
            histogram[Math.max(0, Math.min(255, Math.round(gray[i])))]++;
            if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 220) saturation++;
            channelDelta += Math.abs(r - g) + Math.abs(g - b);
        }

        double entropy = entropy(histogram, pixels.length);
        double residualAbs = 0, residualSq = 0, adjacentResidual = 0;
        double previous = 0; int residualCount = 0;
        for (int y = 1; y < height - 1; y++) for (int x = 1; x < width - 1; x++) {
            int i = y * width + x;
            double local = (gray[i - 1] + gray[i + 1] + gray[i - width] + gray[i + width]) * .25;
            double residual = gray[i] - local;
            residualAbs += Math.abs(residual); residualSq += residual * residual;
            if (residualCount > 0) adjacentResidual += Math.abs(residual - previous);
            previous = residual; residualCount++;
        }
        double meanResidual = residualCount == 0 ? 0 : residualAbs / residualCount;
        double residualStd = residualCount == 0 ? 0 : Math.sqrt(residualSq / residualCount);
        double residualChange = residualCount < 2 ? 0 : adjacentResidual / (residualCount - 1);

        // Haar-style high bands plus periodic 8-pixel boundary measurements.
        double horizontal = 0, vertical = 0, diagonal = 0, block = 0, nonBlock = 0;
        int pairs = 0, blocks = 0, nonBlocks = 0;
        for (int y = 0; y < height - 1; y++) for (int x = 0; x < width - 1; x++) {
            int i = y * width + x;
            horizontal += Math.abs(gray[i] - gray[i + 1]);
            vertical += Math.abs(gray[i] - gray[i + width]);
            diagonal += Math.abs(gray[i] - gray[i + width + 1]); pairs++;
            if (x > 0) {
                double d = Math.abs(gray[i] - gray[i - 1]);
                if (x % 8 == 0) { block += d; blocks++; } else { nonBlock += d; nonBlocks++; }
            }
        }
        horizontal /= Math.max(1, pairs); vertical /= Math.max(1, pairs); diagonal /= Math.max(1, pairs);
        double blockRatio = (block / Math.max(1, blocks)) / Math.max(.25, nonBlock / Math.max(1, nonBlocks));
        double directionalRatio = Math.max(horizontal, vertical) / Math.max(.25, Math.min(horizontal, vertical));
        Spectral spectral = spectral(gray, width, height);

        // Conservative caps: smoothness and compression are common in genuine and edited photos.
        out.noiseScore = clamp((meanResidual < 1.1 ? 24 : 0) + (residualChange < 2.1 ? 12 : 0) + (entropy < 5.0 ? 8 : 0), 45);
        out.frequencyScore = clamp((blockRatio > 1.85 ? 14 : 0) + (directionalRatio > 2.7 ? 8 : 0)
                + (diagonal < 1.8 ? 8 : 0) + (spectral.periodicPeak > 4.8 ? 8 : 0)
                + (spectral.lowMidRatio > 7.5 ? 7 : 0), 45);
        out.ganScore = clamp((blockRatio > 2.25 ? 18 : 0) + (directionalRatio > 3.2 ? 10 : 0)
                + (spectral.periodicPeak > 6.0 ? 8 : 0), 40);
        out.diffusionScore = clamp((meanResidual < .75 ? 18 : 0) + (residualStd < 2.2 ? 12 : 0) + (entropy < 4.6 ? 8 : 0), 42);
        out.pixelScore = clamp((entropy < 4.3 ? 15 : 0) + (saturation / pixels.length > .12 ? 10 : 0)
                + (channelDelta / pixels.length < 6 ? 8 : 0), 38);
        out.compressionScore = clamp(blockRatio > 1.65 ? 18 : 0, 25);

        if (meanResidual >= 1.5 && meanResidual <= 18 && residualChange > meanResidual * 1.15) out.cameraPipelineScore += 22;
        if (entropy >= 5.5 && entropy <= 7.9) out.cameraPipelineScore += 12;
        if (directionalRatio < 1.8) out.cameraPipelineScore += 8;
        out.cameraPipelineScore = Math.min(42, out.cameraPipelineScore);

        analyzeRegions(gray, width, height, out);
        out.signals.add(String.format(Locale.US, "Noise residual: %d%% supporting AI, %d%% supporting a camera pipeline (residual %.2f)", out.noiseScore, out.cameraPipelineScore, meanResidual));
        out.signals.add(String.format(Locale.US, "Frequency analysis (DCT, DFT and Haar bands): %d%% supporting AI (boundary %.2f, peak %.2f, low/mid %.2f)", out.frequencyScore, blockRatio, spectral.periodicPeak, spectral.lowMidRatio));
        out.signals.add("GAN artifact support: " + out.ganScore + "% (periodicity/upsampling proxies)");
        out.signals.add("Diffusion trace support: " + out.diffusionScore + "% (denoising-residual proxies)");
        out.signals.add(String.format(Locale.US, "Pixel statistics: %d%% supporting AI (entropy %.2f bits)", out.pixelScore, entropy));
        out.signals.add("Compression forensics: " + out.compressionScore + "% supporting AI; compression alone is not decisive");
        out.signals.add("Region analysis: " + out.regionSummary);
        out.signals.add("PRNU identity matching, anatomy, face, OCR, and physics models: unavailable; no score inferred");
        return out;
    }

    private static void analyzeRegions(float[] gray, int width, int height, ImageForensicEvidence out) {
        List<Integer> scores = new ArrayList<>(); int best = 0, bestX = 0, bestY = 0;
        for (int gy = 0; gy < 3; gy++) for (int gx = 0; gx < 3; gx++) {
            int left = gx * width / 3, right = (gx + 1) * width / 3;
            int top = gy * height / 3, bottom = (gy + 1) * height / 3;
            double diff = 0; int count = 0;
            for (int y = Math.max(top + 1, 1); y < Math.min(bottom - 1, height - 1); y += 2)
                for (int x = Math.max(left + 1, 1); x < Math.min(right - 1, width - 1); x += 2) {
                    int i = y * width + x;
                    diff += Math.abs(gray[i] - (gray[i - 1] + gray[i + 1] + gray[i - width] + gray[i + width]) * .25); count++;
                }
            double residual = diff / Math.max(1, count);
            int score = residual < .65 ? 48 : residual < 1.1 ? 32 : residual > 36 ? 28 : 0;
            scores.add(score); if (score > best) { best = score; bestX = gx; bestY = gy; }
        }
        Collections.sort(scores);
        out.suspiciousRegionScore = best;
        if (best > 0) out.regionSummary = best + "% supporting AI in " + regionName(bestX, bestY) + "; supporting evidence only";
    }

    private static String regionName(int x, int y) {
        String vertical = y == 0 ? "top" : y == 1 ? "middle" : "bottom";
        String horizontal = x == 0 ? "left" : x == 1 ? "center" : "right";
        return vertical + "-" + horizontal + " patch";
    }

    private static double entropy(int[] histogram, int total) {
        double value = 0;
        for (int count : histogram) if (count > 0) { double p = count / (double) total; value -= p * (Math.log(p) / Math.log(2)); }
        return value;
    }

    private static final class Spectral {
        double periodicPeak, lowMidRatio;
    }

    /** Small sampled DCT/DFT keeps gallery-wide background scans bounded in CPU and memory. */
    private static Spectral spectral(float[] gray, int width, int height) {
        Spectral out = new Spectral();
        double low = 0, mid = 0;
        double[][] cosine = new double[8][8];
        for (int u = 0; u < 8; u++) for (int x = 0; x < 8; x++)
            cosine[u][x] = Math.cos((2 * x + 1) * u * Math.PI / 16);
        for (int by = 0; by < 4; by++) for (int bx = 0; bx < 4; bx++) {
            int left = Math.max(0, (width - 8) * bx / 3), top = Math.max(0, (height - 8) * by / 3);
            for (int v = 0; v < 8; v++) for (int u = 0; u < 8; u++) {
                if (u == 0 && v == 0) continue;
                double coefficient = 0;
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                    coefficient += (gray[(top + y) * width + left + x] - 128) * cosine[u][x] * cosine[v][y];
                double energy = coefficient * coefficient;
                if (u + v <= 2) low += energy; else if (u + v <= 6) mid += energy;
            }
        }
        out.lowMidRatio = low / Math.max(1, mid);
        int count = Math.min(64, width), row = height / 2;
        double mean = 0;
        for (int x = 0; x < count; x++) mean += gray[row * width + x * width / count];
        mean /= Math.max(1, count);
        double peak = 0, sum = 0; int bins = 0;
        for (int k = 2; k <= Math.min(16, count / 2); k++) {
            double real = 0, imaginary = 0;
            for (int x = 0; x < count; x++) {
                double value = gray[row * width + x * width / count] - mean;
                double angle = 2 * Math.PI * k * x / count;
                real += value * Math.cos(angle); imaginary -= value * Math.sin(angle);
            }
            double magnitude = Math.hypot(real, imaginary);
            peak = Math.max(peak, magnitude); sum += magnitude; bins++;
        }
        out.periodicPeak = peak / Math.max(1, sum / Math.max(1, bins));
        return out;
    }

    private static int clamp(int value, int max) { return Math.max(0, Math.min(max, value)); }

    private static Bitmap decode(Context context, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) { if (input == null) return null; BitmapFactory.decodeStream(input, null, bounds); }
            int sample = 1; while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sample; options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) { return input == null ? null : BitmapFactory.decodeStream(input, null, options); }
        } catch (Exception ignored) { return null; }
    }
}
