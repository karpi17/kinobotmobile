package com.asystent.kinowy.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Encja reprezentująca punkt w historii stawek godzinowych.
 * Każdy rekord oznacza: "Od tej daty, moja stawka wynosiła X PLN/h".
 *
 * Dzięki temu kalkulator może precyzyjnie obliczyć wynagrodzenie
 * za miesiąc z podwyżką w trakcie (np. 31.40 do 8.08, potem 32.80).
 */
@Entity(tableName = "rate_history")
public class RateHistory {

    @PrimaryKey(autoGenerate = true)
    private int id;

    /**
     * Data od której ta stawka obowiązuje (format: yyyy-MM-dd).
     * Pobierając stawkę dla danego dnia, szukamy MAX(activeFrom) <= targetDate.
     */
    @ColumnInfo(name = "active_from")
    private String activeFrom;

    /** Stawka godzinowa w PLN. */
    @ColumnInfo(name = "rate")
    private float rate;

    /** Opcjonalna notatka (np. "Podwyżka lipiec 2026", "Stawka startowa"). */
    @ColumnInfo(name = "note")
    private String note;

    // --- Konstruktor główny ---
    public RateHistory(String activeFrom, float rate, String note) {
        this.activeFrom = activeFrom;
        this.rate = rate;
        this.note = note;
    }

    // --- Gettery i Settery ---

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getActiveFrom() { return activeFrom; }
    public void setActiveFrom(String activeFrom) { this.activeFrom = activeFrom; }

    public float getRate() { return rate; }
    public void setRate(float rate) { this.rate = rate; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
