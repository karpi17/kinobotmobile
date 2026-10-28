package com.asystent.kinowy.parsers;

import com.asystent.kinowy.models.GlobalShift;
import com.asystent.kinowy.models.Shift;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class NovemberExcelTest {

    @Test
    public void testParseNovemberSchedule() throws Exception {
        File file = new File("../grafik_listopad.xlsx");
        if (!file.exists()) file = new File("grafik_listopad.xlsx");
        if (!file.exists()) file = new File("j:/kinobot/grafik_listopad.xlsx");
        assertTrue("Plik grafik_listopad.xlsx musi istnieć", file.exists());

        NewFormatExcelParser parser = new NewFormatExcelParser();
        ScheduleParseOptions options = new ScheduleParseOptions("Kacper", "Team Leader", 2026, true);

        try (FileInputStream fis = new FileInputStream(file)) {
            ScheduleParseResult result = parser.parse(fis, options);
            assertNotNull(result);

            System.out.println("Confidence: " + result.getConfidence());
            System.out.println("Found names: " + result.getFoundNames());
            System.out.println("Target user shifts: " + result.getTargetUserShifts().size());

            for (Shift s : result.getTargetUserShifts()) {
                System.out.println(s.getDate() + " " + s.getStartTime() + " - " + s.getEndTime()
                        + " (" + s.getCategory() + ") desc='" + s.getDescription()
                        + "' closing=" + s.isClosingShift() + " | ekipa: " + s.getClosingCrew());
            }

            // Sprawdzamy zmiany Kacpra (Team Leader)
            List<Shift> kacperShifts = result.getTargetUserShifts();
            assertEquals(30, kacperShifts.size()); // 30 dni w listopadzie

            // 1 listopada (niedziela) - Kacper TL ma WOLNE
            Shift d1 = kacperShifts.get(0);
            assertEquals("2026-11-01", d1.getDate());
            assertEquals("WOLNE", d1.getDescription());

            // 2 listopada (poniedziałek) - Kacper TL ma open (09:00 - 17:00)
            Shift d2 = kacperShifts.get(1);
            assertEquals("2026-11-02", d2.getDate());
            assertEquals("09:00", d2.getStartTime());
            assertEquals("17:00", d2.getEndTime());
            assertEquals("TL", d2.getCategory());

            // 6 listopada (piątek) - Kacper TL ma close od 18 (18:00 - 01:00)
            Shift d6 = kacperShifts.get(5);
            assertEquals("2026-11-06", d6.getDate());
            assertEquals("18:00", d6.getStartTime());
            assertEquals("01:00", d6.getEndTime());
            assertTrue(d6.isClosingShift());
            assertEquals("TL (ZAMEK)", d6.getCategory());

            // 7 listopada (sobota) - Kacper TL ma 12-20
            Shift d7 = kacperShifts.get(6);
            assertEquals("2026-11-07", d7.getDate());
            assertEquals("12:00", d7.getStartTime());
            assertEquals("20:00", d7.getEndTime());
            assertEquals("TL", d7.getCategory());
        }
    }
}
