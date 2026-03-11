package com.smartsight.smartsight;


import androidx.lifecycle.LiveData;
import androidx.room.*;

import java.util.List;

@Dao
public interface ItemDao {

    @Insert
    void insert(SavedItem item);

    @Update
    void update(SavedItem item);

    @Delete
    void delete(SavedItem item);

    @Query("SELECT * FROM saved_items ORDER BY scanDate DESC")
    LiveData<List<SavedItem>> getAllItems();
}