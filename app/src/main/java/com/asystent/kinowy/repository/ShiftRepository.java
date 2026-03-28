package com.asystent.kinowy.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.asystent.kinowy.db.AppDatabase;
import com.asystent.kinowy.db.ShiftDao;
import com.asystent.kinowy.models.Shift;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repozytorium dla encji {@link Shift}.
 * <p>
 * Warstwa pośrednia między {@link ShiftDao} a ViewModelem.
 * Operacje zapisu/usuwania wykonywane są na osobnym wątku
 * za pomocą {@link ExecutorService}, natomiast odczyty korzystają
 * z Room {@link LiveData} (obserwowane automatycznie na main thread).
 */
public class ShiftRepository {

    private final ShiftDao shiftDao;
    private final LiveData<List<Shift>> allShifts;
    private final ExecutorService executor;

    /**
     * @param application kontekst aplikacji potrzebny do uzyskania instancji bazy danych
     */
    public ShiftRepository(Application application) {
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

    // ─── Zapisy (ExecutorService — background thread) ────────────────────

    /**
     * Wstawia nową zmianę do bazy danych.
     */
    public void insert(Shift shift) {
        executor.execute(() -> shiftDao.insert(shift));
    }

    /**
     * Aktualizuje istniejącą zmianę.
     */
    public void update(Shift shift) {
        executor.execute(() -> shiftDao.update(shift));
    }

    /**
     * Usuwa podaną zmianę.
     */
    public void delete(Shift shift) {
        executor.execute(() -> shiftDao.delete(shift));
    }

    /**
     * Usuwa wszystkie zmiany z bazy danych.
     */
    public void deleteAll() {
        executor.execute(shiftDao::deleteAll);
    }
}
