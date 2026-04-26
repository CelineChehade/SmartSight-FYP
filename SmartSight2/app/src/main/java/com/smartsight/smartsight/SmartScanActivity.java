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

import android.provider.Settings;

import android.speech.RecognitionListener;

import android.speech.RecognizerIntent;

import android.speech.SpeechRecognizer;

import android.speech.tts.TextToSpeech;

import android.speech.tts.UtteranceProgressListener;

import android.text.TextUtils;

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

    // NEW: reminder voice flow (built lazily after each save)

    private ReminderVoiceFlow reminderFlow;

    private static final int CAMERA_PERMISSION_CODE = 100;

    private static final int AUDIO_PERMISSION_CODE = 101;

    private final Handler handler = new Handler();

    private boolean isHolding = false;

    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    private enum SaveState { IDLE, ASK_SAVE, ASK_NAME, ASK_REMINDER }

    private SaveState saveState = SaveState.IDLE;

    private enum ScanType { TEXT, OBJECT }

    private ScanType lastScanType;

    private String lastScannedText;

    private String lastDetectedObject;

    private Bitmap lastCapturedBitmap;

    private Bitmap lastObjectCrop;

    private String lastObjectFingerprint;

    // Track the just-saved item so the reminder can be linked to it

    private int lastSavedItemId = -1;

    private String lastSavedItemName;

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

        if (AccessibilityUtils.isTalkBackEnabled(this)) {

            speak(getString(R.string.instruction_talkback_scan));

        } else {

            speak(getString(R.string.instruction_non_talkback_scan));

        }

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

    private boolean isTalkBackEnabled() {

        String enabled = Settings.Secure.getString(

                getContentResolver(),

                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        return !TextUtils.isEmpty(enabled) && enabled.toLowerCase().contains("talkback");

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

                            .setMaxResults(3)

                            .setScoreThreshold(0.5f)

                            .build();

            objectDetector = ObjectDetector.createFromFileAndOptions(

                    this, "object_detection.tflite", options);

        } catch (Exception e) {

            Log.e("ObjectDetector", "Failed to load model: " + e.getMessage());

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

                if (matches != null && !matches.isEmpty()) handleSpeechResult(matches.get(0));

                else speakAndThen(getString(R.string.didnt_catch),

                        SmartScanActivity.this::startListening);

            }

            @Override

            public void onError(int error) {

                speakAndThen(getString(R.string.didnt_catch),

                        SmartScanActivity.this::startListening);

            }

        });

    }

    private void startListening() {

        if (speechRecognizer != null && recognizerIntent != null)

            speechRecognizer.startListening(recognizerIntent);

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

            } catch (Exception e) { e.printStackTrace(); }

        }, ContextCompat.getMainExecutor(this));

    }

    @OptIn(markerClass = ExperimentalGetImage.class)

    private void captureImage() {

        tts.speak(getString(R.string.scanning), TextToSpeech.QUEUE_FLUSH, null, null);

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

                                        handleTextScan(text, rotatedBitmap);

                                    } else {

                                        if (rotatedBitmap != null) detectObjects(rotatedBitmap);

                                        else speak(getString(R.string.could_not_process));

                                    }

                                });

                    }

                    @Override

                    public void onError(@NonNull ImageCaptureException exception) {

                        Log.e("Scan", "Capture failed: " + exception.getMessage());

                        runOnUiThread(() -> speak(getString(R.string.capture_failed)));

                    }

                });

    }

    private void handleTextScan(String text, Bitmap bitmap) {

        lastScanType = ScanType.TEXT;

        lastScannedText = text;

        lastCapturedBitmap = bitmap;

        backgroundExecutor.execute(() -> {

            SavedItem match = itemMatcher.matchText(text);

            runOnUiThread(() -> {

                if (match != null) {

                    vibrate();

                    speak(getString(R.string.this_is_your, match.customName));

                    saveState = SaveState.IDLE;

                } else {

                    speakAndThen(text, this::askIfSave);

                }

            });

        });

    }

    private void detectObjects(Bitmap bitmap) {

        backgroundExecutor.execute(() -> {

            try {

                if (objectDetector == null) {

                    runOnUiThread(() -> speak(getString(R.string.object_detector_not_ready)));

                    return;

                }

                TensorImage tensorImage = TensorImage.fromBitmap(bitmap);

                List<Detection> results = objectDetector.detect(tensorImage);

                if (results.isEmpty() || results.get(0).getCategories().isEmpty()) {

                    runOnUiThread(() -> speak(getString(R.string.couldnt_identify)));

                    return;

                }

                Detection primary = results.get(0);

                String primaryLabel = primary.getCategories().get(0).getLabel();

                RectF boxF = primary.getBoundingBox();

                Rect box = new Rect((int) boxF.left, (int) boxF.top,

                        (int) boxF.right, (int) boxF.bottom);

                Bitmap crop = ItemMatcher.cropToBoundingBox(bitmap, box);

                String summary;

                if (results.size() > 1) {

                    StringBuilder joined = new StringBuilder();

                    int added = 0;

                    for (Detection d : results) {

                        if (!d.getCategories().isEmpty()) {

                            if (added > 0) joined.append(", ");

                            joined.append(LabelTranslator.translate(this,

                                    d.getCategories().get(0).getLabel()));

                            added++;

                        }

                    }

                    summary = getString(R.string.i_see_a, joined.toString());

                } else {

                    summary = getString(R.string.i_see_a,

                            LabelTranslator.translate(this, primaryLabel));

                }

                SavedItem match = itemMatcher.matchObject(crop, primaryLabel);

                lastScanType = ScanType.OBJECT;

                lastDetectedObject = primaryLabel;

                lastCapturedBitmap = bitmap;

                lastObjectCrop = crop;

                lastObjectFingerprint = fingerprintExtractor.extractFingerprint(crop);

                final SavedItem finalMatch = match;

                final String finalSummary = summary;

                runOnUiThread(() -> {

                    vibrate();

                    if (finalMatch != null) {

                        speak(getString(R.string.this_is_your, finalMatch.customName));

                        saveState = SaveState.IDLE;

                    } else {

                        speakAndThen(finalSummary, this::askIfSave);

                    }

                });

            } catch (Exception e) {

                Log.e("ObjectDetector", "Detection failed: " + e.getMessage());

                runOnUiThread(() -> speak(getString(R.string.error_detecting)));

            }

        });

    }

    private void askIfSave() {

        saveState = SaveState.ASK_SAVE;

        speakAndThen(getString(R.string.ask_save), this::startListening);

    }

    private void askForName() {

        saveState = SaveState.ASK_NAME;

        speakAndThen(getString(R.string.ask_name_for_item), this::startListening);

    }

    // NEW: ask whether to add a reminder to the freshly-saved item

    private void askIfAddReminder() {

        saveState = SaveState.ASK_REMINDER;

        speakAndThen(getString(R.string.ask_add_reminder), this::startListening);

    }

    private void handleSpeechResult(String spokenText) {

        String cleaned = spokenText.toLowerCase().trim();

        switch (saveState) {

            case ASK_SAVE:

                if (isYes(cleaned)) askForName();

                else if (isNo(cleaned)) {

                    saveState = SaveState.IDLE;

                    speak(getString(R.string.not_saving));

                } else speakAndThen(getString(R.string.please_say_yes_no), this::startListening);

                break;

            case ASK_NAME:

                String chosenName = spokenText.trim();

                if (chosenName.isEmpty()) {

                    speakAndThen(getString(R.string.didnt_catch_name), this::startListening);

                    return;

                }

                saveItem(chosenName);

                break;

            case ASK_REMINDER:

                if (isYes(cleaned)) {

                    startReminderFlow();

                } else if (isNo(cleaned)) {

                    saveState = SaveState.IDLE;

                    speak(getString(R.string.saved_as, lastSavedItemName));

                } else {

                    speakAndThen(getString(R.string.please_say_yes_no), this::startListening);

                }

                break;

            default: break;

        }

    }

    // NEW: launch the reusable voice flow

    private void startReminderFlow() {

        // Free the activity's SpeechRecognizer temporarily — the flow owns its own.

        // (No need to destroy; just make sure we don't overlap.)

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

                    speak(getString(R.string.saved_as, lastSavedItemName));

                });

            }

        });

        reminderFlow.start();

    }

    // NEW: insert reminder row, then hand its reminderId to AlarmManager

    private void persistAndScheduleReminder(String repeatType, long reminderTimeMs) {

        if (lastSavedItemId < 0 || lastSavedItemName == null) {

            Log.e("SmartScan", "No saved item to attach reminder to");

            runOnUiThread(() -> {

                saveState = SaveState.IDLE;

                speak(getString(R.string.reminder_failed));

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

            // Back on background thread — schedule the actual alarm

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

                speak(getString(R.string.reminder_saved));

            });

        });

    }

    private boolean isYes(String s) {

        return s.matches(".*\\b(yes|yeah|yup|yep|sure|okay|ok|save|correct|right|oui|ouais|d'accord)\\b.*");

    }

    private boolean isNo(String s) {

        return s.matches(".*\\b(no|nope|nah|don't|do not|cancel|skip|non|annuler)\\b.*");

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

            item.voiceNotePath = null;

            item.imageFingerprint = null;

            // We need the generated itemId for the reminder — insert synchronously

            backgroundExecutor.execute(() -> {

                long newId = AppDatabase.getInstance(this).itemDao() != null

                        ? insertItemSync(item)

                        : -1;

                SavedNote note = new SavedNote();

                note.customName = customName;

                note.extractedText = lastScannedText;

                note.scanType = "text";

                note.scanDate = now;

                note.language = Locale.getDefault().toString();

                noteViewModel.insert(note);

                runOnUiThread(() -> {

                    lastSavedItemId = (int) newId;

                    lastSavedItemName = customName;

                    // After save, ask about reminder rather than jumping straight to IDLE

                    speakAndThen(getString(R.string.saved_as, customName), this::askIfAddReminder);

                });

            });

        } else if (lastScanType == ScanType.OBJECT) {

            final Bitmap bitmapToSave = lastCapturedBitmap;

            final String detectedName = lastDetectedObject;

            final String fingerprint = lastObjectFingerprint;

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

                long newId = insertItemSync(item);

                runOnUiThread(() -> {

                    lastSavedItemId = (int) newId;

                    lastSavedItemName = customName;

                    speakAndThen(getString(R.string.saved_as, customName), this::askIfAddReminder);

                });

            });

        }

    }

    /**

     * Inserts a SavedItem synchronously and returns its autogenerated itemId.

     * Needed so we can attach a reminder to the right row.

     */

    private long insertItemSync(SavedItem item) {

        try {

            return AppDatabase.getInstance(this).itemDao().insertAndGetId(item);

        } catch (Exception e) {

            Log.e("SmartScan", "insertAndGetId failed: " + e.getMessage());

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

            Log.e("SaveImage", "Failed: " + e.getMessage());

            return null;

        }

    }

    private void speak(String msg) {

        tts.speak(msg, TextToSpeech.QUEUE_FLUSH, null, null);

    }

    private void speakAndThen(String msg, Runnable action) {

        if (tts == null) return;

        tts.stop();

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
    protected void onPause() {
        super.onPause();
        // Stop any in-flight voice flows when app backgrounds
        if (reminderFlow != null) {
            reminderFlow.shutdown();
            reminderFlow = null;
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
