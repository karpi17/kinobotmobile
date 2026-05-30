package com.asystent.kinowy.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.asystent.kinowy.models.ActiveEmployee;

import java.util.List;

/**
 * DAO dla słownika pracowników ({@link ActiveEmployee}).
 * <p>
 * {@code insertOrIgnore} — dodaje nowego pracownika, ignoruje jeśli already exists
 * (dzięki {@link OnConflictStrategy#IGNORE} i PrimaryKey na name).
 * <p>
 * {@code getAllEmployeeNames} — zwraca LiveData z posortowaną listą imion.
 */
@Dao
public interface EmployeeDao {

    /**
     * Wstawia pracownika do słownika. Jeśli już istnieje (po name) — ignoruje.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertOrIgnore(ActiveEmployee employee);

    /**
     * Zwraca wszystkie imiona pracowników, posortowane alfabetycznie.
     * LiveData — automatycznie reaguje na zmiany w tabeli.
     */
    @Query("SELECT name FROM active_employees ORDER BY name ASC")
    LiveData<List<String>> getAllEmployeeNames();
}
