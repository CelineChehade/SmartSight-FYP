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

        NotificationHelper.showReminder(context, reminderId, itemName);
        speakReminder(context, itemName);

        if (repeatType == null || "ONCE".equalsIgnoreCase(repeatType)) {
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

        long nextFire = computeNextFire(fireTime, repeatType);
        if (nextFire > 0) {
            ReminderScheduler.scheduleAt(
                    context, reminderId, itemId, itemName, repeatType, nextFire);

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

    private void speakReminder(Context context, String itemName) {
        final TextToSpeech[] ttsHolder = new TextToSpeech[1];

        ttsHolder[0] = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                TextToSpeech tts = ttsHolder[0];

                String lang = SettingsPrefs.getLanguage(context);
                Locale locale = "fr".equals(lang) ? Locale.FRENCH : Locale.US;
                tts.setLanguage(locale);

                float speed = SettingsPrefs.getSpeechSpeed(context);
                tts.setSpeechRate(speed);

                String message = context.getString(R.string.reminder_notification_message, itemName);

                Bundle params = new Bundle();
                tts.speak(message, TextToSpeech.QUEUE_FLUSH, params,
                        "reminder_" + System.currentTimeMillis());

                Log.d(TAG, "Speaking reminder: " + message);

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (ttsHolder[0] != null) {
                        ttsHolder[0].stop();
                        ttsHolder[0].shutdown();
                    }
                }, 5000);
            } else {
                Log.e(TAG, "TTS initialization failed");
            }
        });
    }

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
