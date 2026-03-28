package com.asystent.kinowy.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.Shift;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;

public class DashboardCarouselAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SHIFT = 0;
    private static final int TYPE_ANALYTICS = 1;

    private Shift nextShift;
    private double currentHours = 0;
    private double goalHours = 100;

    public void updateNextShift(Shift shift) {
        this.nextShift = shift;
        notifyItemChanged(TYPE_SHIFT);
    }

    public void updateAnalytics(double current, double goal) {
        this.currentHours = current;
        this.goalHours = goal;
        notifyItemChanged(TYPE_ANALYTICS);
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_SHIFT : TYPE_ANALYTICS;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SHIFT) {
            return new ShiftViewHolder(inflater.inflate(R.layout.item_dashboard_carousel_shift, parent, false));
        } else {
            return new AnalyticsViewHolder(inflater.inflate(R.layout.item_dashboard_carousel_analytics, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_SHIFT) {
            ((ShiftViewHolder) holder).bind(nextShift);
        } else {
            ((AnalyticsViewHolder) holder).bind(currentHours, goalHours);
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    static class ShiftViewHolder extends RecyclerView.ViewHolder {
        View layoutData, layoutNoData;
        TextView tvDate, tvTime;
        Chip chipDesc;

        public ShiftViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutData = itemView.findViewById(R.id.layout_next_shift_data);
            layoutNoData = itemView.findViewById(R.id.layout_no_next_shift);
            tvDate = itemView.findViewById(R.id.tv_next_shift_date);
            tvTime = itemView.findViewById(R.id.tv_next_shift_time);
            chipDesc = itemView.findViewById(R.id.chip_next_shift_desc);
        }

        void bind(Shift shift) {
            if (shift == null) {
                layoutData.setVisibility(View.GONE);
                layoutNoData.setVisibility(View.VISIBLE);
            } else {
                layoutData.setVisibility(View.VISIBLE);
                layoutNoData.setVisibility(View.GONE);
                tvDate.setText(shift.getDate());
                tvTime.setText(shift.getStartTime() + " - " + shift.getEndTime());
                chipDesc.setText(shift.getRole() != null && !shift.getRole().isEmpty() ? shift.getRole() : shift.getCategory());
            }
        }
    }

    static class AnalyticsViewHolder extends RecyclerView.ViewHolder {
        PieChart pieChart;

        public AnalyticsViewHolder(@NonNull View itemView) {
            super(itemView);
            pieChart = itemView.findViewById(R.id.chart_hours_donut);
            setupChart();
        }

        private void setupChart() {
            pieChart.setUsePercentValues(false);
            pieChart.getDescription().setEnabled(false);
            pieChart.setDrawHoleEnabled(true);
            pieChart.setHoleColor(android.graphics.Color.TRANSPARENT);
            pieChart.setTransparentCircleRadius(0f);
            pieChart.setDrawCenterText(true);
            pieChart.setCenterTextSize(18f);
            pieChart.setCenterTextColor(android.graphics.Color.WHITE);
            pieChart.getLegend().setEnabled(false);
            pieChart.setRotationEnabled(false);
            pieChart.setHighlightPerTapEnabled(false);
            pieChart.setDrawEntryLabels(false);
        }

        void bind(double current, double goal) {
            pieChart.setCenterText(String.format(java.util.Locale.US, "%.1f / %.0f h", current, goal));

            ArrayList<PieEntry> entries = new ArrayList<>();
            float remaining = (float) Math.max(0, goal - current);
            entries.add(new PieEntry((float) current, "Zrobione"));
            if (remaining > 0) {
                entries.add(new PieEntry(remaining, "Pozostało"));
            }

            PieDataSet dataSet = new PieDataSet(entries, "");
            dataSet.setDrawValues(false);
            
            ArrayList<Integer> colors = new ArrayList<>();
            colors.add(android.graphics.Color.parseColor("#FF6600")); // Primary Cinema City Orange
            colors.add(android.graphics.Color.parseColor("#333333")); // Dark gray remainder

            dataSet.setColors(colors);

            PieData data = new PieData(dataSet);
            pieChart.setData(data);
            pieChart.invalidate();
        }
    }
}
