package com.asystent.kinowy.utils;

import com.asystent.kinowy.models.GlobalShift;
import com.asystent.kinowy.models.RateHistory;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.models.YearlyStats;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StatisticsCalculatorTest {

    @Test
    public void calculate_excludesUserSelfDeletedAndNonOverlappingCoworkers_andIgnoresDaysOffInMarathonAndCategories() {
        Shift s1 = new Shift("2026-08-01", "16:00", "23:00", "Zamek", true, false, "BAR");
        s1.setClosingShift(true);

        Shift s2 = new Shift("2026-08-02", "16:00", "23:00", "Zamek", true, false, "BAR");
        s2.setClosingShift(true);

        // Dzień wolny — nie powinien liczyć się do maratonu ani do najczęstszej kategorii
        Shift s3Wolne = new Shift("2026-08-03", "", "", "WOLNE", false, false, "WOLNE");
        Shift s4Wolne = new Shift("2026-08-04", "", "", "WOLNE", false, false, "WOLNE");
        Shift s5Wolne = new Shift("2026-08-05", "", "", "WOLNE", false, false, "WOLNE");

        // Zmiana oddana (isReplacement = true) — nie powinna liczyć się do godzin ani maratonu
        Shift s6Replacement = new Shift("2026-08-06", "10:00", "18:00", "Oddana", true, true, "BAR");

        List<Shift> shifts = Arrays.asList(s1, s2, s3Wolne, s4Wolne, s5Wolne, s6Replacement);

        // GlobalShifts na 2026-08-01 i 2026-08-02:
        // 1. Sam użytkownik ("Kacper W.") — musi zostać wykluczony z topCoworker
        GlobalShift gsSelf1 = new GlobalShift("Kacper W.", "2026-08-01", "16:00", "23:00", "BAR");
        GlobalShift gsSelf2 = new GlobalShift("Kacper W.", "2026-08-02", "16:00", "23:00", "BAR");

        // 2. Współpracownik rano (08:00-15:00) — NIE pokrywa się z 16:00-23:00
        GlobalShift gsMorning1 = new GlobalShift("Marek Ranny", "2026-08-01", "08:00", "15:00", "OBSŁUGA");
        GlobalShift gsMorning2 = new GlobalShift("Marek Ranny", "2026-08-02", "08:00", "15:00", "OBSŁUGA");

        // 3. Współpracownik usunięty (isDeleted = true) — musi zostać zignorowany
        GlobalShift gsDeleted = new GlobalShift("Usunięty Pracownik", "2026-08-01", "16:00", "23:00", "BAR");
        gsDeleted.setDeleted(true);

        // 4. Realny współpracownik pokrywający się czasowo (17:00-23:00)
        GlobalShift gsOverlap1 = new GlobalShift("Anna Nowak", "2026-08-01", "17:00", "23:00", "BAR");
        GlobalShift gsOverlap2 = new GlobalShift("Anna Nowak", "2026-08-02", "17:00", "23:00", "BAR");

        List<GlobalShift> globalShifts = Arrays.asList(
                gsSelf1, gsSelf2, gsMorning1, gsMorning2, gsDeleted, gsOverlap1, gsOverlap2
        );

        List<RateHistory> rates = Collections.singletonList(new RateHistory("2026-01-01", 30.0f, null));

        YearlyStats stats = StatisticsCalculator.calculate(
                "2026", shifts, globalShifts, Collections.emptyList(), Collections.emptyList(), rates, "Kacper"
        );

        assertEquals(14.0, stats.getTotalHours(), 0.01);
        assertEquals(2, stats.getClosingShifts());
        assertEquals(2, stats.getLongestMarathon());
        assertEquals("BAR", stats.getTopCategory());
        assertTrue("Top coworker should be Anna Nowak, got: " + stats.getTopCoworker(),
                stats.getTopCoworker().startsWith("Anna Nowak"));
    }
}
