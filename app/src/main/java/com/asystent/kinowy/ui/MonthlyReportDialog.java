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

    private final String targetMonthYear;
    private final double calculatedHours;
    private final double calculatedSalary;

    public MonthlyReportDialog(String monthYear, double calcHours, double calcSalary) {
        this.targetMonthYear = monthYear;
        this.calculatedHours = calcHours;
        this.calculatedSalary = calcSalary;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Otwórz PayrollBottomSheet z tymi samymi parametrami
        PayrollBottomSheet bottomSheet = new PayrollBottomSheet(
                targetMonthYear, calculatedHours, calculatedSalary);
        bottomSheet.show(getParentFragmentManager(), "PayrollBottomSheet");
        // Zamknij ten dialog natychmiast
        dismiss();
    }
}
