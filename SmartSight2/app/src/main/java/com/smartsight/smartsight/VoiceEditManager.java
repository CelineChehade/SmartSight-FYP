package com.example.smartsight;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.ArrayList;

/**
 * Voice-driven item editing (rename, delete, reminder actions).
 * Uses SharedSpeechRecognizer — no independent recognizer created here.
 */
public class VoiceEditManager {

    private static final String TAG        = "VoiceEditManager";
    private static final int    MAX_RETRIES = 3;

    public interface Callbacks {
        void onActionRename(String itemName);
        void onActionAddReminder(String itemName);
        void onActionEditReminder(String itemName);
        void onActionDeleteReminder(String itemName);
        void onActionDelete(String itemName);
        void onCancelled();
    }

    private enum State { IDLE, ASK_ACTION, RENAME_ASK_NAME, RENAME_CONFIRM, DELETE_CONFIRM }

    private final Context      context;
    private final TextToSpeech tts;
    private final Callbacks    callbacks;
    private final Handler      mainHandler = new Handler(Looper.getMainLooper());

    private State   state = State.IDLE;
    private String  currentItemName;
    private boolean hasReminder;
    private String  pendingNewName;
    private boolean isShutdown = false;
    private int     retryCount = 0;

    public VoiceEditManager(Context context, TextToSpeech tts, Callbacks callbacks) {
        this.context   = LocaleManager.wrap(context.getApplicationContext());
        this.tts       = tts;
        this.callbacks = callbacks;
    }

    public void buildItemDescription(String itemName, boolean hasReminder) {
        this.currentItemName = itemName;
        this.hasReminder     = hasReminder;
        state = State.ASK_ACTION;
        speakThenListen(itemName + ". " + buildActionPrompt());
    }

    public void shutdown() {
        isShutdown = true;
        mainHandler.removeCallbacksAndMessages(null);
        SharedSpeechRecognizer.get().cancel();
    }

    // ── STT ──────────────────────────────────────────────────────────────────

    private void speakThenListen(String msg) {
        if (isShutdown) return;
        retryCount = 0;
        // speakThen waits for TTS + silence buffer before calling startListeningOnce
        TtsHelper.speakThen(tts, msg, this::startListeningOnce);
    }

    private void startListeningOnce() {
        if (isShutdown) return;
        // listen() polls ttsActive internally before opening the mic
        SharedSpeechRecognizer.get().listen(context, result -> {
            if (isShutdown) return;
            if (result.success) {
                retryCount = 0;
                handleSpeech(result.allResults);
            } else {
                handleListenError(result.errorCode);
            }
        });
    }

    private void handleListenError(int errorCode) {
        if (isShutdown) return;
        boolean isSoft = errorCode == SpeechRecognizer.ERROR_NO_MATCH
                || errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || errorCode == SpeechRecognizer.ERROR_CLIENT;

        if (isSoft && retryCount < MAX_RETRIES) {
            retryCount++;
            Log.d(TAG, "Soft error, silent retry #" + retryCount);
            // Silent retry — don't speak anything, just try again
            startListeningOnce();
        } else {
            retryCount = 0;
            repromptCurrentState();
        }
    }

    // ── Speech handling ───────────────────────────────────────────────────────

    private void handleSpeech(ArrayList<String> alternatives) {
        Log.d(TAG, "state=" + state + " alternatives=" + alternatives);

        for (String alt : alternatives) {
            String s = alt.toLowerCase().trim();
            if (containsAny(s, "cancel", "back", "annuler", "retour")) {
                TtsHelper.speak(tts, context.getString(R.string.vem_cancelled));
                state = State.IDLE;
                if (callbacks != null) callbacks.onCancelled();
                return;
            }
        }
        for (String alt : alternatives) { if (tryHandle(alt)) return; }
        repromptCurrentState();
    }

    private boolean tryHandle(String raw) {
        String s = raw.toLowerCase().trim();
        switch (state) {

            case ASK_ACTION:
                if (containsAny(s, "rename", "change name", "renommer", "changer le nom")) {
                    state = State.RENAME_ASK_NAME;
                    speakThenListen(context.getString(R.string.vem_ask_new_name));
                    return true;
                }
                if (containsAny(s, "add reminder", "ajouter un rappel", "ajouter rappel")) {
                    state = State.IDLE;
                    if (callbacks != null) callbacks.onActionAddReminder(currentItemName);
                    return true;
                }
                if (containsAny(s, "edit reminder", "modify reminder",
                        "modifier rappel", "modifier le rappel", "éditer rappel")) {
                    state = State.IDLE;
                    if (callbacks != null) callbacks.onActionEditReminder(currentItemName);
                    return true;
                }
                if (containsAny(s, "delete reminder", "remove reminder",
                        "supprimer rappel", "supprimer le rappel", "retirer rappel")) {
                    state = State.IDLE;
                    if (callbacks != null) callbacks.onActionDeleteReminder(currentItemName);
                    return true;
                }
                if (containsAny(s, "delete", "remove", "supprimer", "effacer")) {
                    state = State.DELETE_CONFIRM;
                    speakThenListen(context.getString(
                            R.string.vem_confirm_delete, currentItemName));
                    return true;
                }
                return false;

            case RENAME_ASK_NAME:
                if (raw.trim().isEmpty()) return false;
                pendingNewName = raw.trim();
                state = State.RENAME_CONFIRM;
                speakThenListen(context.getString(
                        R.string.vem_confirm_new_name, pendingNewName));
                return true;

            case RENAME_CONFIRM:
                if (isYes(s)) {
                    state = State.IDLE;
                    if (callbacks != null) callbacks.onActionRename(pendingNewName);
                    return true;
                }
                if (isNo(s)) {
                    state = State.RENAME_ASK_NAME;
                    speakThenListen(context.getString(R.string.vem_ask_new_name));
                    return true;
                }
                return false;

            case DELETE_CONFIRM:
                if (isYes(s)) {
                    state = State.IDLE;
                    if (callbacks != null) callbacks.onActionDelete(currentItemName);
                    return true;
                }
                if (isNo(s)) {
                    state = State.ASK_ACTION;
                    speakThenListen(buildActionPrompt());
                    return true;
                }
                return false;
        }
        return false;
    }

    private void repromptCurrentState() {
        if (isShutdown) return;
        String msg;
        switch (state) {
            case ASK_ACTION:      msg = buildActionPrompt(); break;
            case RENAME_ASK_NAME: msg = context.getString(R.string.vem_ask_new_name); break;
            case RENAME_CONFIRM:  msg = context.getString(R.string.vem_confirm_new_name, pendingNewName); break;
            case DELETE_CONFIRM:  msg = context.getString(R.string.vem_confirm_delete, currentItemName); break;
            default:              msg = context.getString(R.string.didnt_catch);
        }
        speakThenListen(msg);
    }

    private String buildActionPrompt() {
        return hasReminder
                ? context.getString(R.string.vem_ask_action_with_reminder)
                : context.getString(R.string.vem_ask_action_no_reminder);
    }

    private boolean containsAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    private boolean isYes(String s) {
        return s.matches(
                ".*\\b(yes|yeah|yup|sure|okay|ok|correct|right|oui|ouais|d.accord)\\b.*");
    }

    private boolean isNo(String s) {
        return s.matches(
                ".*\\b(no|nope|nah|non|incorrect|wrong)\\b.*");
    }
}
