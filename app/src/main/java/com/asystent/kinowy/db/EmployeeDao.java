package com.asystent.kinowy.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.asystent.kinowy.models.ActiveEmployee;

import java.util.List;

/**
 * DAO dla słownika pracowników ({@link ActiveEmployee}).
 */
@Dao
public interface EmployeeDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertOrIgnore(ActiveEmployee employee);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<ActiveEmployee> employees);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ActiveEmployee employee);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<ActiveEmployee> employees);

    @Update
    void update(ActiveEmployee employee);

    /**
     * Zwraca imiona WYŁĄCZNIE aktywnych pracowników (dla AutoComplete i podpowiedzi).
     */
    @Query("SELECT name FROM active_employees WHERE is_active = 1 ORDER BY name ASC")
    LiveData<List<String>> getAllActiveEmployeeNames();

    /**
     * Alias kompatybilności wstecznej — zwraca tylko aktywnych pracowników.
     */
    @Query("SELECT name FROM active_employees WHERE is_active = 1 ORDER BY name ASC")
    LiveData<List<String>> getAllEmployeeNames();

    @Query("SELECT * FROM active_employees ORDER BY is_active DESC, name ASC")
    LiveData<List<ActiveEmployee>> getAllEmployeesLive();

    @Query("SELECT * FROM active_employees ORDER BY is_active DESC, name ASC")
    List<ActiveEmployee> getAllEmployeesSync();

    @Query("SELECT * FROM active_employees WHERE is_active = 1 ORDER BY name ASC")
    List<ActiveEmployee> getAllActiveEmployeesSync();

    @Query("SELECT * FROM active_employees WHERE name = :name LIMIT 1")
    ActiveEmployee getEmployeeByName(String name);

    @Query("UPDATE active_employees SET is_active = :isActive WHERE name = :name")
    void setActiveStatus(String name, boolean isActive);

    @Query("UPDATE active_employees SET role = :role WHERE name = :name")
    void setRole(String name, String role);

    @Query("DELETE FROM active_employees WHERE name = :name")
    void deleteByName(String name);
}
