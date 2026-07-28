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

                    // Jeśli to nie sam użytkownik, dodajemy imię do ekipy z tego dnia
                    if (!emp.name.equalsIgnoreCase(targetUserCol.name)) {
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

                List<String> coworkers = dailyCrewMap.get(dateStr);
                if (coworkers != null && !coworkers.isEmpty()) {
                    userShift.setClosingCrew(String.join(", ", coworkers));
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

        Log.d(TAG, "Parsowanie zakończone sukcesem. Parsowano " + targetUserShifts.size() + " zmian użytkownika.");
        return result;
    }

    private int parseMonthFromSheet(Sheet sheet) {
        String[] monthsEn = {"january", "february", "march", "april", "may", "june",
                "july", "august", "september", "october", "november", "december"};
        String[] monthsPl = {"styczeń", "luty", "marzec", "kwiecień", "maj", "czerwiec",
                "lipiec", "sierpień", "wrzesień", "październik", "listopad", "grudzień"};

        for (int r = 0; r <= 3; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c <= 5; c++) {
                String text = getCellValueAsString(row.getCell(c)).toLowerCase();
                for (int m = 0; m < 12; m++) {
                    if (text.contains(monthsEn[m]) || text.contains(monthsPl[m])) {
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

        String currentPosition = "";
        for (int c = 0; c < nameRow.getLastCellNum(); c++) {
            if (posRow != null) {
                String posStr = getCellValueAsString(posRow.getCell(c));
                if (!posStr.trim().isEmpty() && !posStr.equalsIgnoreCase("godz")) {
                    currentPosition = posStr.trim();
                }
            }

            String nameStr = getCellValueAsString(nameRow.getCell(c)).trim();
            if (!nameStr.isEmpty() && !nameStr.equalsIgnoreCase("godz") && !nameStr.equalsIgnoreCase("Manager")
                    && !nameStr.equalsIgnoreCase("Deputy Manager") && !nameStr.equalsIgnoreCase("Team Leader")) {
                result.add(new EmployeeHeader(c, nameStr, currentPosition));
            }
        }
        return result;
    }

    private EmployeeHeader findTargetUserColumn(List<EmployeeHeader> employees, String targetUserName, String targetRole) {
        if (targetUserName == null || targetUserName.isEmpty()) targetUserName = "Kacper";
        String lowerTarget = targetUserName.toLowerCase();

        if (targetRole != null && !targetRole.isEmpty()) {
            for (EmployeeHeader emp : employees) {
                if (emp.position.equalsIgnoreCase(targetRole) && emp.name.toLowerCase().contains(lowerTarget)) {
                    return emp;
                }
            }
        }

        // Szukamy pod stanowiskiem Team Leader jako domyślnie
        for (EmployeeHeader emp : employees) {
            if (emp.position.equalsIgnoreCase("Team Leader") && emp.name.toLowerCase().contains(lowerTarget)) {
                return emp;
            }
        }

        // Dopasowanie ogólne po imieniu
        for (EmployeeHeader emp : employees) {
            if (emp.name.toLowerCase().contains(lowerTarget)) {
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

        // Autokorekta błędów OCR
        lower = lower.replace("ciose", "close")
                     .replace("c1ose", "close")
                     .replace("clos=", "close")
                     .replace("cios=", "close")
                     .replace("0pen", "open")
                     .replace("oden", "open")
                     .replace("s2k", "szk")
                     .replace("--", "-")
                     .replaceAll("^oft$", "off")
                     .replaceAll("^of$", "off")
                     .replaceAll("^offt$", "off")
                     .replaceAll("^0ff$", "off");

        // Jeśli to jest coś typu "open szk TMS", skróćmy to tylko do "open"
        if (lower.contains("open") && lower.contains("tms")) {
            lower = "open";
        }

        if (lower.equals("off") || lower.equals("vacation") || lower.equals("urlop") || lower.equals("wz")) {
            return new ParsedShiftInfo(false);
        }

        // 1. Szkolenie Open / Open
        if (lower.equals("open") || lower.equals("szk open")) {
            return new ParsedShiftInfo("09:00", "17:00", false, text.startsWith("szk") ? text : "", "OBSŁUGA");
        }

        // 2. Szkolenie Close / Close
        if (lower.equals("close") || lower.equals("szk close")) {
            return new ParsedShiftInfo("17:00", "01:00", true, text.startsWith("szk") ? text : "", "ZAMEK");
        }

        // 3. close od HH
        Matcher closeOdMatcher = CLOSE_OD_PATTERN.matcher(lower);
        if (closeOdMatcher.find()) {
            int startHour = Integer.parseInt(closeOdMatcher.group(1));
            int startMin = closeOdMatcher.group(2) != null ? Integer.parseInt(closeOdMatcher.group(2)) : 0;
            String startTime = String.format(Locale.US, "%02d:%02d", startHour, startMin);
            return new ParsedShiftInfo(startTime, "01:00", true, text, "ZAMEK");
        }

        // 4. open do HH
        Matcher openDoMatcher = OPEN_DO_PATTERN.matcher(lower);
        if (openDoMatcher.find()) {
            int endHour = Integer.parseInt(openDoMatcher.group(1));
            int endMin = openDoMatcher.group(2) != null ? Integer.parseInt(openDoMatcher.group(2)) : 0;
            String endTime = String.format(Locale.US, "%02d:%02d", endHour, endMin);
            return new ParsedShiftInfo("09:00", endTime, false, text, "OBSŁUGA");
        }

        // 5. Zakres z różnymi separatorami e.g. "14-22", "12.30-20.30", "10/18", "TMS paczki 17–22"
        Matcher rangeMatcher = RANGE_PATTERN.matcher(text);
        if (rangeMatcher.find()) {
            int startHour = Integer.parseInt(rangeMatcher.group(1));
            int startMin = rangeMatcher.group(2) != null ? Integer.parseInt(rangeMatcher.group(2)) : 0;
            int endHour = Integer.parseInt(rangeMatcher.group(3));
            int endMin = rangeMatcher.group(4) != null ? Integer.parseInt(rangeMatcher.group(4)) : 0;
            
            String startTime = String.format(Locale.US, "%02d:%02d", startHour, startMin);
            String endTime = String.format(Locale.US, "%02d:%02d", endHour, endMin);
            boolean isClosing = endHour <= 5 || endHour >= 23;
            String desc = text.replace(rangeMatcher.group(0), "").trim();
            return new ParsedShiftInfo(startTime, endTime, isClosing, desc, isClosing ? "ZAMEK" : "OBSŁUGA");
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
