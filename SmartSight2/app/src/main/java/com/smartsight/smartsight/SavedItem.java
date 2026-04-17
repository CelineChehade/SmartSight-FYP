package com.example.smartsight;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "saved_items")
public class SavedItem {

    @PrimaryKey(autoGenerate = true)
    public int itemId;

    public String customName;
    public String detectedName;
    public String category;
    public long scanDate;
    public Long expirationDate;
    public String imagePath;
    public String voiceNotePath;
    public boolean isMedication;

    // NEW: image fingerprint for object matching (perceptual hash string, or null for text items)
    public String imageFingerprint;
}
