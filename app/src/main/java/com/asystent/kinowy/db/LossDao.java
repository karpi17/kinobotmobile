package com.asystent.kinowy.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.asystent.kinowy.models.Loss;

import java.util.List;

/**
 * Data Access Object dla encji Loss.
 * Zapewnia metody CRUD oraz zapytania do tabeli losses.
 */
@Dao
public interface LossDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Loss loss);

    @Update
    void update(Loss loss);

    @Delete
    void delete(Loss loss);

    @Query("SELECT * FROM losses ORDER BY date DESC")
    LiveData<List<Loss>> getAllLosses();

    @Query("SELECT * FROM losses WHERE id = :id LIMIT 1")
    Loss getLossById(int id);

    @Query("DELETE FROM losses")
    void deleteAll();
}
