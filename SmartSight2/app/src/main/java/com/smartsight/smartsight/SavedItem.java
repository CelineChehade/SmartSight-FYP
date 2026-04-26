package com.example.smartsight;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "saved_items")
public class SavedItem {

    @PrimaryKey(autoGenerate = true)
    public int itemId;

    public String customName;
    public String detectedName;
    public String category;          // "text" or "object"
    public long scanDate;
    public String imagePath;
    public boolean isMedication;
    public String imageFingerprint;  // perceptual hash for object matching

    // REMOVED: expirationDate (never used)
    // REMOVED: voiceNotePath (never used)
}
