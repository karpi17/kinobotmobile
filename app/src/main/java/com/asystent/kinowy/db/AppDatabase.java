package com.asystent.kinowy.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.asystent.kinowy.models.ActiveEmployee;
import com.asystent.kinowy.models.GlobalShift;
import com.asystent.kinowy.models.Loss;
import com.asystent.kinowy.models.MonthlyReport;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.models.Tip;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Główna baza danych Room dla aplikacji Asystent Kinowy.
 * Singleton — dostęp przez {@link #getInstance(Context)}.
 *
 * Historia wersji:
 *  v3 → v4 : dodano kolumnę `category` w shifts + tabela monthly_reports
 *  v5 → v6 : (brak oficjalnej migracji — destructive fallback)
 *  v5 → v6 : dodano kolumnę `is_replacement` w shifts
 *  v6 → v7 : dodano kolumny `is_closing_shift` i `closing_crew` w shifts
 *  v7 → v8 : dodano tabelę `active_employees` (słownik pracowników)
 *  v8 → v9 : dodano tabelę `global_shifts` (globalny grafik ekipy)
 */
@Database(
    entities = {Shift.class, Loss.class, Tip.class, MonthlyReport.class, ActiveEmployee.class, GlobalShift.class},
    version = 12,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "asystent_kinowy_db";
    private static volatile AppDatabase INSTANCE;

    // -------------------------------------------------------------------------
    // Abstrakcyjne metody DAO
    // -------------------------------------------------------------------------

    public abstract ShiftDao shiftDao();

    public abstract LossDao lossDao();

    public abstract TipDao tipDao();

    public abstract MonthlyReportDao monthlyReportDao();

    public abstract EmployeeDao employeeDao();

    public abstract GlobalShiftDao globalShiftDao();

    // -------------------------------------------------------------------------
    // Migracje
    // -------------------------------------------------------------------------

    /**
     * v3 → v4
     * Dodaje kolumnę `category` do shifts i tworzy tabelę monthly_reports.
     */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "ALTER TABLE shifts ADD COLUMN category TEXT DEFAULT 'UNKNOWN'"
            );
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `monthly_reports` (" +
                "`monthYear` TEXT NOT NULL, " +
                "`calculatedHours` REAL NOT NULL, " +
                "`paperHours` REAL NOT NULL, " +
                "`calculatedSalary` REAL NOT NULL, " +
                "`actualSalary` REAL NOT NULL, " +
                "PRIMARY KEY(`monthYear`))"
            );
        }
    };

    /**
     * v5 → v6
     * Dodaje kolumnę `is_replacement` do shifts.
     * Każda kolumna wymaga osobnego ALTER TABLE w SQLite.
     */
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            // Pobieramy listę wszystkich kolumn w tabeli 'shifts'
            android.database.Cursor cursor = database.query("PRAGMA table_info(shifts)");
            boolean hasIsReplacement = false;
            
            int nameIndex = cursor.getColumnIndex("name");
            if (nameIndex != -1) {
                while (cursor.moveToNext()) {
                    String columnName = cursor.getString(nameIndex);
                    if ("is_replacement".equals(columnName)) {
                        hasIsReplacement = true;
                        break;
                    }
                }
            }
            cursor.close();

            // Jeśli kolumny nie ma, dopiero wtedy wykonujemy ALTER TABLE
            if (!hasIsReplacement) {
                database.execSQL(
                    "ALTER TABLE shifts ADD COLUMN is_replacement INTEGER NOT NULL DEFAULT 0"
                );
            }
        }
    };

    /**
     * v6 → v7
     * Dodaje dwie nowe kolumny do obsługi zmian zamknięcia kina.
     * SQLite nie pozwala na dodanie wielu kolumn w jednym ALTER TABLE —
     * wymagane są dwa osobne zapytania.
     */
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            android.database.Cursor cursor = database.query("PRAGMA table_info(shifts)");
            boolean hasIsClosingShift = false;
            boolean hasClosingCrew = false;
            
            int nameIndex = cursor.getColumnIndex("name");
            if (nameIndex != -1) {
                while (cursor.moveToNext()) {
                    String columnName = cursor.getString(nameIndex);
                    if ("is_closing_shift".equals(columnName)) hasIsClosingShift = true;
                    if ("closing_crew".equals(columnName)) hasClosingCrew = true;
                }
            }
            cursor.close();

            // Dodajemy tylko to, czego brakuje
            if (!hasIsClosingShift) {
                database.execSQL(
                    "ALTER TABLE shifts ADD COLUMN is_closing_shift INTEGER NOT NULL DEFAULT 0"
                );
            }
            if (!hasClosingCrew) {
                database.execSQL(
                    "ALTER TABLE shifts ADD COLUMN closing_crew TEXT"
                );
            }
        }
    };

    /**
     * v7 → v8
     * Tworzy tabelę `active_employees` — słownik pracowników
     * zasilany automatycznie z parsera Excela.
     */
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `active_employees` (" +
                "`name` TEXT NOT NULL, " +
                "PRIMARY KEY(`name`))"
            );
        }
    };

    /**
     * v8 → v9
     * Tworzy tabelę `global_shifts` — pełny grafik ekipy kina.
     * Append Only — nigdy nie usuwamy, INSERT OR IGNORE chroni przed duplikatami.
     * Indeksy: po dacie (szybkie lookup) + unikalny composite (deduplikacja).
     */
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `global_shifts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`start_time` TEXT, " +
                "`end_time` TEXT, " +
                "`category` TEXT DEFAULT 'UNKNOWN')"
            );
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_global_shifts_date` ON `global_shifts` (`date`)"
            );
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_global_shifts_name_date_start_time_end_time` " +
                "ON `global_shifts` (`name`, `date`, `start_time`, `end_time`)"
            );
        }
    };

    /**
     * v9 → v10
     * Dodaje kolumnę `is_manually_edited` do global_shifts.
     * Chroni ręczne zmiany użytkownika przed nadpisaniem przez parser Excela.
     */
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "ALTER TABLE global_shifts ADD COLUMN is_manually_edited INTEGER NOT NULL DEFAULT 0"
            );
        }
    };

    /**
     * v10 → v11
     * Dodaje kolumnę `is_deleted` do global_shifts (soft delete).
     * Usunięte rekordy pozostają w bazie, blokując parser przed ponownym wstawieniem.
     */
    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "ALTER TABLE global_shifts ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0"
            );
        }
    };

    /**
     * v11 → v12
     * Dodaje kolumny alarmu do global_shifts.
     * has_alarm — czy użytkownik ustawił budzik na tę zmianę.
     * alarm_offset_minutes — ile minut przed startTime ma się włączyć.
     */
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@androidx.annotation.NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                "ALTER TABLE global_shifts ADD COLUMN has_alarm INTEGER NOT NULL DEFAULT 0"
            );
            database.execSQL(
                "ALTER TABLE global_shifts ADD COLUMN alarm_offset_minutes INTEGER NOT NULL DEFAULT 0"
            );
        }
    };

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    )
                    .addMigrations(
                        MIGRATION_3_4,   // v3 → v4
                        MIGRATION_5_6,   // v5 → v6
                        MIGRATION_6_7,   // v6 → v7
                        MIGRATION_7_8,   // v7 → v8 (słownik pracowników)
                        MIGRATION_8_9,   // v8 → v9 (globalny grafik ekipy)
                        MIGRATION_9_10,  // v9 → v10 (manual override flag)
                        MIGRATION_10_11, // v10 → v11 (soft delete)
                        MIGRATION_11_12  // v11 → v12 (alarm budzik)
                    )
                    // Ostatnia linia obrony dla instalacji starszych niż v3
                    // lub luki v4→v5. Przy destructive dane są kasowane.
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
