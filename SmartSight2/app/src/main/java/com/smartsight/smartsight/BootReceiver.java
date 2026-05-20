package com.example.smartsight;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * Fired when the phone boots. Re-schedules all active reminders because
 * Android wipes AlarmManager on reboot.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        Log.d(TAG, "Boot completed — re-scheduling reminders");

        // Query all active reminders and re-schedule them
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
                List<Reminder> activeReminders = db.reminderDao().getAllActiveSync();

                Log.d(TAG, "Found " + activeReminders.size() + " active reminders");

                for (Reminder r : activeReminders) {
                    // Get the item name so the notification shows it
                    SavedItem item = db.itemDao().getByIdSync(r.itemId);
                    if (item == null) {
                        Log.w(TAG, "Reminder " + r.reminderId + " has no matching item, skipping");
                        continue;
                    }

                    String itemName = item.customName != null ? item.customName : "";
                    ReminderScheduler.scheduleAt(
                            context,
                            r.reminderId,
                            r.itemId,
                            itemName,
                            r.repeatType,
                            r.reminderTime);

                    Log.d(TAG, "Re-scheduled reminder " + r.reminderId
                            + " for " + itemName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to re-schedule reminders: " + e.getMessage());
            }
        });
    }
}
