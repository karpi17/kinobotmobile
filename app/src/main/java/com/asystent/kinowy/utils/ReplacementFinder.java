package com.asystent.kinowy.utils;

import com.asystent.kinowy.models.ActiveEmployee;
import com.asystent.kinowy.models.BhpAlert;
import com.asystent.kinowy.models.GlobalShift;
import com.asystent.kinowy.models.Shift;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Wyszukiwarka kandydatów na zastępstwo (R5 + v3.5.1).
 * <p>
 * Reguły biznesowe:
 * 1. Zwykły pracownik (BAR/OW) NIE MOŻE wziąć zmiany kierowniczej / Team Leader.
 *    Jeśli oddawana zmiana jest zmianą TL/Kierownik, szukamy WYŁĄCZNIE wśród osób
 *    posiadających uprawnienia kierownicze (rola TL/MANAGER lub historia zmian TL).
 * 2. Pracownicy oznaczeni jako nieaktywni lub niepracujący od ponad 45 dni
 *    są automatycznie odrzucani.
 * 3. Kandydat musi mieć wolne w dniu docelowym D oraz zachować bezpieczny odpoczynek
 *    dobowy BHP (≥11h) w stosunku do zmian w dniach D-1 i D+1 (wykluczenie clopenów).
 */
public class ReplacementFinder {

    public static class Candidate {
        private final String name;
        private final String note;
        private final boolean isLeadership;

        public Candidate(String name, String note) {
            this(name, note, false);
        }

        public Candidate(String name, String note, boolean isLeadership) {
            this.name = name;
            this.note = note;
            this.isLeadership = isLeadership;
        }

        public String getName() {
            return name;
        }

        public String getNote() {
            return note;
        }

        public boolean isLeadership() {
            return isLeadership;
        }

        @Override
        public String toString() {
            return note != null && !note.isEmpty() ? name + " (" + note + ")" : name;
        }
    }

    /**
     * Sprawdza, czy dana kategoria/stanowisko/nazwa wskazuje na rolę kierowniczą / Team Leader.
     */
    public static boolean isLeadershipCategory(String category) {
        if (category == null) return false;
        String c = category.trim().toLowerCase(Locale.ROOT);
        return c.contains("tl")
                || c.contains("leader")
                || c.contains("leoder")
                || c.contains("kierownik")
                || c.contains("manager")
                || c.contains("menadżer")
                || c.contains("deputy")
                || c.contains("z-ca")
                || c.contains("dyrektor");
    }

    /**
     * Przeciążenie kompatybilności wstecznej.
     */
    public static List<Candidate> findCandidates(
            String targetDateStr,
            String startTime,
            String endTime,
            String currentUserName,
            List<String> knownNames,
            List<GlobalShift> allGlobalShifts) {

        List<ActiveEmployee> activeList = null;
        if (knownNames != null) {
            activeList = new ArrayList<>();
            for (String n : knownNames) {
                if (n != null && !n.trim().isEmpty()) {
                    activeList.add(new ActiveEmployee(n.trim(), true, ActiveEmployee.ROLE_EMPLOYEE, null));
                }
            }
        }
        return findCandidates(targetDateStr, startTime, endTime, "UNKNOWN", currentUserName, activeList, allGlobalShifts);
    }

    /**
     * Główny algorytm wyszukiwania zastępstw z kwalifikacją ról i statusem aktywności.
     */
    public static List<Candidate> findCandidates(
            String targetDateStr,
            String startTime,
            String endTime,
            String category,
            String currentUserName,
            List<ActiveEmployee> activeEmployeesList,
            List<GlobalShift> allGlobalShifts) {

        if (targetDateStr == null || targetDateStr.trim().isEmpty()) {
            return Collections.emptyList();
        }

        LocalDate targetDate;
        try {
            targetDate = LocalDate.parse(targetDateStr.trim());
        } catch (Exception e) {
            return Collections.emptyList();
        }

        boolean targetIsLeadership = isLeadershipCategory(category);
        String effStart = (startTime != null && !startTime.trim().isEmpty()) ? startTime.trim() : "09:00";
        String effEnd = (endTime != null && !endTime.trim().isEmpty()) ? endTime.trim() : "17:00";
        String prevDateStr = targetDate.minusDays(1).toString();
        String nextDateStr = targetDate.plusDays(1).toString();
        String trimmedUser = currentUserName != null ? currentUserName.trim() : "";

        // Granica świeżości grafiku: kandydat musi mieć zmianę w okresie ±45 dni od targetDate
        LocalDate minRecentDate = targetDate.minusDays(45);
        LocalDate maxRecentDate = targetDate.plusDays(45);

        // Mapa pracowników ze słownika
        Map<String, ActiveEmployee> employeeMap = new HashMap<>();
        if (activeEmployeesList != null) {
            for (ActiveEmployee ae : activeEmployeesList) {
                if (ae != null && ae.getName() != null) {
                    employeeMap.put(ae.getName().trim().toLowerCase(Locale.ROOT), ae);
                    if (ae.isLeadership() && isSameUser(ae.getName(), trimmedUser)) {
                        targetIsLeadership = true;
                    }
                }
            }
        }

        // Analiza historii zmian w global_shifts (ostatnia data + role TL)
        Map<String, LocalDate> lastShiftDatePerPerson = new HashMap<>();
        Map<String, LocalDate> firstShiftDatePerPerson = new HashMap<>();
        Set<String> historicalLeaders = new LinkedHashSet<>();
        Set<String> allKnownPersons = new LinkedHashSet<>();

        if (allGlobalShifts != null) {
            for (GlobalShift gs : allGlobalShifts) {
                if (gs == null || gs.isDeleted() || gs.getName() == null) continue;
                String personName = gs.getName().trim();
                if (personName.isEmpty()) continue;
                allKnownPersons.add(personName);

                if (isLeadershipCategory(gs.getCategory()) || isLeadershipCategory(personName)) {
                    historicalLeaders.add(personName.toLowerCase(Locale.ROOT));
                    if (isSameUser(personName, trimmedUser)) {
                        targetIsLeadership = true;
                    }
                }

                if (gs.getDate() != null && gs.getDate().length() >= 10) {
                    try {
                        LocalDate d = LocalDate.parse(gs.getDate());
                        String key = personName.toLowerCase(Locale.ROOT);
                        LocalDate prevMax = lastShiftDatePerPerson.get(key);
                        if (prevMax == null || d.isAfter(prevMax)) {
                            lastShiftDatePerPerson.put(key, d);
                        }
                        LocalDate prevMin = firstShiftDatePerPerson.get(key);
                        if (prevMin == null || d.isBefore(prevMin)) {
                            firstShiftDatePerPerson.put(key, d);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Jeśli activeEmployeesList zawiera osoby, dodaj je również do puli
        if (activeEmployeesList != null) {
            for (ActiveEmployee ae : activeEmployeesList) {
                if (ae.getName() != null && !ae.getName().trim().isEmpty()) {
                    allKnownPersons.add(ae.getName().trim());
                }
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        Shift proposedShift = new Shift(targetDateStr, effStart, effEnd, "Zastępstwo", true, false,
                category != null ? category : "BAR");

        for (String person : allKnownPersons) {
            if (isSameUser(person, trimmedUser)) continue;
            if ("Użytkownik".equalsIgnoreCase(person) || "Ty".equalsIgnoreCase(person)) continue;

            String personKey = person.toLowerCase(Locale.ROOT);
            ActiveEmployee dictEntry = employeeMap.get(personKey);

            // 1. Filtr aktywności: odrzuć jeśli oznaczony w słowniku jako nieaktywny
            if (dictEntry != null && !dictEntry.isActive()) {
                continue;
            }

            // 2. Filtr świeżości (duchy z przeszłości):
            // Jeśli pracownik ma historię w bazie, ale jego zmiany kończą się przed minRecentDate
            // LUB zaczynają dopiero po maxRecentDate — nie pracował w tym okresie
            LocalDate personLast = lastShiftDatePerPerson.get(personKey);
            LocalDate personFirst = firstShiftDatePerPerson.get(personKey);
            if (personLast != null && personLast.isBefore(minRecentDate)) {
                // Pracował dawno temu, brak zmian w oknie 45 dni
                continue;
            }
            if (personFirst != null && personFirst.isAfter(maxRecentDate)) {
                // Nowy pracownik zatrudniony długo po dacie zmiany
                continue;
            }

            // 3. Weryfikacja kwalifikacji / roli:
            boolean isPersonLeader = historicalLeaders.contains(personKey)
                    || isLeadershipCategory(person)
                    || (dictEntry != null && dictEntry.isLeadership());

            // REGULA: Jeśli zmiana jest kierownicza/TL, zwykły pracownik NIE MOŻE jej wziąć!
            if (targetIsLeadership && !isPersonLeader) {
                continue;
            }

            // 4. Sprawdzenie dostępności w dniu D oraz kolizji BHP
            boolean worksOnTargetDay = false;
            List<GlobalShift> prevDayShifts = new ArrayList<>();
            List<GlobalShift> nextDayShifts = new ArrayList<>();

            if (allGlobalShifts != null) {
                for (GlobalShift gs : allGlobalShifts) {
                    if (gs == null || gs.isDeleted() || gs.getName() == null) continue;
                    if (!gs.getName().trim().equalsIgnoreCase(person)) continue;
                    if (gs.getStartTime() == null || gs.getStartTime().trim().isEmpty()
                            || gs.getEndTime() == null || gs.getEndTime().trim().isEmpty()) {
                        continue;
                    }

                    String d = gs.getDate();
                    if (targetDateStr.equals(d)) {
                        worksOnTargetDay = true;
                        break;
                    } else if (prevDateStr.equals(d)) {
                        prevDayShifts.add(gs);
                    } else if (nextDateStr.equals(d)) {
                        nextDayShifts.add(gs);
                    }
                }
            }

            if (worksOnTargetDay) continue;

            // Sprawdź naruszenia BHP (Clopen < 11h) z dniem poprzednim i następnym
            boolean clopenViolation = false;
            List<String> contextNotes = new ArrayList<>();

            for (GlobalShift prevGs : prevDayShifts) {
                Shift prevShift = new Shift(prevGs.getDate(), prevGs.getStartTime(), prevGs.getEndTime(),
                        "", true, false, prevGs.getCategory() != null ? prevGs.getCategory() : "BAR");
                List<BhpAlert> alerts = BhpGuard.checkSchedule(Arrays.asList(prevShift, proposedShift));
                for (BhpAlert a : alerts) {
                    if ("CLOPEN".equals(a.getType())) {
                        clopenViolation = true;
                        break;
                    }
                }
                contextNotes.add("wczoraj: " + prevGs.getStartTime() + "–" + prevGs.getEndTime());
            }

            if (clopenViolation) continue;

            for (GlobalShift nextGs : nextDayShifts) {
                Shift nextShift = new Shift(nextGs.getDate(), nextGs.getStartTime(), nextGs.getEndTime(),
                        "", true, false, nextGs.getCategory() != null ? nextGs.getCategory() : "BAR");
                List<BhpAlert> alerts = BhpGuard.checkSchedule(Arrays.asList(proposedShift, nextShift));
                for (BhpAlert a : alerts) {
                    if ("CLOPEN".equals(a.getType())) {
                        clopenViolation = true;
                        break;
                    }
                }
                contextNotes.add("jutro: " + nextGs.getStartTime() + "–" + nextGs.getEndTime());
            }

            if (clopenViolation) continue;

            String baseNote = contextNotes.isEmpty()
                    ? "pełne wolne (D-1..D+1)"
                    : String.join(", ", contextNotes);

            String finalNote;
            if (targetIsLeadership) {
                finalNote = "⭐ Team Leader — " + baseNote;
            } else if (isPersonLeader) {
                finalNote = baseNote + " (Team Leader)";
            } else {
                finalNote = baseNote;
            }

            candidates.add(new Candidate(person, finalNote, isPersonLeader));
        }

        // Sortowanie: dla zwykłych zmian preferujemy zwykłych pracowników, dla TL — alfabetycznie
        final boolean finalTargetIsLeadership = targetIsLeadership;
        candidates.sort((a, b) -> {
            if (!finalTargetIsLeadership) {
                // Regular employees first, leaders second
                if (a.isLeadership() != b.isLeadership()) {
                    return a.isLeadership() ? 1 : -1;
                }
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });

        return candidates;
    }

    /**
     * Buduje gotową wiadomość na Messengera z zapytaniem o zastępstwo.
     */
    public static String buildMessengerMessage(String dateStr, String startTime, String endTime, String category) {
        String formattedDate = dateStr != null ? dateStr : "";
        try {
            if (dateStr != null && dateStr.length() >= 10) {
                LocalDate d = LocalDate.parse(dateStr.trim());
                formattedDate = d.format(DateTimeFormatter.ofPattern("dd.MM (EEEE)", new Locale("pl", "PL")));
            }
        } catch (Exception ignored) {}

        String hours = (startTime != null && !startTime.isEmpty() && endTime != null && !endTime.isEmpty())
                ? startTime + "–" + endTime
                : "do ustalenia";

        boolean isTL = isLeadershipCategory(category);
        String catInfo = (category != null && !category.isEmpty() && !"UNKNOWN".equalsIgnoreCase(category))
                ? " [" + category + "]"
                : "";

        if (isTL) {
            return "Hej! 👋 Szukam zastępstwa na moją zmianę Team Leader / Kierowniczą:\n"
                    + "📅 " + formattedDate + "\n"
                    + "⏰ " + hours + catInfo + "\n"
                    + "Czy któryś z Team Leaderów / Kierowników mógłby mnie zastąpić? Dajcie znać! 🙏";
        }

        return "Hej! 👋 Szukam osoby chętnej na wzięcie mojej zmiany:\n"
                + "📅 " + formattedDate + "\n"
                + "⏰ " + hours + catInfo + "\n"
                + "Dajcie znać, jeśli ktoś może przygarnąć! 🙏";
    }

    private static boolean isSameUser(String person, String currentUserName) {
        if (person == null || currentUserName == null) return false;
        String p = person.trim().toLowerCase(Locale.ROOT);
        String u = currentUserName.trim().toLowerCase(Locale.ROOT);
        if (p.isEmpty() || u.isEmpty()) return false;
        if (p.equals(u)) return true;
        boolean userIsDeputyOrMgr = u.contains("deputy") || u.contains("manager");
        boolean personIsDeputyOrMgr = p.contains("deputy") || p.contains("manager");
        if (!userIsDeputyOrMgr && personIsDeputyOrMgr) {
            return false;
        }
        return p.startsWith(u + " ") || u.startsWith(p + " ");
    }
}
