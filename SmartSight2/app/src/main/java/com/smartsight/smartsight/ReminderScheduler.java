package com.example.smartsight;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class ReminderScheduler {

    private static final String TAG = "ReminderScheduler";

    /**
     * Schedule (or re-schedule) a reminder to fire at the given absolute time.
     * If a pending alarm already exists with this reminderId, it is replaced.
     *
     * @param reminderId  unique per reminder (use Reminder.reminderId from DB)
     * @param itemId      the saved item this reminder belongs to
     * @param itemName    the custom name (shown in notification)
     * @param repeatType  "ONCE" | "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY"
     * @param fireTimeMs  absolute time in milliseconds (System.currentTimeMillis() based)
     */
    public static void scheduleAt(Context context,
                                  int reminderId,
                                  int itemId,
                                  String itemName,
                                  String repeatType,
                                  long fireTimeMs) {

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            Log.e(TAG, "AlarmManager unavailable");
            return;
        }

        PendingIntent pi = buildPendingIntent(
                context, reminderId, itemId, itemName, repeatType, fireTimeMs);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // setExactAndAllowWhileIdle survives Doze mode — important for reminders.
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireTimeMs, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, fireTimeMs, pi);
            }
            Log.d(TAG, "Scheduled reminderId=" + reminderId
                    + " at=" + fireTimeMs + " repeat=" + repeatType);
        } catch (SecurityException e) {
            // Android 12+ may deny SCHEDULE_EXACT_ALARM. Fallback to inexact.
            Log.w(TAG, "Exact alarm denied, falling back to inexact: " + e.getMessage());
            am.set(AlarmManager.RTC_WAKEUP, fireTimeMs, pi);
        }
    }

    /**
     * Cancel a scheduled reminder (e.g. user deleted it or the item).
     */
    public static void cancel(Context context, int reminderId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // Build a matching PendingIntent (same reminderId) to cancel.
        Intent intent = new Intent(context, ReminderReceiver.class);
        int flags = PendingIntent.FLAG_NO_CREATE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent existing = PendingIntent.getBroadcast(
                context, reminderId, intent, flags);

        if (existing != null) {
            am.cancel(existing);
            existing.cancel();
            Log.d(TAG, "Cancelled reminderId=" + reminderId);
        }
    }

    private static PendingIntent buildPendingIntent(Context context,
                                                    int reminderId,
                                                    int itemId,
                                                    String itemName,
                                                    String repeatType,
                                                    long fireTimeMs) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId);
        intent.putExtra(ReminderReceiver.EXTRA_ITEM_ID, itemId);
        intent.putExtra(ReminderReceiver.EXTRA_ITEM_NAME, itemName);
        intent.putExtra(ReminderReceiver.EXTRA_REPEAT_TYPE, repeatType);
        intent.putExtra(ReminderReceiver.EXTRA_FIRE_TIME, fireTimeMs);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, reminderId, intent, flags);
    }
}