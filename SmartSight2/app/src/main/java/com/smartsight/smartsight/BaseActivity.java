package com.example.smartsight;

import android.content.Context;
import android.view.accessibility.AccessibilityEvent;

import androidx.appcompat.app.AppCompatActivity;

/**
 * All SmartSight activities extend this instead of AppCompatActivity.
 *
 * dispatchPopulateAccessibilityEvent intercepts events at the WINDOW level —
 * this is the only hook that also catches the window title and activity label
 * that TalkBack announces when an activity opens. Individual view content
 * descriptions cannot suppress that announcement, but this override can.
 *
 * When TalkBack is disabled this method is never invoked, so there is
 * zero impact on normal (non-accessibility) usage.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        // Apply saved locale for every activity automatically
        super.attachBaseContext(LocaleManager.wrap(newBase));
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (AccessibilityUtils.isTalkBackEnabled(this)) {
            // Wipe all text from the event — TalkBack tracks focus but speaks nothing
            event.getText().clear();
            event.setContentDescription("");
            return true; // consumed
        }
        return super.dispatchPopulateAccessibilityEvent(event);
    }
}