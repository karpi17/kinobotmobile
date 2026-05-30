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

    public void updateNextShift(Shift shift) {
        this.nextShift = shift;
        notifyItemChanged(TYPE_SHIFT);
    }

    public void updateAnalytics(double bar, double ow) {
        this.barHours = bar;
        this.owHours = ow;
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
            ((AnalyticsViewHolder) holder).bind(barHours, owHours);
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
            pieChart.setCenterText("BAR vs OW");
            pieChart.setCenterTextSize(16f);
            pieChart.setCenterTextColor(android.graphics.Color.WHITE);
            pieChart.getLegend().setEnabled(false);
            pieChart.setRotationEnabled(false);
            pieChart.setHighlightPerTapEnabled(false);
            pieChart.setDrawEntryLabels(false);
        }

        void bind(double bar, double ow) {
            // Ensure at least a placeholder slice when both are zero
            float barVal = (float) bar;
            float owVal  = (float) ow;
            if (barVal <= 0 && owVal <= 0) {
                barVal = 1f;
                owVal  = 1f;
            }

            ArrayList<PieEntry> entries = new ArrayList<>();
            entries.add(new PieEntry(barVal, "BAR"));
            entries.add(new PieEntry(owVal,  "OW"));

            PieDataSet dataSet = new PieDataSet(entries, "");
            dataSet.setDrawValues(false);
            dataSet.setSliceSpace(2f);

            ArrayList<Integer> colors = new ArrayList<>();
            colors.add(android.graphics.Color.parseColor("#FF6600")); // BAR — neonowy pomarańcz
            colors.add(android.graphics.Color.parseColor("#222222")); // OW  — ciemny szary
            dataSet.setColors(colors);

            PieData data = new PieData(dataSet);
            pieChart.setData(data);
            pieChart.invalidate();

            if (tvFraction != null) {
                tvFraction.setText(String.format(java.util.Locale.US,
                        "BAR: %.1fh  |  OW: %.1fh", bar, ow));
            }
        }
    }
}
