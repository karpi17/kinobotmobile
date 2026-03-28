package com.asystent.kinowy.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.asystent.kinowy.models.Loss;
import com.asystent.kinowy.models.MonthlyReport;
import com.asystent.kinowy.models.Shift;

import com.asystent.kinowy.models.Tip;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Główna baza danych Room dla aplikacji Asystent Kinowy.
 * Singleton — dostęp przez {@link #getInstance(Context)}.
 */
@Database(entities = {Shift.class, Loss.class, Tip.class, MonthlyReport.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "asystent_kinowy_db";
    private static volatile AppDatabase INSTANCE;

    // --- Abstrakcyjne metody DAO ---

    public abstract ShiftDao shiftDao();

    public abstract LossDao lossDao();

    public abstract TipDao tipDao();

    public abstract MonthlyReportDao monthlyReportDao();

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE shifts ADD COLUMN category TEXT DEFAULT 'UNKNOWN'");
            database.execSQL("CREATE TABLE IF NOT EXISTS `monthly_reports` (`monthYear` TEXT NOT NULL, `calculatedHours` REAL NOT NULL, `paperHours` REAL NOT NULL, `calculatedSalary` REAL NOT NULL, `actualSalary` REAL NOT NULL, PRIMARY KEY(`monthYear`))");
        }
    };

    // --- Singleton ---

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    ).addMigrations(MIGRATION_3_4)
                     .fallbackToDestructiveMigration()
                     .build();
                }
            }
        }
        return INSTANCE;
    }
}
