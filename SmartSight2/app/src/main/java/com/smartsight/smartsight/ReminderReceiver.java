package com.example.smartsight;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executors;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderReceiver";

    // Intent extras
    public static final String EXTRA_REMINDER_ID = "extra_reminder_id";
    public static final String EXTRA_ITEM_ID = "extra_item_id";
    public static final String EXTRA_ITEM_NAME = "extra_item_name";
    public static final String EXTRA_REPEAT_TYPE = "extra_repeat_type";
    public static final String EXTRA_FIRE_TIME = "extra_fire_time";

    @Override
    public void onReceive(Context context, Intent intent) {
        final int reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, -1);
        final int itemId = intent.getIntExtra(EXTRA_ITEM_ID, -1);
        final String itemName = intent.getStringExtra(EXTRA_ITEM_NAME);
        final String repeatType = intent.getStringExtra(EXTRA_REPEAT_TYPE);
        final long fireTime = intent.getLongExtra(EXTRA_FIRE_TIME, 0L);

        Log.d(TAG, "Alarm fired: reminderId=" + reminderId
                + " item=" + itemName + " repeat=" + repeatType);

        if (reminderId < 0 || itemName == null) {
            Log.w(TAG, "Invalid alarm payload, ignoring.");
            return;
        }

        // 1) Show notification (with vibration + sound)
        NotificationHelper.showReminder(context, reminderId, itemName);

        // 2) SPEAK the reminder aloud using TTS
        speakReminder(context, itemName);

        // 3) Handle recurrence
        if (repeatType == null || "ONCE".equalsIgnoreCase(repeatType)) {
            // One-time reminder: mark inactive in DB
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    ReminderDao dao = AppDatabase.getInstance(context).reminderDao();
                    Reminder r = dao.getByIdSync(reminderId);
                    if (r != null) {
                        r.isActive = false;
                        dao.update(r);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to mark ONCE reminder inactive: " + e.getMessage());
                }
            });
            return;
        }

        // Recurring reminder: compute next fire time and re-schedule
        long nextFire = computeNextFire(fireTime, repeatType);
        if (nextFire > 0) {
            ReminderScheduler.scheduleAt(
                    context, reminderId, itemId, itemName, repeatType, nextFire);

            // Update DB with new fire time
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    ReminderDao dao = AppDatabase.getInstance(context).reminderDao();
                    Reminder r = dao.getByIdSync(reminderId);
                    if (r != null) {
                        r.reminderTime = nextFire;
                        dao.update(r);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to update reminderTime: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Speak the reminder aloud using TTS.
     * Uses array to hold TTS reference so it can be accessed in callback.
     */
    private void speakReminder(Context context, String itemName) {
        final TextToSpeech[] ttsHolder = new TextToSpeech[1];

        ttsHolder[0] = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                TextToSpeech tts = ttsHolder[0];

                // Apply user settings
                String lang = SettingsPrefs.getLanguage(context);
                Locale locale = "fr".equals(lang) ? Locale.FRENCH : Locale.US;
                tts.setLanguage(locale);

                float speed = SettingsPrefs.getSpeechSpeed(context);
                tts.setSpeechRate(speed);

                // Speak the reminder message
                String message = context.getString(R.string.reminder_notification_message, itemName);

                Bundle params = new Bundle();
                tts.speak(message, TextToSpeech.QUEUE_FLUSH, params, "reminder_" + System.currentTimeMillis());

                Log.d(TAG, "Speaking reminder: " + message);

                // Shutdown TTS after speaking (with delay)
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (ttsHolder[0] != null) {
                        ttsHolder[0].stop();
                        ttsHolder[0].shutdown();
                    }
                }, 5000); // 5 seconds should be enough
            } else {
                Log.e(TAG, "TTS initialization failed");
            }
        });
    }

    /**
     * Compute the next fire time based on the current one and repeat type.
     */
    private long computeNextFire(long currentFire, String repeatType) {
        if (currentFire <= 0) currentFire = System.currentTimeMillis();

        switch (repeatType.toUpperCase()) {
            case "DAILY":
                return currentFire + 24L * 60 * 60 * 1000;
            case "WEEKLY":
                return currentFire + 7L * 24 * 60 * 60 * 1000;
            case "MONTHLY": {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(currentFire);
                c.add(Calendar.MONTH, 1);
                return c.getTimeInMillis();
            }
            case "YEARLY": {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(currentFire);
                c.add(Calendar.YEAR, 1);
                return c.getTimeInMillis();
            }
            default:
                return -1L;
        }
    }
}
