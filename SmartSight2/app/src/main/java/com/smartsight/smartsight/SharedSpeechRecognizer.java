package com.example.smartsight;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;

public class SharedSpeechRecognizer {

    private static final String TAG = "SharedSTT";

    private static final long POLL_INTERVAL_MS = 100;
    private static final long SETTLE_MS        = 300;
    private static final long MAX_WAIT_MS      = 6000;

    private static SharedSpeechRecognizer instance;

    // ── Result ────────────────────────────────────────────────────────────────

    public static class Result {
        public final boolean           success;
        public final String            text;
        public final ArrayList<String> allResults;
        public final int               errorCode;

        Result(ArrayList<String> matches) {
            success    = true;
            text       = matches.get(0);
            allResults = matches;
            errorCode  = 0;
        }

        Result(int errorCode) {
            success    = false;
            text       = null;
            allResults = new ArrayList<>();
            this.errorCode = errorCode;
        }
    }

    public interface ResultListener {
        void onResult(Result result);
    }

    // ── Singleton ─────────────────────────────────────────────────────────────

    public static void init(Context context) {
        if (instance == null) {
            instance = new SharedSpeechRecognizer(context.getApplicationContext());
        }
    }

    public static SharedSpeechRecognizer get() {
        if (instance == null)
            throw new IllegalStateException("Call SharedSpeechRecognizer.init() first");
        return instance;
    }

    // ── Instance ──────────────────────────────────────────────────────────────

    private final Context    appContext;
    private SpeechRecognizer recognizer;
    private final Handler    handler = new Handler(Looper.getMainLooper());

    private volatile boolean        isListening     = false;
    private volatile ResultListener pendingListener = null;

    private SharedSpeechRecognizer(Context appContext) {
        this.appContext = appContext;
        buildRecognizer();
    }

    private void buildRecognizer() {
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) {}
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext);
        recognizer.setRecognitionListener(new RecognitionListener() {

            @Override public void onReadyForSpeech(android.os.Bundle p) { Log.d(TAG, "✓ Ready"); }
            @Override public void onBeginningOfSpeech()                  { Log.d(TAG, "✓ Speaking"); }
            @Override public void onEndOfSpeech()                        { Log.d(TAG, "✓ End"); }
            @Override public void onBufferReceived(byte[] b)             {}
            @Override public void onEvent(int t, android.os.Bundle p)   {}
            @Override public void onRmsChanged(float r)                  {}
            @Override public void onPartialResults(android.os.Bundle r) {}

            @Override
            public void onResults(android.os.Bundle results) {
                isListening = false;
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                Log.d(TAG, "Results: " + matches);

                ResultListener listener = pendingListener;
                pendingListener = null;
                if (listener == null) return;

                if (matches != null && !matches.isEmpty()) {
                    handler.post(() -> listener.onResult(new Result(matches)));
                } else {
                    handler.post(() -> listener.onResult(
                            new Result(SpeechRecognizer.ERROR_NO_MATCH)));
                }
            }

            @Override
            public void onError(int error) {
                isListening = false;
                Log.e(TAG, "STT error: " + error);

                ResultListener listener = pendingListener;
                pendingListener = null;
                if (listener != null) {
                    handler.post(() -> listener.onResult(new Result(error)));
                }
            }
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void listen(Context context, ResultListener listener) {
        if (isListening) {
            Log.w(TAG, "Already listening — ignoring duplicate listen() call");
            return;
        }
        waitForTtsThenListen(context, listener, 0);
    }

    private void waitForTtsThenListen(Context context, ResultListener listener,
                                      long totalWaitedMs) {
        if (TtsHelper.isTtsActive()) {
            if (totalWaitedMs >= MAX_WAIT_MS) {
                Log.w(TAG, "TTS still active after " + MAX_WAIT_MS + " ms — starting STT anyway");
                startRecognizer(context, listener);
                return;
            }
            handler.postDelayed(
                    () -> waitForTtsThenListen(context, listener,
                            totalWaitedMs + POLL_INTERVAL_MS),
                    POLL_INTERVAL_MS);
        } else {
            Log.d(TAG, "TTS done, settling " + SETTLE_MS + " ms before mic open");
            handler.postDelayed(() -> startRecognizer(context, listener), SETTLE_MS);
        }
    }

    private void startRecognizer(Context context, ResultListener listener) {
        if (isListening) {
            Log.w(TAG, "Already listening after settle — dropping duplicate");
            return;
        }
        Intent intent = buildIntent(context);
        pendingListener = listener;
        try {
            isListening = true;
            recognizer.startListening(intent);
            Log.d(TAG, "🎤 Mic open");
        } catch (Exception e) {
            isListening     = false;
            pendingListener = null;
            Log.e(TAG, "startListening failed: " + e.getMessage());
            buildRecognizer();
            handler.post(() -> listener.onResult(new Result(SpeechRecognizer.ERROR_CLIENT)));
        }
    }

    public void cancel() {
        if (isListening) {
            try { recognizer.stopListening(); } catch (Exception ignored) {}
        }
        isListening     = false;
        pendingListener = null;
        handler.removeCallbacksAndMessages(null);
    }

    public boolean isListening() { return isListening; }

    public void destroy() {
        cancel();
        try { recognizer.destroy(); } catch (Exception ignored) {}
        instance = null;
    }

    // ── Intent builder ────────────────────────────────────────────────────────

    private Intent buildIntent(Context context) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                LocaleManager.getSttLanguageTag(context));
        // REMOVED: EXTRA_PREFER_OFFLINE — French offline recognition is not installed
        // on most devices. Forcing offline caused immediate ERROR_NO_MATCH in French.
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        // MINIMUM_LENGTH removed — it caused the recogniser to discard one-syllable
        // words like "yes"/"no"/"oui" that are spoken in under 300 ms.
        // Silence windows tightened so short commands finalise quickly.
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L);
        return intent;
    }
}
