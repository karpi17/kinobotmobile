package com.asystent.kinowy.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.GlobalShift;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adapter RecyclerView do wyświetlania edytowalnej listy współpracowników
 * w dialogu edycji zmiany lub pełnej obsady dnia (Tryb Kierownika).
 * <p>
 * Automatycznie oznacza zmiany otwierające (☀️ OPEN) oraz zamykające (🌙 ZAMEK).
 */
public class CoworkerAdapter extends RecyclerView.Adapter<CoworkerAdapter.ViewHolder> {

    public interface OnCoworkerClickListener {
        void onCoworkerClick(GlobalShift globalShift, int position);
    }

    private final List<GlobalShift> coworkers = new ArrayList<>();
    private final Map<String, LocalTime> earliestStartByCategory = new HashMap<>();
    private final OnCoworkerClickListener listener;

    public CoworkerAdapter(OnCoworkerClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<GlobalShift> data) {
        setData(data, data);
    }

    public void setData(List<GlobalShift> data, List<GlobalShift> fullDayContext) {
        coworkers.clear();
        earliestStartByCategory.clear();

        List<GlobalShift> contextList = (fullDayContext != null && !fullDayContext.isEmpty())
                ? fullDayContext : data;
        if (contextList != null) {
            for (GlobalShift gs : contextList) {
                if (gs == null || gs.isDeleted()) continue;
                LocalTime start = parseTimeSafe(gs.getStartTime());
                if (start == null) continue;
                String catKey = normalizeCategoryKey(gs.getCategory());
                LocalTime prevMin = earliestStartByCategory.get(catKey);
                if (prevMin == null || start.isBefore(prevMin)) {
                    earliestStartByCategory.put(catKey, start);
                }
            }
        }

        if (data != null) {
            for (GlobalShift gs : data) {
                if (gs != null && !gs.isDeleted()
                        && gs.getStartTime() != null && !gs.getStartTime().trim().isEmpty()) {
                    coworkers.add(gs);
                }
            }
        }

        coworkers.sort((a, b) -> {
            String sa = a.getStartTime() != null ? a.getStartTime() : "";
            String sb = b.getStartTime() != null ? b.getStartTime() : "";
            int cmp = sa.compareTo(sb);
            if (cmp != 0) return cmp;
            String na = a.getName() != null ? a.getName() : "";
            String nb = b.getName() != null ? b.getName() : "";
            return na.compareToIgnoreCase(nb);
        });

        notifyDataSetChanged();
    }

    public void removeAt(int position) {
        if (position >= 0 && position < coworkers.size()) {
            coworkers.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void updateAt(int position, GlobalShift updated) {
        if (position >= 0 && position < coworkers.size()) {
            coworkers.set(position, updated);
            notifyItemChanged(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_coworker_edit, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GlobalShift gs = coworkers.get(position);
        holder.tvName.setText(gs.getName());
        holder.tvTime.setText(gs.getStartTime() + "–" + gs.getEndTime());

        String cat = gs.getCategory() != null && !gs.getCategory().isEmpty() ? gs.getCategory() : "?";
        boolean isClose = isClosingShift(gs);
        boolean isOpen = !isClose && isOpeningShift(gs);

        StringBuilder badgeBuilder = new StringBuilder();
        if (isOpen) {
            badgeBuilder.append("☀️ OPEN");
            if (!"OBSŁUGA".equalsIgnoreCase(cat) && !"OPEN".equalsIgnoreCase(cat)
                    && !"?".equals(cat) && !"UNKNOWN".equalsIgnoreCase(cat)) {
                badgeBuilder.append(" • ").append(cat);
            }
        } else if (isClose) {
            badgeBuilder.append("🌙 ZAMEK");
            if (!"ZAMEK".equalsIgnoreCase(cat) && !"?".equals(cat) && !"UNKNOWN".equalsIgnoreCase(cat)) {
                badgeBuilder.append(" • ").append(cat);
            }
        } else {
            if ("MIĘDZYZMIANA".equalsIgnoreCase(cat) || "OBSŁUGA".equalsIgnoreCase(cat)
                    || "TL".equalsIgnoreCase(cat) || "?".equals(cat) || "UNKNOWN".equalsIgnoreCase(cat)) {
                badgeBuilder.append("🕐 MIĘDZYZMIANA");
            } else {
                badgeBuilder.append(cat);
            }
        }
        holder.tvCategory.setText(badgeBuilder.toString());

        // Pokaż ikonkę ✏️ dla ręcznie edytowanych
        if (holder.tvManualIcon != null) {
            holder.tvManualIcon.setVisibility(gs.isManuallyEdited() ? View.VISIBLE : View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCoworkerClick(gs, holder.getAdapterPosition());
            }
        });
    }

    private boolean isOpeningShift(GlobalShift gs) {
        if (gs.getCategory() != null && gs.getCategory().toUpperCase(Locale.ROOT).contains("OPEN")) {
            return true;
        }
        LocalTime start = parseTimeSafe(gs.getStartTime());
        if (start == null) return false;
        if (!start.isAfter(LocalTime.of(9, 30))) return true;
        if (start.isAfter(LocalTime.of(11, 0))) return false;
        String catKey = normalizeCategoryKey(gs.getCategory());
        LocalTime earliest = earliestStartByCategory.get(catKey);
        return earliest != null && start.equals(earliest);
    }

    private boolean isClosingShift(GlobalShift gs) {
        if (gs.getCategory() != null && gs.getCategory().toUpperCase(Locale.ROOT).contains("ZAMEK")) {
            return true;
        }
        LocalTime start = parseTimeSafe(gs.getStartTime());
        LocalTime end = parseTimeSafe(gs.getEndTime());
        if (end == null) return false;
        if (start != null && end.isBefore(start)) return true;
        return !end.isAfter(LocalTime.of(5, 0)) || !end.isBefore(LocalTime.of(23, 0));
    }

    private String normalizeCategoryKey(String cat) {
        if (cat == null || cat.trim().isEmpty()) return "ALL";
        String u = cat.trim().toUpperCase(Locale.ROOT);
        if ("UNKNOWN".equals(u) || "?".equals(u)) return "ALL";
        if (u.contains("BAR")) return "BAR";
        if (u.contains("OW") || u.contains("OBSŁUGA") || u.contains("OBSLUGA") || u.contains("SP")) return "OW";
        if (u.contains("KASA") || u.contains("BILET")) return "KASA";
        if (u.contains("KAWIARNIA") || u.contains("CAFE")) return "KAWIARNIA";
        if (u.contains("PROJEKCJA") || u.contains("TMS")) return "PROJEKCJA";
        return u;
    }

    private LocalTime parseTimeSafe(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return null;
        try {
            String t = timeStr.trim();
            if (t.length() == 4 && t.charAt(1) == ':') t = "0" + t;
            return LocalTime.parse(t);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int getItemCount() {
        return coworkers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvManualIcon, tvName, tvTime, tvCategory;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvManualIcon = itemView.findViewById(R.id.tv_coworker_manual_icon);
            tvName = itemView.findViewById(R.id.tv_coworker_name);
            tvTime = itemView.findViewById(R.id.tv_coworker_time);
            tvCategory = itemView.findViewById(R.id.tv_coworker_category);
        }
    }
}
