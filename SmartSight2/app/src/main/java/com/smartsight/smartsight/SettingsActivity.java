package com.example.smartsight;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;

public class SettingsActivity extends AppCompatActivity
        implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;

    private UserViewModel userViewModel;
    private AppRepository repository;

    private TextView txtStatus;

    private static final int AUDIO_PERMISSION_CODE = 400;

    private enum State {
        IDLE,
        CHANGE_NAME_ASK,
        CHANGE_NAME_CONFIRM,
        LANGUAGE_ASK,
        VOICE_SPEED_DEMO,
        HIGH_CONTRAST_ASK,
        RESET_CONFIRM
    }

    private State state = State.IDLE;
    private String pendingName;
    private float tempSpeed;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (SettingsPrefs.isHighContrast(this)) {
            setTheme(android.R.style.Theme_Black_NoTitleBar);
        }

        setContentView(R.layout.activity_settings);

        txtStatus = findViewById(R.id.txtStatus);

        tts = new TextToSpeech(this, this);
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        repository = new AppRepository(this);

        findViewById(R.id.btnChangeName).setOnClickListener(v -> startChangeName());
        findViewById(R.id.btnLanguage).setOnClickListener(v -> startLanguage());
        findViewById(R.id.btnVoiceSpeed).setOnClickListener(v -> startVoiceSpeed());
        findViewById(R.id.btnHighContrast).setOnClickListener(v -> startHighContrast());
        findViewById(R.id.btnResetData).setOnClickListener(v -> startResetData());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_CODE);
        } else {
            initSpeechRecognizer();
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            TtsHelper.applySettings(this, tts);

            // Speak greeting and then list buttons with pauses
            String[] prompts = {
                    getString(R.string.settings_status_default),
                    getString(R.string.btn_change_name),
                    getString(R.string.btn_language),
                    getString(R.string.btn_voice_speed),
                    getString(R.string.btn_high_contrast),
                    getString(R.string.btn_reset_data)
            };
            TtsHelper.speakWithDelay(tts, prompts, 800, this::startListening);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initSpeechRecognizer();
        }
    }

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                LocaleManager.getSttLanguageTag(this));

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onRmsChanged(float rmsdB) {}

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    handleSpeechResult(matches.get(0));
                } else {
                    speakAndThen(getString(R.string.didnt_catch),
                            SettingsActivity.this::startListening);
                }
            }

            @Override
            public void onError(int error) {
                speakAndThen(getString(R.string.didnt_catch),
                        SettingsActivity.this::startListening);
            }
        });
    }

    private void startListening() {
        if (speechRecognizer != null && recognizerIntent != null) {
            speechRecognizer.startListening(recognizerIntent);
        }
    }

    private void handleSpeechResult(String raw) {
        String spoken = raw.toLowerCase().trim();
        Log.d("Settings", "state=" + state + " heard=" + spoken);

        if (state != State.IDLE && (spoken.contains("cancel") || spoken.contains("back")
                || spoken.contains("menu") || spoken.contains("annuler")
                || spoken.contains("retour"))) {
            returnToMenu(getString(R.string.vem_cancelled));
            return;
        }

        switch (state) {
            case IDLE:
                if (containsAny(spoken, "name", "nom")) startChangeName();
                else if (containsAny(spoken, "language", "langue")) startLanguage();
                else if (containsAny(spoken, "speed", "vitesse")) startVoiceSpeed();
                else if (containsAny(spoken, "contrast", "contraste")) startHighContrast();
                else if (containsAny(spoken, "reset", "réinitialiser")) startResetData();
                else {
                    speakAndThen(getString(R.string.settings_please_say_commands),
                            this::startListening);
                }
                break;

            case CHANGE_NAME_ASK:
                pendingName = raw.trim();
                if (pendingName.isEmpty()) {
                    speakAndThen(getString(R.string.didnt_catch), this::startListening);
                    return;
                }
                state = State.CHANGE_NAME_CONFIRM;
                speakAndThen(getString(R.string.settings_confirm_new_name, pendingName),
                        this::startListening);
                break;

            case CHANGE_NAME_CONFIRM:
                if (isYes(spoken)) {
                    UserProfile u = new UserProfile();
                    u.id = 1;
                    u.fullName = pendingName;
                    u.language = SettingsPrefs.getLanguage(this);
                    userViewModel.insertUser(u);
                    returnToMenu(getString(R.string.settings_name_updated, pendingName));
                } else if (isNo(spoken)) {
                    state = State.CHANGE_NAME_ASK;
                    speakAndThen(getString(R.string.settings_retry_name),
                            this::startListening);
                } else {
                    speakAndThen(getString(R.string.please_say_yes_no), this::startListening);
                }
                break;

            case LANGUAGE_ASK:
                String langCode = null;
                String langName = null;
                if (containsAny(spoken, "english", "anglais")) {
                    langCode = "en"; langName = getString(R.string.lang_english);
                } else if (containsAny(spoken, "french", "français", "francais")) {
                    langCode = "fr"; langName = getString(R.string.lang_french);
                }

                if (langCode != null) {
                    SettingsPrefs.setLanguage(this, langCode);
                    final String finalLangName = langName;
                    speakAndThen(
                            getString(R.string.settings_language_set, finalLangName),
                            this::restartAppToMain);
                } else {
                    speakAndThen(getString(R.string.settings_please_say_language),
                            this::startListening);
                }
                break;

            case VOICE_SPEED_DEMO:
                if (containsAny(spoken, "good", "ok", "okay", "fine", "done", "perfect", "keep",
                        "bien", "d'accord", "bon")) {
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
                    speakAndThen(getString(R.string.please_say_speed),
                            this::startListening);
                }
                break;

            case HIGH_CONTRAST_ASK:
                if (containsAny(spoken, "enable", "on", "turn on",
                        "activer", "active")) {
                    SettingsPrefs.setHighContrast(this, true);
                    speakAndThen(getString(R.string.contrast_now_enabled), this::recreate);
                } else if (containsAny(spoken, "disable", "off", "turn off",
                        "désactiver", "désactive")) {
                    SettingsPrefs.setHighContrast(this, false);
                    speakAndThen(getString(R.string.contrast_now_disabled), this::recreate);
                } else {
                    speakAndThen(getString(R.string.contrast_please_say),
                            this::startListening);
                }
                break;

            case RESET_CONFIRM:
                if (isYes(spoken)) {
                    repository.wipeAllData(this);
                    SettingsPrefs.clearAll(this);
                    speakAndThen(getString(R.string.reset_done), this::restartAppToMain);
                } else if (isNo(spoken)) {
                    returnToMenu(getString(R.string.reset_cancelled));
                } else {
                    speakAndThen(getString(R.string.please_say_yes_no),
                            this::startListening);
                }
                break;
        }
    }

    private void startChangeName() {
        state = State.CHANGE_NAME_ASK;
        setStatus(getString(R.string.btn_change_name));
        speakAndThen(getString(R.string.settings_ask_new_name), this::startListening);
    }

    private void startLanguage() {
        state = State.LANGUAGE_ASK;
        setStatus(getString(R.string.btn_language));
        speakAndThen(getString(R.string.settings_ask_language), this::startListening);
    }

    private void startVoiceSpeed() {
        state = State.VOICE_SPEED_DEMO;
        setStatus(getString(R.string.btn_voice_speed));
        tempSpeed = SettingsPrefs.getVoiceSpeed(this);
        playSpeedDemo();
    }

    private void playSpeedDemo() {
        tts.setSpeechRate(tempSpeed);
        String msg = getString(R.string.speed_test_phrase) + " " +
                getString(R.string.speed_prompt);
        speakAndThen(msg, this::startListening);
    }

    private void startHighContrast() {
        state = State.HIGH_CONTRAST_ASK;
        setStatus(getString(R.string.btn_high_contrast));
        boolean current = SettingsPrefs.isHighContrast(this);
        String stateStr = current
                ? getString(R.string.contrast_state_enabled)
                : getString(R.string.contrast_state_disabled);
        speakAndThen(getString(R.string.contrast_ask, stateStr), this::startListening);
    }

    private void startResetData() {
        state = State.RESET_CONFIRM;
        setStatus(getString(R.string.btn_reset_data));
        speakAndThen(getString(R.string.reset_confirm), this::startListening);
    }

    private void returnToMenu(String message) {
        state = State.IDLE;
        setStatus(getString(R.string.settings_status_default));
        speakAndThen(getString(R.string.settings_return_to_menu, message),
                this::startListening);
    }

    private void restartAppToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setStatus(String s) {
        if (txtStatus != null) txtStatus.setText(s);
    }

    private boolean containsAny(String s, String... words) {
        for (String w : words) if (s.contains(w.toLowerCase())) return true;
        return false;
    }

    private boolean isYes(String s) {
        return s.matches(".*\\b(yes|yeah|yup|yep|sure|okay|ok|correct|right|confirm|oui|ouais|d'accord)\\b.*");
    }

    private boolean isNo(String s) {
        return s.matches(".*\\b(no|nope|nah|cancel|stop|wrong|incorrect|non|annuler)\\b.*");
    }

    private void speakAndThen(String msg, Runnable action) {
        if (tts == null) return;
        tts.stop();   // kill any in-flight utterance first — prevents overlap
        String utterId = "utt_" + System.currentTimeMillis();

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onDone(String utteranceId) {
                if (utterId.equals(utteranceId)) runOnUiThread(action);
            }
            @Override public void onError(String utteranceId) {}
        });

        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, utterId);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) speechRecognizer.destroy();
        super.onDestroy();
    }
}
