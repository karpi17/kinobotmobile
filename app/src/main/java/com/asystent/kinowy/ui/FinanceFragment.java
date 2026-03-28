package com.asystent.kinowy.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Fragment z widokiem finansów i rozliczeń.
 * <p>
 * Odpowiedzialności:
 * <ul>
 *   <li>Zarządzanie stawką godzinową (SharedPreferences)</li>
 *   <li>Dodawanie strat / mank do bazy Room</li>
 *   <li>Wyświetlanie podsumowania wypłaty za bieżący miesiąc</li>
 *   <li>Wyświetlanie listy strat w bieżącym miesiącu</li>
 * </ul>
 */
public class FinanceFragment extends Fragment {

    private static final String PREFS_NAME = "asystent_kinowy_prefs";
    private static final String PREF_HOURLY_RATE = "hourly_rate";

    private MainViewModel viewModel;
    private LossAdapter lossAdapter;

    // UI — Podsumowanie
    private TextView tvSummaryAmount;
    private TextView tvSummaryDetails;

    // UI — Stawka
    private TextInputEditText etHourlyRate;
    private MaterialButton btnSaveRate;
    private TextView tvSavedRate;

    // UI — Strata
    private TextInputEditText etLossAmount;
    private TextInputEditText etLossDescription;
    private MaterialButton btnAddLoss;

    // UI — Napiwek
    private TextInputEditText etTipAmount;
    private TextInputEditText etTipDescription;
    private MaterialButton btnAddTip;

    // UI — Lista strat
    private RecyclerView rvLosses;
    private TextView tvLossesEmpty;

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

        // --- ViewModel (współdzielony z Activity) ---
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // --- Wybierak Miesiąca ---
        ImageButton btnPrevM = view.findViewById(R.id.btn_prev_month);
        ImageButton btnNextM = view.findViewById(R.id.btn_next_month);
        TextView tvCurrentM = view.findViewById(R.id.tv_current_month);

        btnPrevM.setOnClickListener(v -> viewModel.previousMonth());
        btnNextM.setOnClickListener(v -> viewModel.nextMonth());

        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("pl", "PL"));
        viewModel.getCurrentSelectedMonth().observe(getViewLifecycleOwner(), yearMonth -> {
            if (yearMonth != null) {
                String formatted = yearMonth.format(monthFormatter);
                formatted = formatted.substring(0, 1).toUpperCase(new Locale("pl", "PL")) + formatted.substring(1);
                tvCurrentM.setText(formatted);
            }
        });

        // --- Bind UI ---
        bindViews(view);

        // --- Stawka godzinowa ---
        setupHourlyRate();

        // --- Dodawanie strat ---
        setupLossInput();
        
        // --- Dodawanie napiwków ---
        setupTipInput();

        // --- Lista strat ---
        setupLossesList();

        // --- Obserwatory ---
        observePayroll();
        observeMonthlyLosses();
    }

    // ─── Bind UI ─────────────────────────────────────────────────────────

    private void bindViews(View view) {
        tvSummaryAmount = view.findViewById(R.id.tv_summary_amount);
        tvSummaryDetails = view.findViewById(R.id.tv_summary_details);

        etHourlyRate = view.findViewById(R.id.et_hourly_rate);
        btnSaveRate = view.findViewById(R.id.btn_save_rate);
        tvSavedRate = view.findViewById(R.id.tv_saved_rate);

        etLossAmount = view.findViewById(R.id.et_loss_amount);
        etLossDescription = view.findViewById(R.id.et_loss_description);
        btnAddLoss = view.findViewById(R.id.btn_add_loss);

        etTipAmount = view.findViewById(R.id.et_tip_amount);
        etTipDescription = view.findViewById(R.id.et_tip_description);
        btnAddTip = view.findViewById(R.id.btn_add_tip);

        rvLosses = view.findViewById(R.id.rv_losses);
        tvLossesEmpty = view.findViewById(R.id.tv_losses_empty);
    }

    // ─── Stawka godzinowa (SharedPreferences) ────────────────────────────

    private SharedPreferences getPrefs() {
        return requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void setupHourlyRate() {
        float savedRate = getPrefs().getFloat(PREF_HOURLY_RATE, 0f);

        if (savedRate > 0) {
            etHourlyRate.setText(String.valueOf(savedRate));
            tvSavedRate.setText(String.format("Aktualna stawka: %.2f zł/h netto", savedRate));
            tvSavedRate.setVisibility(View.VISIBLE);
        }

        // Przekazujemy stawkę do ViewModelu
        viewModel.setHourlyRate(savedRate);

        btnSaveRate.setOnClickListener(v -> {
            String rateStr = etHourlyRate.getText() != null
                    ? etHourlyRate.getText().toString().trim() : "";
            if (rateStr.isEmpty()) {
                etHourlyRate.setError("Wpisz stawkę");
                return;
            }

            try {
                float rate = Float.parseFloat(rateStr.replace(",", "."));
                if (rate <= 0) {
                    etHourlyRate.setError("Stawka musi być > 0");
                    return;
                }

                // Zapisz do SharedPreferences
                getPrefs().edit().putFloat(PREF_HOURLY_RATE, rate).apply();

                // Przekaż do ViewModelu (wywoła przeliczenie)
                viewModel.setHourlyRate(rate);

                tvSavedRate.setText(String.format("Aktualna stawka: %.2f zł/h netto", rate));
                tvSavedRate.setVisibility(View.VISIBLE);

                Toast.makeText(requireContext(), "Stawka zapisana", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                etHourlyRate.setError("Nieprawidłowy format liczby");
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
                if (amount <= 0) {
                    etLossAmount.setError("Kwota musi być > 0");
                    return;
                }

                String description = etLossDescription.getText() != null
                        ? etLossDescription.getText().toString().trim() : "";

                // Dzisiejsza data w formacie ISO
                String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

                // Utwórz i zapisz nowy obiekt Loss
                Loss loss = new Loss(today, amount, description);
                viewModel.insertLoss(loss);

                // Wyczyść formularz
                etLossAmount.setText("");
                etLossDescription.setText("");

                Toast.makeText(requireContext(),
                        String.format("Dodano stratę: %.2f zł", amount),
                        Toast.LENGTH_SHORT).show();

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
                if (amount <= 0) {
                    etTipAmount.setError("Kwota musi być > 0");
                    return;
                }

                String description = etTipDescription.getText() != null
                        ? etTipDescription.getText().toString().trim() : "";

                // Dzisiejsza data w formacie ISO
                String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

                com.asystent.kinowy.models.Tip tip = new com.asystent.kinowy.models.Tip(today, amount, description);
                viewModel.insertTip(tip);

                // Wyczyść formularz
                etTipAmount.setText("");
                etTipDescription.setText("");

                Toast.makeText(requireContext(),
                        String.format("Dodano napiwek: %.2f zł", amount),
                        Toast.LENGTH_SHORT).show();

            } catch (NumberFormatException e) {
                etTipAmount.setError("Nieprawidłowy format liczby");
            }
        });
    }

    // ─── Lista strat (RecyclerView) ──────────────────────────────────────

    private void setupLossesList() {
        lossAdapter = new LossAdapter();
        rvLosses.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvLosses.setAdapter(lossAdapter);
    }

    // ─── Obserwatory ─────────────────────────────────────────────────────

    private void observePayroll() {
        viewModel.getMonthlyPayroll().observe(getViewLifecycleOwner(), payrollInfo -> {
            if (payrollInfo == null) return;

            tvSummaryAmount.setText(String.format("%.2f zł", payrollInfo.getNetPay()));
            tvSummaryDetails.setText(String.format(
                    "%.1f h × %.2f zł − %.2f zł strat + %.2f zł napiwków",
                    payrollInfo.getTotalHours(),
                    payrollInfo.getHourlyRate(),
                    payrollInfo.getTotalLosses(),
                    payrollInfo.getTotalTips()
            ));
        });
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
