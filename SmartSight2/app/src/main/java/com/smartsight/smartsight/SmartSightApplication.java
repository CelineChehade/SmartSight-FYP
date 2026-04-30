package com.example.smartsight;

import android.app.Application;
import android.content.Context;

/**
 * Custom Application class.
 *
 * WHY THIS IS NEEDED
 * ==================
 * Android can reset the app-level locale between activity recreations,
 * especially after FLAG_ACTIVITY_CLEAR_TASK restarts or after the system
 * kills and restores the process. Without this class, the locale set in
 * attachBaseContext() of individual activities could be overridden by the
 * system's default locale before the next activity starts — causing some
 * UI strings to appear in English even when French is selected.
 *
 * By overriding attachBaseContext() here, the saved language is applied
 * at the Application level (the very first context Android creates), so
 * every Activity, Service, and BroadcastReceiver that inherits from it
 * gets the correct locale from the start.
 *
 * MANIFEST: Add android:name=".SmartSightApplication" to <application> tag.
 */
public class SmartSightApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        // Apply the saved language before anything else in the app initialises
        super.attachBaseContext(LocaleManager.wrap(base));
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Re-apply our locale if the system tries to reset it
        // (e.g. user changes system language while app is running)
        LocaleManager.wrap(this);
    }
}