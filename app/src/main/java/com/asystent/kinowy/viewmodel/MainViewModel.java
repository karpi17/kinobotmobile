package com.asystent.kinowy.viewmodel;

import android.app.Application;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.asystent.kinowy.models.Loss;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.models.Tip;
import com.asystent.kinowy.network.ExcelParsingService;
import com.asystent.kinowy.repository.GmailRepository;
import com.asystent.kinowy.repository.LossRepository;
import com.asystent.kinowy.repository.ShiftRepository;
import com.asystent.kinowy.repository.TipRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Główny ViewModel aplikacji Asystent Kinowy.
 * <p>
 * Łączy trzy repozytoria i wystawia dane z bazy danych
 * jako {@link LiveData} do warstwy UI. Zarządza:
 * <ul>
 *   <li>Przekazywaniem tokena OAuth do {@link GmailRepository}</li>
 *   <li>Synchronizacją grafiku: Gmail → Excel parser → Room DB</li>
 *   <li>Obliczaniem wypłaty za bieżący miesiąc</li>
 * </ul>
 */
public class MainViewModel extends AndroidViewModel {

    private static final String TAG = "MainViewModel";

    // ─── Repozytoria ─────────────────────────────────────────────────────

    private final ShiftRepository shiftRepository;
    private final LossRepository lossRepository;
    private final TipRepository tipRepository;
    private final GmailRepository gmailRepository;

    // ─── Serwisy ─────────────────────────────────────────────────────────

    private final ExcelParsingService excelParsingService;
    private final ExecutorService executor;

    // ─── LiveData ────────────────────────────────────────────────────────

    private final LiveData<List<Shift>> allShifts;
    private final MediatorLiveData<List<Shift>> monthlyShifts;
    private final MediatorLiveData<Shift> nextShift;
    private final LiveData<List<Loss>> allLosses;
    private final LiveData<List<Tip>> allTips;
    private final MutableLiveData<String> syncStatus;

    // --- Kategoryzacja ---
    private final MediatorLiveData<List<Shift>> unknownShifts;

    // --- Raporty ---
    private final MutableLiveData<String> missingReportAlert;

    // ─── Finanse ─────────────────────────────────────────────────────────

    private final MutableLiveData<Float> hourlyRateLive;
    private final MutableLiveData<Integer> monthlyHoursGoal;
    private final MediatorLiveData<PayrollInfo> monthlyPayroll;
    private final MediatorLiveData<List<Loss>> monthlyLosses;
    private final MediatorLiveData<List<Tip>> monthlyTips;
    private final MutableLiveData<YearMonth> currentSelectedMonth;

    /** Nazwisko użytkownika w grafiku (ustawiane z ustawień / po rejestracji) */
    private String targetUserName;

    // ─── Konstruktor ─────────────────────────────────────────────────────

    public MainViewModel(@NonNull Application application) {
        super(application);
        currentSelectedMonth = new MutableLiveData<>(java.time.YearMonth.now());
        shiftRepository = new ShiftRepository(application);
        lossRepository = new LossRepository(application);
        tipRepository = new TipRepository(application);
        gmailRepository = new GmailRepository();
        excelParsingService = new ExcelParsingService();
        executor = Executors.newSingleThreadExecutor();

        allShifts = shiftRepository.getAllShifts();
        monthlyShifts = new MediatorLiveData<>();
        nextShift = new MediatorLiveData<>();
        allLosses = lossRepository.getAllLosses();
        allTips = tipRepository.getAllTips();
        syncStatus = new MutableLiveData<>();
        unknownShifts = new MediatorLiveData<>();
        missingReportAlert = new MutableLiveData<>();

        // ─── Finanse ─────────────────────────────────────────────────
        hourlyRateLive = new MutableLiveData<>(0f);
        monthlyHoursGoal = new MutableLiveData<>(100);
        monthlyPayroll = new MediatorLiveData<>();
        monthlyLosses = new MediatorLiveData<>();
        monthlyTips = new MediatorLiveData<>();

        setupNextShiftComputation();
        setupPayrollCalculation();
        setupMonthlyLosses();
        setupMonthlyTips();
        setupMonthlyShifts();
        setupUnknownShifts();
        checkMissingMonthlyReport();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PAYROLL — logika obliczania wypłaty
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Obiekt podsumowania wypłaty za bieżący miesiąc.
     */
    public static class PayrollInfo {
        private final double totalHours;
        private final float hourlyRate;
        private final double totalLosses;
        private final double totalTips;
        private final double netPay;

        public PayrollInfo(double totalHours, float hourlyRate, double totalLosses, double totalTips) {
            this.totalHours = totalHours;
            this.hourlyRate = hourlyRate;
            this.totalLosses = totalLosses;
            this.totalTips = totalTips;
            this.netPay = Math.max(0, (totalHours * hourlyRate) - totalLosses) + totalTips;
        }

        public double getTotalHours() { return totalHours; }
        public float getHourlyRate() { return hourlyRate; }
        public double getTotalLosses() { return totalLosses; }
        public double getTotalTips() { return totalTips; }
        public double getNetPay() { return netPay; }
    }

    /**
     * Konfiguruje MediatorLiveData, który reaguje na zmiany w:
     * allShifts, allLosses, hourlyRateLive oraz currentSelectedMonth
     * — i przelicza wypłatę za bieżący miesiąc.
     */
    private void setupPayrollCalculation() {
        monthlyPayroll.addSource(allShifts, shifts -> recalculatePayroll());
        monthlyPayroll.addSource(allLosses, losses -> recalculatePayroll());
        monthlyPayroll.addSource(allTips, tips -> recalculatePayroll());
        monthlyPayroll.addSource(hourlyRateLive, rate -> recalculatePayroll());
        monthlyPayroll.addSource(currentSelectedMonth, month -> recalculatePayroll());
    }

    /**
     * Konfiguruje MediatorLiveData filtrujący straty z wybranego miesiąca.
     */
    private void setupMonthlyLosses() {
        monthlyLosses.addSource(allLosses, losses -> updateMonthlyLosses(losses));
        monthlyLosses.addSource(currentSelectedMonth, month -> updateMonthlyLosses(allLosses.getValue()));
    }

    private void updateMonthlyLosses(List<Loss> losses) {
        if (losses == null) {
            monthlyLosses.setValue(new ArrayList<>());
            return;
        }
        String currentMonthPrefix = getCurrentMonthPrefix();
        List<Loss> filtered = new ArrayList<>();
        for (Loss loss : losses) {
            if (loss.getDate() != null && loss.getDate().startsWith(currentMonthPrefix)) {
                filtered.add(loss);
            }
        }
        monthlyLosses.setValue(filtered);
    }

    /**
     * Konfiguruje MediatorLiveData filtrujący zmiany z wybranego miesiąca.
     */
    private void setupMonthlyShifts() {
        monthlyShifts.addSource(allShifts, shifts -> updateMonthlyShifts(shifts));
        monthlyShifts.addSource(currentSelectedMonth, month -> updateMonthlyShifts(allShifts.getValue()));
    }

    private void updateMonthlyShifts(List<Shift> shifts) {
        if (shifts == null) {
            monthlyShifts.setValue(new ArrayList<>());
            return;
        }
        String currentMonthPrefix = getCurrentMonthPrefix();
        List<Shift> filtered = new ArrayList<>();
        for (Shift shift : shifts) {
            if (shift.getDate() != null && shift.getDate().startsWith(currentMonthPrefix)) {
                filtered.add(shift);
            }
        }
        monthlyShifts.setValue(filtered);
    }

    private void setupMonthlyTips() {
        monthlyTips.addSource(allTips, tips -> updateMonthlyTips(tips));
        monthlyTips.addSource(currentSelectedMonth, month -> updateMonthlyTips(allTips.getValue()));
    }

    private void updateMonthlyTips(List<Tip> tips) {
        if (tips == null) {
            monthlyTips.setValue(new ArrayList<>());
            return;
        }
        String currentMonthPrefix = getCurrentMonthPrefix();
        List<Tip> filtered = new ArrayList<>();
        for (Tip tip : tips) {
            if (tip.getDate() != null && tip.getDate().startsWith(currentMonthPrefix)) {
                filtered.add(tip);
            }
        }
        monthlyTips.setValue(filtered);
    }

    private void setupNextShiftComputation() {
        nextShift.addSource(allShifts, shifts -> {
            if (shifts == null || shifts.isEmpty()) {
                nextShift.setValue(null);
                return;
            }
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            Shift upcoming = null;
            java.time.LocalDateTime upcomingTime = null;

            for (Shift shift : shifts) {
                if (shift.getDate() == null || shift.getStartTime() == null) continue;
                try {
                    java.time.LocalDate date = java.time.LocalDate.parse(shift.getDate());
                    LocalTime time = LocalTime.parse(shift.getStartTime());
                    java.time.LocalDateTime shiftStart = java.time.LocalDateTime.of(date, time);
                    if (shiftStart.isAfter(now)) {
                        if (upcomingTime == null || shiftStart.isBefore(upcomingTime)) {
                            upcomingTime = shiftStart;
                            upcoming = shift;
                        }
                    }
                } catch (Exception ignored) { }
            }
            nextShift.setValue(upcoming);
        });
    }

    /**
     * Przelicza wypłatę: sumuje godziny z bieżącego miesiąca × stawka − straty.
     */
    private void recalculatePayroll() {
        List<Shift> shifts = allShifts.getValue();
        List<Loss> losses = allLosses.getValue();
        List<Tip> tips = allTips.getValue();
        Float rate = hourlyRateLive.getValue();

        if (rate == null) rate = 0f;
        String currentMonthPrefix = getCurrentMonthPrefix();

        // ─── Suma godzin ze zmian bieżącego miesiąca ─────────────────
        double totalHours = 0;
        if (shifts != null) {
            for (Shift shift : shifts) {
                if (shift.getDate() != null && shift.getDate().startsWith(currentMonthPrefix)) {
                    totalHours += calculateShiftHours(shift);
                }
            }
        }

        // ─── Suma strat z bieżącego miesiąca ────────────────────────
        double totalLosses = 0;
        if (losses != null) {
            for (Loss loss : losses) {
                if (loss.getDate() != null && loss.getDate().startsWith(currentMonthPrefix)) {
                    totalLosses += loss.getAmount();
                }
            }
        }

        // ─── Suma napiwków z bieżącego miesiąca ─────────────────────
        double totalTips = 0;
        if (tips != null) {
            for (Tip tip : tips) {
                if (tip.getDate() != null && tip.getDate().startsWith(currentMonthPrefix)) {
                    totalTips += tip.getAmount();
                }
            }
        }

        monthlyPayroll.setValue(new PayrollInfo(totalHours, rate, totalLosses, totalTips));
    }

    /**
     * Oblicza liczbę przepracowanych godzin z pojedynczej zmiany.
     * Obsługuje zmiany przechodzące przez północ (np. 22:00→06:00).
     */
    private double calculateShiftHours(Shift shift) {
        if (shift.isReplacement()) {
            return 0; // Nie liczymy godzin za zmianę oddaną
        }

        String startStr = shift.getStartTime();
        String endStr = shift.getEndTime();

        if (startStr == null || startStr.isEmpty() || endStr == null || endStr.isEmpty()) {
            return 0;
        }

        try {
            LocalTime start = LocalTime.parse(startStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime end = LocalTime.parse(endStr, DateTimeFormatter.ofPattern("HH:mm"));
            return ExcelParsingService.calculateHours(start, end);
        } catch (Exception e) {
            Log.w(TAG, "Nie można obliczyć godzin dla zmiany: " + startStr + "-" + endStr, e);
            return 0;
        }
    }

    /**
     * Zwraca prefiks wybranego miesiąca w formacie "yyyy-MM" do filtrowania dat ISO.
     */
    private String getCurrentMonthPrefix() {
        YearMonth selected = currentSelectedMonth.getValue();
        if (selected == null) selected = YearMonth.now();
        return String.format("%04d-%02d", selected.getYear(), selected.getMonthValue());
    }

    // ─── Finanse — publiczne API ─────────────────────────────────────────

    /**
     * Ustawia stawkę godzinową. Powoduje automatyczne przeliczenie wypłaty.
     */
    public void setHourlyRate(float rate) {
        hourlyRateLive.setValue(rate);
    }

    /**
     * LiveData z podsumowaniem wypłaty za bieżący miesiąc.
     * Reaguje automatycznie na zmiany w shifts, losses i stawce.
     */
    public LiveData<PayrollInfo> getMonthlyPayroll() {
        return monthlyPayroll;
    }
    public LiveData<List<Shift>> getMonthlyShifts() {
        return monthlyShifts;
    }
    /**
     * LiveData ze stratami z bieżącego miesiąca (do wyświetlenia w UI).
     */
    public LiveData<List<Loss>> getMonthlyLosses() {
        return monthlyLosses;
    }

    // ─── Cykl rozliczeniowy (Wybór miesiąca) ─────────────────────────────

    public LiveData<YearMonth> getCurrentSelectedMonth() {
        return currentSelectedMonth;
    }

    public void setCurrentSelectedMonth(YearMonth month) {
        currentSelectedMonth.setValue(month);
    }

    public void nextMonth() {
        YearMonth current = currentSelectedMonth.getValue();
        if (current != null) {
            currentSelectedMonth.setValue(current.plusMonths(1));
        }
    }

    public void previousMonth() {
        YearMonth current = currentSelectedMonth.getValue();
        if (current != null) {
            currentSelectedMonth.setValue(current.minusMonths(1));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LiveData — odczyty (istniejące)
    // ═══════════════════════════════════════════════════════════════════════

    public LiveData<List<Shift>> getAllShifts() {
        return allShifts;
    }

    public LiveData<List<Loss>> getAllLosses() {
        return allLosses;
    }

    /**
     * Status synchronizacji grafiku. Obserwuj w UI, by wyświetlać komunikaty.
     * Możliwe wartości: "syncing", "success:N" (N = liczba zmian), "error:msg".
     */
    public LiveData<List<Shift>> getUnknownShifts() {
        return unknownShifts;
    }

    private void setupUnknownShifts() {
        unknownShifts.addSource(allShifts, shifts -> updateUnknownShifts(shifts, currentSelectedMonth.getValue()));
        unknownShifts.addSource(currentSelectedMonth, month -> updateUnknownShifts(allShifts.getValue(), month));
    }

    private void updateUnknownShifts(List<Shift> shifts, java.time.YearMonth month) {
        if (shifts == null || month == null) {
            unknownShifts.setValue(new java.util.ArrayList<>());
            return;
        }

        String prefix = String.format("%04d-%02d", month.getYear(), month.getMonthValue());
        List<Shift> filtered = new java.util.ArrayList<>();
        for (Shift s : shifts) {
            if (s.getDate() != null && s.getDate().startsWith(prefix) && "UNKNOWN".equals(s.getCategory())) {
                filtered.add(s);
            }
        }
        unknownShifts.setValue(filtered);
    }

    public LiveData<String> getMissingReportAlert() {
        return missingReportAlert;
    }

    private void checkMissingMonthlyReport() {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.YearMonth lastMonth = java.time.YearMonth.from(today).minusMonths(1);
        String monthYear = String.format(java.util.Locale.US, "%04d-%02d", lastMonth.getYear(), lastMonth.getMonthValue());

        executor.execute(() -> {
            com.asystent.kinowy.db.AppDatabase db = com.asystent.kinowy.db.AppDatabase.getInstance(getApplication());
            com.asystent.kinowy.models.MonthlyReport report = db.monthlyReportDao().getReportByMonth(monthYear);
            if (report == null) {
                String displayMonth = lastMonth.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("pl", "PL"));
                displayMonth = displayMonth.substring(0, 1).toUpperCase() + displayMonth.substring(1);
                missingReportAlert.postValue("Wykryto brak raportu za " + displayMonth + " " + lastMonth.getYear() + "!");
            } else {
                missingReportAlert.postValue(null);
            }
        });
    }

    public void refreshMissingReportCheck() {
        checkMissingMonthlyReport();
    }

    public LiveData<String> getSyncStatus() {
        return syncStatus;
    }

    // ─── Ustawienia użytkownika ──────────────────────────────────────────

    public void setTargetUserName(String name) {
        this.targetUserName = name;
    }

    public String getTargetUserName() {
        return targetUserName;
    }

    // ─── Shift — operacje zapisu ─────────────────────────────────────────

    public void insertShift(Shift shift) { shiftRepository.insert(shift); }
    public void updateShift(Shift shift) { shiftRepository.update(shift); }
    public void deleteShift(Shift shift) { shiftRepository.delete(shift); }
    public void deleteAllShifts() { shiftRepository.deleteAll(); }

    public LiveData<List<Tip>> getMonthlyTips() {
        return monthlyTips;
    }

    public LiveData<Shift> getNextShift() {
        return nextShift;
    }

    public MutableLiveData<Integer> getMonthlyHoursGoal() {
        return monthlyHoursGoal;
    }

    // ─── Logika biznesowa — Dodawanie i Usuwanie ─────────────────────

    public void insertTip(Tip tip) {
        tipRepository.insert(tip);
    }

    public void insertLoss(Loss loss) { lossRepository.insert(loss); }
    public void updateLoss(Loss loss) { lossRepository.update(loss); }
    public void deleteLoss(Loss loss) { lossRepository.delete(loss); }
    public void deleteAllLosses() { lossRepository.deleteAll(); }

    // ═══════════════════════════════════════════════════════════════════════
    // Gmail / OAuth / Synchronizacja (istniejący kod)
    // ═══════════════════════════════════════════════════════════════════════

    public void setGmailAccessToken(String accessToken) {
        gmailRepository.setAccessToken(accessToken);
    }

    public boolean isGmailAuthenticated() {
        return gmailRepository.isAuthenticated();
    }

    public GmailRepository getGmailRepository() {
        return gmailRepository;
    }

    // ─── Synchronizacja grafiku (MULTI-FETCH) ─────────────────────────────

    public void syncSchedule() {
        if (targetUserName == null || targetUserName.isEmpty()) {
            syncStatus.setValue("error:Nie ustawiono nazwiska użytkownika");
            return;
        }
        if (!gmailRepository.isAuthenticated()) {
            syncStatus.setValue("error:Brak tokena OAuth — zaloguj się");
            return;
        }

        syncStatus.setValue("syncing");

        gmailRepository.fetchMessageList(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    syncStatus.postValue("error:Błąd API (" + response.code() + ")");
                    return;
                }

                JsonArray messages = response.body().getAsJsonArray("messages");
                if (messages == null || messages.size() == 0) {
                    syncStatus.postValue("error:Brak wiadomości z plikiem .xlsx");
                    return;
                }

                // Zaczynamy asynchroniczne pobieranie do 5 ostatnich załączników
                List<Shift> allParsedShifts = new ArrayList<>();
                fetchMultipleAttachments(messages, 0, 15, allParsedShifts);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchMessageList failed", t);
                syncStatus.postValue("error:" + t.getMessage());
            }
        });
    }

    /**
     * Rekurencyjna metoda pobierająca kolejne załączniki.
     * Wywołuje samą siebie dla następnego indeksu, aż osiągnie limit.
     */
    private void fetchMultipleAttachments(JsonArray messages, int index, int maxLimit, List<Shift> accumulatedShifts) {
        // Warunek końcowy: pobraliśmy limit plików lub skończyły się maile
        if (index >= maxLimit || index >= messages.size()) {
            processAndMergeAllShifts(accumulatedShifts);
            return;
        }

        String messageId = messages.get(index).getAsJsonObject().get("id").getAsString();

        gmailRepository.fetchMessageDetail(messageId, new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call, @NonNull Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String attachmentId = findXlsxAttachmentId(response.body());
                    if (attachmentId != null) {
                        // Pobierz sam plik
                        gmailRepository.fetchAttachment(messageId, attachmentId, new Callback<JsonObject>() {
                            @Override
                            public void onResponse(@NonNull Call<JsonObject> attachCall, @NonNull Response<JsonObject> attachResponse) {
                                if (attachResponse.isSuccessful() && attachResponse.body() != null) {
                                    String base64Data = attachResponse.body().get("data").getAsString();
                                    executor.execute(() -> {
                                        try {
                                            byte[] fileBytes = Base64.decode(base64Data, Base64.URL_SAFE);
                                            InputStream inputStream = new ByteArrayInputStream(fileBytes);
                                            List<Shift> parsed = excelParsingService.parseSchedule(inputStream, targetUserName);
                                            accumulatedShifts.addAll(parsed);
                                        } catch (Exception e) {
                                            Log.e(TAG, "Błąd parsowania pliku " + index, e);
                                        } finally {
                                            // Niezależnie czy się udało, idziemy do następnego pliku
                                            fetchMultipleAttachments(messages, index + 1, maxLimit, accumulatedShifts);
                                        }
                                    });
                                } else {
                                    // Błąd pobierania pliku, idziemy dalej
                                    fetchMultipleAttachments(messages, index + 1, maxLimit, accumulatedShifts);
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<JsonObject> attachCall, @NonNull Throwable t) {
                                fetchMultipleAttachments(messages, index + 1, maxLimit, accumulatedShifts);
                            }
                        });
                        return; // Wychodzimy, bo fetchAttachment zawoła kolejny krok
                    }
                }
                // Jeśli nie ma załącznika lub błąd, idziemy do następnego maila
                fetchMultipleAttachments(messages, index + 1, maxLimit, accumulatedShifts);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                fetchMultipleAttachments(messages, index + 1, maxLimit, accumulatedShifts);
            }
        });
    }

    /**
     * Łączy wszystkie pobrane grafiki i nakłada na nie ręczne zmiany użytkownika.
     */
    private void processAndMergeAllShifts(List<Shift> allParsedShifts) {
        executor.execute(() -> {
            try {
                final List<Shift> currentDbShifts = allShifts.getValue();
                List<Shift> finalShifts = new ArrayList<>();
                List<Shift> keptManualShifts = new ArrayList<>();

                // 1. ZATRZYMUJEMY RĘCZNE ZMIANY (zastępstwa, edycje)
                if (currentDbShifts != null) {
                    for (Shift s : currentDbShifts) {
                        if (s.isReplacement() || s.isManual()) {
                            keptManualShifts.add(s);
                        }
                    }
                }
                finalShifts.addAll(keptManualShifts);

                // 2. FILTRUJEMY DUPLIKATY Z EXCELA (Nowszy mail wygrywa)
                // Maile były pobierane od najnowszego do najstarszego, więc pierwsze na liście są najświeższe.
                java.util.Map<String, Shift> uniqueExcelShifts = new java.util.HashMap<>();
                for (Shift parsed : allParsedShifts) {
                    // Jeśli jeszcze nie mamy zmiany na ten dzień z Excela, dodajemy
                    if (!uniqueExcelShifts.containsKey(parsed.getDate())) {
                        uniqueExcelShifts.put(parsed.getDate(), parsed);
                    }
                }

                // 3. ŁĄCZYMY EXCELA Z RĘCZNYMI
                for (Shift excelShift : uniqueExcelShifts.values()) {
                    boolean conflict = false;
                    for (Shift manualShift : keptManualShifts) {
                        if (manualShift.getDate() != null && manualShift.getDate().equals(excelShift.getDate())) {
                            conflict = true;
                            break;
                        }
                    }
                    if (!conflict) {
                        finalShifts.add(excelShift);
                    }
                }

                // 4. ZAPIS DO BAZY
                shiftRepository.deleteAll();
                for (Shift shift : finalShifts) {
                    shiftRepository.insert(shift);
                }

                syncStatus.postValue("success:" + finalShifts.size());
                Log.d(TAG, "Multi-Sync complete. Merged " + finalShifts.size() + " total shifts.");

            } catch (Exception e) {
                Log.e(TAG, "Error saving merged schedule", e);
                syncStatus.postValue("error:" + e.getMessage());
            }
        });
    }

    private String findXlsxAttachmentId(JsonObject messageDetail) {
        try {
            JsonObject payload = messageDetail.getAsJsonObject("payload");
            if (payload == null) return null;

            JsonArray parts = payload.getAsJsonArray("parts");
            if (parts == null) return null;

            for (JsonElement partEl : parts) {
                JsonObject part = partEl.getAsJsonObject();
                String filename = part.has("filename") ? part.get("filename").getAsString() : "";
                if (filename.toLowerCase().endsWith(".xlsx")) {
                    JsonObject body = part.getAsJsonObject("body");
                    if (body != null && body.has("attachmentId")) {
                        return body.get("attachmentId").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding xlsx attachment", e);
        }
        return null;
    }

    public void exportToCalendar(android.content.Context context, List<Shift> shifts) {
        if (shifts == null || shifts.isEmpty()) return;

        android.content.ContentResolver cr = context.getContentResolver();
        java.util.TimeZone timeZone = java.util.TimeZone.getDefault();

        for (Shift shift : shifts) {
            // Walidacja danych i pomijanie zmian, które oddajesz (zastępstwa)
            if (shift.getDate() == null || shift.getStartTime() == null || shift.getEndTime() == null) continue;
            if (shift.isReplacement()) continue;

            try {
                LocalDate date = LocalDate.parse(shift.getDate());
                LocalTime startTime = LocalTime.parse(shift.getStartTime());
                LocalTime endTime = LocalTime.parse(shift.getEndTime());

                java.time.LocalDateTime startDateTime = java.time.LocalDateTime.of(date, startTime);
                java.time.LocalDateTime endDateTime = java.time.LocalDateTime.of(date, endTime);

                // Tip: Obsługa "nocek" w kinie (jeśli koniec jest przed startem, znaczy że zmiana kończy się jutro)
                if (endTime.isBefore(startTime)) {
                    endDateTime = endDateTime.plusDays(1);
                }

                long startMillis = startDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                long endMillis = endDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.CalendarContract.Events.DTSTART, startMillis);
                values.put(android.provider.CalendarContract.Events.DTEND, endMillis);
                values.put(android.provider.CalendarContract.Events.TITLE, "[Kino] " + (shift.getDescription() != null ? shift.getDescription() : "Zmiana"));
                values.put(android.provider.CalendarContract.Events.CALENDAR_ID, 1); // Zazwyczaj 1 to główny kalendarz urządzenia
                values.put(android.provider.CalendarContract.Events.EVENT_TIMEZONE, timeZone.getID());

                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    cr.insert(android.provider.CalendarContract.Events.CONTENT_URI, values);
                }
            } catch (Exception e) {
                Log.e(TAG, "Błąd eksportu zmiany do kalendarza", e);
            }
        }
    }
}
