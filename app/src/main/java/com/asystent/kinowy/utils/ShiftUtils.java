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

        List<GlobalShift> sorted = new ArrayList<>(overlapping);
        sorted.sort((a, b) -> {
            boolean aLead = isOfficeOrLeadershipShift(a);
            boolean bLead = isOfficeOrLeadershipShift(b);
            if (aLead != bLead) return aLead ? -1 : 1;
            String sa = a.getStartTime() != null ? a.getStartTime() : "";
            String sb2 = b.getStartTime() != null ? b.getStartTime() : "";
            return sa.compareTo(sb2);
        });

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            GlobalShift gs = sorted.get(i);
            boolean isLead = isOfficeOrLeadershipShift(gs);
            if (isLead && gs.getName() != null && !gs.getName().startsWith("⭐")) {
                sb.append("⭐ ");
            }
            sb.append(gs.getName())
              .append("  |  ")
              .append(gs.getStartTime()).append("–").append(gs.getEndTime())
              .append("  |  ")
              .append(gs.getCategory() != null ? gs.getCategory() : "?");
            if (i < sorted.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Formatuje listę dla widżetu — kompaktowy format z samymi imionami.
     * <p>
     * Przykład: {@code "Z Tobą: Kacper W., Ola G., Tomek R."}
     * <p>
     * Ogranicza do 5 imion i dodaje {@code "+N"} jeśli jest więcej.
     * Kadra kierownicza / biurowa (Manager, Deputy, TL) zawsze trafia na początek listy.
     *
     * @param overlapping lista z {@link #getOverlappingShifts}
     * @return skrócony string lub {@code null} jeśli lista pusta
     */
    public static String formatWidgetCrew(List<GlobalShift> overlapping) {
        if (overlapping == null || overlapping.isEmpty()) return null;

        List<GlobalShift> sorted = new ArrayList<>(overlapping);
        sorted.sort((a, b) -> {
            boolean aLead = isOfficeOrLeadershipShift(a);
            boolean bLead = isOfficeOrLeadershipShift(b);
            if (aLead != bLead) return aLead ? -1 : 1;
            String sa = a.getStartTime() != null ? a.getStartTime() : "";
            String sb2 = b.getStartTime() != null ? b.getStartTime() : "";
            return sa.compareTo(sb2);
        });

        int max = 5;
        StringBuilder sb = new StringBuilder("Z Tobą: ");
        int count = Math.min(sorted.size(), max);
        for (int i = 0; i < count; i++) {
            sb.append(sorted.get(i).getName());
            if (i < count - 1) sb.append(", ");
        }
        if (sorted.size() > max) {
            sb.append(" +").append(sorted.size() - max);
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
     * Sprawdza, czy zmiana {@link GlobalShift} należy do kadry kierowniczej / biurowej
     * (Manager, Deputy Manager, Team Leader / Kierownik).
     */
    public static boolean isOfficeOrLeadershipShift(GlobalShift gs) {
        if (gs == null) return false;
        String name = gs.getName() != null ? gs.getName().trim() : "";
        String cat = gs.getCategory() != null ? gs.getCategory().trim() : "";

        if (ReplacementFinder.isLeadershipCategory(name) || ReplacementFinder.isLeadershipCategory(cat)) {
            return true;
        }

        // Jeżeli kategoria to typowe stanowisko pracownicze z Excela (BAR, OW, OBSŁUGA, SP, KASA, KAWIARNIA),
        // a w nazwie nie ma oznaczenia roli (TL/Manager/Deputy), to jest to zwykły pracownik.
        String catUpper = cat.toUpperCase(java.util.Locale.ROOT);
        if (catUpper.contains("BAR") || catUpper.equals("OW") || catUpper.contains("OBSŁUG")
                || catUpper.contains("OBSLUG") || catUpper.equals("SP") || catUpper.contains("KASA")
                || catUpper.contains("KAWIARN") || catUpper.contains("MARATON")) {
            return false;
        }

        // Dla starszych wpisów z grafiku biurowego bez sufiksu w nazwie (gdzie kategoria to OPEN / ZAMEK / MIĘDZYZMIANA)
        String baseLower = name.replaceAll("\\s*\\(.*\\)$", "").replaceAll("\\s+#\\d+$", "").trim().toLowerCase(java.util.Locale.ROOT);
        if ("OPEN".equals(catUpper) || "ZAMEK".equals(catUpper) || "MIĘDZYZMIANA".equals(catUpper)) {
            if ("kasia".equals(baseLower) || "zosia".equals(baseLower) || "dominik".equals(baseLower)
                    || "alan".equals(baseLower) || "sara".equals(baseLower) || "kacper".equals(baseLower)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sprawdza, czy dany rekord {@link GlobalShift} należy do samego zalogowanego użytkownika
     * (uwzględniając sufiks roli np. "Kacper (TL)", ale odróżniając np. "Kacper (Deputy)" / "Kacper (Manager)").
     */
    public static boolean isSelfShift(GlobalShift gs, String myName, String myStart, String myEnd) {
        if (gs == null || gs.getName() == null) return false;
        String gName = gs.getName().trim().toLowerCase(java.util.Locale.ROOT);
        String gCat = gs.getCategory() != null ? gs.getCategory().trim().toLowerCase(java.util.Locale.ROOT) : "";
        String uName = (myName != null && !myName.trim().isEmpty())
                ? myName.trim().toLowerCase(java.util.Locale.ROOT)
                : "";
        if (uName.isEmpty()) return false;

        // Jeśli użytkownik nie ma w nazwie "deputy"/"manager", a globalShift ma "(deputy)" lub "(manager)"
        // (lub kategorię manager/deputy/kierownik), to jest to inna osoba o tym samym imieniu (np. Kacper Deputy vs Kacper TL)
        boolean userIsDeputyOrMgr = uName.contains("deputy") || uName.contains("manager") || uName.contains("kierownik");
        boolean gsIsDeputyOrMgr = gName.contains("deputy") || gName.contains("manager") || gName.contains("kierownik")
                || gCat.contains("deputy") || gCat.contains("manager") || gCat.contains("kierownik");
        if (!userIsDeputyOrMgr && gsIsDeputyOrMgr) {
            return false;
        }

        if (gName.equals(uName) || gName.equals(uName + " (tl)")) return true;
        String firstWord = uName.split("\\s+")[0];
        if (gName.equals(firstWord) || gName.equals(firstWord + " (tl)")) return true;

        if (gName.startsWith(firstWord + " ") || uName.startsWith(gName + " ")) {
            // Nie traktuj innego pracownika z innym nazwiskiem jako siebie (np. "Kacper Kowalski" vs "Kacper")
            if (gName.equals(firstWord + " w") || gName.equals(firstWord + " w.")
                    || gName.contains("wernerowicz") || gName.contains("(tl)")) {
                return true;
            }
            if (myStart != null && myStart.equals(gs.getStartTime())
                    && myEnd != null && myEnd.equals(gs.getEndTime())
                    && gName.contains("#")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Buduje listę współpracowników wyświetlanych na kafelku zmiany użytkownika.
     * Zwraca WYŁĄCZNIE osoby z kadry kierowniczej / biura (Menago / Deputy / TL),
     * których zmiana realnie nakłada się czasowo ze zmianą użytkownika.
     * Pełna obsada (wraz ze wszystkimi pracownikami z Excela) jest widoczna po kliknięciu w szczegóły zmiany.
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
            if (!isOfficeOrLeadershipShift(gs)) continue;

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
