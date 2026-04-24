package com.example.smartsight;

import android.content.Context;
import android.provider.Settings;

public class AccessibilityUtils {

    public static boolean isTalkBackEnabled(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        return enabled != null && enabled.toLowerCase().contains("talkback");
    }
}
