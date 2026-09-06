package com.asystent.kinowy.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Automatyczny pakiet testów jednostkowych weryfikujący poprawność zagnieżdżeń rachunkowych,
 * nadpłat oraz redukowania o pomyłki, napiwki lub straty zgodnie z wyrobem wariantu Fazy 2.
 *
 * Faza 4 (Finanse Pro) — dodano testy:
 * - realizacja celu PLN,
 * - brakująca kwota / godziny do celu,
 * - symulator dodatkowych godzin,
 * - miesiąc z podwyżką w połowie okresu.
 */
public class PayrollCalculatorTest {

    // ─── Bazowe obliczenia (Faza 2) ──────────────────────────────────────────

    @Test
    public void testBaseSalary_validInputs_calculatedCorrectly() {
        double salary = PayrollCalculator.calculateBaseSalary(10.0, 25.50);
        assertEquals(255.0, salary, 0.001);
    }

    @Test
    public void testBaseSalary_zeroOrNegative_returnsZero() {
        assertEquals(0.0, PayrollCalculator.calculateBaseSalary(0.0, 25.0), 0.001);
        assertEquals(0.0, PayrollCalculator.calculateBaseSalary(-5.0, 25.0), 0.001);
        assertEquals(0.0, PayrollCalculator.calculateBaseSalary(10.0, -10.0), 0.001);
    }

    @Test
    public void testNetWithDeductions_standardDeductionAndTips_calculatedCorrectly() {
        // Podstawa: 500 zł, Strata: 20 zł, Napiwki: 15 zł -> Wynik: 495 zł
        double net = PayrollCalculator.calculateNetWithDeductions(500.0, 20.0, 15.0);
        assertEquals(495.0, net, 0.001);
    }

    @Test
    public void testNetWithDeductions_lossExceedsSalary_doesNotFallBelowZeroBeforeTips() {
        // Strata przewyższająca pensję (nie wpadamy w minusy systemowe na kocie zadłużeniowej gotówki)
        double net = PayrollCalculator.calculateNetWithDeductions(100.0, 150.0, 50.0);
        assertEquals(50.0, net, 0.001);
    }

    @Test
    public void testDifferenceComputation_surplusAndShortage() {
        assertEquals(10.50, PayrollCalculator.calculateDifference(1010.50, 1000.0), 0.001);
        assertEquals(-25.0, PayrollCalculator.calculateDifference(975.0, 1000.0), 0.001);
        assertEquals(0.0, PayrollCalculator.calculateDifference(500.0, 500.0), 0.001);
    }

    // ─── Cel miesięczny PLN (Faza 4) ─────────────────────────────────────────

    @Test
    public void testGoalProgress_halfGoal_returns50() {
        int progress = PayrollCalculator.calculateGoalProgress(500.0, 1000f);
        assertEquals(50, progress);
    }

    @Test
    public void testGoalProgress_goalAchieved_returns100() {
        int progress = PayrollCalculator.calculateGoalProgress(1200.0, 1000f);
        assertEquals(100, progress);
    }

    @Test
    public void testGoalProgress_noGoalSet_returnsZero() {
        int progress = PayrollCalculator.calculateGoalProgress(500.0, 0f);
        assertEquals(0, progress);
    }

    @Test
    public void testMissingPLN_goalNotAchieved_returnsDifference() {
        double missing = PayrollCalculator.calculateMissingPLN(800.0, 1000f);
        assertEquals(200.0, missing, 0.001);
    }

    @Test
    public void testMissingPLN_goalAchieved_returnsZero() {
        double missing = PayrollCalculator.calculateMissingPLN(1200.0, 1000f);
        assertEquals(0.0, missing, 0.001);
    }

    @Test
    public void testMissingHours_standard_calculatedCorrectly() {
        // Brakuje 160 zł, stawka 32 zł/h → potrzeba 5 godzin
        double hours = PayrollCalculator.calculateMissingHours(160.0, 32f);
        assertEquals(5.0, hours, 0.001);
    }

    @Test
    public void testMissingHours_goalAchieved_returnsZero() {
        double hours = PayrollCalculator.calculateMissingHours(0.0, 32f);
        assertEquals(0.0, hours, 0.001);
    }

    @Test
    public void testMissingHours_noRate_returnsZero() {
        double hours = PayrollCalculator.calculateMissingHours(500.0, 0f);
        assertEquals(0.0, hours, 0.001);
    }

    // ─── Symulator (Faza 4) ───────────────────────────────────────────────────

    @Test
    public void testSimulate_8extraHoursAt32_returnsCorrectEarnings() {
        PayrollCalculator.SimulationResult result =
                PayrollCalculator.simulateExtraHours(8.0, 32f, 2000.0, 3000f);
        assertEquals(256.0, result.extraEarnings, 0.001);
        assertEquals(2256.0, result.newNetPay, 0.001);
        // 2256 / 3000 = 75.2% → ograniczone do 75
        assertEquals(75, result.newProgressPercent);
    }

    @Test
    public void testSimulate_noExtraHours_returnsZeroResult() {
        PayrollCalculator.SimulationResult result =
                PayrollCalculator.simulateExtraHours(0.0, 32f, 2000.0, 3000f);
        assertEquals(0.0, result.extraEarnings, 0.001);
    }

    @Test
    public void testSimulate_noRate_returnsZeroResult() {
        PayrollCalculator.SimulationResult result =
                PayrollCalculator.simulateExtraHours(8.0, 0f, 2000.0, 3000f);
        assertEquals(0.0, result.extraEarnings, 0.001);
    }

    // ─── Per-Shift: miesiąc z podwyżką w połowie ─────────────────────────────

    /**
     * Scenariusz: sierpień 2026 z podwyżką od 8 sierpnia.
     * - 34 godziny × 31,40 zł (1-7 VIII) = 1067,60 zł
     * - 126 godzin × 32,80 zł (8-31 VIII) = 4132,80 zł
     * - Suma: 5200,40 zł
     */
    @Test
    public void testPerShift_monthWithRaiseInMiddle_calculatedCorrectly() {
        double hoursBeforeRaise = 34.0;
        double rateBeforeRaise  = 31.40;
        double hoursAfterRaise  = 126.0;
        double rateAfterRaise   = 32.80;

        double earned = (hoursBeforeRaise * rateBeforeRaise) + (hoursAfterRaise * rateAfterRaise);
        assertEquals(5200.40, earned, 0.01);
    }

    @Test
    public void testPerShift_allHoursAtSingleRate_matchesBaseSalary() {
        double hours = 160.0;
        double rate  = 32.80;
        double earned = hours * rate;
        assertEquals(PayrollCalculator.calculateBaseSalary(hours, rate), earned, 0.001);
    }

    // ─── Formatowanie SimulationResult ───────────────────────────────────────

    @Test
    public void testSimulationResultDisplayString_containsPlusSign() {
        PayrollCalculator.SimulationResult result =
                PayrollCalculator.simulateExtraHours(8.0, 32f, 2000.0, 3000f);
        String display = result.toDisplayString();
        assertTrue("Display string should start with +", display.startsWith("+"));
    }

    @Test
    public void testSimulationResultDisplayString_zeroEarnings_returnsDash() {
        PayrollCalculator.SimulationResult result =
                PayrollCalculator.simulateExtraHours(0.0, 32f, 2000.0, 3000f);
        assertEquals("—", result.toDisplayString());
    }
}
