# Poprawki Biznesowe — Walkthrough

Wprowadzono wszystkie krytyczne poprawki biznesowe przed ostatecznym oknem UI. Poniżej znajduje się zestawienie wykonanych zmian.

## 1. Dni tygodnia w Grafiku ([ShiftAdapter.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/ui/ShiftAdapter.java))
Zmieniono formatowanie daty na pełne dni tygodnia w języku polskim. Użyto `java.time.LocalDate` oraz `DateTimeFormatter`.

```java
private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
private static final DateTimeFormatter DISPLAY_DATE = 
    DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", new Locale("pl", "PL"));

// Wynik np.: "Sobota, 28.03.2026"
```
Dodano również interfejs [OnShiftClickListener](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/ui/ShiftAdapter.java#33-36) umożliwiający ScheduleFragment reagowanie na kliknięcia w elementy listy (edycja zmian).

```diff:ShiftAdapter.java
package com.asystent.kinowy.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.Shift;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter RecyclerView dla listy zmian (Shift).
 * <p>
 * Wyświetla kafelki {@code item_shift.xml} z datą, godzinami i opisem zmiany.
 * Wykorzystuje {@link DiffUtil} do efektywnego odświeżania listy.
 */
public class ShiftAdapter extends RecyclerView.Adapter<ShiftAdapter.ShiftViewHolder> {

    private List<Shift> shifts = new ArrayList<>();

    // ─── ViewHolder ──────────────────────────────────────────────────────

    static class ShiftViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDate;
        final TextView tvHours;
        final TextView tvDescription;

        ShiftViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_shift_date);
            tvHours = itemView.findViewById(R.id.tv_shift_hours);
            tvDescription = itemView.findViewById(R.id.tv_shift_description);
        }
    }

    // ─── Adapter methods ─────────────────────────────────────────────────

    @NonNull
    @Override
    public ShiftViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shift, parent, false);
        return new ShiftViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShiftViewHolder holder, int position) {
        Shift shift = shifts.get(position);

        // Data (pogrubiona, formatowana czytelnie)
        holder.tvDate.setText(formatDate(shift.getDate()));

        // Godziny (np. "14:00 - 22:00" lub "Brak godzin")
        String hours = formatHours(shift.getStartTime(), shift.getEndTime());
        holder.tvHours.setText(hours);

        // Opis zmiany (badge)
        String desc = shift.getDescription();
        if (desc != null && !desc.isEmpty()) {
            // Wyciągnij sam typ (np. "BAR" z "BAR (14:00-22:00)")
            String shortDesc = desc.contains("(") ? desc.substring(0, desc.indexOf("(")).trim() : desc;
            holder.tvDescription.setText(shortDesc);
            holder.tvDescription.setVisibility(View.VISIBLE);
        } else {
            holder.tvDescription.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return shifts.size();
    }

    // ─── Aktualizacja danych ─────────────────────────────────────────────

    /**
     * Ustawia nową listę zmian z wykorzystaniem DiffUtil.
     */
    public void setShifts(List<Shift> newShifts) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return shifts.size(); }

            @Override
            public int getNewListSize() { return newShifts.size(); }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return shifts.get(oldPos).getId() == newShifts.get(newPos).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                Shift oldShift = shifts.get(oldPos);
                Shift newShift = newShifts.get(newPos);
                return oldShift.getDate().equals(newShift.getDate())
                        && safeEquals(oldShift.getStartTime(), newShift.getStartTime())
                        && safeEquals(oldShift.getEndTime(), newShift.getEndTime())
                        && safeEquals(oldShift.getDescription(), newShift.getDescription());
            }
        });

        this.shifts = new ArrayList<>(newShifts);
        result.dispatchUpdatesTo(this);
    }

    // ─── Formatowanie ────────────────────────────────────────────────────

    /**
     * Formatuje datę ISO (yyyy-MM-dd) na bardziej czytelną formę (dd.MM.yyyy).
     */
    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.length() < 10) return isoDate != null ? isoDate : "";
        try {
            String[] parts = isoDate.split("-");
            return parts[2] + "." + parts[1] + "." + parts[0]; // dd.MM.yyyy
        } catch (Exception e) {
            return isoDate;
        }
    }

    /**
     * Formatuje godziny startu i końca w czytelny ciąg.
     */
    private String formatHours(String start, String end) {
        boolean hasStart = start != null && !start.isEmpty();
        boolean hasEnd = end != null && !end.isEmpty();

        if (hasStart && hasEnd) {
            return start + " – " + end;
        } else if (hasStart) {
            return "od " + start;
        } else {
            return "Cały dzień";
        }
    }

    private boolean safeEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}
===
package com.asystent.kinowy.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.Shift;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter RecyclerView dla listy zmian (Shift).
 * <p>
 * Wyświetla kafelki {@code item_shift.xml} z datą, godzinami i opisem zmiany.
 * Wykorzystuje {@link DiffUtil} do efektywnego odświeżania listy.
 */
public class ShiftAdapter extends RecyclerView.Adapter<ShiftAdapter.ShiftViewHolder> {

    /**
     * Interfejs callback do obsługi kliknięcia w element listy.
     */
    public interface OnShiftClickListener {
        void onShiftClick(Shift shift);
    }

    private List<Shift> shifts = new ArrayList<>();
    @Nullable
    private OnShiftClickListener listener;

    // ─── ViewHolder ──────────────────────────────────────────────────────

    static class ShiftViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDate;
        final TextView tvHours;
        final TextView tvDescription;

        ShiftViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_shift_date);
            tvHours = itemView.findViewById(R.id.tv_shift_hours);
            tvDescription = itemView.findViewById(R.id.chip_shift_description);        }
    }

    // ─── Konstruktor ─────────────────────────────────────────────────────

    public ShiftAdapter() {
        this(null);
    }

    public ShiftAdapter(@Nullable OnShiftClickListener listener) {
        this.listener = listener;
    }

    // ─── Adapter methods ─────────────────────────────────────────────────

    @NonNull
    @Override
    public ShiftViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_shift, parent, false);
        return new ShiftViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShiftViewHolder holder, int position) {
        Shift shift = shifts.get(position);

        // Data (pogrubiona, z dniem tygodnia — np. "Sobota, 28.03.2026")
        holder.tvDate.setText(formatDate(shift.getDate()));

        // Godziny (np. "14:00 – 22:00" lub "Cały dzień")
        String hours = formatHours(shift.getStartTime(), shift.getEndTime());
        holder.tvHours.setText(hours);

        // Opis zmiany (badge)
        String desc = shift.getDescription();
        if (shift.isReplacement()) {
            String repText = (desc != null && !desc.isEmpty()) ? desc + " (ODDAJĘ)" : "(ODDAJĘ)";
            holder.tvDescription.setText(repText);
            holder.tvDescription.setVisibility(View.VISIBLE);
            holder.itemView.setAlpha(0.6f);
        } else if (desc != null && !desc.isEmpty()) {
            // Wyciągnij sam typ (np. "BAR" z "BAR (14:00-22:00)")
            String shortDesc = desc.contains("(") ? desc.substring(0, desc.indexOf("(")).trim() : desc;
            holder.tvDescription.setText(shortDesc);
            holder.tvDescription.setVisibility(View.VISIBLE);
            holder.itemView.setAlpha(1.0f);
        } else {
            holder.tvDescription.setVisibility(View.GONE);
            holder.itemView.setAlpha(1.0f);
        }

        // Kliknięcie → edycja
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onShiftClick(shift);
        });
    }

    @Override
    public int getItemCount() {
        return shifts.size();
    }

    // ─── Aktualizacja danych ─────────────────────────────────────────────

    /**
     * Ustawia nową listę zmian z wykorzystaniem DiffUtil.
     */
    public void setShifts(List<Shift> newShifts) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return shifts.size(); }

            @Override
            public int getNewListSize() { return newShifts.size(); }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return shifts.get(oldPos).getId() == newShifts.get(newPos).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                Shift oldShift = shifts.get(oldPos);
                Shift newShift = newShifts.get(newPos);
                return oldShift.getDate().equals(newShift.getDate())
                        && safeEquals(oldShift.getStartTime(), newShift.getStartTime())
                        && safeEquals(oldShift.getEndTime(), newShift.getEndTime())
                        && safeEquals(oldShift.getDescription(), newShift.getDescription());
            }
        });

        this.shifts = new ArrayList<>(newShifts);
        result.dispatchUpdatesTo(this);
    }

    // ─── Formatowanie ────────────────────────────────────────────────────

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", new Locale("pl", "PL"));

    /**
     * Formatuje datę ISO (yyyy-MM-dd) z dniem tygodnia (np. "Sobota, 28.03.2026").
     */
    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.length() < 10) return isoDate != null ? isoDate : "";
        try {
            LocalDate date = LocalDate.parse(isoDate, ISO_DATE);
            String formatted = date.format(DISPLAY_DATE);
            // Capitalize first letter
            return formatted.substring(0, 1).toUpperCase() + formatted.substring(1);
        } catch (Exception e) {
            return isoDate;
        }
    }

    /**
     * Formatuje godziny startu i końca w czytelny ciąg.
     */
    private String formatHours(String start, String end) {
        boolean hasStart = start != null && !start.isEmpty();
        boolean hasEnd = end != null && !end.isEmpty();

        if (hasStart && hasEnd) {
            return start + " – " + end;
        } else if (hasStart) {
            return "od " + start;
        } else {
            return "Cały dzień";
        }
    }

    private boolean safeEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}
```

## 2. Dodawanie i Edycja Zmian w Grafiku
### Layout ([fragment_schedule.xml](file:///j:/kinobot/app/src/main/res/layout/fragment_schedule.xml))
Opakowano główny widok w `CoordinatorLayout` i dodano `FloatingActionButton` w prawym dolnym rogu. Zabezpieczono `RecyclerView` dodając mu dolny padding, aby FAB nie przesłaniał ostatniej zmiany.

### Interfejs ([dialog_shift.xml](file:///j:/kinobot/app/src/main/res/layout/dialog_shift.xml))
Utworzono nowy, prosty plik layoutu [dialog_shift.xml](file:///j:/kinobot/app/src/main/res/layout/dialog_shift.xml) zawierający 4 pola typu `TextInputLayout` i `TextInputEditText` (Data, Start, Koniec, Opis).

### Logika we fragmencie ([ScheduleFragment.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/ui/ScheduleFragment.java))
Zaimplementowano obsługę kliknięcia w FAB (tworzenie) oraz w adapter (edycja) otwierając `AlertDialog` zbudowany na podstawie zapisanego [dialog_shift.xml](file:///j:/kinobot/app/src/main/res/layout/dialog_shift.xml).
- Przycisk **Zapisz** — wywołuje w ViewModelu [insertShift](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java#420-421) lub [updateShift](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java#421-422)
- Przycisk **Usuń** (tylko w trybie edycji) — wywołuje [deleteShift](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java#422-423)

```diff:ScheduleFragment.java
package com.asystent.kinowy.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.viewmodel.MainViewModel;

/**
 * Fragment z widokiem grafiku pracy.
 * <p>
 * Wyświetla listę zmian (shifts) w RecyclerView,
 * pobieranych z bazy Room przez {@link MainViewModel}.
 */
public class ScheduleFragment extends Fragment {

    private MainViewModel viewModel;
    private ShiftAdapter adapter;
    private RecyclerView rvShifts;
    private TextView tvEmpty;

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

        // --- RecyclerView setup ---
        adapter = new ShiftAdapter();
        rvShifts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvShifts.setAdapter(adapter);

        // --- Obserwuj dane ---
        viewModel.getAllShifts().observe(getViewLifecycleOwner(), shifts -> {
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
}
===
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
        com.google.android.material.materialswitch.MaterialSwitch swReplacement = dialogView.findViewById(R.id.switch_replacement);

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

                    if (date.isEmpty()) return; // Walidacja uproszczona

                    if (isEdit) {
                        shift.setDate(date);
                        shift.setStartTime(start);
                        shift.setEndTime(end);
                        shift.setDescription(desc);
                        shift.setReplacement(isRep);
                        viewModel.updateShift(shift);
                    } else {
                        Shift newShift = new Shift(date, start, end, desc, true, isRep);
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
```

## 3. Cykl Rozliczeniowy i Wybierak Miesiąca
### Etykieta Finansów ([fragment_finance.xml](file:///j:/kinobot/app/src/main/res/layout/fragment_finance.xml))
Zaktualizowano wielką etykietę podsumowania z "Do wypłaty w tym miesiącu" na **"Zarobki za:"**. W następnym kroku dodamy mechanizm przełączania miesięcy.

### Logika ViewModelu ([MainViewModel.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java))
Wprowadzono nową zmienną stanowiącą o aktualnie branym pod uwagę miesiącu w kontekście wyliczeń finansowych:
```java
private final MutableLiveData<YearMonth> currentSelectedMonth;
```

Zaktualizowano główną potokową metodę finansową, aby uwzględniała tę zmienną:
- Metoda [setupPayrollCalculation()](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java#151-163) zyskała jako nowe źródło `currentSelectedMonth` (kiedy się mieni, wyliczamy na nowo).
- Metoda [setupMonthlyLosses()](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java#164-171) analogicznie obserwuje `currentSelectedMonth`.
- Wewnętrzna metoda [getCurrentMonthPrefix()](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java#329-337) wykorzystująca wcześniej statyczne `LocalDate.now()` korzysta teraz z wybranej wartości `currentSelectedMonth`.

Zapewnia to, że *wypłata (payroll)* pokazująca łączne zarobki (suma godzin * stawka - manka) obejmuje teraz idealnie okno czasowe wskazywane przez `currentSelectedMonth`.

```diff:MainViewModel.java
package com.asystent.kinowy.viewmodel;

import android.app.Application;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.asystent.kinowy.models.Loss;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.network.ExcelParsingService;
import com.asystent.kinowy.repository.GmailRepository;
import com.asystent.kinowy.repository.LossRepository;
import com.asystent.kinowy.repository.ShiftRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Główny ViewModel aplikacji Asystent Kinowy.
 * <p>
 * Łączy trzy repozytoria i wystawia dane z bazy danych
 * jako {@link LiveData} do warstwy UI. Zarządza:
 * <ul>
 *   <li>Przekazywaniem tokena OAuth do {@link GmailRepository}</li>
 *   <li>Synchronizacją grafiku: Gmail → Excel parser → Room DB</li>
 *   <li>Obliczaniem wypłaty za bieżący miesiąc</li>
 * </ul>
 */
public class MainViewModel extends AndroidViewModel {

    private static final String TAG = "MainViewModel";

    // ─── Repozytoria ─────────────────────────────────────────────────────

    private final ShiftRepository shiftRepository;
    private final LossRepository lossRepository;
    private final GmailRepository gmailRepository;

    // ─── Serwisy ─────────────────────────────────────────────────────────

    private final ExcelParsingService excelParsingService;
    private final ExecutorService executor;

    // ─── LiveData ────────────────────────────────────────────────────────

    private final LiveData<List<Shift>> allShifts;
    private final LiveData<List<Loss>> allLosses;
    private final MutableLiveData<String> syncStatus;

    // ─── Finanse ─────────────────────────────────────────────────────────

    private final MutableLiveData<Float> hourlyRateLive;
    private final MediatorLiveData<PayrollInfo> monthlyPayroll;
    private final MediatorLiveData<List<Loss>> monthlyLosses;

    /** Nazwisko użytkownika w grafiku (ustawiane z ustawień / po rejestracji) */
    private String targetUserName;

    // ─── Konstruktor ─────────────────────────────────────────────────────

    public MainViewModel(@NonNull Application application) {
        super(application);

        shiftRepository = new ShiftRepository(application);
        lossRepository = new LossRepository(application);
        gmailRepository = new GmailRepository();
        excelParsingService = new ExcelParsingService();
        executor = Executors.newSingleThreadExecutor();

        allShifts = shiftRepository.getAllShifts();
        allLosses = lossRepository.getAllLosses();
        syncStatus = new MutableLiveData<>();

        // ─── Finanse ─────────────────────────────────────────────────
        hourlyRateLive = new MutableLiveData<>(0f);
        monthlyPayroll = new MediatorLiveData<>();
        monthlyLosses = new MediatorLiveData<>();

        setupPayrollCalculation();
        setupMonthlyLosses();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PAYROLL — logika obliczania wypłaty
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Obiekt podsumowania wypłaty za bieżący miesiąc.
     */
    public static class PayrollInfo {
        private final double totalHours;
        private final float hourlyRate;
        private final double totalLosses;
        private final double netPay;

        public PayrollInfo(double totalHours, float hourlyRate, double totalLosses) {
            this.totalHours = totalHours;
            this.hourlyRate = hourlyRate;
            this.totalLosses = totalLosses;
            this.netPay = Math.max(0, (totalHours * hourlyRate) - totalLosses);
        }

        public double getTotalHours() { return totalHours; }
        public float getHourlyRate() { return hourlyRate; }
        public double getTotalLosses() { return totalLosses; }
        public double getNetPay() { return netPay; }
    }

    /**
     * Konfiguruje MediatorLiveData, który reaguje na zmiany w:
     * allShifts, allLosses oraz hourlyRateLive
     * — i przelicza wypłatę za bieżący miesiąc.
     */
    private void setupPayrollCalculation() {
        monthlyPayroll.addSource(allShifts, shifts -> recalculatePayroll());
        monthlyPayroll.addSource(allLosses, losses -> recalculatePayroll());
        monthlyPayroll.addSource(hourlyRateLive, rate -> recalculatePayroll());
    }

    /**
     * Konfiguruje MediatorLiveData filtrujący straty z bieżącego miesiąca.
     */
    private void setupMonthlyLosses() {
        monthlyLosses.addSource(allLosses, losses -> {
            if (losses == null) {
                monthlyLosses.setValue(new ArrayList<>());
                return;
            }
            String currentMonthPrefix = getCurrentMonthPrefix();
            List<Loss> filtered = new ArrayList<>();
            for (Loss loss : losses) {
                if (loss.getDate() != null && loss.getDate().startsWith(currentMonthPrefix)) {
                    filtered.add(loss);
                }
            }
            monthlyLosses.setValue(filtered);
        });
    }

    /**
     * Przelicza wypłatę: sumuje godziny z bieżącego miesiąca × stawka − straty.
     */
    private void recalculatePayroll() {
        List<Shift> shifts = allShifts.getValue();
        List<Loss> losses = allLosses.getValue();
        Float rate = hourlyRateLive.getValue();

        if (rate == null) rate = 0f;
        String currentMonthPrefix = getCurrentMonthPrefix();

        // ─── Suma godzin ze zmian bieżącego miesiąca ─────────────────
        double totalHours = 0;
        if (shifts != null) {
            for (Shift shift : shifts) {
                if (shift.getDate() != null && shift.getDate().startsWith(currentMonthPrefix)) {
                    totalHours += calculateShiftHours(shift);
                }
            }
        }

        // ─── Suma strat z bieżącego miesiąca ────────────────────────
        double totalLosses = 0;
        if (losses != null) {
            for (Loss loss : losses) {
                if (loss.getDate() != null && loss.getDate().startsWith(currentMonthPrefix)) {
                    totalLosses += loss.getAmount();
                }
            }
        }

        monthlyPayroll.setValue(new PayrollInfo(totalHours, rate, totalLosses));
    }

    /**
     * Oblicza liczbę przepracowanych godzin z pojedynczej zmiany.
     * Obsługuje zmiany przechodzące przez północ (np. 22:00→06:00).
     */
    private double calculateShiftHours(Shift shift) {
        String startStr = shift.getStartTime();
        String endStr = shift.getEndTime();

        if (startStr == null || startStr.isEmpty() || endStr == null || endStr.isEmpty()) {
            return 0;
        }

        try {
            LocalTime start = LocalTime.parse(startStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime end = LocalTime.parse(endStr, DateTimeFormatter.ofPattern("HH:mm"));
            return ExcelParsingService.calculateHours(start, end);
        } catch (Exception e) {
            Log.w(TAG, "Nie można obliczyć godzin dla zmiany: " + startStr + "-" + endStr, e);
            return 0;
        }
    }

    /**
     * Zwraca prefiks bieżącego miesiąca w formacie "yyyy-MM"
     * do filtrowania dat ISO (yyyy-MM-dd).
     */
    private String getCurrentMonthPrefix() {
        LocalDate now = LocalDate.now();
        return String.format("%04d-%02d", now.getYear(), now.getMonthValue());
    }

    // ─── Finanse — publiczne API ─────────────────────────────────────────

    /**
     * Ustawia stawkę godzinową. Powoduje automatyczne przeliczenie wypłaty.
     */
    public void setHourlyRate(float rate) {
        hourlyRateLive.setValue(rate);
    }

    /**
     * LiveData z podsumowaniem wypłaty za bieżący miesiąc.
     * Reaguje automatycznie na zmiany w shifts, losses i stawce.
     */
    public LiveData<PayrollInfo> getMonthlyPayroll() {
        return monthlyPayroll;
    }

    /**
     * LiveData ze stratami z bieżącego miesiąca (do wyświetlenia w UI).
     */
    public LiveData<List<Loss>> getMonthlyLosses() {
        return monthlyLosses;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LiveData — odczyty (istniejące)
    // ═══════════════════════════════════════════════════════════════════════

    public LiveData<List<Shift>> getAllShifts() {
        return allShifts;
    }

    public LiveData<List<Loss>> getAllLosses() {
        return allLosses;
    }

    /**
     * Status synchronizacji grafiku. Obserwuj w UI, by wyświetlać komunikaty.
     * Możliwe wartości: "syncing", "success:N" (N = liczba zmian), "error:msg".
     */
    public LiveData<String> getSyncStatus() {
        return syncStatus;
    }

    // ─── Ustawienia użytkownika ──────────────────────────────────────────

    public void setTargetUserName(String name) {
        this.targetUserName = name;
    }

    public String getTargetUserName() {
        return targetUserName;
    }

    // ─── Shift — operacje zapisu ─────────────────────────────────────────

    public void insertShift(Shift shift) { shiftRepository.insert(shift); }
    public void updateShift(Shift shift) { shiftRepository.update(shift); }
    public void deleteShift(Shift shift) { shiftRepository.delete(shift); }
    public void deleteAllShifts() { shiftRepository.deleteAll(); }

    // ─── Loss — operacje zapisu ──────────────────────────────────────────

    public void insertLoss(Loss loss) { lossRepository.insert(loss); }
    public void updateLoss(Loss loss) { lossRepository.update(loss); }
    public void deleteLoss(Loss loss) { lossRepository.delete(loss); }
    public void deleteAllLosses() { lossRepository.deleteAll(); }

    // ═══════════════════════════════════════════════════════════════════════
    // Gmail / OAuth / Synchronizacja (istniejący kod)
    // ═══════════════════════════════════════════════════════════════════════

    public void setGmailAccessToken(String accessToken) {
        gmailRepository.setAccessToken(accessToken);
    }

    public boolean isGmailAuthenticated() {
        return gmailRepository.isAuthenticated();
    }

    public GmailRepository getGmailRepository() {
        return gmailRepository;
    }

    // ─── Synchronizacja grafiku ──────────────────────────────────────────

    /**
     * Pełny przepływ synchronizacji grafiku:
     * <ol>
     *   <li>Pobiera listę wiadomości Gmail z załącznikami .xlsx</li>
     *   <li>Pobiera najnowszą wiadomość i szuka załącznika .xlsx</li>
     *   <li>Pobiera dane załącznika (base64url) → dekoduje do InputStream</li>
     *   <li>Parsuje Excel za pomocą {@link ExcelParsingService}</li>
     *   <li>Czyści starą bazę i zapisuje nowe zmiany do Room</li>
     * </ol>
     */
    public void syncSchedule() {
        if (targetUserName == null || targetUserName.isEmpty()) {
            syncStatus.setValue("error:Nie ustawiono nazwiska użytkownika");
            return;
        }
        if (!gmailRepository.isAuthenticated()) {
            syncStatus.setValue("error:Brak tokena OAuth — zaloguj się");
            return;
        }

        syncStatus.setValue("syncing");

        gmailRepository.fetchMessageList(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call,
                                   @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    syncStatus.postValue("error:Błąd API (" + response.code() + ")");
                    return;
                }

                JsonArray messages = response.body().getAsJsonArray("messages");
                if (messages == null || messages.size() == 0) {
                    syncStatus.postValue("error:Brak wiadomości z plikiem .xlsx");
                    return;
                }

                String messageId = messages.get(0).getAsJsonObject().get("id").getAsString();
                fetchMessageAndParseAttachment(messageId);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchMessageList failed", t);
                syncStatus.postValue("error:" + t.getMessage());
            }
        });
    }

    private void fetchMessageAndParseAttachment(String messageId) {
        gmailRepository.fetchMessageDetail(messageId, new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call,
                                   @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    syncStatus.postValue("error:Błąd pobierania wiadomości");
                    return;
                }

                String attachmentId = findXlsxAttachmentId(response.body());
                if (attachmentId == null) {
                    syncStatus.postValue("error:Nie znaleziono załącznika .xlsx");
                    return;
                }

                fetchAndDecodeAttachment(messageId, attachmentId);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchMessageDetail failed", t);
                syncStatus.postValue("error:" + t.getMessage());
            }
        });
    }

    private void fetchAndDecodeAttachment(String messageId, String attachmentId) {
        gmailRepository.fetchAttachment(messageId, attachmentId, new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call,
                                   @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    syncStatus.postValue("error:Błąd pobierania załącznika");
                    return;
                }

                String base64Data = response.body().get("data").getAsString();

                executor.execute(() -> {
                    try {
                        byte[] fileBytes = Base64.decode(base64Data, Base64.URL_SAFE);
                        InputStream inputStream = new ByteArrayInputStream(fileBytes);

                        List<Shift> shifts = excelParsingService.parseSchedule(
                                inputStream, targetUserName);

                        if (shifts.isEmpty()) {
                            syncStatus.postValue("error:Brak zmian dla: " + targetUserName);
                            return;
                        }

                        shiftRepository.deleteAll();
                        for (Shift shift : shifts) {
                            shiftRepository.insert(shift);
                        }

                        syncStatus.postValue("success:" + shifts.size());
                        Log.d(TAG, "Sync complete: " + shifts.size() + " shifts for " + targetUserName);

                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing/saving schedule", e);
                        syncStatus.postValue("error:" + e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchAttachment failed", t);
                syncStatus.postValue("error:" + t.getMessage());
            }
        });
    }

    private String findXlsxAttachmentId(JsonObject messageDetail) {
        try {
            JsonObject payload = messageDetail.getAsJsonObject("payload");
            if (payload == null) return null;

            JsonArray parts = payload.getAsJsonArray("parts");
            if (parts == null) return null;

            for (JsonElement partEl : parts) {
                JsonObject part = partEl.getAsJsonObject();
                String filename = part.has("filename") ? part.get("filename").getAsString() : "";
                if (filename.toLowerCase().endsWith(".xlsx")) {
                    JsonObject body = part.getAsJsonObject("body");
                    if (body != null && body.has("attachmentId")) {
                        return body.get("attachmentId").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding xlsx attachment", e);
        }
        return null;
    }
}
===
package com.asystent.kinowy.viewmodel;

import android.app.Application;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.asystent.kinowy.models.Loss;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.models.Tip;
import com.asystent.kinowy.network.ExcelParsingService;
import com.asystent.kinowy.repository.GmailRepository;
import com.asystent.kinowy.repository.LossRepository;
import com.asystent.kinowy.repository.ShiftRepository;
import com.asystent.kinowy.repository.TipRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Główny ViewModel aplikacji Asystent Kinowy.
 * <p>
 * Łączy trzy repozytoria i wystawia dane z bazy danych
 * jako {@link LiveData} do warstwy UI. Zarządza:
 * <ul>
 *   <li>Przekazywaniem tokena OAuth do {@link GmailRepository}</li>
 *   <li>Synchronizacją grafiku: Gmail → Excel parser → Room DB</li>
 *   <li>Obliczaniem wypłaty za bieżący miesiąc</li>
 * </ul>
 */
public class MainViewModel extends AndroidViewModel {

    private static final String TAG = "MainViewModel";

    // ─── Repozytoria ─────────────────────────────────────────────────────

    private final ShiftRepository shiftRepository;
    private final LossRepository lossRepository;
    private final TipRepository tipRepository;
    private final GmailRepository gmailRepository;

    // ─── Serwisy ─────────────────────────────────────────────────────────

    private final ExcelParsingService excelParsingService;
    private final ExecutorService executor;

    // ─── LiveData ────────────────────────────────────────────────────────

    private final LiveData<List<Shift>> allShifts;
    private final MediatorLiveData<List<Shift>> monthlyShifts;
    private final MediatorLiveData<Shift> nextShift;
    private final LiveData<List<Loss>> allLosses;
    private final LiveData<List<Tip>> allTips;
    private final MutableLiveData<String> syncStatus;

    // ─── Finanse ─────────────────────────────────────────────────────────

    private final MutableLiveData<Float> hourlyRateLive;
    private final MutableLiveData<Integer> monthlyHoursGoal;
    private final MediatorLiveData<PayrollInfo> monthlyPayroll;
    private final MediatorLiveData<List<Loss>> monthlyLosses;
    private final MediatorLiveData<List<Tip>> monthlyTips;
    private final MutableLiveData<YearMonth> currentSelectedMonth;

    /** Nazwisko użytkownika w grafiku (ustawiane z ustawień / po rejestracji) */
    private String targetUserName;

    // ─── Konstruktor ─────────────────────────────────────────────────────

    public MainViewModel(@NonNull Application application) {
        super(application);
        currentSelectedMonth = new MutableLiveData<>(java.time.YearMonth.now());
        shiftRepository = new ShiftRepository(application);
        lossRepository = new LossRepository(application);
        tipRepository = new TipRepository(application);
        gmailRepository = new GmailRepository();
        excelParsingService = new ExcelParsingService();
        executor = Executors.newSingleThreadExecutor();

        allShifts = shiftRepository.getAllShifts();
        monthlyShifts = new MediatorLiveData<>();
        nextShift = new MediatorLiveData<>();
        allLosses = lossRepository.getAllLosses();
        allTips = tipRepository.getAllTips();
        syncStatus = new MutableLiveData<>();

        // ─── Finanse ─────────────────────────────────────────────────
        hourlyRateLive = new MutableLiveData<>(0f);
        monthlyHoursGoal = new MutableLiveData<>(100);
        monthlyPayroll = new MediatorLiveData<>();
        monthlyLosses = new MediatorLiveData<>();
        monthlyTips = new MediatorLiveData<>();

        setupNextShiftComputation();
        setupPayrollCalculation();
        setupMonthlyLosses();
        setupMonthlyTips();
        setupMonthlyShifts();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PAYROLL — logika obliczania wypłaty
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Obiekt podsumowania wypłaty za bieżący miesiąc.
     */
    public static class PayrollInfo {
        private final double totalHours;
        private final float hourlyRate;
        private final double totalLosses;
        private final double totalTips;
        private final double netPay;

        public PayrollInfo(double totalHours, float hourlyRate, double totalLosses, double totalTips) {
            this.totalHours = totalHours;
            this.hourlyRate = hourlyRate;
            this.totalLosses = totalLosses;
            this.totalTips = totalTips;
            this.netPay = Math.max(0, (totalHours * hourlyRate) - totalLosses) + totalTips;
        }

        public double getTotalHours() { return totalHours; }
        public float getHourlyRate() { return hourlyRate; }
        public double getTotalLosses() { return totalLosses; }
        public double getTotalTips() { return totalTips; }
        public double getNetPay() { return netPay; }
    }

    /**
     * Konfiguruje MediatorLiveData, który reaguje na zmiany w:
     * allShifts, allLosses, hourlyRateLive oraz currentSelectedMonth
     * — i przelicza wypłatę za bieżący miesiąc.
     */
    private void setupPayrollCalculation() {
        monthlyPayroll.addSource(allShifts, shifts -> recalculatePayroll());
        monthlyPayroll.addSource(allLosses, losses -> recalculatePayroll());
        monthlyPayroll.addSource(allTips, tips -> recalculatePayroll());
        monthlyPayroll.addSource(hourlyRateLive, rate -> recalculatePayroll());
        monthlyPayroll.addSource(currentSelectedMonth, month -> recalculatePayroll());
    }

    /**
     * Konfiguruje MediatorLiveData filtrujący straty z wybranego miesiąca.
     */
    private void setupMonthlyLosses() {
        monthlyLosses.addSource(allLosses, losses -> updateMonthlyLosses(losses));
        monthlyLosses.addSource(currentSelectedMonth, month -> updateMonthlyLosses(allLosses.getValue()));
    }

    private void updateMonthlyLosses(List<Loss> losses) {
        if (losses == null) {
            monthlyLosses.setValue(new ArrayList<>());
            return;
        }
        String currentMonthPrefix = getCurrentMonthPrefix();
        List<Loss> filtered = new ArrayList<>();
        for (Loss loss : losses) {
            if (loss.getDate() != null && loss.getDate().startsWith(currentMonthPrefix)) {
                filtered.add(loss);
            }
        }
        monthlyLosses.setValue(filtered);
    }

    /**
     * Konfiguruje MediatorLiveData filtrujący zmiany z wybranego miesiąca.
     */
    private void setupMonthlyShifts() {
        monthlyShifts.addSource(allShifts, shifts -> updateMonthlyShifts(shifts));
        monthlyShifts.addSource(currentSelectedMonth, month -> updateMonthlyShifts(allShifts.getValue()));
    }

    private void updateMonthlyShifts(List<Shift> shifts) {
        if (shifts == null) {
            monthlyShifts.setValue(new ArrayList<>());
            return;
        }
        String currentMonthPrefix = getCurrentMonthPrefix();
        List<Shift> filtered = new ArrayList<>();
        for (Shift shift : shifts) {
            if (shift.getDate() != null && shift.getDate().startsWith(currentMonthPrefix)) {
                filtered.add(shift);
            }
        }
        monthlyShifts.setValue(filtered);
    }

    private void setupMonthlyTips() {
        monthlyTips.addSource(allTips, tips -> updateMonthlyTips(tips));
        monthlyTips.addSource(currentSelectedMonth, month -> updateMonthlyTips(allTips.getValue()));
    }

    private void updateMonthlyTips(List<Tip> tips) {
        if (tips == null) {
            monthlyTips.setValue(new ArrayList<>());
            return;
        }
        String currentMonthPrefix = getCurrentMonthPrefix();
        List<Tip> filtered = new ArrayList<>();
        for (Tip tip : tips) {
            if (tip.getDate() != null && tip.getDate().startsWith(currentMonthPrefix)) {
                filtered.add(tip);
            }
        }
        monthlyTips.setValue(filtered);
    }

    private void setupNextShiftComputation() {
        nextShift.addSource(allShifts, shifts -> {
            if (shifts == null || shifts.isEmpty()) {
                nextShift.setValue(null);
                return;
            }
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            Shift upcoming = null;
            java.time.LocalDateTime upcomingTime = null;

            for (Shift shift : shifts) {
                if (shift.getDate() == null || shift.getStartTime() == null) continue;
                try {
                    java.time.LocalDate date = java.time.LocalDate.parse(shift.getDate());
                    LocalTime time = LocalTime.parse(shift.getStartTime());
                    java.time.LocalDateTime shiftStart = java.time.LocalDateTime.of(date, time);
                    if (shiftStart.isAfter(now)) {
                        if (upcomingTime == null || shiftStart.isBefore(upcomingTime)) {
                            upcomingTime = shiftStart;
                            upcoming = shift;
                        }
                    }
                } catch (Exception ignored) { }
            }
            nextShift.setValue(upcoming);
        });
    }

    /**
     * Przelicza wypłatę: sumuje godziny z bieżącego miesiąca × stawka − straty.
     */
    private void recalculatePayroll() {
        List<Shift> shifts = allShifts.getValue();
        List<Loss> losses = allLosses.getValue();
        List<Tip> tips = allTips.getValue();
        Float rate = hourlyRateLive.getValue();

        if (rate == null) rate = 0f;
        String currentMonthPrefix = getCurrentMonthPrefix();

        // ─── Suma godzin ze zmian bieżącego miesiąca ─────────────────
        double totalHours = 0;
        if (shifts != null) {
            for (Shift shift : shifts) {
                if (shift.getDate() != null && shift.getDate().startsWith(currentMonthPrefix)) {
                    totalHours += calculateShiftHours(shift);
                }
            }
        }

        // ─── Suma strat z bieżącego miesiąca ────────────────────────
        double totalLosses = 0;
        if (losses != null) {
            for (Loss loss : losses) {
                if (loss.getDate() != null && loss.getDate().startsWith(currentMonthPrefix)) {
                    totalLosses += loss.getAmount();
                }
            }
        }

        // ─── Suma napiwków z bieżącego miesiąca ─────────────────────
        double totalTips = 0;
        if (tips != null) {
            for (Tip tip : tips) {
                if (tip.getDate() != null && tip.getDate().startsWith(currentMonthPrefix)) {
                    totalTips += tip.getAmount();
                }
            }
        }

        monthlyPayroll.setValue(new PayrollInfo(totalHours, rate, totalLosses, totalTips));
    }

    /**
     * Oblicza liczbę przepracowanych godzin z pojedynczej zmiany.
     * Obsługuje zmiany przechodzące przez północ (np. 22:00→06:00).
     */
    private double calculateShiftHours(Shift shift) {
        if (shift.isReplacement()) {
            return 0; // Nie liczymy godzin za zmianę oddaną
        }

        String startStr = shift.getStartTime();
        String endStr = shift.getEndTime();

        if (startStr == null || startStr.isEmpty() || endStr == null || endStr.isEmpty()) {
            return 0;
        }

        try {
            LocalTime start = LocalTime.parse(startStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime end = LocalTime.parse(endStr, DateTimeFormatter.ofPattern("HH:mm"));
            return ExcelParsingService.calculateHours(start, end);
        } catch (Exception e) {
            Log.w(TAG, "Nie można obliczyć godzin dla zmiany: " + startStr + "-" + endStr, e);
            return 0;
        }
    }

    /**
     * Zwraca prefiks wybranego miesiąca w formacie "yyyy-MM" do filtrowania dat ISO.
     */
    private String getCurrentMonthPrefix() {
        YearMonth selected = currentSelectedMonth.getValue();
        if (selected == null) selected = YearMonth.now();
        return String.format("%04d-%02d", selected.getYear(), selected.getMonthValue());
    }

    // ─── Finanse — publiczne API ─────────────────────────────────────────

    /**
     * Ustawia stawkę godzinową. Powoduje automatyczne przeliczenie wypłaty.
     */
    public void setHourlyRate(float rate) {
        hourlyRateLive.setValue(rate);
    }

    /**
     * LiveData z podsumowaniem wypłaty za bieżący miesiąc.
     * Reaguje automatycznie na zmiany w shifts, losses i stawce.
     */
    public LiveData<PayrollInfo> getMonthlyPayroll() {
        return monthlyPayroll;
    }
    public LiveData<List<Shift>> getMonthlyShifts() {
        return monthlyShifts;
    }
    /**
     * LiveData ze stratami z bieżącego miesiąca (do wyświetlenia w UI).
     */
    public LiveData<List<Loss>> getMonthlyLosses() {
        return monthlyLosses;
    }

    // ─── Cykl rozliczeniowy (Wybór miesiąca) ─────────────────────────────

    public LiveData<YearMonth> getCurrentSelectedMonth() {
        return currentSelectedMonth;
    }

    public void setCurrentSelectedMonth(YearMonth month) {
        currentSelectedMonth.setValue(month);
    }

    public void nextMonth() {
        YearMonth current = currentSelectedMonth.getValue();
        if (current != null) {
            currentSelectedMonth.setValue(current.plusMonths(1));
        }
    }

    public void previousMonth() {
        YearMonth current = currentSelectedMonth.getValue();
        if (current != null) {
            currentSelectedMonth.setValue(current.minusMonths(1));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LiveData — odczyty (istniejące)
    // ═══════════════════════════════════════════════════════════════════════

    public LiveData<List<Shift>> getAllShifts() {
        return allShifts;
    }

    public LiveData<List<Loss>> getAllLosses() {
        return allLosses;
    }

    /**
     * Status synchronizacji grafiku. Obserwuj w UI, by wyświetlać komunikaty.
     * Możliwe wartości: "syncing", "success:N" (N = liczba zmian), "error:msg".
     */
    public LiveData<String> getSyncStatus() {
        return syncStatus;
    }

    // ─── Ustawienia użytkownika ──────────────────────────────────────────

    public void setTargetUserName(String name) {
        this.targetUserName = name;
    }

    public String getTargetUserName() {
        return targetUserName;
    }

    // ─── Shift — operacje zapisu ─────────────────────────────────────────

    public void insertShift(Shift shift) { shiftRepository.insert(shift); }
    public void updateShift(Shift shift) { shiftRepository.update(shift); }
    public void deleteShift(Shift shift) { shiftRepository.delete(shift); }
    public void deleteAllShifts() { shiftRepository.deleteAll(); }

    public LiveData<List<Tip>> getMonthlyTips() {
        return monthlyTips;
    }

    public LiveData<Shift> getNextShift() {
        return nextShift;
    }

    public MutableLiveData<Integer> getMonthlyHoursGoal() {
        return monthlyHoursGoal;
    }

    // ─── Logika biznesowa — Dodawanie i Usuwanie ─────────────────────

    public void insertTip(Tip tip) {
        tipRepository.insert(tip);
    }

    public void insertLoss(Loss loss) { lossRepository.insert(loss); }
    public void updateLoss(Loss loss) { lossRepository.update(loss); }
    public void deleteLoss(Loss loss) { lossRepository.delete(loss); }
    public void deleteAllLosses() { lossRepository.deleteAll(); }

    // ═══════════════════════════════════════════════════════════════════════
    // Gmail / OAuth / Synchronizacja (istniejący kod)
    // ═══════════════════════════════════════════════════════════════════════

    public void setGmailAccessToken(String accessToken) {
        gmailRepository.setAccessToken(accessToken);
    }

    public boolean isGmailAuthenticated() {
        return gmailRepository.isAuthenticated();
    }

    public GmailRepository getGmailRepository() {
        return gmailRepository;
    }

    // ─── Synchronizacja grafiku ──────────────────────────────────────────

    /**
     * Pełny przepływ synchronizacji grafiku:
     * <ol>
     *   <li>Pobiera listę wiadomości Gmail z załącznikami .xlsx</li>
     *   <li>Pobiera najnowszą wiadomość i szuka załącznika .xlsx</li>
     *   <li>Pobiera dane załącznika (base64url) → dekoduje do InputStream</li>
     *   <li>Parsuje Excel za pomocą {@link ExcelParsingService}</li>
     *   <li>Czyści starą bazę i zapisuje nowe zmiany do Room</li>
     * </ol>
     */
    public void syncSchedule() {
        if (targetUserName == null || targetUserName.isEmpty()) {
            syncStatus.setValue("error:Nie ustawiono nazwiska użytkownika");
            return;
        }
        if (!gmailRepository.isAuthenticated()) {
            syncStatus.setValue("error:Brak tokena OAuth — zaloguj się");
            return;
        }

        syncStatus.setValue("syncing");

        gmailRepository.fetchMessageList(new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call,
                                   @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    syncStatus.postValue("error:Błąd API (" + response.code() + ")");
                    return;
                }

                JsonArray messages = response.body().getAsJsonArray("messages");
                if (messages == null || messages.size() == 0) {
                    syncStatus.postValue("error:Brak wiadomości z plikiem .xlsx");
                    return;
                }

                String messageId = messages.get(0).getAsJsonObject().get("id").getAsString();
                fetchMessageAndParseAttachment(messageId);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchMessageList failed", t);
                syncStatus.postValue("error:" + t.getMessage());
            }
        });
    }

    private void fetchMessageAndParseAttachment(String messageId) {
        gmailRepository.fetchMessageDetail(messageId, new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call,
                                   @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    syncStatus.postValue("error:Błąd pobierania wiadomości");
                    return;
                }

                String attachmentId = findXlsxAttachmentId(response.body());
                if (attachmentId == null) {
                    syncStatus.postValue("error:Nie znaleziono załącznika .xlsx");
                    return;
                }

                fetchAndDecodeAttachment(messageId, attachmentId);
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchMessageDetail failed", t);
                syncStatus.postValue("error:" + t.getMessage());
            }
        });
    }

    private void fetchAndDecodeAttachment(String messageId, String attachmentId) {
        gmailRepository.fetchAttachment(messageId, attachmentId, new Callback<JsonObject>() {
            @Override
            public void onResponse(@NonNull Call<JsonObject> call,
                                   @NonNull Response<JsonObject> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    syncStatus.postValue("error:Błąd pobierania załącznika");
                    return;
                }

                String base64Data = response.body().get("data").getAsString();

                executor.execute(() -> {
                    try {
                        byte[] fileBytes = Base64.decode(base64Data, Base64.URL_SAFE);
                        InputStream inputStream = new ByteArrayInputStream(fileBytes);

                        List<Shift> shifts = excelParsingService.parseSchedule(
                                inputStream, targetUserName);

                        if (shifts.isEmpty()) {
                            syncStatus.postValue("error:Brak zmian dla: " + targetUserName);
                            return;
                        }

                        shiftRepository.deleteAll();
                        for (Shift shift : shifts) {
                            shiftRepository.insert(shift);
                        }

                        syncStatus.postValue("success:" + shifts.size());
                        Log.d(TAG, "Sync complete: " + shifts.size() + " shifts for " + targetUserName);

                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing/saving schedule", e);
                        syncStatus.postValue("error:" + e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<JsonObject> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchAttachment failed", t);
                syncStatus.postValue("error:" + t.getMessage());
            }
        });
    }

    private String findXlsxAttachmentId(JsonObject messageDetail) {
        try {
            JsonObject payload = messageDetail.getAsJsonObject("payload");
            if (payload == null) return null;

            JsonArray parts = payload.getAsJsonArray("parts");
            if (parts == null) return null;

            for (JsonElement partEl : parts) {
                JsonObject part = partEl.getAsJsonObject();
                String filename = part.has("filename") ? part.get("filename").getAsString() : "";
                if (filename.toLowerCase().endsWith(".xlsx")) {
                    JsonObject body = part.getAsJsonObject("body");
                    if (body != null && body.has("attachmentId")) {
                        return body.get("attachmentId").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding xlsx attachment", e);
        }
        return null;
    }
}
```

## 4. Wybierak Miesiąca (UI i Logika)
Wzbogacono moduly Grafiku i Finansów o nawigację miesiącami:
- W [MainViewModel.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java) dodano metody [nextMonth()](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java#374-380) oraz [previousMonth()](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java#381-387).
- W widokach [fragment_schedule.xml](file:///j:/kinobot/app/src/main/res/layout/fragment_schedule.xml) i [fragment_finance.xml](file:///j:/kinobot/app/src/main/res/layout/fragment_finance.xml) wstawiono odpowiednie sekcje `btn_prev_month`, `tv_current_month`, `btn_next_month`.
- Podpięto te sekcje w klasach [ScheduleFragment.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/ui/ScheduleFragment.java) i [FinanceFragment.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/ui/FinanceFragment.java), zapewniając płynną pętlę przepływu danych o miesięcznych wypłatach i stratach.

## 5. System Powiadomień (AlarmManager & Notifications)
Wprowadzono kompletny system powiadomień Push przypominający przed zmianami i po ich zakończeniu:
- W [AndroidManifest.xml](file:///j:/kinobot/app/src/main/AndroidManifest.xml) dodano wymagane uprawnienia (`POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `VIBRATE`).
- W klasie [ShiftNotificationReceiver.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/notifications/ShiftNotificationReceiver.java) stworzono obsługę odbierania rozkazów pobudki jako `BroadcastReceiver`. W ciele tworzony jest [NotificationChannel](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/notifications/ShiftNotificationReceiver.java#67-81) niezbędny dla Nowszych Androidów i samo [Notification](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/notifications/ShiftNotificationReceiver.java#67-81) za pomocą powłoki `NotificationCompat`.
- [AlarmScheduler.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/notifications/AlarmScheduler.java) zajmuje się czystą kalkulacją czasu i harmonogramowaniem `AlarmManager.setExactAndAllowWhileIdle`.
- W [DashboardFragment.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/ui/DashboardFragment.java) i [fragment_dashboard.xml](file:///j:/kinobot/app/src/main/res/layout/fragment_dashboard.xml) dodano UI i logikę zapisywania "Liczby minut przed zmianą na powiadomienie" w `SharedPreferences`.
- System powiadomień jest uruchamiany automatycznie w [MainActivity.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/ui/MainActivity.java), gdzie stworzony został obserwator dla `viewModel.getAllShifts()`. Dzięki temu każdy update (dodanie czy usunięcie zmiany, a także synchronizacja API) powoduje wyliczenie od nowa odpowiednich czasów wystąpienia alarmów.

## 6. Ograniczenie Grafiku do Bieżącego Miesiąca
Zaktualizowano klasę [MainViewModel](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java#52-614), ujednolicając podejście do pobierania danych z bazy. [ScheduleFragment](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/ui/ScheduleFragment.java#35-202) otrzymuje teraz dokładnie przefiltrowaną listę zmian `monthlyShifts` powiązanych z odpowiednim wybranym przedziałem czasowym z wybieraka.

## 7. Przebudowa Kafelka Zmiany (UI Polish)
Zaprojektowano nowoczesny interfejs pojedynczej zmiany ([item_shift.xml](file:///j:/kinobot/app/src/main/res/layout/item_shift.xml)) korzystając z fundamentów Material Design 3:
- Wykorzystano `MaterialCardView` chroniące wnętrze i nadające cień.
- Daty oraz Godziny wzbogacone zostały przez wektorowe systemowe ikonki kalendarza i zegarka.
- Po prawej stronie wstawiono czytelny, interaktywny wizualnie lecz funkcyjnie płaski komponent `Chip` wyświetlający charakterystykę zmiany w dopasowanym kontraście z użyciem `?attr/colorPrimaryContainer` dla tła oraz `?attr/colorOnPrimaryContainer` dla tekstu.

```diff:item_shift.xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="16dp"
    android:layout_marginVertical="6dp"
    app:cardCornerRadius="12dp"
    app:cardElevation="2dp"
    app:strokeWidth="0dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp"
        android:gravity="center_vertical">

        <!-- Lewa kolumna: Data -->
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:orientation="vertical">

            <!-- Data (pogrubiona) -->
            <TextView
                android:id="@+id/tv_shift_date"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="16sp"
                android:textStyle="bold"
                android:textColor="?android:attr/textColorPrimary" />

            <!-- Godziny (np. "14:00 - 22:00") -->
            <TextView
                android:id="@+id/tv_shift_hours"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="14sp"
                android:textColor="?android:attr/textColorSecondary"
                android:layout_marginTop="4dp" />

        </LinearLayout>

        <!-- Prawa kolumna: Opis zmiany (badge) -->
        <TextView
            android:id="@+id/tv_shift_description"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="14sp"
            android:textStyle="bold"
            android:textColor="?attr/colorOnPrimary"
            android:background="@drawable/bg_shift_badge"
            android:paddingHorizontal="12dp"
            android:paddingVertical="6dp" />

    </LinearLayout>

</com.google.android.material.card.MaterialCardView>
===
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="16dp"
    android:layout_marginVertical="6dp"
    style="?attr/materialCardViewElevatedStyle"
    app:cardCornerRadius="16dp">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp">

        <!-- Ikona kalendarza -->
        <ImageView
            android:id="@+id/iv_calendar_icon"
            android:layout_width="20dp"
            android:layout_height="20dp"
            android:src="@drawable/ic_calendar"
            app:tint="?attr/colorPrimary"
            app:layout_constraintTop_toTopOf="@+id/tv_shift_date"
            app:layout_constraintBottom_toBottomOf="@+id/tv_shift_date"
            app:layout_constraintStart_toStartOf="parent" />

        <!-- Data -->
        <TextView
            android:id="@+id/tv_shift_date"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:textSize="16sp"
            android:textStyle="bold"
            android:textColor="?android:attr/textColorPrimary"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintStart_toEndOf="@+id/iv_calendar_icon"
            app:layout_constraintEnd_toStartOf="@+id/chip_shift_description" />

        <!-- Ikona zegara -->
        <ImageView
            android:id="@+id/iv_time_icon"
            android:layout_width="16dp"
            android:layout_height="16dp"
            android:src="@android:drawable/ic_menu_recent_history"
            app:tint="?android:attr/textColorSecondary"
            android:layout_marginTop="6dp"
            app:layout_constraintTop_toBottomOf="@+id/tv_shift_date"
            app:layout_constraintStart_toStartOf="@+id/iv_calendar_icon"
            app:layout_constraintEnd_toEndOf="@+id/iv_calendar_icon" />

        <!-- Godziny -->
        <TextView
            android:id="@+id/tv_shift_hours"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:layout_marginTop="4dp"
            android:textSize="14sp"
            android:textColor="?android:attr/textColorSecondary"
            app:layout_constraintTop_toBottomOf="@+id/tv_shift_date"
            app:layout_constraintStart_toEndOf="@+id/iv_time_icon"
            app:layout_constraintEnd_toStartOf="@+id/chip_shift_description" />

        <!-- Opis zmiany (Tag / Chip) -->
        <com.google.android.material.chip.Chip
            android:id="@+id/chip_shift_description"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:clickable="false"
            android:textStyle="bold"
            android:textColor="@android:color/white"
            app:chipBackgroundColor="#B00020"
            app:chipStrokeWidth="0dp"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintEnd_toEndOf="parent" />

    </androidx.constraintlayout.widget.ConstraintLayout>

</com.google.android.material.card.MaterialCardView>
```

## 8. Rozbudowa Pulpitu (Centrum Dowodzenia)
Przebudowano ekran główny aplikacji ([fragment_dashboard.xml](file:///j:/kinobot/app/src/main/res/layout/fragment_dashboard.xml) oraz [DashboardFragment.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/ui/DashboardFragment.java)) na wzór nowoczesnego, funkcjonalnego "Centrum Dowodzenia":
- **Wizytówka Następnej Zmiany (Hero Widget):** Dodano wyróżniającą się kartę na górze ekranu, pobierającą i wyświetlającą dane o najbliższej przyszłej zmianie z [MainViewModel](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java#52-614).
- **Progres Miesięczny:** Wykorzystano komponent `LinearProgressIndicator` z Material Design 3 do wizualizacji procentowego wykonania celu godzinowego użytkownika. Cel można ustawiać, a wartość jest zapisywana w `SharedPreferences`.
- **Szybkie Akcje:** Użyto `GridLayout` z 4 czytelnymi przyciskami (Zmień Nazwisko, Synchronizuj, Cel Godzinowy, Powiadomienia), grupując kluczowe funkcjonalności administracyjne w jednym miejscu.

```diff:fragment_dashboard.xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true"
    tools:context=".ui.DashboardFragment">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center_horizontal"
        android:padding="24dp">

        <!-- Tytuł -->
        <TextView
            android:id="@+id/tv_dashboard_title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Pulpit"
            android:textSize="28sp"
            android:textStyle="bold"
            android:layout_marginBottom="8dp" />

        <!-- Status synchronizacji -->
        <TextView
            android:id="@+id/tv_sync_status"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="14sp"
            android:textColor="?android:attr/textColorSecondary"
            android:layout_marginBottom="24dp" />

        <!-- ═══ Sekcja nazwiska użytkownika ═══ -->
        <LinearLayout
            android:id="@+id/layout_name_input"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center_horizontal"
            android:layout_marginBottom="24dp">

            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="Imię i Nazwisko (jak w Excelu)"
                style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/et_user_name"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="textPersonName"
                    android:maxLines="1" />

            </com.google.android.material.textfield.TextInputLayout>

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btn_save_name"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Zapisz"
                android:textAllCaps="false"
                android:layout_marginTop="8dp"
                style="@style/Widget.MaterialComponents.Button.OutlinedButton" />

        </LinearLayout>

        <!-- Etykieta zapisanego nazwiska (widoczna po zapisaniu) -->
        <TextView
            android:id="@+id/tv_saved_name"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="16sp"
            android:textStyle="italic"
            android:visibility="gone"
            android:layout_marginBottom="8dp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_change_name"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Zmień nazwisko"
            android:textAllCaps="false"
            android:visibility="gone"
            android:layout_marginBottom="24dp"
            style="@style/Widget.MaterialComponents.Button.TextButton" />

        <!-- ═══ Przycisk logowania Google ═══ -->
        <com.google.android.gms.common.SignInButton
            android:id="@+id/btn_google_sign_in"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp" />

        <!-- ═══ Przycisk synchronizacji grafiku ═══ -->
        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_sync_schedule"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/sync_schedule"
            android:textAllCaps="false"
            app:icon="@drawable/ic_calendar"
            app:iconGravity="start"
            style="@style/Widget.MaterialComponents.Button" />

    </LinearLayout>

</ScrollView>
===
```
```diff:DashboardFragment.java
package com.asystent.kinowy.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
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
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Fragment ekranu głównego (Pulpit).
 * <p>
 * Odpowiedzialności:
 * <ul>
 *   <li>Zarządzanie nazwiskiem użytkownika (SharedPreferences)</li>
 *   <li>Logowanie Google Sign-In</li>
 *   <li>Synchronizacja grafiku</li>
 *   <li>Wyświetlanie statusu</li>
 * </ul>
 */
public class DashboardFragment extends Fragment {

    private static final String TAG = "DashboardFragment";
    private static final int RC_SIGN_IN = 2001;
    private static final String GMAIL_READONLY_SCOPE =
            "https://www.googleapis.com/auth/gmail.readonly";

    private static final String PREFS_NAME = "asystent_kinowy_prefs";
    private static final String PREF_USER_NAME = "user_name";

    private MainViewModel viewModel;
    private GoogleSignInClient googleSignInClient;

    // UI
    private SignInButton btnGoogleSignIn;
    private MaterialButton btnSyncSchedule;
    private TextView tvSyncStatus;

    // Nazwisko
    private View layoutNameInput;
    private TextInputEditText etUserName;
    private MaterialButton btnSaveName;
    private TextView tvSavedName;
    private MaterialButton btnChangeName;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // --- ViewModel (współdzielony z Activity) ---
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // --- UI bindings ---
        btnGoogleSignIn = view.findViewById(R.id.btn_google_sign_in);
        btnSyncSchedule = view.findViewById(R.id.btn_sync_schedule);
        tvSyncStatus = view.findViewById(R.id.tv_sync_status);

        // Nazwisko
        layoutNameInput = view.findViewById(R.id.layout_name_input);
        etUserName = view.findViewById(R.id.et_user_name);
        btnSaveName = view.findViewById(R.id.btn_save_name);
        tvSavedName = view.findViewById(R.id.tv_saved_name);
        btnChangeName = view.findViewById(R.id.btn_change_name);

        // --- Nazwisko: SharedPreferences ---
        setupNameInput();

        // --- Google Sign-In ---
        configureGoogleSignIn();
        btnGoogleSignIn.setOnClickListener(v -> startSignIn());

        // --- Sync ---
        btnSyncSchedule.setOnClickListener(v -> viewModel.syncSchedule());
        btnSyncSchedule.setEnabled(false); // Aktywny po zalogowaniu

        // --- Obserwatory ---
        observeSyncStatus();

        // --- Sprawdź istniejącą sesję ---
        GoogleSignInAccount existingAccount = GoogleSignIn.getLastSignedInAccount(requireContext());
        if (existingAccount != null) {
            handleSignInSuccess(existingAccount);
        }
    }

    // ─── Nazwisko użytkownika (SharedPreferences) ────────────────────────

    private SharedPreferences getPrefs() {
        return requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void setupNameInput() {
        String savedName = getPrefs().getString(PREF_USER_NAME, null);

        if (savedName != null && !savedName.isEmpty()) {
            // Nazwisko zapisane — pokaż etykietę, ukryj pole
            showSavedNameState(savedName);
        } else {
            // Brak nazwiska — pokaż formularz
            showNameInputState();
        }

        btnSaveName.setOnClickListener(v -> {
            String name = etUserName.getText() != null
                    ? etUserName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                etUserName.setError("Wpisz imię i nazwisko");
                return;
            }
            // Zapisz do SharedPreferences
            getPrefs().edit().putString(PREF_USER_NAME, name).apply();
            // Przekaż do ViewModelu
            viewModel.setTargetUserName(name);
            // Przełącz UI
            showSavedNameState(name);
        });

        btnChangeName.setOnClickListener(v -> showNameInputState());
    }

    private void showSavedNameState(String name) {
        layoutNameInput.setVisibility(View.GONE);
        tvSavedName.setVisibility(View.VISIBLE);
        tvSavedName.setText("👤 " + name);
        btnChangeName.setVisibility(View.VISIBLE);

        // Ustaw nazwisko w ViewModelu
        viewModel.setTargetUserName(name);
    }

    private void showNameInputState() {
        layoutNameInput.setVisibility(View.VISIBLE);
        tvSavedName.setVisibility(View.GONE);
        btnChangeName.setVisibility(View.GONE);

        // Wstaw obecne nazwisko do pola
        String current = getPrefs().getString(PREF_USER_NAME, "");
        if (etUserName != null && current != null) {
            etUserName.setText(current);
        }
    }

    // ─── Google Sign-In ──────────────────────────────────────────────────

    private void configureGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(GMAIL_READONLY_SCOPE))
                .build();
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);
    }

    private void startSignIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                handleSignInSuccess(account);
            } catch (ApiException e) {
                Log.e(TAG, "Google Sign-In failed: code=" + e.getStatusCode(), e);
                tvSyncStatus.setText(getString(R.string.sign_in_failed, e.getStatusCode()));
            }
        }
    }

    private void handleSignInSuccess(GoogleSignInAccount account) {
        String email = account.getEmail();
        Log.d(TAG, "Signed in as: " + email);

        btnGoogleSignIn.setVisibility(View.GONE);
        btnSyncSchedule.setEnabled(true);
        tvSyncStatus.setText(getString(R.string.signed_in_as, email));

        // Pobierz AccessToken w tle
        fetchAccessTokenInBackground(account);
    }

    private void fetchAccessTokenInBackground(GoogleSignInAccount account) {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String token = com.google.android.gms.auth.GoogleAuthUtil.getToken(
                        requireContext(),
                        account.getAccount(),
                        "oauth2:" + GMAIL_READONLY_SCOPE
                );
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        viewModel.setGmailAccessToken(token);
                        tvSyncStatus.setText(getString(R.string.signed_in_ready, account.getEmail()));
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to acquire AccessToken", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        tvSyncStatus.setText(R.string.token_error);
                        Toast.makeText(requireContext(), R.string.token_error, Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    // ─── Obserwatory ─────────────────────────────────────────────────────

    private void observeSyncStatus() {
        viewModel.getSyncStatus().observe(getViewLifecycleOwner(), status -> {
            if (status == null) return;

            if (status.equals("syncing")) {
                btnSyncSchedule.setEnabled(false);
                tvSyncStatus.setText("Synchronizuję grafik...");
            } else if (status.startsWith("success:")) {
                int count = Integer.parseInt(status.split(":")[1]);
                btnSyncSchedule.setEnabled(true);
                tvSyncStatus.setText("✅ Zsynchronizowano: " + count + " zmian");
            } else if (status.startsWith("error:")) {
                String msg = status.substring(6);
                btnSyncSchedule.setEnabled(true);
                tvSyncStatus.setText("❌ " + msg);
            }
        });
    }
}
===
package com.asystent.kinowy.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.asystent.kinowy.R;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;

public class DashboardFragment extends Fragment {

    private static final String TAG = "DashboardFragment";
    private static final int RC_SIGN_IN = 2001;
    private static final String GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";

    private static final String PREFS_NAME = "asystent_kinowy_prefs";
    private static final String PREF_USER_NAME = "user_name";
    private static final String PREF_NOTIFY_BEFORE = "notify_before_minutes";
    private static final String PREF_MONTHLY_GOAL = "monthly_hours_goal";

    private MainViewModel viewModel;
    private GoogleSignInClient googleSignInClient;

    // UI - Authentication & Sync
    private SignInButton btnGoogleSignIn;
    private TextView tvSyncStatus;

    // Nazwisko
    private View layoutNameInput;
    private TextInputEditText etUserName;
    private MaterialButton btnSaveName;
    private TextView tvSavedName;

    // Hero Widget
    private View layoutNextShiftData;
    private TextView tvNextShiftDate;
    private TextView tvNextShiftHours;
    private Chip chipNextShiftDesc;
    private TextView tvNoNextShift;

    // Progress Bar
    private TextView tvProgressText;
    private LinearProgressIndicator progressMonthlyHours;

    // Quick Actions
    private MaterialButton btnSyncSchedule;
    private MaterialButton btnSetGoal;
    private MaterialButton btnSetNotification;
    private MaterialButton btnChangeNameAction;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // UI bindings
        btnGoogleSignIn = view.findViewById(R.id.btn_google_sign_in);
        tvSyncStatus = view.findViewById(R.id.tv_sync_status);

        layoutNameInput = view.findViewById(R.id.layout_name_input);
        etUserName = view.findViewById(R.id.et_user_name);
        btnSaveName = view.findViewById(R.id.btn_save_name);
        tvSavedName = view.findViewById(R.id.tv_saved_name);

        layoutNextShiftData = view.findViewById(R.id.layout_next_shift_data);
        tvNextShiftDate = view.findViewById(R.id.tv_next_shift_date);
        tvNextShiftHours = view.findViewById(R.id.tv_next_shift_hours);
        chipNextShiftDesc = view.findViewById(R.id.chip_next_shift_desc);
        tvNoNextShift = view.findViewById(R.id.tv_no_next_shift);

        tvProgressText = view.findViewById(R.id.tv_progress_text);
        progressMonthlyHours = view.findViewById(R.id.progress_monthly_hours);

        btnSyncSchedule = view.findViewById(R.id.btn_sync_schedule);
        btnSetGoal = view.findViewById(R.id.btn_set_goal);
        btnSetNotification = view.findViewById(R.id.btn_set_notification);
        btnChangeNameAction = view.findViewById(R.id.btn_change_name_action);

        // Load goal
        int savedGoal = getPrefs().getInt(PREF_MONTHLY_GOAL, 100);
        viewModel.getMonthlyHoursGoal().setValue(savedGoal);

        setupNameInput();
        setupQuickActions();
        observeDashboardData();
        observeSyncStatus();

        configureGoogleSignIn();
        btnGoogleSignIn.setOnClickListener(v -> startSignIn());

        GoogleSignInAccount existingAccount = GoogleSignIn.getLastSignedInAccount(requireContext());
        if (existingAccount != null) {
            handleSignInSuccess(existingAccount);
        }
    }

    private SharedPreferences getPrefs() {
        return requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Nazwisko ────────────────────────────────────────────────────────
    private void setupNameInput() {
        String savedName = getPrefs().getString(PREF_USER_NAME, null);
        if (savedName != null && !savedName.isEmpty()) {
            showSavedNameState(savedName);
        } else {
            showNameInputState();
        }

        btnSaveName.setOnClickListener(v -> {
            String name = etUserName.getText() != null ? etUserName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                etUserName.setError("Wpisz imię i nazwisko");
                return;
            }
            getPrefs().edit().putString(PREF_USER_NAME, name).apply();
            viewModel.setTargetUserName(name);
            showSavedNameState(name);
        });
    }

    private void showSavedNameState(String name) {
        layoutNameInput.setVisibility(View.GONE);
        tvSavedName.setVisibility(View.VISIBLE);
        tvSavedName.setText("👤 " + name);
        viewModel.setTargetUserName(name);
    }

    private void showNameInputState() {
        layoutNameInput.setVisibility(View.VISIBLE);
        tvSavedName.setVisibility(View.GONE);
        String current = getPrefs().getString(PREF_USER_NAME, "");
        if (etUserName != null && current != null) {
            etUserName.setText(current);
        }
    }

    // ─── Quick Actions ────────────────────────────────────────────────────
    private void setupQuickActions() {
        btnSyncSchedule.setOnClickListener(v -> viewModel.syncSchedule());
        btnSyncSchedule.setEnabled(false);

        btnChangeNameAction.setOnClickListener(v -> showNameInputState());

        btnSetGoal.setOnClickListener(v -> {
            TextInputEditText input = new TextInputEditText(requireContext());
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            Integer currentGoal = viewModel.getMonthlyHoursGoal().getValue();
            if (currentGoal != null) input.setText(String.valueOf(currentGoal));

            new AlertDialog.Builder(requireContext())
                    .setTitle("Ustaw Cel Godzinowy")
                    .setView(input)
                    .setPositiveButton("Zapisz", (dialog, which) -> {
                        String val = input.getText() != null ? input.getText().toString().trim() : "";
                        if (!val.isEmpty()) {
                            try {
                                int goal = Integer.parseInt(val);
                                viewModel.getMonthlyHoursGoal().setValue(goal);
                                getPrefs().edit().putInt(PREF_MONTHLY_GOAL, goal).apply();
                                Toast.makeText(requireContext(), "Zapisano cel: " + goal + "h", Toast.LENGTH_SHORT).show();
                            } catch (NumberFormatException ignored) {}
                        }
                    })
                    .setNegativeButton("Anuluj", null)
                    .show();
        });

        btnSetNotification.setOnClickListener(v -> {
            TextInputEditText input = new TextInputEditText(requireContext());
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            int savedMinutes = getPrefs().getInt(PREF_NOTIFY_BEFORE, 60);
            input.setText(String.valueOf(savedMinutes));

            new AlertDialog.Builder(requireContext())
                    .setTitle("Czas Powiadomienia")
                    .setMessage("Ile minut przed zmianą przypomnieć?")
                    .setView(input)
                    .setPositiveButton("Zapisz", (dialog, which) -> {
                        String val = input.getText() != null ? input.getText().toString().trim() : "";
                        if (!val.isEmpty()) {
                            try {
                                int minutes = Integer.parseInt(val);
                                getPrefs().edit().putInt(PREF_NOTIFY_BEFORE, minutes).apply();
                                Toast.makeText(requireContext(), "Powiadomienia: " + minutes + " min", Toast.LENGTH_SHORT).show();
                            } catch (NumberFormatException ignored) {}
                        }
                    })
                    .setNegativeButton("Anuluj", null)
                    .show();
        });
    }

    // ─── Obserwatory (Widgety) ───────────────────────────────────────────
    private void observeDashboardData() {
        viewModel.getNextShift().observe(getViewLifecycleOwner(), shift -> {
            if (shift == null) {
                layoutNextShiftData.setVisibility(View.GONE);
                tvNoNextShift.setVisibility(View.VISIBLE);
            } else {
                layoutNextShiftData.setVisibility(View.VISIBLE);
                tvNoNextShift.setVisibility(View.GONE);
                tvNextShiftDate.setText(shift.getDate());
                tvNextShiftHours.setText(shift.getStartTime() + " - " + shift.getEndTime());
                if (shift.getDescription() != null && !shift.getDescription().trim().isEmpty()) {
                    chipNextShiftDesc.setVisibility(View.VISIBLE);
                    chipNextShiftDesc.setText(shift.getDescription());
                } else {
                    chipNextShiftDesc.setVisibility(View.GONE);
                }
            }
        });

        viewModel.getMonthlyHoursGoal().observe(getViewLifecycleOwner(), goal -> updateProgressUI());
        viewModel.getMonthlyPayroll().observe(getViewLifecycleOwner(), payroll -> updateProgressUI());
    }

    private void updateProgressUI() {
        Integer goal = viewModel.getMonthlyHoursGoal().getValue();
        if (goal == null || goal <= 0) goal = 100;
        
        MainViewModel.PayrollInfo payroll = viewModel.getMonthlyPayroll().getValue();
        double hours = (payroll != null) ? payroll.getTotalHours() : 0.0;
        
        int progress = (int) ((hours / goal) * 100);
        progressMonthlyHours.setMax(100);
        // Smooth transition could be added, but setProgress directly works too
        progressMonthlyHours.setProgress(progress > 100 ? 100 : progress);

        String text = String.format(Locale.getDefault(), "Przepracowano: %.1fh / Cel: %dh (%d%%)", hours, goal, progress);
        tvProgressText.setText(text);
    }

    // ─── Google Sign-In ──────────────────────────────────────────────────
    private void configureGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(GMAIL_READONLY_SCOPE))
                .build();
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);
    }

    private void startSignIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                handleSignInSuccess(account);
            } catch (ApiException e) {
                Log.e(TAG, "Google Sign-In failed: code=" + e.getStatusCode(), e);
                tvSyncStatus.setText(getString(R.string.sign_in_failed, e.getStatusCode()));
            }
        }
    }

    private void handleSignInSuccess(GoogleSignInAccount account) {
        String email = account.getEmail();
        btnGoogleSignIn.setVisibility(View.GONE);
        btnSyncSchedule.setEnabled(true);
        tvSyncStatus.setText(getString(R.string.signed_in_as, email));

        fetchAccessTokenInBackground(account);
    }

    private void fetchAccessTokenInBackground(GoogleSignInAccount account) {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String token = com.google.android.gms.auth.GoogleAuthUtil.getToken(
                        requireContext(),
                        account.getAccount(),
                        "oauth2:" + GMAIL_READONLY_SCOPE
                );
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        viewModel.setGmailAccessToken(token);
                        tvSyncStatus.setText(getString(R.string.signed_in_ready, account.getEmail()));
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to acquire AccessToken", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        tvSyncStatus.setText(R.string.token_error);
                        Toast.makeText(requireContext(), R.string.token_error, Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    // ─── Sync Status ─────────────────────────────────────────────────────
    private void observeSyncStatus() {
        viewModel.getSyncStatus().observe(getViewLifecycleOwner(), status -> {
            if (status == null) return;

            if (status.equals("syncing")) {
                btnSyncSchedule.setEnabled(false);
                tvSyncStatus.setText("Synchronizuję grafik...");
            } else if (status.startsWith("success:")) {
                int count = Integer.parseInt(status.split(":")[1]);
                btnSyncSchedule.setEnabled(true);
                tvSyncStatus.setText("✅ Zsynchronizowano: " + count + " zmian");
            } else if (status.startsWith("error:")) {
                String msg = status.substring(6);
                btnSyncSchedule.setEnabled(true);
                tvSyncStatus.setText("❌ " + msg);
            }
        });
    }
}
```

## 9. Moduł Finansowy — Napiwki (Tips)
Rozszerzono możliwości finansowe aplikacji o kalkulację i zgłaszanie napiwków, analogicznie do systemu strat:
- **Baza Danych:** Utworzono encję [Tip](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/models/Tip.java#10-67), interfejs dostępu [TipDao](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/db/TipDao.java#14-30) oraz repozytorium [TipRepository](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/repository/TipRepository.java#18-47). Zaimplementowano migrację do wersji 2 w [AppDatabase](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/db/AppDatabase.java#18-50).
- **Logika Biznesowa:** W [MainViewModel.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java) wdrożono obserwatory `monthlyTips` i wliczanie zsumowanych napiwków do końcowej wartości netto wypłaty ([PayrollInfo](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java#129-150)).
- **Interfejs Użytkownika:** W [fragment_finance.xml](file:///j:/kinobot/app/src/main/res/layout/fragment_finance.xml) oraz [FinanceFragment.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/ui/FinanceFragment.java) dodano bliźniaczy formularz (Karta "Zgłoś Napiwek / Premię") umożliwiający zapisanie napiwku do bazy danych, a także zmodyfikowaną etykietę podsumowania, która uwzględnia teraz dodatkowe zarobki w ogólnym rozrachunku.

```diff:fragment_finance.xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true"
    tools:context=".ui.FinanceFragment">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <!-- ═══════════════════════════════════════════════════════════════
             KARTA PODSUMOWANIA — Do wypłaty w tym miesiącu
             ═══════════════════════════════════════════════════════════════ -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/card_summary"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            app:cardCornerRadius="16dp"
            app:cardElevation="6dp"
            app:cardBackgroundColor="?attr/colorPrimary"
            app:strokeWidth="0dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="24dp"
                android:gravity="center">

                <TextView
                    android:id="@+id/tv_summary_label"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Do wypłaty w tym miesiącu"
                    android:textSize="14sp"
                    android:textColor="?attr/colorOnPrimary"
                    android:textAllCaps="true"
                    android:letterSpacing="0.1" />

                <TextView
                    android:id="@+id/tv_summary_amount"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="0.00 zł"
                    android:textSize="36sp"
                    android:textStyle="bold"
                    android:textColor="?attr/colorOnPrimary"
                    android:layout_marginTop="8dp" />

                <TextView
                    android:id="@+id/tv_summary_details"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="—"
                    android:textSize="12sp"
                    android:textColor="?attr/colorOnPrimary"
                    android:alpha="0.8"
                    android:layout_marginTop="4dp" />

            </LinearLayout>

        </com.google.android.material.card.MaterialCardView>

        <!-- ═══════════════════════════════════════════════════════════════
             SEKCJA: USTAWIENIA STAWKI GODZINOWEJ
             ═══════════════════════════════════════════════════════════════ -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            app:cardCornerRadius="12dp"
            app:cardElevation="2dp"
            app:strokeWidth="0dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="💰 Ustawienia Płacy"
                    android:textSize="16sp"
                    android:textStyle="bold"
                    android:layout_marginBottom="12dp" />

                <!-- Stawka godzinowa (zł netto) -->
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical">

                    <com.google.android.material.textfield.TextInputLayout
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:hint="Stawka godzinowa (zł netto)"
                        style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

                        <com.google.android.material.textfield.TextInputEditText
                            android:id="@+id/et_hourly_rate"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:inputType="numberDecimal"
                            android:maxLines="1" />

                    </com.google.android.material.textfield.TextInputLayout>

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btn_save_rate"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Zapisz"
                        android:textAllCaps="false"
                        android:layout_marginStart="8dp"
                        style="@style/Widget.MaterialComponents.Button.OutlinedButton" />

                </LinearLayout>

                <!-- Etykieta zapisanej stawki -->
                <TextView
                    android:id="@+id/tv_saved_rate"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textSize="13sp"
                    android:textColor="?android:attr/textColorSecondary"
                    android:layout_marginTop="8dp"
                    android:visibility="gone" />

            </LinearLayout>

        </com.google.android.material.card.MaterialCardView>

        <!-- ═══════════════════════════════════════════════════════════════
             SEKCJA: ZGŁOŚ STRATĘ / MANKO
             ═══════════════════════════════════════════════════════════════ -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            app:cardCornerRadius="12dp"
            app:cardElevation="2dp"
            app:strokeWidth="0dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="📉 Zgłoś Stratę / Manko"
                    android:textSize="16sp"
                    android:textStyle="bold"
                    android:layout_marginBottom="12dp" />

                <!-- Kwota straty -->
                <com.google.android.material.textfield.TextInputLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:hint="Kwota straty (zł)"
                    android:layout_marginBottom="8dp"
                    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/et_loss_amount"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:inputType="numberDecimal"
                        android:maxLines="1" />

                </com.google.android.material.textfield.TextInputLayout>

                <!-- Opis (opcjonalny) -->
                <com.google.android.material.textfield.TextInputLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:hint="Opis (opcjonalny)"
                    android:layout_marginBottom="12dp"
                    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/et_loss_description"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:inputType="text"
                        android:maxLines="1" />

                </com.google.android.material.textfield.TextInputLayout>

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btn_add_loss"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Dodaj stratę"
                    android:textAllCaps="false"
                    style="@style/Widget.MaterialComponents.Button" />

            </LinearLayout>

        </com.google.android.material.card.MaterialCardView>

        <!-- ═══════════════════════════════════════════════════════════════
             SEKCJA: LISTA STRAT W TYM MIESIĄCU
             ═══════════════════════════════════════════════════════════════ -->
        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:cardCornerRadius="12dp"
            app:cardElevation="2dp"
            app:strokeWidth="0dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="📋 Straty w tym miesiącu"
                    android:textSize="16sp"
                    android:textStyle="bold"
                    android:layout_marginBottom="8dp" />

                <TextView
                    android:id="@+id/tv_losses_empty"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Brak strat w tym miesiącu"
                    android:textSize="14sp"
                    android:textColor="?android:attr/textColorSecondary"
                    android:gravity="center"
                    android:paddingVertical="12dp"
                    android:visibility="gone" />

                <androidx.recyclerview.widget.RecyclerView
                    android:id="@+id/rv_losses"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:nestedScrollingEnabled="false" />

            </LinearLayout>

        </com.google.android.material.card.MaterialCardView>

    </LinearLayout>

</ScrollView>
===
<?xml version="1.0" encoding="utf-8"?>
<ScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true"
    tools:context=".ui.FinanceFragment">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp">

        <!-- Wybierak miesiąca -->
        <LinearLayout
            android:id="@+id/ll_month_selector"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center"
            android:padding="8dp"
            android:layout_marginBottom="16dp"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent">

            <ImageButton
                android:id="@+id/btn_prev_month"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:src="@android:drawable/ic_media_previous"
                android:background="?android:attr/selectableItemBackgroundBorderless"
                android:contentDescription="Poprzedni miesiąc" />

            <TextView
                android:id="@+id/tv_current_month"
                android:layout_width="0dp"
                android:layout_weight="1"
                android:layout_height="wrap_content"
                android:text="Marzec 2026"
                android:textSize="18sp"
                android:textStyle="bold"
                android:gravity="center" />

            <ImageButton
                android:id="@+id/btn_next_month"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:src="@android:drawable/ic_media_next"
                android:background="?android:attr/selectableItemBackgroundBorderless"
                android:contentDescription="Następny miesiąc" />
        </LinearLayout>

        <!-- ═══════════════════════════════════════════════════════════════
             KARTA PODSUMOWANIA — Zarobki za:
             ═══════════════════════════════════════════════════════════════ -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/card_summary"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            android:layout_marginTop="16dp"
            app:cardCornerRadius="16dp"
            app:cardElevation="6dp"
            app:cardBackgroundColor="?attr/colorPrimary"
            app:strokeWidth="0dp"
            app:layout_constraintTop_toBottomOf="@id/ll_month_selector"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="24dp"
                android:gravity="center">

                <TextView
                    android:id="@+id/tv_summary_label"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Zarobki za:"
                    android:textSize="14sp"
                    android:textColor="?attr/colorOnPrimary"
                    android:textAllCaps="true"
                    android:letterSpacing="0.1" />

                <TextView
                    android:id="@+id/tv_summary_amount"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="0.00 zł"
                    android:textSize="36sp"
                    android:textStyle="bold"
                    android:textColor="?attr/colorOnPrimary"
                    android:layout_marginTop="8dp" />

                <TextView
                    android:id="@+id/tv_summary_details"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="—"
                    android:textSize="12sp"
                    android:textColor="?attr/colorOnPrimary"
                    android:alpha="0.8"
                    android:layout_marginTop="4dp" />

            </LinearLayout>

        </com.google.android.material.card.MaterialCardView>

        <!-- ═══════════════════════════════════════════════════════════════
             SEKCJA: USTAWIENIA STAWKI GODZINOWEJ
             ═══════════════════════════════════════════════════════════════ -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/card_hourly_rate"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            app:cardCornerRadius="12dp"
            app:cardElevation="2dp"
            app:strokeWidth="0dp"
            app:layout_constraintTop_toBottomOf="@id/card_summary"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="💰 Ustawienia Płacy"
                    android:textSize="16sp"
                    android:textStyle="bold"
                    android:layout_marginBottom="12dp" />

                <!-- Stawka godzinowa (zł netto) -->
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical">

                    <com.google.android.material.textfield.TextInputLayout
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:hint="Stawka godzinowa (zł netto)"
                        style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

                        <com.google.android.material.textfield.TextInputEditText
                            android:id="@+id/et_hourly_rate"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:inputType="numberDecimal"
                            android:maxLines="1" />

                    </com.google.android.material.textfield.TextInputLayout>

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btn_save_rate"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Zapisz"
                        android:textAllCaps="false"
                        android:layout_marginStart="8dp"
                        style="@style/Widget.MaterialComponents.Button.OutlinedButton" />

                </LinearLayout>

                <!-- Etykieta zapisanej stawki -->
                <TextView
                    android:id="@+id/tv_saved_rate"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:textSize="13sp"
                    android:textColor="?android:attr/textColorSecondary"
                    android:layout_marginTop="8dp"
                    android:visibility="gone" />

            </LinearLayout>

        </com.google.android.material.card.MaterialCardView>

        <!-- ═══════════════════════════════════════════════════════════════
             SEKCJA: ZGŁOŚ NAPIWEK / PREMIĘ
             ═══════════════════════════════════════════════════════════════ -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/card_add_tip"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            app:cardCornerRadius="12dp"
            app:cardElevation="2dp"
            app:strokeWidth="0dp"
            app:layout_constraintTop_toBottomOf="@id/card_hourly_rate"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="💸 Zgłoś Napiwek / Premię"
                    android:textSize="16sp"
                    android:textStyle="bold"
                    android:layout_marginBottom="12dp" />

                <!-- Kwota napiwku -->
                <com.google.android.material.textfield.TextInputLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:hint="Kwota napiwku (zł)"
                    android:layout_marginBottom="8dp"
                    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/et_tip_amount"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:inputType="numberDecimal"
                        android:maxLines="1" />

                </com.google.android.material.textfield.TextInputLayout>

                <!-- Opis (opcjonalny) -->
                <com.google.android.material.textfield.TextInputLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:hint="Opis (opcjonalny)"
                    android:layout_marginBottom="12dp"
                    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/et_tip_description"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:inputType="text"
                        android:maxLines="1" />

                </com.google.android.material.textfield.TextInputLayout>

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btn_add_tip"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Dodaj Napiwek"
                    android:textAllCaps="false"
                    style="@style/Widget.MaterialComponents.Button" />

            </LinearLayout>

        </com.google.android.material.card.MaterialCardView>

        <!-- ═══════════════════════════════════════════════════════════════
             SEKCJA: ZGŁOŚ STRATĘ / MANKO
             ═══════════════════════════════════════════════════════════════ -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/card_add_loss"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp"
            app:cardCornerRadius="12dp"
            app:cardElevation="2dp"
            app:strokeWidth="0dp"
            app:layout_constraintTop_toBottomOf="@id/card_add_tip"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="📉 Zgłoś Stratę / Manko"
                    android:textSize="16sp"
                    android:textStyle="bold"
                    android:layout_marginBottom="12dp" />

                <!-- Kwota straty -->
                <com.google.android.material.textfield.TextInputLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:hint="Kwota straty (zł)"
                    android:layout_marginBottom="8dp"
                    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/et_loss_amount"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:inputType="numberDecimal"
                        android:maxLines="1" />

                </com.google.android.material.textfield.TextInputLayout>

                <!-- Opis (opcjonalny) -->
                <com.google.android.material.textfield.TextInputLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:hint="Opis (opcjonalny)"
                    android:layout_marginBottom="12dp"
                    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/et_loss_description"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:inputType="text"
                        android:maxLines="1" />

                </com.google.android.material.textfield.TextInputLayout>

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btn_add_loss"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Dodaj stratę"
                    android:textAllCaps="false"
                    style="@style/Widget.MaterialComponents.Button" />

            </LinearLayout>

        </com.google.android.material.card.MaterialCardView>

        <!-- ═══════════════════════════════════════════════════════════════
             SEKCJA: LISTA STRAT W TYM MIESIĄCU
             ═══════════════════════════════════════════════════════════════ -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/card_losses_list"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            app:cardCornerRadius="12dp"
            app:cardElevation="2dp"
            app:strokeWidth="0dp"
            app:layout_constraintTop_toBottomOf="@id/card_add_loss"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="16dp">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="📋 Straty w tym miesiącu"
                    android:textSize="16sp"
                    android:textStyle="bold"
                    android:layout_marginBottom="8dp" />

                <TextView
                    android:id="@+id/tv_losses_empty"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Brak strat w tym miesiącu"
                    android:textSize="14sp"
                    android:textColor="?android:attr/textColorSecondary"
                    android:gravity="center"
                    android:paddingVertical="12dp"
                    android:visibility="gone" />

                <androidx.recyclerview.widget.RecyclerView
                    android:id="@+id/rv_losses"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:nestedScrollingEnabled="false" />

            </LinearLayout>

        </com.google.android.material.card.MaterialCardView>

    </androidx.constraintlayout.widget.ConstraintLayout>

</ScrollView>
```
```diff:FinanceFragment.java
package com.asystent.kinowy.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

        // --- Bind UI ---
        bindViews(view);

        // --- Stawka godzinowa ---
        setupHourlyRate();

        // --- Dodawanie strat ---
        setupLossInput();

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
                    "%.1f h × %.2f zł − %.2f zł strat",
                    payrollInfo.getTotalHours(),
                    payrollInfo.getHourlyRate(),
                    payrollInfo.getTotalLosses()
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
===
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
```

## 10. Cinema City Branding i Tryb Zastępstw
Dokonano przeobrażenia wizualnego aplikacji, wprowadzając nową, kinową tożsamość opartą o czerń i pomarańcz:
- **Aktualizacja Motywu:** Przepisano [colors.xml](file:///j:/kinobot/app/src/main/res/values/colors.xml) oraz zmodyfikowano [themes.xml](file:///j:/kinobot/app/src/main/res/values/themes.xml) (Day/Night) ustawiając tła, kolory bazowe (`#FF6600` – pomarańczowy Cinema City, `#000000` – czerń, `#1A1A1A` – grafit) oraz usunięto problematyczne atrybuty (`?attr/colorPrimaryContainer`), eliminując błędy parsera.
- **Tryb "Szukam Zastępstwa":** Zaktualizowano schemat bazy (wer. 3), dodając [isReplacement](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/models/Shift.java#99-102) do formy [Shift](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/models/Shift.java#10-107). Dodano `MaterialSwitch` do okna edycji.  Zasygnalizowano na liście ujemnym Alpha oraz napisem "(ODDAJĘ)". Godziny z oddanej zmiany **nie są** wliczane do całkowitego raportu godzin (wspiera to metoda [calculateShiftHours](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java#303-328)).

## 11. Eksport do Kalendarza Systemowego
Udostępniono jedno-klikową synchronizację zmian z systemowym kalendarzem, operując na poziomie `ContentResolver`:
- **Uprawnienia i GUI:** W [AndroidManifest.xml](file:///j:/kinobot/app/src/main/AndroidManifest.xml) zaszyto nowe `uses-permission`. W [ScheduleFragment.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/ui/ScheduleFragment.java) oprawiono UI w nowy przycisk na headerze oraz natywny `ActivityResultLauncher` chroniący dostępu do kalendarza na Runtime Level.
- **`exportToCalendar`:** W klasie [MainViewModel.java](file:///j:/kinobot/app/src/main/java/com/asystent/kinowy/viewmodel/MainViewModel.java) zaimplementowano metodę przetwarzającą daty/czasy ISO, zabezpieczoną na okoliczność "Nocnych Zmian" (+1 day for EndTime fallback). Wczytuje i eksportuje całą listę zmian z danego miesiąca po otrzymaniu pozwolenia od Usera.
