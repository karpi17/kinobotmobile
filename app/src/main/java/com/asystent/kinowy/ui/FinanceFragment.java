package com.asystent.kinowy.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.Loss;
import com.asystent.kinowy.utils.PayrollCalculator;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Fragment z widokiem finansów i rozliczeń.
 *
 * Faza 4 (Finanse Pro):
 * - ProgressBar realizacji celu miesięcznego w PLN
 * - Symulator dodatkowych godzin (TextWatcher → wynik w czasie rzeczywistym)
 * - Historia transakcji (Straty + Napiwki)
 * - Obliczenia Per-Shift (każda zmiana × stawka w jej dniu)
 */
public class FinanceFragment extends Fragment {

    private MainViewModel viewModel;
    private LossAdapter lossAdapter;

    // UI — Podsumowanie i Progress
    private TextView tvSummaryAmount;
    private TextView tvSummaryDetails;
    private ProgressBar progressGoal;
    private TextView tvGoalPercent;
    private TextView tvGoalMissing;

    // UI — Symulator
    private TextInputEditText etSimulateHours;
    private TextView tvSimulateResult;

    // UI — Strata
    private TextInputEditText etLossAmount;
    private TextInputEditText etLossDescription;
    private MaterialButton btnAddLoss;

    // UI — Napiwek
    private TextInputEditText etTipAmount;
    private TextInputEditText etTipDescription;
    private MaterialButton btnAddTip;

    // UI — Lista transakcji
    private RecyclerView rvLosses;
    private TextView tvLossesEmpty;

    // Stan bieżący (do symulatora)
    private double currentNetPay = 0;
    private float currentGoalPLN = 0;
    private float currentHourlyRate = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_finance, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // --- Wybierak Miesiąca ---
        ImageButton btnPrevM = view.findViewById(R.id.btn_prev_month);
        ImageButton btnNextM = view.findViewById(R.id.btn_next_month);
        TextView tvCurrentM = view.findViewById(R.id.tv_current_month);

        btnPrevM.setOnClickListener(v -> viewModel.previousMonth());
        btnNextM.setOnClickListener(v -> viewModel.nextMonth());

        java.time.format.DateTimeFormatter monthFormatter =
                java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("pl", "PL"));
        viewModel.getCurrentSelectedMonth().observe(getViewLifecycleOwner(), yearMonth -> {
            if (yearMonth != null) {
                String formatted = yearMonth.format(monthFormatter);
                formatted = formatted.substring(0, 1).toUpperCase(new Locale("pl", "PL")) + formatted.substring(1);
                tvCurrentM.setText(formatted);
            }
        });

        bindViews(view);
        setupLossInput();
        setupTipInput();
        setupLossesList();
        setupSimulator();
        observePayroll();
        observeMonthlyLosses();
    }

    // ─── Bind UI ─────────────────────────────────────────────────────────

    private void bindViews(View view) {
        tvSummaryAmount = view.findViewById(R.id.tv_summary_amount);
        tvSummaryDetails = view.findViewById(R.id.tv_summary_details);
        progressGoal = view.findViewById(R.id.progress_goal);
        tvGoalPercent = view.findViewById(R.id.tv_goal_percent);
        tvGoalMissing = view.findViewById(R.id.tv_goal_missing);

        etSimulateHours = view.findViewById(R.id.et_simulate_hours);
        tvSimulateResult = view.findViewById(R.id.tv_simulate_result);

        etLossAmount = view.findViewById(R.id.et_loss_amount);
        etLossDescription = view.findViewById(R.id.et_loss_description);
        btnAddLoss = view.findViewById(R.id.btn_add_loss);

        etTipAmount = view.findViewById(R.id.et_tip_amount);
        etTipDescription = view.findViewById(R.id.et_tip_description);
        btnAddTip = view.findViewById(R.id.btn_add_tip);

        rvLosses = view.findViewById(R.id.rv_losses);
        tvLossesEmpty = view.findViewById(R.id.tv_losses_empty);
    }

    // ─── Symulator ────────────────────────────────────────────────────────

    private void setupSimulator() {
        etSimulateHours.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String input = s != null ? s.toString().trim() : "";
                if (input.isEmpty()) {
                    tvSimulateResult.setText("—");
                    return;
                }
                try {
                    double extraHours = Double.parseDouble(input.replace(",", "."));
                    PayrollCalculator.SimulationResult result = PayrollCalculator.simulateExtraHours(
                            extraHours, currentHourlyRate, currentNetPay, currentGoalPLN
                    );
                    tvSimulateResult.setText(result.toDisplayString());
                } catch (NumberFormatException e) {
                    tvSimulateResult.setText("—");
                }
            }
        });
    }

    // ─── Dodawanie straty ────────────────────────────────────────────────

    private void setupLossInput() {
        btnAddLoss.setOnClickListener(v -> {
            String amountStr = etLossAmount.getText() != null
                    ? etLossAmount.getText().toString().trim() : "";
            if (amountStr.isEmpty()) {
                etLossAmount.setError("Wpisz kwotę straty");
                return;
            }
            try {
                double amount = Double.parseDouble(amountStr.replace(",", "."));
                if (amount <= 0) { etLossAmount.setError("Kwota musi być > 0"); return; }

                String description = etLossDescription.getText() != null
                        ? etLossDescription.getText().toString().trim() : "";
                String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

                Loss loss = new Loss(today, amount, description);
                viewModel.insertLoss(loss);
                etLossAmount.setText("");
                etLossDescription.setText("");
                Toast.makeText(requireContext(),
                        String.format("Dodano stratę: %.2f zł", amount), Toast.LENGTH_SHORT).show();

            } catch (NumberFormatException e) {
                etLossAmount.setError("Nieprawidłowy format liczby");
            }
        });
    }

    // ─── Dodawanie napiwku ───────────────────────────────────────────────

    private void setupTipInput() {
        btnAddTip.setOnClickListener(v -> {
            String amountStr = etTipAmount.getText() != null
                    ? etTipAmount.getText().toString().trim() : "";
            if (amountStr.isEmpty()) {
                etTipAmount.setError("Wpisz kwotę napiwku");
                return;
            }
            try {
                double amount = Double.parseDouble(amountStr.replace(",", "."));
                if (amount <= 0) { etTipAmount.setError("Kwota musi być > 0"); return; }

                String description = etTipDescription.getText() != null
                        ? etTipDescription.getText().toString().trim() : "";
                String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

                com.asystent.kinowy.models.Tip tip = new com.asystent.kinowy.models.Tip(today, amount, description);
                viewModel.insertTip(tip);
                etTipAmount.setText("");
                etTipDescription.setText("");
                Toast.makeText(requireContext(),
                        String.format("Dodano napiwek: %.2f zł", amount), Toast.LENGTH_SHORT).show();

            } catch (NumberFormatException e) {
                etTipAmount.setError("Nieprawidłowy format liczby");
            }
        });
    }

    // ─── Lista transakcji (RecyclerView) ─────────────────────────────────

    private void setupLossesList() {
        lossAdapter = new LossAdapter();
        rvLosses.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvLosses.setAdapter(lossAdapter);
    }

    // ─── Obserwatory ─────────────────────────────────────────────────────

    private void observePayroll() {
        viewModel.getMonthlyPayroll().observe(getViewLifecycleOwner(), payrollInfo -> {
            if (payrollInfo == null) return;

            // Aktualizuj stan do symulatora
            currentNetPay = payrollInfo.getNetPay();
            currentGoalPLN = payrollInfo.getGoalPLN();
            currentHourlyRate = payrollInfo.getHourlyRate();

            // Główna kwota
            tvSummaryAmount.setText(String.format(Locale.getDefault(), "%.2f zł", payrollInfo.getNetPay()));

            // Szczegóły (Per-Shift — stawka może być różna)
            tvSummaryDetails.setText(String.format(
                    "%.1f h × ~%.2f zł − %.2f zł strat + %.2f zł napiwków",
                    payrollInfo.getTotalHours(),
                    payrollInfo.getHourlyRate(),
                    payrollInfo.getTotalLosses(),
                    payrollInfo.getTotalTips()
            ));

            // Progress celu
            if (payrollInfo.getGoalPLN() > 0) {
                int progress = payrollInfo.getGoalProgress();
                progressGoal.setProgress(progress);
                tvGoalPercent.setText(progress + "%");

                double missingPLN = payrollInfo.getMissingPLN();
                double missingH   = payrollInfo.getMissingHours();
                if (missingPLN > 0.01) {
                    tvGoalMissing.setText(String.format(Locale.getDefault(),
                            "Brakuje %.2f zł (~%.1f h) do celu %.2f zł",
                            missingPLN, missingH, payrollInfo.getGoalPLN()));
                } else {
                    tvGoalMissing.setText("🎉 Cel miesięczny osiągnięty!");
                }
            } else {
                progressGoal.setProgress(0);
                tvGoalPercent.setText("0%");
                tvGoalMissing.setText("Ustaw cel finansowy w Profilu →");
            }

            // Odśwież symulator jeśli użytkownik coś wpisał
            String simInput = etSimulateHours.getText() != null
                    ? etSimulateHours.getText().toString().trim() : "";
            if (!simInput.isEmpty()) {
                try {
                    double extraHours = Double.parseDouble(simInput.replace(",", "."));
                    PayrollCalculator.SimulationResult result = PayrollCalculator.simulateExtraHours(
                            extraHours, currentHourlyRate, currentNetPay, currentGoalPLN
                    );
                    tvSimulateResult.setText(result.toDisplayString());
                } catch (NumberFormatException ignored) {}
            }
        });
        
        // Kliknięcie w główną kartę — pokaż szczegóły
        View cardSummary = getView() != null ? getView().findViewById(R.id.card_summary) : null;
        if (cardSummary != null) {
            cardSummary.setOnClickListener(v -> {
                MainViewModel.PayrollInfo payroll = viewModel.getMonthlyPayroll().getValue();
                if (payroll == null || payroll.getBreakdown() == null || payroll.getBreakdown().isEmpty()) return;

                StringBuilder sb = new StringBuilder();
                for (MainViewModel.RateBreakdown b : payroll.getBreakdown()) {
                    sb.append(String.format(Locale.getDefault(), 
                        "• %.1f h ze stawką %.2f zł = %.2f zł\n", b.hours, b.rate, b.earned));
                }
                
                double totalEarnedGross = 0;
                for (MainViewModel.RateBreakdown b : payroll.getBreakdown()) {
                    totalEarnedGross += b.earned;
                }
                sb.append(String.format(Locale.getDefault(), "\nRazem brutto: %.2f zł", totalEarnedGross));
                
                if (payroll.getTotalLosses() > 0) {
                    sb.append(String.format(Locale.getDefault(), "\nOdliczono straty: -%.2f zł", payroll.getTotalLosses()));
                }
                if (payroll.getTotalTips() > 0) {
                    sb.append(String.format(Locale.getDefault(), "\nDoliczono napiwki: +%.2f zł", payroll.getTotalTips()));
                }
                sb.append(String.format(Locale.getDefault(), "\n\nSuma na rękę: %.2f zł", payroll.getNetPay()));

                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Szczegóły wyliczenia")
                        .setMessage(sb.toString().trim())
                        .setPositiveButton("Zamknij", null)
                        .show();
            });
        }
    }

    private void observeMonthlyLosses() {
        viewModel.getMonthlyLosses().observe(getViewLifecycleOwner(), losses -> {
            if (losses == null || losses.isEmpty()) {
                rvLosses.setVisibility(View.GONE);
                tvLossesEmpty.setVisibility(View.VISIBLE);
            } else {
                rvLosses.setVisibility(View.VISIBLE);
                tvLossesEmpty.setVisibility(View.GONE);
                lossAdapter.setLosses(losses);
            }
        });
    }
}
