package com.asystent.kinowy.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Encja reprezentująca zmianę (shift) w grafiku pracy kina.
 */
@Entity(tableName = "shifts")
public class Shift {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "date")
    private String date; // ISO-8601: yyyy-MM-dd

    @ColumnInfo(name = "start_time")
    private String startTime; // HH:mm

    @ColumnInfo(name = "end_time")
    private String endTime; // HH:mm

    @ColumnInfo(name = "description")
    private String description;

    @ColumnInfo(name = "confirmed")
    private boolean confirmed;

    private boolean isManual;

    @ColumnInfo(name = "is_replacement", defaultValue = "false")
    private boolean isReplacement;

    @ColumnInfo(name = "category", defaultValue = "UNKNOWN")
    private String category;

    // --- Konstruktor ---
    @Ignore
    public Shift(String date, String startTime, String endTime, String description, boolean confirmed) {
        this(date, startTime, endTime, description, confirmed, false, "UNKNOWN");
    }

    @Ignore
    public Shift(String date, String startTime, String endTime, String description, boolean confirmed, boolean isReplacement) {
        this(date, startTime, endTime, description, confirmed, isReplacement, "UNKNOWN");
    }

    public Shift(String date, String startTime, String endTime, String description, boolean confirmed, boolean isReplacement, String category) {
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.description = description;
        this.confirmed = confirmed;
        this.isReplacement = isReplacement;
        this.category = category != null ? category : "UNKNOWN";
    }

    // --- Gettery i Settery ---

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public boolean isReplacement() {
        return isReplacement;
    }

    public void setReplacement(boolean replacement) {
        isReplacement = replacement;
    }

    public boolean isManual() { return isManual; }
    public void setManual(boolean manual) { isManual = manual; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
