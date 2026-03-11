package com.smartsight.smartsight;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                UserProfile.class,
                SavedItem.class,
                SavedNote.class,
                Reminder.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    // ===== DAO ACCESS =====

    public abstract ItemDao itemDao();
    public abstract NoteDao noteDao();
    public abstract ReminderDao reminderDao();
    public abstract UserDao userDao();


    // ===== SINGLETON INSTANCE =====

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(final Context context) {

        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {

                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "smartsight_db"
                            )
                            // Remove destructive migration in final version if you add migrations later
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }

        return INSTANCE;
    }
}