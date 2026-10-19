package com.asystent.kinowy.utils;

import com.asystent.kinowy.models.GlobalShift;
import com.asystent.kinowy.models.Loss;
import com.asystent.kinowy.models.RateHistory;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.models.Tip;
import com.asystent.kinowy.models.YearlyStats;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatisticsCalculator {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public static YearlyStats calculate(String selectedYear, List<Shift> shifts, List<GlobalShift> globalShifts,
                                        List<Loss> losses, List<Tip> tips, List<RateHistory> rateHistories) {
        return calculate(selectedYear, shifts, globalShifts, losses, tips, rateHistories, "");
    }

    public static YearlyStats calculate(String selectedYear, List<Shift> shifts, List<GlobalShift> globalShifts,
                                        List<Loss> losses, List<Tip> tips, List<RateHistory> rateHistories,
                                        String userName) {

        List<Shift> yearShifts = new ArrayList<>();
        List<Loss> yearLosses = new ArrayList<>();
        List<Tip> yearTips = new ArrayList<>();

        // Filtrowanie po roku
        boolean isAllTime = selectedYear == null || selectedYear.isEmpty() || selectedYear.equalsIgnoreCase("all-time");

        if (shifts != null) {
            for (Shift s : shifts) {
                if (s != null && s.getDate() != null && (isAllTime || s.getDate().startsWith(selectedYear))) {
                    yearShifts.add(s);
                }
            }
        }
        if (losses != null) {
            for (Loss l : losses) {
                if (l != null && l.getDate() != null && (isAllTime || l.getDate().startsWith(selectedYear))) {
                    yearLosses.add(l);
                }
            }
        }
        if (tips != null) {
            for (Tip t : tips) {
                if (t != null && t.getDate() != null && (isAllTime || t.getDate().startsWith(selectedYear))) {
                    yearTips.add(t);
                }
            }
        }

        // Sortowanie historii stawek (od najnowszej do najstarszej) dla szybkiego dostępu
        List<RateHistory> ratesSortedDesc = new ArrayList<>();
        if (rateHistories != null) {
            for (RateHistory rh : rateHistories) {
                if (rh != null && rh.getActiveFrom() != null) {
                    ratesSortedDesc.add(rh);
                }
            }
            ratesSortedDesc.sort((a, b) -> b.getActiveFrom().compareTo(a.getActiveFrom()));
        }

        // Grupowanie globalnych zmian w strukturę szybkiego wyszukiwania (z pominięciem usuniętych)
        // Map<Date, Map<Category, EarliestStartTime>> oraz Map<Date, List<GlobalShift>>
        Map<String, Map<String, LocalTime>> earliestGlobalShifts = new HashMap<>();
        Map<String, List<GlobalShift>> globalShiftsByDate = new HashMap<>();
        if (globalShifts != null) {
            for (GlobalShift gs : globalShifts) {
                if (gs == null || gs.isDeleted()) continue;
                if (gs.getDate() != null && gs.getStartTime() != null) {
                    globalShiftsByDate.computeIfAbsent(gs.getDate(), k -> new ArrayList<>()).add(gs);
                    try {
                        LocalTime st = LocalTime.parse(gs.getStartTime(), TIME_FORMAT);
                        earliestGlobalShifts.putIfAbsent(gs.getDate(), new HashMap<>());
                        Map<String, LocalTime> catMap = earliestGlobalShifts.get(gs.getDate());
                        String cat = gs.getCategory() != null ? gs.getCategory() : "UNKNOWN";
                        if (!catMap.containsKey(cat) || st.isBefore(catMap.get(cat))) {
                            catMap.put(cat, st);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        double totalHours = 0;
        int closingShifts = 0;
        int openingShifts = 0;

        Map<String, Integer> categoryCounts = new HashMap<>();

        // Kasa wg miesiąca: Map<yyyy-MM, Double>
        Map<String, Double> monthlyNetPay = new HashMap<>();

        // Współpracownicy z GlobalShift (pokrywający się godzinowo w dni pracy użytkownika)
        Map<String, Integer> coworkerCounts = new HashMap<>();

        // Sortowanie wg daty
        List<Shift> sortedShifts = new ArrayList<>(yearShifts);
        sortedShifts.sort(Comparator.comparing(Shift::getDate, Comparator.nullsLast(String::compareTo)));

        for (Shift s : sortedShifts) {
            if (s.isReplacement()) continue;
            if ("WOLNE".equalsIgnoreCase(s.getCategory()) || "WOLNE".equalsIgnoreCase(s.getDescription())) continue;

            // 1. Zliczanie godzin — pomijamy dni wolne (h <= 0)
            double h = calculateHours(s.getStartTime(), s.getEndTime());
            if (h <= 0) continue;
            totalHours += h;

            // 2. Kategoria (tylko dla realnie przepracowanych zmian h > 0)
            String cat = s.getCategory() != null && !s.getCategory().trim().isEmpty() ? s.getCategory() : "UNKNOWN";
            categoryCounts.put(cat, categoryCounts.getOrDefault(cat, 0) + 1);

            // 3. Zamki i Openy
            if (s.isClosingShift()) {
                closingShifts++;
            } else {
                // Algorytm "Pierwsza zmiana na danym stanowisku"
                try {
                    LocalTime myStart = LocalTime.parse(s.getStartTime(), TIME_FORMAT);
                    boolean isOpen = true; // domyślnie true jeśli nie ma danych globalnych

                    if (earliestGlobalShifts.containsKey(s.getDate())) {
                        Map<String, LocalTime> catMap = earliestGlobalShifts.get(s.getDate());
                        if (catMap.containsKey(cat)) {
                            LocalTime earliestForCat = catMap.get(cat);
                            if (myStart.isAfter(earliestForCat)) {
                                isOpen = false;
                            }
                        }
                    }
                    if (isOpen) {
                        openingShifts++;
                    }
                } catch (Exception ignored) {}
            }

            // 4. Finanse: Miesięczne zarobki
            String month = s.getDate().length() >= 7 ? s.getDate().substring(0, 7) : "";
            if (!month.isEmpty()) {
                float rate = getRateForDate(s.getDate(), ratesSortedDesc);
                double gross = h * rate;
                monthlyNetPay.put(month, monthlyNetPay.getOrDefault(month, 0.0) + gross);
            }

            // 5. Współpracownicy (pokrywający się czasowo, bez usuniętych i bez samego użytkownika)
            List<GlobalShift> dailyGlobal = globalShiftsByDate.get(s.getDate());
            if (dailyGlobal != null && !dailyGlobal.isEmpty()) {
                List<GlobalShift> overlapping = ShiftUtils.getOverlappingShifts(
                        s.getStartTime(), s.getEndTime(), dailyGlobal);
                for (GlobalShift gs : overlapping) {
                    if (gs == null || gs.isDeleted() || gs.getName() == null) continue;
                    String coworkerName = gs.getName().trim();
                    if (coworkerName.isEmpty() || isSameUser(coworkerName, userName)) continue;
                    coworkerCounts.put(coworkerName, coworkerCounts.getOrDefault(coworkerName, 0) + 1);
                }
            }
        }

        // Odejmujemy straty od miesięcznych zarobków (z ograniczeniem od dołu do 0)
        for (Loss l : yearLosses) {
            String month = l.getDate().length() >= 7 ? l.getDate().substring(0, 7) : "";
            if (monthlyNetPay.containsKey(month)) {
                monthlyNetPay.put(month, Math.max(0.0, monthlyNetPay.get(month) - l.getAmount()));
            }
        }

        // Dodajemy napiwki do miesięcznych zarobków
        for (Tip t : yearTips) {
            String month = t.getDate().length() >= 7 ? t.getDate().substring(0, 7) : "";
            if (monthlyNetPay.containsKey(month)) {
                monthlyNetPay.put(month, monthlyNetPay.get(month) + t.getAmount());
            }
        }

        // Zliczanie najdłuższego maratonu (tylko realnie przepracowane dni h > 0)
        int longestMarathon = 0;
        int currentMarathon = 0;
        LocalDate lastDate = null;
        for (Shift s : sortedShifts) {
            if (s.isReplacement()) continue;
            if ("WOLNE".equalsIgnoreCase(s.getCategory()) || "WOLNE".equalsIgnoreCase(s.getDescription())) continue;
            if (calculateHours(s.getStartTime(), s.getEndTime()) <= 0) continue;
            try {
                LocalDate date = LocalDate.parse(s.getDate());
                if (lastDate == null) {
                    currentMarathon = 1;
                } else {
                    long diff = ChronoUnit.DAYS.between(lastDate, date);
                    if (diff == 1) {
                        currentMarathon++;
                    } else if (diff > 1) {
                        currentMarathon = 1;
                    }
                }
                if (currentMarathon > longestMarathon) {
                    longestMarathon = currentMarathon;
                }
                lastDate = date;
            } catch (Exception ignored) {}
        }

        // Top Współpracownik
        String topCoworker = "Brak danych";
        int topCoworkerCount = 0;
        for (Map.Entry<String, Integer> entry : coworkerCounts.entrySet()) {
            if (entry.getValue() > topCoworkerCount) {
                topCoworkerCount = entry.getValue();
                topCoworker = entry.getKey();
            }
        }
        if (topCoworkerCount > 0) {
            topCoworker = topCoworker + " (" + topCoworkerCount + " z. wspólnych)";
        }

        // Najczęstsza kategoria
        String topCategory = "Brak danych";
        int topCatCount = 0;
        for (Map.Entry<String, Integer> entry : categoryCounts.entrySet()) {
            if (entry.getValue() > topCatCount) {
                topCatCount = entry.getValue();
                topCategory = entry.getKey();
            }
        }

        // Najlepszy i najgorszy miesiąc
        String bestMonth = "Brak";
        double bestMonthAmount = 0.0;
        String worstMonth = "Brak";
        double worstMonthAmount = Double.MAX_VALUE;

        for (Map.Entry<String, Double> entry : monthlyNetPay.entrySet()) {
            if (entry.getValue() > bestMonthAmount) {
                bestMonthAmount = entry.getValue();
                bestMonth = entry.getKey();
            }
            if (entry.getValue() < worstMonthAmount) {
                worstMonthAmount = entry.getValue();
                worstMonth = entry.getKey();
            }
        }
        if (worstMonthAmount == Double.MAX_VALUE) worstMonthAmount = 0.0;

        return new YearlyStats(
            isAllTime ? "Cały czas" : selectedYear,
            totalHours,
            closingShifts,
            openingShifts,
            longestMarathon,
            topCoworker,
            topCategory,
            bestMonth,
            bestMonthAmount,
            worstMonth,
            worstMonthAmount
        );
    }

    private static boolean isSameUser(String gsName, String userName) {
        if (gsName == null) return false;
        String g = gsName.trim().toLowerCase(Locale.ROOT);
        if (g.equals("ty") || g.equals("użytkownik")) return true;
        if (userName == null || userName.trim().isEmpty()) return false;
        String u = userName.trim().toLowerCase(Locale.ROOT);
        return g.equals(u) || g.startsWith(u + " ") || g.startsWith(u + "(") || u.startsWith(g + " ");
    }
    
    private static float getRateForDate(String date, List<RateHistory> ratesSortedDesc) {
        if (ratesSortedDesc == null || ratesSortedDesc.isEmpty()) return 0f;
        for (RateHistory rh : ratesSortedDesc) {
            if (date.compareTo(rh.getActiveFrom()) >= 0) {
                return rh.getRate();
            }
        }
        return ratesSortedDesc.get(ratesSortedDesc.size() - 1).getRate();
    }
    
    private static double calculateHours(String startTime, String endTime) {
        if (startTime == null || endTime == null || startTime.isEmpty() || endTime.isEmpty()) {
            return 0.0;
        }
        try {
            LocalTime start = LocalTime.parse(startTime, TIME_FORMAT);
            LocalTime end = LocalTime.parse(endTime, TIME_FORMAT);
            long min = Duration.between(start, end).toMinutes();
            if (min < 0) min += 24 * 60; // Nocka
            return min / 60.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
