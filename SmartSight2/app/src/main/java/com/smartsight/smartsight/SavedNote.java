package com.example.smartsight;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "saved_notes")
public class SavedNote {

    @PrimaryKey(autoGenerate = true)
    public int noteId;

    public String customName;
    public String extractedText;   // OCR text OR object label
    public String scanType;        // "text" or "object"
    public long scanDate;
    public String language;
}
