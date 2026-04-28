package com.example.smartsight;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;

/**
 * Centralized TTS management to prevent overlapping speech.
 */
public class TtsHelper {

    private static final String TAG = "TtsHelper";

    /**
     * Apply user settings (speed, locale) to a TTS instance.
     */
    public static void applySettings(Context context, TextToSpeech tts) {
        if (tts == null) return;

        String lang = SettingsPrefs.getLanguage(context);
        Locale locale = "fr".equals(lang) ? Locale.FRENCH : Locale.US;

        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Language not supported: " + locale);
            tts.setLanguage(Locale.US);
        }

        float speed = SettingsPrefs.getSpeechSpeed(context);
        tts.setSpeechRate(speed);

        Log.d(TAG, "TTS configured: lang=" + locale + " speed=" + speed);
    }

    /**
     * Speak immediately, interrupting any current speech.
     */
    public static void speak(TextToSpeech tts, String text) {
        if (tts == null || text == null || text.trim().isEmpty()) return;

        // Stop any ongoing TTS
        tts.stop();

        // ✅ Small delay to ensure TalkBack releases audio focus
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            Log.d(TAG, "Speaking: " + text.substring(0, Math.min(50, text.length())));
        }, 100); // 100ms delay
    }

    /**
     * Speak, then execute action when done.
     * FIXED: Properly handles TalkBack interruption and ensures callback fires.
     */
    public static void speakThen(TextToSpeech tts, String text, Runnable action) {
        if (tts == null || text == null || text.trim().isEmpty()) {
            if (action != null) action.run();
            return;
        }

        // Stop any ongoing speech
        tts.stop();

        String utterId = "tts_" + System.currentTimeMillis();

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "TTS started: " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                if (utterId.equals(utteranceId)) {
                    Log.d(TAG, "TTS done: " + utteranceId);
                    if (action != null) {
                        // ✅ Post to main thread with slight delay for audio cleanup
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(action, 200);
                    }
                }
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS error: " + utteranceId);
                if (utterId.equals(utteranceId) && action != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(action, 200);
                }
            }
        });

        // ✅ Small delay to ensure TalkBack releases audio focus
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utterId);
            Log.d(TAG, "Queued speech (then action): " + text.substring(0, Math.min(50, text.length())));
        }, 100); // 100ms delay
    }

    /**
     * Improved yes/no detection with logging.
     */
    public static boolean isYes(String spoken) {
        if (spoken == null) return false;
        String s = spoken.toLowerCase().trim();

        boolean match = s.matches(".*(yes|yeah|yup|yep|yea|sure|okay|ok|correct|right|confirm|save|oui|ouais|d'accord).*");
        Log.d(TAG, "isYes(\"" + s + "\") = " + match);
        return match;
    }

    public static boolean isNo(String spoken) {
        if (spoken == null) return false;
        String s = spoken.toLowerCase().trim();

        boolean match = s.matches(".*(no|nope|nah|don't|dont|do not|cancel|skip|wrong|incorrect|non|annuler).*");
        Log.d(TAG, "isNo(\"" + s + "\") = " + match);
        return match;
    }
}
