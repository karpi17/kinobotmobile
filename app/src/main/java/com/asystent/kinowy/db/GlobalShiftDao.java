package com.asystent.kinowy.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.asystent.kinowy.models.GlobalShift;

import java.util.List;

/**
 * DAO dla tabeli globalnych zmian ({@link GlobalShift}).
 * <p>
 * Tabela jest w trybie <b>Append Only</b> z obsługą ręcznych edycji.
 * {@code IGNORE} przy insercie zapobiega duplikatom dzięki unikalnemu indeksowi
 * na {@code (name, date, start_time, end_time)}.
 * <p>
 * Rekordy z {@code is_manually_edited = 1} są chronione przed nadpisaniem
 * przez parser Excela — patrz {@link #insertAllSafe(List)}.
 */
@Dao
public interface GlobalShiftDao {

    /**
     * Wstawia listę zmian globalnych. Duplikaty (po unikalnym indeksie) są ignorowane.
     * <p>
     * <b>UWAGA:</b> Używać wyłącznie do surowego wstawiania.
     * Do importu z Excela użyj {@link #insertAllSafe(List)}.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<GlobalShift> shifts);

    /**
     * Wstawia pojedynczą zmianę globalną (ręczne dodanie współpracownika).
     * Duplikaty (po unikalnym indeksie) są ignorowane.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertGlobalShift(GlobalShift shift);

    /**
     * Aktualizuje istniejącą zmianę globalną (po ID).
     * Używane przy ręcznej edycji godzin współpracownika.
     */
    @Update
    void updateGlobalShift(GlobalShift shift);

    /**
     * Usuwa zmianę globalną (po ID).
     * Używane przy ręcznym usuwaniu współpracownika ze zmiany.
     */
    @Delete
    void deleteGlobalShift(GlobalShift shift);

    /**
     * Aktualizuje stan alarmu dla konkretnej zmiany.
     * Identyfikacja po dacie + godzinie startu (bo alarm dotyczy zmiany użytkownika).
     *
     * @param date      data zmiany (yyyy-MM-dd)
     * @param startTime godzina startu (HH:mm)
     * @param hasAlarm  true = alarm aktywny
     * @param offset    minuty wyprzedzenia przed startTime
     */
    @Query("UPDATE global_shifts SET has_alarm = :hasAlarm, alarm_offset_minutes = :offset " +
           "WHERE date = :date AND start_time = :startTime AND is_deleted = 0")
    void updateAlarmState(String date, String startTime, boolean hasAlarm, int offset);

    /**
     * Pobiera wszystkie zmiany z aktywnym alarmem (do reschedulingu po rebocie).
     */
    @Query("SELECT * FROM global_shifts WHERE has_alarm = 1 AND is_deleted = 0 ORDER BY date ASC, start_time ASC")
    List<GlobalShift> getShiftsWithAlarms();

    /**
     * Pobiera pierwszą zmianę dla danej daty i godziny startu (synchroniczne).
     * Używane do sprawdzenia stanu alarmu przy otwieraniu dialogu zmiany.
     *
     * @param date      data zmiany (yyyy-MM-dd)
     * @param startTime godzina startu (HH:mm)
     * @return GlobalShift z flagami alarmu, lub null
     */
    @Query("SELECT * FROM global_shifts WHERE date = :date AND start_time = :startTime " +
           "AND is_deleted = 0 LIMIT 1")
    GlobalShift getShiftByDateAndStart(String date, String startTime);
    /**
     * Pobiera wszystkie zmiany dla danej daty.
     * Synchroniczne — wywoływać na wątku w tle.
     *
     * @param date data w formacie yyyy-MM-dd
     * @return lista zmian wszystkich pracowników w tym dniu
     */
    @Query("SELECT * FROM global_shifts WHERE date = :date AND is_deleted = 0 ORDER BY start_time ASC")
    List<GlobalShift> getShiftsByDate(String date);

    /**
     * Jak getShiftsByDate, ale pomija rekord użytkownika (po imieniu) — używane
     * przez widget żeby nie pokazywał siebie w liście ekipy.
     *
     * @param date       data yyyy-MM-dd
     * @param excludeName imię i nazwisko użytkownika do pominięcia
     */
    @Query("SELECT * FROM global_shifts WHERE date = :date AND is_deleted = 0 AND name != :excludeName ORDER BY start_time ASC")
    List<GlobalShift> getCrewByDateExcluding(String date, String excludeName);


    /**
     * Zwraca unikalne imiona pracowników posortowane alfabetycznie.
     * <p>
     * Zastępuje stary słownik {@code active_employees} — źródłem prawdy
     * jest teraz tabela {@code global_shifts} (Single Source of Truth).
     */
    @Query("SELECT DISTINCT name FROM global_shifts WHERE is_deleted = 0 ORDER BY name ASC")
    LiveData<List<String>> getActiveEmployeeNames();

    /**
     * Sprawdza, czy istnieje ręcznie edytowany rekord o tych samych danych.
     * Używane w {@link #insertAllSafe(List)} do ochrony manualnych zmian.
     */
    @Query("SELECT COUNT(*) FROM global_shifts " +
           "WHERE name = :name AND date = :date " +
           "AND (is_manually_edited = 1 OR is_deleted = 1)")
    int countManuallyEditedByNameAndDate(String name, String date);

    /**
     * Wstawia listę zmian z Excela, chroniąc ręcznie edytowane rekordy.
     * <p>
     * Dla każdej zmiany sprawdza, czy istnieje ręczny override
     * na ten sam dzień i pracownika — jeśli tak, pomija wstawianie.
     * <p>
     * <b>Wywoływać na wątku w tle.</b>
     */
    default void insertAllSafe(List<GlobalShift> shifts) {
        if (shifts == null || shifts.isEmpty()) return;

        // Zbieramy bezpieczne rekordy do wstawienia — jedna transakcja zamiast N
        List<GlobalShift> safeToInsert = new java.util.ArrayList<>();
        for (GlobalShift gs : shifts) {
            // Pomiń jeśli istnieje ręczna edycja lub soft-delete dla tego pracownika w tym dniu
            int manualCount = countManuallyEditedByNameAndDate(gs.getName(), gs.getDate());
            if (manualCount == 0) {
                safeToInsert.add(gs);
            }
        }

        // Wstaw wszystkie bezpieczne rekordy jednym wywołaniem (batch insert)
        if (!safeToInsert.isEmpty()) {
            insertAll(safeToInsert);
        }
    }
}
