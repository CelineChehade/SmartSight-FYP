package com.example.smartsight;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import java.util.List;

public class ItemMatcher {

    private static final String TAG = "ItemMatcher";

    // 70% of hash bits must match
    private static final float IMAGE_SIMILARITY_THRESHOLD = 0.70f;

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
        Log.d(TAG, "matchText called with: " + scannedText);

        if (scannedText == null || scannedText.trim().isEmpty()) {
            Log.d(TAG, "matchText: empty input");
            return null;
        }

        String normalizedScan = normalize(scannedText);

        List<SavedItem> allItems = AppDatabase.getInstance(context)
                .itemDao().getAllItemsSync();

        Log.d(TAG, "matchText: found " + allItems.size() + " saved items total");

        for (SavedItem item : allItems) {
            if (!"text".equalsIgnoreCase(item.category)) continue;
            if (item.detectedName == null) continue;

            String normalizedSaved = normalize(item.detectedName);

            Log.d(TAG, "Compare TEXT: scan='" + normalizedScan +
                    "' vs saved='" + normalizedSaved + "'");

            if (normalizedScan.equals(normalizedSaved)
                    || normalizedScan.contains(normalizedSaved)
                    || normalizedSaved.contains(normalizedScan)) {
                Log.d(TAG, "TEXT MATCH: " + item.customName);
                return item;
            }
        }

        Log.d(TAG, "matchText: no match found");
        return null;
    }

    /**
     * Check if scanned object (cropped from bounding box) matches any saved object.
     */
    public SavedItem matchObject(Bitmap croppedObject, String detectedLabel) {
        Log.d(TAG, "matchObject called with label: " + detectedLabel);

        if (croppedObject == null || detectedLabel == null) {
            Log.d(TAG, "matchObject: null input, returning null");
            return null;
        }
        if (extractor == null) {
            Log.d(TAG, "matchObject: extractor is null");
            return null;
        }

        String scanFingerprint = extractor.extractFingerprint(croppedObject);
        if (scanFingerprint == null) {
            Log.d(TAG, "matchObject: fingerprint is null");
            return null;
        }

        Log.d(TAG, "Scan fingerprint len=" + scanFingerprint.length());

        List<SavedItem> allItems = AppDatabase.getInstance(context)
                .itemDao().getAllItemsSync();

        Log.d(TAG, "matchObject: found " + allItems.size() + " saved items total");

        SavedItem bestMatch = null;
        float bestScore = IMAGE_SIMILARITY_THRESHOLD - 0.01f;  // Initialize just below threshold

        for (SavedItem item : allItems) {
            Log.d(TAG, "Inspecting item: " + item.customName
                    + " | category=" + item.category
                    + " | detectedName=" + item.detectedName
                    + " | has fingerprint=" + (item.imageFingerprint != null));

            if (!"object".equalsIgnoreCase(item.category)) {
                Log.d(TAG, "  skip: not an object");
                continue;
            }
            if (item.imageFingerprint == null) {
                Log.d(TAG, "  skip: no fingerprint stored");
                continue;
            }

            if (item.detectedName != null
                    && !item.detectedName.equalsIgnoreCase(detectedLabel)) {
                Log.d(TAG, "  skip: label mismatch (saved=" +
                        item.detectedName + " vs scan=" + detectedLabel + ")");
                continue;
            }

            float score = ImageFingerprintExtractor.compareSimilarity(
                    scanFingerprint, item.imageFingerprint);

            Log.d(TAG, "  SCORE for " + item.customName + " = " + score);

            if (score >= bestScore) {  // Use >= to catch exact threshold matches
                bestScore = score;
                bestMatch = item;
            }
        }

        if (bestMatch != null) {
            Log.d(TAG, "OBJECT MATCH: " + bestMatch.customName
                    + " (score=" + bestScore + ")");
        } else {
            Log.d(TAG, "matchObject: no match above threshold " + IMAGE_SIMILARITY_THRESHOLD);
        }
        return bestMatch;
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
     * Normalize text for comparison: lowercase, collapse whitespace, remove punctuation.
     */
    private String normalize(String s) {
        return s.toLowerCase()
                .replaceAll("\\s+", " ")
                .replaceAll("[^a-z0-9 ]", "")
                .trim();
    }
}
