package com.asystent.kinowy.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.asystent.kinowy.models.MonthlyReport;

import java.util.List;

@Dao
public interface MonthlyReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MonthlyReport report);

    @Update
    void update(MonthlyReport report);

    @Query("SELECT * FROM monthly_reports WHERE monthYear = :monthYear LIMIT 1")
    MonthlyReport getReportForMonthSync(String monthYear);

    @Query("SELECT * FROM monthly_reports WHERE monthYear = :monthYear LIMIT 1")
    LiveData<MonthlyReport> getReportForMonth(String monthYear);
    
    @Query("SELECT * FROM monthly_reports ORDER BY monthYear DESC")
    LiveData<List<MonthlyReport>> getAllReports();
}
