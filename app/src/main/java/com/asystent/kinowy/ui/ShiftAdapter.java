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

        String desc = shift.getDescription();
        String shortDesc = "";

        if (desc != null && !desc.isEmpty()) {
            // Wyciągamy sam typ (np. "BAR" z "BAR (14:00-22:00)") dla każdego przypadku
            shortDesc = desc.contains("(") ? desc.substring(0, desc.indexOf("(")).trim() : desc;
        }

        if (shift.isReplacement()) {
            // Dodajemy ikonkę zastępstwa przed krótką nazwą (np. "🔄 BAR")
            holder.tvDescription.setText("🔄 " + shortDesc);
            holder.tvDescription.setVisibility(View.VISIBLE);
            holder.itemView.setAlpha(0.4f); // Mocniej przygaszamy kartę (40% widoczności)
        } else if (!shortDesc.isEmpty()) {
            holder.tvDescription.setText(shortDesc);
            holder.tvDescription.setVisibility(View.VISIBLE);
            holder.itemView.setAlpha(1.0f);
        } else {
            holder.tvDescription.setVisibility(View.GONE);
            holder.itemView.setAlpha(1.0f);
        }

        // Kuloodporne Kliknięcie → edycja
        holder.itemView.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION && listener != null) {
                listener.onShiftClick(shifts.get(currentPos));
            }
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
        this.shifts = new ArrayList<>(newShifts);
        notifyDataSetChanged(); // Brutalnie, ale w 100% skutecznie odświeża całą listę
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
