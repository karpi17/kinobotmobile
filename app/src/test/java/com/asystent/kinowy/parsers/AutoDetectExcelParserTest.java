package com.asystent.kinowy.parsers;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Testy dla AutoDetectExcelParser – weryfikacja czy dobrze rozróżnia 
 * STARY (wiersze poziome dla 1 pracownika przez 7 dni) 
 * i NOWY format grafiku (1 wiersz = 1 dzień dla całej ekipy).
 */
public class AutoDetectExcelParserTest {

    private boolean detectOldFormat(Workbook wb) throws Exception {
        AutoDetectExcelParser parser = new AutoDetectExcelParser();
        Method method = AutoDetectExcelParser.class.getDeclaredMethod("detectOldFormat", Sheet.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(parser, wb.getSheetAt(0));
    }

    @Test
    public void testDetectNewFormat_isCorrectlyDetected() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Grafik");
            
            // Wiersz 0: stanowiska (wskazuje na Nowy Format - kolumny 1, 2)
            Row r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue("SIERPIEŃ");
            r0.createCell(1).setCellValue("Team Leader");
            r0.createCell(2).setCellValue("OBSŁUGA");

            // Wiersz 1: Imiona
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Dzień");
            r1.createCell(1).setCellValue("Kacper");
            r1.createCell(2).setCellValue("Anna");

            // Wiersz 2, kolumna 0 = numer dnia "1" -> sygnal nowego formatu
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue(1);
            
            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue(2);

            assertFalse("Powinno wykryć jako NOWY format (zwrócić false dla oldFormat)", detectOldFormat(wb));
        }
    }

    @Test
    public void testDetectOldFormat_isCorrectlyDetected() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Grafik");

            // Wiersz 3, kolumna 2 i 5 = data (wskazuje na stary format gdzie pracownik to wiersz, a dni to kolumny)
            Row r3 = sheet.createRow(3);
            r3.createCell(2).setCellValue("2026-08-01");
            r3.createCell(5).setCellValue("2026-08-02");

            // Wiersz 4: nazwy dni
            Row r4 = sheet.createRow(4);
            r4.createCell(2).setCellValue("Poniedziałek");

            // Wiersz 5: pracownicy (Lp w 0, Imię w 1)
            Row r5 = sheet.createRow(5);
            r5.createCell(0).setCellValue(1);
            r5.createCell(1).setCellValue("Kacper Testowy");

            assertTrue("Powinno wykryć jako STARY format (zwrócić true dla oldFormat)", detectOldFormat(wb));
        }
    }
}
