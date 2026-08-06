package com.asystent.kinowy.ui;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.asystent.kinowy.R;
import com.asystent.kinowy.utils.BackupManager;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.asystent.kinowy.widget.ShiftStackWidgetProvider;
import com.asystent.kinowy.widget.ShiftWidgetProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Ekran Profilu — centralne miejsce ustawień użytkownika oraz silnik bezpiecznych zrzutów archiwizacyjnych z dysku i chmur.
 */
public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";

    // Klucze SharedPreferences — identyczne jak w DashboardFragment i FinanceFragment
    private static final String PREFS_NAME         = "asystent_kinowy_prefs";
    private static final String PREF_USER_NAME     = "user_name";
    private static final String PREF_HOURLY_RATE   = "hourly_rate";
    private static final String PREF_MONTHLY_GOAL  = "monthly_hours_goal";
    private static final String PREF_NOTIFY_BEFORE = "notify_before_minutes";

    private MainViewModel viewModel;

    private TextInputEditText etName;
    private TextInputEditText etHourlyRate;
    private TextView          tvSavedRate;
    private TextInputEditText etGoalHours;
    private TextInputEditText etNotifyMinutes;

    private String currentAppVersionName = "v2.9";

    // Rejestratory do bezproblemowej obsługi plików bez potrzeby dręczenia o osobne uprawnienia do pamięci (SAF)
    private final ActivityResultLauncher<String> createBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri != null) {
                    exportBackupToUri(uri);
                }
            });

    private final ActivityResultLauncher<String[]> openBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importBackupFromUri(uri);
                }
            });

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

        etName          = view.findViewById(R.id.et_profile_name);
        etHourlyRate    = view.findViewById(R.id.et_profile_hourly_rate);
        tvSavedRate     = view.findViewById(R.id.tv_profile_saved_rate);
        etGoalHours     = view.findViewById(R.id.et_profile_goal_hours);
        etNotifyMinutes = view.findViewById(R.id.et_profile_notify_minutes);

        // ── Wersja aplikacji ──────────────────────────────────────────────────
        TextView tvVersion = view.findViewById(R.id.tv_profile_version);
        try {
            currentAppVersionName = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        tvVersion.setText("KinoBot v" + currentAppVersionName);

        // ── Wczytaj zapisane wartości ─────────────────────────────────────────
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);

        String savedName = prefs.getString(PREF_USER_NAME, "");
        if (!TextUtils.isEmpty(savedName)) {
            etName.setText(savedName);
        }

        float savedRate = prefs.getFloat(PREF_HOURLY_RATE, 0f);
        viewModel.setHourlyRate(savedRate);
        if (savedRate > 0f) {
            etHourlyRate.setText(String.valueOf(savedRate));
            showSavedRateLabel(savedRate);
        }

        view.findViewById(R.id.btn_save_name).setOnClickListener(v -> saveName(prefs));

        int savedGoal = prefs.getInt(PREF_MONTHLY_GOAL, 100);
        etGoalHours.setText(String.valueOf(savedGoal));
        viewModel.getMonthlyHoursGoal().setValue(savedGoal);
        view.findViewById(R.id.btn_profile_save_goal).setOnClickListener(v -> saveGoal(prefs));

        int savedNotify = prefs.getInt(PREF_NOTIFY_BEFORE, 30);
        etNotifyMinutes.setText(String.valueOf(savedNotify));
        view.findViewById(R.id.btn_profile_save_notify).setOnClickListener(v -> saveNotify(prefs));

        view.findViewById(R.id.btn_profile_save_rate).setOnClickListener(v -> saveRate(prefs));

        // ── Kopie zapasowe (JSON) ─────────────────────────────────────────────
        view.findViewById(R.id.btn_export_backup).setOnClickListener(v -> {
            String defaultFileName = "kinobot_backup_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".json";
            createBackupLauncher.launch(defaultFileName);
        });

        view.findViewById(R.id.btn_import_backup).setOnClickListener(v -> {
            // Wsparcie dla plikowych przeglądarek z dysku lokalnego oraz widoków z Dysku Google
            openBackupLauncher.launch(new String[]{"application/json", "*/*"});
        });
    }

    private void exportBackupToUri(Uri uri) {
        Toast.makeText(requireContext(), "⌛ Trwa tworzenie zrzutu JSON...", Toast.LENGTH_SHORT).show();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                BackupManager manager = new BackupManager(requireContext());
                String jsonContent = manager.createBackupJsonSync(currentAppVersionName);

                try (OutputStream os = requireContext().getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        os.write(jsonContent.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }
                }
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> 
                        Toast.makeText(requireContext(), "🎉 Kopia zapasowa bezpiecznie zapisana!", Toast.LENGTH_LONG).show()
                    );
                }
            } catch (Exception e) {
                Log.e(TAG, "Awaria eksportu do pliku z powziętą kopią: " + e.getMessage(), e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> 
                        Toast.makeText(requireContext(), "❌ Błąd zapisu pliku: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }
        });
    }

    private void importBackupFromUri(Uri uri) {
        Toast.makeText(requireContext(), "⌛ Trwa analiza pliku i odsiew duplikatów...", Toast.LENGTH_SHORT).show();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }

                BackupManager manager = new BackupManager(requireContext());
                BackupManager.RestoreSummary summary = manager.restoreBackupFromJsonSync(sb.toString());

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(summary.isSuccess() ? "Raport ze Zrzutu Kopii" : "Awaria Importu")
                                .setMessage(summary.toUserFriendlySummary())
                                .setPositiveButton("Gotowe!", (d, w) -> d.dismiss())
                                .setCancelable(false)
                                .show();

                        if (summary.isSuccess()) {
                            // Natychmiastowe obudzenie i przestylizowanie żyjących widgetów z ekranu powtarzalnego
                            ShiftWidgetProvider.triggerUpdate(requireContext());
                            ShiftStackWidgetProvider.triggerUpdate(requireContext());
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Koszmar przy wybudzaniu archiwum JSON ze strumienia: " + e.getMessage(), e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> 
                        Toast.makeText(requireContext(), "❌ Awaria odczytu archiwum z wyboru: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }
        });
    }

    // ── Logika zapisu ─────────────────────────────────────────────────────────

    private void saveName(SharedPreferences prefs) {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(requireContext(), "Wpisz swoje imię", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString(PREF_USER_NAME, name).apply();
        viewModel.setTargetUserName(name); // natychmiastowa synchronizacja z ViewModel
        ShiftWidgetProvider.triggerUpdate(requireContext());
        ShiftStackWidgetProvider.triggerUpdate(requireContext());
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
            viewModel.setHourlyRate(rate);
            showSavedRateLabel(rate);
            Toast.makeText(requireContext(),
                    "✅ Stawka zapisana: " + rate + " PLN/h",
                    Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Nieprawidłowa stawka", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveGoal(SharedPreferences prefs) {
        String s = etGoalHours.getText() != null ? etGoalHours.getText().toString().trim() : "";
        if (TextUtils.isEmpty(s)) {
            Toast.makeText(requireContext(), "Wpisz cel godzinowy", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            int goal = Integer.parseInt(s);
            if (goal <= 0) throw new NumberFormatException();
            prefs.edit().putInt(PREF_MONTHLY_GOAL, goal).apply();
            viewModel.getMonthlyHoursGoal().setValue(goal);
            Toast.makeText(requireContext(),
                    "✅ Cel: " + goal + "h/miesiąc", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Nieprawidłowa wartość", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveNotify(SharedPreferences prefs) {
        String s = etNotifyMinutes.getText() != null ? etNotifyMinutes.getText().toString().trim() : "";
        if (TextUtils.isEmpty(s)) {
            Toast.makeText(requireContext(), "Wpisz liczbę minut", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            int minutes = Integer.parseInt(s);
            if (minutes < 0) throw new NumberFormatException();
            prefs.edit().putInt(PREF_NOTIFY_BEFORE, minutes).apply();
            Toast.makeText(requireContext(),
                    "✅ Budzik: " + minutes + " min przed zmianą", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Nieprawidłowa wartość", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSavedRateLabel(float rate) {
        tvSavedRate.setText("Aktualna stawka: " + rate + " PLN/h");
        tvSavedRate.setVisibility(View.VISIBLE);
    }
}
