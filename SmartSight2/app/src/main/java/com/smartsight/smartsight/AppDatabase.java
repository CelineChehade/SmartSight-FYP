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
        version = 5,
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
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                            .fallbackToDestructiveMigration()  // Keep for development; remove for production
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
            db.execSQL("ALTER TABLE saved_items ADD COLUMN imageFingerprint TEXT");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // This migration added customName + scanType to saved_notes, but we never used them
            // Keeping the migration for users who already have v4 DB
            db.execSQL("ALTER TABLE saved_notes ADD COLUMN customName TEXT");
            db.execSQL("ALTER TABLE saved_notes ADD COLUMN scanType TEXT");
        }
    };

    // NEW: Drop unused columns from saved_items and saved_notes
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            // SQLite doesn't support DROP COLUMN directly, so we recreate tables

            // SavedItems: drop expirationDate, voiceNotePath
            db.execSQL("CREATE TABLE saved_items_new (" +
                    "itemId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "customName TEXT, " +
                    "detectedName TEXT, " +
                    "category TEXT, " +
                    "scanDate INTEGER NOT NULL, " +
                    "imagePath TEXT, " +
                    "isMedication INTEGER NOT NULL, " +
                    "imageFingerprint TEXT)");
            db.execSQL("INSERT INTO saved_items_new SELECT itemId, customName, detectedName, category, scanDate, imagePath, isMedication, imageFingerprint FROM saved_items");
            db.execSQL("DROP TABLE saved_items");
            db.execSQL("ALTER TABLE saved_items_new RENAME TO saved_items");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_items_scanDate ON saved_items(scanDate)");

            // SavedNotes: drop customName, scanType
            db.execSQL("CREATE TABLE saved_notes_new (" +
                    "noteId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "extractedText TEXT, " +
                    "scanDate INTEGER NOT NULL, " +
                    "language TEXT)");
            db.execSQL("INSERT INTO saved_notes_new SELECT noteId, extractedText, scanDate, language FROM saved_notes");
            db.execSQL("DROP TABLE saved_notes");
            db.execSQL("ALTER TABLE saved_notes_new RENAME TO saved_notes");
            db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_notes_scanDate ON saved_notes(scanDate)");
        }
    };
}
