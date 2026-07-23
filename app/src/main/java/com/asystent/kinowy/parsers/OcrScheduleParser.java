package com.asystent.kinowy.parsers;

import android.util.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser zdjęć grafików ze skanera OCR (ML Kit / Vision).
 */
public class OcrScheduleParser implements ScheduleParser {

    private static final String TAG = "OcrScheduleParser";

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        String lower = mimeType.toLowerCase();
        return lower.contains("image") || lower.contains("jpg") || lower.contains("png");
    }

    @Override
    public ScheduleParseResult parse(InputStream inputStream, ScheduleParseOptions options) throws Exception {
        Log.d(TAG, "Parsowanie obrazu OCR...");
        List<ParserWarning> warnings = new ArrayList<>();
        warnings.add(new ParserWarning(ParserWarning.Type.LOW_CONFIDENCE,
                "Skaner OCR wymaga zatwierdzenia podglądu zdjęć przed zapisanym grafikiem."));

        return new ScheduleParseResult(
                null,
                null,
                null,
                null,
                warnings,
                0.60f,
                "Zdjęcie OCR"
        );
    }
}
