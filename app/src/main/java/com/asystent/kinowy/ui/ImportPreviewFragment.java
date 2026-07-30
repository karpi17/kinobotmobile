package com.asystent.kinowy.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.parsers.ParserWarning;
import com.asystent.kinowy.parsers.ScheduleParseResult;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/**
 * Fragment podglądu importu grafiku (z Excela, PDF lub OCR).
 * Wyświetla wyciągnięte zmiany, ostrzeżenia parsera i poziom pewności,
 * umożliwiając użytkownikowi zatwierdzenie lub anulowanie zapisu w bazie.
 */
public class ImportPreviewFragment extends Fragment {

    private MainViewModel viewModel;
    private TextView tvImportSourceTitle;
    private TextView tvConfidenceBadge;
    private TextView tvSourceFileName;
    private TextView tvSummaryDetails;
    private MaterialCardView cardWarnings;
    private TextView tvWarningsText;
    private RecyclerView rvPreviewShifts;
    private TextView tvEmptyShiftsNotice;
    private Button btnCancelImport;
    private Button btnConfirmImport;
    private Spinner spinnerRole;

    private ShiftAdapter shiftAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_import_preview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        tvImportSourceTitle = view.findViewById(R.id.tvImportSourceTitle);
        tvConfidenceBadge = view.findViewById(R.id.tvConfidenceBadge);
        tvSourceFileName = view.findViewById(R.id.tvSourceFileName);
        tvSummaryDetails = view.findViewById(R.id.tvSummaryDetails);
        cardWarnings = view.findViewById(R.id.cardWarnings);
        tvWarningsText = view.findViewById(R.id.tvWarningsText);
        rvPreviewShifts = view.findViewById(R.id.rvPreviewShifts);
        tvEmptyShiftsNotice = view.findViewById(R.id.tvEmptyShiftsNotice);
        btnCancelImport = view.findViewById(R.id.btnCancelImport);
        btnConfirmImport = view.findViewById(R.id.btnConfirmImport);
        spinnerRole = view.findViewById(R.id.spinnerRole);

        rvPreviewShifts.setLayoutManager(new LinearLayoutManager(requireContext()));
        shiftAdapter = new ShiftAdapter(shift -> {
            showEditShiftDialog(shift);
        });
        rvPreviewShifts.setAdapter(shiftAdapter);

        // Ustawienie wartości Spinnera z SharedPreferences
        SharedPreferences prefs = requireContext().getSharedPreferences("asystent_kinowy_prefs", Context.MODE_PRIVATE);
        String currentRole = prefs.getString("preferred_role", "Dowolna (Automatycznie)");
        String[] rolesArray = getResources().getStringArray(R.array.roles_array);
        for (int i = 0; i < rolesArray.length; i++) {
            if (rolesArray[i].equals(currentRole)) {
                spinnerRole.setSelection(i);
                break;
            }
        }

        spinnerRole.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString();
                String saved = prefs.getString("preferred_role", "Dowolna (Automatycznie)");
                if (!selected.equals(saved)) {
                    prefs.edit().putString("preferred_role", selected).apply();
                    Toast.makeText(requireContext(), "Zmieniono rolę na: " + selected + ". Zimportuj grafik ponownie, aby zastosować.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        viewModel.getPendingImport().observe(getViewLifecycleOwner(), this::bindParseResult);

        btnCancelImport.setOnClickListener(v -> {
            viewModel.clearPendingImport();
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            }
        });

        btnConfirmImport.setOnClickListener(v -> {
            ScheduleParseResult result = viewModel.getPendingImport().getValue();
            if (result != null) {
                viewModel.commitImport(result, true);
                Toast.makeText(requireContext(), "Zatwierdzono import grafiku!", Toast.LENGTH_SHORT).show();
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                }
            }
        });
    }

    private void bindParseResult(@Nullable ScheduleParseResult result) {
        if (result == null) return;

        tvSourceFileName.setText("Źródło: " + result.getSourceDescription());

        int userShiftCount = result.getTargetUserShifts() != null ? result.getTargetUserShifts().size() : 0;
        int employeesCount = result.getFoundNames() != null ? result.getFoundNames().size() : 0;

        tvSummaryDetails.setText("Znaleziono: " + employeesCount + " pracowników, " + userShiftCount + " Twoich zmian.");

        int confPct = Math.round(result.getConfidence() * 100);
        tvConfidenceBadge.setText(confPct + "% pewności");

        if (result.getWarnings() != null && !result.getWarnings().isEmpty()) {
            cardWarnings.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (ParserWarning warning : result.getWarnings()) {
                sb.append("• ").append(warning.getMessage()).append("\n");
            }
            tvWarningsText.setText(sb.toString().trim());
        } else {
            cardWarnings.setVisibility(View.GONE);
        }

        List<Shift> shifts = result.getTargetUserShifts();
        if (shifts == null || shifts.isEmpty()) {
            // Tryb obserwatora: brak Twoich zmian – pokaż wszystkich pracowników
            List<Shift> allShifts = new java.util.ArrayList<>();
            if (result.getScheduleByName() != null) {
                for (java.util.Map.Entry<String, java.util.List<Shift>> entry : result.getScheduleByName().entrySet()) {
                    String empName = entry.getKey();
                    for (Shift s : entry.getValue()) {
                        // Dodaj imię pracownika do opisu żeby wiedzieć czyja to zmiana
                        if (s.getDescription() == null || s.getDescription().isEmpty()) {
                            s.setDescription(empName);
                        } else if (!s.getDescription().contains(empName)) {
                            s.setDescription(empName + ": " + s.getDescription());
                        }
                        allShifts.add(s);
                    }
                }
                // Posortuj po dacie
                allShifts.sort((a, b) -> {
                    if (a.getDate() == null) return 1;
                    if (b.getDate() == null) return -1;
                    return a.getDate().compareTo(b.getDate());
                });
            }
            if (allShifts.isEmpty()) {
                rvPreviewShifts.setVisibility(View.GONE);
                tvEmptyShiftsNotice.setVisibility(View.VISIBLE);
                tvEmptyShiftsNotice.setText("Nie znaleziono zmian w pliku.");
            } else {
                tvSummaryDetails.setText("Tryb obserwatora: " + result.getFoundNames().size() + " pracowników, " + allShifts.size() + " zmian łącznie.");
                rvPreviewShifts.setVisibility(View.VISIBLE);
                tvEmptyShiftsNotice.setVisibility(View.GONE);
                shiftAdapter.setShifts(allShifts);
            }
        } else {
            rvPreviewShifts.setVisibility(View.VISIBLE);
            tvEmptyShiftsNotice.setVisibility(View.GONE);
            shiftAdapter.setShifts(shifts);
        }
    }

    private void showEditShiftDialog(Shift shift) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_shift, null);

        android.widget.EditText etStart = dialogView.findViewById(R.id.et_shift_start);
        android.widget.EditText etEnd = dialogView.findViewById(R.id.et_shift_end);

        if (etStart != null && etEnd != null) {
            etStart.setText(shift.getStartTime());
            etEnd.setText(shift.getEndTime());
            
            etStart.setFocusable(false);
            etStart.setOnClickListener(v -> {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                new android.app.TimePickerDialog(requireContext(), (view, h, m) -> {
                    etStart.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", h, m));
                }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show();
            });

            etEnd.setFocusable(false);
            etEnd.setOnClickListener(v -> {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                new android.app.TimePickerDialog(requireContext(), (view, h, m) -> {
                    etEnd.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", h, m));
                }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show();
            });
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Edytuj godziny zmiany")
                .setView(dialogView)
                .setPositiveButton("Zapisz", (dialog, which) -> {
                    if (etStart != null && etEnd != null) {
                        shift.setStartTime(etStart.getText().toString());
                        shift.setEndTime(etEnd.getText().toString());
                        shiftAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }
}
