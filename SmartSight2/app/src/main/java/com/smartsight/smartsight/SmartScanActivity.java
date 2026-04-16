package com.example.smartsight;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

public class SmartScanActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private PreviewView previewView;
    private ImageCapture imageCapture;

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;

    private static final int CAMERA_PERMISSION_CODE = 100;
    private final Handler handler = new Handler();
    private boolean isHolding = false;

    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    // ───────────────────── ON CREATE ─────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_scan);

        previewView = findViewById(R.id.cameraPreview);

        tts = new TextToSpeech(this, this);
        initSpeechRecognizer();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else startCamera();

        View root = findViewById(R.id.smartScanRoot);
        root.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isHolding = true;
                    handler.postDelayed(() -> {
                        if (isHolding && imageCapture != null) captureImage();
                    }, 2000);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isHolding = false;
                    handler.removeCallbacksAndMessages(null);
                    return true;
            }
            return false;
        });
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS)
            tts.setLanguage(Locale.getDefault());
    }

    // ───────────────────── SPEECH ─────────────────────

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
    }

    // ───────────────────── CAMERA ─────────────────────

    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider =
                        ProcessCameraProvider.getInstance(this).get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture);

            } catch (Exception e) { e.printStackTrace(); }
        }, ContextCompat.getMainExecutor(this));
    }

    // ───────────────────── CAPTURE ─────────────────────

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void captureImage() {
        tts.speak("Scanning", TextToSpeech.QUEUE_FLUSH, null, null);

        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {

                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {

                        Bitmap bitmap = imageProxyToBitmap(image);
                        int rotation = image.getImageInfo().getRotationDegrees();

                        InputImage inputImage =
                                InputImage.fromMediaImage(image.getImage(), rotation);

                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                                .process(inputImage)
                                .addOnSuccessListener(result -> {
                                    String text = result.getText().trim();
                                    image.close();

                                    if (!text.isEmpty()) speak(text);
                                    else runCloudVision(rotateBitmap(bitmap, rotation));
                                });
                    }
                });
    }

    // ───────────────────── CLOUD VISION ─────────────────────

    private void runCloudVision(Bitmap bitmap) {
        backgroundExecutor.execute(() -> {
            try {
                String apiKey = BuildConfig.VISION_API_KEY;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
                String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                JsonObject imageObj = new JsonObject();
                imageObj.addProperty("content", base64Image);

                JsonObject feature = new JsonObject();
                feature.addProperty("type", "TEXT_DETECTION");

                JsonArray features = new JsonArray();
                features.add(feature);

                JsonObject req = new JsonObject();
                req.add("image", imageObj);
                req.add("features", features);

                JsonArray requests = new JsonArray();
                requests.add(req);

                JsonObject bodyJson = new JsonObject();
                bodyJson.add("requests", requests);

                RequestBody body = RequestBody.create(
                        bodyJson.toString(),
                        MediaType.get("application/json"));

                Request request = new Request.Builder()
                        .url("https://vision.googleapis.com/v1/images:annotate?key=" + apiKey)
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.body() != null)
                        parseVisionResponse(response.body().string());
                }

            } catch (Exception e) { Log.e("Vision", e.getMessage()); }
        });
    }

    private void parseVisionResponse(String json) {
        runOnUiThread(() -> {
            try {
                JsonObject first = JsonParser.parseString(json)
                        .getAsJsonObject()
                        .getAsJsonArray("responses")
                        .get(0).getAsJsonObject();

                if (!first.has("textAnnotations")) {
                    speak("I couldn't read anything");
                    return;
                }

                String detectedText = first.getAsJsonArray("textAnnotations")
                        .get(0).getAsJsonObject()
                        .get("description").getAsString();

                vibrate();
                speak(detectedText);

            } catch (Exception e) { speak("Error reading result"); }
        });
    }

    // ───────────────────── HELPERS ─────────────────────

    private void speak(String msg) {
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            java.nio.ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) { return null; }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int rotation) {
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        return Bitmap.createBitmap(bitmap, 0, 0,
                bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) return;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
        else vibrator.vibrate(200);
    }
}
