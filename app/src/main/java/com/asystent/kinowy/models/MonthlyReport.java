package com.asystent.kinowy.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
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

    @ColumnInfo(name = "actualSalary")
    private double actualSalary;

    public MonthlyReport(@NonNull String monthYear, double calculatedHours, double paperHours, double calculatedSalary, double actualSalary) {
        this.monthYear = monthYear;
        this.calculatedHours = calculatedHours;
        this.paperHours = paperHours;
        this.calculatedSalary = calculatedSalary;
        this.actualSalary = actualSalary;
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

    public double getActualSalary() { return actualSalary; }
    public void setActualSalary(double actualSalary) { this.actualSalary = actualSalary; }
}
