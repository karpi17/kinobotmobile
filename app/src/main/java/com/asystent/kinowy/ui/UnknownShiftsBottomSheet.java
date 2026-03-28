package com.asystent.kinowy.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;
import java.util.Map;

public class UnknownShiftsBottomSheet extends BottomSheetDialogFragment {

    private MainViewModel viewModel;
    private UnknownShiftsAdapter adapter;

    public static UnknownShiftsBottomSheet newInstance() {
        return new UnknownShiftsBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_unknown_shifts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        
        RecyclerView rv = view.findViewById(R.id.rv_unknown_shifts);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new UnknownShiftsAdapter();
        rv.setAdapter(adapter);

        viewModel.getUnknownShifts().observe(getViewLifecycleOwner(), shifts -> {
            if (shifts != null) {
                adapter.setShifts(shifts);
                if (shifts.isEmpty()) {
                    dismiss(); // close if everything is resolved
                }
            }
        });

        view.findViewById(R.id.btn_save_categories).setOnClickListener(v -> saveCategories());
    }

    private void saveCategories() {
        Map<Integer, String> selected = adapter.getSelectedCategories();
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), "Wybierz przynajmniej jedną kategorię", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Shift> currentShifts = adapter.getShifts();
        for (Shift s : currentShifts) {
            String newCat = selected.get(s.getId());
            if (newCat != null) {
                s.setCategory(newCat);
                viewModel.updateShift(s); // Persist to DB
            }
        }
        
        Toast.makeText(requireContext(), "Zaktualizowano kategorie", Toast.LENGTH_SHORT).show();
    }
}
