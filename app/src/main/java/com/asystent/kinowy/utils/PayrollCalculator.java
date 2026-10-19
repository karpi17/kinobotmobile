package com.asystent.kinowy.utils;

import java.util.Locale;

/**
 * Czysto logiczny moduł kalkulatora wynagrodzenia pracownika oraz analizy bilansowej.
 * Wyizolowany od UI zgodnie ze wzorcami architektury Android i wymaganiami stabilizacyjnymi Kinobota.
 *
 * v2 (Faza 4 Finanse Pro):
 * - Obliczenia Per-Shift (każda zmiana mnożona przez stawkę obowiązującą w jej dniu)
 * - Obsługa celu miesięcznego w PLN
 * - Symulacja dodatkowych zmian
 */
public final class PayrollCalculator {

    private PayrollCalculator() {
        // Klasa narzędziowa bez instancji
    }

    /**
     * Wylicza należne wynagrodzenie bazowe na podstawie przepracowanych godzin i stawki za godzinę.
     */
    public static double calculateBaseSalary(double hours, double hourlyRate) {
        if (hours <= 0 || hourlyRate <= 0) {
            return 0.0;
        }
        return hours * hourlyRate;
    }

    /**
     * Wylicza liczbę przepracowanych godzin dla zmiany (obsługuje zmiany nocne przechodzące przez północ).
     */
    public static double calculateShiftHours(String startTimeStr, String endTimeStr) {
        if (startTimeStr == null || endTimeStr == null) return 0.0;
        String s = startTimeStr.trim();
        String e = endTimeStr.trim();
        if (s.isEmpty() || e.isEmpty()) return 0.0;
        if (s.length() == 4 && s.charAt(1) == ':') s = "0" + s;
        if (e.length() == 4 && e.charAt(1) == ':') e = "0" + e;
        try {
            java.time.LocalTime start = java.time.LocalTime.parse(s);
            java.time.LocalTime end = java.time.LocalTime.parse(e);
            long mins = java.time.Duration.between(start, end).toMinutes();
            if (mins < 0) mins += 24 * 60;
            return mins / 60.0;
        } catch (Exception ex) {
            return 0.0;
        }
    }

    /**
     * Wylicza sumaryczną ostateczną wycenę gotówkową z uwzględnieniem strat (odliczeń) oraz otrzymanych napiwków.
     */
    public static double calculateNetWithDeductions(double baseSalary, double totalLosses, double totalTips) {
        double afterLosses = Math.max(0.0, baseSalary - Math.max(0.0, totalLosses));
        return afterLosses + Math.max(0.0, totalTips);
    }

    /**
     * Alias dla {@link #calculateNetWithDeductions(double, double, double)}.
     */
    public static double calculateNetPay(double baseSalary, double totalLosses, double totalTips) {
        return calculateNetWithDeductions(baseSalary, totalLosses, totalTips);
    }

    /**
     * Zwraca precyzyjną różnicę bilansową pomiędzy wpłatą przelewową z banku a wyliczoną kwotą w systemie.
     * Wartość dodatnia oznacza nadpłatę ze strony kina, wartość ujemna – niedobór próg zaniżenia.
     */
    public static double calculateDifference(double actualDeposit, double expectedSalary) {
        return actualDeposit - expectedSalary;
    }

    /**
     * Formatuje wynikową kwotę różnicy na czytelny opis z polskim wykrętem znaku.
     */
    public static String getDifferenceSummary(double actualDeposit, double expectedSalary) {
        double diff = calculateDifference(actualDeposit, expectedSalary);
        if (Math.abs(diff) < 0.01) {
            return "Wypłata idealna — 100% zgodności!";
        } else if (diff > 0) {
            return String.format(Locale.getDefault(), "Nadpłata na Twoją korzyść: +%.2f zł", diff);
        } else {
            return String.format(Locale.getDefault(), "Brakuje z wyliczenia: %.2f zł", diff);
        }
    }

    /**
     * Oblicza liczbę godzin przypadających na porę nocną (22:00–06:00) dla pojedynczej zmiany.
     * Obsługuje zmiany przechodzące przez północ (np. 17:00–01:00 -> 3.0h w porze nocnej).
     */
    public static double calculateNightHours(String startTimeStr, String endTimeStr) {
        if (startTimeStr == null || endTimeStr == null) return 0.0;
        String s = startTimeStr.trim();
        String e = endTimeStr.trim();
        if (s.isEmpty() || e.isEmpty()) return 0.0;
        if (s.length() == 4 && s.charAt(1) == ':') s = "0" + s;
        if (e.length() == 4 && e.charAt(1) == ':') e = "0" + e;
        try {
            java.time.LocalTime start = java.time.LocalTime.parse(s);
            java.time.LocalTime end = java.time.LocalTime.parse(e);
            return calculateNightHours(start, end);
        } catch (Exception ex) {
            return 0.0;
        }
    }

    /**
     * Oblicza liczbę godzin przypadających na porę nocną (22:00–06:00) pomiędzy start a end.
     */
    public static double calculateNightHours(java.time.LocalTime start, java.time.LocalTime end) {
        if (start == null || end == null) return 0.0;
        int startMin = start.getHour() * 60 + start.getMinute();
        int endMin = end.getHour() * 60 + end.getMinute();
        if (startMin == endMin) return 0.0;
        if (endMin < startMin) {
            endMin += 24 * 60;
        }

        // Przedziały nocne (22:00–06:00) w przestrzeni minut [0, 2880]:
        // 1) 00:00–06:00 dnia pierwszego: [0, 360]
        // 2) 22:00 dnia pierwszego – 06:00 dnia drugiego: [1320, 1800]
        // 3) 22:00–24:00 dnia drugiego: [2760, 2880]
        int[][] nightWindows = {
                {0, 6 * 60},
                {22 * 60, 30 * 60},
                {46 * 60, 48 * 60}
        };

        int nightMinutes = 0;
        for (int[] window : nightWindows) {
            int overlapStart = Math.max(startMin, window[0]);
            int overlapEnd = Math.min(endMin, window[1]);
            if (overlapEnd > overlapStart) {
                nightMinutes += (overlapEnd - overlapStart);
            }
        }
        return nightMinutes / 60.0;
    }

    // =========================================================================
    // FAZA 4: Cel Finansowy i Symulacje
    // =========================================================================

    /**
     * Oblicza procent realizacji celu miesięcznego (0–100+).
     *
     * @param netPay    aktualne wynagrodzenie netto (po stratach, z napiwkami)
     * @param goalPLN   cel miesięczny w PLN (> 0)
     * @return procent realizacji celu (może przekroczyć 100%)
     */
    public static int calculateGoalProgress(double netPay, float goalPLN) {
        if (goalPLN <= 0) return 0;
        return (int) Math.min(100, (netPay / goalPLN) * 100.0);
    }

    /**
     * Ile PLN brakuje do osiągnięcia celu miesięcznego.
     *
     * @param netPay  aktualne wynagrodzenie netto
     * @param goalPLN cel miesięczny w PLN
     * @return brakująca kwota (0 jeśli cel osiągnięty)
     */
    public static double calculateMissingPLN(double netPay, float goalPLN) {
        return Math.max(0.0, goalPLN - netPay);
    }

    /**
     * Ile godzin brakuje do osiągnięcia celu przy podanej stawce.
     *
     * @param missingPLN   kwota brakująca do celu
     * @param hourlyRate   aktualna stawka godzinowa
     * @return liczba godzin (0 jeśli cel osiągnięty lub brak stawki)
     */
    public static double calculateMissingHours(double missingPLN, float hourlyRate) {
        if (hourlyRate <= 0 || missingPLN <= 0) return 0.0;
        return missingPLN / hourlyRate;
    }

    /**
     * Symuluje efekt finansowy wzięcia dodatkowych godzin.
     *
     * @param extraHours  liczba dodatkowych godzin
     * @param hourlyRate  stawka godzinowa
     * @param netPay      aktualne wynagrodzenie netto
     * @param goalPLN     cel miesięczny w PLN
     * @return wynik symulacji jako tekst gotowy do wyświetlenia w UI
     */
    public static SimulationResult simulateExtraHours(double extraHours, float hourlyRate,
                                                       double netPay, float goalPLN) {
        if (hourlyRate <= 0 || extraHours <= 0) {
            return new SimulationResult(0, 0, 0);
        }

        double extraEarnings = extraHours * hourlyRate;
        double newNetPay = netPay + extraEarnings;
        int newProgressPercent = goalPLN > 0
                ? (int) Math.min(100, (newNetPay / goalPLN) * 100.0)
                : 0;

        return new SimulationResult(extraEarnings, newNetPay, newProgressPercent);
    }

    /**
     * Wynik symulacji dodatkowych godzin.
     */
    public static class SimulationResult {
        /** Dodatkowe zarobki z symulowanych godzin. */
        public final double extraEarnings;
        /** Nowe wynagrodzenie netto po doliczeniu symulowanych godzin. */
        public final double newNetPay;
        /** Nowy procent realizacji celu (0–100). */
        public final int newProgressPercent;

        SimulationResult(double extraEarnings, double newNetPay, int newProgressPercent) {
            this.extraEarnings = extraEarnings;
            this.newNetPay = newNetPay;
            this.newProgressPercent = newProgressPercent;
        }

        /**
         * Formatuje wynik symulacji do czytelnego stringa dla UI.
         * Przykład: "+224,80 zł (↑ do 78% celu)"
         */
        public String toDisplayString() {
            if (extraEarnings <= 0) return "—";
            return String.format(Locale.getDefault(),
                    "+%.2f zł (cel: %d%%)", extraEarnings, newProgressPercent);
        }
    }
}
