package com.example.smartsight;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity extends AppCompatActivity {

    private AudioManager      audioManager;
    private AudioFocusRequest audioFocusRequest; // API 26+

    /**
     * Blocks ALL accessibility events from reaching TalkBack in French mode.
     * - onRequestSendAccessibilityEvent: intercepts child events travelling up the parent chain
     * - sendAccessibilityEvent / sendAccessibilityEventUnchecked: intercepts events
     *   sent directly to the decor root (e.g. TYPE_WINDOW_STATE_CHANGED)
     */
    private final View.AccessibilityDelegate frenchBlocker = new View.AccessibilityDelegate() {
        @Override
        public boolean onRequestSendAccessibilityEvent(ViewGroup host, View child,
                                                       AccessibilityEvent event) {
            return false; // drop every child event — TalkBack sees nothing
        }

        @Override
        public void sendAccessibilityEvent(View host, int eventType) {
            // swallow — do not forward to AccessibilityManager
        }

        @Override
        public void sendAccessibilityEventUnchecked(View host, AccessibilityEvent event) {
            // swallow — do not forward to AccessibilityManager
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleManager.wrap(newBase));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if ("fr".equals(SettingsPrefs.getLanguage(getApplicationContext()))) {
            applyFrenchBlock();
        } else {
            removeFrenchBlock(); // English: undo any French block and leave TalkBack alone
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        removeFrenchBlock(); // release audio focus when activity leaves screen
    }

    private void applyFrenchBlock() {
        // Blank window title so TalkBack cannot announce it via AccessibilityWindowInfo
        setTitle("");

        // Request audio focus so TalkBack ducks/stops speaking
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(change -> { /* hold focus */ })
                    .build();
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            //noinspection deprecation
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        }

        // Block all accessibility events from the decor root downward
        getWindow().getDecorView().setAccessibilityDelegate(frenchBlocker);
    }

    private void removeFrenchBlock() {
        // Remove blocking delegate — restores normal TalkBack event flow
        View decor = getWindow().getDecorView();
        if (decor != null && decor.getAccessibilityDelegate() == frenchBlocker) {
            decor.setAccessibilityDelegate(null);
        }

        // Release audio focus
        if (audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
                audioFocusRequest = null;
            } else {
                //noinspection deprecation
                audioManager.abandonAudioFocus(null);
            }
            audioManager = null;
        }
    }

    // onWindowFocusChanged intentionally removed.
    // It was calling TalkBackSilencer.silence(this, null) which overwrote
    // every addFocusSpeech delegate that activities set up in onInit(),
    // replacing them with plain silencing delegates that speak nothing.

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (AccessibilityUtils.isTalkBackEnabled(this)
                && "fr".equals(SettingsPrefs.getLanguage(getApplicationContext()))) {
            event.getText().clear();
            event.setContentDescription(null);
            return false;
        }
        return super.dispatchPopulateAccessibilityEvent(event);
    }
}
