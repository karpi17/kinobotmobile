package com.asystent.kinowy.utils;

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
    public void buildMessengerMessage_formatsCleanly() {
        String msg = ReplacementFinder.buildMessengerMessage("2026-08-10", "17:00", "01:00", "ZAMEK");
        assertTrue(msg.contains("17:00–01:00"));
        assertTrue(msg.contains("[ZAMEK]"));
        assertFalse(msg.isEmpty());
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
