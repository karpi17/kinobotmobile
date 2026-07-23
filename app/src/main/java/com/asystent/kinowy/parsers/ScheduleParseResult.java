package com.asystent.kinowy.parsers;

import com.asystent.kinowy.models.GlobalShift;
import com.asystent.kinowy.models.Shift;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ujednolicony wynik parsowania grafiku ze wszystkich źródeł (Excel, PDF, OCR).
 */
public class ScheduleParseResult {
    private final Map<String, List<Shift>> scheduleByName;
    private final List<String> allDates;
    private final List<String> foundNames;
    private final List<GlobalShift> allGlobalShifts;
    private final List<ParserWarning> warnings;
    private final float confidence;
    private final String sourceDescription;
    private List<Shift> targetUserShifts;

    public ScheduleParseResult(Map<String, List<Shift>> scheduleByName,
                               List<String> allDates,
                               List<String> foundNames,
                               List<GlobalShift> allGlobalShifts,
                               List<ParserWarning> warnings,
                               float confidence,
                               String sourceDescription) {
        this.scheduleByName = scheduleByName != null ? scheduleByName : new LinkedHashMap<>();
        this.allDates = allDates != null ? allDates : new ArrayList<>();
        this.foundNames = foundNames != null ? foundNames : new ArrayList<>();
        this.allGlobalShifts = allGlobalShifts != null ? allGlobalShifts : new ArrayList<>();
        this.warnings = warnings != null ? warnings : new ArrayList<>();
        this.confidence = confidence;
        this.sourceDescription = sourceDescription != null ? sourceDescription : "Grafik";
        this.targetUserShifts = new ArrayList<>();
    }

    public Map<String, List<Shift>> getScheduleByName() {
        return scheduleByName;
    }

    public List<String> getAllDates() {
        return allDates;
    }

    public List<String> getFoundNames() {
        return foundNames;
    }

    public List<GlobalShift> getAllGlobalShifts() {
        return allGlobalShifts;
    }

    public List<ParserWarning> getWarnings() {
        return warnings;
    }

    public float getConfidence() {
        return confidence;
    }

    public String getSourceDescription() {
        return sourceDescription;
    }

    public List<Shift> getTargetUserShifts() {
        return targetUserShifts;
    }

    public void setTargetUserShifts(List<Shift> targetUserShifts) {
        this.targetUserShifts = targetUserShifts != null ? targetUserShifts : new ArrayList<>();
    }
}
