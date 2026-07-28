package com.asystent.kinowy.parsers;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Testy jednostkowe dla NewFormatExcelParser i reguł parsowania tekstów zmian.
 */
public class NewFormatExcelParserTest {

    private NewFormatExcelParser parser;

    @Before
    public void setUp() {
        parser = new NewFormatExcelParser();
    }

    @Test
    public void testParseOpenShift() {
        NewFormatExcelParser.ParsedShiftInfo info = parser.parseShiftText("open");
        assertTrue(info.isShift);
        assertEquals("09:00", info.startTime);
        assertEquals("17:00", info.endTime);
        assertFalse(info.isClosingShift);
    }

    @Test
    public void testParseSzkOpenShift() {
        NewFormatExcelParser.ParsedShiftInfo info = parser.parseShiftText("szk open");
        assertTrue(info.isShift);
        assertEquals("09:00", info.startTime);
        assertEquals("17:00", info.endTime);
        assertFalse(info.isClosingShift);
    }

    @Test
    public void testParseCloseShift() {
        NewFormatExcelParser.ParsedShiftInfo info = parser.parseShiftText("close");
        assertTrue(info.isShift);
        assertEquals("17:00", info.startTime);
        assertEquals("01:00", info.endTime);
        assertTrue(info.isClosingShift);
    }

    @Test
    public void testParseCloseOdShift() {
        NewFormatExcelParser.ParsedShiftInfo info22 = parser.parseShiftText("close od 22");
        assertTrue(info22.isShift);
        assertEquals("22:00", info22.startTime);
        assertEquals("01:00", info22.endTime);
        assertTrue(info22.isClosingShift);

        NewFormatExcelParser.ParsedShiftInfo info20 = parser.parseShiftText("close od 20");
        assertTrue(info20.isShift);
        assertEquals("20:00", info20.startTime);
        assertEquals("01:00", info20.endTime);
        assertTrue(info20.isClosingShift);

        NewFormatExcelParser.ParsedShiftInfo info1930 = parser.parseShiftText("close od 19:30");
        assertTrue(info1930.isShift);
        assertEquals("19:30", info1930.startTime);
        assertEquals("01:00", info1930.endTime);
        assertTrue(info1930.isClosingShift);
    }

    @Test
    public void testParseOpenDoShift() {
        NewFormatExcelParser.ParsedShiftInfo info = parser.parseShiftText("open do 20");
        assertTrue(info.isShift);
        assertEquals("09:00", info.startTime);
        assertEquals("20:00", info.endTime);
        assertFalse(info.isClosingShift);

        NewFormatExcelParser.ParsedShiftInfo info1730 = parser.parseShiftText("open do 17.30");
        assertTrue(info1730.isShift);
        assertEquals("09:00", info1730.startTime);
        assertEquals("17:30", info1730.endTime);
        assertFalse(info1730.isClosingShift);
    }

    @Test
    public void testParseRangeShift() {
        NewFormatExcelParser.ParsedShiftInfo info14_22 = parser.parseShiftText("14–22");
        assertTrue(info14_22.isShift);
        assertEquals("14:00", info14_22.startTime);
        assertEquals("22:00", info14_22.endTime);

        NewFormatExcelParser.ParsedShiftInfo info12_20 = parser.parseShiftText("12-20");
        assertTrue(info12_20.isShift);
        assertEquals("12:00", info12_20.startTime);
        assertEquals("20:00", info12_20.endTime);

        NewFormatExcelParser.ParsedShiftInfo info10_18 = parser.parseShiftText("10/18");
        assertTrue(info10_18.isShift);
        assertEquals("10:00", info10_18.startTime);
        assertEquals("18:00", info10_18.endTime);

        NewFormatExcelParser.ParsedShiftInfo info1030_1830 = parser.parseShiftText("10.30-18.30");
        assertTrue(info1030_1830.isShift);
        assertEquals("10:30", info1030_1830.startTime);
        assertEquals("18:30", info1030_1830.endTime);

        NewFormatExcelParser.ParsedShiftInfo info1115_1945 = parser.parseShiftText("11:15 - 19:45");
        assertTrue(info1115_1945.isShift);
        assertEquals("11:15", info1115_1945.startTime);
        assertEquals("19:45", info1115_1945.endTime);
    }

    @Test
    public void testParseTaskWithRangeShift() {
        NewFormatExcelParser.ParsedShiftInfo infoTms = parser.parseShiftText("TMS paczki 17–22");
        assertTrue(infoTms.isShift);
        assertEquals("17:00", infoTms.startTime);
        assertEquals("22:00", infoTms.endTime);
        assertEquals("TMS paczki", infoTms.description);
    }

    @Test
    public void testParseOffAndVacation() {
        NewFormatExcelParser.ParsedShiftInfo offInfo = parser.parseShiftText("off");
        assertFalse(offInfo.isShift);

        NewFormatExcelParser.ParsedShiftInfo vacInfo = parser.parseShiftText("vacation");
        assertFalse(vacInfo.isShift);

        NewFormatExcelParser.ParsedShiftInfo wzInfo = parser.parseShiftText("WZ");
        assertFalse(wzInfo.isShift);
    }
}
