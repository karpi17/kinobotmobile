package com.asystent.kinowy.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Fragment z widokiem grafiku pracy.
 * <p>
 * Wyświetla listę zmian (shifts) w RecyclerView,
 * pobieranych z bazy Room przez {@link MainViewModel}.
 */
public class ScheduleFragment extends Fragment implements ShiftAdapter.OnShiftClickListener {

    private MainViewModel viewModel;
    private ShiftAdapter adapter;
    private RecyclerView rvShifts;
    private TextView tvEmpty;

    // -- LAUNCHER --
    private final androidx.activity.result.ActivityResultLauncher<String> requestCalendarPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    exportCurrentShifts();
                } else {
                    android.widget.Toast.makeText(requireContext(), "Brak uprawnień do kalendarza", android.widget.Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_schedule, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // --- ViewModel ---
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // --- UI ---
        rvShifts = view.findViewById(R.id.rv_shifts);
        tvEmpty = view.findViewById(R.id.tv_schedule_empty);

        // --- Obsługa Wybieraka Miesiąca ---
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

        // --- RecyclerView setup ---
        adapter = new ShiftAdapter(this);
        rvShifts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvShifts.setAdapter(adapter);

        // --- Przyciski ---
        FloatingActionButton fabAdd = view.findViewById(R.id.fab_add_shift);
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> showShiftDialog(null));
        }

        com.google.android.material.button.MaterialButton btnExport = view.findViewById(R.id.btn_export_calendar);
        if (btnExport != null) {
            btnExport.setOnClickListener(v -> handleExportClick());
        }

        // --- Obserwuj dane ---
        viewModel.getMonthlyShifts().observe(getViewLifecycleOwner(), shifts -> {
            if (shifts == null || shifts.isEmpty()) {
                rvShifts.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
            } else {
                rvShifts.setVisibility(View.VISIBLE);
                tvEmpty.setVisibility(View.GONE);
                adapter.setShifts(shifts);
            }
        });
    }

    @Override
    public void onShiftClick(Shift shift) {
        showShiftDialog(shift);
    }

    private void handleExportClick() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.WRITE_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            exportCurrentShifts();
        } else {
            requestCalendarPermissionLauncher.launch(android.Manifest.permission.WRITE_CALENDAR);
        }
    }

    private void exportCurrentShifts() {
        java.util.List<Shift> currentShifts = viewModel.getMonthlyShifts().getValue();
        if (currentShifts != null && !currentShifts.isEmpty()) {
            viewModel.exportToCalendar(requireContext(), currentShifts);
            android.widget.Toast.makeText(requireContext(), "Eksportowano do kalendarza!", android.widget.Toast.LENGTH_SHORT).show();
        } else {
            android.widget.Toast.makeText(requireContext(), "Brak zmian do eksportu", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Pokazuje dialog dodawania lub edycji zmiany.
     * @param shift Zmiana do edycji, lub null jeśli dodajemy nową.
     */
    private void showShiftDialog(@Nullable Shift shift) {
        boolean isEdit = shift != null;

        // Inflate custom view for dialog
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_shift, null);
        TextInputEditText etDate = dialogView.findViewById(R.id.et_shift_date);
        TextInputEditText etStart = dialogView.findViewById(R.id.et_shift_start);
        TextInputEditText etEnd = dialogView.findViewById(R.id.et_shift_end);
        TextInputEditText etDesc = dialogView.findViewById(R.id.et_shift_desc);
        com.google.android.material.switchmaterial.SwitchMaterial swReplacement = dialogView.findViewById(R.id.switch_replacement);
        // 1. Data
        etDate.setFocusable(false); // Blokuje klawiaturę
        etDate.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            new android.app.DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
                // Miesiące w Javie są indeksowane od 0 (Styczeń to 0), więc dodajemy +1
                etDate.setText(String.format(java.util.Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth));
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });

        // 2. Godzina Startu
        etStart.setFocusable(false);
        etStart.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            // Ostatni parametr 'true' oznacza format 24-godzinny (bez AM/PM)
            new android.app.TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
                etStart.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show();
        });

        // 3. Godzina Końca
        etEnd.setFocusable(false);
        etEnd.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            new android.app.TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
                etEnd.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show();
        });

        // ═════════════════════════════════════════════
        // Pre-fill
        if (isEdit) {
            etDate.setText(shift.getDate());
            etStart.setText(shift.getStartTime());
            etEnd.setText(shift.getEndTime());
            etDesc.setText(shift.getDescription());
            swReplacement.setChecked(shift.isReplacement());
        } else {
            // Default to today
            etDate.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(isEdit ? "Edytuj zmianę" : "Dodaj zmianę")
                .setView(dialogView)
                .setPositiveButton("Zapisz", (dialog, which) -> {
                    String date = etDate.getText() != null ? etDate.getText().toString().trim() : "";
                    String start = etStart.getText() != null ? etStart.getText().toString().trim() : "";
                    String end = etEnd.getText() != null ? etEnd.getText().toString().trim() : "";
                    String desc = etDesc.getText() != null ? etDesc.getText().toString().trim() : "";
                    boolean isRep = swReplacement.isChecked();

                    if (date.isEmpty()) return; // Zabezpieczenie przed pustą datą

                    // Fix formatowania godzin (z 0:00 na 00:00)
                    if (start.length() == 4 && start.charAt(1) == ':') start = "0" + start;
                    if (end.length() == 4 && end.charAt(1) == ':') end = "0" + end;

                    if (isEdit) {
                        // 1. EDYCJA ISTNIEJĄCEJ ZMIANY
                        shift.setDate(date);
                        shift.setStartTime(start);
                        shift.setEndTime(end);
                        shift.setDescription(desc);
                        shift.setReplacement(isRep);
                        shift.setManual(true); // Oznaczamy jako modyfikację ręczną
                        viewModel.updateShift(shift);
                    } else {
                        // 2. TWORZENIE NOWEJ ZMIANY (tutaj zmienna 'shift' to null, więc używamy 'newShift'!)
                        Shift newShift = new Shift(date, start, end, desc, true, isRep);
                        newShift.setManual(true); // Oznaczamy jako wpis ręczny
                        viewModel.insertShift(newShift);
                    }
                })
                .setNegativeButton("Anuluj", null);

        if (isEdit) {
            builder.setNeutralButton("Usuń", (dialog, which) -> {
                viewModel.deleteShift(shift);
            });
        }

        builder.show();
    }
}
