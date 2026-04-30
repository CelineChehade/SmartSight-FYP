package com.example.smartsight;

import android.app.Activity;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Silences TalkBack completely for an Activity while keeping swipe
 * navigation and focus ring working.
 *
 * STRATEGY
 * =========
 * 1. Override dispatchPopulateAccessibilityEvent() in the Activity to
 *    return true without adding any text → TalkBack gets empty events.
 *
 * 2. Set an AccessibilityDelegate on every view that:
 *    - Clears all text from every AccessibilityEvent before dispatch
 *    - Returns empty AccessibilityNodeInfo text/description
 *    This covers views that generate their own events independently.
 *
 * 3. For interactive views (buttons), register an onFocusChangeListener
 *    so our own TTS speaks the correct label when TalkBack focuses them.
 *
 * HOW TO USE
 * ===========
 * Step 1 — In your Activity, override dispatchPopulateAccessibilityEvent:
 *
 *   @Override
 *   public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
 *       if (AccessibilityUtils.isTalkBackEnabled(this)) {
 *           event.getText().clear();
 *           event.setContentDescription("");
 *           return true;   // consumed — TalkBack gets nothing to speak
 *       }
 *       return super.dispatchPopulateAccessibilityEvent(event);
 *   }
 *
 * Step 2 — After setContentView(), call:
 *   TalkBackSilencer.silence(this, tts);
 *
 * Step 3 — For buttons you want spoken in the correct language, call:
 *   TalkBackSilencer.addFocusSpeech(button, "Label to speak", tts);
 */
public class TalkBackSilencer {

    /**
     * Silence all views in this activity.
     * Call after setContentView() and after any views become VISIBLE.
     */
    public static void silence(Activity activity, TextToSpeech tts) {
        View root = activity.getWindow().getDecorView();
        silenceTree(root, tts);
    }

    private static void silenceTree(View v, TextToSpeech tts) {
        applyDelegate(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                silenceTree(g.getChildAt(i), tts);
            }
        }
    }

    private static void applyDelegate(View v) {
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {

            @Override
            public void onInitializeAccessibilityNodeInfo(View host,
                                                          AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                // Wipe every text field TalkBack might read
                info.setContentDescription("");
                info.setText("");
                info.setHintText("");
                info.setTooltipText("");
                info.setStateDescription("");
            }

            @Override
            public void onPopulateAccessibilityEvent(View host,
                                                     AccessibilityEvent event) {
                // Do NOT call super — prevents the view adding its own text
                event.getText().clear();
                event.setContentDescription("");
            }

            @Override
            public void sendAccessibilityEvent(View host, int eventType) {
                // Still send the event so TalkBack can track focus,
                // but strip all text first
                AccessibilityEvent e = AccessibilityEvent.obtain(eventType);
                e.getText().clear();
                e.setContentDescription("");
                super.sendAccessibilityEvent(host, eventType);
            }

            @Override
            public void sendAccessibilityEventUnchecked(View host,
                                                        AccessibilityEvent event) {
                event.getText().clear();
                event.setContentDescription("");
                super.sendAccessibilityEventUnchecked(host, event);
            }
        });
    }

    /**
     * Make TalkBack focus on this view speak `label` via our TTS
     * instead of the system default.
     * Call AFTER silence() so the focus listener isn't wiped.
     */
    public static void addFocusSpeech(View view, String label,
                                      TextToSpeech tts) {
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && tts != null) {
                TtsHelper.speak(tts, label);
            }
        });
    }
}