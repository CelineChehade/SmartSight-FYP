package com.example.smartsight;

import androidx.lifecycle.LiveData;
import androidx.room.*;

import java.util.List;

@Dao
public interface ReminderDao {

    @Insert
    long insert(Reminder reminder);   // returns the new rowId

    @Update
    void update(Reminder reminder);

    @Delete
    void delete(Reminder reminder);

    @Query("SELECT * FROM reminders WHERE itemId = :itemId")
    LiveData<List<Reminder>> getRemindersForItem(int itemId);

    // NEW: synchronous getters for use in BroadcastReceiver / background threads
    @Query("SELECT * FROM reminders WHERE reminderId = :reminderId LIMIT 1")
    Reminder getByIdSync(int reminderId);

    @Query("SELECT * FROM reminders WHERE itemId = :itemId LIMIT 1")
    Reminder getFirstForItemSync(int itemId);

    @Query("SELECT * FROM reminders WHERE isActive = 1")
    List<Reminder> getAllActiveSync();

    @Query("DELETE FROM reminders WHERE itemId = :itemId")
    void deleteAllForItem(int itemId);
}
