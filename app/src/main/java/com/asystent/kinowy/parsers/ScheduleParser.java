package com.asystent.kinowy.parsers;

import java.io.InputStream;

/**
 * Główny interfejs dla wszystkich parserów grafików (Excel, PDF, OCR).
 */
public interface ScheduleParser {

    /**
     * Parsuje podany strumień i zwraca ujednolicony wynik parsowania.
     *
     * @param inputStream Strumień z plikiem grafiku
     * @param options     Opcje parsowania (imię użytkownika, tryb bezpieczny itp.)
     * @return Ujednolicony {@link ScheduleParseResult}
     * @throws Exception w przypadku błędu odczytu/parsowania
     */
    ScheduleParseResult parse(InputStream inputStream, ScheduleParseOptions options) throws Exception;

    /**
     * Sprawdza, czy ten parser obsługuje podany typ MIME lub rozszerzenie pliku.
     *
     * @param mimeType Mime-type lub nazwa pliku (np. "application/pdf", "xlsx")
     * @return true jeśli parser obsługuje dany format
     */
    boolean supports(String mimeType);
}
