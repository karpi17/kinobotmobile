package com.asystent.kinowy.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.asystent.kinowy.models.RateHistory;

import java.util.List;

/**
 * DAO dla historii stawek godzinowych.
 * Kluczowa metoda: {@link #getRateForDate(String)} — zwraca stawkę
 * obowiązującą w danym dniu (ostatnia stawka z activeFrom <= targetDate).
 */
@Dao
public interface RateHistoryDao {

    /** Wstawia nowy wpis historii stawki. Ignoruje duplikaty tej samej daty. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RateHistory rateHistory);

    /** Aktualizuje istniejący wpis historii. */
    @Update
    void update(RateHistory rateHistory);

    /** Usuwa wpis historii stawki. */
    @Delete
    void delete(RateHistory rateHistory);

    /**
     * Zwraca stawkę godzinową obowiązującą w podanym dniu.
     * Szuka ostatniego wpisu, którego activeFrom <= targetDate.
     *
     * Przykład: stawki w bazie: (2026-08-01, 31.40), (2026-08-08, 32.80)
     * - Dla dnia 2026-08-05 → zwróci 31.40
     * - Dla dnia 2026-08-10 → zwróci 32.80
     *
     * @param targetDate data zmiany w formacie yyyy-MM-dd
     * @return stawka godzinowa (0 jeśli brak historii dla tego okresu)
     */
    @Query("SELECT rate FROM rate_history WHERE active_from <= :targetDate ORDER BY active_from DESC LIMIT 1")
    float getRateForDate(String targetDate);

    /** Zwraca wszystkie stawki posortowane od najnowszej (do wyświetlenia w UI). */
    @Query("SELECT * FROM rate_history ORDER BY active_from DESC")
    LiveData<List<RateHistory>> getAllRates();

    /** Synchroniczna wersja dla wątku tła. */
    @Query("SELECT * FROM rate_history ORDER BY active_from DESC")
    List<RateHistory> getAllRatesSync();

    /** Liczba rekordów w historii (do sprawdzenia czy baza jest pusta). */
    @Query("SELECT COUNT(*) FROM rate_history")
    int countAll();

    /** Zwraca aktualnie obowiązującą stawkę (najnowszy wpis). */
    @Query("SELECT rate FROM rate_history ORDER BY active_from DESC LIMIT 1")
    float getLatestRate();
}
