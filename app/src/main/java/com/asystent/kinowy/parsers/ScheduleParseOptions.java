package com.asystent.kinowy.parsers;

/**
 * Opcje parsowania dla parserów grafik (Excel, PDF, OCR).
 */
public class ScheduleParseOptions {
    private final String targetUserName;
    private final String targetRole;
    private final int selectedYear;
    private final boolean safeMode;

    public ScheduleParseOptions(String targetUserName, String targetRole, int selectedYear, boolean safeMode) {
        this.targetUserName = targetUserName;
        this.targetRole = targetRole;
        this.selectedYear = selectedYear;
        this.safeMode = safeMode;
    }

    public ScheduleParseOptions(String targetUserName, boolean safeMode) {
        this(targetUserName, null, -1, safeMode);
    }

    public static ScheduleParseOptions defaultOptions(String targetUserName) {
        return new ScheduleParseOptions(targetUserName, null, -1, true);
    }

    public String getTargetUserName() {
        return targetUserName;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public int getSelectedYear() {
        return selectedYear;
    }

    public boolean isSafeMode() {
        return safeMode;
    }
}
