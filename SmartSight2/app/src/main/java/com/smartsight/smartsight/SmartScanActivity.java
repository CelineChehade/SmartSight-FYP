package com.example.smartsight;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
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

/**
 * Extends BaseActivity (not AppCompatActivity directly) so that:
 * 1. LocaleManager.wrap() is applied automatically via attachBaseContext.
 * 2. dispatchPopulateAccessibilityEvent() silences TalkBack window announcement.
 */
public class SmartScanActivity extends BaseActivity implements TextToSpeech.OnInitListener {

    private static final String TAG          = "SmartScanActivity";
    private static final long   STT_DELAY_MS = 1200;
    private static final int    MAX_RETRIES  = 3;

    private TextToSpeech   tts;
    private PreviewView    previewView;
    private ImageCapture   imageCapture;
    private TextView       btnScan;
    private ObjectDetector objectDetector;
    private ImageFingerprintExtractor fingerprintExtractor;
    private ItemMatcher    itemMatcher;
    private ItemViewModel  itemViewModel;
    private NoteViewModel  noteViewModel;
    private AppRepository  repository;
    private ReminderVoiceFlow reminderFlow;

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int AUDIO_PERMISSION_CODE  = 101;

    private final Handler  handler            = new Handler(Looper.getMainLooper());
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    private boolean isHolding  = false;
    private int     retryCount = 0;

    // Tracks the last prompt spoken so we can re-ask it on STT failure
    private String lastPrompt = null;

    private enum SaveState { IDLE, ASK_WHICH_OBJECT, ASK_TEXT_OR_OBJECT, ASK_SAVE, ASK_NAME, ASK_REMINDER }
    private SaveState saveState = SaveState.IDLE;

    private enum ScanType { TEXT, OBJECT }
    private ScanType lastScanType;

    private String               lastScannedText;
    private List<DetectedObject> detectedObjects = new ArrayList<>();
    private DetectedObject       chosenObject;
    private Bitmap               lastCapturedBitmap;
    private int                  lastSavedItemId   = -1;
    private String               lastSavedItemName;

    private static class DetectedObject {
        String label; Bitmap crop; String fingerprint;
        DetectedObject(String l, Bitmap c, String f) { label=l; crop=c; fingerprint=f; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SettingsPrefs.isHighContrast(this))
            setTheme(android.R.style.Theme_Black_NoTitleBar);

        setContentView(R.layout.activity_smart_scan);
        TalkBackSilencer.silence(this, null);

        previewView = findViewById(R.id.cameraPreview);
        btnScan     = findViewById(R.id.btnScan);

        tts = new TextToSpeech(this, this);
        initObjectDetector();
        fingerprintExtractor = new ImageFingerprintExtractor(this);
        itemMatcher          = new ItemMatcher(this, fingerprintExtractor);
        itemViewModel        = new ViewModelProvider(this).get(ItemViewModel.class);
        noteViewModel        = new ViewModelProvider(this).get(NoteViewModel.class);
        repository           = new AppRepository(this);

        SharedSpeechRecognizer.init(this);
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
            TalkBackSilencer.silence(this, tts);
            boolean talkBackOn = AccessibilityUtils.isTalkBackEnabled(this);
            if (talkBackOn) {
                TalkBackSilencer.addFocusSpeech(
                        btnScan, getString(R.string.scan_button_label), tts, this);
            }
            // Only speak via app TTS if French (TalkBack silenced) or TalkBack is off.
            // In English + TalkBack, QUEUE_FLUSH would cut off TalkBack's own announcement.
            if ("fr".equals(SettingsPrefs.getLanguage(this)) || !talkBackOn) {
                TtsHelper.speak(tts, talkBackOn
                        ? getString(R.string.instruction_talkback_scan)
                        : getString(R.string.instruction_non_talkback_scan));
            }
        }
    }

    private void setupScanUI() {
        boolean tb   = AccessibilityUtils.isTalkBackEnabled(this);
        View    root = findViewById(R.id.smartScanRoot);
        if (tb) {
            btnScan.setVisibility(View.VISIBLE);
            btnScan.setContentDescription(getString(R.string.scan_button_description));
            btnScan.setOnClickListener(v -> {
                if (saveState == SaveState.IDLE && imageCapture != null) captureImage();
            });
            // Re-apply on resume in case the delegate was cleared (tts may be null on first
            // pass through onCreate; onInit() handles that case separately)
            if (tts != null) {
                TalkBackSilencer.addFocusSpeech(
                        btnScan, getString(R.string.scan_button_label), tts, this);
            }
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

    // ── Permissions ──────────────────────────────────────────────────────────

    private void requestPermissionsIfNeeded() {
        boolean camOk = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
        boolean micOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        if (!camOk) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else if (!micOk) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_CODE);
        } else {
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);

        if (code == CAMERA_PERMISSION_CODE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
                // Now ask for mic if not already granted
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            AUDIO_PERMISSION_CODE);
                }
            } else {
                // Camera denied — cannot function without it
                TtsHelper.speakThen(tts,
                        getString(R.string.permission_camera_denied_go_to_settings),
                        () -> {
                            Intent intent = new Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", getPackageName(), null));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        });
            }
        }

        if (code == AUDIO_PERMISSION_CODE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                // Mic granted — nothing extra to do
            } else {
                // Mic denied — voice commands won't work
                TtsHelper.speakThen(tts,
                        getString(R.string.permission_mic_denied_go_to_settings),
                        () -> {
                            Intent intent = new Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", getPackageName(), null));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        });
            }
        }
    }

    // ── Object detector ──────────────────────────────────────────────────────

    private void initObjectDetector() {
        try {
            objectDetector = ObjectDetector.createFromFileAndOptions(
                    this, "object_detection.tflite",
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setMaxResults(5)
                            .setScoreThreshold(0.4f)
                            .build());
        } catch (Exception e) {
            Log.e(TAG, "Model load failed: " + e.getMessage());
        }
    }

    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider p = ProcessCameraProvider.getInstance(this).get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();
                p.unbindAll();
                p.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
            } catch (Exception e) {
                Log.e(TAG, "Camera error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ── Capture & process ────────────────────────────────────────────────────

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void captureImage() {
        TtsHelper.speak(tts, getString(R.string.scanning));
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        android.media.Image mediaImage = image.getImage();
                        int    rot     = image.getImageInfo().getRotationDegrees();
                        Bitmap bm      = imageProxyToBitmap(image);
                        Bitmap rotated = rotateBitmap(bm, rot);

                        if (mediaImage != null) {
                            processBothTextAndObjects(
                                    InputImage.fromMediaImage(mediaImage, rot), rotated);
                        } else if (rotated != null) {
                            processBothTextAndObjects(
                                    InputImage.fromBitmap(rotated, 0), rotated);
                        } else {
                            runOnUiThread(() ->
                                    TtsHelper.speak(tts, getString(R.string.capture_failed)));
                        }
                        image.close();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        runOnUiThread(() ->
                                TtsHelper.speak(tts, getString(R.string.capture_failed)));
                    }
                });
    }

    private void processBothTextAndObjects(InputImage inputImage, Bitmap bitmap) {
        final String[]             detectedText = {null};
        final List<DetectedObject> foundObjects = new ArrayList<>();
        final int[] pending = {2};

        Runnable checkDone = () -> {
            pending[0]--;
            if (pending[0] == 0) {
                runOnUiThread(() -> checkScanComplete(detectedText[0], foundObjects, bitmap));
            }
        };

        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(inputImage)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        String t = task.getResult().getText().trim();
                        if (!t.isEmpty()) detectedText[0] = t;
                    }
                    checkDone.run();
                });

        backgroundExecutor.execute(() -> {
            try {
                if (objectDetector != null) {
                    for (Detection d : objectDetector.detect(TensorImage.fromBitmap(bitmap))) {
                        if (d.getCategories().isEmpty()) continue;
                        String label = d.getCategories().get(0).getLabel();
                        RectF  boxF  = d.getBoundingBox();
                        Rect   box   = new Rect(
                                (int) boxF.left, (int) boxF.top,
                                (int) boxF.right, (int) boxF.bottom);
                        Bitmap crop  = ItemMatcher.cropToBoundingBox(bitmap, box);
                        foundObjects.add(new DetectedObject(
                                label, crop,
                                fingerprintExtractor.extractFingerprint(crop)));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Detection failed: " + e.getMessage());
            }
            checkDone.run();
        });
    }

    private void checkScanComplete(String text, List<DetectedObject> objects, Bitmap bm) {
        lastCapturedBitmap = bm;
        lastScannedText    = text;
        detectedObjects    = new ArrayList<>(objects);

        boolean hasText    = text != null && !text.isEmpty();
        boolean hasObjects = !objects.isEmpty();

        if (!hasText && !hasObjects) {
            TtsHelper.speak(tts, getString(R.string.couldnt_identify));
            saveState = SaveState.IDLE;
            return;
        }
        vibrate();

        if (hasText) {
            backgroundExecutor.execute(() -> {
                SavedItem match = itemMatcher.matchText(text);
                runOnUiThread(() -> {
                    if (match != null) {
                        String name = match.customName != null ? match.customName : "";
                        TtsHelper.speak(tts,
                                getString(R.string.this_is_your, name));
                        saveState = SaveState.IDLE;
                    } else {
                        checkObjectMatches(true, hasObjects);
                    }
                });
            });
        } else {
            checkObjectMatches(false, hasObjects);
        }
    }

    private void checkObjectMatches(boolean hasText, boolean hasObjects) {
        if (!hasObjects) {
            lastScanType = ScanType.TEXT;
            saveState    = SaveState.ASK_SAVE;
            speakThenListen(lastScannedText + ". " + getString(R.string.ask_save));
            return;
        }

        backgroundExecutor.execute(() -> {
            List<SavedItem>      matched   = new ArrayList<>();
            List<DetectedObject> unmatched = new ArrayList<>();
            for (DetectedObject obj : detectedObjects) {
                SavedItem m = itemMatcher.matchObject(obj.crop, obj.label);
                if (m != null) matched.add(m);
                else unmatched.add(obj);
            }

            runOnUiThread(() -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < matched.size(); i++) {
                    if (sb.length() > 0) sb.append(". ");
                    String n = matched.get(i).customName != null ? matched.get(i).customName : "";
                    sb.append(getString(R.string.this_is_your, n));
                }
                if (!unmatched.isEmpty()) {
                    if (sb.length() > 0) sb.append(". ");
                    String firstLabel = LabelTranslator.translateForTts(
                            SmartScanActivity.this, unmatched.get(0).label);
                    sb.append(getString(R.string.i_see_a, firstLabel));
                    for (int i = 1; i < unmatched.size(); i++) {
                        String lbl = LabelTranslator.translateForTts(
                                SmartScanActivity.this, unmatched.get(i).label);
                        sb.append(", ").append(getString(R.string.and_a, lbl));
                    }
                }

                String announcement = sb.toString();

                if (!matched.isEmpty() && unmatched.isEmpty()) {
                    TtsHelper.speak(tts, announcement);
                    saveState = SaveState.IDLE;
                } else if (unmatched.size() > 1) {
                    saveState = SaveState.ASK_WHICH_OBJECT;
                    speakThenListen(announcement + ". "
                            + getString(R.string.ask_which_object));
                } else if (unmatched.size() == 1 && hasText) {
                    chosenObject = unmatched.get(0);
                    saveState    = SaveState.ASK_TEXT_OR_OBJECT;
                    speakThenListen(announcement + ". "
                            + getString(R.string.ask_text_or_object));
                } else {
                    chosenObject = unmatched.get(0);
                    lastScanType = ScanType.OBJECT;
                    saveState    = SaveState.ASK_SAVE;
                    speakThenListen(announcement + ". " + getString(R.string.ask_save));
                }
            });
        });
    }

    // ── STT helpers ───────────────────────────────────────────────────────────

    /**
     * Speak a prompt, record it as the "last prompt" for re-asking on failure,
     * then start listening after TTS finishes + STT_DELAY_MS buffer.
     */
    private void speakThenListen(String msg) {
        retryCount = 0;
        lastPrompt = msg;   // remember so we can re-ask on STT error
        TtsHelper.speakThen(tts, msg,
                () -> handler.postDelayed(this::startListeningOnce, STT_DELAY_MS));
    }

    private void startListeningOnce() {
        SharedSpeechRecognizer.get().listen(this, result -> {
            if (result.success) {
                retryCount = 0;
                // For name capture only the top result matters (free-form text).
                // For every command state, join all hypotheses so one-syllable words
                // like "yes"/"no" that land in a lower-confidence hypothesis still match.
                String text = (saveState == SaveState.ASK_NAME)
                        ? result.text
                        : String.join(" ", result.allResults);
                handleSpeechResult(text);
            } else {
                handleListenError(result.errorCode);
            }
        });
    }

    /**
     * On STT failure, silently retry up to MAX_RETRIES times.
     * After all retries are exhausted, say "I didn't catch that" then
     * RE-ASK the exact same question the user was on — not just a bare retry.
     */
    private void handleListenError(int errorCode) {
        boolean isSoft = errorCode == SpeechRecognizer.ERROR_NO_MATCH
                || errorCode == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || errorCode == SpeechRecognizer.ERROR_CLIENT;

        if (isSoft && retryCount < MAX_RETRIES) {
            retryCount++;
            handler.postDelayed(this::startListeningOnce, STT_DELAY_MS);
        } else {
            retryCount = 0;
            // Re-ask the original question (not just "try again")
            String reAsk = getString(R.string.didnt_catch)
                    + (lastPrompt != null ? " " + lastPrompt : "");
            speakThenListen(reAsk);
        }
    }

    // ── Speech result handler ─────────────────────────────────────────────────

    private void handleSpeechResult(String spokenText) {
        String cleaned = spokenText.toLowerCase().trim();
        Log.d(TAG, "State=" + saveState + " Heard=\"" + cleaned + "\"");

        switch (saveState) {
            case ASK_WHICH_OBJECT:
                for (DetectedObject obj : detectedObjects) {
                    String tr = LabelTranslator.translate(this, obj.label);
                    if (cleaned.contains(obj.label.toLowerCase())
                            || cleaned.contains(tr.toLowerCase())) {
                        chosenObject = obj;
                        if (lastScannedText != null && !lastScannedText.isEmpty()) {
                            saveState = SaveState.ASK_TEXT_OR_OBJECT;
                            speakThenListen(getString(R.string.ask_text_or_object));
                        } else {
                            lastScanType = ScanType.OBJECT;
                            saveState    = SaveState.ASK_SAVE;
                            speakThenListen(getString(R.string.ask_save));
                        }
                        return;
                    }
                }
                speakThenListen(getString(R.string.please_say_which_object));
                break;

            case ASK_TEXT_OR_OBJECT:
                if (containsAny(cleaned, "text","texte","words","writing","mots","écriture")) {
                    lastScanType = ScanType.TEXT;
                    saveState    = SaveState.ASK_SAVE;
                    speakThenListen(lastScannedText + ". " + getString(R.string.ask_save));
                } else if (containsAny(cleaned, "object","objet","thing","item","chose","image")) {
                    lastScanType = ScanType.OBJECT;
                    saveState    = SaveState.ASK_SAVE;
                    speakThenListen(getString(R.string.ask_save));
                } else {
                    speakThenListen(getString(R.string.please_say_text_or_object));
                }
                break;

            case ASK_SAVE:
                if (TtsHelper.isYes(cleaned)) {
                    saveState = SaveState.ASK_NAME;
                    speakThenListen(getString(R.string.ask_name_for_item));
                } else if (TtsHelper.isNo(cleaned)) {
                    saveState = SaveState.IDLE;
                    TtsHelper.speak(tts, getString(R.string.not_saving));
                } else {
                    speakThenListen(getString(R.string.please_say_yes_no));
                }
                break;

            case ASK_NAME:
                if (spokenText.trim().isEmpty()) {
                    speakThenListen(getString(R.string.didnt_catch_name));
                } else {
                    saveItem(spokenText.trim());
                }
                break;

            case ASK_REMINDER:
                if (TtsHelper.isYes(cleaned)) {
                    startReminderFlow();
                } else if (TtsHelper.isNo(cleaned)) {
                    saveState = SaveState.IDLE;
                    TtsHelper.speak(tts, getString(R.string.saved_as, lastSavedItemName));
                } else {
                    speakThenListen(getString(R.string.please_say_yes_no));
                }
                break;

            default:
                break;
        }
    }

    // ── Reminder flow ─────────────────────────────────────────────────────────

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
        if (lastSavedItemId < 0) {
            runOnUiThread(() -> {
                saveState = SaveState.IDLE;
                TtsHelper.speak(tts, getString(R.string.reminder_failed));
            });
            return;
        }
        final int    idSnap   = lastSavedItemId;
        final String nameSnap = lastSavedItemName;
        Reminder r = new Reminder();
        r.itemId       = idSnap;
        r.repeatType   = repeatType;
        r.isActive     = true;
        r.reminderTime = reminderTimeMs;
        repository.insertReminder(r, newId -> {
            ReminderScheduler.scheduleAt(SmartScanActivity.this,
                    (int) newId, idSnap, nameSnap, repeatType, reminderTimeMs);
            runOnUiThread(() -> {
                saveState = SaveState.IDLE;
                TtsHelper.speak(tts, getString(R.string.reminder_saved));
            });
        });
    }

    // ── Save item ─────────────────────────────────────────────────────────────

    private void saveItem(String customName) {
        long now = System.currentTimeMillis();
        if (lastScanType == ScanType.TEXT) {
            SavedItem item = new SavedItem();
            item.customName   = customName;
            item.detectedName = lastScannedText;
            item.category     = "text";
            item.scanDate     = now;
            item.isMedication = false;
            backgroundExecutor.execute(() -> {
                long newId = insertItemSync(item);
                SavedNote note = new SavedNote();
                note.extractedText = lastScannedText;
                note.scanDate      = now;
                note.language      = Locale.getDefault().toString();
                AppDatabase.getInstance(this).noteDao().insert(note);
                runOnUiThread(() -> {
                    lastSavedItemId   = (int) newId;
                    lastSavedItemName = customName;
                    saveState         = SaveState.ASK_REMINDER;
                    speakThenListen(getString(R.string.saved_as, customName)
                            + ". " + getString(R.string.ask_add_reminder));
                });
            });
        } else if (lastScanType == ScanType.OBJECT && chosenObject != null) {
            final Bitmap bm = lastCapturedBitmap;
            backgroundExecutor.execute(() -> {
                String imagePath = saveBitmapToInternalStorage(bm, now);
                SavedItem item = new SavedItem();
                item.customName       = customName;
                item.detectedName     = chosenObject.label;
                item.category         = "object";
                item.scanDate         = now;
                item.isMedication     = false;
                item.imagePath        = imagePath;
                item.imageFingerprint = chosenObject.fingerprint;
                long newId = insertItemSync(item);
                runOnUiThread(() -> {
                    lastSavedItemId   = (int) newId;
                    lastSavedItemName = customName;
                    saveState         = SaveState.ASK_REMINDER;
                    speakThenListen(getString(R.string.saved_as, customName)
                            + ". " + getString(R.string.ask_add_reminder));
                });
            });
        }
    }

    private long insertItemSync(SavedItem item) {
        try {
            return AppDatabase.getInstance(this).itemDao().insertAndGetId(item);
        } catch (Exception e) {
            Log.e(TAG, "insert failed: " + e.getMessage());
            return -1;
        }
    }

    private String saveBitmapToInternalStorage(Bitmap bm, long ts) {
        if (bm == null) return null;
        try {
            File dir = new File(getFilesDir(), "saved_images");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "img_" + ts + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(f)) {
                bm.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                fos.flush();
            }
            return f.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private boolean containsAny(String s, String... needles) {
        for (String n : needles) if (s.contains(n)) return true;
        return false;
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            java.nio.ByteBuffer buf = image.getPlanes()[0].getBuffer();
            byte[] b = new byte[buf.remaining()];
            buf.get(b);
            return BitmapFactory.decodeByteArray(b, 0, b.length);
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap rotateBitmap(Bitmap bm, int deg) {
        if (bm == null || deg == 0) return bm;
        Matrix m = new Matrix();
        m.postRotate(deg);
        return Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
    }

    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(android.content.Context.VIBRATOR_SERVICE);
        if (v == null) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(200);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onPause() {
        super.onPause();
        SharedSpeechRecognizer.get().cancel();
        if (reminderFlow != null) { reminderFlow.shutdown(); reminderFlow = null; }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (objectDetector != null) objectDetector.close();
        if (fingerprintExtractor != null) fingerprintExtractor.close();
        if (reminderFlow != null) reminderFlow.shutdown();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
