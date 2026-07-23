package com.asystent.kinowy.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.asystent.kinowy.models.ScheduleImportLog;

import java.util.List;

/**
 * DAO dla tabeli historii importów grafików ({@link ScheduleImportLog}).
 */
@Dao
public interface ImportLogDao {

    @Insert
    long insert(ScheduleImportLog log);

    @Query("SELECT * FROM import_log ORDER BY id DESC LIMIT 50")
    LiveData<List<ScheduleImportLog>> getAllImportLogs();

    @Query("SELECT * FROM import_log ORDER BY id DESC LIMIT 1")
    ScheduleImportLog getLatestImportLog();
}
