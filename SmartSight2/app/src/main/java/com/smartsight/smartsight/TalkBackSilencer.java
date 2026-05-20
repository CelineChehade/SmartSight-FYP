package com.example.smartsight;

import android.app.Activity;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class TalkBackSilencer {

    public static void silence(Activity activity, TextToSpeech tts) {
        if (!"fr".equals(SettingsPrefs.getLanguage(activity.getApplicationContext()))) return;
        silenceTree(activity.getWindow().getDecorView());
    }

    private static void silenceTree(View v) {
        applySilenceDelegate(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                silenceTree(g.getChildAt(i));
            }
        }
    }

    private static void applySilenceDelegate(View v) {
        v.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setContentDescription("");
                info.setText("");
                info.setHintText("");
                info.setTooltipText("");
                info.setStateDescription("");
                info.setLabeledBy(null);
                // Suppress TalkBack's role announcement ("button", "bouton", etc.)
                info.setClassName(View.class.getName());
            }

            @Override
            public void onPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
                event.getText().clear();
                event.setContentDescription("");
            }

            @Override
            public void sendAccessibilityEventUnchecked(View host, AccessibilityEvent event) {
                event.getText().clear();
                event.setContentDescription("");
                super.sendAccessibilityEventUnchecked(host, event);
            }
        });
    }

    /**
     * Call AFTER silence() for each interactive view.
     *
     * When context is non-null (buttons): speaks "[label] button." / "[label] bouton."
     * using the localised talkback_button_suffix string.
     *
     * When context is null (list rows, non-buttons): speaks label as-is, no suffix.
     */
    public static void addFocusSpeech(View view, String label,
                                      TextToSpeech tts, Context context) {
        // Always resolve via getApplicationContext() so the language pref is read
        // from the real app prefs, not a createConfigurationContext() wrapper that
        // may not delegate SharedPreferences correctly on all Android versions.
        Context checkCtx = context != null
                ? context.getApplicationContext()
                : view.getContext().getApplicationContext();
        if (!"fr".equals(SettingsPrefs.getLanguage(checkCtx))) return;

        final String spoken = (context != null)
                ? label + " " + context.getString(R.string.talkback_button_suffix)
                : label;

        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setContentDescription("");
                info.setText("");
                // Space (not empty) so TalkBack uses this literal hint instead of
                // generating "double tap to activate" from the click action.
                info.setHintText(" ");
                info.setTooltipText("");
                info.setStateDescription("");
                info.setLabeledBy(null);
                // className=View suppresses TalkBack's "Button" / "bouton" role announcement.
                // Clickable flags are intentionally left as-is (set by super) so TalkBack's
                // double-tap and double-tap-and-hold dispatch continue to work.
                info.setClassName(View.class.getName());
            }

            @Override
            public void onPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
                event.getText().clear();
                event.setContentDescription("");
            }

            @Override
            public void sendAccessibilityEvent(View host, int eventType) {
                if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    // Only speak when nothing else is playing so startup auto-focus
                    // does not cut off the greeting mid-sentence.
                    if (tts != null && !TtsHelper.isTtsActive()) {
                        TtsHelper.speak(tts, spoken);
                    }
                    // Always call super so TalkBack records focus — required for
                    // double-tap and double-tap-and-hold activation.
                }
                super.sendAccessibilityEvent(host, eventType);
            }

            @Override
            public void sendAccessibilityEventUnchecked(View host, AccessibilityEvent event) {
                event.getText().clear();
                event.setContentDescription("");
                super.sendAccessibilityEventUnchecked(host, event);
            }
        });
    }
}
