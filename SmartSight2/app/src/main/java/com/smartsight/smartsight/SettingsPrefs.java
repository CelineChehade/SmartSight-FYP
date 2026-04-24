package com.example.smartsight;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsPrefs {

    private static final String PREFS_NAME = "smartsight_settings";

    private static final String KEY_VOICE_SPEED = "voice_speed";
    private static final String KEY_HIGH_CONTRAST = "high_contrast";
    private static final String KEY_LANGUAGE = "language";

    // Defaults
    public static final float DEFAULT_SPEED = 1.0f;
    public static final boolean DEFAULT_CONTRAST = false;
    public static final String DEFAULT_LANGUAGE = "en";

    // Bounds
    public static final float MIN_SPEED = 0.5f;
    public static final float MAX_SPEED = 2.0f;
    public static final float SPEED_STEP = 0.2f;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ────────── VOICE SPEED ──────────
    public static float getVoiceSpeed(Context c) {
        return prefs(c).getFloat(KEY_VOICE_SPEED, DEFAULT_SPEED);
    }
    public static void setVoiceSpeed(Context c, float speed) {
        float clamped = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
        prefs(c).edit().putFloat(KEY_VOICE_SPEED, clamped).apply();
    }

    // ────────── HIGH CONTRAST ──────────
    public static boolean isHighContrast(Context c) {
        return prefs(c).getBoolean(KEY_HIGH_CONTRAST, DEFAULT_CONTRAST);
    }
    public static void setHighContrast(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_HIGH_CONTRAST, on).apply();
    }

    // ────────── LANGUAGE ──────────
    public static String getLanguage(Context c) {
        return prefs(c).getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
    }
    public static void setLanguage(Context c, String lang) {
        prefs(c).edit().putString(KEY_LANGUAGE, lang).apply();
    }

    // ────────── RESET ──────────
    public static void clearAll(Context c) {
        prefs(c).edit().clear().apply();
    }
}