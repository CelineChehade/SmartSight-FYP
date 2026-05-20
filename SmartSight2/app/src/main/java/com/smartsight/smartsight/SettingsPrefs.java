package com.example.smartsight;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

public class SettingsPrefs {

    private static final String PREFS_NAME = "SmartSightSettings";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_SPEECH_SPEED = "speech_speed";
    private static final String KEY_HIGH_CONTRAST = "high_contrast";

    public static final float MIN_SPEED = 0.5f;
    public static final float MAX_SPEED = 2.0f;
    public static final float SPEED_STEP = 0.25f;

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.contains(KEY_LANGUAGE)) {
            String systemLang = Locale.getDefault().getLanguage();
            String detected = "fr".equals(systemLang) ? "fr" : "en";
            prefs.edit().putString(KEY_LANGUAGE, detected).apply();
            return detected;
        }
        return prefs.getString(KEY_LANGUAGE, "en");
    }

    public static void setLanguage(Context context, String language) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    public static float getSpeechSpeed(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getFloat(KEY_SPEECH_SPEED, 1.0f);
    }

    public static void setSpeechSpeed(Context context, float speed) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putFloat(KEY_SPEECH_SPEED, speed).apply();
    }

    public static float getVoiceSpeed(Context context) {
        return getSpeechSpeed(context);
    }

    public static void setVoiceSpeed(Context context, float speed) {
        setSpeechSpeed(context, speed);
    }

    public static boolean isHighContrast(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_HIGH_CONTRAST, false);
    }

    public static void setHighContrast(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply();
    }

    public static void clearAll(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    public static void resetAll(Context context) {
        clearAll(context);
    }

    public static void speakWithDelay(android.speech.tts.TextToSpeech tts, String[] prompts, int delayMs, Runnable afterAction) {
        if (tts == null || prompts == null || prompts.length == 0) {
            if (afterAction != null) afterAction.run();
            return;
        }

        speakSequence(tts, prompts, 0, delayMs, afterAction);
    }

    private static void speakSequence(android.speech.tts.TextToSpeech tts, String[] prompts, int index, int delayMs, Runnable afterAction) {
        if (index >= prompts.length) {
            if (afterAction != null) afterAction.run();
            return;
        }

        String utterId = "seq_" + System.currentTimeMillis() + "_" + index;

        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}

            @Override
            public void onDone(String utteranceId) {
                if (utterId.equals(utteranceId)) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        speakSequence(tts, prompts, index + 1, delayMs, afterAction);
                    }, delayMs);
                }
            }

            @Override public void onError(String utteranceId) {
                if (utterId.equals(utteranceId)) {
                    speakSequence(tts, prompts, index + 1, delayMs, afterAction);
                }
            }
        });

        tts.speak(prompts[index], android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, utterId);
    }
}
