package com.smartsight.smartsight;

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
}
