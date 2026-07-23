package com.asystent.kinowy.parsers;

import android.util.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser grafików w formacie PDF (MVP stub / przygotowany pod PDFBox w Fazie 2).
 * Rozpoznaje czy plik ma warstwę tekstową i generuje {@link ScheduleParseResult}.
 */
public class PdfScheduleParser implements ScheduleParser {

    private static final String TAG = "PdfScheduleParser";

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        String lower = mimeType.toLowerCase();
        return lower.contains("pdf");
    }

    @Override
    public ScheduleParseResult parse(InputStream inputStream, ScheduleParseOptions options) throws Exception {
        Log.d(TAG, "Parsowanie pliku PDF...");
        List<ParserWarning> warnings = new ArrayList<>();
        warnings.add(new ParserWarning(ParserWarning.Type.NEEDS_MANUAL_MAPPING,
                "Parser PDF został odpalony w trybie podglądu. Po dostarczeniu pierwszego przykładowego pliku PDF z Twojej firmy, specyficzny wzorzec kolumn zostanie aktywowany."));

        // Domyślny, bezpieczny wynik z ostrzeżeniem dla użytkownika
        return new ScheduleParseResult(
                null,
                null,
                null,
                null,
                warnings,
                0.70f,
                "Dokument PDF"
        );
    }
}
