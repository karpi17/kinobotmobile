package com.asystent.kinowy.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.asystent.kinowy.models.Shift;

import java.util.List;

/**
 * Data Access Object dla encji Shift.
 * Zapewnia metody CRUD oraz zapytania do tabeli shifts.
 */
@Dao
public interface ShiftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Shift shift);

    @Update
    void update(Shift shift);

    @Delete
    void delete(Shift shift);

    @Query("SELECT * FROM shifts ORDER BY date DESC")
    LiveData<List<Shift>> getAllShifts();

    @Query("SELECT * FROM shifts WHERE id = :id LIMIT 1")
    Shift getShiftById(int id);

    @Query("DELETE FROM shifts")
    void deleteAll();

    /**
     * Pobiera wszystkie zmiany synchronicznie (bez LiveData).
     * Używane przez AppWidgetProvider, który działa poza kontekstem Activity.
     */
    @Query("SELECT * FROM shifts ORDER BY date ASC, start_time ASC")
    List<Shift> getAllShiftsSync();

    /**
     * Pobiera surowe wartości kolumny closing_crew z ostatnich 14 dni.
     * Każdy wiersz może zawierać wiele imion po przecinku (np. "Kacper W., Ola G.").
     * Wywoływać wyłącznie na wątku w tle (synchroniczne).
     */
    @Query("SELECT closing_crew FROM shifts " +
           "WHERE date >= date('now', '-14 days') " +
           "AND closing_crew IS NOT NULL " +
           "AND closing_crew != ''")
    List<String> getRecentClosingCrews();
}
