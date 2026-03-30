package com.example.smartsight;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Locale;

public class SmartScanActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private Button btnScan;

    private static final int CAMERA_PERMISSION_CODE = 100;

    private Handler handler = new Handler();
    private boolean isHolding = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smart_scan);

        previewView = findViewById(R.id.cameraPreview);
        btnScan = findViewById(R.id.btnScan);
        View root = findViewById(R.id.smartScanRoot);

        tts = new TextToSpeech(this, this);

        boolean isTalkBack = AccessibilityUtils.isTalkBackEnabled(this);

        // TalkBack announcement
        root.post(() -> {
            if (isTalkBack) {
                root.announceForAccessibility(
                        "Smart Scan ready. Double tap the scan button to scan."
                );
            }
        });

        if (isTalkBack) {
            // ✅ BUTTON MODE
            btnScan.setVisibility(View.VISIBLE);

            btnScan.setOnClickListener(v -> {
                if (imageCapture != null) {
                    captureImage();
                }
            });

        } else {
            // ✅ HOLD ANYWHERE MODE
            btnScan.setVisibility(View.GONE);

            root.setOnTouchListener((v, event) -> {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        isHolding = true;

                        handler.postDelayed(() -> {
                            if (isHolding && imageCapture != null) {
                                captureImage();
                            }
                        }, 2000); // 2 sec hold

                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isHolding = false;
                        return true;
                }

                return false;
            });
        }

        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);

        } else {
            startCamera();
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.getDefault());

            if (!AccessibilityUtils.isTalkBackEnabled(this)) {
                tts.speak(
                        "Smart Scan ready. Press and hold anywhere on the screen to scan.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                startCamera();
            }
        }
    }

    private void startCamera() {

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture
                );

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureImage() {

        imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {

                    @Override
                    public void onCaptureSuccess(ImageProxy image) {

                        if (AccessibilityUtils.isTalkBackEnabled(SmartScanActivity.this)) {

                            // TalkBack handles vibration itself
                            findViewById(R.id.smartScanRoot)
                                    .announceForAccessibility("Image captured");

                        } else {

                            // TTS + vibration
                            tts.speak("Image captured",
                                    TextToSpeech.QUEUE_FLUSH, null, null);

                            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

                            if (vibrator != null) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
                                } else {
                                    vibrator.vibrate(200);
                                }
                            }
                        }

                        image.close();
                    }

                    @Override
                    public void onError(ImageCaptureException exception) {
                        exception.printStackTrace();
                    }
                }
        );
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
