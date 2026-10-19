package com.asystent.kinowy.ui;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

/**
 * Delegujący dialog — natychmiast otwiera {@link PayrollBottomSheet}
 * z przekazanymi parametrami i zamyka się.
 * <p>
 * Wywoływany z alertu "brak raportu za poprzedni miesiąc" na Dashboardzie.
 */
public class MonthlyReportDialog extends DialogFragment {

    private static final String ARG_MONTH_YEAR = "arg_month_year";
    private static final String ARG_CALC_HOURS = "arg_calc_hours";
    private static final String ARG_CALC_SALARY = "arg_calc_salary";

    private String targetMonthYear;
    private double calculatedHours;
    private double calculatedSalary;

    public MonthlyReportDialog() {
        // Wymagany konstruktor bezargumentowy dla odtwarzania Fragmentu
    }

    public MonthlyReportDialog(String monthYear, double calcHours, double calcSalary) {
        this.targetMonthYear = monthYear;
        this.calculatedHours = calcHours;
        this.calculatedSalary = calcSalary;
        Bundle args = new Bundle();
        args.putString(ARG_MONTH_YEAR, monthYear);
        args.putDouble(ARG_CALC_HOURS, calcHours);
        args.putDouble(ARG_CALC_SALARY, calcSalary);
        setArguments(args);
    }

    public static MonthlyReportDialog newInstance(String monthYear, double calcHours, double calcSalary) {
        return new MonthlyReportDialog(monthYear, calcHours, calcSalary);
    }

    @Override
    public void onStart() {
        super.onStart();
        Bundle args = getArguments();
        if (args != null) {
            if (targetMonthYear == null) {
                targetMonthYear = args.getString(ARG_MONTH_YEAR, "");
            }
            if (calculatedHours == 0.0 && args.containsKey(ARG_CALC_HOURS)) {
                calculatedHours = args.getDouble(ARG_CALC_HOURS, 0.0);
            }
            if (calculatedSalary == 0.0 && args.containsKey(ARG_CALC_SALARY)) {
                calculatedSalary = args.getDouble(ARG_CALC_SALARY, 0.0);
            }
        }
        // Otwórz PayrollBottomSheet z tymi samymi parametrami
        PayrollBottomSheet bottomSheet = PayrollBottomSheet.newInstance(
                targetMonthYear != null ? targetMonthYear : "", calculatedHours, calculatedSalary);
        bottomSheet.show(getParentFragmentManager(), "PayrollBottomSheet");
        // Zamknij ten dialog natychmiast
        dismiss();
    }
}
