package com.asystent.kinowy.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.ActiveEmployee;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter do zarządzania kadrą kina (role, status aktywności i usuwanie).
 */
public class StaffAdapter extends RecyclerView.Adapter<StaffAdapter.StaffViewHolder> {

    public interface OnStaffActionListener {
        void onToggleStatus(ActiveEmployee employee);
        void onToggleRole(ActiveEmployee employee);
        void onDelete(ActiveEmployee employee);
    }

    private final List<ActiveEmployee> fullList = new ArrayList<>();
    private final List<ActiveEmployee> displayedList = new ArrayList<>();
    private final OnStaffActionListener listener;
    private String currentQuery = "";

    public StaffAdapter(OnStaffActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<ActiveEmployee> employees) {
        fullList.clear();
        if (employees != null) {
            fullList.addAll(employees);
        }
        applyFilter();
    }

    public void filter(String query) {
        this.currentQuery = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        applyFilter();
    }

    private void applyFilter() {
        displayedList.clear();
        if (currentQuery.isEmpty()) {
            displayedList.addAll(fullList);
        } else {
            for (ActiveEmployee ae : fullList) {
                if (ae.getName() != null && ae.getName().toLowerCase(Locale.ROOT).contains(currentQuery)) {
                    displayedList.add(ae);
                }
            }
        }
        notifyDataSetChanged();
    }

    public int getTotalCount() {
        return fullList.size();
    }

    public int getActiveCount() {
        int c = 0;
        for (ActiveEmployee ae : fullList) {
            if (ae.isActive()) c++;
        }
        return c;
    }

    @NonNull
    @Override
    public StaffViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_staff_member, parent, false);
        return new StaffViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StaffViewHolder holder, int position) {
        ActiveEmployee employee = displayedList.get(position);
        holder.tvName.setText(employee.getName());

        String lastDate = employee.getLastSeenDate();
        if (lastDate != null && !lastDate.trim().isEmpty()) {
            holder.tvLastSeen.setText("Ostatnio w grafiku: " + lastDate);
        } else {
            holder.tvLastSeen.setText("Brak odnotowanej daty zmiany");
        }

        // Chip Statusu (Aktywny / Były pracownik)
        if (employee.isActive()) {
            holder.chipStatus.setText("✅ Aktywny");
            holder.chipStatus.setTextColor(Color.parseColor("#A5D6A7"));
            holder.chipStatus.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#1B5E20")));
        } else {
            holder.chipStatus.setText("❌ Były pracownik");
            holder.chipStatus.setTextColor(Color.parseColor("#E0E0E0"));
            holder.chipStatus.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#424242")));
        }

        // Chip Roli (Team Leader / Pracownik)
        if (employee.isLeadership()) {
            holder.chipRole.setText("⭐ Team Leader / Kierownik");
            holder.chipRole.setTextColor(Color.parseColor("#E1BEE7"));
            holder.chipRole.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#4A148C")));
        } else {
            holder.chipRole.setText("👤 Pracownik");
            holder.chipRole.setTextColor(Color.parseColor("#B0BEC5"));
            holder.chipRole.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#263238")));
        }

        holder.chipStatus.setOnClickListener(v -> {
            if (listener != null) listener.onToggleStatus(employee);
        });

        holder.chipRole.setOnClickListener(v -> {
            if (listener != null) listener.onToggleRole(employee);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(employee);
        });
    }

    @Override
    public int getItemCount() {
        return displayedList.size();
    }

    static class StaffViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvLastSeen;
        Chip chipStatus;
        Chip chipRole;
        ImageButton btnDelete;

        public StaffViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_staff_name);
            tvLastSeen = itemView.findViewById(R.id.tv_staff_last_seen);
            chipStatus = itemView.findViewById(R.id.chip_staff_status);
            chipRole = itemView.findViewById(R.id.chip_staff_role);
            btnDelete = itemView.findViewById(R.id.btn_staff_delete);
        }
    }
}
