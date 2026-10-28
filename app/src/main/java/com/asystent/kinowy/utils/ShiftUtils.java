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
    // Pomocnicze i klasyfikacja zmian (OPEN / ZAMEK / MIĘDZYZMIANA)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Wyznacza czytelny typ zmiany na podstawie godzin i kategorii:
     * OPEN, ZAMEK, MIĘDZYZMIANA lub WOLNE (z zachowaniem specyficznych np. BAR, KASA, INNE (TMS)).
     */
    public static String resolveShiftType(String startTime, String endTime, String rawCategory) {
        if (startTime == null || startTime.trim().isEmpty()
                || endTime == null || endTime.trim().isEmpty()) {
            return "WOLNE";
        }

        if (rawCategory != null && !rawCategory.trim().isEmpty()) {
            String u = rawCategory.trim().toUpperCase(java.util.Locale.ROOT);
            if ("WOLNE".equals(u)) return "WOLNE";
            if (u.contains("TMS") || u.contains("PROJEKCJ")) return "INNE (TMS)";
            if ("BAR".equals(u) || "BAR (ZAMEK)".equals(u) || "KASA".equals(u) || "KAWIARNIA".equals(u)) {
                return u;
            }
            if ("OPEN".equals(u) || "ZAMEK".equals(u) || "MIĘDZYZMIANA".equals(u)) {
                return u;
            }
        }

        try {
            int sMin = toMinutes(startTime.trim());
            int eMin = toMinutes(endTime.trim());
            int sHour = sMin / 60;
            int eHour = eMin / 60;

            boolean isClose = (eMin <= sMin) || (eHour <= 5) || (eHour >= 23)
                    || (rawCategory != null && rawCategory.toUpperCase(java.util.Locale.ROOT).contains("ZAMEK"));
            if (isClose) {
                return "ZAMEK";
            }
            if (sHour <= 9) {
                return "OPEN";
            }
            return "MIĘDZYZMIANA";
        } catch (Exception ignored) {
            return rawCategory != null && !rawCategory.trim().isEmpty() ? rawCategory.trim() : "OBSŁUGA";
        }
    }

    /**
     * Sprawdza, czy dany rekord {@link GlobalShift} należy do samego zalogowanego użytkownika
     * (uwzględniając sufiks roli np. "Kacper (TL)", ale odróżniając np. "Kacper (Deputy)" o innych godzinach).
     */
    public static boolean isSelfShift(GlobalShift gs, String myName, String myStart, String myEnd) {
        if (gs == null || gs.getName() == null) return false;
        String gName = gs.getName().trim().toLowerCase(java.util.Locale.ROOT);
        String uName = (myName != null && !myName.trim().isEmpty())
                ? myName.trim().toLowerCase(java.util.Locale.ROOT)
                : "";
        if (uName.isEmpty()) return false;

        if (gName.equals(uName) || gName.equals(uName + " (tl)")) return true;
        String firstWord = uName.split("\\s+")[0];
        if (gName.equals(firstWord) || gName.equals(firstWord + " (tl)")) return true;

        // Jeśli użytkownik nie ma w nazwie "deputy"/"manager", a globalShift ma "(deputy)" lub "(manager)",
        // to jest to inna osoba o tym samym imieniu (np. Kacper Deputy vs Kacper TL)
        boolean userIsDeputyOrMgr = uName.contains("deputy") || uName.contains("manager");
        boolean gsIsDeputyOrMgr = gName.contains("deputy") || gName.contains("manager");
        if (!userIsDeputyOrMgr && gsIsDeputyOrMgr) {
            return false;
        }

        if (gName.startsWith(firstWord + " ") || uName.startsWith(gName + " ")) {
            if (myStart != null && myStart.equals(gs.getStartTime())
                    && myEnd != null && myEnd.equals(gs.getEndTime())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Buduje listę współpracowników wyświetlanych na kafelku zmiany użytkownika.
     * Zwraca WYŁĄCZNIE osoby, których zmiana realnie nakłada się czasowo ze zmianą użytkownika.
     */
    public static String buildOverlappingCrewString(
            String myStart, String myEnd, String myCategory, String myName, List<GlobalShift> dailyShifts) {
        if (myStart == null || myStart.trim().isEmpty()
                || myEnd == null || myEnd.trim().isEmpty()
                || dailyShifts == null || dailyShifts.isEmpty()) {
            return "";
        }

        List<GlobalShift> overlapping = getOverlappingShifts(myStart, myEnd, dailyShifts);
        if (overlapping.isEmpty()) return "";

        String myType = resolveShiftType(myStart, myEnd, myCategory);
        List<String> crewLabels = new ArrayList<>();

        for (GlobalShift gs : overlapping) {
            if (gs == null || gs.isDeleted() || gs.getName() == null || gs.getName().trim().isEmpty()) continue;
            if (isSelfShift(gs, myName, myStart, myEnd)) continue;

            String personName = gs.getName().trim();
            String gsType = resolveShiftType(gs.getStartTime(), gs.getEndTime(), gs.getCategory());
            String label;
            if (gsType.equalsIgnoreCase(myType)) {
                label = personName;
            } else if ("MIĘDZYZMIANA".equals(gsType)
                    && gs.getStartTime() != null && gs.getStartTime().length() >= 2
                    && gs.getEndTime() != null && gs.getEndTime().length() >= 2) {
                label = personName + " [" + gs.getStartTime().substring(0, 2) + "-" + gs.getEndTime().substring(0, 2) + "]";
            } else {
                label = personName + " [" + gsType + "]";
            }

            if (!crewLabels.contains(label)) {
                crewLabels.add(label);
            }
        }

        return crewLabels.isEmpty() ? "" : String.join(", ", crewLabels);
    }

    /**
     * Konwertuje czas (HH:mm) na liczbę minut od północy.
     */
    public static int toMinutes(String time) {
        LocalTime lt = LocalTime.parse(time, TIME_FMT);
        return lt.getHour() * 60 + lt.getMinute();
    }
}
