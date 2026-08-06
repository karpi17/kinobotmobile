package com.asystent.kinowy.utils;

/**
 * Czysto logiczny moduł kalkulatora wynagrodzenia pracownika oraz analizy bilansowej.
 * Wyizolowany od UI zgodnie ze wzorcami architektury Android i wymaganiami stabilizacyjnymi Kinobota.
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
     * Wylicza sumaryczną ostateczną wycenę gotówkową z uwzględnieniem strat (odliczeń) oraz otrzymanych napiwków.
     */
    public static double calculateNetWithDeductions(double baseSalary, double totalLosses, double totalTips) {
        double afterLosses = Math.max(0.0, baseSalary - Math.max(0.0, totalLosses));
        return afterLosses + Math.max(0.0, totalTips);
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
            return String.format(java.util.Locale.getDefault(), "Nadpłata na Twoją korzyść: +%.2f zł", diff);
        } else {
            return String.format(java.util.Locale.getDefault(), "Brakuje z wyliczenia: %.2f zł", diff);
        }
    }
}
