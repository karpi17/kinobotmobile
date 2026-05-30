package com.asystent.kinowy.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;

/**
 * Encja — globalny grafik pracy całej ekipy kina.
 * <p>
 * Przechowuje <b>każdą</b> zmianę <b>każdego</b> pracownika odczytaną z plików .xlsx.
 * Tabela działa w trybie <b>Append Only</b> — nigdy nie usuwamy starych rekordów,
 * co pozwala budować roczne statystyki.
 * <p>
 * Unikalny indeks złożony {@code (name, date, startTime, endTime)} zapobiega
 * duplikatom przy ponownym imporcie tego samego pliku. {@code INSERT OR IGNORE}
 * po stronie DAO gwarantuje bezpieczność operacji.
 * <p>
 * Pole {@code isManuallyEdited} chroni ręczne zmiany użytkownika przed
 * nadpisaniem przez parser Excela.
 *
 * @see com.asystent.kinowy.db.GlobalShiftDao
 */
@Entity(
    tableName = "global_shifts",
    indices = {
        @Index(value = "date"),
        @Index(value = {"name", "date", "start_time", "end_time"}, unique = true)
    }
)
public class GlobalShift {

    @androidx.room.PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    @ColumnInfo(name = "name")
    private String name; // "Kacper W." / "Wiśniewski Kacper"

    @NonNull
    @ColumnInfo(name = "date")
    private String date; // yyyy-MM-dd

    @ColumnInfo(name = "start_time")
    private String startTime; // HH:mm

    @ColumnInfo(name = "end_time")
    private String endTime; // HH:mm

    @ColumnInfo(name = "category", defaultValue = "UNKNOWN")
    private String category; // BAR / OW / UNKNOWN

    @ColumnInfo(name = "is_manually_edited", defaultValue = "0")
    private boolean isManuallyEdited; // true = ręcznie edytowane, chronione przed parserem

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    private boolean isDeleted; // true = soft-deleted, ukryte ale blokuje parser

    @ColumnInfo(name = "has_alarm", defaultValue = "0")
    private boolean hasAlarm; // true = użytkownik ustawił alarm na tę zmianę

    @ColumnInfo(name = "alarm_offset_minutes", defaultValue = "0")
    private int alarmOffsetMinutes; // minuty wyprzedzenia przed startTime

    // ─── Konstruktor ─────────────────────────────────────────────────────

    public GlobalShift(@NonNull String name, @NonNull String date,
                       String startTime, String endTime, String category) {
        this.name = name;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.category = category != null ? category : "UNKNOWN";
        this.isManuallyEdited = false;
        this.isDeleted = false;
        this.hasAlarm = false;
        this.alarmOffsetMinutes = 0;
    }

    // ─── Gettery / Settery ───────────────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    @NonNull
    public String getName() { return name; }
    public void setName(@NonNull String name) { this.name = name; }

    @NonNull
    public String getDate() { return date; }
    public void setDate(@NonNull String date) { this.date = date; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isManuallyEdited() { return isManuallyEdited; }
    public void setManuallyEdited(boolean manuallyEdited) { this.isManuallyEdited = manuallyEdited; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { this.isDeleted = deleted; }

    public boolean isHasAlarm() { return hasAlarm; }
    public void setHasAlarm(boolean hasAlarm) { this.hasAlarm = hasAlarm; }

    public int getAlarmOffsetMinutes() { return alarmOffsetMinutes; }
    public void setAlarmOffsetMinutes(int alarmOffsetMinutes) { this.alarmOffsetMinutes = alarmOffsetMinutes; }
}
