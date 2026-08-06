package com.asystent.kinowy.parsers;

import android.util.Log;

import com.asystent.kinowy.models.GlobalShift;
import com.asystent.kinowy.models.Shift;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser dla nowego formatu grafików kinowych (pliki XLS / XLSX).
 * <p>
 * Stosuje reguły biznesowe:
 * <ul>
 *   <li>Miesiąc pobierany z nagłówka, rok domyślnie bieżący</li>
 *   <li>Kolumna użytkownika określana na podstawie stanowiska (Team Leader) oraz imienia</li>
 *   <li>Wszyscy pracownicy przetwarzani jako {@link GlobalShift}</li>
 *   <li>Wszyscy koledzy pracujący w danym dniu agregowani do closing_crew</li>
 *   <li>Obsługa open (09:00-16:00), close (17:00-01:00), close od HH, open do HH, oraz zakresów HH-HH</li>
 * </ul>
 */
public class NewFormatExcelParser implements ScheduleParser {

    private static final String TAG = "NewFormatExcelParser";

    // Wzorce regex dla godzin (wspiera minuty np. 10.30, 10:30 oraz różne separatory np. -, /, \)
    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d{1,2})(?:[:.](\\d{2}))?\\s*[-–—/\\\\]\\s*(\\d{1,2})(?:[:.](\\d{2}))?");
    private static final Pattern CLOSE_OD_PATTERN = Pattern.compile("close\\s+od\\s+(\\d{1,2})(?:[:.](\\d{2}))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPEN_DO_PATTERN = Pattern.compile("open\\s+do\\s+(\\d{1,2})(?:[:.](\\d{2}))?", Pattern.CASE_INSENSITIVE);

    public static class EmployeeHeader {
        public final int colIndex;
        public final String name;
        public final String position;

        public EmployeeHeader(int colIndex, String name, String position) {
            this.colIndex = colIndex;
            this.name = name != null ? name.trim() : "";
            this.position = position != null ? position.trim() : "";
        }
    }

    public static class ParsedShiftInfo {
        public String startTime;
        public String endTime;
        public boolean isClosingShift;
        public String description;
        public String category;
        public boolean isShift;

        public ParsedShiftInfo(boolean isShift) {
            this.isShift = isShift;
        }

        public ParsedShiftInfo(String startTime, String endTime, boolean isClosingShift, String description, String category) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.isClosingShift = isClosingShift;
            this.description = description;
            this.category = category != null ? category : "UNKNOWN";
            this.isShift = true;
        }
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        String lower = mimeType.toLowerCase();
        return lower.contains("spreadsheet") || lower.contains("excel")
                || lower.contains("xlsx") || lower.contains("xls");
    }

    @Override
    public ScheduleParseResult parse(InputStream inputStream, ScheduleParseOptions options) throws Exception {
        Log.d(TAG, "Rozpoczynanie parsowania XLS/XLSX nowego formatu...");
        List<ParserWarning> warnings = new ArrayList<>();

        Workbook workbook = WorkbookFactory.create(inputStream);
        Sheet sheet = workbook.getSheetAt(0);

        // 1. Wykrycie miesiąca i roku
        int currentYear = (options != null && options.getSelectedYear() > 0) ? options.getSelectedYear() : Year.now().getValue();
        int monthValue = parseMonthFromSheet(sheet);
        if (monthValue == -1) {
            monthValue = LocalDate.now().getMonthValue();
            warnings.add(new ParserWarning(ParserWarning.Type.OTHER,
                    "Nie wykryto nazwy miesiąca w nagłówku. Przyjęto bieżący miesiąc."));
        }

        // 2. Parsowanie nagłówków pracowników (wiersz 0 i wiersz 1)
        List<EmployeeHeader> employees = parseEmployeeHeaders(sheet);
        if (employees.isEmpty()) {
            throw new IllegalArgumentException("Nie znaleziono kolumn z pracownikami w pliku.");
        }

        String targetUserName = options != null ? options.getTargetUserName() : "Kacper";
        String targetRole = options != null ? options.getTargetRole() : null;
        EmployeeHeader targetUserCol = findTargetUserColumn(employees, targetUserName, targetRole);

        if (targetUserCol == null) {
            warnings.add(new ParserWarning(ParserWarning.Type.UNKNOWN_NAME,
                    "Nie znaleziono w grafiku pracownika o imieniu: " + targetUserName + ". Używam pierwszej dostępnej kolumny Team Leader."));
            targetUserCol = employees.stream()
                    .filter(e -> e.position.equalsIgnoreCase("Team Leader"))
                    .findFirst()
                    .orElse(employees.get(0));
        }

        Log.d(TAG, "Wybrana kolumna użytkownika: " + targetUserCol.name + " (" + targetUserCol.position + ") na kolumnie " + targetUserCol.colIndex);

        Map<String, List<Shift>> scheduleByName = new LinkedHashMap<>();
        List<String> allDates = new ArrayList<>();
        List<String> foundNames = new ArrayList<>();
        List<GlobalShift> allGlobalShifts = new ArrayList<>();
        List<Shift> targetUserShifts = new ArrayList<>();

        for (EmployeeHeader emp : employees) {
            foundNames.add(emp.name);
            scheduleByName.put(emp.name, new ArrayList<>());
        }

        // 3. Iteracja po wierszach danych (dni miesiąca)
        Map<String, List<String>> dailyCrewMap = new HashMap<>(); // date -> list of names working

        for (int r = 2; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            int dayNum = parseDayNumber(row.getCell(0));
            if (dayNum < 1 || dayNum > 31) {
                // Może być w kolumnie 1 jeśli kolumna 0 to np. pusta
                dayNum = parseDayNumber(row.getCell(1));
            }
            if (dayNum < 1 || dayNum > 31) {
                continue; // Pomijamy wiersze sumaryczne i puste
            }

            String dateStr = String.format(Locale.US, "%04d-%02d-%02d", currentYear, monthValue, dayNum);
            if (!allDates.contains(dateStr)) {
                allDates.add(dateStr);
            }

            List<String> coworkersForDay = new ArrayList<>();

            // Najpierw czytamy i parsujemy wszystkich pracowników dla tej daty
            for (EmployeeHeader emp : employees) {
                Cell cell = row.getCell(emp.colIndex);
                String rawText = getCellValueAsString(cell);
                ParsedShiftInfo info = parseShiftText(rawText);

                if (info.isShift) {
                    GlobalShift gs = new GlobalShift(
                            emp.name,
                            dateStr,
                            info.startTime,
                            info.endTime,
                            info.category
                    );
                    gs.setManuallyEdited(false);
                    allGlobalShifts.add(gs);

                    // Jeśli to nie sam użytkownik i też zamyka, dodajemy imię do ekipy zamykającej
                    if (!emp.name.equalsIgnoreCase(targetUserCol.name) && info.isClosingShift) {
                        if (!coworkersForDay.contains(emp.name)) {
                            coworkersForDay.add(emp.name);
                        }
                    }
                }
            }

            dailyCrewMap.put(dateStr, coworkersForDay);
        }

        // 4. Budujemy obiekty Shift dla docelowego użytkownika
        for (int r = 2; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            int dayNum = parseDayNumber(row.getCell(0));
            if (dayNum < 1 || dayNum > 31) {
                dayNum = parseDayNumber(row.getCell(1));
            }
            if (dayNum < 1 || dayNum > 31) continue;

            String dateStr = String.format(Locale.US, "%04d-%02d-%02d", currentYear, monthValue, dayNum);
            Cell userCell = row.getCell(targetUserCol.colIndex);
            String rawText = getCellValueAsString(userCell);
            ParsedShiftInfo info = parseShiftText(rawText);

            if (info.isShift) {
                Shift userShift = new Shift(
                        dateStr,
                        info.startTime,
                        info.endTime,
                        info.description != null ? info.description : "",
                        true,
                        false,
                        info.category
                );
                userShift.setClosingShift(info.isClosingShift);

                if (info.isClosingShift) {
                    List<String> coworkers = dailyCrewMap.get(dateStr);
                    if (coworkers != null && !coworkers.isEmpty()) {
                        userShift.setClosingCrew(String.join(", ", coworkers));
                    }
                }

                targetUserShifts.add(userShift);
                scheduleByName.get(targetUserCol.name).add(userShift);
            } else {
                // Pokazujemy w podglądzie jako WOLNE (żeby zachować ciągłość dni)
                Shift userShift = new Shift(
                        dateStr,
                        "",
                        "",
                        "WOLNE",
                        false,
                        false,
                        "UNKNOWN"
                );
                targetUserShifts.add(userShift);
                scheduleByName.get(targetUserCol.name).add(userShift);
            }
        }

        workbook.close();

        ScheduleParseResult result = new ScheduleParseResult(
                scheduleByName,
                allDates,
                foundNames,
                allGlobalShifts,
                warnings,
                0.95f,
                "Nowy Grafik Excel (XLS/XLSX)"
        );
        result.setTargetUserShifts(targetUserShifts);

        Log.d(TAG, "============ PODSUMOWANIE PARSOWANIA EXCEL ============");
        Log.d(TAG, "Wykryte osoby (" + employees.size() + "): " + foundNames.toString());
        Log.d(TAG, "Zmiany użytkownika: " + targetUserShifts.size());
        for (int i = 0; i < targetUserShifts.size(); i++) {
            Shift s = targetUserShifts.get(i);
            String ekipa = s.getClosingCrew() != null ? s.getClosingCrew() : "Brak danych";
            Log.d(TAG, "  " + (i + 1) + ". " + s.getDate() + " " + s.getStartTime() + "-" + s.getEndTime() + " (" + s.getCategory() + ") | Ekipa: " + ekipa);
        }
        Log.d(TAG, "=======================================================");

        return result;
    }

    private int parseMonthFromSheet(Sheet sheet) {
        String[] monthsEn = {"january", "february", "march", "april", "may", "june",
                "july", "august", "september", "october", "november", "december"};
        String[] monthsPl = {"styczeń", "luty", "marzec", "kwiecień", "maj", "czerwiec",
                "lipiec", "sierpień", "wrzesień", "październik", "listopad", "grudzień"};

        // Skanujemy szerszy obszar (5 wierszy x 15 kolumn) żeby nie pominąć
        // nagłówków jak "August 160" w scalonej komórce lub dalszej kolumnie
        for (int r = 0; r <= 4; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < Math.min(row.getLastCellNum() + 1, 15); c++) {
                String text = getCellValueAsString(row.getCell(c)).toLowerCase();
                if (text.isEmpty()) continue;
                for (int m = 0; m < 12; m++) {
                    if (text.contains(monthsEn[m]) || text.contains(monthsPl[m])) {
                        Log.d(TAG, "Wykryto miesiąc " + (m + 1) + " w tekście: '" + text + "' (wiersz " + r + ", kol " + c + ")");
                        return m + 1;
                    }
                }
            }
        }
        return -1;
    }

    private List<EmployeeHeader> parseEmployeeHeaders(Sheet sheet) {
        List<EmployeeHeader> result = new ArrayList<>();
        Row posRow = sheet.getRow(0);
        Row nameRow = sheet.getRow(1);

        if (nameRow == null) return result;

        // Zbierz zakresy scalonych komórek żeby poprawnie odczytywać wartości
        // (Apache POI zwraca wartość tylko dla pierwszej komórki w scalonym zakresie)
        java.util.List<org.apache.poi.ss.util.CellRangeAddress> mergedRegions = sheet.getMergedRegions();

        String currentPosition = "";
        int lastCol = nameRow.getLastCellNum();
        // Uwzględnij też kolumny z wiersza pozycji (row 0) — może być dłuższy
        if (posRow != null && posRow.getLastCellNum() > lastCol) {
            lastCol = posRow.getLastCellNum();
        }

        for (int c = 0; c < lastCol; c++) {
            if (posRow != null) {
                String posStr = getMergedCellValue(posRow, c, mergedRegions);
                if (!posStr.trim().isEmpty() && !posStr.equalsIgnoreCase("godz")) {
                    currentPosition = posStr.trim();
                }
            }

            String nameStr = getMergedCellValue(nameRow, c, mergedRegions).trim();

            // Filtrujemy:
            // 1. Puste komórki
            // 2. Nagłówki ról (stanowisk)
            // 3. Komórki z samymi cyframi (kolumny "godz" jak "8", "12", "32")
            // 4. "godz"
            if (nameStr.isEmpty()) continue;
            if (nameStr.equalsIgnoreCase("godz")) continue;
            if (nameStr.equalsIgnoreCase("Manager")) continue;
            if (nameStr.equalsIgnoreCase("Deputy Manager")) continue;
            if (nameStr.equalsIgnoreCase("Team Leader")) continue;
            if (nameStr.matches("^\\d+([.,]\\d+)?$")) continue; // czysto liczbowe → kolumna godz

            result.add(new EmployeeHeader(c, nameStr, currentPosition));
            Log.d(TAG, "Znaleziono pracownika: " + nameStr + " (" + currentPosition + ") kol=" + c);
        }

        if (result.isEmpty()) {
            Log.w(TAG, "parseEmployeeHeaders: 0 pracowników! Próbuję odczyt bez filtrowania scalonych komórek...");
            // Fallback: bezpośredni odczyt bez merged-cell resolve
            for (int c = 0; c < nameRow.getLastCellNum(); c++) {
                String nameStr = getCellValueAsString(nameRow.getCell(c)).trim();
                if (nameStr.isEmpty() || nameStr.equalsIgnoreCase("godz")
                        || nameStr.equalsIgnoreCase("Manager")
                        || nameStr.equalsIgnoreCase("Deputy Manager")
                        || nameStr.equalsIgnoreCase("Team Leader")
                        || nameStr.matches("^\\d+([.,]\\d+)?$")) continue;
                result.add(new EmployeeHeader(c, nameStr, currentPosition));
            }
        }
        return result;
    }

    /**
     * Odczytuje wartość komórki uwzględniając scalone zakresy (merged cells).
     * Jeśli komórka jest częścią scalonego zakresu, zwraca wartość z komórki-źródłowej (lewy-górny róg).
     */
    private String getMergedCellValue(Row row, int colIndex, java.util.List<org.apache.poi.ss.util.CellRangeAddress> mergedRegions) {
        String direct = getCellValueAsString(row.getCell(colIndex));
        if (!direct.isEmpty()) return direct;

        // Sprawdź czy ta komórka jest częścią scalonego zakresu
        int rowIndex = row.getRowNum();
        for (org.apache.poi.ss.util.CellRangeAddress region : mergedRegions) {
            if (region.isInRange(rowIndex, colIndex)) {
                // Pobierz wartość z pierwszej komórki scalenia
                Row firstRow = row.getSheet().getRow(region.getFirstRow());
                if (firstRow != null) {
                    return getCellValueAsString(firstRow.getCell(region.getFirstColumn()));
                }
            }
        }
        return "";
    }

    private EmployeeHeader findTargetUserColumn(List<EmployeeHeader> employees, String targetUserName, String targetRole) {
        if (targetUserName == null || targetUserName.isEmpty()) targetUserName = "Kacper";
        String lowerTarget = targetUserName.toLowerCase();

        Log.d(TAG, "Szukam kolumny dla: '" + targetUserName + "' rola='" + targetRole + "' wśród " + employees.size() + " pracowników");
        for (EmployeeHeader e : employees) {
            Log.d(TAG, "  Kandydat: '" + e.name + "' pos='" + e.position + "' kol=" + e.colIndex);
        }

        // 1. Priorytet: dokładna rola z ustawień (jeśli ustawiona przez użytkownika)
        if (targetRole != null && !targetRole.isEmpty() && !targetRole.equalsIgnoreCase("Dowolna (Automatycznie)")) {
            for (EmployeeHeader emp : employees) {
                if (emp.position.equalsIgnoreCase(targetRole) && emp.name.toLowerCase().contains(lowerTarget)) {
                    Log.d(TAG, "Dopasowano po roli z ustawień: " + emp.name + " (" + emp.position + ")");
                    return emp;
                }
            }
        }

        // 2. Szukamy: Team Leader (najczęstsza historycznie rola Kacpra)
        for (EmployeeHeader emp : employees) {
            if (emp.position.equalsIgnoreCase("Team Leader") && emp.name.toLowerCase().contains(lowerTarget)) {
                Log.d(TAG, "Dopasowano po Team Leader: " + emp.name);
                return emp;
            }
        }

        // 3. Jeśli grafik się zmienił i Kacper jest teraz Deputy Manager / Manager — szukamy po samym imieniu
        // To jest kluczowy fallback gdy rola zmienia się między miesiącami!
        for (EmployeeHeader emp : employees) {
            if (emp.name.toLowerCase().contains(lowerTarget)) {
                Log.d(TAG, "Dopasowano po imieniu (rola: " + emp.position + "): " + emp.name);
                return emp;
            }
        }

        return null;
    }

    private int parseDayNumber(Cell cell) {
        if (cell == null) return -1;
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        } else if (cell.getCellType() == CellType.STRING) {
            try {
                String val = cell.getStringCellValue().trim();
                return Integer.parseInt(val);
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    public ParsedShiftInfo parseShiftText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return new ParsedShiftInfo(false);
        }

        String text = rawText.trim();
        String lower = text.toLowerCase();

        // Autokorekta błędów OCR - kolejność ważna (dłuższe przed krótszymi)
        lower = lower.replace("ciose", "close")
                     .replace("c1ose", "close")
                     .replace("clos=", "close")
                     .replace("cios=", "close")
                     .replace("cios", "close")     // OCR: 'close' → 'cios'
                     .replace("0pen", "open")
                     .replace("oden", "open")
                     .replace("oden", "open")
                     .replace("s2k", "szk")
                     .replace("--", "-");

        // Autokorekta "off" - obsługuje warianty z prefiksem/sufiksem cyfry (np. "8 of", "8 off")
        // Zamieniamy tylko gdy jako token (po spacjach)
        lower = lower.replaceAll("\\boft\\b", "off")
                     .replaceAll("\\bofft\\b", "off")
                     .replaceAll("\\b0ff\\b", "off")
                     .replaceAll("\\bof\\b", "off");   // "8 of" → "8 off"

        // Używamy ujednoliconego ciągu obniżonych liter do wyszukiwań regex
        String originalText = rawText.trim();

        // Skrót TMS + open → traktuj jako open
        if (lower.contains("open") && lower.contains("tms")) {
            // Zachowaj opis TMS ale traktuj jako open
        }

        // ── KROK 1: Sprawdź czy to wolne (off/urlop) ───────────────────────────
        // Używamy split żeby sprawdzić tokeny — "8 off" zawiera token "off"
        for (String token : lower.split("\\s+")) {
            if (token.equals("off") || token.equals("vacation") || token.equals("urlop") || token.equals("wz")) {
                return new ParsedShiftInfo(false);
            }
        }

        // ── KROK 2: Specyficzne wzorce (muszą być przed generycznymi) ──────────

        // 2a. close od HH (np. "close od 14", "close od 14:30")
        Matcher closeOdMatcher = CLOSE_OD_PATTERN.matcher(lower);
        if (closeOdMatcher.find()) {
            int startHour = Integer.parseInt(closeOdMatcher.group(1));
            int startMin = closeOdMatcher.group(2) != null ? Integer.parseInt(closeOdMatcher.group(2)) : 0;
            String startTime = String.format(Locale.US, "%02d:%02d", startHour, startMin);
            return new ParsedShiftInfo(startTime, "01:00", true, originalText, "ZAMEK");
        }

        // 2b. open do HH (np. "open do 16", "open do 15:30")
        Matcher openDoMatcher = OPEN_DO_PATTERN.matcher(lower);
        if (openDoMatcher.find()) {
            int endHour = Integer.parseInt(openDoMatcher.group(1));
            int endMin = openDoMatcher.group(2) != null ? Integer.parseInt(openDoMatcher.group(2)) : 0;
            String endTime = String.format(Locale.US, "%02d:%02d", endHour, endMin);
            return new ParsedShiftInfo("09:00", endTime, false, originalText, "OBSŁUGA");
        }

        // 2c. Zakres HH-HH (np. "14-22", "12.30-20.30", "10/18", "8 12-20 8", "TMS paczki 17–22")
        // UWAGA: matchujemy na 'lower' (po autokorekcie "--" → "-") żeby obsłużyć zapis OCR "12--20"
        Matcher rangeMatcher = RANGE_PATTERN.matcher(lower);
        if (rangeMatcher.find()) {
            int startHour = Integer.parseInt(rangeMatcher.group(1));
            int startMin = rangeMatcher.group(2) != null ? Integer.parseInt(rangeMatcher.group(2)) : 0;
            int endHour = Integer.parseInt(rangeMatcher.group(3));
            int endMin = rangeMatcher.group(4) != null ? Integer.parseInt(rangeMatcher.group(4)) : 0;

            // Odrzuć fałszywe rangi: np. "8" odczytane jako "8-8" lub "1-8" bez sensu (oba poniżej 9)
            // Prawidłowe godziny pracy: start >= 6, end >= 12 LUB end <= 5 (nocna)
            boolean validRange = (startHour >= 6 && (endHour >= 12 || endHour <= 5));
            if (validRange) {
                String startTime = String.format(Locale.US, "%02d:%02d", startHour, startMin);
                String endTime = String.format(Locale.US, "%02d:%02d", endHour, endMin);
                boolean isClosing = endHour <= 5 || endHour >= 23;
                String desc = (originalText.substring(0, rangeMatcher.start()) + originalText.substring(rangeMatcher.end())).trim();
                String category = isClosing ? "ZAMEK" : "OBSŁUGA";
                if (lower.contains("tms")) category = "INNE (TMS)";
                return new ParsedShiftInfo(startTime, endTime, isClosing, desc, category);
            }
        }

        // ── KROK 3: Generyczne open/close (contains — obsługuje szum cyfr OCR) ──
        // Szkolenie/TMS open: "szk open", "open szk TMS", "8 open 8", "open"
        if (lower.contains("open")) {
            String category = lower.contains("tms") ? "INNE (TMS)" : "OBSŁUGA";
            String desc = lower.contains("szk") ? text : "";
            return new ParsedShiftInfo("09:00", "17:00", false, desc, category);
        }

        // Szkolenie/generyczne close: "szk close", "close 8", "8 close", "8 close 8"
        if (lower.contains("close")) {
            String desc = lower.contains("szk") ? text : "";
            return new ParsedShiftInfo("17:00", "01:00", true, desc, "ZAMEK");
        }

        return new ParsedShiftInfo(false);
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            double val = cell.getNumericCellValue();
            if (val == (long) val) {
                return String.valueOf((long) val);
            }
            return String.valueOf(val);
        } else if (cell.getCellType() == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        } else if (cell.getCellType() == CellType.FORMULA) {
            try {
                return cell.getStringCellValue();
            } catch (Exception e) {
                return String.valueOf(cell.getNumericCellValue());
            }
        }
        return "";
    }
}
