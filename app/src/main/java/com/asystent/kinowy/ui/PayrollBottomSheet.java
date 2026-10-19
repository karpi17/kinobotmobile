package com.asystent.kinowy.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.asystent.kinowy.R;
import com.asystent.kinowy.db.AppDatabase;
import com.asystent.kinowy.models.MonthlyReport;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Bottom Sheet do archiwizacji miesiąca (dwustopniowy flow).
 * <p>
 * Stopień 1: Użytkownik podaje "Godziny z papierka" → ZAPISZ (actualSalary = null).
 * Stopień 2: Użytkownik otwiera ponownie → pola wypełnione z bazy → wpisuje kwotę → UPDATE.
 */
public class PayrollBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_TARGET_MONTH = "target_month_year";
    private static final String ARG_CALC_HOURS = "calculated_hours";
    private static final String ARG_CALC_SALARY = "calculated_salary";
    private static final String ARG_NIGHT_HOURS = "night_hours";

    private String targetMonthYear = "";
    private double calculatedHours = 0.0;
    private double calculatedSalary = 0.0;
    private double nightHours = 0.0;

    // Tryb edycji — true jeśli raport już istnieje w bazie
    private boolean isEditMode = false;
    // Zapamiętana wartość: paperHours * stawka
    private double paperCalculatedSalary = 0.0;

    public PayrollBottomSheet() {
        // Required empty public constructor
    }

    public PayrollBottomSheet(String targetMonthYear, double calculatedHours, double calculatedSalary) {
        this(targetMonthYear, calculatedHours, calculatedSalary, 0.0);
    }

    public PayrollBottomSheet(String targetMonthYear, double calculatedHours, double calculatedSalary, double nightHours) {
        this.targetMonthYear = targetMonthYear != null ? targetMonthYear : "";
        this.calculatedHours = calculatedHours;
        this.calculatedSalary = calculatedSalary;
        this.nightHours = nightHours;
        Bundle args = new Bundle();
        args.putString(ARG_TARGET_MONTH, this.targetMonthYear);
        args.putDouble(ARG_CALC_HOURS, calculatedHours);
        args.putDouble(ARG_CALC_SALARY, calculatedSalary);
        args.putDouble(ARG_NIGHT_HOURS, nightHours);
        setArguments(args);
    }

    public static PayrollBottomSheet newInstance(String targetMonthYear, double calculatedHours, double calculatedSalary) {
        return new PayrollBottomSheet(targetMonthYear, calculatedHours, calculatedSalary, 0.0);
    }

    public static PayrollBottomSheet newInstance(String targetMonthYear, double calculatedHours, double calculatedSalary, double nightHours) {
        return new PayrollBottomSheet(targetMonthYear, calculatedHours, calculatedSalary, nightHours);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            targetMonthYear = args.getString(ARG_TARGET_MONTH, targetMonthYear);
            calculatedHours = args.getDouble(ARG_CALC_HOURS, calculatedHours);
            calculatedSalary = args.getDouble(ARG_CALC_SALARY, calculatedSalary);
            nightHours = args.getDouble(ARG_NIGHT_HOURS, nightHours);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_payroll, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MainViewModel vm = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        MainViewModel.PayrollInfo payrollInfo = vm.getMonthlyPayroll().getValue();
        if (nightHours <= 0 && targetMonthYear != null) {
            if (payrollInfo != null && targetMonthYear.equals(vm.getCurrentMonthPrefix())) {
                nightHours = payrollInfo.getNightHours();
            } else if (vm.getAllShifts().getValue() != null) {
                double computedNight = 0.0;
                for (com.asystent.kinowy.models.Shift s : vm.getAllShifts().getValue()) {
                    if (s != null && !s.isReplacement() && s.getDate() != null
                            && s.getDate().startsWith(targetMonthYear)) {
                        computedNight += com.asystent.kinowy.utils.PayrollCalculator.calculateNightHours(
                                s.getStartTime(), s.getEndTime());
                    }
                }
                nightHours = Math.round(computedNight * 100.0) / 100.0;
            }
        }

        // --- UI Refs ---
        TextView tvTitle = view.findViewById(R.id.tv_report_title);
        TextView tvCalcHours = view.findViewById(R.id.tv_calc_hours);
        TextView tvCalcNightHours = view.findViewById(R.id.tv_calc_night_hours);
        TextView tvCalcSalary = view.findViewById(R.id.tv_calc_salary);
        TextView tvPaperSalary = view.findViewById(R.id.tv_paper_salary);
        TextView tvDifference = view.findViewById(R.id.tv_difference);
        TextInputEditText etPaperHours = view.findViewById(R.id.et_paper_hours);
        TextInputEditText etActualSalary = view.findViewById(R.id.et_actual_salary);

        // --- Statyczne dane z systemu ---
        tvTitle.setText(String.format("Archiwizacja Miesiąca: %s", targetMonthYear));
        tvCalcHours.setText(String.format(Locale.getDefault(), "%.2f h", calculatedHours));
        if (tvCalcNightHours != null) {
            tvCalcNightHours.setText(String.format(Locale.getDefault(), "%.1f h", nightHours));
        }
        tvCalcSalary.setText(String.format(Locale.getDefault(), "%.2f zł", calculatedSalary));

        // --- Pobierz stawkę godzinową ---
        Float rateObj = vm.getHourlyRateLive().getValue();
        float fallbackRate = (rateObj != null) ? rateObj : 0f;
        final float hourlyRate = (payrollInfo != null && payrollInfo.getHourlyRate() > 0)
                ? payrollInfo.getHourlyRate() : fallbackRate;

        // ═══════════════════════════════════════════════════════════════
        // KROK 2: TextWatcher — "Godziny z papierka" → reaktywne obliczenie
        // ═══════════════════════════════════════════════════════════════
        TextWatcher paperHoursWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().trim();
                if (text.isEmpty()) {
                    tvPaperSalary.setText("Należy się z papierka: --");
                    tvPaperSalary.setTextColor(0xFFAAAAAA);
                    paperCalculatedSalary = 0.0;
                    recalculateDifference(etActualSalary, tvDifference);
                    return;
                }
                try {
                    double paperHours = Double.parseDouble(text);
                    if (hourlyRate <= 0f) {
                        tvPaperSalary.setText("Ustaw stawkę w zakładce Finanse");
                        tvPaperSalary.setTextColor(0xFFF44336);
                        paperCalculatedSalary = 0.0;
                    } else {
                        paperCalculatedSalary = paperHours * hourlyRate;
                        tvPaperSalary.setText(String.format(Locale.getDefault(),
                                "Należy się z papierka: %.2f zł", paperCalculatedSalary));
                        tvPaperSalary.setTextColor(0xFFFFFFFF);
                    }
                    recalculateDifference(etActualSalary, tvDifference);
                } catch (NumberFormatException e) {
                    tvPaperSalary.setText("Należy się z papierka: --");
                    tvPaperSalary.setTextColor(0xFFAAAAAA);
                    paperCalculatedSalary = 0.0;
                    recalculateDifference(etActualSalary, tvDifference);
                }
            }
        };
        etPaperHours.addTextChangedListener(paperHoursWatcher);

        // ═══════════════════════════════════════════════════════════════
        // KROK 3: TextWatcher — "Kwota przelewu" → różnica
        // ═══════════════════════════════════════════════════════════════
        TextWatcher actualSalaryWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                recalculateDifference(etActualSalary, tvDifference);
            }
        };
        etActualSalary.addTextChangedListener(actualSalaryWatcher);

        // ═══════════════════════════════════════════════════════════════
        // KROK 4: Tryb edycji — sprawdź czy raport już istnieje
        // ═══════════════════════════════════════════════════════════════
        Executors.newSingleThreadExecutor().execute(() -> {
            MonthlyReport existing = AppDatabase.getInstance(requireContext())
                    .monthlyReportDao().getReportForMonthSync(targetMonthYear);
            if (existing != null && getActivity() != null) {
                isEditMode = true;
                getActivity().runOnUiThread(() -> {
                    etPaperHours.setText(String.valueOf(existing.getPaperHours()));
                    if (existing.getActualSalary() != null && existing.getActualSalary() > 0) {
                        etActualSalary.setText(String.valueOf(existing.getActualSalary()));
                    }
                    tvTitle.setText(String.format("Edycja Raportu: %s", targetMonthYear));
                });
            }
        });

        // ═══════════════════════════════════════════════════════════════
        // ZAPIS — INSERT lub UPDATE w zależności od trybu
        // ═══════════════════════════════════════════════════════════════
        view.findViewById(R.id.btn_save_report).setOnClickListener(v -> {
            String paperHoursStr = etPaperHours.getText() != null ? etPaperHours.getText().toString().trim() : "";
            String actualSalaryStr = etActualSalary.getText() != null ? etActualSalary.getText().toString().trim() : "";

            // Godziny z papierka są OBOWIĄZKOWE
            if (paperHoursStr.isEmpty()) {
                Toast.makeText(requireContext(), "Podaj godziny z grafiku papierowego", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double paperHours = Double.parseDouble(paperHoursStr);

                // Kwota przelewu jest OPCJONALNA
                Double actualSalary = null;
                if (!actualSalaryStr.isEmpty()) {
                    actualSalary = Double.parseDouble(actualSalaryStr);
                }

                MonthlyReport report = new MonthlyReport(
                        targetMonthYear, calculatedHours, paperHours, calculatedSalary, actualSalary);

                final boolean editMode = isEditMode;
                Executors.newSingleThreadExecutor().execute(() -> {
                    if (editMode) {
                        AppDatabase.getInstance(requireContext()).monthlyReportDao().update(report);
                    } else {
                        AppDatabase.getInstance(requireContext()).monthlyReportDao().insert(report);
                    }
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            vm.refreshMissingReportCheck();
                            String msg = editMode ? "Raport został zaktualizowany" : "Raport został zarchiwizowany";
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                            dismiss();
                        });
                    }
                });

            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Wprowadź poprawne wartości numeryczne", Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.btn_dismiss_payroll).setOnClickListener(v -> dismiss());
    }

    /**
     * Przelicza i wyświetla różnicę: actualSalary - paperCalculatedSalary.
     * Zielony jeśli >= 0, czerwony jeśli < 0.
     */
    private void recalculateDifference(TextInputEditText etActualSalary, TextView tvDifference) {
        String actualStr = etActualSalary.getText() != null ? etActualSalary.getText().toString().trim() : "";
        if (actualStr.isEmpty() || paperCalculatedSalary <= 0) {
            tvDifference.setText("Różnica: --");
            tvDifference.setTextColor(0xFF888888);
            return;
        }
        try {
            double actual = Double.parseDouble(actualStr);
            double diff = actual - paperCalculatedSalary;
            tvDifference.setText(String.format(Locale.getDefault(),
                    "Różnica: %s%.2f zł", diff > 0 ? "+" : "", diff));
            tvDifference.setTextColor(diff >= 0 ? 0xFF4CAF50 : 0xFFF44336);
        } catch (NumberFormatException e) {
            tvDifference.setText("Różnica: --");
            tvDifference.setTextColor(0xFF888888);
        }
    }
}
