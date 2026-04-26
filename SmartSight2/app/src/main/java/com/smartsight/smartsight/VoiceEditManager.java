package com.example.smartsight;

import android.content.Context;

import android.content.Intent;

import android.os.Bundle;

import android.os.Handler;

import android.os.Looper;

import android.speech.RecognitionListener;

import android.speech.RecognizerIntent;

import android.speech.SpeechRecognizer;

import android.speech.tts.TextToSpeech;

import android.speech.tts.UtteranceProgressListener;

import android.util.Log;

import java.util.ArrayList;

/**

 * Handles the long-hold voice menu in SavedItemsActivity.

 * Asks: rename / add-or-edit reminder / delete reminder / delete.

 * Owns its own SpeechRecognizer to avoid conflicts with the activity.

 */

public class VoiceEditManager {

    private static final String TAG = "VoiceEditManager";

    public interface Callbacks {

        void onActionRename(String itemName);

        void onActionAddReminder(String itemName);

        void onActionEditReminder(String itemName);

        void onActionDeleteReminder(String itemName);

        void onActionDelete(String itemName);

        void onCancelled();

    }

    private enum State { IDLE, ASK_ACTION, RENAME_ASK_NAME, RENAME_CONFIRM, DELETE_CONFIRM }

    private final Context context;

    private final TextToSpeech tts;

    private final Callbacks callbacks;

    private final SpeechRecognizer recognizer;

    private final Intent recognizerIntent;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private State state = State.IDLE;

    private String currentItemName;

    private boolean hasReminder;

    private String pendingNewName;

    private boolean isListening = false;

    private boolean isShutdown = false;

    public VoiceEditManager(Context context, TextToSpeech tts, Callbacks callbacks) {

        this.context = LocaleManager.wrap(context.getApplicationContext());

        this.tts = tts;

        this.callbacks = callbacks;

        recognizer = SpeechRecognizer.createSpeechRecognizer(context);

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,

                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,

                LocaleManager.getSttLanguageTag(context));

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);

        recognizer.setRecognitionListener(new RecognitionListener() {

            @Override public void onReadyForSpeech(Bundle params) {}

            @Override public void onBeginningOfSpeech() {}

            @Override public void onBufferReceived(byte[] buffer) {}

            @Override public void onEndOfSpeech() {}

            @Override public void onEvent(int eventType, Bundle params) {}

            @Override public void onPartialResults(Bundle partialResults) {}

            @Override public void onRmsChanged(float rmsdB) {}

            @Override

            public void onResults(Bundle results) {

                isListening = false;

                if (isShutdown) return;

                ArrayList<String> matches =

                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (matches != null && !matches.isEmpty()) {

                    handleSpeech(matches);

                } else {

                    repromptCurrentState();

                }

            }

            @Override

            public void onError(int error) {

                isListening = false;

                if (isShutdown) return;

                Log.w(TAG, "STT error: " + error);

                switch (error) {

                    case SpeechRecognizer.ERROR_NO_MATCH:

                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:

                        repromptCurrentState();

                        break;

                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:

                    case SpeechRecognizer.ERROR_CLIENT:

                        mainHandler.postDelayed(VoiceEditManager.this::startListening, 600);

                        break;

                    default:

                        mainHandler.postDelayed(VoiceEditManager.this::startListening, 600);

                }

            }

        });

    }

    /**

     * Kick off the menu for a specific item.

     * @param itemName the item's customName

     * @param hasReminder whether the item already has a reminder

     */

    public void buildItemDescription(String itemName, boolean hasReminder) {

        this.currentItemName = itemName;

        this.hasReminder = hasReminder;

        state = State.ASK_ACTION;

        String prompt = buildActionPrompt();

        speakAndListen(itemName + ". " + prompt);

    }

    public void shutdown() {

        try {

            isShutdown = true;

            isListening = false;

            mainHandler.removeCallbacksAndMessages(null);

            recognizer.stopListening();

            recognizer.destroy();

        } catch (Exception ignored) {}

    }

    // ─────────────────────────────────────────────────────────────

    private void handleSpeech(ArrayList<String> alternatives) {

        Log.d(TAG, "state=" + state + " alternatives=" + alternatives);

        // Check cancel across all alternatives first

        for (String alt : alternatives) {

            String s = alt.toLowerCase().trim();

            if (containsAny(s, "cancel", "back", "annuler", "retour")) {

                speak(context.getString(R.string.vem_cancelled));

                state = State.IDLE;

                if (callbacks != null) callbacks.onCancelled();

                return;

            }

        }

        // Try each alternative

        for (String alt : alternatives) {

            if (tryHandle(alt)) return;

        }

        repromptCurrentState();

    }

    private boolean tryHandle(String raw) {

        String s = raw.toLowerCase().trim();

        switch (state) {

            case ASK_ACTION: {

                if (containsAny(s, "rename", "change name", "renommer", "changer le nom")) {

                    state = State.RENAME_ASK_NAME;

                    speakAndListen(context.getString(R.string.vem_ask_new_name));

                    return true;

                }

                if (containsAny(s, "add reminder", "ajouter un rappel", "ajouter rappel")) {

                    state = State.IDLE;

                    if (callbacks != null) callbacks.onActionAddReminder(currentItemName);

                    return true;

                }

                if (containsAny(s, "edit reminder", "modify reminder", "modifier rappel",

                        "modifier le rappel", "éditer rappel")) {

                    state = State.IDLE;

                    if (callbacks != null) callbacks.onActionEditReminder(currentItemName);

                    return true;

                }

                if (containsAny(s, "delete reminder", "remove reminder", "supprimer rappel",

                        "supprimer le rappel", "retirer rappel")) {

                    state = State.IDLE;

                    if (callbacks != null) callbacks.onActionDeleteReminder(currentItemName);

                    return true;

                }

                if (containsAny(s, "delete", "remove", "supprimer", "effacer")) {

                    state = State.DELETE_CONFIRM;

                    speakAndListen(context.getString(R.string.vem_confirm_delete, currentItemName));

                    return true;

                }

                return false;

            }

            case RENAME_ASK_NAME: {

                String newName = raw.trim();

                if (newName.isEmpty()) return false;

                pendingNewName = newName;

                state = State.RENAME_CONFIRM;

                speakAndListen(context.getString(R.string.vem_confirm_new_name, newName));

                return true;

            }

            case RENAME_CONFIRM: {

                if (isYes(s)) {

                    state = State.IDLE;

                    if (callbacks != null) callbacks.onActionRename(pendingNewName);

                    return true;

                }

                if (isNo(s)) {

                    state = State.RENAME_ASK_NAME;

                    speakAndListen(context.getString(R.string.vem_ask_new_name));

                    return true;

                }

                return false;

            }

            case DELETE_CONFIRM: {

                if (isYes(s)) {

                    state = State.IDLE;

                    if (callbacks != null) callbacks.onActionDelete(currentItemName);

                    return true;

                }

                if (isNo(s)) {

                    state = State.ASK_ACTION;

                    speakAndListen(buildActionPrompt());

                    return true;

                }

                return false;

            }

        }

        return false;

    }

    // ─────────────────────────────────────────────────────────────

    private String buildActionPrompt() {

        if (hasReminder) {

            return context.getString(R.string.vem_ask_action_with_reminder);

        } else {

            return context.getString(R.string.vem_ask_action_no_reminder);

        }

    }

    private boolean containsAny(String s, String... needles) {

        for (String n : needles) if (s.contains(n)) return true;

        return false;

    }

    private boolean isYes(String s) {

        return s.matches(".*\\b(yes|yeah|yup|yep|sure|okay|ok|correct|right|oui|ouais|d'accord)\\b.*");

    }

    private boolean isNo(String s) {

        return s.matches(".*\\b(no|nope|nah|non|incorrect|wrong)\\b.*");

    }

    // ─────────────────────────────────────────────────────────────

    // TTS / STT plumbing

    // ─────────────────────────────────────────────────────────────

    private void speak(String msg) {

        if (tts == null) return;

        tts.stop();

        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "vem_speak");

    }

    private void speakAndThen(String msg, Runnable action) {

        if (tts == null) return;

        tts.stop();

        String utterId = "vem_then_" + System.currentTimeMillis();

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {

            @Override public void onStart(String id) {}

            @Override public void onDone(String id) {

                if (utterId.equals(id) && !isShutdown) mainHandler.post(action);

            }

            @Override public void onError(String id) {}

        });

        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, utterId);

    }

    private void speakAndListen(String msg) {

        speakAndThen(msg, () -> mainHandler.postDelayed(this::startListening, 300));

    }

    private void startListening() {

        if (isShutdown || isListening) return;

        try {

            isListening = true;

            recognizer.startListening(recognizerIntent);

        } catch (Exception e) {

            isListening = false;

            Log.e(TAG, "startListening failed: " + e.getMessage());

            mainHandler.postDelayed(this::startListening, 600);

        }

    }

    private void repromptCurrentState() {

        String msg;

        switch (state) {

            case ASK_ACTION:

                msg = buildActionPrompt();

                break;

            case RENAME_ASK_NAME:

                msg = context.getString(R.string.vem_ask_new_name);

                break;

            case RENAME_CONFIRM:

                msg = context.getString(R.string.vem_confirm_new_name, pendingNewName);

                break;

            case DELETE_CONFIRM:

                msg = context.getString(R.string.vem_confirm_delete, currentItemName);

                break;

            default:

                msg = context.getString(R.string.didnt_catch);

        }

        speakAndListen(msg);

    }

}
