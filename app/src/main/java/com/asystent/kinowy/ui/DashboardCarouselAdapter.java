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
    private String coworkersText; // sformatowana lista współpracowników
    private double barHours = 0;
    private double owHours = 0;
    private java.util.Map<String, Double> categoryHours = new java.util.LinkedHashMap<>();

    public void updateNextShift(Shift shift) {
        this.nextShift = shift;
        notifyItemChanged(TYPE_SHIFT);
    }

    public void updateAnalytics(double bar, double ow) {
        this.barHours = bar;
        this.owHours = ow;
        this.categoryHours.clear();
        if (bar > 0) this.categoryHours.put("BAR", bar);
        if (ow > 0) this.categoryHours.put("OW", ow);
        notifyItemChanged(TYPE_ANALYTICS);
    }

    public void updateAnalytics(java.util.Map<String, Double> categories) {
        this.categoryHours.clear();
        if (categories != null) {
            this.categoryHours.putAll(categories);
        }
        this.barHours = this.categoryHours.getOrDefault("BAR", 0.0);
        this.owHours = this.categoryHours.getOrDefault("OW", 0.0);
        notifyItemChanged(TYPE_ANALYTICS);
    }

    public void updateCoworkers(String coworkers) {
        this.coworkersText = coworkers;
        notifyItemChanged(TYPE_SHIFT);
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
            ((ShiftViewHolder) holder).bind(nextShift, coworkersText);
        } else {
            ((AnalyticsViewHolder) holder).bind(categoryHours, barHours, owHours);
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    static class ShiftViewHolder extends RecyclerView.ViewHolder {
        View layoutData, layoutNoData;
        TextView tvDate, tvTime;
        TextView tvMoonIcon, tvClosingCrew;
        View dividerCoworkers;
        TextView tvCoworkersLabel, tvCoworkers;
        Chip chipDesc;

        public ShiftViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutData = itemView.findViewById(R.id.layout_next_shift_data);
            layoutNoData = itemView.findViewById(R.id.layout_no_next_shift);
            tvDate = itemView.findViewById(R.id.tv_next_shift_date);
            tvTime = itemView.findViewById(R.id.tv_next_shift_time);
            chipDesc = itemView.findViewById(R.id.chip_next_shift_desc);
            tvMoonIcon = itemView.findViewById(R.id.tv_carousel_moon_icon);
            tvClosingCrew = itemView.findViewById(R.id.tv_carousel_closing_crew);
            dividerCoworkers = itemView.findViewById(R.id.divider_coworkers);
            tvCoworkersLabel = itemView.findViewById(R.id.tv_carousel_coworkers_label);
            tvCoworkers = itemView.findViewById(R.id.tv_carousel_coworkers);
        }

        void bind(Shift shift, String coworkers) {
            if (shift == null) {
                layoutData.setVisibility(View.GONE);
                layoutNoData.setVisibility(View.VISIBLE);
            } else {
                layoutData.setVisibility(View.VISIBLE);
                layoutNoData.setVisibility(View.GONE);
                tvDate.setText(shift.getDate());
                tvTime.setText(shift.getStartTime() + " - " + shift.getEndTime());
                chipDesc.setText(shift.getDescription() != null && !shift.getDescription().isEmpty() ? shift.getDescription() : shift.getCategory());

                // 🌙 Zamek
                if (tvMoonIcon != null) {
                    tvMoonIcon.setVisibility(shift.isClosingShift() ? View.VISIBLE : View.GONE);
                }

                // Ekipa zamykająca
                if (tvClosingCrew != null) {
                    String crew = shift.getClosingCrew();
                    if (crew != null && !crew.isEmpty()) {
                        tvClosingCrew.setText("🧑‍🤝‍🧑 Ekipa: " + crew);
                        tvClosingCrew.setVisibility(View.VISIBLE);
                    } else {
                        tvClosingCrew.setVisibility(View.GONE);
                    }
                }

                // Współpracownicy (overlap z global_shifts)
                if (coworkers != null && !coworkers.isEmpty()) {
                    if (dividerCoworkers != null) dividerCoworkers.setVisibility(View.VISIBLE);
                    if (tvCoworkersLabel != null) tvCoworkersLabel.setVisibility(View.VISIBLE);
                    if (tvCoworkers != null) {
                        tvCoworkers.setText(coworkers);
                        tvCoworkers.setVisibility(View.VISIBLE);
                    }
                } else {
                    if (dividerCoworkers != null) dividerCoworkers.setVisibility(View.GONE);
                    if (tvCoworkersLabel != null) tvCoworkersLabel.setVisibility(View.GONE);
                    if (tvCoworkers != null) tvCoworkers.setVisibility(View.GONE);
                }
            }
        }
    }

    static class AnalyticsViewHolder extends RecyclerView.ViewHolder {
        PieChart pieChart;
        TextView tvFraction;

        private static final int[] PALETTE = new int[]{
                android.graphics.Color.parseColor("#FF6600"),
                android.graphics.Color.parseColor("#2196F3"),
                android.graphics.Color.parseColor("#9C27B0"),
                android.graphics.Color.parseColor("#4CAF50"),
                android.graphics.Color.parseColor("#FFC107"),
                android.graphics.Color.parseColor("#607D8B")
        };

        public AnalyticsViewHolder(@NonNull View itemView) {
            super(itemView);
            pieChart = itemView.findViewById(R.id.chart_hours_donut);
            tvFraction = itemView.findViewById(R.id.tv_donut_fraction);
            setupChart();
        }

        private void setupChart() {
            pieChart.setTouchEnabled(false);
            pieChart.setUsePercentValues(false);
            pieChart.getDescription().setEnabled(false);
            pieChart.setDrawHoleEnabled(true);
            pieChart.setHoleRadius(85f);
            pieChart.setHoleColor(android.graphics.Color.TRANSPARENT);
            pieChart.setTransparentCircleRadius(0f);
            pieChart.setDrawCenterText(true);
            pieChart.setCenterText("Stanowiska");
            pieChart.setCenterTextSize(15f);
            pieChart.setCenterTextColor(android.graphics.Color.WHITE);
            pieChart.getLegend().setEnabled(false);
            pieChart.setRotationEnabled(false);
            pieChart.setHighlightPerTapEnabled(false);
            pieChart.setDrawEntryLabels(false);
        }

        void bind(java.util.Map<String, Double> categoryHours, double bar, double ow) {
            ArrayList<PieEntry> entries = new ArrayList<>();
            ArrayList<Integer> colors = new ArrayList<>();
            StringBuilder summary = new StringBuilder();

            if (categoryHours != null && !categoryHours.isEmpty()) {
                int colorIdx = 0;
                for (java.util.Map.Entry<String, Double> entry : categoryHours.entrySet()) {
                    double val = entry.getValue() != null ? entry.getValue() : 0.0;
                    if (val <= 0) continue;
                    entries.add(new PieEntry((float) val, entry.getKey()));
                    colors.add(PALETTE[colorIdx % PALETTE.length]);
                    colorIdx++;
                    if (summary.length() > 0) summary.append("  |  ");
                    summary.append(String.format(java.util.Locale.US, "%s: %.1fh", entry.getKey(), val));
                }
            }

            if (entries.isEmpty()) {
                entries.add(new PieEntry(1f, "BAR"));
                entries.add(new PieEntry(1f, "OW"));
                colors.add(PALETTE[0]);
                colors.add(android.graphics.Color.parseColor("#222222"));
                summary.append(String.format(java.util.Locale.US, "BAR: %.1fh  |  OW: %.1fh", bar, ow));
            }

            PieDataSet dataSet = new PieDataSet(entries, "");
            dataSet.setDrawValues(false);
            dataSet.setSliceSpace(2f);
            dataSet.setColors(colors);

            PieData data = new PieData(dataSet);
            pieChart.setData(data);
            pieChart.invalidate();

            if (tvFraction != null) {
                tvFraction.setText(summary.toString());
            }
        }
    }
}
