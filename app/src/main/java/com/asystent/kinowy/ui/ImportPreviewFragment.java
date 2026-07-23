package com.asystent.kinowy.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.parsers.ParserWarning;
import com.asystent.kinowy.parsers.ScheduleParseResult;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/**
 * Fragment podglądu importu grafiku (z Excela, PDF lub OCR).
 * Wyświetla wyciągnięte zmiany, ostrzeżenia parsera i poziom pewności,
 * umożliwiając użytkownikowi zatwierdzenie lub anulowanie zapisu w bazie.
 */
public class ImportPreviewFragment extends Fragment {

    private MainViewModel viewModel;
    private TextView tvImportSourceTitle;
    private TextView tvConfidenceBadge;
    private TextView tvSourceFileName;
    private TextView tvSummaryDetails;
    private MaterialCardView cardWarnings;
    private TextView tvWarningsText;
    private RecyclerView rvPreviewShifts;
    private TextView tvEmptyShiftsNotice;
    private Button btnCancelImport;
    private Button btnConfirmImport;

    private ShiftAdapter shiftAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_import_preview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        tvImportSourceTitle = view.findViewById(R.id.tvImportSourceTitle);
        tvConfidenceBadge = view.findViewById(R.id.tvConfidenceBadge);
        tvSourceFileName = view.findViewById(R.id.tvSourceFileName);
        tvSummaryDetails = view.findViewById(R.id.tvSummaryDetails);
        cardWarnings = view.findViewById(R.id.cardWarnings);
        tvWarningsText = view.findViewById(R.id.tvWarningsText);
        rvPreviewShifts = view.findViewById(R.id.rvPreviewShifts);
        tvEmptyShiftsNotice = view.findViewById(R.id.tvEmptyShiftsNotice);
        btnCancelImport = view.findViewById(R.id.btnCancelImport);
        btnConfirmImport = view.findViewById(R.id.btnConfirmImport);

        rvPreviewShifts.setLayoutManager(new LinearLayoutManager(requireContext()));
        shiftAdapter = new ShiftAdapter(shift -> {
            // Po prostu podgląd w tym miejscu
        });
        rvPreviewShifts.setAdapter(shiftAdapter);

        viewModel.getPendingImport().observe(getViewLifecycleOwner(), this::bindParseResult);

        btnCancelImport.setOnClickListener(v -> {
            viewModel.clearPendingImport();
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            }
        });

        btnConfirmImport.setOnClickListener(v -> {
            ScheduleParseResult result = viewModel.getPendingImport().getValue();
            if (result != null) {
                viewModel.commitImport(result, true);
                Toast.makeText(requireContext(), "Zatwierdzono import grafiku!", Toast.LENGTH_SHORT).show();
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                }
            }
        });
    }

    private void bindParseResult(@Nullable ScheduleParseResult result) {
        if (result == null) return;

        tvSourceFileName.setText("Źródło: " + result.getSourceDescription());

        int userShiftCount = result.getTargetUserShifts() != null ? result.getTargetUserShifts().size() : 0;
        int employeesCount = result.getFoundNames() != null ? result.getFoundNames().size() : 0;

        tvSummaryDetails.setText("Znaleziono: " + employeesCount + " pracowników, " + userShiftCount + " Twoich zmian.");

        int confPct = Math.round(result.getConfidence() * 100);
        tvConfidenceBadge.setText(confPct + "% pewności");

        if (result.getWarnings() != null && !result.getWarnings().isEmpty()) {
            cardWarnings.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (ParserWarning warning : result.getWarnings()) {
                sb.append("• ").append(warning.getMessage()).append("\n");
            }
            tvWarningsText.setText(sb.toString().trim());
        } else {
            cardWarnings.setVisibility(View.GONE);
        }

        List<Shift> shifts = result.getTargetUserShifts();
        if (shifts == null || shifts.isEmpty()) {
            rvPreviewShifts.setVisibility(View.GONE);
            tvEmptyShiftsNotice.setVisibility(View.VISIBLE);
        } else {
            rvPreviewShifts.setVisibility(View.VISIBLE);
            tvEmptyShiftsNotice.setVisibility(View.GONE);
            shiftAdapter.setShifts(shifts);
        }
    }
}
