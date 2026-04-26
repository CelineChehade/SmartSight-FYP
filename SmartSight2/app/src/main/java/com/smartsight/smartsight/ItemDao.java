package com.example.smartsight;

import androidx.lifecycle.LiveData;
import androidx.room.*;

import java.util.List;

@Dao
public interface ItemDao {

    @Insert
    void insert(SavedItem item);

    @Insert
    long insertAndGetId(SavedItem item);

    @Update
    void update(SavedItem item);

    @Delete
    void delete(SavedItem item);

    @Query("SELECT * FROM saved_items ORDER BY scanDate DESC")
    LiveData<List<SavedItem>> getAllItems();

    @Query("SELECT * FROM saved_items")
    List<SavedItem> getAllItemsSync();

    @Query("UPDATE saved_items SET customName = :newName WHERE itemId = :itemId")
    void updateName(int itemId, String newName);

    // NEW: sync getter for single item by ID (used by BootReceiver)
    @Query("SELECT * FROM saved_items WHERE itemId = :itemId LIMIT 1")
    SavedItem getByIdSync(int itemId);
}
