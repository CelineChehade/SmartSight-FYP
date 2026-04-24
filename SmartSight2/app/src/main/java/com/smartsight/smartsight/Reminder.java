package com.example.smartsight;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import static androidx.room.ForeignKey.CASCADE;

@Entity(
        tableName = "reminders",
        foreignKeys = @ForeignKey(
                entity = SavedItem.class,
                parentColumns = "itemId",
                childColumns = "itemId",
                onDelete = CASCADE
        ),
        indices = {@Index("itemId")}
)
public class Reminder {

    @PrimaryKey(autoGenerate = true)
    public int reminderId;

    // Foreign key reference to SavedItem
    public int itemId;

    // Example values: "ONCE", "DAILY", "WEEKLY"
    public String repeatType;

    // Whether reminder is active
    public boolean isActive;

    // Time in milliseconds (System.currentTimeMillis())
    public long reminderTime;
}
