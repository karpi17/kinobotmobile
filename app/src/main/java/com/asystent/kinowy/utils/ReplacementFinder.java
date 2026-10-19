package com.asystent.kinowy.utils;

import com.asystent.kinowy.models.BhpAlert;
import com.asystent.kinowy.models.GlobalShift;
import com.asystent.kinowy.models.Shift;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Wyszukiwarka kandydatów na zastępstwo (R5).
 * Analizuje globalny grafik dla dni D-1, D oraz D+1 i wskazuje pracowników,
 * którzy mają wolne w dniu D oraz nie złamią zasady 11h odpoczynku dobowego BHP.
 */
public class ReplacementFinder {

    public static class Candidate {
        private final String name;
        private final String note;

        public Candidate(String name, String note) {
            this.name = name;
            this.note = note;
        }

        public String getName() {
            return name;
        }

        public String getNote() {
            return note;
        }

        @Override
        public String toString() {
            return note != null && !note.isEmpty() ? name + " (" + note + ")" : name;
        }
    }

    /**
     * Znajduje pracowników mogących wziąć zmianę w dniu {@code targetDateStr} ({@code startTime}–{@code endTime})
     * bez naruszenia 11h odpoczynku BHP.
     */
    public static List<Candidate> findCandidates(
            String targetDateStr,
            String startTime,
            String endTime,
            String currentUserName,
            List<String> knownNames,
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

        String effStart = (startTime != null && !startTime.trim().isEmpty()) ? startTime.trim() : "09:00";
        String effEnd = (endTime != null && !endTime.trim().isEmpty()) ? endTime.trim() : "17:00";
        String prevDateStr = targetDate.minusDays(1).toString();
        String nextDateStr = targetDate.plusDays(1).toString();
        String trimmedUser = currentUserName != null ? currentUserName.trim() : "";

        // Zbierz unikalne imiona wszystkich znanych pracowników
        Set<String> allNames = new LinkedHashSet<>();
        if (knownNames != null) {
            for (String n : knownNames) {
                if (n != null && !n.trim().isEmpty()) allNames.add(n.trim());
            }
        }
        if (allGlobalShifts != null) {
            for (GlobalShift gs : allGlobalShifts) {
                if (gs != null && !gs.isDeleted() && gs.getName() != null && !gs.getName().trim().isEmpty()) {
                    allNames.add(gs.getName().trim());
                }
            }
        }

        List<Candidate> result = new ArrayList<>();
        Shift proposedShift = new Shift(targetDateStr, effStart, effEnd, "Zastępstwo", true, false, "BAR");

        for (String person : allNames) {
            if (isSameUser(person, trimmedUser)) continue;
            if ("Użytkownik".equalsIgnoreCase(person)) continue;

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
                contextNotes.add("wczoraj: " + prevGs.getStartTime() + "-" + prevGs.getEndTime());
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
                contextNotes.add("jutro: " + nextGs.getStartTime() + "-" + nextGs.getEndTime());
            }

            if (clopenViolation) continue;

            String note = contextNotes.isEmpty()
                    ? "pełne wolne (D-1..D+1)"
                    : String.join(", ", contextNotes);
            result.add(new Candidate(person, note));
        }

        result.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return result;
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
        String catInfo = (category != null && !category.isEmpty() && !"UNKNOWN".equalsIgnoreCase(category))
                ? " [" + category + "]"
                : "";

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
        return p.equals(u) || p.startsWith(u + " ") || u.startsWith(p + " ");
    }
}
