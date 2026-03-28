package com.asystent.kinowy.models;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Encja reprezentująca stratę / wypłatę (loss) w systemie kinowym.
 */
@Entity(tableName = "losses")
public class Loss {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "date")
    private String date; // ISO-8601: yyyy-MM-dd

    @ColumnInfo(name = "amount")
    private double amount;

    @ColumnInfo(name = "description")
    private String description;

    // --- Konstruktor ---

    public Loss(String date, double amount, String description) {
        this.date = date;
        this.amount = amount;
        this.description = description;
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

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
