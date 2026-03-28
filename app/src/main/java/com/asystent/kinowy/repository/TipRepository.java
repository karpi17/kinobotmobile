package com.asystent.kinowy.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.asystent.kinowy.db.AppDatabase;
import com.asystent.kinowy.db.TipDao;
import com.asystent.kinowy.models.Tip;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repozytorium zarządzające encją Tip.
 */
public class TipRepository {

    private final TipDao tipDao;
    private final LiveData<List<Tip>> allTips;
    private final ExecutorService executor;

    public TipRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        tipDao = db.tipDao();
        allTips = tipDao.getAllTips();
        executor = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<Tip>> getAllTips() {
        return allTips;
    }

    public void insert(Tip tip) {
        executor.execute(() -> tipDao.insert(tip));
    }

    public void update(Tip tip) {
        executor.execute(() -> tipDao.update(tip));
    }

    public void delete(Tip tip) {
        executor.execute(() -> tipDao.delete(tip));
    }
}
