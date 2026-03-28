package com.asystent.kinowy.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.asystent.kinowy.R;
import com.asystent.kinowy.db.AppDatabase;
import com.asystent.kinowy.models.MonthlyReport;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;
import java.util.concurrent.Executors;

public class MonthlyReportDialog extends DialogFragment {

    private String targetMonthYear; // np. "2026-03"
    private double calculatedHours = 0.0;
    private double calculatedSalary = 0.0;

    public MonthlyReportDialog(String monthYear, double calcHours, double calcSalary) {
        this.targetMonthYear = monthYear;
        this.calculatedHours = calcHours;
        this.calculatedSalary = calcSalary;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_monthly_report, null);

        TextView tvTitle = view.findViewById(R.id.tv_report_title);
        TextView tvStats = view.findViewById(R.id.tv_calculated_stats);
        TextInputEditText etPaperHours = view.findViewById(R.id.et_paper_hours);
        TextInputEditText etActualSalary = view.findViewById(R.id.et_actual_salary);

        tvTitle.setText(String.format("Raport za %s", targetMonthYear));
        tvStats.setText(String.format(Locale.US, "Z pamięci systemu:\nGodziny: %.2f | Wypłata: %.2f zł", calculatedHours, calculatedSalary));

        view.findViewById(R.id.btn_cancel_report).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_save_report).setOnClickListener(v -> {
            String paperHoursStr = etPaperHours.getText() != null ? etPaperHours.getText().toString().trim() : "";
            String actualSalaryStr = etActualSalary.getText() != null ? etActualSalary.getText().toString().trim() : "";

            if (paperHoursStr.isEmpty() || actualSalaryStr.isEmpty()) {
                Toast.makeText(requireContext(), "Wypełnij wszystkie pola", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double paperHours = Double.parseDouble(paperHoursStr);
                double actualSalary = Double.parseDouble(actualSalaryStr);

                MonthlyReport report = new MonthlyReport(targetMonthYear, calculatedHours, paperHours, calculatedSalary, actualSalary);
                
                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase.getInstance(requireContext()).monthlyReportDao().insert(report);
                    // Odśwież UI w głównym wątku za pośrednictwem ViewModelu
                    requireActivity().runOnUiThread(() -> {
                        MainViewModel vm = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
                        vm.refreshMissingReportCheck();
                        Toast.makeText(requireContext(), "Zapisano raport", Toast.LENGTH_SHORT).show();
                        dismiss();
                    });
                });

            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Błędne wartości numeryczne", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setView(view);
        AlertDialog dialog = builder.create();
        return dialog;
    }
}
