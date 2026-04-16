package com.example.smartsight;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {UserProfile.class, SavedItem.class, SavedNote.class, Reminder.class},
        version = 4,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract ItemDao itemDao();
    public abstract NoteDao noteDao();
    public abstract ReminderDao reminderDao();
    public abstract UserDao userDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    ctx.getApplicationContext(),
                                    AppDatabase.class,
                                    "smartsight_db"
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_itemId ON reminders(itemId)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_items_scanDate ON saved_items(scanDate)");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_notes_scanDate ON saved_notes(scanDate)");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE saved_notes ADD COLUMN customName TEXT");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE saved_notes ADD COLUMN scanType TEXT");
        }
    };
}
