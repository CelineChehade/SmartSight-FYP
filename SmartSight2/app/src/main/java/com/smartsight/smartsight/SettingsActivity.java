package com.example.smartsight;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.content.pm.PackageManager;

public class SettingsActivity extends BaseActivity implements TextToSpeech.OnInitListener {

    private static final String TAG               = "SettingsActivity";
    private static final int    MAX_RETRIES       = 3;
    private static final int    AUDIO_PERMISSION_CODE = 400;

    private TextToSpeech  tts;
    private UserViewModel userViewModel;
    private AppRepository repository;
    private TextView      txtStatus;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private enum State {
        IDLE, CHANGE_NAME_ASK, CHANGE_NAME_CONFIRM,
        LANGUAGE_ASK, VOICE_SPEED_DEMO, RESET_CONFIRM
    }

    private State  state      = State.IDLE;
    private String pendingName;
    private float  tempSpeed;
    private int    retryCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Silence TalkBack speech
        TalkBackSilencer.silence(this, null);

        txtStatus     = findViewById(R.id.txtStatus);
        tts           = new TextToSpeech(this, this);
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        repository    = new AppRepository(this);

        findViewById(R.id.btnChangeName).setOnClickListener(v -> startChangeName());
        findViewById(R.id.btnLanguage).setOnClickListener(v -> startLanguage());
        findViewById(R.id.btnVoiceSpeed).setOnClickListener(v -> startVoiceSpeed());
        findViewById(R.id.btnResetData).setOnClickListener(v -> startResetData());

        SharedSpeechRecognizer.init(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    AUDIO_PERMISSION_CODE);
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            TtsHelper.applySettings(this, tts);
            // Re-apply silencer with tts ready, add focus speech to buttons
            TalkBackSilencer.silence(this, tts);
            TalkBackSilencer.addFocusSpeech(
                    findViewById(R.id.btnChangeName),
                    getString(R.string.btn_change_name), tts);
            TalkBackSilencer.addFocusSpeech(
                    findViewById(R.id.btnLanguage),
                    getString(R.string.btn_language), tts);
            TalkBackSilencer.addFocusSpeech(
                    findViewById(R.id.btnVoiceSpeed),
                    getString(R.string.btn_voice_speed), tts);
            TalkBackSilencer.addFocusSpeech(
                    findViewById(R.id.btnResetData),
                    getString(R.string.btn_reset_data), tts);
            // Speak greeting and immediately start listening for voice commands
            speakThenListen(getString(R.string.settings_greeting));
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
    }

    // ── STT ──────────────────────────────────────────────────────────────────

    private void speakThenListen(String msg) {
        retryCount = 0;
        TtsHelper.speakThen(tts, msg, this::startListeningOnce);
    }

    private void startListeningOnce() {
        SharedSpeechRecognizer.get().listen(this, result -> {
            if (result.success) {
                retryCount = 0;
                handleSpeechResult(result.text);
            } else {
                handleListenError(result.errorCode);
            }
        });
    }

    private void handleListenError(int errorCode) {
        boolean isSoft = errorCode == SpeechRecognizer.ERROR_NO_MATCH
                || errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || errorCode == SpeechRecognizer.ERROR_CLIENT;

        if (isSoft && retryCount < MAX_RETRIES) {
            retryCount++;
            Log.d(TAG, "Soft STT error, silent retry #" + retryCount);
            startListeningOnce();
        } else {
            retryCount = 0;
            TtsHelper.speakThen(tts, getString(R.string.didnt_catch),
                    this::startListeningOnce);
        }
    }

    // ── Speech result ─────────────────────────────────────────────────────────

    private void handleSpeechResult(String raw) {
        String spoken = raw.toLowerCase().trim();
        Log.d(TAG, "State=" + state + " Heard=\"" + spoken + "\"");

        if (state != State.IDLE && containsAny(spoken,
                "cancel", "back", "menu", "annuler", "retour")) {
            returnToMenu(getString(R.string.vem_cancelled));
            return;
        }

        switch (state) {

            case IDLE:
                if (containsAny(spoken, "name", "nom", "changer", "prénom", "prenom"))
                    startChangeName();
                else if (containsAny(spoken, "language", "langue"))
                    startLanguage();
                else if (containsAny(spoken, "speed", "vitesse"))
                    startVoiceSpeed();
                else if (containsAny(spoken, "reset", "réinitialiser",
                        "reinitialiser", "delete", "effacer", "wipe"))
                    startResetData();
                else
                    speakThenListen(getString(R.string.settings_please_say_commands));
                break;

            case CHANGE_NAME_ASK:
                pendingName = raw.trim();
                if (pendingName.isEmpty()) {
                    speakThenListen(getString(R.string.didnt_catch));
                    return;
                }
                state = State.CHANGE_NAME_CONFIRM;
                speakThenListen(getString(R.string.settings_confirm_new_name, pendingName));
                break;

            case CHANGE_NAME_CONFIRM:
                if (TtsHelper.isYes(spoken)) {
                    UserProfile u = new UserProfile();
                    u.id       = 1;
                    u.fullName = pendingName;
                    u.language = SettingsPrefs.getLanguage(this);
                    userViewModel.insertUser(u);
                    returnToMenu(getString(R.string.settings_name_updated, pendingName));
                } else if (TtsHelper.isNo(spoken)) {
                    state = State.CHANGE_NAME_ASK;
                    speakThenListen(getString(R.string.settings_retry_name));
                } else {
                    speakThenListen(getString(R.string.please_say_yes_no));
                }
                break;

            case LANGUAGE_ASK:
                String langCode = null;
                String langLabelCurrentLang = null;

                if (containsAny(spoken, "english", "anglais")) {
                    langCode             = "en";
                    langLabelCurrentLang = getString(R.string.lang_english);
                } else if (containsAny(spoken, "french", "français", "francais")) {
                    langCode             = "fr";
                    langLabelCurrentLang = getString(R.string.lang_french);
                }

                if (langCode != null) {
                    final String finalLangCode = langCode;
                    // Speak confirmation in CURRENT language/voice BEFORE switching
                    final String confirmMsg = getString(
                            R.string.settings_language_set, langLabelCurrentLang);
                    TtsHelper.speakThen(tts, confirmMsg, () -> {
                        SettingsPrefs.setLanguage(SettingsActivity.this, finalLangCode);
                        restartAppToMain();
                    });
                } else {
                    speakThenListen(getString(R.string.settings_please_say_language));
                }
                break;

            case VOICE_SPEED_DEMO:
                if (containsAny(spoken, "good", "ok", "okay", "fine", "done",
                        "perfect", "keep", "bien", "d'accord", "bon")) {
                    SettingsPrefs.setVoiceSpeed(this, tempSpeed);
                    TtsHelper.applySettings(this, tts);
                    returnToMenu(getString(R.string.speed_saved));
                } else if (containsAny(spoken, "faster", "fast", "quick",
                        "plus vite", "vite", "rapide")) {
                    tempSpeed = Math.min(SettingsPrefs.MAX_SPEED,
                            tempSpeed + SettingsPrefs.SPEED_STEP);
                    playSpeedDemo();
                } else if (containsAny(spoken, "slower", "slow",
                        "plus lentement", "lent", "lentement")) {
                    tempSpeed = Math.max(SettingsPrefs.MIN_SPEED,
                            tempSpeed - SettingsPrefs.SPEED_STEP);
                    playSpeedDemo();
                } else {
                    speakThenListen(getString(R.string.please_say_speed));
                }
                break;

            case RESET_CONFIRM:
                if (TtsHelper.isYes(spoken)) {
                    repository.wipeAllData(this);
                    SettingsPrefs.clearAll(this);
                    TtsHelper.speakThen(tts, getString(R.string.reset_done),
                            this::restartAppToMain);
                } else if (TtsHelper.isNo(spoken)) {
                    returnToMenu(getString(R.string.reset_cancelled));
                } else {
                    speakThenListen(getString(R.string.please_say_yes_no));
                }
                break;
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void startChangeName() {
        state = State.CHANGE_NAME_ASK;
        setStatus(getString(R.string.btn_change_name));
        speakThenListen(getString(R.string.settings_ask_new_name));
    }

    private void startLanguage() {
        state = State.LANGUAGE_ASK;
        setStatus(getString(R.string.btn_language));
        speakThenListen(getString(R.string.settings_ask_language));
    }

    private void startVoiceSpeed() {
        state     = State.VOICE_SPEED_DEMO;
        tempSpeed = SettingsPrefs.getVoiceSpeed(this);
        setStatus(getString(R.string.btn_voice_speed));
        playSpeedDemo();
    }

    private void startResetData() {
        state = State.RESET_CONFIRM;
        setStatus(getString(R.string.btn_reset_data));
        speakThenListen(getString(R.string.reset_confirm));
    }

    private void playSpeedDemo() {
        tts.setSpeechRate(tempSpeed);
        TtsHelper.speakThen(tts, getString(R.string.speed_test_phrase),
                () -> speakThenListen(getString(R.string.speed_prompt)));
    }

    private void returnToMenu(String message) {
        state = State.IDLE;
        setStatus(getString(R.string.settings_status_default));
        speakThenListen(getString(R.string.settings_return_to_menu, message));
    }

    private void restartAppToMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void setStatus(String s) {
        if (txtStatus != null) txtStatus.setText(s);
    }

    private boolean containsAny(String s, String... words) {
        for (String w : words) if (s.contains(w.toLowerCase())) return true;
        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedSpeechRecognizer.get().cancel();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
