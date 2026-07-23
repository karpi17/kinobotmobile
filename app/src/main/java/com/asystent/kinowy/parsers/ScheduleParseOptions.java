package com.asystent.kinowy.parsers;

/**
 * Opcje parsowania dla parserów grafik (Excel, PDF, OCR).
 */
public class ScheduleParseOptions {
    private final String targetUserName;
    private final boolean safeMode;

    public ScheduleParseOptions(String targetUserName, boolean safeMode) {
        this.targetUserName = targetUserName;
        this.safeMode = safeMode;
    }

    public static ScheduleParseOptions defaultOptions(String targetUserName) {
        return new ScheduleParseOptions(targetUserName, true);
    }

    public String getTargetUserName() {
        return targetUserName;
    }

    public boolean isSafeMode() {
        return safeMode;
    }
}
