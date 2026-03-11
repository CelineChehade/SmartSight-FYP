package com.smartsight.smartsight;


import androidx.lifecycle.LiveData;
import androidx.room.*;

import java.util.List;

@Dao
public interface NoteDao {

    @Insert
    void insert(SavedNote note);

    @Delete
    void delete(SavedNote note);

    @Query("SELECT * FROM saved_notes ORDER BY scanDate DESC")
    LiveData<List<SavedNote>> getAllNotes();
}