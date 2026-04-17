package com.example.smartsight;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class VoiceEditManager {

    public interface Callbacks {
        void onDeleteConfirmed(SavedItem item);
        void onRenameConfirmed(SavedItem item, String newName);
    }

    private enum State {
        IDLE,
        READING_ITEM,
        ASKING_ACTION,
        CONFIRMING_DELETE,
        ASKING_NEW_NAME,
        CONFIRMING_NEW_NAME
    }

    private final Context context;
    private final TextToSpeech tts;
    private final Callbacks callbacks;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy 'at' HH:mm", Locale.getDefault());

    private SpeechRecognizer recognizer;
    private State state = State.IDLE;
    private SavedItem currentItem;
    private String pendingNewName;

    public VoiceEditManager(Context context, TextToSpeech tts, Callbacks callbacks) {
        this.context = context.getApplicationContext();
        this.tts = tts;
        this.callbacks = callbacks;
        setupTtsListener();
    }

    // ---------- PUBLIC ENTRY ----------

    public void startEditFlow(SavedItem item) {
        if (state != State.IDLE) return;
        this.currentItem = item;
        this.pendingNewName = null;
        vibrateAndBeep();

        state = State.READING_ITEM;
        speak(buildItemDescription(item) + ". Do you want to delete or rename this item?");
    }

    public void cancelFlow() {
        stopListening();
        state = State.IDLE;
        currentItem = null;
        pendingNewName = null;
    }

    public void shutdown() {
        cancelFlow();
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    // ---------- TTS ----------

    private void setupTtsListener() {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onError(String utteranceId) {
                mainHandler.post(VoiceEditManager.this::onTtsFinished);
            }
            @Override public void onDone(String utteranceId) {
                // Only advance the flow if this was a VoiceEditManager utterance
                if ("vem".equals(utteranceId)) {
                    mainHandler.post(VoiceEditManager.this::onTtsFinished);
                }
            }
        });
    }

    private void speak(String message) {
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "vem");
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, params, "vem");
    }

    private void onTtsFinished() {
        switch (state) {
            case READING_ITEM:
                state = State.ASKING_ACTION;
                startListening();
                break;
            case ASKING_ACTION:
            case CONFIRMING_DELETE:
            case ASKING_NEW_NAME:
            case CONFIRMING_NEW_NAME:
                startListening();
                break;
            case IDLE:
            default:
                break;
        }
    }

    // ---------- SPEECH RECOGNITION ----------

    private void startListening() {
        stopListening();

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            speakFinal("Speech recognition is not available on this device.");
            resetToIdle();
            return;
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}
            @Override public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onError(int error) {
                // Silence / no match / busy -> just keep listening (no timeout).
                mainHandler.postDelayed(VoiceEditManager.this::restartListeningIfStillInFlow, 300);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String spoken = (matches != null && !matches.isEmpty())
                        ? matches.get(0).trim() : "";
                handleSpeechResult(spoken);
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L);

        try {
            recognizer.startListening(intent);
        } catch (Exception e) {
            mainHandler.postDelayed(this::restartListeningIfStillInFlow, 500);
        }
    }

    private void restartListeningIfStillInFlow() {
        if (state == State.IDLE) return;
        startListening();
    }

    private void stopListening() {
        if (recognizer != null) {
            try {
                recognizer.cancel();
                recognizer.destroy();
            } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    // ---------- STATE MACHINE ----------

    private void handleSpeechResult(String rawSpoken) {
        String spoken = rawSpoken.toLowerCase(Locale.getDefault()).trim();

        switch (state) {
            case ASKING_ACTION:
                if (containsAny(spoken, "delete", "remove", "erase")) {
                    state = State.CONFIRMING_DELETE;
                    speak("Are you sure you want to delete " + safeName(currentItem) + "?");
                } else if (containsAny(spoken, "rename", "change name", "new name")) {
                    state = State.ASKING_NEW_NAME;
                    speak("What is the new name?");
                } else {
                    speak("Please say delete or rename.");
                }
                break;

            case CONFIRMING_DELETE:
                if (containsYes(spoken)) {
                    SavedItem toDelete = currentItem;
                    resetToIdle();
                    speakFinal("Deleted.");
                    if (callbacks != null) callbacks.onDeleteConfirmed(toDelete);
                } else if (containsNo(spoken)) {
                    resetToIdle();
                    speakFinal("Cancelled.");
                } else {
                    speak("Please say yes or no.");
                }
                break;

            case ASKING_NEW_NAME:
                if (spoken.isEmpty()) {
                    speak("I didn't catch that. What is the new name?");
                } else {
                    // Preserve original casing for display, not lowercased version
                    pendingNewName = rawSpoken.trim();
                    state = State.CONFIRMING_NEW_NAME;
                    speak("I heard " + pendingNewName + ". Is this correct?");
                }
                break;

            case CONFIRMING_NEW_NAME:
                if (containsYes(spoken)) {
                    SavedItem toRename = currentItem;
                    String finalName = pendingNewName;
                    resetToIdle();
                    speakFinal("Renamed to " + finalName + ".");
                    if (callbacks != null) callbacks.onRenameConfirmed(toRename, finalName);
                } else if (containsNo(spoken)) {
                    // Per spec: cancel the whole rename
                    resetToIdle();
                    speakFinal("Cancelled.");
                } else {
                    speak("Please say yes or no.");
                }
                break;

            case IDLE:
            default:
                break;
        }
    }

    private void resetToIdle() {
        stopListening();
        state = State.IDLE;
        currentItem = null;
        pendingNewName = null;
    }

    /** Speak a final announcement that should NOT restart listening. */
    private void speakFinal(String message) {
        // state is already IDLE here, so onTtsFinished will do nothing
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "vem-final");
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, params, "vem-final");
    }

    // ---------- HELPERS ----------

    private String buildItemDescription(SavedItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("Item: ").append(safeName(item)).append(". ");
        sb.append("Saved on ").append(dateFormat.format(new Date(item.scanDate))).append(". ");
        if ("text".equalsIgnoreCase(item.category)) {
            sb.append("This is a text note. ");
            if (item.detectedName != null && !item.detectedName.isEmpty()) {
                sb.append("The text reads: ").append(item.detectedName).append(". ");
            }
        } else {
            sb.append("This is an object. ");
            if (item.detectedName != null && !item.detectedName.isEmpty()) {
                sb.append("Detected as ").append(item.detectedName).append(". ");
            }
        }
        return sb.toString();
    }

    private String safeName(SavedItem item) {
        return (item != null && item.customName != null && !item.customName.isEmpty())
                ? item.customName : "this item";
    }

    private boolean containsAny(String spoken, String... keywords) {
        if (spoken == null) return false;
        for (String k : keywords) if (spoken.contains(k)) return true;
        return false;
    }

    private boolean containsYes(String spoken) {
        return containsAny(spoken, "yes", "yeah", "yep", "sure", "confirm", "correct", "ok", "okay");
    }

    private boolean containsNo(String spoken) {
        return containsAny(spoken, "no", "nope", "cancel", "wrong", "incorrect");
    }

    private void vibrateAndBeep() {
        Vibrator vib = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vib != null && vib.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vib.vibrate(200);
            }
        }
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
        } catch (Exception ignored) {}
    }
}