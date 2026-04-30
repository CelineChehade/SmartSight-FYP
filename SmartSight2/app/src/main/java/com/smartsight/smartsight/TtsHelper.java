package com.example.smartsight;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

public class TtsHelper {

    private static final String TAG = "TtsHelper";

    /**
     * Extra silence (ms) added AFTER TTS onDone before we clear ttsActive.
     * French sentences are longer so the audio pipeline needs more time to
     * flush. 600 ms is safe for both languages on most devices.
     */
    private static final long POST_SPEECH_SILENCE_MS = 600;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Volatile so SharedSpeechRecognizer can read it from any thread.
    private static volatile boolean ttsActive = false;

    /** True while TTS audio is playing OR during the post-speech silence buffer. */
    public static boolean isTtsActive() {
        return ttsActive;
    }

    public static void applySettings(Context context, TextToSpeech tts) {
        if (tts == null) return;
        String lang = SettingsPrefs.getLanguage(context);
        Locale locale = "fr".equals(lang) ? Locale.FRENCH : Locale.US;
        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA
                || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Language not supported: " + locale);
            tts.setLanguage(Locale.US);
        }
        tts.setSpeechRate(SettingsPrefs.getSpeechSpeed(context));
    }

    /** Fire-and-forget. Interrupts any current speech. */
    public static void speak(TextToSpeech tts, String text) {
        if (tts == null || text == null || text.trim().isEmpty()) return;
        ttsActive = true;
        // Cancel any pending silence-clear callback from a previous utterance
        mainHandler.removeCallbacksAndMessages(TAG);

        final String id = "speak_" + System.currentTimeMillis();
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String u) {}

            @Override
            public void onDone(String u) {
                if (id.equals(u)) {
                    // Post-speech silence: keep ttsActive=true for a bit longer
                    mainHandler.postAtTime(() -> ttsActive = false,
                            TAG, android.os.SystemClock.uptimeMillis() + POST_SPEECH_SILENCE_MS);
                }
            }

            @Override
            public void onError(String u) {
                if (id.equals(u)) {
                    mainHandler.postAtTime(() -> ttsActive = false,
                            TAG, android.os.SystemClock.uptimeMillis() + POST_SPEECH_SILENCE_MS);
                }
            }
        });
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    /**
     * Speak text, then run action on the main thread after speech AND the
     * post-speech silence buffer have both completed.
     *
     * This is the key method that prevents TTS/STT overlap: the action
     * (which ultimately calls SharedSpeechRecognizer.listen()) is only
     * invoked after ttsActive has been cleared.
     */
    public static void speakThen(TextToSpeech tts, String text, Runnable action) {
        if (tts == null || text == null || text.trim().isEmpty()) {
            if (action != null) action.run();
            return;
        }
        ttsActive = true;
        mainHandler.removeCallbacksAndMessages(TAG);

        final String id = "then_" + System.currentTimeMillis();
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String u) {}

            @Override
            public void onDone(String u) {
                if (!id.equals(u)) return;
                // Wait for the post-speech silence, THEN clear flag AND fire action
                mainHandler.postAtTime(() -> {
                    ttsActive = false;
                    if (action != null) action.run();
                }, TAG, android.os.SystemClock.uptimeMillis() + POST_SPEECH_SILENCE_MS);
            }

            @Override
            public void onError(String u) {
                if (!id.equals(u)) return;
                mainHandler.postAtTime(() -> {
                    ttsActive = false;
                    if (action != null) action.run();
                }, TAG, android.os.SystemClock.uptimeMillis() + POST_SPEECH_SILENCE_MS);
            }
        });
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    // ── Yes / No helpers ──────────────────────────────────────────────────────

    public static boolean isYes(String spoken) {
        if (spoken == null) return false;
        String s = spoken.toLowerCase().trim();
        return s.matches(
                ".*(yes|yeah|yup|yep|yea|sure|okay|ok|correct|right|confirm|save"
                        + "|oui|ouais|d'accord|daccord|bien sûr|bien sur).*");
    }

    public static boolean isNo(String spoken) {
        if (spoken == null) return false;
        String s = spoken.toLowerCase().trim();
        return s.matches(
                ".*(no|nope|nah|don't|dont|do not|cancel|skip|wrong|incorrect"
                        + "|non|annuler|pas question|jamais).*");
    }
}
