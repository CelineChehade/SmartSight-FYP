package com.smartsight.smartsight;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    // ---------------------------------------------------
    // FIELDS
    // ---------------------------------------------------
    private TextToSpeech textToSpeech;
    private UserViewModel userViewModel;
    private TextView tvGreeting;

    private String pendingName;              // temporarily holds spoken name

    // New Activity-results API launchers
    private ActivityResultLauncher<Intent> nameLauncher;
    private ActivityResultLauncher<Intent> confirmLauncher;

    // ---------------------------------------------------
    // LIFECYCLE
    // ---------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvGreeting    = findViewById(R.id.tvGreeting);
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        textToSpeech  = new TextToSpeech(this, this);

        // ---------- Register launchers ----------
        nameLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                    ArrayList<String> list = result.getData()
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (list != null && !list.isEmpty()) handleNameResult(list);
                });

        confirmLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                    ArrayList<String> list = result.getData()
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (list != null && !list.isEmpty()) handleConfirmResult(list);
                });
    }

    // ---------------------------------------------------
    // TTS READY
    // ---------------------------------------------------
    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;

        textToSpeech.setLanguage(Locale.ENGLISH);

        // One-shot observe the user profile
        userViewModel.getUser().observe(this, user -> {
            if (user == null) {
                askForUserName();
            } else {
                greetUser(user.fullName);
            }
            userViewModel.getUser().removeObservers(this);
        });
    }

    // ---------------------------------------------------
    // GREETING
    // ---------------------------------------------------
    private void greetUser(String name) {
        tvGreeting.setText("Welcome, " + name);

        textToSpeech.speak("Welcome, " + name,
                TextToSpeech.QUEUE_FLUSH, null, null);

        new Handler().postDelayed(this::checkTalkBackAndGuide, 1500);
    }

    // ---------------------------------------------------
    // NAME CAPTURE
    // ---------------------------------------------------
    private void askForUserName() {
        textToSpeech.speak("Please say your full name",
                TextToSpeech.QUEUE_FLUSH, null, null);

        new Handler().postDelayed(this::launchNameRecognition, 2000);
    }

    private void launchNameRecognition() {
        Intent i = buildSpeechIntent("Say your full name");
        nameLauncher.launch(i);
    }

    private void launchConfirmRecognition() {
        Intent i = buildSpeechIntent("Say yes or no");
        confirmLauncher.launch(i);
    }

    private Intent buildSpeechIntent(String prompt) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, prompt);

        // Helpful extras
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000);
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000);

        return intent;
    }

    // ---------------------------------------------------
    // RESULT HANDLERS
    // ---------------------------------------------------
    private void handleNameResult(List<String> results) {
        pendingName = results.get(0);

        textToSpeech.speak("I heard " + pendingName +
                        ". Is this correct? Say yes or no.",
                TextToSpeech.QUEUE_FLUSH, null, null);

        new Handler().postDelayed(this::launchConfirmRecognition, 2500);
    }

    private void handleConfirmResult(List<String> results) {
        boolean positive = isPositive(results);
        boolean negative = isNegative(results);

        if (positive && !negative) {          // YES
            UserProfile user = new UserProfile();
            user.id       = 1;
            user.fullName = pendingName;
            user.language = "en";
            userViewModel.insertUser(user);
            greetUser(pendingName);

        } else if (negative && !positive) {   // NO
            textToSpeech.speak("Okay, let's try again. Please say your full name.",
                    TextToSpeech.QUEUE_FLUSH, null, null);
            new Handler().postDelayed(this::askForUserName, 2000);

        } else {                              // ambiguous
            textToSpeech.speak("I didn't catch that clearly. Please say yes or no.",
                    TextToSpeech.QUEUE_FLUSH, null, null);
            new Handler().postDelayed(this::launchConfirmRecognition, 2000);
        }
    }

    // ---------------------------------------------------
    // YES / NO DETECTION
    // ---------------------------------------------------
    private boolean isPositive(List<String> results) {
        for (String hyp : results) {
            String lower = hyp.trim().toLowerCase(Locale.US);
            if (lower.matches("^(y(es)?|yeah|yup|yep|sure|correct|right|affirmative)\\b.*"))
                return true;
        }
        return false;
    }

    private boolean isNegative(List<String> results) {
        for (String hyp : results) {
            String lower = hyp.trim().toLowerCase(Locale.US);
            if (lower.matches("^(n(o|ope|ah)?|now|know|negative|incorrect|wrong)\\b.*"))
                return true;
        }
        return false;
    }

    // ---------------------------------------------------
    // TALKBACK CHECK
    // ---------------------------------------------------
    private boolean isTalkBackEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return !TextUtils.isEmpty(enabled) &&
                enabled.toLowerCase().contains("talkback");
    }

    private void checkTalkBackAndGuide() {
        if (isTalkBackEnabled()) return;

        textToSpeech.speak(
                "Accessibility services appear to be disabled. Please enable TalkBack for full voice navigation.",
                TextToSpeech.QUEUE_FLUSH, null, null);

        new Handler().postDelayed(() -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        }, 2500);
    }

    // ---------------------------------------------------
    // CLEAN-UP
    // ---------------------------------------------------
    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}