package com.asystent.kinowy.parsers;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import com.asystent.kinowy.models.GlobalShift;
import com.asystent.kinowy.models.Shift;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Testy integracyjne parsera Excela.
 * Tworzy testowy plik XLSX w pamięci (ByteArrayOutputStream), a następnie 
 * puszcza przez NewFormatExcelParser. Zastępuje to konieczność przetrzymywania 
 * binarnego pliku w katalogu resources.
 */
public class ExcelParserIntegrationTest {

    private NewFormatExcelParser parser;

    @Before
    public void setUp() {
        parser = new NewFormatExcelParser();
    }

    private ByteArrayInputStream createMockExcelSchedule() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Grafik");

            // Wiersz 0: Nagłówki i miesiąc (np. "SIERPIEŃ")
            Row r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue("SIERPIEŃ");
            r0.createCell(1).setCellValue("Team Leader");
            r0.createCell(2).setCellValue("Obsługa");

            // Wiersz 1: Imiona
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Dzień");
            r1.createCell(1).setCellValue("Kacper");
            r1.createCell(2).setCellValue("Anna");

            // Dni miesiąca (od wiersza 2 do 32 dla 31 dni, ale wstawmy tylko 3 dni dla testu)
            // Dzień 1: Kacper (open), Anna (12-20)
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue(1);
            r2.createCell(1).setCellValue("open");
            r2.createCell(2).setCellValue("12-20");

            // Dzień 2: Kacper (close), Anna (off)
            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue(2);
            r3.createCell(1).setCellValue("close");
            r3.createCell(2).setCellValue("off");

            // Dzień 3: Kacper (TMS paczki 17–22), Anna (10/18)
            Row r4 = sheet.createRow(4);
            r4.createCell(0).setCellValue(3);
            r4.createCell(1).setCellValue("TMS paczki 17–22");
            r4.createCell(2).setCellValue("10/18");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Test
    public void testFullIntegration_parseMockExcel() throws Exception {
        ByteArrayInputStream is = createMockExcelSchedule();
        ScheduleParseOptions options = new ScheduleParseOptions("Kacper", "Team Leader", 2026, true);
        
        ScheduleParseResult result = parser.parse(is, options);
        
        assertNotNull(result);
        
        // 1. Sprawdzamy wyciągnięte nazwy i stanowiska
        List<String> names = result.getFoundNames();
        assertTrue(names.contains("Kacper"));
        assertTrue(names.contains("Anna"));

        // 2. Sprawdzamy zmiany Kacpra (target user)
        List<Shift> userShifts = result.getTargetUserShifts();
        assertEquals(3, userShifts.size());

        // Shift 1: open (09:00 - 17:00)
        Shift s1 = userShifts.get(0);
        assertEquals("2026-08-01", s1.getDate());
        assertEquals("09:00", s1.getStartTime());
        assertEquals("17:00", s1.getEndTime());
        // Wg logiki parsera: wszyscy koledzy pracujący danego dnia
        // Anna w dniu 1 robi 12-20, więc powinna być w closing crew (chociaż to koledzy, closing crew w nazwie zostało)
        assertTrue(s1.getClosingCrew().contains("Anna"));

        // Shift 2: close (17:00 - 01:00)
        Shift s2 = userShifts.get(1);
        assertEquals("2026-08-02", s2.getDate());
        assertEquals("17:00", s2.getStartTime());
        assertEquals("01:00", s2.getEndTime());
        assertEquals(true, s2.isClosingShift());
        // Anna w dniu 2 ma 'off', więc Kacper jest sam / closing crew jest puste w stosunku do innych pracowników w excelu (z tych co parsowaliśmy)
        assertEquals("", s2.getClosingCrew().trim());

        // Shift 3: TMS paczki 17–22
        Shift s3 = userShifts.get(2);
        assertEquals("2026-08-03", s3.getDate());
        assertEquals("17:00", s3.getStartTime());
        assertEquals("22:00", s3.getEndTime());
        assertEquals("TMS paczki", s3.getDescription());
        assertTrue(s3.getClosingCrew().contains("Anna"));

        // 3. Sprawdzamy GlobalShifts
        List<GlobalShift> allGlobal = result.getAllGlobalShifts();
        assertEquals(5, allGlobal.size()); // 3 dni dla Kacpra + 2 dni pracy dla Anny (1 off pominięty)
    }
}
