package com.asystent.kinowy.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.asystent.kinowy.R;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class StatisticsFragment extends Fragment {

    private MainViewModel viewModel;

    private TextView tvTotalHours;
    private TextView tvOpenClose;
    private TextView tvMarathon;
    private TextView tvTopCategory;
    private TextView tvTopCoworker;
    private TextView tvBestMonth;
    private TextView tvWorstMonth;

    private View scrollContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_statistics, container, false);

        tvTotalHours = view.findViewById(R.id.tv_total_hours);
        tvOpenClose = view.findViewById(R.id.tv_open_close);
        tvMarathon = view.findViewById(R.id.tv_marathon);
        tvTopCategory = view.findViewById(R.id.tv_top_category);
        tvTopCoworker = view.findViewById(R.id.tv_top_coworker);
        tvBestMonth = view.findViewById(R.id.tv_best_month);
        tvWorstMonth = view.findViewById(R.id.tv_worst_month);
        scrollContainer = view.findViewById(R.id.scroll_view_stats);

        Spinner spinnerYear = view.findViewById(R.id.spinner_year);
        FloatingActionButton fabShare = view.findViewById(R.id.fab_share);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        int currentYear = LocalDate.now().getYear();
        List<String> years = Arrays.asList("all-time", String.valueOf(currentYear), String.valueOf(currentYear - 1));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, 
                Arrays.asList("Cały czas", String.valueOf(currentYear), String.valueOf(currentYear - 1)));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYear.setAdapter(adapter);

        spinnerYear.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = years.get(position);
                viewModel.calculateStatistics(selected);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        Runnable triggerRecalculate = () -> {
            int pos = spinnerYear.getSelectedItemPosition();
            String selected = (pos >= 0 && pos < years.size()) ? years.get(pos) : "all-time";
            viewModel.calculateStatistics(selected);
        };

        viewModel.getAllShifts().observe(getViewLifecycleOwner(), shifts -> triggerRecalculate.run());
        viewModel.getAllLosses().observe(getViewLifecycleOwner(), losses -> triggerRecalculate.run());
        viewModel.getAllTips().observe(getViewLifecycleOwner(), tips -> triggerRecalculate.run());
        viewModel.getAllRates().observe(getViewLifecycleOwner(), rates -> triggerRecalculate.run());
        viewModel.getHourlyRateLive().observe(getViewLifecycleOwner(), rate -> triggerRecalculate.run());

        viewModel.getYearlyStats().observe(getViewLifecycleOwner(), stats -> {
            if (stats != null) {
                tvTotalHours.setText(String.format(Locale.getDefault(), "%.1f h", stats.getTotalHours()));
                tvOpenClose.setText(String.format(Locale.getDefault(), "☀️ %d openów  |  🌙 %d zamków", stats.getOpeningShifts(), stats.getClosingShifts()));
                tvMarathon.setText(stats.getLongestMarathon() + " dni z rzędu");
                tvTopCategory.setText(stats.getTopCategory());
                tvTopCoworker.setText(stats.getTopCoworker());
                tvBestMonth.setText(String.format(Locale.getDefault(), "🔥 Najlepszy: %s (%.2f zł)", stats.getBestMonth(), stats.getBestMonthAmount()));
                tvWorstMonth.setText(String.format(Locale.getDefault(), "📉 Najgorszy: %s (%.2f zł)", stats.getWorstMonth(), stats.getWorstMonthAmount()));
            }
        });

        fabShare.setOnClickListener(v -> shareScreenshot());

        return view;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && viewModel != null && getView() != null) {
            Spinner spinnerYear = getView().findViewById(R.id.spinner_year);
            if (spinnerYear != null) {
                int currentYear = LocalDate.now().getYear();
                List<String> years = Arrays.asList("all-time", String.valueOf(currentYear), String.valueOf(currentYear - 1));
                int pos = spinnerYear.getSelectedItemPosition();
                String selected = (pos >= 0 && pos < years.size()) ? years.get(pos) : "all-time";
                viewModel.calculateStatistics(selected);
            }
        }
    }

    private void shareScreenshot() {
        try {
            // Generowanie zrzutu ze ScrollView z ciemnym tłem motywu (#121212)
            int totalHeight = ((androidx.core.widget.NestedScrollView) scrollContainer).getChildAt(0).getHeight();
            int totalWidth = scrollContainer.getWidth();
            Bitmap b = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.drawColor(android.graphics.Color.parseColor("#121212"));
            ((androidx.core.widget.NestedScrollView) scrollContainer).getChildAt(0).draw(c);

            File cachePath = new File(requireContext().getCacheDir(), "images");
            cachePath.mkdirs();
            File file = new File(cachePath, "kinobot_wrapped.png");
            FileOutputStream stream = new FileOutputStream(file);
            b.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

            Uri contentUri = FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file);

            if (contentUri != null) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); 
                shareIntent.setDataAndType(contentUri, requireActivity().getContentResolver().getType(contentUri));
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Moje statystyki pracy z KinoBot Wrapped! \uD83C\uDF7F");
                startActivity(Intent.createChooser(shareIntent, "Udostępnij swoje statystyki"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Błąd przy udostępnianiu zrzutu", Toast.LENGTH_SHORT).show();
        }
    }
}
