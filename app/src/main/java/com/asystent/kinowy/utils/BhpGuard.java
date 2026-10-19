package com.asystent.kinowy.utils;

import com.asystent.kinowy.models.BhpAlert;
import com.asystent.kinowy.models.Shift;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BhpGuard {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Główna funkcja analizująca zmiany pracownika pod kątem naruszeń BHP.
     * Zwraca listę zidentyfikowanych alertów.
     */
    public static List<BhpAlert> checkSchedule(List<Shift> shifts) {
        List<BhpAlert> alerts = new ArrayList<>();
        if (shifts == null || shifts.isEmpty()) {
            return alerts;
        }

        // 1. Odfiltruj zmiany oddane (isReplacement), dni wolne ("WOLNE") oraz niepełne wpisy przed sortowaniem
        List<Shift> sortedShifts = new ArrayList<>();
        for (Shift s : shifts) {
            if (s == null || s.isReplacement()) continue;
            if ("WOLNE".equalsIgnoreCase(s.getCategory()) || "WOLNE".equalsIgnoreCase(s.getDescription())) continue;
            if (parseDate(s.getDate()) == null || parseTime(s.getStartTime()) == null || parseTime(s.getEndTime()) == null) {
                continue;
            }
            sortedShifts.add(s);
        }

        if (sortedShifts.isEmpty()) {
            return alerts;
        }

        // 2. Sortujemy zmiany chronologicznie z pełnym null-safety
        sortedShifts.sort(Comparator.comparing(Shift::getDate, Comparator.nullsLast(String::compareTo))
                .thenComparing(Shift::getStartTime, Comparator.nullsLast(String::compareTo)));

        int currentMarathonDays = 1;

        for (int i = 0; i < sortedShifts.size() - 1; i++) {
            Shift current = sortedShifts.get(i);
            Shift next = sortedShifts.get(i + 1);

            LocalDate currentDate = parseDate(current.getDate());
            LocalDate nextDate = parseDate(next.getDate());
            LocalTime currentStart = parseTime(current.getStartTime());
            LocalTime currentEnd = parseTime(current.getEndTime());
            LocalTime nextStart = parseTime(next.getStartTime());

            if (currentDate == null || nextDate == null || currentStart == null || currentEnd == null || nextStart == null) {
                continue;
            }

            // Clopen - analiza przerwy między zmianami.
            long daysBetween = ChronoUnit.DAYS.between(currentDate, nextDate);

            if (daysBetween == 0 || daysBetween == 1) {
                // Jeśli zmiana kończy się po północy (currentEnd < currentStart, np. 17:00-01:00),
                // jej realny koniec wypada w dniu currentDate + 1 niezależnie od tego czy daysBetween == 0 czy 1.
                LocalDateTime endTimeFull;
                if (currentEnd.isBefore(currentStart) || (current.isClosingShift() && currentEnd.isBefore(LocalTime.of(6, 0)))) {
                    endTimeFull = currentDate.plusDays(1).atTime(currentEnd);
                } else {
                    endTimeFull = currentDate.atTime(currentEnd);
                }

                LocalDateTime nextStartFull = nextDate.atTime(nextStart);
                long hoursBetween = Duration.between(endTimeFull, nextStartFull).toHours();

                // BHP: Odpoczynek powinien wynosić minimum 11 godzin (Clopen)
                if (hoursBetween < 11 && hoursBetween >= 0) {
                    String alertId = "CLOPEN_" + current.getId() + "_" + next.getId();
                    String msg = "Przerwa przed " + next.getDate() + " (" + next.getStartTime() + ") wynosi tylko " + hoursBetween + "h (wymagane 11h).";
                    alerts.add(new BhpAlert(alertId, msg, "CLOPEN", next.getDate()));
                }
            }

            if (daysBetween == 1) {
                currentMarathonDays++;
                if (currentMarathonDays > 6) { // Powyżej 6 dni roboczych pod rząd
                    String alertId = "MARATHON_" + next.getDate();
                    String msg = "W " + next.getDate() + " będziesz pracować " + currentMarathonDays + ". dzień z rzędu.";
                    alerts.add(new BhpAlert(alertId, msg, "MARATHON", next.getDate()));
                }
            } else if (daysBetween > 1) {
                currentMarathonDays = 1; // Zresetowano przez dni wolne
            }
        }

        // 3. Tygodniowy czas pracy (max 48h) bazując na tygodniach ISO
        java.util.Map<String, Double> weeks = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> weekLastDate = new java.util.HashMap<>();
        for (Shift s : sortedShifts) {
            LocalDate d = parseDate(s.getDate());
            LocalTime start = parseTime(s.getStartTime());
            LocalTime end = parseTime(s.getEndTime());
            if (d != null && start != null && end != null) {
                long min = Duration.between(start, end).toMinutes();
                if (min < 0) min += 24 * 60;

                int week = d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                int year = d.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR);
                String key = year + "-W" + week;

                weeks.put(key, weeks.getOrDefault(key, 0.0) + (min / 60.0));
                weekLastDate.put(key, s.getDate());
            }
        }

        for (java.util.Map.Entry<String, Double> entry : weeks.entrySet()) {
            if (entry.getValue() > 48.0) {
                String msg = "W tygodniu " + entry.getKey() + " przekroczyłeś limit 48h pracy (" + String.format(java.util.Locale.US, "%.1f", entry.getValue()) + "h).";
                String alertDate = weekLastDate.getOrDefault(entry.getKey(), "");
                alerts.add(new BhpAlert("OVERTIME_" + entry.getKey(), msg, "OVERTIME", alertDate));
            }
        }

        return alerts;
    }

    private static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return null;
        try {
            return LocalTime.parse(timeStr, TIME_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }
}
