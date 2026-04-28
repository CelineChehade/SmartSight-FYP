package com.example.smartsight;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import java.util.List;

public class ItemMatcher {

    private static final String TAG = "ItemMatcher";

    // LOWERED threshold - 60% match is enough for angle variations
    private static final float IMAGE_SIMILARITY_THRESHOLD = 0.55f;

    private final Context context;
    private final ImageFingerprintExtractor extractor;

    public ItemMatcher(Context context, ImageFingerprintExtractor extractor) {
        this.context = context.getApplicationContext();
        this.extractor = extractor;
    }

    /**
     * Check if scanned text matches any saved item.
     */
    public SavedItem matchText(String scannedText) {
        Log.d(TAG, "========================================");
        Log.d(TAG, "TEXT MATCH ATTEMPT");
        Log.d(TAG, "Scanned text: \"" + scannedText + "\"");

        if (scannedText == null || scannedText.trim().isEmpty()) {
            Log.d(TAG, "Empty input, returning null");
            Log.d(TAG, "========================================");
            return null;
        }

        String normalizedScan = normalize(scannedText);
        Log.d(TAG, "Normalized: \"" + normalizedScan + "\"");

        List<SavedItem> allItems = AppDatabase.getInstance(context)
                .itemDao().getAllItemsSync();

        Log.d(TAG, "Checking against " + allItems.size() + " saved items");

        for (SavedItem item : allItems) {
            if (!"text".equalsIgnoreCase(item.category)) continue;
            if (item.detectedName == null) continue;

            String normalizedSaved = normalize(item.detectedName);

            if (normalizedScan.equals(normalizedSaved)
                    || normalizedScan.contains(normalizedSaved)
                    || normalizedSaved.contains(normalizedScan)) {
                Log.d(TAG, "✅ TEXT MATCH FOUND: " + item.customName);
                Log.d(TAG, "========================================");
                return item;
            }
        }

        Log.d(TAG, "❌ No text match found");
        Log.d(TAG, "========================================");
        return null;
    }

    /**
     * Check if scanned object matches any saved object.
     * Now with lower threshold to handle angle variations.
     */
    public SavedItem matchObject(Bitmap croppedObject, String detectedLabel) {
        Log.d(TAG, "========================================");
        Log.d(TAG, "OBJECT MATCH ATTEMPT");
        Log.d(TAG, "Detected label: \"" + detectedLabel + "\"");

        if (croppedObject == null || detectedLabel == null) {
            Log.d(TAG, "Null input, returning null");
            Log.d(TAG, "========================================");
            return null;
        }
        if (extractor == null) {
            Log.e(TAG, "Extractor is null!");
            Log.d(TAG, "========================================");
            return null;
        }

        String scanFingerprint = extractor.extractFingerprint(croppedObject);
        if (scanFingerprint == null) {
            Log.e(TAG, "Failed to extract fingerprint");
            Log.d(TAG, "========================================");
            return null;
        }

        Log.d(TAG, "Scan fingerprint: " + scanFingerprint.substring(0, Math.min(24, scanFingerprint.length())) + "...");

        List<SavedItem> allItems = AppDatabase.getInstance(context)
                .itemDao().getAllItemsSync();

        Log.d(TAG, "Checking against " + allItems.size() + " saved items");

        SavedItem bestMatch = null;
        float bestScore = 0f;
        String bestMatchReason = "";

        for (SavedItem item : allItems) {
            if (!"object".equalsIgnoreCase(item.category)) {
                continue;
            }
            if (item.imageFingerprint == null) {
                Log.d(TAG, "  Item \"" + item.customName + "\": ❌ no fingerprint stored");
                continue;
            }

            // Label must match
            if (item.detectedName != null
                    && !item.detectedName.equalsIgnoreCase(detectedLabel)) {
                Log.d(TAG, "  Item \"" + item.customName + "\": ❌ label mismatch (saved=\"" +
                        item.detectedName + "\" vs scan=\"" + detectedLabel + "\")");
                continue;
            }

            float score = ImageFingerprintExtractor.compareSimilarity(
                    scanFingerprint, item.imageFingerprint);

            String status = score >= IMAGE_SIMILARITY_THRESHOLD ? "✅ MATCH" : "❌ below threshold";
            Log.d(TAG, "  Item \"" + item.customName + "\": " +
                    String.format("%.1f%%", score * 100) + " " + status);

            if (score > bestScore) {
                bestScore = score;
                bestMatch = item;
                bestMatchReason = String.format("%.1f%% similarity", score * 100);
            }
        }

        if (bestMatch != null && bestScore >= IMAGE_SIMILARITY_THRESHOLD) {
            Log.d(TAG, "✅ BEST MATCH: \"" + bestMatch.customName + "\" (" + bestMatchReason + ")");
            Log.d(TAG, "========================================");
            return bestMatch;
        } else {
            if (bestMatch != null) {
                Log.d(TAG, "❌ Best candidate \"" + bestMatch.customName + "\" only scored " +
                        String.format("%.1f%%", bestScore * 100) + " (threshold=" +
                        String.format("%.1f%%", IMAGE_SIMILARITY_THRESHOLD * 100) + ")");
            } else {
                Log.d(TAG, "❌ No candidates found");
            }
            Log.d(TAG, "========================================");
            return null;
        }
    }

    /**
     * Crop a bitmap to a bounding box.
     */
    public static Bitmap cropToBoundingBox(Bitmap source, Rect box) {
        if (source == null || box == null) return source;

        int left = Math.max(0, box.left);
        int top = Math.max(0, box.top);
        int right = Math.min(source.getWidth(), box.right);
        int bottom = Math.min(source.getHeight(), box.bottom);

        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) return source;

        return Bitmap.createBitmap(source, left, top, width, height);
    }

    /**
     * Normalize text for comparison.
     */
    private String normalize(String s) {
        return s.toLowerCase()
                .replaceAll("\\s+", " ")
                .replaceAll("[^a-z0-9 ]", "")
                .trim();
    }
}
