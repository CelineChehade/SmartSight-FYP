package com.smartsight.smartsight;

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

    public void insertReminder(Reminder reminder) {
        executor.execute(() -> reminderDao.insert(reminder));
    }

    public void updateReminder(Reminder reminder) {
        executor.execute(() -> reminderDao.update(reminder));
    }

    public void deleteReminder(Reminder reminder) {
        executor.execute(() -> reminderDao.delete(reminder));
    }

    public LiveData<List<Reminder>> getRemindersForItem(int itemId) {
        return reminderDao.getRemindersForItem(itemId);
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
}