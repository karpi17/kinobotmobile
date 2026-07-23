package com.asystent.kinowy.utils;

import com.asystent.kinowy.models.GlobalShift;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Klasa narzędziowa do operacji na zmianach.
 * <p>
 * Zawiera logikę detekcji nakładania się przedziałów czasowych,
 * obsługę zmian nocnych (przechodzących przez północ) i formatowanie
 * wyników do wyświetlenia w UI.
 * <p>
 * Jedno miejsce — jedna logika (DRY).
 *
 * @see GlobalShift
 */
public final class ShiftUtils {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private ShiftUtils() {} // uniemożliwiamy instancjonowanie

    // ═══════════════════════════════════════════════════════════════════
    // Overlap — detekcja nakładających się zmian
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Zwraca listę {@link GlobalShift}, których czas pracy nakłada się
     * z podanym przedziałem {@code [myStart, myEnd]}.
     * <p>
     * <b>Obsługa zamków (nocnych zmian):</b> jeśli {@code endTime < startTime},
     * zakładamy przejście przez północ (np. 18:00→02:00) i dodajemy 24h do końca.
     *
     * @param myStart     godzina startu użytkownika (HH:mm)
     * @param myEnd       godzina końca użytkownika (HH:mm)
     * @param dailyShifts lista globalnych zmian z tego samego dnia
     * @return lista nakładających się {@link GlobalShift} (może być pusta, nigdy null)
     */
    public static List<GlobalShift> getOverlappingShifts(
            String myStart, String myEnd, List<GlobalShift> dailyShifts) {

        List<GlobalShift> result = new ArrayList<>();
        if (myStart == null || myStart.isEmpty()
                || myEnd == null || myEnd.isEmpty()
                || dailyShifts == null || dailyShifts.isEmpty()) {
            return result;
        }

        try {
            int myS = toMinutes(myStart);
            int myE = toMinutes(myEnd);
            if (myE <= myS) myE += 24 * 60; // zmiana nocna użytkownika

            for (GlobalShift gs : dailyShifts) {
                if (gs.getStartTime() == null || gs.getStartTime().isEmpty()) continue;
                if (gs.getEndTime() == null || gs.getEndTime().isEmpty()) continue;

                int gsS = toMinutes(gs.getStartTime());
                int gsE = toMinutes(gs.getEndTime());
                if (gsE <= gsS) gsE += 24 * 60; // nocka współpracownika

                // Standardowe porównanie: s1 < e2 AND s2 < e1
                if (myS < gsE && gsS < myE) {
                    result.add(gs);
                    continue;
                }

                // Fix: wczesno-poranne zmiany (01:00-05:00) zapisane w bazie jako ten sam dzień
                // co nocka poprzedniego dnia (22:00-02:00). Przesuwamy taki rekord o +24h
                // i sprawdzamy nakładanie ponownie.
                if (gsS < 6 * 60) { // zmiana zaczyna się przed 06:00
                    int gsSShifted = gsS + 24 * 60;
                    int gsEShifted = gsE + 24 * 60;
                    if (myS < gsEShifted && gsSShifted < myE) {
                        result.add(gs);
                    }
                }
            }
        } catch (Exception ignored) {
            // Parsowanie czasu nie powiodło się
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Formatowanie — do wyświetlenia w UI
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Formatuje listę nakładających się zmian do wieloliniowego stringa
     * w formacie: {@code Imię  |  HH:mm–HH:mm  |  Stanowisko}.
     *
     * @param overlapping lista z {@link #getOverlappingShifts}
     * @return sformatowany string lub {@code null} jeśli lista pusta
     */
    public static String formatOverlappingShifts(List<GlobalShift> overlapping) {
        if (overlapping == null || overlapping.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < overlapping.size(); i++) {
            GlobalShift gs = overlapping.get(i);
            sb.append(gs.getName())
              .append("  |  ")
              .append(gs.getStartTime()).append("–").append(gs.getEndTime())
              .append("  |  ")
              .append(gs.getCategory() != null ? gs.getCategory() : "?");
            if (i < overlapping.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Formatuje listę dla widżetu — kompaktowy format z samymi imionami.
     * <p>
     * Przykład: {@code "Z Tobą: Kacper W., Ola G., Tomek R."}
     * <p>
     * Ogranicza do 5 imion i dodaje {@code "+N"} jeśli jest więcej.
     *
     * @param overlapping lista z {@link #getOverlappingShifts}
     * @return skrócony string lub {@code null} jeśli lista pusta
     */
    public static String formatWidgetCrew(List<GlobalShift> overlapping) {
        if (overlapping == null || overlapping.isEmpty()) return null;

        int max = 5;
        StringBuilder sb = new StringBuilder("Z Tobą: ");
        int count = Math.min(overlapping.size(), max);
        for (int i = 0; i < count; i++) {
            sb.append(overlapping.get(i).getName());
            if (i < count - 1) sb.append(", ");
        }
        if (overlapping.size() > max) {
            sb.append(" +").append(overlapping.size() - max);
        }
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Pomocnicze
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Konwertuje czas (HH:mm) na liczbę minut od północy.
     */
    public static int toMinutes(String time) {
        LocalTime lt = LocalTime.parse(time, TIME_FMT);
        return lt.getHour() * 60 + lt.getMinute();
    }
}
