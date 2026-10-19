package com.asystent.kinowy.utils;

import com.asystent.kinowy.models.BhpAlert;
import com.asystent.kinowy.models.Shift;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BhpGuardTest {

    @Test
    public void checkSchedule_nightClopenNextDay_detects8hBreakAlert() {
        Shift closingShift = new Shift("2026-08-10", "17:00", "01:00", "Zamek", true, false, "ZAMEK");
        closingShift.setClosingShift(true);
        Shift morningOpen = new Shift("2026-08-11", "09:00", "17:00", "Open", true, false, "BAR");

        List<BhpAlert> alerts = BhpGuard.checkSchedule(Arrays.asList(closingShift, morningOpen));

        assertEquals(1, alerts.size());
        assertEquals("CLOPEN", alerts.get(0).getType());
        assertEquals("2026-08-11", alerts.get(0).getDate());
        assertTrue(alerts.get(0).getMessage().contains("8h"));
    }

    @Test
    public void checkSchedule_monthBoundaryNightClopen_detectsClopenAlert() {
        Shift aug31Closing = new Shift("2026-08-31", "17:00", "01:00", "Zamek", true, false, "ZAMEK");
        Shift sep01Open = new Shift("2026-09-01", "09:00", "17:00", "Open", true, false, "BAR");

        List<BhpAlert> alerts = BhpGuard.checkSchedule(Arrays.asList(sep01Open, aug31Closing));

        assertEquals(1, alerts.size());
        assertEquals("CLOPEN", alerts.get(0).getType());
        assertEquals("2026-09-01", alerts.get(0).getDate());
    }

    @Test
    public void checkSchedule_safeBreakAfterNightShift_returnsNoAlert() {
        Shift closingShift = new Shift("2026-08-10", "17:00", "01:00", "Zamek", true, false, "ZAMEK");
        Shift afternoonShift = new Shift("2026-08-11", "13:00", "21:00", "Popołudnie", true, false, "BAR");

        List<BhpAlert> alerts = BhpGuard.checkSchedule(Arrays.asList(closingShift, afternoonShift));

        assertTrue(alerts.isEmpty());
    }

    @Test
    public void checkSchedule_replacementAndWolneFilteredBeforeMarathon_resetsContinuity() {
        List<Shift> shifts = new ArrayList<>();
        shifts.add(new Shift("2026-08-01", "10:00", "16:00", "Praca", true, false, "BAR"));
        shifts.add(new Shift("2026-08-02", "10:00", "16:00", "Praca", true, false, "BAR"));
        shifts.add(new Shift("2026-08-03", "10:00", "16:00", "Praca", true, false, "BAR"));
        // Dzień 4 oddany (replacement) -> przerywa ciągłość maratonu
        shifts.add(new Shift("2026-08-04", "10:00", "16:00", "Oddana", true, true, "BAR"));
        shifts.add(new Shift("2026-08-05", "10:00", "16:00", "Praca", true, false, "BAR"));
        shifts.add(new Shift("2026-08-06", "10:00", "16:00", "Praca", true, false, "BAR"));
        shifts.add(new Shift("2026-08-07", "10:00", "16:00", "Praca", true, false, "BAR"));
        shifts.add(new Shift("2026-08-08", "10:00", "16:00", "Praca", true, false, "BAR"));

        List<BhpAlert> alerts = BhpGuard.checkSchedule(shifts);
        for (BhpAlert a : alerts) {
            assertFalse("Should not trigger MARATHON when day 4 is replacement", "MARATHON".equals(a.getType()));
        }
    }

    @Test
    public void checkSchedule_sevenConsecutiveDaysWithWolnePlaceholders_detectsMarathonOnlyWhenContinuous() {
        List<Shift> shifts = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            shifts.add(new Shift(String.format("2026-08-%02d", day), "10:00", "16:00", "Praca", true, false, "BAR"));
        }
        shifts.add(new Shift("2026-08-08", "", "", "WOLNE", false, false, "WOLNE"));

        List<BhpAlert> alerts = BhpGuard.checkSchedule(shifts);
        boolean foundMarathon = false;
        for (BhpAlert a : alerts) {
            if ("MARATHON".equals(a.getType()) && "2026-08-07".equals(a.getDate())) {
                foundMarathon = true;
            }
        }
        assertTrue(foundMarathon);
    }

    @Test
    public void checkSchedule_nullAndMalformedEntries_doesNotThrow() {
        List<Shift> shifts = Arrays.asList(
                null,
                new Shift(null, null, null, null, false, false, null),
                new Shift("2026-08-10", "17:00", "01:00", "Zamek", true, false, "ZAMEK")
        );
        List<BhpAlert> alerts = BhpGuard.checkSchedule(shifts);
        assertTrue(alerts.isEmpty());
        assertTrue(BhpGuard.checkSchedule(Collections.emptyList()).isEmpty());
        assertTrue(BhpGuard.checkSchedule(null).isEmpty());
    }

    @Test
    public void checkSchedule_overtimeAlertCarriesLatestShiftDateInWeek() {
        List<Shift> shifts = Arrays.asList(
                new Shift("2026-07-27", "08:00", "20:00", "Praca", true, false, "BAR"), // 12h
                new Shift("2026-07-28", "08:00", "20:00", "Praca", true, false, "BAR"), // 24h
                new Shift("2026-07-29", "08:00", "20:00", "Praca", true, false, "BAR"), // 36h
                new Shift("2026-07-30", "08:00", "20:00", "Praca", true, false, "BAR"), // 48h
                new Shift("2026-07-31", "10:00", "16:00", "Praca", true, false, "BAR")  // 54h (>48h)
        );

        List<BhpAlert> alerts = BhpGuard.checkSchedule(shifts);
        BhpAlert overtime = null;
        for (BhpAlert a : alerts) {
            if ("OVERTIME".equals(a.getType())) {
                overtime = a;
                break;
            }
        }
        org.junit.Assert.assertNotNull(overtime);
        assertEquals("2026-07-31", overtime.getDate());
    }
}
