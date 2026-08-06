package com.asystent.kinowy.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Automatyczny pakiet testów jednostkowych weryfikujący poprawność zagnieżdżeń rachunkowych,
 * nadpłat oraz redukowania o pomyłki, napiwki lub straty zgodnie z wyrobem wariantu Fazy 2.
 */
public class PayrollCalculatorTest {

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
}
