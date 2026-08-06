package com.asystent.kinowy.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Kontenerowy model danych reprezentujący kompletny zrzut stanu aplikacji Kinobot,
 * dostosowany pod ciche szyfrowanie i bezpardonowe rozpakowywanie przez GSON w systemowych mackach kopii JSON-a.
 */
public class BackupData {

    private int backupSchemaVersion = 1; // numer wersjowania formatu backupu pod przyszłościowe rozszerzenia i migracje
    private String exportTimestamp;      // dokładny sygnaturowy czas zrobienia kopii
    private String appVersionName;       // wersja kinobota w momencie potoku zapasowego (np. v3.5)

    private List<Shift> shifts = new ArrayList<>();
    private List<GlobalShift> globalShifts = new ArrayList<>();
    private List<MonthlyReport> monthlyReports = new ArrayList<>();
    private List<Loss> losses = new ArrayList<>();
    private List<Tip> tips = new ArrayList<>();

    public BackupData() {
        // Pusty konstruktor na potyły zrębów rozbierających obiekt JSON we właściwą klamrę (GSON deserializacja)
    }

    public int getBackupSchemaVersion() {
        return backupSchemaVersion;
    }

    public void setBackupSchemaVersion(int backupSchemaVersion) {
        this.backupSchemaVersion = backupSchemaVersion;
    }

    public String getExportTimestamp() {
        return exportTimestamp;
    }

    public void setExportTimestamp(String exportTimestamp) {
        this.exportTimestamp = exportTimestamp;
    }

    public String getAppVersionName() {
        return appVersionName;
    }

    public void setAppVersionName(String appVersionName) {
        this.appVersionName = appVersionName;
    }

    public List<Shift> getShifts() {
        return shifts != null ? shifts : new ArrayList<>();
    }

    public void setShifts(List<Shift> shifts) {
        this.shifts = shifts;
    }

    public List<GlobalShift> getGlobalShifts() {
        return globalShifts != null ? globalShifts : new ArrayList<>();
    }

    public void setGlobalShifts(List<GlobalShift> globalShifts) {
        this.globalShifts = globalShifts;
    }

    public List<MonthlyReport> getMonthlyReports() {
        return monthlyReports != null ? monthlyReports : new ArrayList<>();
    }

    public void setMonthlyReports(List<MonthlyReport> monthlyReports) {
        this.monthlyReports = monthlyReports;
    }

    public List<Loss> getLosses() {
        return losses != null ? losses : new ArrayList<>();
    }

    public void setLosses(List<Loss> losses) {
        this.losses = losses;
    }

    public List<Tip> getTips() {
        return tips != null ? tips : new ArrayList<>();
    }

    public void setTips(List<Tip> tips) {
        this.tips = tips;
    }
}
