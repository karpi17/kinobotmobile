package com.asystent.kinowy.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "monthly_reports")
public class MonthlyReport {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "monthYear")
    private String monthYear; // format np. "2026-03"

    @ColumnInfo(name = "calculatedHours")
    private double calculatedHours;

    @ColumnInfo(name = "paperHours")
    private double paperHours;

    @ColumnInfo(name = "calculatedSalary")
    private double calculatedSalary;

    @Nullable
    @ColumnInfo(name = "actualSalary")
    private Double actualSalary;

    // Pełny konstruktor (Room uses this one)
    public MonthlyReport(@NonNull String monthYear, double calculatedHours, double paperHours, double calculatedSalary, @Nullable Double actualSalary) {
        this.monthYear = monthYear;
        this.calculatedHours = calculatedHours;
        this.paperHours = paperHours;
        this.calculatedSalary = calculatedSalary;
        this.actualSalary = actualSalary;
    }

    // Konstruktor I stopnia — bez kwoty przelewu
    @Ignore
    public MonthlyReport(@NonNull String monthYear, double calculatedHours, double paperHours, double calculatedSalary) {
        this(monthYear, calculatedHours, paperHours, calculatedSalary, null);
    }

    @NonNull
    public String getMonthYear() { return monthYear; }
    public void setMonthYear(@NonNull String monthYear) { this.monthYear = monthYear; }

    public double getCalculatedHours() { return calculatedHours; }
    public void setCalculatedHours(double calculatedHours) { this.calculatedHours = calculatedHours; }

    public double getPaperHours() { return paperHours; }
    public void setPaperHours(double paperHours) { this.paperHours = paperHours; }

    public double getCalculatedSalary() { return calculatedSalary; }
    public void setCalculatedSalary(double calculatedSalary) { this.calculatedSalary = calculatedSalary; }

    @Nullable
    public Double getActualSalary() { return actualSalary; }
    public void setActualSalary(@Nullable Double actualSalary) { this.actualSalary = actualSalary; }
}
