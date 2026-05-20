package com.example.smartsight;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;

public class MainActivity extends BaseActivity implements TextToSpeech.OnInitListener {

    private TextToSpeech     textToSpeech;
    private UserViewModel    userViewModel;
    private String           pendingName;
    private SpeechRecognizer speechRecognizer;
    private Intent           recognizerIntent;
    private boolean          expectingName  = false;
    private boolean          expectingYesNo = false;

    private TextView tvGreeting;
    private Button   btnScan, btnSaved, btnSettings;

    private static final int REQ_AUDIO_PERM        = 200;
    private static final int REQ_NOTIFICATION_PERM = 201;

    private static final String PREFS_NAME         = "app_prefs";
    private static final String KEY_TALKBACK_ASKED = "talkback_asked";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (SettingsPrefs.isHighContrast(this)) {
            setTheme(android.R.style.Theme_Black_NoTitleBar);
        }

        setContentView(R.layout.activity_main);
        TalkBackSilencer.silence(this, null);

        tvGreeting  = findViewById(R.id.tvGreeting);
        btnScan     = findViewById(R.id.btnSmartScan);
        btnSaved    = findViewById(R.id.btnSavedItems);
        btnSettings = findViewById(R.id.btnSettings);
        setButtonsVisible(false);

        textToSpeech  = new TextToSpeech(this, this);
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            TtsHelper.applySettings(this, textToSpeech);
            NotificationHelper.createChannel(this);
            requestNotificationPermission();
        }
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIFICATION_PERM);
                return;
            }
        }
        requestMicPermission();
    }

    private void requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            initSpeechRecognizer();
            checkUser();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO_PERM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATION_PERM) {
            requestMicPermission();
            return;
        }
        if (requestCode == REQ_AUDIO_PERM) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initSpeechRecognizer();
                checkUser();
            } else {
                speak(getString(R.string.mic_permission_required));
            }
        }
    }

    private void checkUser() {
        userViewModel.getUserProfile().observe(this, user -> {
            if (user != null) {
                greetUser(user.fullName);
            } else {
                askForUserName();
            }
        });
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

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    handleSpeechResult(matches.get(0));
                } else {
                    speakAndThen(getString(R.string.didnt_catch),
                            MainActivity.this::startListening);
                }
            }

            @Override
            public void onError(int error) {
                speakAndThen(getString(R.string.didnt_catch),
                        MainActivity.this::startListening);
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onRmsChanged(float rmsdB) {}
        });
    }

    private void askForUserName() {
        expectingName  = true;
        expectingYesNo = false;
        speakAndThen(getString(R.string.ask_name), this::startListening);
    }

    private void startListening() {
        if (speechRecognizer != null) {
            speechRecognizer.startListening(recognizerIntent);
        }
    }

    private void handleSpeechResult(String spokenText) {
        if (expectingName) {
            pendingName    = spokenText;
            expectingName  = false;
            expectingYesNo = true;
            speakAndThen(getString(R.string.confirm_name, pendingName),
                    this::startListening);
        } else if (expectingYesNo) {
            if (isYes(spokenText)) {
                UserProfile u = new UserProfile();
                u.id       = 1;
                u.fullName = pendingName;
                u.language = SettingsPrefs.getLanguage(this);
                userViewModel.insertUser(u);
                greetUser(pendingName);
            } else if (isNo(spokenText)) {
                speakAndThen(getString(R.string.try_again), this::askForUserName);
            } else {
                speakAndThen(getString(R.string.please_answer_yes_no),
                        this::startListening);
            }
        }
    }

    private boolean isYes(String s) {
        return s.toLowerCase().matches(
                ".*\\b(yes|yeah|yup|yep|sure|correct|right|affirmative|oui|ouais)\\b.*");
    }

    private boolean isNo(String s) {
        return s.toLowerCase().matches(
                ".*\\b(no+|nope|nah|non|negative|incorrect|wrong|not)\\b.*");
    }

    private void speak(String msg) {
        TtsHelper.speak(textToSpeech, msg);
    }

    private void speakAndThen(String msg, Runnable action) {
        TtsHelper.speakThen(textToSpeech, msg, action);
    }

    private void greetUser(String name) {
        String msg = getString(R.string.welcome_user, name);
        tvGreeting.setText(msg);

        // Show buttons immediately
        setButtonsVisible(true);

        TalkBackSilencer.silence(this, textToSpeech);
        TalkBackSilencer.addFocusSpeech(btnScan,
                getString(R.string.smart_scan_button), textToSpeech, this);
        TalkBackSilencer.addFocusSpeech(btnSaved,
                getString(R.string.saved_items_button), textToSpeech, this);
        TalkBackSilencer.addFocusSpeech(btnSettings,
                getString(R.string.settings_button), textToSpeech, this);

        attachButtonLogic();

        boolean talkBackOn   = isTalkBackEnabled();
        boolean alreadyAsked = hasAskedForTalkBack();

        if (!"fr".equals(SettingsPrefs.getLanguage(this)) && talkBackOn) {
            // English + TalkBack: buttons are visible; TalkBack announces them naturally.
            // App TTS QUEUE_FLUSH would cut off TalkBack's window announcement.
            return;
        }

        TtsHelper.speakThen(textToSpeech, msg, () -> {
            if (!talkBackOn && !alreadyAsked) {
                setTalkBackAsked();
                speakAndThen(getString(R.string.talkback_not_enabled), () -> {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    speak(getString(R.string.talkback_settings_opened));
                });
            } else {
                TalkBackSilencer.silence(MainActivity.this, textToSpeech);
                TalkBackSilencer.addFocusSpeech(btnScan,
                        getString(R.string.smart_scan_button), textToSpeech, MainActivity.this);
                TalkBackSilencer.addFocusSpeech(btnSaved,
                        getString(R.string.saved_items_button), textToSpeech, MainActivity.this);
                TalkBackSilencer.addFocusSpeech(btnSettings,
                        getString(R.string.settings_button), textToSpeech, MainActivity.this);
                speak(getString(R.string.main_menu_ready));
            }
        });
    }

    private void setButtonsVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        btnScan.setVisibility(v);
        btnSaved.setVisibility(v);
        btnSettings.setVisibility(v);
    }

    private void attachButtonLogic() {
        btnScan.setOnClickListener(v ->
                startActivity(new Intent(this, SmartScanActivity.class)));
        btnSaved.setOnClickListener(v ->
                startActivity(new Intent(this, SavedItemsActivity.class)));
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private boolean isTalkBackEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return !TextUtils.isEmpty(enabled)
                && enabled.toLowerCase().contains("talkback");
    }

    private boolean hasAskedForTalkBack() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(KEY_TALKBACK_ASKED, false);
    }

    private void setTalkBackAsked() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_TALKBACK_ASKED, true).apply();
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
