package com.asystent.kinowy.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.GlobalShift;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

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

    /** Okno zamkowe — godziny uznawane za nocną zmianę (zamek) */
    private static final LocalTime CLOSING_START = LocalTime.of(0, 0);
    private static final LocalTime CLOSING_END   = LocalTime.of(5, 0);

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

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

    // ─── Detekcja zamka ──────────────────────────────────────────────────

    /**
     * Sprawdza czy godzina zakończenia wpada w okno zamkowe 00:00–05:00.
     */
    private boolean isClosingTime(String endTime) {
        if (endTime == null || endTime.isEmpty()) return false;
        try {
            LocalTime end = LocalTime.parse(endTime, TIME_FMT);
            return !end.isAfter(CLOSING_END);
        } catch (Exception e) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // REFRESH — odświeżanie sekcji współpracowników (via ShiftUtils)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Pobiera globalny grafik dla podanej daty (w tle),
     * oblicza overlap z [myStart, myEnd] i aktualizuje RecyclerView współpracowników.
     * <p>
     * Deleguje logikę overlap do {@link com.asystent.kinowy.utils.ShiftUtils}.
     * Każdy element jest klikalny — otwiera dialog edycji godzin.
     */
    private void refreshCoworkersSection(
            String date, String myStart, String myEnd,
            LinearLayout sectionLayout, RecyclerView rvCoworkers,
            CoworkerAdapter coworkerAdapter) {

        if (date == null || date.isEmpty()
                || myStart == null || myStart.isEmpty()
                || myEnd == null || myEnd.isEmpty()) {
            sectionLayout.setVisibility(View.GONE);
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            List<GlobalShift> dailyShifts = viewModel.getGlobalShiftsByDate(date);
            List<GlobalShift> overlapping = com.asystent.kinowy.utils.ShiftUtils
                    .getOverlappingShifts(myStart, myEnd, dailyShifts);

            // Aktualizuj UI na main thread
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (overlapping == null || overlapping.isEmpty()) {
                        sectionLayout.setVisibility(View.GONE);
                    } else {
                        sectionLayout.setVisibility(View.VISIBLE);
                        coworkerAdapter.setData(overlapping);
                    }
                });
            }
        });
    }

    /**
     * Pokazuje dialog edycji godzin współpracownika.
     * <p>
     * Umożliwia zmianę godziny startu/końca (ustawia isManuallyEdited = true)
     * lub usunięcie współpracownika z globalnego grafiku.
     */
    private void showCoworkerEditDialog(GlobalShift gs, int position,
                                         CoworkerAdapter adapter,
                                         Runnable refreshAll) {
        View editView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_shift, null); // Reuse time picker approach

        // Tworzymy prosty layout programowo
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);

        TextView tvInfo = new TextView(requireContext());
        tvInfo.setText("👤 " + gs.getName() + "  |  " + (gs.getCategory() != null ? gs.getCategory() : "?"));
        tvInfo.setTextSize(16);
        tvInfo.setTextColor(0xFFFF6600);
        layout.addView(tvInfo);

        // Start time
        com.google.android.material.textfield.TextInputLayout tilStart =
                new com.google.android.material.textfield.TextInputLayout(requireContext(),
                        null, com.google.android.material.R.attr.textInputOutlinedStyle);
        tilStart.setHint("Godzina Start (HH:mm)");
        com.google.android.material.textfield.TextInputEditText etStart =
                new com.google.android.material.textfield.TextInputEditText(tilStart.getContext());
        etStart.setText(gs.getStartTime());
        etStart.setInputType(android.text.InputType.TYPE_CLASS_DATETIME);
        tilStart.addView(etStart);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 24;
        layout.addView(tilStart, lp);

        // End time
        com.google.android.material.textfield.TextInputLayout tilEnd =
                new com.google.android.material.textfield.TextInputLayout(requireContext(),
                        null, com.google.android.material.R.attr.textInputOutlinedStyle);
        tilEnd.setHint("Godzina Koniec (HH:mm)");
        com.google.android.material.textfield.TextInputEditText etEnd =
                new com.google.android.material.textfield.TextInputEditText(tilEnd.getContext());
        etEnd.setText(gs.getEndTime());
        etEnd.setInputType(android.text.InputType.TYPE_CLASS_DATETIME);
        tilEnd.addView(etEnd);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = 16;
        layout.addView(tilEnd, lp2);

        // TimePicker click handlers
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

        new AlertDialog.Builder(requireContext())
                .setTitle("Edytuj zmianę: " + gs.getName())
                .setView(layout)
                .setPositiveButton("Zapisz", (dialog, which) -> {
                    String newStart = etStart.getText() != null ? etStart.getText().toString().trim() : "";
                    String newEnd = etEnd.getText() != null ? etEnd.getText().toString().trim() : "";

                    if (!newStart.isEmpty()) gs.setStartTime(newStart);
                    if (!newEnd.isEmpty()) gs.setEndTime(newEnd);
                    gs.setManuallyEdited(true);

                    viewModel.updateGlobalShift(gs);
                    adapter.updateAt(position, gs);

                    // Pełne odświeżenie po krótkim opóźnieniu (dajemy czas na zapis)
                    rvCoworkersRef.postDelayed(refreshAll::run, 300);
                })
                .setNeutralButton("Usuń ze zmiany", (dialog, which) -> {
                    viewModel.deleteGlobalShift(gs);
                    adapter.removeAt(position);

                    rvCoworkersRef.postDelayed(refreshAll::run, 300);
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }

    // Referencja do RecyclerView coworkerów — set w showShiftDialog
    private RecyclerView rvCoworkersRef;

    // Data aktualnie otwartej zmiany — potrzebna do showAddCoworkerDialog
    private String currentShiftDate;

    /**
     * Dialog dodawania nowego współpracownika do zmiany.
     * <p>
     * Influje {@code dialog_add_coworker.xml}, waliduje imię,
     * tworzy {@link GlobalShift} z {@code isManuallyEdited = true}
     * i wstawia go do bazy Room przez {@link MainViewModel#insertGlobalShift}.
     * <p>
     * Pole imienia korzysta z {@link android.widget.AutoCompleteTextView}
     * zasilanego słownikiem pracowników z {@code global_shifts} (LiveData).
     * <p>
     * Po udanym insercie natychmiast odświeża listę współpracowników.
     *
     * @param shiftDate      data zmiany (yyyy-MM-dd)
     * @param myStart        godzina startu użytkownika (do odświeżenia overlapów)
     * @param myEnd          godzina końca użytkownika
     * @param layoutSection  sekcja współpracowników (do show/hide)
     * @param rvCoworkers    RecyclerView z listą
     * @param cwAdapter      adapter CoworkerAdapter
     */
    private void showAddCoworkerDialog(
            String shiftDate, String myStart, String myEnd,
            LinearLayout layoutSection, RecyclerView rvCoworkers,
            CoworkerAdapter cwAdapter) {

        View addView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_coworker, null);

        // ═════ Pola formularza ═════
        android.widget.AutoCompleteTextView etName = addView.findViewById(R.id.et_new_coworker_name);
        android.widget.EditText etStart = addView.findViewById(R.id.et_new_coworker_start);
        android.widget.EditText etEnd = addView.findViewById(R.id.et_new_coworker_end);
        android.widget.EditText etPosition = addView.findViewById(R.id.et_new_coworker_position);

        // ═════ AutoComplete: słownik współpracowników (LiveData) ═════
        android.widget.ArrayAdapter<String> nameAdapter = new android.widget.ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<String>()
        );
        etName.setAdapter(nameAdapter);
        etName.setThreshold(1);

        // Obserwuj sugestie z ViewModelu (ten sam mechanizm co ekipa zamykająca)
        viewModel.getClosingCrewSuggestions().observe(getViewLifecycleOwner(), suggestions -> {
            if (suggestions != null) {
                nameAdapter.clear();
                nameAdapter.addAll(suggestions);
                nameAdapter.notifyDataSetChanged();
            }
        });

        // ═════ TimePicker na polach godzinowych ═════
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

        // ═════ Dialog Builder ═════
        new AlertDialog.Builder(requireContext())
                .setTitle("➕ Dodaj współpracownika")
                .setView(addView)
                .setPositiveButton("Dodaj", (dialog, which) -> {
                    // ═════ Pobierz dane ═════
                    String name = etName.getText() != null
                            ? etName.getText().toString().trim() : "";
                    String startTime = etStart.getText() != null
                            ? etStart.getText().toString().trim() : "";
                    String endTime = etEnd.getText() != null
                            ? etEnd.getText().toString().trim() : "";
                    String position = etPosition.getText() != null
                            ? etPosition.getText().toString().trim() : "";

                    // ═════ Walidacja ═════
                    if (name.isEmpty()) {
                        android.widget.Toast.makeText(requireContext(),
                                "Podaj imię współpracownika", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Domyślne godziny jeśli puste
                    if (startTime.isEmpty()) startTime = myStart;
                    if (endTime.isEmpty()) endTime = myEnd;

                    // Stanowisko — domyślnie UNKNOWN
                    String category = position.isEmpty() ? "UNKNOWN" : position.toUpperCase();

                    // ═════ Buduj GlobalShift ═════
                    GlobalShift newGs = new GlobalShift(
                            name, shiftDate, startTime, endTime, category);
                    newGs.setManuallyEdited(true); // KRYTYCZNE: ochrona przed parserem

                    // ═════ Async insert + refresh UI ═════
                    viewModel.insertGlobalShift(newGs, () -> {
                        // Callback na main thread — odśwież listę
                        refreshCoworkersSection(
                                shiftDate, myStart, myEnd,
                                layoutSection, rvCoworkers, cwAdapter);

                        android.widget.Toast.makeText(requireContext(),
                                "Dodano: " + name, android.widget.Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton("Anuluj", null)
                .show();
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
        android.widget.AutoCompleteTextView etCategory = dialogView.findViewById(R.id.et_shift_category);
        
        // ═════ Konfiguracja predefiniowanych stanowisk ═════
        String[] defaultCategories = new String[]{"BAR", "OW", "SP"};
        android.widget.ArrayAdapter<String> categoryAdapter = new android.widget.ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                defaultCategories
        );
        etCategory.setAdapter(categoryAdapter);
        etCategory.setThreshold(1);
        TextInputLayout tilClosingCrew = dialogView.findViewById(R.id.til_closing_crew);
        android.widget.MultiAutoCompleteTextView etClosingCrew = dialogView.findViewById(R.id.et_closing_crew);
        SwitchMaterial swReplacement = dialogView.findViewById(R.id.switch_replacement);

        // ═════ Sekcja Budzika ═════
        SwitchMaterial swAlarm = dialogView.findViewById(R.id.switch_alarm);
        TextInputLayout tilAlarmOffset = dialogView.findViewById(R.id.til_alarm_offset);
        android.widget.AutoCompleteTextView etAlarmOffset = dialogView.findViewById(R.id.et_alarm_offset);

        // Predefiniowane opcje wyprzedzenia
        String[] alarmOffsetOptions = new String[]{"30 min", "60 min", "90 min", "120 min"};
        android.widget.ArrayAdapter<String> alarmOffsetAdapter = new android.widget.ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                alarmOffsetOptions
        );
        etAlarmOffset.setAdapter(alarmOffsetAdapter);
        etAlarmOffset.setText("60 min", false); // Domyślnie 60 min

        // Logika widoczności: pole minut widoczne tylko gdy Switch ON
        swAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tilAlarmOffset.setVisibility(isChecked ? View.VISIBLE : View.GONE);

            // Android 14+ — sprawdź uprawnienie USE_FULL_SCREEN_INTENT
            if (isChecked && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.app.NotificationManager nm = (android.app.NotificationManager)
                        requireContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
                if (nm != null && !nm.canUseFullScreenIntent()) {
                     com.google.android.material.snackbar.Snackbar
                            .make(dialogView, "Wymagane uprawnienie do budzika pełnoekranowego",
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                            .setAction("Ustawienia", v -> {
                                android.content.Intent settingsIntent = new android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                        android.net.Uri.parse("package:" + requireContext().getPackageName()));
                                startActivity(settingsIntent);
                            })
                            .show();
                }
            }
        });

        // Sekcja współpracowników (Overlap — edytowalna lista)
        LinearLayout layoutCoworkers = dialogView.findViewById(R.id.layout_coworkers_section);
        RecyclerView rvCoworkers = dialogView.findViewById(R.id.rv_coworkers_list);
        final CoworkerAdapter[] adapterHolder = new CoworkerAdapter[1];
        CoworkerAdapter coworkerAdapter = new CoworkerAdapter((gs, pos) -> {
            showCoworkerEditDialog(gs, pos, adapterHolder[0], () -> {
                // Pełne odświeżenie po edycji/usunięciu
                String d2 = etDate.getText() != null ? etDate.getText().toString().trim() : "";
                String s2 = etStart.getText() != null ? etStart.getText().toString().trim() : "";
                String e2 = etEnd.getText() != null ? etEnd.getText().toString().trim() : "";
                refreshCoworkersSection(d2, s2, e2, layoutCoworkers, rvCoworkers, adapterHolder[0]);
            });
        });
        adapterHolder[0] = coworkerAdapter;
        rvCoworkers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCoworkers.setAdapter(coworkerAdapter);
        rvCoworkersRef = rvCoworkers;

        // ═════ Przycisk: Dodaj współpracownika ═════
        android.widget.Button btnAddCoworker = dialogView.findViewById(R.id.btn_add_coworker);
        btnAddCoworker.setOnClickListener(v -> {
            String date = etDate.getText() != null ? etDate.getText().toString().trim() : "";
            String start = etStart.getText() != null ? etStart.getText().toString().trim() : "";
            String end = etEnd.getText() != null ? etEnd.getText().toString().trim() : "";

            if (date.isEmpty()) {
                android.widget.Toast.makeText(requireContext(),
                        "Najpierw wybierz datę zmiany", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            showAddCoworkerDialog(date, start, end,
                    layoutCoworkers, rvCoworkers, coworkerAdapter);
        });

        // ═════ Setup MultiAutoComplete z CommaTokenizer ═════
        etClosingCrew.setTokenizer(new android.widget.MultiAutoCompleteTextView.CommaTokenizer());
        android.widget.ArrayAdapter<String> crewAdapter = new android.widget.ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<String>()
        );
        etClosingCrew.setAdapter(crewAdapter);

        // Obserwuj sugestie z ViewModelu i aktualizuj adapter
        viewModel.getClosingCrewSuggestions().observe(getViewLifecycleOwner(), suggestions -> {
            if (suggestions != null) {
                crewAdapter.clear();
                crewAdapter.addAll(suggestions);
                crewAdapter.notifyDataSetChanged();
            }
        });

        // ═════ Helper lambda: odśwież współpracowników ═════
        Runnable refreshCoworkers = () -> {
            String d = etDate.getText() != null ? etDate.getText().toString().trim() : "";
            String s = etStart.getText() != null ? etStart.getText().toString().trim() : "";
            String e = etEnd.getText() != null ? etEnd.getText().toString().trim() : "";
            refreshCoworkersSection(d, s, e, layoutCoworkers, rvCoworkers, coworkerAdapter);
        };

        // 1. Data — z auto-refresh współpracowników
        etDate.setFocusable(false);
        etDate.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            new android.app.DatePickerDialog(requireContext(), (view, year, month, dayOfMonth) -> {
                etDate.setText(String.format(java.util.Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth));
                refreshCoworkers.run();
            }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });

        // 2. Godzina Startu — z auto-refresh współpracowników
        etStart.setFocusable(false);
        etStart.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            new android.app.TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
                etStart.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
                refreshCoworkers.run();
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show();
        });

        // 3. Godzina Końca — z auto-detekcją zamka + refresh
        etEnd.setFocusable(false);
        etEnd.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            new android.app.TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
                String endStr = String.format(java.util.Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                etEnd.setText(endStr);

                // ═════ Auto-detekcja zamka ═════
                if (isClosingTime(endStr)) {
                    tilClosingCrew.setVisibility(View.VISIBLE);
                } else {
                    tilClosingCrew.setVisibility(View.GONE);
                    etClosingCrew.setText("");
                }

                refreshCoworkers.run();
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true).show();
        });

        // ═════════════════════════════════════════════
        // Pre-fill
        if (isEdit) {
            etDate.setText(shift.getDate());
            etStart.setText(shift.getStartTime());
            etEnd.setText(shift.getEndTime());
            etCategory.setText(shift.getCategory() != null && !shift.getCategory().equals("UNKNOWN") ? shift.getCategory() : "");
            swReplacement.setChecked(shift.isReplacement());

            // Pre-fill closing crew
            if (shift.isClosingShift()) {
                tilClosingCrew.setVisibility(View.VISIBLE);
                if (shift.getClosingCrew() != null) {
                    etClosingCrew.setText(shift.getClosingCrew());
                }
            }

            // ═════ Pre-fill: Stan alarmu z bazy (async) ═════
            if (shift.getDate() != null && shift.getStartTime() != null) {
                Executors.newSingleThreadExecutor().execute(() -> {
                    GlobalShift alarmState = viewModel.getAlarmStateForShift(
                            shift.getDate(), shift.getStartTime());
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (alarmState != null && alarmState.isHasAlarm()) {
                                swAlarm.setChecked(true);
                                tilAlarmOffset.setVisibility(View.VISIBLE);
                                etAlarmOffset.setText(alarmState.getAlarmOffsetMinutes() + " min", false);
                            }
                        });
                    }
                });
            }

            // Auto-load współpracowników dla istniejącej zmiany
            refreshCoworkers.run();
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
                    String cat = etCategory.getText() != null ? etCategory.getText().toString().trim() : "";
                    String crew = etClosingCrew.getText() != null ? etClosingCrew.getText().toString().trim() : "";
                    boolean isRep = swReplacement.isChecked();

                    if (date.isEmpty()) return;

                    // Fix formatowania godzin (z 0:00 na 00:00)
                    if (start.length() == 4 && start.charAt(1) == ':') start = "0" + start;
                    if (end.length() == 4 && end.charAt(1) == ':') end = "0" + end;

                    // Auto-detekcja zamka
                    boolean closing = isClosingTime(end);

                    if (isEdit) {
                        shift.setDate(date);
                        shift.setStartTime(start);
                        shift.setEndTime(end);
                        shift.setCategory(cat.isEmpty() ? "UNKNOWN" : cat.toUpperCase());
                        shift.setReplacement(isRep);
                        shift.setManual(true);
                        shift.setClosingShift(closing);
                        shift.setClosingCrew(closing && !crew.isEmpty() ? crew : null);
                        viewModel.updateShift(shift);
                    } else {
                        Shift newShift = new Shift(date, start, end, "", true, isRep, cat.isEmpty() ? "UNKNOWN" : cat.toUpperCase());
                        newShift.setManual(true);
                        newShift.setClosingShift(closing);
                        newShift.setClosingCrew(closing && !crew.isEmpty() ? crew : null);
                        viewModel.insertShift(newShift);
                    }

                    // ═════ Zapis stanu alarmu ═════
                    boolean alarmEnabled = swAlarm.isChecked();
                    if (!start.isEmpty()) {
                        int offsetMinutes = 60; // domyślnie
                        String offsetText = etAlarmOffset.getText() != null
                                ? etAlarmOffset.getText().toString().trim() : "";
                        if (!offsetText.isEmpty()) {
                            try {
                                offsetMinutes = Integer.parseInt(offsetText.replaceAll("[^0-9]", ""));
                            } catch (NumberFormatException ignored) { }
                        }
                        viewModel.toggleAlarmForShift(date, start, alarmEnabled, offsetMinutes);
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
