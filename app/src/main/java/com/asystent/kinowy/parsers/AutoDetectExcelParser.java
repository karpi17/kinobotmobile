package com.asystent.kinowy.parsers;

import android.util.Log;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.InputStream;

public class AutoDetectExcelParser implements ScheduleParser {

    private static final String TAG = "AutoDetectExcelParser";

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        String lower = mimeType.toLowerCase();
        return lower.contains("spreadsheet") || lower.contains("excel")
                || lower.contains("xlsx") || lower.contains("xls");
    }

    @Override
    public ScheduleParseResult parse(InputStream inputStream, ScheduleParseOptions options) throws Exception {
        Log.d(TAG, "AutoDetectExcelParser: rozpoczynam detekcję formatu");

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = inputStream.read(buffer)) > -1) {
            baos.write(buffer, 0, len);
        }
        baos.flush();
        byte[] fileData = baos.toByteArray();

        java.io.ByteArrayInputStream isForDetection = new java.io.ByteArrayInputStream(fileData);
        Workbook workbook = WorkbookFactory.create(isForDetection);
        Sheet sheet = workbook.getSheetAt(0);

        boolean isOldFormat = detectOldFormat(sheet);
        workbook.close();

        java.io.ByteArrayInputStream isForParsing = new java.io.ByteArrayInputStream(fileData);
        if (isOldFormat) {
            Log.d(TAG, "Wykryto STARY format Excel (ExcelParsingService).");
            return new com.asystent.kinowy.network.ExcelParsingService().parse(isForParsing, options);
        } else {
            Log.d(TAG, "Wykryto NOWY format biurowy (NewFormatExcelParser).");
            return new NewFormatExcelParser().parse(isForParsing, options);
        }
    }

    /**
     * Wykrywa stary format grafiku kinowego na podstawie systemu punktów.
     *
     * Stary format (ExcelParsingService):
     *  - Wiersz 3 (idx): daty w kol 2, 5, 8... (co 3 kolumny, 7 dni)
     *  - Wiersz 4: nazwy dni tygodnia
     *  - Wiersz 5+: kol[0]=lp (cyfra), kol[1]=imię i nazwisko
     *
     * Nowy format (NewFormatExcelParser):
     *  - Wiersz 0: stanowiska (Manager, Deputy Manager, Team Leader, OBSŁUGA)
     *  - Wiersz 1: imiona pracowników
     *  - Kolumna 0: numery dni (1-31)
     */
    private boolean detectOldFormat(Sheet sheet) {
        int score = 0;

        // --- Sygnały STAREGO formatu ---

        // Sygnał 1: wiersz 3 (idx), kol 2 – data
        Row row3 = sheet.getRow(3);
        if (row3 != null) {
            Cell c2 = row3.getCell(2);
            if (c2 != null) {
                if (c2.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c2)) {
                    score += 3;
                    Log.d(TAG, "Sygnał +3: data NUMERIC w wierszu 3, kol 2");
                } else if (c2.getCellType() == CellType.STRING) {
                    String v = c2.getStringCellValue();
                    if (v.matches(".*\\d{4}-\\d{2}-\\d{2}.*") || v.matches(".*\\d{2}\\.\\d{2}\\.\\d{4}.*")) {
                        score += 3;
                        Log.d(TAG, "Sygnał +3: data STRING w wierszu 3, kol 2: " + v);
                    }
                }
            }
            // Dodatkowe daty w kol 5, 8 (stary format co 3 kolumny)
            for (int col : new int[]{5, 8, 11}) {
                Cell c = row3.getCell(col);
                if (c != null && c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
                    score += 2;
                    Log.d(TAG, "Sygnał +2: data w kol " + col + " (stary)");
                    break;
                }
            }
        }

        // Sygnał 2: wiersz 5 (idx), kol 0 – cyfra (lp)
        Row row5 = sheet.getRow(5);
        if (row5 != null) {
            Cell lp = row5.getCell(0);
            if (lp != null) {
                if (lp.getCellType() == CellType.NUMERIC) {
                    score += 2;
                    Log.d(TAG, "Sygnał +2: liczba (lp) NUMERIC w wierszu 5, kol 0");
                } else if (lp.getCellType() == CellType.STRING) {
                    String v = lp.getStringCellValue().trim();
                    if (!v.isEmpty() && Character.isDigit(v.charAt(0))) {
                        score += 2;
                        Log.d(TAG, "Sygnał +2: liczba (lp) STRING w wierszu 5, kol 0: " + v);
                    }
                }
            }
        }

        // --- Sygnały NOWEGO formatu (odejmujemy) ---

        // Sygnał: pierwsze 6 wierszy zawiera stanowiska → nowy format
        boolean hasRoles = false;
        for (int r = 0; r <= 5; r++) {
            Row row = sheet.getRow(r);
            if (row != null) {
                for (int c = 0; c < Math.min(row.getLastCellNum(), 15); c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null && cell.getCellType() == CellType.STRING) {
                        String v = cell.getStringCellValue().toLowerCase();
                        if (v.contains("team leader") || v.contains("manager") || v.contains("obsługa")) {
                            score -= 5; // mocny sygnał nowego formatu
                            Log.d(TAG, "Sygnał -5: stanowisko w wierszu " + r + " (nowy format): " + v);
                            hasRoles = true;
                            break;
                        }
                    }
                }
            }
            if (hasRoles) break;
        }

        // Sygnał: kolumna 0, wiersze 2-5 zawiera cyfry 1-31 (numer dnia) → nowy format
        int dayNumCount = 0;
        for (int r = 2; r <= 6; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Cell c0 = row.getCell(0);
            if (c0 != null && c0.getCellType() == CellType.NUMERIC) {
                double v = c0.getNumericCellValue();
                if (v >= 1 && v <= 31) dayNumCount++;
            }
        }
        if (dayNumCount >= 3) {
            score -= 2;
            Log.d(TAG, "Sygnał -2: numery dni w kol 0 (nowy format), count=" + dayNumCount);
        }

        Log.d(TAG, "Wynik detekcji: score=" + score + " → " + (score >= 2 ? "STARY" : "NOWY") + " format");
        return score >= 2;
    }
}
