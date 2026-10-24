package com.asystent.kinowy.utils;

import com.asystent.kinowy.models.ActiveEmployee;
import com.asystent.kinowy.models.GlobalShift;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReplacementFinderTest {

    @Test
    public void findCandidates_excludesWorkingAndClopenViolators() {
        // Szukamy zastępstwa na 2026-08-10 09:00 - 17:00
        List<String> known = Arrays.asList("Kacper", "Anna", "Bartek", "Celina", "Dawid");

        List<GlobalShift> globalShifts = Arrays.asList(
                // Anna pracuje już 2026-08-10 → odpada
                new GlobalShift("Anna", "2026-08-10", "14:00", "22:00", "BAR"),
                // Bartek zamykał 2026-08-09 do 01:00 → na 09:00 ma tylko 8h przerwy (Clopen < 11h) → odpada
                new GlobalShift("Bartek", "2026-08-09", "17:00", "01:00", "ZAMEK"),
                // Celina pracowała 2026-08-09 09:00-17:00 → ma 16h przerwy do 09:00 → pasuje!
                new GlobalShift("Celina", "2026-08-09", "09:00", "17:00", "OBSŁUGA")
                // Dawid ma całkowite wolne → pasuje!
        );

        List<ReplacementFinder.Candidate> candidates = ReplacementFinder.findCandidates(
                "2026-08-10", "09:00", "17:00", "Kacper", known, globalShifts);

        assertEquals(2, candidates.size());
        assertEquals("Celina", candidates.get(0).getName());
        assertEquals("Dawid", candidates.get(1).getName());
    }

    @Test
    public void findCandidates_whenLeadershipShift_onlySuggestsLeaders() {
        // Kacper szuka zastępstwa na zmianę Team Leader (TL)
        List<ActiveEmployee> employees = Arrays.asList(
                new ActiveEmployee("Kacper W.", true, ActiveEmployee.ROLE_TL, "2026-10-20"),
                new ActiveEmployee("Anna K.", true, ActiveEmployee.ROLE_EMPLOYEE, "2026-10-20"), // zwykły pracownik -> odpada!
                new ActiveEmployee("Marek T.", true, ActiveEmployee.ROLE_TL, "2026-10-20"),       // TL z wolnym -> pasuje!
                new ActiveEmployee("Zuzanna R.", true, ActiveEmployee.ROLE_MANAGER, "2026-10-20") // Kierownik -> pasuje!
        );

        List<GlobalShift> globalShifts = Arrays.asList(
                new GlobalShift("Kacper W.", "2026-10-25", "17:00", "01:00", "TL"),
                new GlobalShift("Anna K.", "2026-10-24", "12:00", "20:00", "BAR"),
                new GlobalShift("Marek T.", "2026-10-24", "09:00", "17:00", "TL"),
                new GlobalShift("Zuzanna R.", "2026-10-24", "09:00", "17:00", "TL")
        );

        List<ReplacementFinder.Candidate> candidates = ReplacementFinder.findCandidates(
                "2026-10-25", "17:00", "01:00", "TL", "Kacper W.", employees, globalShifts);

        assertEquals(2, candidates.size());
        assertTrue(candidates.stream().anyMatch(c -> "Marek T.".equals(c.getName())));
        assertTrue(candidates.stream().anyMatch(c -> "Zuzanna R.".equals(c.getName())));
        assertFalse(candidates.stream().anyMatch(c -> "Anna K.".equals(c.getName())));
    }

    @Test
    public void findCandidates_excludesInactiveEmployees() {
        List<ActiveEmployee> employees = Arrays.asList(
                new ActiveEmployee("Kacper W.", true, ActiveEmployee.ROLE_EMPLOYEE, "2026-10-20"),
                new ActiveEmployee("Jan Były", false, ActiveEmployee.ROLE_EMPLOYEE, "2026-05-10"), // oznaczony jako były -> odpada!
                new ActiveEmployee("Piotr Aktywny", true, ActiveEmployee.ROLE_EMPLOYEE, "2026-10-20")
        );

        List<GlobalShift> globalShifts = Arrays.asList(
                new GlobalShift("Piotr Aktywny", "2026-10-20", "09:00", "17:00", "BAR")
        );

        List<ReplacementFinder.Candidate> candidates = ReplacementFinder.findCandidates(
                "2026-10-25", "12:00", "20:00", "BAR", "Kacper W.", employees, globalShifts);

        assertEquals(1, candidates.size());
        assertEquals("Piotr Aktywny", candidates.get(0).getName());
    }

    @Test
    public void buildMessengerMessage_formatsCleanly() {
        String msg = ReplacementFinder.buildMessengerMessage("2026-08-10", "17:00", "01:00", "ZAMEK");
        assertTrue(msg.contains("17:00–01:00"));
        assertTrue(msg.contains("[ZAMEK]"));
        assertFalse(msg.isEmpty());
    }

    @Test
    public void buildMessengerMessage_forLeadershipShift_tailoredText() {
        String msg = ReplacementFinder.buildMessengerMessage("2026-10-25", "17:00", "01:00", "TL");
        assertTrue(msg.contains("Team Leader / Kierowniczą"));
        assertTrue(msg.contains("Czy któryś z Team Leaderów / Kierowników mógłby mnie zastąpić?"));
    }

    @Test
    public void findCandidates_excludesCurrentUserWithLastNameInitial() {
        List<String> known = Arrays.asList("Kacper W.", "Anna");
        List<ReplacementFinder.Candidate> candidates = ReplacementFinder.findCandidates(
                "2026-08-10", "09:00", "17:00", "Kacper", known, Collections.emptyList());

        assertEquals(1, candidates.size());
        assertEquals("Anna", candidates.get(0).getName());
    }
}
