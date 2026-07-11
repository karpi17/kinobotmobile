package com.asystent.kinowy.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;


import com.asystent.kinowy.R;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Ekran Profilu — centralne miejsce ustawień użytkownika.
 * <ul>
 *   <li>Imię użytkownika (PREF_USER_NAME) — używane przez widget i parsera Excela</li>
 *   <li>Stawka godzinowa (PREF_HOURLY_RATE) — używana przez kalkulator payroll</li>
 *   <li>Wersja aplikacji</li>
 * </ul>
 *
 * Klucze SharedPreferences są identyczne z tymi w DashboardFragment i FinanceFragment,
 * co zapewnia pełną kompatybilność wsteczną bez migracji danych.
 */
public class ProfileFragment extends Fragment {

    // Klucze SharedPreferences — identyczne jak w DashboardFragment i FinanceFragment
    private static final String PREFS_NAME       = "asystent_kinowy_prefs";
    private static final String PREF_USER_NAME   = "user_name";
    private static final String PREF_HOURLY_RATE = "hourly_rate";

    private MainViewModel viewModel;

    private TextInputEditText etName;
    private TextInputEditText etHourlyRate;
    private TextView          tvSavedRate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        etName       = view.findViewById(R.id.et_profile_name);
        etHourlyRate = view.findViewById(R.id.et_profile_hourly_rate);
        tvSavedRate  = view.findViewById(R.id.tv_profile_saved_rate);

        // ── Wersja aplikacji ──────────────────────────────────────────────────
        TextView tvVersion = view.findViewById(R.id.tv_profile_version);
        String versionName = "?";
        try {
            versionName = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        tvVersion.setText("KinoBot v" + versionName);

        // ── Wczytaj zapisane wartości ─────────────────────────────────────────
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);

        String savedName = prefs.getString(PREF_USER_NAME, "");
        if (!TextUtils.isEmpty(savedName)) {
            etName.setText(savedName);
        }

        float savedRate = prefs.getFloat(PREF_HOURLY_RATE, 0f);
        // Zawsze inicjalizuj ViewModel stawką — Finance payroll tego wymaga
        viewModel.setHourlyRate(savedRate);
        if (savedRate > 0f) {
            etHourlyRate.setText(String.valueOf(savedRate));
            showSavedRateLabel(savedRate);
        }

        // ── Zapis imienia ─────────────────────────────────────────────────────
        view.findViewById(R.id.btn_save_name).setOnClickListener(v -> saveName(prefs));

        // ── Zapis stawki ──────────────────────────────────────────────────────
        view.findViewById(R.id.btn_profile_save_rate).setOnClickListener(v -> saveRate(prefs));
    }

    // ── Logika zapisu ─────────────────────────────────────────────────────────

    private void saveName(SharedPreferences prefs) {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(requireContext(), "Wpisz swoje imię", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString(PREF_USER_NAME, name).apply();
        Toast.makeText(requireContext(), "✅ Imię zapisane: " + name, Toast.LENGTH_SHORT).show();
    }

    private void saveRate(SharedPreferences prefs) {
        String rateStr = etHourlyRate.getText() != null
                ? etHourlyRate.getText().toString().trim() : "";
        if (TextUtils.isEmpty(rateStr)) {
            Toast.makeText(requireContext(), "Wpisz stawkę godzinową", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            float rate = Float.parseFloat(rateStr.replace(",", "."));
            if (rate <= 0f) throw new NumberFormatException();

            prefs.edit().putFloat(PREF_HOURLY_RATE, rate).apply();
            viewModel.setHourlyRate(rate);          // natychmiastowy update payrollu
            showSavedRateLabel(rate);
            Toast.makeText(requireContext(),
                    "✅ Stawka zapisana: " + rate + " PLN/h",
                    Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Nieprawidłowa stawka", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSavedRateLabel(float rate) {
        tvSavedRate.setText("Aktualna stawka: " + rate + " PLN/h");
        tvSavedRate.setVisibility(View.VISIBLE);
    }
}
