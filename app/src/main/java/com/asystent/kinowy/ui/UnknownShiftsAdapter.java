package com.asystent.kinowy.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.Shift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnknownShiftsAdapter extends RecyclerView.Adapter<UnknownShiftsAdapter.ViewHolder> {

    private List<Shift> shifts = new ArrayList<>();
    // Map to keep track of user selections: shift ID -> Category String ("BAR", "OW")
    private final Map<Integer, String> selectedCategories = new HashMap<>();

    public void setShifts(List<Shift> shifts) {
        this.shifts = shifts;
        selectedCategories.clear();
        notifyDataSetChanged();
    }

    public Map<Integer, String> getSelectedCategories() {
        return selectedCategories;
    }

    public List<Shift> getShifts() {
        return shifts;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_unknown_shift, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Shift shift = shifts.get(position);
        
        String desc = shift.getDate() + " | " + shift.getStartTime() + " - " + shift.getEndTime() + "\n" + shift.getDescription();
        holder.tvDesc.setText(desc);

        // Reset listeners to prevent unwanted triggers during recycle
        holder.rgCategory.setOnCheckedChangeListener(null);
        holder.rgCategory.clearCheck();

        // Restore state if previously selected
        String category = selectedCategories.get(shift.getId());
        if ("BAR".equals(category)) {
            holder.rbBar.setChecked(true);
        } else if ("OW".equals(category)) {
            holder.rbOw.setChecked(true);
        }

        holder.rgCategory.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_bar) {
                selectedCategories.put(shift.getId(), "BAR");
            } else if (checkedId == R.id.rb_ow) {
                selectedCategories.put(shift.getId(), "OW");
            }
        });
    }

    @Override
    public int getItemCount() {
        return shifts != null ? shifts.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDesc;
        RadioGroup rgCategory;
        RadioButton rbBar, rbOw;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDesc = itemView.findViewById(R.id.tv_unknown_date_desc);
            rgCategory = itemView.findViewById(R.id.rg_category);
            rbBar = itemView.findViewById(R.id.rb_bar);
            rbOw = itemView.findViewById(R.id.rb_ow);
        }
    }
}
