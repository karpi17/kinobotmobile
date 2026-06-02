package com.asystent.kinowy.utils;

import com.asystent.kinowy.models.GlobalShift;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Testy jednostkowe dla {@link ShiftUtils}.
 * <p>
 * Pokrycie: toMinutes, getOverlappingShifts (dzienne, nocne, edge-cases),
 * formatOverlappingShifts, formatWidgetCrew.
 */
public class ShiftUtilsTest {

    // ═══════════════════════════════════════════════════════════════════
    // toMinutes
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void toMinutes_midnight() {
        assertEquals(0, ShiftUtils.toMinutes("00:00"));
    }

    @Test
    public void toMinutes_noon() {
        assertEquals(720, ShiftUtils.toMinutes("12:00"));
    }

    @Test
    public void toMinutes_endOfDay() {
        assertEquals(23 * 60 + 59, ShiftUtils.toMinutes("23:59"));
    }

    @Test
    public void toMinutes_morningShift() {
        assertEquals(9 * 60 + 30, ShiftUtils.toMinutes("09:30"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // getOverlappingShifts — podstawowe scenariusze
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void overlap_nullInputs_returnEmptyList() {
        List<GlobalShift> result = ShiftUtils.getOverlappingShifts(null, "18:00", new ArrayList<>());
        assertNotNull(result);
        assertTrue(result.isEmpty());

        result = ShiftUtils.getOverlappingShifts("09:00", null, new ArrayList<>());
        assertTrue(result.isEmpty());

        result = ShiftUtils.getOverlappingShifts("09:00", "18:00", null);
        assertTrue(result.isEmpty());
    }

    @Test
    public void overlap_emptyList_returnEmptyList() {
        List<GlobalShift> result = ShiftUtils.getOverlappingShifts("09:00", "17:00", new ArrayList<>());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void overlap_identicalShift_detected() {
        GlobalShift gs = createShift("Kacper", "09:00", "17:00");
        List<GlobalShift> result = ShiftUtils.getOverlappingShifts("09:00", "17:00", Collections.singletonList(gs));
        assertEquals(1, result.size());
        assertEquals("Kacper", result.get(0).getName());
    }

    @Test
    public void overlap_partialOverlap_detected() {
        // Moja zmiana: 10:00-18:00, kolega: 14:00-22:00 → overlap 14:00-18:00
        GlobalShift gs = createShift("Ola", "14:00", "22:00");
        List<GlobalShift> result = ShiftUtils.getOverlappingShifts("10:00", "18:00", Collections.singletonList(gs));
        assertEquals(1, result.size());
    }

    @Test
    public void overlap_noOverlap_emptyResult() {
        // Moja zmiana: 09:00-13:00, kolega: 14:00-22:00 → brak overlapu
        GlobalShift gs = createShift("Tomek", "14:00", "22:00");
        List<GlobalShift> result = ShiftUtils.getOverlappingShifts("09:00", "13:00", Collections.singletonList(gs));
        assertTrue(result.isEmpty());
    }

    @Test
    public void overlap_adjacentShifts_noOverlap() {
        // Moja zmiana kończy się o 14:00, kolega zaczyna o 14:00 → NIE nachodzą się
        GlobalShift gs = createShift("Ania", "14:00", "22:00");
        List<GlobalShift> result = ShiftUtils.getOverlappingShifts("09:00", "14:00", Collections.singletonList(gs));
        assertTrue(result.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════════
    // getOverlappingShifts — zmiany nocne (przez północ)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void overlap_nightShift_myShiftCrossedMidnight() {
        // Mój zamek: 18:00-02:00, kolega: 20:00-02:00 → overlap
        GlobalShift gs = createShift("Kacper", "20:00", "02:00");
        List<GlobalShift> result = ShiftUtils.getOverlappingShifts("18:00", "02:00", Collections.singletonList(gs));
        assertEquals(1, result.size());
    }

    @Test
    public void overlap_nightShift_colleagueCrossedMidnight() {
        // Moja zmiana: 22:00-06:00, kolega: 23:00-07:00 → overlap
        GlobalShift gs = createShift("Bartek", "23:00", "07:00");
        List<GlobalShift> result = ShiftUtils.getOverlappingShifts("22:00", "06:00", Collections.singletonList(gs));
        assertEquals(1, result.size());
    }

    @Test
    public void overlap_nightShift_noOverlap() {
        // Moja zmiana: 06:00-14:00, kolega zamek: 22:00-06:00 → brak overlapu
        GlobalShift gs = createShift("Tomek", "22:00", "06:00");
        List<GlobalShift> result = ShiftUtils.getOverlappingShifts("06:00", "14:00", Collections.singletonList(gs));
        assertTrue(result.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════════
    // getOverlappingShifts — wiele osób
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void overlap_multipleShifts_filtersCorrectly() {
        List<GlobalShift> shifts = Arrays.asList(
                createShift("Kacper", "09:00", "17:00"),   // overlap
                createShift("Ola", "18:00", "02:00"),       // no overlap
                createShift("Tomek", "12:00", "20:00")      // overlap
        );
        List<GlobalShift> result = ShiftUtils.getOverlappingShifts("10:00", "16:00", shifts);
        assertEquals(2, result.size());
    }

    // ═══════════════════════════════════════════════════════════════════
    // formatOverlappingShifts
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void format_null_returnsNull() {
        assertNull(ShiftUtils.formatOverlappingShifts(null));
    }

    @Test
    public void format_empty_returnsNull() {
        assertNull(ShiftUtils.formatOverlappingShifts(new ArrayList<>()));
    }

    @Test
    public void format_singleShift_correctFormat() {
        GlobalShift gs = new GlobalShift("Kacper", "2026-06-02", "09:00", "17:00", "BAR");
        String result = ShiftUtils.formatOverlappingShifts(Collections.singletonList(gs));
        assertNotNull(result);
        assertTrue(result.contains("Kacper"));
        assertTrue(result.contains("09:00"));
        assertTrue(result.contains("17:00"));
        assertTrue(result.contains("BAR"));
    }

    @Test
    public void format_nullCategory_showsDefault() {
        // Konstruktor zamienia null → "UNKNOWN"
        GlobalShift gs = createShift("Ola", "10:00", "18:00");
        String result = ShiftUtils.formatOverlappingShifts(Collections.singletonList(gs));
        assertTrue(result.contains("UNKNOWN"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // formatWidgetCrew
    // ═══════════════════════════════════════════════════════════════════

    @Test
    public void widgetCrew_null_returnsNull() {
        assertNull(ShiftUtils.formatWidgetCrew(null));
    }

    @Test
    public void widgetCrew_single_containsPrefix() {
        GlobalShift gs = createShift("Kacper", "09:00", "17:00");
        String result = ShiftUtils.formatWidgetCrew(Collections.singletonList(gs));
        assertTrue(result.startsWith("Z Tobą: "));
        assertTrue(result.contains("Kacper"));
    }

    @Test
    public void widgetCrew_moreThan5_showsOverflow() {
        List<GlobalShift> shifts = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            shifts.add(createShift("Osoba" + i, "09:00", "17:00"));
        }
        String result = ShiftUtils.formatWidgetCrew(shifts);
        assertTrue(result.contains("+2"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════════

    private GlobalShift createShift(String name, String start, String end) {
        return new GlobalShift(name, "2026-06-02", start, end, null);
    }
}
