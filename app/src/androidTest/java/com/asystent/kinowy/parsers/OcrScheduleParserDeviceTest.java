package com.asystent.kinowy.parsers;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.asystent.kinowy.models.Shift;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class OcrScheduleParserDeviceTest {

    @Test
    public void testParseNovemberImage() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        InputStream is = context.getAssets().open("grafik_listopad.png");
        assertNotNull("Image asset not found", is);

        OcrScheduleParser parser = new OcrScheduleParser();
        ScheduleParseOptions options = new ScheduleParseOptions("Kacper", "Team Leader", 2026, true);

        ScheduleParseResult result = parser.parse(is, options);
        assertNotNull(result);

        System.out.println("=== TEST OCR RESULT ===");
        System.out.println("All Dates (" + result.getAllDates().size() + "): " + result.getAllDates());
        System.out.println("Found Names (" + result.getFoundNames().size() + "): " + result.getFoundNames());
        System.out.println("All Global Shifts count: " + result.getAllGlobalShifts().size());
        System.out.println("Target User Shifts count: " + result.getTargetUserShifts().size());

        for (Shift s : result.getTargetUserShifts()) {
            System.out.println("SHIFT: " + s.getDate() + " | " + s.getStartTime() + " - " + s.getEndTime() +
                    " | " + s.getCategory() + " | crew: " + s.getClosingCrew());
        }
        System.out.println("=== END TEST OCR RESULT ===");
    }
}
