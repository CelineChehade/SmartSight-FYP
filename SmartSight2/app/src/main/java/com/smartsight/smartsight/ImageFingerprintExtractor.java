package com.example.smartsight;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

public class ImageFingerprintExtractor {

    private static final int HASH_SIZE = 16;  // produces 64-bit hash

    public ImageFingerprintExtractor(android.content.Context context) {
        // No model to load — pure algorithmic approach
        Log.d("FingerprintExtractor", "Using perceptual hash (no model)");
    }

    /**
     * Extract a perceptual hash fingerprint from a bitmap.
     * Returns a 64-bit binary string (e.g. "1011010010..."), or null on failure.
     */
    public String extractFingerprint(Bitmap bitmap) {
        if (bitmap == null) return null;

        try {
            // Step 1: Resize to 8x8
            Bitmap small = Bitmap.createScaledBitmap(bitmap, HASH_SIZE, HASH_SIZE, true);

            // Step 2: Convert to grayscale and compute average
            int[] pixels = new int[HASH_SIZE * HASH_SIZE];
            int[] gray = new int[HASH_SIZE * HASH_SIZE];
            small.getPixels(pixels, 0, HASH_SIZE, 0, 0, HASH_SIZE, HASH_SIZE);

            int total = 0;
            for (int i = 0; i < pixels.length; i++) {
                int r = Color.red(pixels[i]);
                int g = Color.green(pixels[i]);
                int b = Color.blue(pixels[i]);
                gray[i] = (r + g + b) / 3;
                total += gray[i];
            }
            int average = total / pixels.length;

            // Step 3: Build hash — 1 if pixel >= average, else 0
            StringBuilder hash = new StringBuilder();
            for (int g2 : gray) {
                hash.append(g2 >= average ? "1" : "0");
            }

            return hash.toString();

        } catch (Exception e) {
            Log.e("FingerprintExtractor", "Extraction failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Compare two hash fingerprints using Hamming distance.
     * Returns similarity 0.0 (totally different) to 1.0 (identical).
     */
    public static float compareSimilarity(String fp1, String fp2) {
        if (fp1 == null || fp2 == null) return 0f;
        if (fp1.length() != fp2.length()) return 0f;

        int matches = 0;
        for (int i = 0; i < fp1.length(); i++) {
            if (fp1.charAt(i) == fp2.charAt(i)) matches++;
        }
        return (float) matches / fp1.length();
    }

    public void close() {
        // Nothing to close
    }
}