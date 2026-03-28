package com.asystent.kinowy.network;

import android.util.Log;

import com.asystent.kinowy.models.Shift;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serwis parsujący pliki .xlsx z grafikami pracy kina.
 * <p>
 * Logika oparta na referencyjnej implementacji w Pythonie (bot.py — parse_schedule / calculate_hours).
 * Korzysta z Apache POI do odczytu plików Excel.
 *
 * <h3>Struktura pliku .xlsx:</h3>
 * <ul>
 *   <li><b>Wiersz 3</b> (0-indexed): daty w kolumnach 2, 5, 8, 11, 14, 17, 20 (co 3 kolumny, 7 dni)</li>
 *   <li><b>Wiersz 4</b>: nazwy dni tygodnia</li>
 *   <li><b>Wiersz 5+</b>: dane pracowników — kol[0]=lp, kol[1]=imię i nazwisko</li>
 *   <li>Dla każdego dnia: kol[base]=typ zmiany, kol[base+1]=start, kol[base+2]=koniec</li>
 * </ul>
 */
public class ExcelParsingService {

    private static final String TAG = "ExcelParsingService";

    /** Indeks wiersza z datami (0-indexed) */
    private static final int ROW_DATES_IDX = 3;

    /** Pierwszy wiersz z danymi pracowników (0-indexed) */
    private static final int ROW_DATA_START = 5;

    /** Pierwsza kolumna z danymi dni */
    private static final int FIRST_DAY_COL = 2;

    /** Krok między kolumnami dni (typ, start, koniec) */
    private static final int DAY_COL_STEP = 3;

    /** Liczba dni w grafiku tygodniowym */
    private static final int DAYS_COUNT = 7;

    /** Skróty nazw zmian (odpowiednik SKROTY_NAZW z Pythona) */
    private static final Map<String, String> SKROTY_NAZW = new HashMap<>();
    static {
        SKROTY_NAZW.put("BAR/OW", "BAR+");
        SKROTY_NAZW.put("SP + bar", "SP+");
        SKROTY_NAZW.put("OBSŁUGA", "OBS");
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ─── Publiczne API ───────────────────────────────────────────────────

    /**
     * Wynik parsowania grafiku. Zawiera mapę pracownik → lista zmian
     * oraz listę wszystkich dat.
     */
    public static class ParseResult {
        private final Map<String, List<Shift>> scheduleByName;
        private final List<String> allDates;
        private final List<String> foundNames;

        public ParseResult(Map<String, List<Shift>> scheduleByName,
                           List<String> allDates,
                           List<String> foundNames) {
            this.scheduleByName = scheduleByName;
            this.allDates = allDates;
            this.foundNames = foundNames;
        }

        public Map<String, List<Shift>> getScheduleByName() { return scheduleByName; }
        public List<String> getAllDates() { return allDates; }
        public List<String> getFoundNames() { return foundNames; }
    }

    /**
     * Parsuje plik .xlsx i zwraca zmiany dla podanego pracownika.
     * <p>
     * Odpowiednik Pythonowego {@code parse_schedule(filepath)} + filtrowanie po nazwisku.
     *
     * @param excelFileStream strumień z plikiem .xlsx
     * @param targetUserName  imię i nazwisko pracownika (case-insensitive, z/bez kropki)
     * @return lista obiektów {@link Shift} dla danego pracownika, posortowana wg daty
     */
    public List<Shift> parseSchedule(InputStream excelFileStream, String targetUserName) {
        ParseResult result = parseFullSchedule(excelFileStream);
        if (result == null) return new ArrayList<>();

        // Szukamy pracownika (case-insensitive, ignorujemy kropki)
        String targetNorm = normalize(targetUserName);
        for (Map.Entry<String, List<Shift>> entry : result.scheduleByName.entrySet()) {
            if (normalize(entry.getKey()).equals(targetNorm)) {
                return entry.getValue();
            }
        }

        Log.w(TAG, "Nie znaleziono pracownika: " + targetUserName);
        return new ArrayList<>();
    }

    /**
     * Parsuje plik .xlsx i zwraca pełny wynik (wszyscy pracownicy).
     *
     * @param excelFileStream strumień z plikiem .xlsx
     * @return {@link ParseResult} lub {@code null} w przypadku błędu
     */
    public ParseResult parseFullSchedule(InputStream excelFileStream) {
        Map<String, List<Shift>> scheduleByName = new LinkedHashMap<>();
        List<String> allDates = new ArrayList<>();
        List<String> foundNames = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(excelFileStream)) {

            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                if (sheet.getPhysicalNumberOfRows() < ROW_DATA_START) continue;

                // --- 1. Odczytaj daty z wiersza ROW_DATES_IDX ---
                Map<Integer, String> colToDate = readDateHeaders(sheet);
                allDates.addAll(colToDate.values());

                // --- 2. Iteracja po wierszach pracowników ---
                for (int rowIdx = ROW_DATA_START; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                    Row row = sheet.getRow(rowIdx);
                    if (row == null) continue;

                    // Kolumna 0 = lp (musi być liczbą)
                    String lpStr = getCellString(row, 0);
                    if (lpStr.isEmpty() || !Character.isDigit(lpStr.charAt(0))) continue;

                    // Kolumna 1 = nazwisko i imię
                    String name = getCellString(row, 1).trim();
                    if (name.isEmpty()) continue;

                    if (!foundNames.contains(name)) foundNames.add(name);
                    if (!scheduleByName.containsKey(name)) {
                        scheduleByName.put(name, new ArrayList<>());
                    }

                    // --- 3. Dla każdego dnia parsuj (typ, start, koniec) ---
                    for (Map.Entry<Integer, String> dateEntry : colToDate.entrySet()) {
                        int colIdx = dateEntry.getKey();
                        String dateStr = dateEntry.getValue();

                        Shift shift = parseShiftFromRow(row, colIdx, dateStr);
                        if (shift != null) {
                            // Sprawdź duplikaty (lepsze dane mają priorytet)
                            List<Shift> existingShifts = scheduleByName.get(name);
                            addOrReplace(existingShifts, shift);
                        }
                    }
                }
            }

            // Sortuj daty
            allDates.sort(String::compareTo);

        } catch (Exception e) {
            Log.e(TAG, "Błąd parsowania pliku Excel", e);
            return null;
        }

        return new ParseResult(scheduleByName, allDates, foundNames);
    }

    // ─── Parsowanie nagłówków dat ────────────────────────────────────────

    /**
     * Odczytuje daty z wiersza ROW_DATES_IDX.
     * Daty są w kolumnach 2, 5, 8, 11, 14, 17, 20 (co 3 kolumny).
     */
    private Map<Integer, String> readDateHeaders(Sheet sheet) {
        Map<Integer, String> colToDate = new LinkedHashMap<>();
        Row dateRow = sheet.getRow(ROW_DATES_IDX);
        if (dateRow == null) return colToDate;

        int currentCol = FIRST_DAY_COL;
        for (int i = 0; i < DAYS_COUNT; i++) {
            if (currentCol >= dateRow.getLastCellNum()) break;

            Cell cell = dateRow.getCell(currentCol);
            if (cell != null) {
                String dateStr = extractDateFromCell(cell);
                if (dateStr != null && !dateStr.isEmpty()) {
                    colToDate.put(currentCol, dateStr);
                }
            }
            currentCol += DAY_COL_STEP;
        }

        return colToDate;
    }

    /**
     * Wyciąga datę z komórki jako String w formacie yyyy-MM-dd.
     * Komórka może być typem DATE (Excel datetime) lub STRING.
     */
    private String extractDateFromCell(Cell cell) {
        if (cell == null) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                Date date = cell.getDateCellValue();
                LocalDate ld = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                return ld.format(DATE_FMT);
            } else if (cell.getCellType() == CellType.STRING) {
                String val = cell.getStringCellValue().trim().split(" ")[0];
                if (val.isEmpty() || val.equalsIgnoreCase("nan")) return null;
                return val;
            }
        } catch (Exception e) {
            Log.w(TAG, "Nie można odczytać daty z komórki: " + cell, e);
        }
        return null;
    }

    // ─── Parsowanie zmiany z wiersza ─────────────────────────────────────

    /**
     * Parsuje pojedynczą zmianę z wiersza dla danej daty.
     *
     * @param row     wiersz Excel
     * @param colIdx  kolumna bazowa (typ zmiany)
     * @param dateStr data zmiany (yyyy-MM-dd)
     * @return obiekt Shift lub null jeśli brak danych
     */
    private Shift parseShiftFromRow(Row row, int colIdx, String dateStr) {
        // col[colIdx]   = typ zmiany (BAR, OW, SP, BAR/OW...)
        // col[colIdx+1] = godzina startu
        // col[colIdx+2] = godzina końca
        String typ = getCellString(row, colIdx).trim();
        LocalTime startTime = extractTimeFromCell(row, colIdx + 1);
        LocalTime endTime = extractTimeFromCell(row, colIdx + 2);

        // Pomijamy puste komórki
        if (typ.isEmpty() && startTime == null) return null;

        // Budowanie opisu (odpowiednik Pythonowego `desc`)
        String startStr = startTime != null ? startTime.format(TIME_FMT) : "";
        String endStr = endTime != null ? endTime.format(TIME_FMT) : "";

        StringBuilder desc = new StringBuilder(typ);
        if (!startStr.isEmpty()) {
            desc.append(" (").append(startStr);
            if (!endStr.isEmpty()) desc.append("-").append(endStr);
            desc.append(")");
        }

        // Kategoryzacja zmian
        String category = "UNKNOWN";
        String typUpper = typ.trim().toUpperCase();
        if (typUpper.matches(".*\\bBAR\\b.*")) {
            category = "BAR";
        } else if (typUpper.matches(".*\\bOW\\b.*") || typUpper.matches(".*\\bSP\\b.*")) {
            category = "OW";
        }

        // Utwórz Shift (godziny obliczane na żądanie przez calculateHours())
        // confirmed = false (do potwierdzenia przez użytkownika)
        return new Shift(dateStr, startStr, endStr, desc.toString(), false, false, category);
    }

    /**
     * Wyciąga godzinę (LocalTime) z komórki Excel.
     * Komórka może być typem TIME, DATETIME, STRING lub liczbą (ułamek dnia).
     */
    private LocalTime extractTimeFromCell(Row row, int colIdx) {
        if (row == null) return null;
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                // Excel przechowuje czas jako ułamek dnia (0.5 = 12:00)
                // lub jako datę (1900-01-01 00:00 = midnight)
                if (DateUtil.isCellDateFormatted(cell)) {
                    Date date = cell.getDateCellValue();
                    LocalTime lt = date.toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalTime();
                    return lt;
                } else {
                    // Czysta wartość liczbowa — interpretujemy jako ułamek dnia
                    double val = cell.getNumericCellValue();
                    if (val >= 0 && val < 1) {
                        int totalMinutes = (int) Math.round(val * 24 * 60);
                        return LocalTime.of(totalMinutes / 60, totalMinutes % 60);
                    }
                }
            } else if (cell.getCellType() == CellType.STRING) {
                String raw = cell.getStringCellValue().trim();
                if (raw.isEmpty() || raw.equalsIgnoreCase("nan")) return null;
                // Weź ostatnie 5 znaków (HH:MM) — odpowiednik Pythonowego split(' ')[-1][:5]
                String clean = raw.contains(" ") ? raw.substring(raw.lastIndexOf(' ') + 1) : raw;
                if (clean.length() > 5) clean = clean.substring(0, 5);
                return LocalTime.parse(clean, TIME_FMT);
            }
        } catch (Exception e) {
            Log.w(TAG, "Nie można odczytać czasu z komórki [" + colIdx + "]: " + cell, e);
        }
        return null;
    }

    // ─── Obliczanie godzin ───────────────────────────────────────────────

    /**
     * Oblicza liczbę przepracowanych godzin na podstawie czasu start/koniec.
     * <p>
     * Odpowiednik Pythonowego {@code calculate_hours(start_time, end_time)}.
     * Obsługuje zmiany przechodzące przez północ (np. 22:00→06:00).
     *
     * @param startTime godzina rozpoczęcia
     * @param endTime   godzina zakończenia
     * @return liczba godzin (z dokładnością do 2 miejsc po przecinku)
     */
    public static double calculateHours(LocalTime startTime, LocalTime endTime) {
        if (startTime == null || endTime == null) return 0;

        Duration duration;
        if (endTime.isBefore(startTime)) {
            // Zmiana przechodzi przez północ: 22:00→06:00 = 8h
            Duration toMidnight = Duration.between(startTime, LocalTime.MAX).plusNanos(1);
            Duration fromMidnight = Duration.between(LocalTime.MIN, endTime);
            duration = toMidnight.plus(fromMidnight);
        } else {
            duration = Duration.between(startTime, endTime);
        }

        double hours = duration.toMinutes() / 60.0;
        return Math.round(hours * 100.0) / 100.0; // zaokrąglij do 2 miejsc
    }

    // ─── Metody pomocnicze ───────────────────────────────────────────────

    /**
     * Pobiera wartość komórki jako String.
     * Obsługuje typy: STRING, NUMERIC, BOOLEAN, BLANK.
     */
    private String getCellString(Row row, int colIdx) {
        if (row == null) return "";
        Cell cell = row.getCell(colIdx);
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                String val = cell.getStringCellValue();
                return (val != null && !val.equalsIgnoreCase("nan")) ? val : "";
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return ""; // Daty obsługujemy osobno
                }
                double num = cell.getNumericCellValue();
                if (num == (int) num) return String.valueOf((int) num);
                return String.valueOf(num);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case BLANK:
            default:
                return "";
        }
    }

    /**
     * Normalizuje nazwisko do porównania (lowercase, bez kropek, przycięte).
     */
    private String normalize(String name) {
        if (name == null) return "";
        return name.toLowerCase().replace(".", "").trim();
    }

    /**
     * Dodaje zmianę do listy lub zastępuje istniejącą (dla tego samego dnia),
     * jeśli nowa ma lepsze dane (godziny startu/końca).
     */
    private void addOrReplace(List<Shift> shifts, Shift newShift) {
        for (int i = 0; i < shifts.size(); i++) {
            if (shifts.get(i).getDate().equals(newShift.getDate())) {
                // Zastąp tylko jeśli nowa zmiana ma godziny, a stara nie
                Shift existing = shifts.get(i);
                boolean existingHasTime = !existing.getStartTime().isEmpty();
                boolean newHasTime = !newShift.getStartTime().isEmpty();
                if (!existingHasTime || newHasTime) {
                    shifts.set(i, newShift);
                }
                return;
            }
        }
        shifts.add(newShift);
    }

    /**
     * Zwraca mapę skrótów nazw zmian.
     * Odpowiednik Pythonowego {@code SKROTY_NAZW}.
     */
    public static Map<String, String> getSkrotyNazw() {
        return new HashMap<>(SKROTY_NAZW);
    }
}
