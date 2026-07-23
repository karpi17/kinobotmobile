package com.asystent.kinowy.parsers;

/**
 * Ostrzeżenie wygenerowane podczas parsowania grafiku (np. niepewna godzina, nieznane imię itp.).
 */
public class ParserWarning {
    public enum Type {
        UNKNOWN_NAME,
        UNKNOWN_DATE,
        UNKNOWN_TIME,
        LOW_CONFIDENCE,
        DUPLICATE_SHIFT,
        NEEDS_MANUAL_MAPPING,
        OTHER
    }

    private final Type type;
    private final String message;
    private final String rawText;

    public ParserWarning(Type type, String message, String rawText) {
        this.type = type;
        this.message = message;
        this.rawText = rawText;
    }

    public ParserWarning(Type type, String message) {
        this(type, message, null);
    }

    public Type getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public String getRawText() {
        return rawText;
    }
}
