package com.example.smartsight;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private TextToSpeech textToSpeech;
    private UserViewModel userViewModel;
    private String pendingName;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean expectingName = false;
    private boolean expectingYesNo = false;

    private TextView tvGreeting;
    private Button btnScan, btnSaved, btnSettings;
    private boolean talkBackPromptShown = false;

    private static final int REQ_AUDIO_PERM = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvGreeting  = findViewById(R.id.tvGreeting);
        btnScan     = findViewById(R.id.btnSmartScan);
        btnSaved    = findViewById(R.id.btnSavedItems);
        btnSettings = findViewById(R.id.btnSettings);
        setButtonsVisible(false);

        textToSpeech = new TextToSpeech(this, this);
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.setLanguage(Locale.getDefault());
            requestMicPermission();
        }
    }

    private void requestMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            initSpeechRecognizer();
            askForUserName();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO_PERM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO_PERM) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initSpeechRecognizer();
                askForUserName();
            } else {
                speak("Microphone permission is required.");
            }
        }
    }

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US"); // Force English US

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    handleSpeechResult(matches.get(0));
                } else {
                    speakAndThen("I didn’t catch that. Please try again.", MainActivity.this::startListening);
                }
            }
            @Override public void onError(int error) {
                speakAndThen("I didn’t catch that. Please try again.", MainActivity.this::startListening);
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
        expectingName = true;
        expectingYesNo = false;
        speakAndThen("Please say your full name.", this::startListening);
    }

    private void startListening() {
        if (speechRecognizer != null) {
            speechRecognizer.startListening(recognizerIntent);
        }
    }

    private void handleSpeechResult(String spokenText) {
        if (expectingName) {
            pendingName = spokenText; // ✅ no normalization
            expectingName = false;
            expectingYesNo = true;
            speakAndThen("You said " + pendingName + ". Is that correct?", this::startListening);

        } else if (expectingYesNo) {
            if (isYes(spokenText)) {
                UserProfile u = new UserProfile();
                u.id = 1;
                u.fullName = pendingName;
                u.language = Locale.getDefault().toString();
                userViewModel.insertUser(u);
                greetUser(pendingName);
            } else if (isNo(spokenText)) {
                speakAndThen("Let’s try again.", this::askForUserName);
            } else {
                speakAndThen("Please answer yes or no.", this::startListening);
            }
        }
    }

    private boolean isYes(String s) {
        return s.toLowerCase().matches(".*\\b(yes|yeah|yup|yep|sure|correct|right|affirmative|oui|ouais)\\b.*");
    }
    private boolean isNo(String s) {
        return s.toLowerCase().matches(".*\\b(no+|nope|nah|non|negative|incorrect|wrong|not|know)\\b.*");
    }

    private void speak(String msg) {
        if (textToSpeech != null) {
            textToSpeech.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void speakAndThen(String msg, Runnable action) {
        String utterId = "utt_" + System.currentTimeMillis();
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onDone(String utteranceId) {
                if (utterId.equals(utteranceId)) {
                    runOnUiThread(action);
                }
            }
            @Override public void onError(String utteranceId) {}
        });
        textToSpeech.speak(msg, TextToSpeech.QUEUE_FLUSH, null, utterId);
    }

    private void greetUser(String name) {
        String msg = "Welcome " + name;
        tvGreeting.setText(msg);

        String utterId = "greet_" + System.currentTimeMillis();
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) {
                if (utterId.equals(id)) {
                    runOnUiThread(() -> {
                        setButtonsVisible(true);
                        attachButtonLogic();

                        if (!isTalkBackEnabled() && !talkBackPromptShown) {
                            talkBackPromptShown = true;
                            speakAndThen("TalkBack is not enabled. Opening accessibility settings now.", () -> {
                                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                                startActivity(intent);
                                speak("Accessibility settings opened. Please enable TalkBack if needed.");
                            });
                        } else if (!isTalkBackEnabled()) {
                            speak("Main menu ready.");
                        }
                    });
                }
            }
            @Override public void onError(String id) {}
        });
        textToSpeech.speak(msg, TextToSpeech.QUEUE_FLUSH, null, utterId);
    }
    private void setButtonsVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        btnScan.setVisibility(v);
        btnSaved.setVisibility(v);
        btnSettings.setVisibility(v);
    }

    private void attachButtonLogic() {
        View.OnClickListener todo = v -> speak("Feature not implemented yet.");
        btnScan.setOnClickListener(todo);
        btnSaved.setOnClickListener(todo);
        btnSettings.setOnClickListener(todo);
    }

    private boolean isTalkBackEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return !TextUtils.isEmpty(enabled) && enabled.toLowerCase().contains("talkback");
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
