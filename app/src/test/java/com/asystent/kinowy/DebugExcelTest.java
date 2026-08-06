package com.asystent.kinowy;

import org.junit.Test;
import java.io.FileInputStream;
import java.io.File;
import com.asystent.kinowy.network.ExcelParsingService;
import com.asystent.kinowy.models.Shift;

public class DebugExcelTest {
    @Test
    public void debugExcel() throws Exception {
        File file = new File("C:\\Users\\Karpi\\Downloads\\Grafik AJ. 07.08.2026 - 13.08.2026.xlsx");
        if (!file.exists()) {
            System.out.println("Plik nie istnieje!");
            return;
        }
        ExcelParsingService parser = new ExcelParsingService();
        ExcelParsingService.ParseResult result = parser.parseFullSchedule(new FileInputStream(file));
        
        java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter("C:\\Users\\Karpi\\Downloads\\excel_debug_output.txt"));
        writer.println("Znalazłem " + result.getFoundNames().size() + " osób.");
        for (String name : result.getFoundNames()) {
            writer.println("Pracownik: " + name);
            for (Shift s : result.getScheduleByName().get(name)) {
                writer.println("  " + s.getDate() + " | " + s.getCategory() + " | " + s.getStartTime() + "-" + s.getEndTime() + " | Ekipa: " + s.getClosingCrew());
            }
        }
        writer.close();
    }
}
