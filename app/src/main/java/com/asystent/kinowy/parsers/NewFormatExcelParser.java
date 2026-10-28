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
import java.time.DayOfWeek;
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

        // ── Cross-check: weryfikacja miesiąca z dniami tygodnia w pliku ────────
        // Menedżerka może zapomnieć zmienić nazwę miesiąca — sprawdzamy czy
        // układ dni tygodnia (Tuesday, Wednesday...) pasuje do wykrytego miesiąca.
        // Jeśli nie — szukamy właściwego i dodajemy ostrzeżenie.
        int correctedMonth = crossCheckMonthWithDaysOfWeek(sheet, monthValue, currentYear);
        if (correctedMonth != monthValue) {
            String detectedName  = java.time.Month.of(monthValue).getDisplayName(
                    java.time.format.TextStyle.FULL, new Locale("pl", "PL"));
            String correctedName = java.time.Month.of(correctedMonth).getDisplayName(
                    java.time.format.TextStyle.FULL, new Locale("pl", "PL"));
            warnings.add(new ParserWarning(ParserWarning.Type.OTHER,
                    "⚠️ UWAGA: Nagłówek grafiku wskazuje na " + detectedName.toUpperCase()
                    + ", ale układ dni tygodnia odpowiada " + correctedName.toUpperCase()
                    + ". Menedżer prawdopodobnie zapomniał zmienić nazwę miesiąca. "
                    + "Automatycznie przyjęto: " + correctedName + "."));
            monthValue = correctedMonth;
            Log.w(TAG, "⚠️ Auto-korekta miesiąca: " + detectedName + " → " + correctedName);
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
                    "Brak Twojej kolumny (" + targetUserName + ") w tym pliku — wczytano obsadę ekipy bez nadpisywania Twoich zmian."));
            Log.d(TAG, "Tryb ekipy (observer): brak kolumny dla '" + targetUserName + "'");
        } else {
            Log.d(TAG, "Wybrana kolumna użytkownika: " + targetUserCol.name + " (" + targetUserCol.position + ") na kolumnie " + targetUserCol.colIndex);
        }

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
        Map<String, List<GlobalShift>> dailyOtherGlobalShiftsMap = new HashMap<>(); // date -> list of other employees' GlobalShift
        int maxDayInMonth = java.time.YearMonth.of(currentYear, monthValue).lengthOfMonth();

        for (int r = 2; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            int dayNum = parseDayNumber(row.getCell(0));
            if (dayNum < 1 || dayNum > maxDayInMonth) {
                // Może być w kolumnie 1 jeśli kolumna 0 to np. pusta
                dayNum = parseDayNumber(row.getCell(1));
            }
            if (dayNum < 1 || dayNum > maxDayInMonth) {
                continue; // Pomijamy wiersze sumaryczne, puste i nieprawidłowe dni miesiąca
            }

            String dateStr = String.format(Locale.US, "%04d-%02d-%02d", currentYear, monthValue, dayNum);
            if (!allDates.contains(dateStr)) {
                allDates.add(dateStr);
            }

            List<GlobalShift> otherShiftsForDay = new ArrayList<>();

            // Najpierw czytamy i parsujemy wszystkich pracowników dla tej daty
            for (EmployeeHeader emp : employees) {
                Cell cell = row.getCell(emp.colIndex);
                String rawText = getCellValueAsString(cell);
                ParsedShiftInfo info = parseShiftText(rawText);

                if (info.isShift) {
                    String effCategory = determineEffectiveCategory(emp.position, info.category, info.isClosingShift);
                    GlobalShift gs = new GlobalShift(
                            emp.name,
                            dateStr,
                            info.startTime,
                            info.endTime,
                            effCategory
                    );
                    gs.setManuallyEdited(false);
                    allGlobalShifts.add(gs);

                    // Wszyscy koledzy pracujący w danym dniu (poza kolumną samego użytkownika)
                    if (targetUserCol == null || emp.colIndex != targetUserCol.colIndex) {
                        otherShiftsForDay.add(gs);
                        Shift empShift = new Shift(
                                dateStr,
                                info.startTime,
                                info.endTime,
                                info.description != null && !info.description.isEmpty() ? info.description : effCategory,
                                true,
                                false,
                                effCategory
                        );
                        empShift.setClosingShift(info.isClosingShift);
                        empShift.setClosingCrew("");
                        List<Shift> empList = scheduleByName.get(emp.name);
                        if (empList != null) {
                            empList.add(empShift);
                        }
                    }
                }
            }

            dailyOtherGlobalShiftsMap.put(dateStr, otherShiftsForDay);
        }

        // 4. Budujemy obiekty Shift dla docelowego użytkownika (jeśli występuje w tym grafiku)
        int userWorkShiftCount = 0;
        if (targetUserCol != null) {
            for (int r = 2; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                int dayNum = parseDayNumber(row.getCell(0));
                if (dayNum < 1 || dayNum > maxDayInMonth) {
                    dayNum = parseDayNumber(row.getCell(1));
                }
                if (dayNum < 1 || dayNum > maxDayInMonth) continue;

                String dateStr = String.format(Locale.US, "%04d-%02d-%02d", currentYear, monthValue, dayNum);
                Cell userCell = row.getCell(targetUserCol.colIndex);
                String rawText = getCellValueAsString(userCell);
                ParsedShiftInfo info = parseShiftText(rawText);

                if (info.isShift) {
                    userWorkShiftCount++;
                    String userCat = com.asystent.kinowy.utils.ShiftUtils.resolveShiftType(
                            info.startTime, info.endTime, info.category);
                    String userDesc = (info.description != null && !info.description.isEmpty()) ? info.description : userCat;
                    Shift userShift = new Shift(
                            dateStr,
                            info.startTime,
                            info.endTime,
                            userDesc,
                            true,
                            false,
                            userCat
                    );
                    userShift.setClosingShift(info.isClosingShift);

                    List<GlobalShift> otherShifts = dailyOtherGlobalShiftsMap.get(dateStr);
                    String overlappingCrew = com.asystent.kinowy.utils.ShiftUtils.buildOverlappingCrewString(
                            info.startTime, info.endTime, userCat, targetUserCol.name, otherShifts);
                    userShift.setClosingCrew(overlappingCrew);

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
                            "WOLNE"
                    );
                    userShift.setClosingCrew("");
                    targetUserShifts.add(userShift);
                    scheduleByName.get(targetUserCol.name).add(userShift);
                }
            }
            if (userWorkShiftCount == 0) {
                targetUserShifts.clear();
            }
        }

        workbook.close();

        String latestDate = allDates.isEmpty() ? null : allDates.get(allDates.size() - 1);
        List<com.asystent.kinowy.models.ActiveEmployee> detectedEmployees = new ArrayList<>();
        for (EmployeeHeader emp : employees) {
            boolean isLeader = isLeadershipPosition(emp.position);
            String empRole = isLeader ? com.asystent.kinowy.models.ActiveEmployee.ROLE_TL : com.asystent.kinowy.models.ActiveEmployee.ROLE_EMPLOYEE;
            detectedEmployees.add(new com.asystent.kinowy.models.ActiveEmployee(emp.name, true, empRole, latestDate));
        }

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
        result.setDetectedEmployees(detectedEmployees);

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

    public static boolean isLeadershipPosition(String position) {
        if (position == null) return false;
        String p = position.trim().toLowerCase(Locale.ROOT);
        return p.contains("leader") || p.contains("manager") || p.contains("kierownik") || p.equals("tl");
    }

    private String determineEffectiveCategory(String position, String parsedCategory, boolean isClosing) {
        if ("INNE (TMS)".equals(parsedCategory)) {
            return parsedCategory;
        }
        if (isLeadershipPosition(position)) {
            if (parsedCategory != null && ("OPEN".equals(parsedCategory) || "ZAMEK".equals(parsedCategory)
                    || "MIĘDZYZMIANA".equals(parsedCategory))) {
                return parsedCategory;
            }
            return isClosing ? "ZAMEK" : "OPEN";
        }
        if (position != null && !position.trim().isEmpty()) {
            String pLower = position.trim().toLowerCase(Locale.ROOT);
            if (pLower.contains("bar")) return isClosing ? "BAR (ZAMEK)" : "BAR";
            if (pLower.contains("kasa")) return "KASA";
            if (pLower.contains("tms") || pLower.contains("projekcj")) return "INNE (TMS)";
            if (pLower.contains("obs") || pLower.contains("ow") || pLower.contains("widz") || pLower.contains("bilet")) {
                return "OBSŁUGA";
            }
        }
        return parsedCategory != null ? parsedCategory : "OBSŁUGA";
    }

    /**
     * Cross-check miesiąca: porównuje nazwy dni tygodnia z pliku (kolumna 1 lub 2)
     * z rzeczywistym układem dni w wykrytym miesiącu.
     *
     * Scenario: Menedżer zapomniał zmienić nazwę miesiąca w nagłówku.
     * Nagłówek mówi "August", ale w pliku dzień 1 to "Tuesday" a 1 września 2026
     * to właśnie wtorek → auto-korekta do września.
     *
     * @param sheet        arkusz do analizy
     * @param detectedMonth wykryty miesiąc z nagłówka (1-12)
     * @param year         rok
     * @return poprawiony miesiąc (1-12); równy detectedMonth jeśli brak konfliktu
     */
    private int crossCheckMonthWithDaysOfWeek(Sheet sheet, int detectedMonth, int year) {
        // Mapy nazw dni tygodnia (EN i PL) → DayOfWeek
        java.util.Map<String, DayOfWeek> dayMap = new java.util.LinkedHashMap<>();
        dayMap.put("monday",     DayOfWeek.MONDAY);
        dayMap.put("tuesday",    DayOfWeek.TUESDAY);
        dayMap.put("wednesday",  DayOfWeek.WEDNESDAY);
        dayMap.put("thursday",   DayOfWeek.THURSDAY);
        dayMap.put("friday",     DayOfWeek.FRIDAY);
        dayMap.put("saturday",   DayOfWeek.SATURDAY);
        dayMap.put("sunday",     DayOfWeek.SUNDAY);
        dayMap.put("poniedziałek", DayOfWeek.MONDAY);
        dayMap.put("wtorek",     DayOfWeek.TUESDAY);
        dayMap.put("środa",      DayOfWeek.WEDNESDAY);
        dayMap.put("czwartek",   DayOfWeek.THURSDAY);
        dayMap.put("piątek",     DayOfWeek.FRIDAY);
        dayMap.put("sobota",     DayOfWeek.SATURDAY);
        dayMap.put("niedziela",  DayOfWeek.SUNDAY);

        // Zbierz pary (dayNum, DayOfWeek) z pliku
        java.util.List<int[]> filePairs = new java.util.ArrayList<>(); // [dayNum, dayOfWeekValue]
        for (int r = 2; r <= Math.min(sheet.getLastRowNum(), 35); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            int dayNum = parseDayNumber(row.getCell(0));
            if (dayNum < 1 || dayNum > 31) dayNum = parseDayNumber(row.getCell(1));
            if (dayNum < 1 || dayNum > 31) continue;

            // Kolumna z nazwą dnia tygodnia — zazwyczaj kolumna 1 (idx=1)
            // Sprawdzamy kolumny 1 i 2
            String dayText = "";
            for (int col : new int[]{1, 2}) {
                Cell c = row.getCell(col);
                if (c != null) {
                    String t = getCellValueAsString(c).toLowerCase(Locale.ROOT).trim();
                    if (dayMap.containsKey(t)) { dayText = t; break; }
                }
            }
            if (dayText.isEmpty()) continue;
            filePairs.add(new int[]{dayNum, dayMap.get(dayText).getValue()});
            if (filePairs.size() >= 5) break; // 5 par wystarczy do pewnej detekcji
        }

        if (filePairs.isEmpty()) {
            Log.d(TAG, "crossCheck: brak danych o dniach tygodnia — pomijam cross-check");
            return detectedMonth; // brak danych → nie zmieniamy
        }

        // Sprawdź czy wykryty miesiąc pasuje
        if (monthMatchesPairs(detectedMonth, year, filePairs)) {
            Log.d(TAG, "crossCheck: miesiąc " + detectedMonth + " PASUJE do układu dni ✅");
            return detectedMonth;
        }

        // Nie pasuje — szukaj właściwego w zakresie ±3 miesięcy
        Log.w(TAG, "crossCheck: miesiąc " + detectedMonth + " NIE pasuje do układu dni — szukam korekty...");
        for (int delta = 1; delta <= 3; delta++) {
            for (int sign : new int[]{1, -1}) {
                int candidate = ((detectedMonth - 1 + sign * delta + 12) % 12) + 1;
                int candYear = year;
                if (candidate == 12 && sign == -1 && detectedMonth <= 3) candYear--;
                if (candidate == 1  && sign ==  1 && detectedMonth >= 11) candYear++;
                if (monthMatchesPairs(candidate, candYear, filePairs)) {
                    Log.w(TAG, "crossCheck: auto-korekta " + detectedMonth + " → " + candidate);
                    return candidate;
                }
            }
        }

        Log.w(TAG, "crossCheck: nie znaleziono pasującego miesiąca — zostawiam oryginalny");
        return detectedMonth;
    }

    /**
     * Sprawdza czy co najmniej 3 pary (dayNum, dayOfWeekValue) z pliku
     * pasują do faktycznych dni tygodnia w danym miesiącu/roku.
     */
    private boolean monthMatchesPairs(int month, int year, java.util.List<int[]> pairs) {
        int matches = 0;
        for (int[] pair : pairs) {
            int dayNum = pair[0];
            int expectedDow = pair[1]; // DayOfWeek.getValue() — Monday=1, Sunday=7
            try {
                LocalDate date = LocalDate.of(year, month, dayNum);
                if (date.getDayOfWeek().getValue() == expectedDow) matches++;
            } catch (Exception ignored) {
                // Nieważny dzień (np. 31 w miesiącu 30-dniowym)
            }
        }
        int needed = Math.max(2, pairs.size() - 1); // tolerancja: maks 1 błąd
        return matches >= needed;
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

        java.util.Map<String, Integer> nameCounts = new java.util.HashMap<>();
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
            if (nameStr.equalsIgnoreCase("dzień") || nameStr.equalsIgnoreCase("dzien")
                    || nameStr.equalsIgnoreCase("day") || nameStr.equalsIgnoreCase("data")) continue;
            if (nameStr.equalsIgnoreCase("Manager")) continue;
            if (nameStr.equalsIgnoreCase("Deputy Manager")) continue;
            if (nameStr.equalsIgnoreCase("Team Leader")) continue;
            if (nameStr.matches("^\\d+([.,]\\d+)?$")) continue; // czysto liczbowe → kolumna godz

            String roleSuffix = "";
            String posLow = currentPosition.toLowerCase(Locale.ROOT);
            if (!nameStr.contains("(")) {
                if (posLow.contains("team") || posLow.contains("leader") || posLow.equals("tl")) {
                    roleSuffix = " (TL)";
                } else if (posLow.contains("deputy") || posLow.contains("z-ca") || posLow.contains("zastępca") || posLow.equals("dm")) {
                    roleSuffix = " (Deputy)";
                } else if (posLow.contains("manager") || posLow.contains("menadżer") || posLow.contains("kierownik")) {
                    roleSuffix = " (Manager)";
                }
            }
            String fullEmpName = nameStr + roleSuffix;
            int count = nameCounts.getOrDefault(fullEmpName, 0) + 1;
            nameCounts.put(fullEmpName, count);
            if (count > 1) {
                fullEmpName = fullEmpName + " (" + count + ")";
            }

            result.add(new EmployeeHeader(c, fullEmpName, currentPosition));
            Log.d(TAG, "Znaleziono pracownika: " + fullEmpName + " (" + currentPosition + ") kol=" + c);
        }

        if (result.isEmpty()) {
            Log.w(TAG, "parseEmployeeHeaders: 0 pracowników! Próbuję odczyt bez filtrowania scalonych komórek...");
            // Fallback: bezpośredni odczyt bez merged-cell resolve
            for (int c = 0; c < nameRow.getLastCellNum(); c++) {
                String nameStr = getCellValueAsString(nameRow.getCell(c)).trim();
                if (nameStr.isEmpty() || nameStr.equalsIgnoreCase("godz")
                        || nameStr.equalsIgnoreCase("dzień") || nameStr.equalsIgnoreCase("dzien")
                        || nameStr.equalsIgnoreCase("day") || nameStr.equalsIgnoreCase("data")
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

    private boolean nameMatches(String empName, String targetUserName) {
        if (empName == null || targetUserName == null) return false;
        String e = empName.trim().toLowerCase(Locale.ROOT);
        String t = targetUserName.trim().toLowerCase(Locale.ROOT);
        if (e.isEmpty() || t.isEmpty()) return false;
        if (e.equals(t)) return true;
        if (e.contains(t) || t.contains(e)) return true;
        String firstWordTarget = t.split("\\s+")[0];
        String firstWordEmp = e.split("\\s+")[0];
        return firstWordTarget.equals(firstWordEmp);
    }

    private EmployeeHeader findTargetUserColumn(List<EmployeeHeader> employees, String targetUserName, String targetRole) {
        if (targetUserName == null || targetUserName.isEmpty()) targetUserName = "Kacper";

        Log.d(TAG, "Szukam kolumny dla: '" + targetUserName + "' rola='" + targetRole + "' wśród " + employees.size() + " pracowników");
        for (EmployeeHeader e : employees) {
            Log.d(TAG, "  Kandydat: '" + e.name + "' pos='" + e.position + "' kol=" + e.colIndex);
        }

        // 1. Priorytet: dokładna rola z ustawień (jeśli ustawiona przez użytkownika)
        if (targetRole != null && !targetRole.isEmpty() && !targetRole.equalsIgnoreCase("Dowolna (Automatycznie)")) {
            for (EmployeeHeader emp : employees) {
                if (emp.position.equalsIgnoreCase(targetRole) && nameMatches(emp.name, targetUserName)) {
                    Log.d(TAG, "Dopasowano po roli z ustawień: " + emp.name + " (" + emp.position + ")");
                    return emp;
                }
            }
        }

        // 2. Szukamy: Team Leader (najczęstsza historycznie rola Kacpra)
        for (EmployeeHeader emp : employees) {
            if (emp.position.equalsIgnoreCase("Team Leader") && nameMatches(emp.name, targetUserName)) {
                Log.d(TAG, "Dopasowano po Team Leader: " + emp.name);
                return emp;
            }
        }

        // 3. Jeśli grafik się zmienił i Kacper jest teraz Deputy Manager / Manager — szukamy po samym imieniu
        // Ale jeśli w ustawieniach wybrano rolę kierowniczą (Team Leader / Manager / Deputy), nie dopasowujemy zwykłego pracownika BAR/OW o tym samym imieniu!
        boolean targetIsLeadership = targetRole != null && isLeadershipPosition(targetRole);
        for (EmployeeHeader emp : employees) {
            if (targetIsLeadership && !isLeadershipPosition(emp.position) && !emp.position.isEmpty()) {
                continue;
            }
            if (nameMatches(emp.name, targetUserName)) {
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
                     .replaceAll("(\\d)[dD]\\b", "$10")
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
            return new ParsedShiftInfo(startTime, "01:00", true, "ZAMEK", "ZAMEK");
        }

        // 2b. open do HH (np. "open do 16", "open do 15:30")
        Matcher openDoMatcher = OPEN_DO_PATTERN.matcher(lower);
        if (openDoMatcher.find()) {
            int endHour = Integer.parseInt(openDoMatcher.group(1));
            int endMin = openDoMatcher.group(2) != null ? Integer.parseInt(openDoMatcher.group(2)) : 0;
            String endTime = String.format(Locale.US, "%02d:%02d", endHour, endMin);
            return new ParsedShiftInfo("09:00", endTime, false, "OPEN", "OPEN");
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
                String rawRem = (originalText.substring(0, rangeMatcher.start()) + originalText.substring(rangeMatcher.end())).trim();
                String cleanDesc = rawRem.replaceAll("\\b\\d{1,2}\\b", "").trim();
                String category;
                if (lower.contains("tms")) {
                    category = "INNE (TMS)";
                } else if (isClosing) {
                    category = "ZAMEK";
                } else if (startHour <= 9) {
                    category = "OPEN";
                } else {
                    category = "MIĘDZYZMIANA";
                }
                String desc = !cleanDesc.isEmpty() ? cleanDesc : category;
                return new ParsedShiftInfo(startTime, endTime, isClosing, desc, category);
            }
        }

        // ── KROK 3: Generyczne open/close (contains — obsługuje szum cyfr OCR) ──
        // Szkolenie/TMS open: "szk open", "open szk TMS", "8 open 8", "open"
        if (lower.contains("open")) {
            String category = lower.contains("tms") ? "INNE (TMS)" : "OPEN";
            String desc = lower.contains("szk") ? "OPEN (SZK)" : category;
            return new ParsedShiftInfo("09:00", "17:00", false, desc, category);
        }

        // Szkolenie/generyczne close: "szk close", "close 8", "8 close", "8 close 8"
        if (lower.contains("close")) {
            String desc = lower.contains("szk") ? "ZAMEK (SZK)" : "ZAMEK";
            return new ParsedShiftInfo("17:00", "01:00", true, desc, "ZAMEK");
        }

        // ── KROK 4: Maraton — długa zmiana zamykająca (np. "maraton od 20") ──────
        // Format: "maraton od HH" → start=HH:00, end=01:00 (zamek)
        //         samo "maraton"  → start=17:00, end=01:00
        if (lower.contains("maraton")) {
            java.util.regex.Matcher mOd = java.util.regex.Pattern
                    .compile("od\\s*(\\d{1,2})(?::(\\d{2}))?").matcher(lower);
            if (mOd.find()) {
                int h = Integer.parseInt(mOd.group(1));
                int m = mOd.group(2) != null ? Integer.parseInt(mOd.group(2)) : 0;
                
                int duration = 8;
                String rest = lower.substring(mOd.end());
                java.util.regex.Matcher mDur = java.util.regex.Pattern.compile("\\b(\\d{1,2})\\b$").matcher(rest);
                if (mDur.find()) {
                    duration = Integer.parseInt(mDur.group(1));
                }
                
                int endH = (h + duration) % 24;
                String startTime = String.format(Locale.US, "%02d:%02d", h, m);
                String endTime = String.format(Locale.US, "%02d:%02d", endH, m);
                return new ParsedShiftInfo(startTime, endTime, true, "ZAMEK", "ZAMEK");
            }
            return new ParsedShiftInfo("17:00", "01:00", true, "ZAMEK", "ZAMEK");
        }

        // ── KROK 5: Inne niesprecyzowane (np. "TMS paczki", "MS playlisty") ────
        if (lower.contains("tms") || lower.contains("paczki") || lower.contains("playlisty") || lower.contains("ms")) {
            return new ParsedShiftInfo("09:00", "17:00", false, text, "INNE (TMS)");
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
