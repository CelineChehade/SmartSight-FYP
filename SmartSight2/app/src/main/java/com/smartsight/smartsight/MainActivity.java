package com.smartsight.smartsight;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    // ---------- Request codes ----------
    private static final int SPEECH_REQUEST_CODE  = 100;
    private static final int CONFIRM_REQUEST_CODE = 200;


    private TextToSpeech textToSpeech;


    private UserViewModel userViewModel;


    private TextView tvGreeting;


    private String pendingName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvGreeting = findViewById(R.id.tvGreeting);
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);

        textToSpeech = new TextToSpeech(this, this);
    }


    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) return;

        textToSpeech.setLanguage(Locale.ENGLISH);

        userViewModel.getUser().observe(this, user -> {
            if (user == null) {
                askForUserName();
            } else {
                greetUser(user.fullName);
            }
            userViewModel.getUser().removeObservers(this);
        });
    }

    // =====================================================
    //  Greeting
    // =====================================================

    private void greetUser(String name) {
        tvGreeting.setText("Welcome, " + name);

        textToSpeech.speak("Welcome, " + name,
                TextToSpeech.QUEUE_FLUSH, null, null);

        new Handler().postDelayed(this::checkTalkBackAndGuide, 1500);
    }

    // =====================================================
    //  First-time name capture
    // =====================================================

    private void askForUserName() {
        textToSpeech.speak("Please say your full name",
                TextToSpeech.QUEUE_FLUSH, null, null);

        new Handler().postDelayed(() -> {
            Intent intent = buildSpeechIntent("Say your full name");
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        }, 2000);
    }

    private Intent buildSpeechIntent(String prompt) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, prompt);

        // Optional tweaks
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000);
        intent.putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000);

        return intent;
    }

    // =====================================================
    //  onActivityResult
    // =====================================================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        ArrayList<String> results =
                data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (results == null || results.isEmpty()) return;

        if (requestCode == SPEECH_REQUEST_CODE) {

            pendingName = results.get(0);

            textToSpeech.speak(
                    "I heard " + pendingName + ". Is this correct? Say yes or no.",
                    TextToSpeech.QUEUE_FLUSH, null, null);

            new Handler().postDelayed(() -> {
                Intent confirmIntent = buildSpeechIntent("Say yes or no");
                startActivityForResult(confirmIntent, CONFIRM_REQUEST_CODE);
            }, 2500);

        } else if (requestCode == CONFIRM_REQUEST_CODE) {

            boolean positive = isPositive(results);
            boolean negative = isNegative(results);

            if (positive && !negative) {          // clear YES
                UserProfile user = new UserProfile();
                user.id       = 1;
                user.fullName = pendingName;
                user.language = "en";
                userViewModel.insertUser(user);

                greetUser(pendingName);

            } else if (negative && !positive) {   // clear NO
                textToSpeech.speak(
                        "Okay, let's try again. Please say your full name.",
                        TextToSpeech.QUEUE_FLUSH, null, null);

                new Handler().postDelayed(this::askForUserName, 2000);

            } else {                              // ambiguous
                textToSpeech.speak(
                        "I didn't catch that clearly. Please say yes or no.",
                        TextToSpeech.QUEUE_FLUSH, null, null);

                new Handler().postDelayed(() -> {
                    Intent retry = buildSpeechIntent("Say yes or no");
                    startActivityForResult(retry, CONFIRM_REQUEST_CODE);
                }, 2000);
            }
        }
    }

    // =====================================================
    //  Helpers for yes / no detection
    // =====================================================

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

    // =====================================================
    //  TalkBack utilities
    // =====================================================

    private boolean isTalkBackEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return !TextUtils.isEmpty(enabledServices) &&
                enabledServices.toLowerCase().contains("talkback");
    }

    private void checkTalkBackAndGuide() {
        if (isTalkBackEnabled()) return;

        textToSpeech.speak(
                "Accessibility services appear to be disabled. " +
                        "Please enable TalkBack for full voice navigation.",
                TextToSpeech.QUEUE_FLUSH, null, null);

        new Handler().postDelayed(() -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        }, 2500);
    }

    // =====================================================
    //  Cleanup
    // =====================================================

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}