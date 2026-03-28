package com.asystent.kinowy.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.asystent.kinowy.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

public class PayrollBottomSheet extends BottomSheetDialogFragment {

    private final double totalHours;
    private final double hourlyRate;
    private final double totalLosses;

    public PayrollBottomSheet(double totalHours, double hourlyRate, double totalLosses) {
        this.totalHours = totalHours;
        this.hourlyRate = hourlyRate;
        this.totalLosses = totalLosses;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_payroll, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvTotalHours = view.findViewById(R.id.tv_total_hours);
        TextView tvTaxFreeAmount = view.findViewById(R.id.tv_tax_free_amount);
        TextView tvTaxableAmount = view.findViewById(R.id.tv_taxable_amount);
        TextView tvTotalLosses = view.findViewById(R.id.tv_total_losses);
        TextView tvFinalPayout = view.findViewById(R.id.tv_final_payout);

        tvTotalHours.setText(String.format(Locale.getDefault(), "%.1f h", totalHours));
        tvTotalLosses.setText(String.format(Locale.getDefault(), "-%.2f zł", totalLosses));

        // TODO: Logika podatkowa / podział na gotówkę i przelew.
        // Placeholder implementation matching user request.
        
        double taxFree = 0;
        double taxable = 0;
        
        if (totalHours <= 150) {
            taxFree = totalHours * hourlyRate;
        } else {
            taxFree = 150 * hourlyRate;
            taxable = (totalHours - 150) * hourlyRate * 0.88; // Placeholder: 12% tax assumed
        }
        
        // Final payout applies losses logic (which might be taken from cash or overall salary)
        double finalPayout = Math.max(0, taxFree + taxable - totalLosses);

        tvTaxFreeAmount.setText(String.format(Locale.getDefault(), "%.2f zł", taxFree));
        tvTaxableAmount.setText(String.format(Locale.getDefault(), "%.2f zł", taxable));
        tvFinalPayout.setText(String.format(Locale.getDefault(), "%.2f zł", finalPayout));

        view.findViewById(R.id.btn_dismiss_payroll).setOnClickListener(v -> dismiss());
    }
}
