package com.example.smartsight;

import android.content.Context;
import android.view.accessibility.AccessibilityManager;

public class AccessibilityUtils {

    public static boolean isTalkBackEnabled(Context context) {
        AccessibilityManager am =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);

        return am != null && am.isEnabled() && am.isTouchExplorationEnabled();
    }
}
