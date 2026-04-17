package com.example.smartsight;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
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
import androidx.lifecycle.ViewModelProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SmartScanActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private PreviewView previewView;
    private ImageCapture imageCapture;

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;

    private ObjectDetector objectDetector;
    private ImageFingerprintExtractor fingerprintExtractor;
    private ItemMatcher itemMatcher;

    private ItemViewModel itemViewModel;
    private NoteViewModel noteViewModel;

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int AUDIO_PERMISSION_CODE = 101;

    private final Handler handler = new Handler();
    private boolean isHolding = false;

    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    // ───────────────────── SAVE FLOW STATE ─────────────────────

    private enum SaveState { IDLE, ASK_SAVE, ASK_NAME }
    private SaveState saveState = SaveState.IDLE;

    private enum ScanType { TEXT, OBJECT }
    private ScanType lastScanType;

    private String lastScannedText;
    private String lastDetectedObject;
    private Bitmap lastCapturedBitmap;
    private Bitmap lastObjectCrop;
    private String lastObjectFingerprint;

    // ───────────────────── ON CREATE ─────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_scan);

        previewView = findViewById(R.id.cameraPreview);

        tts = new TextToSpeech(this, this);
        initObjectDetector();

        fingerprintExtractor = new ImageFingerprintExtractor(this);
        itemMatcher = new ItemMatcher(this, fingerprintExtractor);

        itemViewModel = new ViewModelProvider(this).get(ItemViewModel.class);
        noteViewModel = new ViewModelProvider(this).get(NoteViewModel.class);

        requestPermissionsIfNeeded();

        View root = findViewById(R.id.smartScanRoot);
        root.setOnTouchListener((v, event) -> {
            if (saveState != SaveState.IDLE) return true;

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

    // ───────────────────── PERMISSIONS ─────────────────────

    private void requestPermissionsIfNeeded() {
        boolean cameraOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        boolean audioOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;

        if (!cameraOk) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else if (!audioOk) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_CODE);
        } else {
            initSpeechRecognizer();
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_CODE);
            } else {
                initSpeechRecognizer();
            }
        }

        if (requestCode == AUDIO_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initSpeechRecognizer();
        }
    }

    // ───────────────────── OBJECT DETECTOR ─────────────────────

    private void initObjectDetector() {
        try {
            ObjectDetector.ObjectDetectorOptions options =
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setMaxResults(3)
                            .setScoreThreshold(0.5f)
                            .build();

            objectDetector = ObjectDetector.createFromFileAndOptions(
                    this, "object_detection.tflite", options);
        } catch (Exception e) {
            Log.e("ObjectDetector", "Failed to load model: " + e.getMessage());
        }
    }

    // ───────────────────── SPEECH ─────────────────────

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");

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
                if (matches != null && !matches.isEmpty()) handleSpeechResult(matches.get(0));
                else speakAndThen("I didn't catch that. Please try again.",
                        SmartScanActivity.this::startListening);
            }

            @Override
            public void onError(int error) {
                speakAndThen("I didn't catch that. Please try again.",
                        SmartScanActivity.this::startListening);
            }
        });
    }

    private void startListening() {
        if (speechRecognizer != null && recognizerIntent != null)
            speechRecognizer.startListening(recognizerIntent);
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
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
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
                        int rotation = image.getImageInfo().getRotationDegrees();
                        Bitmap bitmap = imageProxyToBitmap(image);
                        Bitmap rotatedBitmap = rotateBitmap(bitmap, rotation);

                        InputImage inputImage =
                                InputImage.fromMediaImage(image.getImage(), rotation);

                        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                                .process(inputImage)
                                .addOnCompleteListener(task -> {
                                    image.close();

                                    if (task.isSuccessful() && task.getResult() != null
                                            && !task.getResult().getText().trim().isEmpty()) {
                                        String text = task.getResult().getText().trim();
                                        Log.d("ItemMatcher", "CAPTURE: OCR found text, going to text flow");
                                        handleTextScan(text, rotatedBitmap);
                                    } else {
                                        Log.d("ItemMatcher", "CAPTURE: no text, going to object flow");
                                        if (rotatedBitmap != null) detectObjects(rotatedBitmap);
                                        else speak("Sorry, couldn't process the image.");
                                    }
                                });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e("Scan", "Capture failed: " + exception.getMessage());
                        runOnUiThread(() -> speak("Capture failed. Please try again."));
                    }
                });
    }

    // ───────────────────── HANDLE TEXT SCAN ─────────────────────

    private void handleTextScan(String text, Bitmap bitmap) {
        Log.d("ItemMatcher", "handleTextScan: text=" + text);

        lastScanType = ScanType.TEXT;
        lastScannedText = text;
        lastCapturedBitmap = bitmap;

        backgroundExecutor.execute(() -> {
            SavedItem match = itemMatcher.matchText(text);

            runOnUiThread(() -> {
                if (match != null) {
                    Log.d("ItemMatcher", "TEXT MATCHED, speaking custom name");
                    vibrate();
                    speak("This is your " + match.customName);
                    saveState = SaveState.IDLE;
                } else {
                    Log.d("ItemMatcher", "TEXT not matched, asking to save");
                    speakAndThen(text, this::askIfSave);
                }
            });
        });
    }

    // ───────────────────── OBJECT DETECTION ─────────────────────

    private void detectObjects(Bitmap bitmap) {
        backgroundExecutor.execute(() -> {
            try {
                Log.d("ItemMatcher", "detectObjects: starting");

                if (objectDetector == null) {
                    Log.d("ItemMatcher", "detectObjects: objectDetector is NULL");
                    runOnUiThread(() -> speak("Object detector not ready"));
                    return;
                }

                TensorImage tensorImage = TensorImage.fromBitmap(bitmap);
                List<Detection> results = objectDetector.detect(tensorImage);

                Log.d("ItemMatcher", "detectObjects: found " + results.size() + " objects");

                if (results.isEmpty() || results.get(0).getCategories().isEmpty()) {
                    Log.d("ItemMatcher", "detectObjects: empty results");
                    runOnUiThread(() -> speak("I couldn't identify anything"));
                    return;
                }

                Detection primary = results.get(0);
                String primaryLabel = primary.getCategories().get(0).getLabel();

                Log.d("ItemMatcher", "detectObjects: primary label=" + primaryLabel);

                RectF boxF = primary.getBoundingBox();
                Rect box = new Rect((int) boxF.left, (int) boxF.top,
                        (int) boxF.right, (int) boxF.bottom);
                Bitmap crop = ItemMatcher.cropToBoundingBox(bitmap, box);

                Log.d("ItemMatcher", "crop size: " +
                        (crop != null ? crop.getWidth() + "x" + crop.getHeight() : "NULL"));

                String summary;
                if (results.size() > 1) {
                    StringBuilder sb = new StringBuilder("I see ");
                    int added = 0;
                    for (Detection d : results) {
                        if (!d.getCategories().isEmpty()) {
                            if (added > 0) sb.append(", and ");
                            sb.append(d.getCategories().get(0).getLabel());
                            added++;
                        }
                    }
                    summary = sb.toString();
                } else {
                    summary = "I see a " + primaryLabel;
                }

                Log.d("ItemMatcher", "About to call matchObject...");
                SavedItem match = itemMatcher.matchObject(crop, primaryLabel);
                Log.d("ItemMatcher", "matchObject returned: " +
                        (match != null ? match.customName : "null"));

                lastScanType = ScanType.OBJECT;
                lastDetectedObject = primaryLabel;
                lastCapturedBitmap = bitmap;
                lastObjectCrop = crop;
                lastObjectFingerprint = fingerprintExtractor.extractFingerprint(crop);

                Log.d("ItemMatcher", "Extracted fingerprint (len=" +
                        (lastObjectFingerprint != null ? lastObjectFingerprint.length() : 0) + ")");

                final SavedItem finalMatch = match;
                final String finalSummary = summary;

                runOnUiThread(() -> {
                    vibrate();
                    if (finalMatch != null) {
                        Log.d("ItemMatcher", "OBJECT MATCHED, speaking custom name");
                        speak("This is your " + finalMatch.customName);
                        saveState = SaveState.IDLE;
                    } else {
                        Log.d("ItemMatcher", "OBJECT not matched, asking to save");
                        speakAndThen(finalSummary, this::askIfSave);
                    }
                });

            } catch (Exception e) {
                Log.e("ObjectDetector", "Detection failed: " + e.getMessage());
                runOnUiThread(() -> speak("Error detecting objects"));
            }
        });
    }

    // ───────────────────── SAVE DIALOG FLOW ─────────────────────

    private void askIfSave() {
        saveState = SaveState.ASK_SAVE;
        speakAndThen("Do you want to save this? Say yes or no.", this::startListening);
    }

    private void askForName() {
        saveState = SaveState.ASK_NAME;
        speakAndThen("What name would you like to give it?", this::startListening);
    }

    private void handleSpeechResult(String spokenText) {
        String cleaned = spokenText.toLowerCase().trim();

        switch (saveState) {
            case ASK_SAVE:
                if (isYes(cleaned)) askForName();
                else if (isNo(cleaned)) {
                    saveState = SaveState.IDLE;
                    speak("Okay, not saving.");
                } else speakAndThen("Please say yes or no.", this::startListening);
                break;

            case ASK_NAME:
                String chosenName = spokenText.trim();
                if (chosenName.isEmpty()) {
                    speakAndThen("I didn't catch the name. Please say it again.", this::startListening);
                    return;
                }
                saveItem(chosenName);
                break;

            default: break;
        }
    }

    private boolean isYes(String s) {
        return s.matches(".*\\b(yes|yeah|yup|yep|sure|okay|ok|save|correct|right)\\b.*");
    }

    private boolean isNo(String s) {
        return s.matches(".*\\b(no|nope|nah|don't|do not|cancel|skip)\\b.*");
    }

    // ───────────────────── SAVING TO DATABASE ─────────────────────

    private void saveItem(String customName) {
        long now = System.currentTimeMillis();

        if (lastScanType == ScanType.TEXT) {
            SavedItem item = new SavedItem();
            item.customName = customName;
            item.detectedName = lastScannedText;
            item.category = "text";
            item.scanDate = now;
            item.isMedication = false;
            item.imagePath = null;
            item.voiceNotePath = null;
            item.imageFingerprint = null;

            Log.d("ItemMatcher", "SAVING TEXT item: " + customName + " | detected=" + lastScannedText);

            itemViewModel.insert(item);

            SavedNote note = new SavedNote();
            note.extractedText = lastScannedText;
            note.scanDate = now;
            note.language = Locale.getDefault().toString();
            noteViewModel.insert(note);

            saveState = SaveState.IDLE;
            speak("Saved as " + customName);

        } else if (lastScanType == ScanType.OBJECT) {
            final Bitmap bitmapToSave = lastCapturedBitmap;
            final String detectedName = lastDetectedObject;
            final String fingerprint = lastObjectFingerprint;

            Log.d("ItemMatcher", "SAVING OBJECT item: " + customName + " | detected=" + detectedName
                    + " | fingerprint len=" + (fingerprint != null ? fingerprint.length() : 0));

            backgroundExecutor.execute(() -> {
                String imagePath = saveBitmapToInternalStorage(bitmapToSave, now);

                SavedItem item = new SavedItem();
                item.customName = customName;
                item.detectedName = detectedName;
                item.category = "object";
                item.scanDate = now;
                item.isMedication = false;
                item.imagePath = imagePath;
                item.voiceNotePath = null;
                item.imageFingerprint = fingerprint;

                runOnUiThread(() -> {
                    itemViewModel.insert(item);
                    saveState = SaveState.IDLE;
                    speak("Saved as " + customName);
                });
            });
        }
    }

    private String saveBitmapToInternalStorage(Bitmap bitmap, long timestamp) {
        if (bitmap == null) return null;
        try {
            File dir = new File(getFilesDir(), "saved_images");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "img_" + timestamp + ".jpg");
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e("SaveImage", "Failed: " + e.getMessage());
            return null;
        }
    }

    // ───────────────────── HELPERS ─────────────────────

    private void speak(String msg) {
        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private void speakAndThen(String msg, Runnable action) {
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

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            java.nio.ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) { return null; }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int rotation) {
        if (bitmap == null) return null;
        if (rotation == 0) return bitmap;
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

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (objectDetector != null) objectDetector.close();
        if (fingerprintExtractor != null) fingerprintExtractor.close();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
