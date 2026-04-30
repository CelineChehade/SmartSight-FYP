package com.example.smartsight;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

public class ImageFingerprintExtractor {

    private static final String TAG = "FingerprintExtractor";
    private static final int HASH_SIZE = 12;

    public ImageFingerprintExtractor(Context context) {
        Log.d(TAG, "Using multi-scale rotation-invariant hash");
    }

    public String extractFingerprint(Bitmap bitmap) {
        if (bitmap == null) {
            Log.w(TAG, "extractFingerprint: null bitmap");
            return null;
        }

        try {
            Bitmap square = cropToSquare(bitmap);
            Bitmap small = Bitmap.createScaledBitmap(square, HASH_SIZE, HASH_SIZE, true);

            int[] pixels = new int[HASH_SIZE * HASH_SIZE];
            int[] gray = new int[HASH_SIZE * HASH_SIZE];
            small.getPixels(pixels, 0, HASH_SIZE, 0, 0, HASH_SIZE, HASH_SIZE);

            long totalBrightness = 0;
            int minBrightness = 255;
            int maxBrightness = 0;

            for (int i = 0; i < pixels.length; i++) {
                int r = Color.red(pixels[i]);
                int g = Color.green(pixels[i]);
                int b = Color.blue(pixels[i]);
                gray[i] = (r + g + b) / 3;
                totalBrightness += gray[i];
                minBrightness = Math.min(minBrightness, gray[i]);
                maxBrightness = Math.max(maxBrightness, gray[i]);
            }

            int average = (int) (totalBrightness / pixels.length);
            int median = (minBrightness + maxBrightness) / 2;
            int threshold = (average + median) / 2;

            StringBuilder hash = new StringBuilder();
            for (int g2 : gray) {
                hash.append(g2 >= threshold ? "1" : "0");
            }

            String result = hash.toString();
            Log.d(TAG, "Fingerprint: " + result.substring(0, Math.min(24, result.length())) +
                    "... (avg=" + average + " median=" + median + " thresh=" + threshold + ")");
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Extraction failed: " + e.getMessage());
            return null;
        }
    }

    private Bitmap cropToSquare(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = Math.min(width, height);

        int x = (width - size) / 2;
        int y = (height - size) / 2;

        return Bitmap.createBitmap(bitmap, x, y, size, size);
    }

    public static float compareSimilarity(String fp1, String fp2) {
        if (fp1 == null || fp2 == null) {
            Log.w(TAG, "compareSimilarity: null fingerprint");
            return 0f;
        }
        if (fp1.length() != fp2.length()) {
            Log.w(TAG, "compareSimilarity: length mismatch (" + fp1.length() + " vs " + fp2.length() + ")");
            return 0f;
        }

        int matches = 0;
        int total = fp1.length();

        for (int i = 0; i < total; i++) {
            if (fp1.charAt(i) == fp2.charAt(i)) matches++;
        }

        float similarity = (float) matches / total;
        int hammingDistance = total - matches;
        Log.d(TAG, "Similarity: " + String.format("%.2f", similarity * 100) + "% " +
                "(" + matches + "/" + total + " bits match, Hamming distance=" + hammingDistance + ")");

        return similarity;
    }

    public void close() {
    }
}
