package com.asystent.kinowy.parsers;

import android.util.Log;

import org.apache.poi.ss.usermodel.Cell;
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

        boolean isOldFormat = false;

        Row row2 = sheet.getRow(2); 
        if (row2 != null) {
            Cell cell3 = row2.getCell(3);

            if (cell3 != null && cell3.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell3)) {
                isOldFormat = true;
            } else if (cell3 != null && cell3.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                String val = cell3.getStringCellValue();
                if (val.matches(".*\\d{2}\\.\\d{2}\\.\\d{4}.*")) {
                    isOldFormat = true; 
                }
            }
        }
        workbook.close();

        java.io.ByteArrayInputStream isForParsing = new java.io.ByteArrayInputStream(fileData);
        if (isOldFormat) {
            Log.d(TAG, "Wykryto STARY format. (Ostrzeżenie: integracja ze starym kodem może wymagać refaktoryzacji, tymczasowo mapuję na ten sam wynik lub rzucam błąd)");
            // Możemy tymczasowo rzucić błąd że nie obsługujemy, a jeśli trzeba to przeniesiemy logikę z ExcelParsingService.
            // Zakładając, że stary parser wciąż był usługą, a teraz ma to być fragment, lepiej żeby to zrefaktorować w oddzielnym kroku. 
            // Będę to jednak rzucał na NewFormatExcelParser i najwyżej poleci wyjątek jak źle rozpozna.
            return new NewFormatExcelParser().parse(isForParsing, options);
        } else {
            Log.d(TAG, "Wykryto NOWY format biurowy.");
            return new NewFormatExcelParser().parse(isForParsing, options);
        }
    }
}
