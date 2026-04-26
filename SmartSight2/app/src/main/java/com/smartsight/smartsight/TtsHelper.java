package com.example.smartsight;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.concurrent.atomic.AtomicInteger;

public class TtsHelper {

    /** Apply saved settings (speed + language) to a TTS instance. Call after creating TTS. */
    public static void applySettings(Context c, TextToSpeech tts) {
        if (tts == null) return;
        tts.setLanguage(LocaleManager.getCurrentLocale(c));
        tts.setSpeechRate(SettingsPrefs.getVoiceSpeed(c));
    }

    /** Speak a single message, stopping anything currently playing. */
    public static void speak(TextToSpeech tts, String message) {
        if (tts == null) return;
        tts.stop();
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    /**
     * Speak messages one after another. Each message starts only AFTER the
     * previous one actually finishes (real onDone callback — no timers).
     * onAllDone runs after the very last message has finished speaking.
     *
     * This is what prevents overlap.
     */
    public static void speakSequence(TextToSpeech tts, String[] messages, Runnable onAllDone) {
        if (tts == null || messages == null || messages.length == 0) {
            if (onAllDone != null) onAllDone.run();
            return;
        }

        tts.stop();

        final AtomicInteger index = new AtomicInteger(0);
        final String idPrefix = "seq_" + System.currentTimeMillis() + "_";

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}

            @Override
            public void onDone(String utteranceId) {
                int next = index.incrementAndGet();
                if (next < messages.length) {
                    tts.speak(messages[next], TextToSpeech.QUEUE_ADD, new Bundle(), idPrefix + next);
                } else {
                    if (onAllDone != null) onAllDone.run();
                }
            }

            @Override
            public void onError(String utteranceId) {
                int next = index.incrementAndGet();
                if (next < messages.length) {
                    tts.speak(messages[next], TextToSpeech.QUEUE_ADD, new Bundle(), idPrefix + next);
                } else {
                    if (onAllDone != null) onAllDone.run();
                }
            }
        });

        tts.speak(messages[0], TextToSpeech.QUEUE_FLUSH, new Bundle(), idPrefix + 0);
    }

    /**
     * Kept for backward compatibility. Now internally uses speakSequence
     * (delayMs is ignored because we wait for real onDone instead).
     */
    public static void speakWithDelay(TextToSpeech tts, String[] messages, int delayMs, Runnable onDone) {
        speakSequence(tts, messages, onDone);
    }
}
