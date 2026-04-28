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
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

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

    private static final String TAG = "SmartScanActivity";
    private TextToSpeech tts;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private Button btnScan;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private ObjectDetector objectDetector;
    private ImageFingerprintExtractor fingerprintExtractor;
    private ItemMatcher itemMatcher;
    private ItemViewModel itemViewModel;
    private NoteViewModel noteViewModel;
    private AppRepository repository;
    private ReminderVoiceFlow reminderFlow;

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int AUDIO_PERMISSION_CODE = 101;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isHolding = false;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    private enum SaveState {
        IDLE,
        ASK_WHICH_OBJECT,      // NEW: when multiple objects detected
        ASK_TEXT_OR_OBJECT,    // When chosen object has text
        ASK_SAVE,
        ASK_NAME,
        ASK_REMINDER
    }
    private SaveState saveState = SaveState.IDLE;

    private enum ScanType { TEXT, OBJECT }
    private ScanType lastScanType;

    private String lastScannedText;
    private List<DetectedObject> detectedObjects = new ArrayList<>();  // NEW: multiple objects
    private DetectedObject chosenObject;  // NEW: user's choice

    private Bitmap lastCapturedBitmap;

    private int lastSavedItemId = -1;
    private String lastSavedItemName;

    private boolean isListening = false;

    // Helper class to store detected objects
    private static class DetectedObject {
        String label;
        Bitmap crop;
        String fingerprint;

        DetectedObject(String label, Bitmap crop, String fingerprint) {
            this.label = label;
            this.crop = crop;
            this.fingerprint = fingerprint;
        }
    }

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

        setContentView(R.layout.activity_smart_scan);

        previewView = findViewById(R.id.cameraPreview);
        btnScan = findViewById(R.id.btnScan);

        tts = new TextToSpeech(this, this);

        initObjectDetector();

        fingerprintExtractor = new ImageFingerprintExtractor(this);
        itemMatcher = new ItemMatcher(this, fingerprintExtractor);

        itemViewModel = new ViewModelProvider(this).get(ItemViewModel.class);
        noteViewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        repository = new AppRepository(this);

        requestPermissionsIfNeeded();
        setupScanUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupScanUI();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            TtsHelper.applySettings(this, tts);

            if (AccessibilityUtils.isTalkBackEnabled(this)) {
                TtsHelper.speak(tts, getString(R.string.instruction_talkback_scan));
            } else {
                TtsHelper.speak(tts, getString(R.string.instruction_non_talkback_scan));
            }
        }
    }

    private void setupScanUI() {
        boolean talkBackOn = AccessibilityUtils.isTalkBackEnabled(this);
        View root = findViewById(R.id.smartScanRoot);

        if (talkBackOn) {
            btnScan.setVisibility(View.VISIBLE);
            btnScan.setContentDescription(getString(R.string.scan_button_description));
            btnScan.setOnClickListener(v -> {
                if (saveState == SaveState.IDLE && imageCapture != null) {
                    captureImage();
                }
            });
            root.setOnTouchListener(null);
            root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        } else {
            btnScan.setVisibility(View.GONE);
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
    }

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

    private void initObjectDetector() {
        try {
            ObjectDetector.ObjectDetectorOptions options =
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setMaxResults(5)  // INCREASED from 3 to 5
                            .setScoreThreshold(0.4f)  // LOWERED from 0.5 to catch more objects
                            .build();

            objectDetector = ObjectDetector.createFromFileAndOptions(
                    this, "object_detection.tflite", options);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load model: " + e.getMessage());
        }
    }

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                LocaleManager.getSttLanguageTag(this));
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "🎤 Ready for speech");
            }
            @Override public void onBeginningOfSpeech() {
                Log.d(TAG, "🗣️ User speaking");
            }
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {
                Log.d(TAG, "✋ Speech ended");
            }
            @Override public void onEvent(int eventType, Bundle params) {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onRmsChanged(float rmsdB) {}

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                Log.d(TAG, "📝 Heard: " + (matches != null ? matches.toString() : "null"));

                if (matches != null && !matches.isEmpty()) {
                    handleSpeechResult(matches.get(0));
                } else {
                    TtsHelper.speakThen(tts, getString(R.string.didnt_catch), SmartScanActivity.this::startListening);
                }
            }

            @Override
            public void onError(int error) {
                isListening = false;
                Log.e(TAG, "❌ Speech error: " + error);

                handler.postDelayed(() -> {
                    TtsHelper.speakThen(tts, getString(R.string.didnt_catch), SmartScanActivity.this::startListening);
                }, 500);
            }
        });
    }

    private void startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening, skipping");
            return;
        }

        if (speechRecognizer != null && recognizerIntent != null) {
            try {
                isListening = true;
                speechRecognizer.startListening(recognizerIntent);
                Log.d(TAG, "🎤 Started listening");
            } catch (Exception e) {
                isListening = false;
                Log.e(TAG, "Failed to start listening: " + e.getMessage());
            }
        }
    }

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

            } catch (Exception e) {
                Log.e(TAG, "Camera error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void captureImage() {
        TtsHelper.speak(tts, getString(R.string.scanning));

        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        int rotation = image.getImageInfo().getRotationDegrees();
                        Bitmap bitmap = imageProxyToBitmap(image);
                        Bitmap rotatedBitmap = rotateBitmap(bitmap, rotation);

                        InputImage inputImage =
                                InputImage.fromMediaImage(image.getImage(), rotation);

                        processBothTextAndObjects(inputImage, rotatedBitmap);
                        image.close();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Capture failed: " + exception.getMessage());
                        runOnUiThread(() -> TtsHelper.speak(tts, getString(R.string.capture_failed)));
                    }
                });
    }

    private void processBothTextAndObjects(InputImage inputImage, Bitmap bitmap) {
        final String[] detectedText = {null};
        final List<DetectedObject> foundObjects = new ArrayList<>();

        // Run OCR
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(inputImage)
                .addOnCompleteListener(textTask -> {
                    if (textTask.isSuccessful() && textTask.getResult() != null) {
                        String text = textTask.getResult().getText().trim();
                        if (!text.isEmpty()) {
                            detectedText[0] = text;
                        }
                    }
                    checkScanComplete(detectedText[0], foundObjects, bitmap);
                });

        // Run object detection (ALL objects, not just first)
        backgroundExecutor.execute(() -> {
            try {
                if (objectDetector == null) {
                    runOnUiThread(() -> checkScanComplete(detectedText[0], foundObjects, bitmap));
                    return;
                }

                TensorImage tensorImage = TensorImage.fromBitmap(bitmap);
                List<Detection> results = objectDetector.detect(tensorImage);

                Log.d(TAG, "Object detection found " + results.size() + " objects");

                // Process ALL detected objects
                for (Detection detection : results) {
                    if (detection.getCategories().isEmpty()) continue;

                    String label = detection.getCategories().get(0).getLabel();
                    RectF boxF = detection.getBoundingBox();
                    Rect box = new Rect((int) boxF.left, (int) boxF.top,
                            (int) boxF.right, (int) boxF.bottom);

                    Bitmap crop = ItemMatcher.cropToBoundingBox(bitmap, box);
                    String fingerprint = fingerprintExtractor.extractFingerprint(crop);

                    foundObjects.add(new DetectedObject(label, crop, fingerprint));
                    Log.d(TAG, "  - " + label);
                }

                runOnUiThread(() -> checkScanComplete(detectedText[0], foundObjects, bitmap));

            } catch (Exception e) {
                Log.e(TAG, "Detection failed: " + e.getMessage());
                runOnUiThread(() -> checkScanComplete(detectedText[0], foundObjects, bitmap));
            }
        });
    }

    private void checkScanComplete(String text, List<DetectedObject> objects, Bitmap fullBitmap) {
        boolean hasText = (text != null && !text.isEmpty());
        boolean hasObjects = (!objects.isEmpty());

        lastCapturedBitmap = fullBitmap;
        lastScannedText = text;
        detectedObjects = new ArrayList<>(objects);

        if (!hasText && !hasObjects) {
            TtsHelper.speak(tts, getString(R.string.couldnt_identify));
            saveState = SaveState.IDLE;
            return;
        }

        vibrate();

        // Check for matches FIRST
        if (hasText) {
            backgroundExecutor.execute(() -> {
                SavedItem textMatch = itemMatcher.matchText(text);
                runOnUiThread(() -> {
                    if (textMatch != null) {
                        TtsHelper.speak(tts, getString(R.string.this_is_your, textMatch.customName));
                        saveState = SaveState.IDLE;
                        return;
                    }
                    checkObjectMatches(hasText, hasObjects);
                });
            });
        } else {
            checkObjectMatches(hasText, hasObjects);
        }
    }

    private void checkObjectMatches(boolean hasText, boolean hasObjects) {
        if (!hasObjects) {
            // Only text, no match found
            lastScanType = ScanType.TEXT;
            TtsHelper.speakThen(tts, lastScannedText, this::askIfSave);
            return;
        }

        // Check ALL objects for matches
        backgroundExecutor.execute(() -> {
            List<SavedItem> matchedItems = new ArrayList<>();
            List<DetectedObject> unmatchedObjects = new ArrayList<>();

            Log.d(TAG, "Checking " + detectedObjects.size() + " detected objects against saved items");

            for (DetectedObject obj : detectedObjects) {
                SavedItem match = itemMatcher.matchObject(obj.crop, obj.label);
                if (match != null) {
                    Log.d(TAG, "✅ Object \"" + obj.label + "\" matched to saved item \"" + match.customName + "\"");
                    matchedItems.add(match);
                } else {
                    Log.d(TAG, "❌ Object \"" + obj.label + "\" has no match");
                    unmatchedObjects.add(obj);
                }
            }

            runOnUiThread(() -> {
                // Build announcement
                StringBuilder announcement = new StringBuilder();

                // Announce matched objects first
                if (!matchedItems.isEmpty()) {
                    for (int i = 0; i < matchedItems.size(); i++) {
                        if (i > 0) announcement.append(". ");
                        announcement.append("This is your ").append(matchedItems.get(i).customName);
                    }
                }

                // Then announce unmatched objects
                if (!unmatchedObjects.isEmpty()) {
                    if (!matchedItems.isEmpty()) {
                        announcement.append(". I also see ");
                    } else {
                        announcement.append("I see ");
                    }

                    for (int i = 0; i < unmatchedObjects.size(); i++) {
                        String translated = LabelTranslator.translate(this, unmatchedObjects.get(i).label);
                        announcement.append(translated);
                        if (i < unmatchedObjects.size() - 2) {
                            announcement.append(", ");
                        } else if (i == unmatchedObjects.size() - 2) {
                            announcement.append(" and ");
                        }
                    }
                }

                if (!matchedItems.isEmpty() && unmatchedObjects.isEmpty()) {
                    // Only matches, no new objects
                    TtsHelper.speak(tts, announcement.toString());
                    saveState = SaveState.IDLE;
                } else if (matchedItems.isEmpty() && !unmatchedObjects.isEmpty()) {
                    // Only unmatched objects - proceed to ask which one to save
                    announceDetectedItems(hasText, hasObjects);
                } else {
                    // Both matched and unmatched - announce everything, don't save
                    announcement.append(".");
                    TtsHelper.speak(tts, announcement.toString());
                    saveState = SaveState.IDLE;
                }
            });
        });
    }

    private void announceDetectedItems(boolean hasText, boolean hasObjects) {
        StringBuilder announcement = new StringBuilder();

        if (hasObjects) {
            announcement.append("I see ");
            for (int i = 0; i < detectedObjects.size(); i++) {
                String translated = LabelTranslator.translate(this, detectedObjects.get(i).label);
                announcement.append(translated);
                if (i < detectedObjects.size() - 2) {
                    announcement.append(", ");
                } else if (i == detectedObjects.size() - 2) {
                    announcement.append(" and ");
                }
            }
            announcement.append(". ");
        }

        if (hasText) {
            announcement.append("There is also text. ");
        }

        if (detectedObjects.size() > 1) {
            // Multiple objects - ask which one
            announcement.append("Which one would you like to save?");
            saveState = SaveState.ASK_WHICH_OBJECT;
            TtsHelper.speakThen(tts, announcement.toString(), this::startListening);
        } else if (detectedObjects.size() == 1 && hasText) {
            // One object + text - choose object, then ask text/object
            chosenObject = detectedObjects.get(0);
            announcement.append("Would you like to save the ");
            announcement.append(LabelTranslator.translate(this, chosenObject.label));
            announcement.append(" as text or as an object?");
            saveState = SaveState.ASK_TEXT_OR_OBJECT;
            TtsHelper.speakThen(tts, announcement.toString(), this::startListening);
        } else if (detectedObjects.size() == 1) {
            // Only one object, no text
            chosenObject = detectedObjects.get(0);
            lastScanType = ScanType.OBJECT;
            String translated = LabelTranslator.translate(this, chosenObject.label);
            TtsHelper.speakThen(tts, getString(R.string.i_see_a, translated), this::askIfSave);
        } else {
            // Only text
            lastScanType = ScanType.TEXT;
            TtsHelper.speakThen(tts, lastScannedText, this::askIfSave);
        }
    }

    private void askIfSave() {
        saveState = SaveState.ASK_SAVE;
        TtsHelper.speakThen(tts, getString(R.string.ask_save), this::startListening);
    }

    private void askForName() {
        saveState = SaveState.ASK_NAME;
        TtsHelper.speakThen(tts, getString(R.string.ask_name_for_item), this::startListening);
    }

    private void askIfAddReminder() {
        saveState = SaveState.ASK_REMINDER;
        TtsHelper.speakThen(tts, getString(R.string.ask_add_reminder), this::startListening);
    }

    private void handleSpeechResult(String spokenText) {
        String cleaned = spokenText.toLowerCase().trim();
        Log.d(TAG, "State=" + saveState + " Heard=\"" + cleaned + "\"");

        switch (saveState) {
            case ASK_WHICH_OBJECT:
                // User choosing between multiple objects
                for (DetectedObject obj : detectedObjects) {
                    if (cleaned.contains(obj.label.toLowerCase())) {
                        chosenObject = obj;

                        // Check if there's also text
                        if (lastScannedText != null && !lastScannedText.isEmpty()) {
                            saveState = SaveState.ASK_TEXT_OR_OBJECT;
                            TtsHelper.speakThen(tts, "Would you like to save this as text or as an object?", this::startListening);
                        } else {
                            lastScanType = ScanType.OBJECT;
                            String translated = LabelTranslator.translate(this, chosenObject.label);
                            TtsHelper.speakThen(tts, getString(R.string.i_see_a, translated), this::askIfSave);
                        }
                        return;
                    }
                }
                TtsHelper.speakThen(tts, "I didn't catch that. Please say which object you want.", this::startListening);
                break;

            case ASK_TEXT_OR_OBJECT:
                if (containsAny(cleaned, "text", "texte", "words", "writing", "mots", "écriture")) {
                    lastScanType = ScanType.TEXT;
                    TtsHelper.speakThen(tts, lastScannedText, this::askIfSave);
                } else if (containsAny(cleaned, "object", "objet", "thing", "item", "chose")) {
                    lastScanType = ScanType.OBJECT;
                    String translated = LabelTranslator.translate(this, chosenObject.label);
                    TtsHelper.speakThen(tts, getString(R.string.i_see_a, translated), this::askIfSave);
                } else {
                    TtsHelper.speakThen(tts, getString(R.string.please_say_text_or_object), this::startListening);
                }
                break;

            case ASK_SAVE:
                if (TtsHelper.isYes(cleaned)) {
                    askForName();
                } else if (TtsHelper.isNo(cleaned)) {
                    saveState = SaveState.IDLE;
                    TtsHelper.speak(tts, getString(R.string.not_saving));
                } else {
                    TtsHelper.speakThen(tts, getString(R.string.please_say_yes_no), this::startListening);
                }
                break;

            case ASK_NAME:
                String chosenName = spokenText.trim();
                if (chosenName.isEmpty()) {
                    TtsHelper.speakThen(tts, getString(R.string.didnt_catch_name), this::startListening);
                    return;
                }
                saveItem(chosenName);
                break;

            case ASK_REMINDER:
                if (TtsHelper.isYes(cleaned)) {
                    startReminderFlow();
                } else if (TtsHelper.isNo(cleaned)) {
                    saveState = SaveState.IDLE;
                    TtsHelper.speak(tts, getString(R.string.saved_as, lastSavedItemName));
                } else {
                    TtsHelper.speakThen(tts, getString(R.string.please_say_yes_no), this::startListening);
                }
                break;

            default: break;
        }
    }

    private boolean containsAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    private void startReminderFlow() {
        if (reminderFlow != null) reminderFlow.shutdown();

        reminderFlow = new ReminderVoiceFlow(this, tts, new ReminderVoiceFlow.Callbacks() {
            @Override
            public void onReminderDefined(String repeatType, long reminderTimeMs) {
                persistAndScheduleReminder(repeatType, reminderTimeMs);
            }

            @Override
            public void onCancelled() {
                runOnUiThread(() -> {
                    saveState = SaveState.IDLE;
                    TtsHelper.speak(tts, getString(R.string.saved_as, lastSavedItemName));
                });
            }
        });

        reminderFlow.start();
    }

    private void persistAndScheduleReminder(String repeatType, long reminderTimeMs) {
        if (lastSavedItemId < 0 || lastSavedItemName == null) {
            Log.e(TAG, "No saved item to attach reminder to");
            runOnUiThread(() -> {
                saveState = SaveState.IDLE;
                TtsHelper.speak(tts, getString(R.string.reminder_failed));
            });
            return;
        }

        final int itemIdSnapshot = lastSavedItemId;
        final String nameSnapshot = lastSavedItemName;

        Reminder r = new Reminder();
        r.itemId = itemIdSnapshot;
        r.repeatType = repeatType;
        r.isActive = true;
        r.reminderTime = reminderTimeMs;

        repository.insertReminder(r, newId -> {
            int reminderIdInt = (int) newId;

            ReminderScheduler.scheduleAt(
                    SmartScanActivity.this,
                    reminderIdInt,
                    itemIdSnapshot,
                    nameSnapshot,
                    repeatType,
                    reminderTimeMs);

            runOnUiThread(() -> {
                saveState = SaveState.IDLE;
                TtsHelper.speak(tts, getString(R.string.reminder_saved));
            });
        });
    }

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
            item.imageFingerprint = null;

            backgroundExecutor.execute(() -> {
                long newId = insertItemSync(item);

                SavedNote note = new SavedNote();
                note.extractedText = lastScannedText;
                note.scanDate = now;
                note.language = Locale.getDefault().toString();

                AppDatabase.getInstance(this).noteDao().insert(note);

                runOnUiThread(() -> {
                    lastSavedItemId = (int) newId;
                    lastSavedItemName = customName;
                    TtsHelper.speakThen(tts, getString(R.string.saved_as, customName), this::askIfAddReminder);
                });
            });

        } else if (lastScanType == ScanType.OBJECT && chosenObject != null) {
            final Bitmap bitmapToSave = lastCapturedBitmap;
            final String detectedName = chosenObject.label;
            final String fingerprint = chosenObject.fingerprint;

            backgroundExecutor.execute(() -> {
                String imagePath = saveBitmapToInternalStorage(bitmapToSave, now);

                SavedItem item = new SavedItem();
                item.customName = customName;
                item.detectedName = detectedName;
                item.category = "object";
                item.scanDate = now;
                item.isMedication = false;
                item.imagePath = imagePath;
                item.imageFingerprint = fingerprint;

                long newId = insertItemSync(item);

                runOnUiThread(() -> {
                    lastSavedItemId = (int) newId;
                    lastSavedItemName = customName;
                    TtsHelper.speakThen(tts, getString(R.string.saved_as, customName), this::askIfAddReminder);
                });
            });
        }
    }

    private long insertItemSync(SavedItem item) {
        try {
            return AppDatabase.getInstance(this).itemDao().insertAndGetId(item);
        } catch (Exception e) {
            Log.e(TAG, "insertAndGetId failed: " + e.getMessage());
            return -1;
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
            Log.e(TAG, "Failed to save bitmap: " + e.getMessage());
            return null;
        }
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
    protected void onPause() {
        super.onPause();
        if (reminderFlow != null) {
            reminderFlow.shutdown();
            reminderFlow = null;
        }
        if (speechRecognizer != null && isListening) {
            speechRecognizer.stopListening();
            isListening = false;
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (objectDetector != null) objectDetector.close();
        if (fingerprintExtractor != null) fingerprintExtractor.close();
        if (reminderFlow != null) reminderFlow.shutdown();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
