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
    private androidx.appcompat.app.AlertDialog currentShiftDialog;

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

        com.google.android.material.button.MaterialButton btnManagerDayView = view.findViewById(R.id.btn_manager_day_view);
        if (btnManagerDayView != null) {
            btnManagerDayView.setOnClickListener(v -> showManagerDayPicker());
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

    private void showManagerDayPicker() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        java.time.YearMonth selectedYm = viewModel.getCurrentSelectedMonth().getValue();
        int year = selectedYm != null ? selectedYm.getYear() : cal.get(java.util.Calendar.YEAR);
        int month = selectedYm != null ? selectedYm.getMonthValue() - 1 : cal.get(java.util.Calendar.MONTH);
        int day = cal.get(java.util.Calendar.DAY_OF_MONTH);
        new android.app.DatePickerDialog(requireContext(), (view, y, m, d) -> {
            String dateStr = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            showFullDayCrewDialog(dateStr);
        }, year, month, Math.min(day, 28)).show();
    }

    private void showFullDayCrewDialog(String dateStr) {
        if (!isAdded()) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            List<GlobalShift> dailyShifts = viewModel.getGlobalShiftsByDate(dateStr);
            if (!isAdded() || getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                LinearLayout container = new LinearLayout(requireContext());
                container.setOrientation(LinearLayout.VERTICAL);
                container.setPadding(32, 24, 32, 16);

                RecyclerView rv = new RecyclerView(requireContext());
                rv.setLayoutManager(new LinearLayoutManager(requireContext()));
                final CoworkerAdapter[] cwHolder = new CoworkerAdapter[1];
                CoworkerAdapter cwAdapter = new CoworkerAdapter((gs, pos) -> {
                    showCoworkerEditDialog(gs, pos, cwHolder[0], rv, () -> {
                        Executors.newSingleThreadExecutor().execute(() -> {
                            List<GlobalShift> refreshed = viewModel.getGlobalShiftsByDate(dateStr);
                            if (isAdded() && getActivity() != null) {
                                getActivity().runOnUiThread(() -> cwHolder[0].setData(refreshed, refreshed));
                            }
                        });
                    });
                });
                cwHolder[0] = cwAdapter;
                rv.setAdapter(cwAdapter);
                cwAdapter.setData(dailyShifts, dailyShifts);

                if (dailyShifts == null || dailyShifts.isEmpty()) {
                    TextView tvNone = new TextView(requireContext());
                    tvNone.setText("Brak zapisanej obsady w globalnym grafiku na dzień " + dateStr + ".");
                    tvNone.setTextColor(0xFFAAAAAA);
                    tvNone.setPadding(8, 16, 8, 16);
                    container.addView(tvNone);
                }
                container.addView(rv, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 650));

                new AlertDialog.Builder(requireContext())
                        .setTitle("👥 Obsada dnia: " + dateStr)
                        .setView(container)
                        .setNeutralButton("+ Dodaj osobę", (d, w) -> {
                            showAddCoworkerDialog(dateStr, "09:00", "17:00", container, rv, cwAdapter, true);
                        })
                        .setPositiveButton("Zamknij", null)
                        .show();
            });
        });
    }

    private void showReplacementCandidatesDialog(String dateStr, String startStr, String endStr, String category) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "Najpierw wybierz datę zmiany", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isAdded()) return;
        final android.content.Context ctx = requireContext().getApplicationContext();
        final List<String> suggestionsSnapshot = viewModel.getClosingCrewSuggestions().getValue();

        Executors.newSingleThreadExecutor().execute(() -> {
            List<GlobalShift> allGlobal = viewModel.getAllGlobalShiftsSync();
            String myName = ctx.getSharedPreferences("asystent_kinowy_prefs", android.content.Context.MODE_PRIVATE)
                    .getString("user_name", "");
            List<com.asystent.kinowy.utils.ReplacementFinder.Candidate> candidates =
                    com.asystent.kinowy.utils.ReplacementFinder.findCandidates(
                            dateStr, startStr, endStr, myName, suggestionsSnapshot, allGlobal);
            String messengerText = com.asystent.kinowy.utils.ReplacementFinder.buildMessengerMessage(
                    dateStr, startStr, endStr, category);

            if (!isAdded() || getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                StringBuilder sb = new StringBuilder();
                if (candidates.isEmpty()) {
                    sb.append("Brak dostępnych kandydatów w bazie (wszyscy pracują lub mieliby naruszenie 11h odpoczynku BHP).\n\n");
                } else {
                    sb.append("Osoby mające wolne i bezpieczną przerwę BHP (≥11h):\n\n");
                    for (com.asystent.kinowy.utils.ReplacementFinder.Candidate c : candidates) {
                        sb.append("✅ ").append(c.getName())
                          .append(" — ").append(c.getNote()).append("\n");
                    }
                    sb.append("\n");
                }
                sb.append("────────────────────\n");
                sb.append("Gotowa wiadomość na Messenger:\n").append(messengerText);

                new AlertDialog.Builder(requireContext())
                        .setTitle("🔍 Kto może wziąć zmianę? (" + dateStr + ")")
                        .setMessage(sb.toString().trim())
                        .setPositiveButton("📋 Kopiuj wiadomość", (d, w) -> {
                            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                                    requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                android.content.ClipData clip = android.content.ClipData.newPlainText(
                                        "Zastępstwo KinoBot", messengerText);
                                clipboard.setPrimaryClip(clip);
                                android.widget.Toast.makeText(requireContext(),
                                        "Skopiowano wiadomość do schowka!", android.widget.Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Zamknij", null)
                        .show();
            });
        });
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

    private void refreshCoworkersSection(
            String date, String myStart, String myEnd,
            LinearLayout sectionLayout, RecyclerView rvCoworkers,
            CoworkerAdapter coworkerAdapter) {
        refreshCoworkersSection(date, myStart, myEnd, sectionLayout, rvCoworkers, coworkerAdapter, false);
    }

    private void refreshCoworkersSection(
            String date, String myStart, String myEnd,
            LinearLayout sectionLayout, RecyclerView rvCoworkers,
            CoworkerAdapter coworkerAdapter, boolean forceFullDay) {

        if (date == null || date.isEmpty()) {
            sectionLayout.setVisibility(View.GONE);
            return;
        }

        if (!isAdded()) return;
        final android.content.Context ctx = requireContext().getApplicationContext();
        final boolean hasMyHours = myStart != null && !myStart.trim().isEmpty()
                && myEnd != null && !myEnd.trim().isEmpty();
        final boolean fullDayMode = forceFullDay || !hasMyHours;

        Executors.newSingleThreadExecutor().execute(() -> {
            List<GlobalShift> dailyShifts = viewModel.getGlobalShiftsByDate(date);
            List<GlobalShift> displayList;

            if (fullDayMode) {
                displayList = new ArrayList<>();
                if (dailyShifts != null) {
                    for (GlobalShift gs : dailyShifts) {
                        if (gs != null && !gs.isDeleted()
                                && gs.getStartTime() != null && !gs.getStartTime().trim().isEmpty()) {
                            displayList.add(gs);
                        }
                    }
                }
            } else {
                List<GlobalShift> overlapping = com.asystent.kinowy.utils.ShiftUtils
                        .getOverlappingShifts(myStart, myEnd, dailyShifts);
                String myName = ctx
                        .getSharedPreferences("asystent_kinowy_prefs",
                                android.content.Context.MODE_PRIVATE)
                        .getString("user_name", "").trim();
                displayList = new ArrayList<>();
                if (overlapping != null) {
                    for (GlobalShift gs : overlapping) {
                        if (myName.isEmpty() || !myName.equalsIgnoreCase(
                                gs.getName() != null ? gs.getName().trim() : "")) {
                            displayList.add(gs);
                        }
                    }
                }
            }

            final List<GlobalShift> finalList = displayList;
            if (isAdded() && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    sectionLayout.setVisibility(View.VISIBLE);
                    TextView tvTitle = sectionLayout.findViewById(R.id.tv_coworkers_section_title);
                    if (tvTitle != null) {
                        tvTitle.setText(fullDayMode
                                ? "👥 Pełna obsada dnia (" + finalList.size() + ")"
                                : "👥 Współpracownicy w tym czasie (" + finalList.size() + ")");
                    }
                    coworkerAdapter.setData(finalList, dailyShifts);
                });
            }
        });
    }


    /**
     * Pokazuje dialog edycji godzin współpracownika.
     */
    private void showCoworkerEditDialog(GlobalShift gs, int position,
                                         CoworkerAdapter adapter,
                                         RecyclerView rvCoworkers,
                                         Runnable refreshAll) {
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

                    rvCoworkers.postDelayed(refreshAll::run, 300);
                })
                .setNeutralButton("Usuń ze zmiany", (dialog, which) -> {
                    viewModel.deleteGlobalShift(gs);
                    adapter.removeAt(position);

                    rvCoworkers.postDelayed(refreshAll::run, 300);
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }

    private String currentShiftDate;

    private void showAddCoworkerDialog(
            String shiftDate, String myStart, String myEnd,
            LinearLayout layoutSection, RecyclerView rvCoworkers,
            CoworkerAdapter cwAdapter) {
        showAddCoworkerDialog(shiftDate, myStart, myEnd, layoutSection, rvCoworkers, cwAdapter, false);
    }

    private void showAddCoworkerDialog(
            String shiftDate, String myStart, String myEnd,
            LinearLayout layoutSection, RecyclerView rvCoworkers,
            CoworkerAdapter cwAdapter, boolean forceFullDay) {

        View addView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_coworker, null);

        android.widget.AutoCompleteTextView etName = addView.findViewById(R.id.et_new_coworker_name);
        android.widget.EditText etStart = addView.findViewById(R.id.et_new_coworker_start);
        android.widget.EditText etEnd = addView.findViewById(R.id.et_new_coworker_end);
        android.widget.EditText etPosition = addView.findViewById(R.id.et_new_coworker_position);

        android.widget.ArrayAdapter<String> nameAdapter = new android.widget.ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<String>()
        );
        etName.setAdapter(nameAdapter);
        etName.setThreshold(1);

        viewModel.getClosingCrewSuggestions().observe(getViewLifecycleOwner(), suggestions -> {
            if (suggestions != null) {
                nameAdapter.clear();
                nameAdapter.addAll(suggestions);
                nameAdapter.notifyDataSetChanged();
            }
        });

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
                .setTitle("➕ Dodaj współpracownika")
                .setView(addView)
                .setPositiveButton("Dodaj", (dialog, which) -> {
                    String name = etName.getText() != null
                            ? etName.getText().toString().trim() : "";
                    String startTime = etStart.getText() != null
                            ? etStart.getText().toString().trim() : "";
                    String endTime = etEnd.getText() != null
                            ? etEnd.getText().toString().trim() : "";
                    String position = etPosition.getText() != null
                            ? etPosition.getText().toString().trim() : "";

                    if (name.isEmpty()) {
                        android.widget.Toast.makeText(requireContext(),
                                "Podaj imię współpracownika", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!startTime.isEmpty() && !startTime.matches("\\d{2}:\\d{2}")) {
                        android.widget.Toast.makeText(requireContext(),
                                "Zły format godziny startu (np. 08:00)", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!endTime.isEmpty() && !endTime.matches("\\d{2}:\\d{2}")) {
                        android.widget.Toast.makeText(requireContext(),
                                "Zły format godziny końca (np. 22:00)", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (startTime.isEmpty()) startTime = (myStart != null && !myStart.isEmpty()) ? myStart : "09:00";
                    if (endTime.isEmpty()) endTime = (myEnd != null && !myEnd.isEmpty()) ? myEnd : "17:00";

                    String category = position.isEmpty() ? "UNKNOWN" : position.toUpperCase();

                    GlobalShift newGs = new GlobalShift(
                            name, shiftDate, startTime, endTime, category);
                    newGs.setManuallyEdited(true);

                    final String effMyStart = startTime;
                    final String effMyEnd = endTime;
                    viewModel.insertGlobalShift(newGs, (rowId) -> {
                        if (rowId == -1L) {
                            android.widget.Toast.makeText(requireContext(),
                                     name + " jest już na tej zmianie", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        refreshCoworkersSection(
                                shiftDate,
                                (myStart != null && !myStart.isEmpty()) ? myStart : effMyStart,
                                (myEnd != null && !myEnd.isEmpty()) ? myEnd : effMyEnd,
                                layoutSection, rvCoworkers, cwAdapter, forceFullDay);
                        android.widget.Toast.makeText(requireContext(),
                                "Dodano: " + name, android.widget.Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }

    public void openShiftDialog(@Nullable Shift shift) {
        showShiftDialog(shift);
    }

    private void showShiftDialog(@Nullable Shift shift) {
        boolean isEdit = shift != null;
        final String originalDate = (isEdit && shift.getDate() != null) ? shift.getDate() : "";
        final String originalStartTime = (isEdit && shift.getStartTime() != null) ? shift.getStartTime() : "";

        // Inflate custom view for dialog
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_shift, null);
        TextInputEditText etDate = dialogView.findViewById(R.id.et_shift_date);
        TextInputEditText etStart = dialogView.findViewById(R.id.et_shift_start);
        TextInputEditText etEnd = dialogView.findViewById(R.id.et_shift_end);
        android.widget.AutoCompleteTextView etCategory = dialogView.findViewById(R.id.et_shift_category);
        
        String[] defaultCategories = new String[]{"BAR", "OW", "SP", "ZAMEK", "OBSŁUGA"};
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
        com.google.android.material.button.MaterialButton btnFindReplacement =
                dialogView.findViewById(R.id.btn_find_replacement);

        if (btnFindReplacement != null) {
            btnFindReplacement.setOnClickListener(v -> {
                String d = etDate.getText() != null ? etDate.getText().toString().trim() : "";
                String s = etStart.getText() != null ? etStart.getText().toString().trim() : "";
                String e = etEnd.getText() != null ? etEnd.getText().toString().trim() : "";
                String c = etCategory.getText() != null ? etCategory.getText().toString().trim() : "";
                showReplacementCandidatesDialog(d, s, e, c);
            });
        }

        // ═════ Sekcja Budzika ═════
        SwitchMaterial swAlarm = dialogView.findViewById(R.id.switch_alarm);
        TextInputLayout tilAlarmOffset = dialogView.findViewById(R.id.til_alarm_offset);
        android.widget.AutoCompleteTextView etAlarmOffset = dialogView.findViewById(R.id.et_alarm_offset);

        String[] alarmOffsetOptions = new String[]{"30 min", "60 min", "90 min", "120 min"};
        android.widget.ArrayAdapter<String> alarmOffsetAdapter = new android.widget.ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                alarmOffsetOptions
        );
        etAlarmOffset.setAdapter(alarmOffsetAdapter);
        etAlarmOffset.setText("60 min", false);

        swAlarm.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tilAlarmOffset.setVisibility(isChecked ? View.VISIBLE : View.GONE);

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

        // Sekcja współpracowników (Overlap lub Cały dzień — Tryb Kierownika)
        LinearLayout layoutCoworkers = dialogView.findViewById(R.id.layout_coworkers_section);
        RecyclerView rvCoworkers = dialogView.findViewById(R.id.rv_coworkers_list);
        com.google.android.material.button.MaterialButton btnToggleFullDay =
                dialogView.findViewById(R.id.btn_toggle_full_day_crew);
        final boolean[] showFullDayCrew = new boolean[]{false};

        final CoworkerAdapter[] adapterHolder = new CoworkerAdapter[1];
        CoworkerAdapter coworkerAdapter = new CoworkerAdapter((gs, pos) -> {
            showCoworkerEditDialog(gs, pos, adapterHolder[0], rvCoworkers, () -> {
                String d2 = etDate.getText() != null ? etDate.getText().toString().trim() : "";
                String s2 = etStart.getText() != null ? etStart.getText().toString().trim() : "";
                String e2 = etEnd.getText() != null ? etEnd.getText().toString().trim() : "";
                refreshCoworkersSection(d2, s2, e2, layoutCoworkers, rvCoworkers, adapterHolder[0], showFullDayCrew[0]);
            });
        });
        adapterHolder[0] = coworkerAdapter;
        rvCoworkers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCoworkers.setAdapter(coworkerAdapter);

        Runnable refreshCoworkers = () -> {
            String d = etDate.getText() != null ? etDate.getText().toString().trim() : "";
            String s = etStart.getText() != null ? etStart.getText().toString().trim() : "";
            String e = etEnd.getText() != null ? etEnd.getText().toString().trim() : "";
            refreshCoworkersSection(d, s, e, layoutCoworkers, rvCoworkers, coworkerAdapter, showFullDayCrew[0]);
        };

        if (btnToggleFullDay != null) {
            btnToggleFullDay.setOnClickListener(v -> {
                showFullDayCrew[0] = !showFullDayCrew[0];
                btnToggleFullDay.setText(showFullDayCrew[0] ? "W moim czasie" : "Cały dzień");
                refreshCoworkers.run();
            });
        }

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
                    layoutCoworkers, rvCoworkers, coworkerAdapter, showFullDayCrew[0]);
        });

        // ═════ Setup MultiAutoComplete z CommaTokenizer ═════
        etClosingCrew.setTokenizer(new android.widget.MultiAutoCompleteTextView.CommaTokenizer());
        android.widget.ArrayAdapter<String> crewAdapter = new android.widget.ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<String>()
        );
        etClosingCrew.setAdapter(crewAdapter);

        // Wypełnij podpowiedzi pracowników snapshotem z ViewModelu
        List<String> currentSuggestions = viewModel.getClosingCrewSuggestions().getValue();
        if (currentSuggestions != null) {
            crewAdapter.addAll(currentSuggestions);
        }

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

            if (shift.isClosingShift()) {
                tilClosingCrew.setVisibility(View.VISIBLE);
                if (shift.getClosingCrew() != null) {
                    etClosingCrew.setText(shift.getClosingCrew());
                }
            }

            if (shift.getDate() != null && shift.getStartTime() != null && !shift.getStartTime().isEmpty()) {
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

            refreshCoworkers.run();
        } else {
            etDate.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            refreshCoworkers.run();
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

                    if (start.length() == 4 && start.charAt(1) == ':') start = "0" + start;
                    if (end.length() == 4 && end.charAt(1) == ':') end = "0" + end;

                    boolean closing = isClosingTime(end);
                    String finalCat = cat.isEmpty() ? "UNKNOWN" : cat.toUpperCase();

                    if (isEdit) {
                        shift.setDate(date);
                        shift.setStartTime(start);
                        shift.setEndTime(end);
                        shift.setCategory(finalCat);
                        shift.setReplacement(isRep);
                        shift.setManual(true);
                        shift.setClosingShift(closing);
                        shift.setClosingCrew(!crew.isEmpty() ? crew : (shift.getClosingCrew() != null ? shift.getClosingCrew() : ""));
                        String updatedDesc = start.isEmpty() ? finalCat : finalCat + " (" + start + "-" + end + ")";
                        shift.setDescription(updatedDesc);
                        viewModel.updateShift(shift);
                    } else {
                        String desc = start.isEmpty() ? finalCat : finalCat + " (" + start + "-" + end + ")";
                        Shift newShift = new Shift(date, start, end, desc, true, isRep, finalCat);
                        newShift.setManual(true);
                        newShift.setClosingShift(closing);
                        newShift.setClosingCrew(!crew.isEmpty() ? crew : "");
                        viewModel.insertShift(newShift);
                    }

                    // ═════ Zapis stanu alarmu (z anulowaniem starej daty/godziny przy edycji, WOLNE lub oddaniu zmiany) ═════
                    boolean alarmEnabled = swAlarm.isChecked() && !isRep && !start.isEmpty();
                    if (!start.isEmpty() || !originalStartTime.isEmpty()) {
                        int offsetMinutes = 60;
                        String offsetText = etAlarmOffset.getText() != null
                                ? etAlarmOffset.getText().toString().trim() : "";
                        if (!offsetText.isEmpty()) {
                            try {
                                offsetMinutes = Integer.parseInt(offsetText.replaceAll("[^0-9]", ""));
                            } catch (NumberFormatException ignored) { }
                        }
                        viewModel.toggleAlarmForShift(date, start, end, finalCat,
                                alarmEnabled, offsetMinutes, originalDate, originalStartTime);
                    }
                })
                .setNegativeButton("Anuluj", null);

        if (isEdit) {
            builder.setNeutralButton("Usuń", (dialog, which) -> {
                if (!originalStartTime.isEmpty()) {
                    viewModel.toggleAlarmForShift(originalDate, "", "", "UNKNOWN",
                            false, 60, originalDate, originalStartTime);
                }
                viewModel.deleteShift(shift);
            });
        }

        if (currentShiftDialog != null && currentShiftDialog.isShowing()) {
            try {
                currentShiftDialog.dismiss();
            } catch (Exception ignored) {}
        }
        currentShiftDialog = builder.create();
        currentShiftDialog.show();
    }

    @Override
    public void onDestroyView() {
        if (currentShiftDialog != null && currentShiftDialog.isShowing()) {
            try {
                currentShiftDialog.dismiss();
            } catch (Exception ignored) {}
            currentShiftDialog = null;
        }
        super.onDestroyView();
    }
}
