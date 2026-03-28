package com.asystent.kinowy.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.asystent.kinowy.db.AppDatabase;
import com.asystent.kinowy.db.LossDao;
import com.asystent.kinowy.models.Loss;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repozytorium dla encji {@link Loss}.
 * <p>
 * Warstwa pośrednia między {@link LossDao} a ViewModelem.
 * Operacje zapisu/usuwania wykonywane są na osobnym wątku
 * za pomocą {@link ExecutorService}, natomiast odczyty korzystają
 * z Room {@link LiveData} (obserwowane automatycznie na main thread).
 */
public class LossRepository {

    private final LossDao lossDao;
    private final LiveData<List<Loss>> allLosses;
    private final ExecutorService executor;

    /**
     * @param application kontekst aplikacji potrzebny do uzyskania instancji bazy danych
     */
    public LossRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        this.lossDao = db.lossDao();
        this.allLosses = lossDao.getAllLosses();
        this.executor = Executors.newSingleThreadExecutor();
    }

    // ─── Odczyty (LiveData — main thread) ────────────────────────────────

    /**
     * Zwraca obserwowalne dane ze wszystkimi stratami, posortowane malejąco wg daty.
     */
    public LiveData<List<Loss>> getAllLosses() {
        return allLosses;
    }

    /**
     * Pobiera stratę po identyfikatorze (blokujące — wywoływać na wątku w tle).
     */
    public Loss getLossById(int id) {
        return lossDao.getLossById(id);
    }

    // ─── Zapisy (ExecutorService — background thread) ────────────────────

    /**
     * Wstawia nową stratę do bazy danych.
     */
    public void insert(Loss loss) {
        executor.execute(() -> lossDao.insert(loss));
    }

    /**
     * Aktualizuje istniejącą stratę.
     */
    public void update(Loss loss) {
        executor.execute(() -> lossDao.update(loss));
    }

    /**
     * Usuwa podaną stratę.
     */
    public void delete(Loss loss) {
        executor.execute(() -> lossDao.delete(loss));
    }

    /**
     * Usuwa wszystkie straty z bazy danych.
     */
    public void deleteAll() {
        executor.execute(lossDao::deleteAll);
    }
}
