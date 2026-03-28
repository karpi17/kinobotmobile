package com.asystent.kinowy.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.asystent.kinowy.models.Tip;

import java.util.List;

@Dao
public interface TipDao {

    @Insert
    void insert(Tip tip);

    @Update
    void update(Tip tip);

    @Delete
    void delete(Tip tip);

    @Query("SELECT * FROM tips ORDER BY date DESC")
    LiveData<List<Tip>> getAllTips();

}
