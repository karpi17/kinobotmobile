package com.asystent.kinowy.utils;

import android.content.Context;
import android.util.Log;

import com.asystent.kinowy.db.AppDatabase;
import com.asystent.kinowy.models.BackupData;
import com.asystent.kinowy.models.GlobalShift;
import com.asystent.kinowy.models.Loss;
import com.asystent.kinowy.models.MonthlyReport;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.models.Tip;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Mózg operacji z zakresu rygora bezpardonowej archiwizacji stanu aplikacji do wszechobecnego standardu JSON
 * oraz inteligentnej reanimacji z backupu pancernego (bez naruszania dotkniętej bazy powtórzonymi wpisami!).
 */
public class BackupManager {

    private static final String TAG = "BackupManager";
    private final AppDatabase db;
    private final Gson gson;

    public BackupManager(Context context) {
        this(AppDatabase.getInstance(context));
    }

    public BackupManager(AppDatabase database) {
        this.db = database;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Synchronicznie wydobywa całkowity stan Twoich dochodów, zmian, grafiku i strat
     * ze strumienia bazowego w czyste tekstowe ciało zrzutowe gotowe do przechowania z kropką .json.
     * Wywołać w odizolowanym od ekranu UI procesie w tle!
     */
    public String createBackupJsonSync(String currentAppVersion) {
        long startTime = System.currentTimeMillis();
        Log.d(TAG, "Rozpoczynam zrzut bezpieczny danych z kinowej bazy (Backup)...");

        BackupData backup = new BackupData();
        backup.setBackupSchemaVersion(1);
        backup.setAppVersionName(currentAppVersion != null ? currentAppVersion : "unknown");
        backup.setExportTimestamp(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date()));

        // Wyłapujemy bez LiveData natywne wykazy z baz z każdego sektoru rzędu
        backup.setShifts(db.shiftDao().getAllShiftsSync());
        backup.setGlobalShifts(db.globalShiftDao().getAllGlobalShiftsSync());
        backup.setMonthlyReports(db.monthlyReportDao().getAllReportsSync());
        backup.setLosses(db.lossDao().getAllLossesSync());
        backup.setTips(db.tipDao().getAllTipsSync());

        String jsonResult = gson.toJson(backup);
        Log.d(TAG, "Pomyślny koniec budowy kopii (.json)! Czas: " + (System.currentTimeMillis() - startTime) + " ms, Wyrobiony rozmiar znaków: " + jsonResult.length());
        return jsonResult;
    }

    /**
     * Wzbogaca obecny potencjał pamiątkowy bazy zaawansowaną porcją ze wskazanego napisu JSON.
     * Stosuje bezlitosne odsiewy chroniące Twój kalendarz przed dublami:
     * - Jeśli wpis o danej dacie i wyliczonym rozkładowie pracy tam już figuruje, pomijamy wtórny duplikat z dumą.
     * - Zapisy rozstrzygnięć raportowanych są konsolidowane na korzyść bez błędnych potknięć rzędowych.
     */
    public RestoreSummary restoreBackupFromJsonSync(String jsonString) {
        Log.d(TAG, "Rozpoczynam proces uzdrawiania z pliku w oparciu o bezpieczny parter redukowania duplikatów...");
        RestoreSummary summary = new RestoreSummary();
        if (jsonString == null || jsonString.trim().isEmpty()) {
            summary.setFailedReason("Plik archiwum wydaje się całkowicie pusty lub zniekształcony.");
            return summary;
        }

        BackupData backup;
        try {
            backup = gson.fromJson(jsonString, BackupData.class);
            if (backup == null) {
                summary.setFailedReason("Nie udało się sparsować schematu JSON. Nieznajoma struktura pliku.");
                return summary;
            }
        } catch (Exception e) {
            Log.e(TAG, "Błąd czytelniczy z pliku u klatki z GSONem: " + e.getMessage(), e);
            summary.setFailedReason("Błąd czytelnoci formatowania z podziemi pliku: " + e.getLocalizedMessage());
            return summary;
        }

        db.runInTransaction(() -> {
            // 1. Bezpieczna infiltracja Twojego grafiku (Shifts)
            List<Shift> existingShifts = db.shiftDao().getAllShiftsSync();
            Set<String> shiftFingerprints = new HashSet<>();
            for (Shift s : existingShifts) {
                shiftFingerprints.add(generateShiftKey(s.getDate(), s.getStartTime()));
            }
            int addedShifts = 0;
            int skippedShifts = 0;
            for (Shift s : backup.getShifts()) {
                String key = generateShiftKey(s.getDate(), s.getStartTime());
                if (!shiftFingerprints.contains(key)) {
                    // Wyjąć sztucznie zaaretowany identyfikator starego auto-increment z SQL-a ze zdmuchniętego pliku
                    s.setId(0); 
                    db.shiftDao().insert(s);
                    shiftFingerprints.add(key);
                    addedShifts++;
                } else {
                    skippedShifts++;
                }
            }
            summary.setShiftsAdded(addedShifts);
            summary.setShiftsSkipped(skippedShifts);

            // 2. Ekipa wszech-zmian (GlobalShifts - mają unikalny index bezpośednio ze wstrzelaniem IGNORE w bazie)
            int addedGlobal = 0;
            for (GlobalShift gs : backup.getGlobalShifts()) {
                gs.setId(0);
                long res = db.globalShiftDao().insertGlobalShift(gs);
                if (res != -1) {
                    addedGlobal++;
                }
            }
            summary.setGlobalShiftsAdded(addedGlobal);

            // 3. Spis budżetowych miesięcy i podsumowań
            int addedReports = 0;
            for (MonthlyReport mr : backup.getMonthlyReports()) {
                MonthlyReport current = db.monthlyReportDao().getReportForMonthSync(mr.getMonthYear());
                if (current == null) {
                    db.monthlyReportDao().insert(mr);
                    addedReports++;
                }
            }
            summary.setReportsAdded(addedReports);

            // 4. Ewentualne wpisy ubytkowo-napiwkowe
            int addedLosses = 0;
            List<Loss> existingLosses = db.lossDao().getAllLossesSync();
            Set<String> lossKeys = new HashSet<>();
            for (Loss l : existingLosses) {
                lossKeys.add(l.getDate() + "_" + l.getAmount());
            }
            for (Loss l : backup.getLosses()) {
                String lKey = l.getDate() + "_" + l.getAmount();
                if (!lossKeys.contains(lKey)) {
                    l.setId(0);
                    db.lossDao().insert(l);
                    lossKeys.add(lKey);
                    addedLosses++;
                }
            }
            summary.setLossesAdded(addedLosses);

            int addedTips = 0;
            List<Tip> existingTips = db.tipDao().getAllTipsSync();
            Set<String> tipKeys = new HashSet<>();
            for (Tip t : existingTips) {
                tipKeys.add(t.getDate() + "_" + t.getAmount());
            }
            for (Tip t : backup.getTips()) {
                String tKey = t.getDate() + "_" + t.getAmount();
                if (!tipKeys.contains(tKey)) {
                    t.setId(0);
                    db.tipDao().insert(t);
                    tipKeys.add(tKey);
                    addedTips++;
                }
            }
            summary.setTipsAdded(addedTips);

        });

        summary.setSuccess(true);
        Log.d(TAG, "Reanimacja w całości wyliczona i osadzona pomyślnie z tarczą! Podsumowanie importowe: " + summary.toUserFriendlySummary());
        return summary;
    }

    private String generateShiftKey(String date, String start) {
        return (date != null ? date.trim() : "") + "|" + (start != null ? start.trim() : "");
    }

    /**
     * Wzorcowa pigułka statystyk o efektach wariantu bezbłędnej infiltracji spoin w bazie.
     */
    public static class RestoreSummary {
        private boolean success = false;
        private String failedReason = null;
        private int shiftsAdded;
        private int shiftsSkipped;
        private int globalShiftsAdded;
        private int reportsAdded;
        private int lossesAdded;
        private int tipsAdded;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getFailedReason() { return failedReason; }
        public void setFailedReason(String failedReason) { this.failedReason = failedReason; }

        public int getShiftsAdded() { return shiftsAdded; }
        public void setShiftsAdded(int shiftsAdded) { this.shiftsAdded = shiftsAdded; }

        public int getShiftsSkipped() { return shiftsSkipped; }
        public void setShiftsSkipped(int shiftsSkipped) { this.shiftsSkipped = shiftsSkipped; }

        public int getGlobalShiftsAdded() { return globalShiftsAdded; }
        public void setGlobalShiftsAdded(int globalShiftsAdded) { this.globalShiftsAdded = globalShiftsAdded; }

        public int getReportsAdded() { return reportsAdded; }
        public void setReportsAdded(int reportsAdded) { this.reportsAdded = reportsAdded; }

        public int getLossesAdded() { return lossesAdded; }
        public void setLossesAdded(int lossesAdded) { this.lossesAdded = lossesAdded; }

        public int getTipsAdded() { return tipsAdded; }
        public void setTipsAdded(int tipsAdded) { this.tipsAdded = tipsAdded; }

        public String toUserFriendlySummary() {
            if (!success) {
                return "Błąd przywracania z kopii: " + (failedReason != null ? failedReason : "Nieznana awaria pliku");
            }
            return String.format(Locale.getDefault(),
                "🎉 Kopia bezpiecznie wczytana w mgnieniu oka!\n\n" +
                "➕ Przywrócone zmiany robocze: %d\n" +
                "⏭ Pominięte duplikaty z Twoich dat: %d\n" +
                "🍿 Zapisy współpracowników: +%d\n" +
                "📊 Odnowione zestawienia budżetu: +%d\n\n" +
                "Twoje dotychczasowe zmiany są nienaruszone!", 
                shiftsAdded, shiftsSkipped, globalShiftsAdded, reportsAdded);
        }
    }
}
