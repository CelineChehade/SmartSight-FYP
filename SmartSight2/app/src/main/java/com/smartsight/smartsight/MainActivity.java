package com.smartsight.smartsight;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final int SPEECH_REQUEST_CODE = 100;
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

        userViewModel = new ViewModelProvider(this)
                .get(UserViewModel.class);

        // Initialize TTS and wait for onInit()
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

    private void greetUser(String name) {
        tvGreeting.setText("Welcome, " + name);

        textToSpeech.speak(
                "Welcome, " + name,
                TextToSpeech.QUEUE_FLUSH,
                null,
                null
        );
    }

    private void askForUserName() {

        textToSpeech.speak(
                "Please say your full name",
                TextToSpeech.QUEUE_FLUSH,
                null,
                null
        );

        new Handler().postDelayed(() -> {

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                    "Say your full name");

            startActivityForResult(intent, SPEECH_REQUEST_CODE);

        }, 2000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        ArrayList<String> results =
                data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

        if (results == null || results.isEmpty()) return;

        if (requestCode == SPEECH_REQUEST_CODE) {

            pendingName = results.get(0);

            textToSpeech.speak(
                    "I heard " + pendingName +
                            ". Is this correct? Say yes or no.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    null
            );

            new Handler().postDelayed(() -> {

                Intent confirmIntent =
                        new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

                confirmIntent.putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

                startActivityForResult(confirmIntent, CONFIRM_REQUEST_CODE);

            }, 2500);

        } else if (requestCode == CONFIRM_REQUEST_CODE) {

            String answer = results.get(0).toLowerCase();

            if (answer.contains("yes")) {

                UserProfile user = new UserProfile();
                user.id = 1;
                user.fullName = pendingName;
                user.language = "en";

                userViewModel.insertUser(user);

                greetUser(pendingName);

            } else if(answer.contains("no")) {

                textToSpeech.speak(
                        "Okay, let's try again. Please say your full name.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                );

                new Handler().postDelayed(this::askForUserName, 2000);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}