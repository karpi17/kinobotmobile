package com.asystent.kinowy.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.ActiveEmployee;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * Dialog do zarządzania kadrą kina — role (TL vs Pracownicy), status aktywności oraz usuwanie.
 */
public class StaffManagementDialog {

    public static void show(Context context, LifecycleOwner lifecycleOwner, MainViewModel viewModel) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_staff_management, null);

        EditText etSearch = dialogView.findViewById(R.id.et_staff_search);
        TextView tvSummary = dialogView.findViewById(R.id.tv_staff_summary);
        RecyclerView rvStaff = dialogView.findViewById(R.id.rv_staff_list);
        TextView tvEmpty = dialogView.findViewById(R.id.tv_staff_empty);

        rvStaff.setLayoutManager(new LinearLayoutManager(context));

        StaffAdapter adapter = new StaffAdapter(new StaffAdapter.OnStaffActionListener() {
            @Override
            public void onToggleStatus(ActiveEmployee employee) {
                boolean newStatus = !employee.isActive();
                viewModel.updateEmployeeActiveStatus(employee.getName(), newStatus);
                Toast.makeText(context, (newStatus ? "Aktywowano: " : "Oznaczono jako były pracownik: ")
                        + employee.getName(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onToggleRole(ActiveEmployee employee) {
                String newRole = employee.isLeadership()
                        ? ActiveEmployee.ROLE_EMPLOYEE
                        : ActiveEmployee.ROLE_TL;
                viewModel.updateEmployeeRole(employee.getName(), newRole);
                Toast.makeText(context, employee.getName() + " → "
                        + (ActiveEmployee.ROLE_TL.equals(newRole) ? "Team Leader" : "Pracownik"),
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDelete(ActiveEmployee employee) {
                new MaterialAlertDialogBuilder(context)
                        .setTitle("Usuń pracownika ze słownika")
                        .setMessage("Czy na pewno chcesz usunąć " + employee.getName()
                                + " ze słownika pracowników? Zmiany historyczne w grafiku pozostaną nienaruszone.")
                        .setPositiveButton("Usuń", (d, w) -> {
                            viewModel.deleteEmployee(employee.getName());
                            Toast.makeText(context, "Usunięto: " + employee.getName(), Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Anuluj", null)
                        .show();
            }
        });

        rvStaff.setAdapter(adapter);

        viewModel.getAllEmployeesLive().observe(lifecycleOwner, employees -> {
            if (employees == null || employees.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                rvStaff.setVisibility(View.GONE);
                tvSummary.setText("Brak pracowników w bazie.");
            } else {
                tvEmpty.setVisibility(View.GONE);
                rvStaff.setVisibility(View.VISIBLE);
                adapter.setData(employees);
                tvSummary.setText("Wszyscy: " + adapter.getTotalCount()
                        + " | Aktywni: " + adapter.getActiveCount()
                        + " | Byli: " + (adapter.getTotalCount() - adapter.getActiveCount()));
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Przycisk ukrycia nieaktywnych od 60 dni
        dialogView.findViewById(R.id.btn_staff_cleanup).setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(context)
                    .setTitle("🧹 Auto-czyszczenie kadr")
                    .setMessage("Czy chcesz automatycznie oznaczyć jako „Były pracownik” wszystkie osoby, które nie miały żadnej zmiany w grafiku od co najmniej 60 dni?")
                    .setPositiveButton("Ukryj nieaktywnych", (d, w) -> {
                        viewModel.cleanupInactiveEmployees(60);
                        Toast.makeText(context, "Zaktualizowano statusy nieaktywnych pracowników.", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Anuluj", null)
                    .show();
        });

        // Przycisk dodania nowego pracownika
        dialogView.findViewById(R.id.btn_staff_add).setOnClickListener(v -> {
            showAddEmployeeDialog(context, viewModel);
        });

        new MaterialAlertDialogBuilder(context)
                .setTitle("👥 Kadra i Uprawnienia")
                .setView(dialogView)
                .setPositiveButton("Zamknij", null)
                .show();
    }

    private static void showAddEmployeeDialog(Context context, MainViewModel viewModel) {
        View addView = LayoutInflater.from(context).inflate(R.layout.dialog_add_staff_member, null);

        EditText etName = addView.findViewById(R.id.et_add_staff_name);
        android.widget.AutoCompleteTextView actvRole = addView.findViewById(R.id.actv_add_staff_role);

        String[] roles = new String[]{"👤 Pracownik", "⭐ Team Leader / Kierownik"};
        android.widget.ArrayAdapter<String> roleAdapter = new android.widget.ArrayAdapter<>(
                context, android.R.layout.simple_dropdown_item_1line, roles);
        actvRole.setAdapter(roleAdapter);

        new MaterialAlertDialogBuilder(context)
                .setTitle("➕ Dodaj pracownika do bazy")
                .setView(addView)
                .setPositiveButton("Zapisz", (d, w) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    String roleChoice = actvRole.getText() != null ? actvRole.getText().toString().trim() : "";
                    if (name.isEmpty()) {
                        Toast.makeText(context, "Imię nie może być puste", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean isTL = roleChoice.contains("Team Leader");
                    String role = isTL ? ActiveEmployee.ROLE_TL : ActiveEmployee.ROLE_EMPLOYEE;
                    ActiveEmployee ae = new ActiveEmployee(name, true, role, java.time.LocalDate.now().toString());
                    viewModel.upsertEmployee(ae);
                    Toast.makeText(context, "Dodano: " + name + " (" + (isTL ? "Team Leader" : "Pracownik") + ")", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }
}
