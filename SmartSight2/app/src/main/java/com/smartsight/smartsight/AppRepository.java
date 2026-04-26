package com.example.smartsight;

import android.content.Context;
import androidx.lifecycle.LiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppRepository {
    private final ItemDao itemDao;
    private final NoteDao noteDao;
    private final ReminderDao reminderDao;
    private final UserDao userDao;
    private final ExecutorService executor;

    // ==========================
    // Constructor
    // ==========================
    public AppRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        itemDao = db.itemDao();
        noteDao = db.noteDao();
        reminderDao = db.reminderDao();
        userDao = db.userDao();
        executor = Executors.newSingleThreadExecutor();
    }

    // =====================================================
    // ================= SAVED ITEMS ========================
    // =====================================================
    public void insertItem(SavedItem item) {
        executor.execute(() -> itemDao.insert(item));
    }

    public void updateItem(SavedItem item) {
        executor.execute(() -> itemDao.update(item));
    }

    public void deleteItem(SavedItem item) {
        executor.execute(() -> itemDao.delete(item));
    }

    public void updateItemName(int itemId, String newName) {
        executor.execute(() -> itemDao.updateName(itemId, newName));
    }

    public LiveData<List<SavedItem>> getAllItems() {
        return itemDao.getAllItems();
    }

    // =====================================================
    // ================= SAVED NOTES ========================
    // =====================================================
    public void insertNote(SavedNote note) {
        executor.execute(() -> noteDao.insert(note));
    }

    public void deleteNote(SavedNote note) {
        executor.execute(() -> noteDao.delete(note));
    }

    public LiveData<List<SavedNote>> getAllNotes() {
        return noteDao.getAllNotes();
    }

    // =====================================================
    // ================= REMINDERS ==========================
    // =====================================================
    /**
     * Insert a reminder and return its new ID via callback.
     * Callback runs on the background thread — use handler to post to UI if needed.
     */
    public void insertReminder(Reminder reminder, java.util.function.LongConsumer onInserted) {
        executor.execute(() -> {
            long newId = reminderDao.insert(reminder);
            if (onInserted != null) onInserted.accept(newId);
        });
    }

    public void updateReminder(Reminder reminder) {
        executor.execute(() -> reminderDao.update(reminder));
    }

    public void deleteReminder(Reminder reminder) {
        executor.execute(() -> reminderDao.delete(reminder));
    }

    public void deleteAllRemindersForItem(int itemId) {
        executor.execute(() -> reminderDao.deleteAllForItem(itemId));
    }

    public LiveData<List<Reminder>> getRemindersForItem(int itemId) {
        return reminderDao.getRemindersForItem(itemId);
    }

    /** Background thread. Returns null if none exists. */
    public void getFirstReminderForItem(int itemId,
                                        java.util.function.Consumer<Reminder> callback) {
        executor.execute(() -> {
            Reminder r = reminderDao.getFirstForItemSync(itemId);
            if (callback != null) callback.accept(r);
        });
    }

    // =====================================================
    // ================= USER PROFILE =======================
    // =====================================================
    public void insertUser(UserProfile user) {
        executor.execute(() -> userDao.insert(user));
    }

    public LiveData<UserProfile> getUserProfile() {
        return userDao.getUserProfile();
    }

    public void wipeAllData(android.content.Context context) {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);

            // Cancel all scheduled alarms before wiping DB
            List<Reminder> reminders = db.reminderDao().getAllActiveSync();
            for (Reminder r : reminders) {
                ReminderScheduler.cancel(context, r.reminderId);
            }

            db.clearAllTables();

            // Delete saved images folder
            java.io.File dir = new java.io.File(context.getFilesDir(), "saved_images");
            if (dir.exists()) {
                java.io.File[] files = dir.listFiles();
                if (files != null) for (java.io.File f : files) f.delete();
            }
        });
    }
}
