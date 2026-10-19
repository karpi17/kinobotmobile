package com.asystent.kinowy.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.asystent.kinowy.db.AppDatabase;
import com.asystent.kinowy.db.ShiftDao;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.notifications.NotificationScheduler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repozytorium dla encji {@link Shift}.
 * <p>
 * Warstwa pośrednia między {@link ShiftDao} a ViewModelem.
 * Operacje zapisu/usuwania wykonywane są na osobnym wątku
 * za pomocą {@link ExecutorService}, natomiast odczyty korzystają
 * z Room {@link LiveData} (obserwowane automatycznie na main thread).
 * <p>
 * Przy update/delete synchronizuje alarmy systemowe (cancel + reschedule).
 */
public class ShiftRepository {

    private final ShiftDao shiftDao;
    private final LiveData<List<Shift>> allShifts;
    private final ExecutorService executor;
    private final Application application;

    /**
     * @param application kontekst aplikacji potrzebny do uzyskania instancji bazy danych
     */
    public ShiftRepository(Application application) {
        this.application = application;
        AppDatabase db = AppDatabase.getInstance(application);
        this.shiftDao = db.shiftDao();
        this.allShifts = shiftDao.getAllShifts();
        this.executor = Executors.newSingleThreadExecutor();
    }

    // ─── Odczyty (LiveData — main thread) ────────────────────────────────

    /**
     * Zwraca obserwowalne dane ze wszystkimi zmianami, posortowane malejąco wg daty.
     */
    public LiveData<List<Shift>> getAllShifts() {
        return allShifts;
    }

    /**
     * Pobiera zmianę po identyfikatorze (blokujące — wywoływać na wątku w tle).
     */
    public Shift getShiftById(int id) {
        return shiftDao.getShiftById(id);
    }

    /**
     * Pobiera unikalne imiona z ekip zamykających z ostatnich 14 dni.
     * <p>
     * Każdy wpis closing_crew może zawierać wiele osób po przecinku
     * (np. "Kacper W., Aleksandra G."). Ta metoda:
     * 1. Pobiera surowe wpisy z DAO
     * 2. Rozbija każdy wpis po przecinku (split)
     * 3. Tworzy HashSet — automatycznie usuwa duplikaty
     * 4. Zwraca posortowaną listę unikalnych imion
     * <p>
     * ⚠ Blokujące — wywoływać wyłącznie na wątku w tle!
     */
    public List<String> getRecentClosingCrewNames() {
        List<String> rawCrews = shiftDao.getRecentClosingCrews();
        Set<String> uniqueNames = new HashSet<>();

        if (rawCrews != null) {
            for (String crew : rawCrews) {
                String[] parts = crew.split(",");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        uniqueNames.add(trimmed);
                    }
                }
            }
        }

        List<String> sorted = new ArrayList<>(uniqueNames);
        java.util.Collections.sort(sorted);
        return sorted;
    }

    // ─── Zapisy (ExecutorService — background thread) ────────────────────

    /**
     * Wstawia nową zmianę do bazy danych.
     * Alarm zaplanuje się automatycznie przez obserwatora LiveData w MainActivity.
     */
    public void insert(Shift shift) {
        executor.execute(() -> {
            shiftDao.insert(shift);
            com.asystent.kinowy.widget.ShiftWidgetProvider.triggerUpdate(application);
            com.asystent.kinowy.widget.ShiftStackWidgetProvider.triggerUpdate(application);
        });
    }

    /**
     * Aktualizuje istniejącą zmianę i synchronizuje alarm:
     * anuluje stary alarm → planuje nowy na zaktualizowany czas.
     */
    public void update(Shift shift) {
        executor.execute(() -> {
            shiftDao.update(shift);
            NotificationScheduler.rescheduleForShift(application, shift);
            com.asystent.kinowy.widget.ShiftWidgetProvider.triggerUpdate(application);
            com.asystent.kinowy.widget.ShiftStackWidgetProvider.triggerUpdate(application);
        });
    }

    /**
     * Usuwa podaną zmianę i anuluje przypisane do niej alarmy (zarówno powiadomienia jak i budzik pełnoekranowy).
     */
    public void delete(Shift shift) {
        executor.execute(() -> {
            NotificationScheduler.cancelAlarmsForShift(application, shift);
            try {
                AppDatabase db = AppDatabase.getInstance(application);
                String userName = application.getSharedPreferences("asystent_kinowy_prefs", android.content.Context.MODE_PRIVATE)
                        .getString("user_name", "");
                com.asystent.kinowy.models.GlobalShift gs = userName.isEmpty()
                        ? db.globalShiftDao().getShiftByDateAndStart(shift.getDate(), shift.getStartTime())
                        : db.globalShiftDao().getShiftByDateStartAndUser(shift.getDate(), shift.getStartTime(), userName);
                if (gs != null && gs.isHasAlarm()) {
                    com.asystent.kinowy.alarm.AlarmScheduler.cancelAlarm(application, gs);
                    gs.setHasAlarm(false);
                    gs.setAlarmOffsetMinutes(0);
                    db.globalShiftDao().updateGlobalShift(gs);
                }
            } catch (Exception ignored) {}
            shiftDao.delete(shift);
            com.asystent.kinowy.widget.ShiftWidgetProvider.triggerUpdate(application);
            com.asystent.kinowy.widget.ShiftStackWidgetProvider.triggerUpdate(application);
        });
    }

    /**
     * Usuwa wszystkie zmiany z bazy danych.
     * Alarmy zostaną automatycznie zsynchronizowane przez obserwatora w MainActivity
     * (pusta lista → brak planowania).
     */
    public void deleteAll() {
        executor.execute(() -> {
            shiftDao.deleteAll();
            com.asystent.kinowy.widget.ShiftWidgetProvider.triggerUpdate(application);
            com.asystent.kinowy.widget.ShiftStackWidgetProvider.triggerUpdate(application);
        });
    }

    // ─── Metody synchroniczne (wywoływać TYLKO z wątku w tle) ────────────────

    /**
     * Pobiera synchronicznie wszystkie zmiany z bazy danych.
     * Wywoływać wyłącznie z wątku w tle.
     */
    public List<Shift> getAllShiftsSync() {
        return shiftDao.getAllShiftsSync();
    }

    /**
     * Wstawia zmianę synchronicznie (blokująco) — używać wyłącznie z wątku Executor.
     * Używane podczas importu grafiku gdzie potrzebujemy kontrolować kolejność operacji.
     */
    public void insertSync(Shift shift) {
        shiftDao.insert(shift);
    }

    /**
     * Pobiera synchronicznie wszystkie zmiany na podany dzień.
     * Wywoływać tylko z wątku w tle (np. Executor).
     */
    public List<Shift> getShiftsByDateSync(String date) {
        return shiftDao.getShiftsByDateSync(date);
    }

    /**
     * Kasuje synchronicznie wszystkie nieręczne zmiany na podany dzień.
     * Wywoływać tylko z wątku w tle (np. Executor).
     *
     * @return liczba usuniętych wierszy
     */
    public int deleteNonManualShiftsForDate(String date) {
        return shiftDao.deleteNonManualShiftsForDate(date);
    }
}
