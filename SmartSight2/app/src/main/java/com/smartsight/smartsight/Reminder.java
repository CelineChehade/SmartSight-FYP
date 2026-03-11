package com.smartsight.smartsight;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

@Entity(
        tableName = "reminders",
        foreignKeys = @ForeignKey(
                entity = SavedItem.class,
                parentColumns = "itemId",
                childColumns = "itemId",
                onDelete = CASCADE
        )
)
public class Reminder {

    @PrimaryKey(autoGenerate = true)
    public int reminderId;

    public int itemId;
    public String repeatType;
    public boolean isActive;
    public long reminderTime;
}