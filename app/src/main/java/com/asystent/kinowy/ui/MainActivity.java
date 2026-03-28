package com.asystent.kinowy.ui;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.asystent.kinowy.R;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Główna aktywność aplikacji Asystent Kinowy.
 * <p>
 * Pełni rolę hosta nawigacji — zarządza fragmentami
 * za pomocą {@link BottomNavigationView}.
 * <p>
 * Fragmenty:
 * <ul>
 *   <li>{@link DashboardFragment} — Pulpit (logowanie, synchronizacja)</li>
 *   <li>{@link ScheduleFragment} — Grafik (lista zmian)</li>
 *   <li>{@link FinanceFragment} — Finanse (straty/rozliczenia)</li>
 * </ul>
 */
public class MainActivity extends AppCompatActivity {

    private MainViewModel viewModel;

    // Fragmenty — trzymamy referencje, żeby nie re-kreować przy każdym kliknięciu
    private final DashboardFragment dashboardFragment = new DashboardFragment();
    private final ScheduleFragment scheduleFragment = new ScheduleFragment();
    private final FinanceFragment financeFragment = new FinanceFragment();
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- ViewModel ---
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // --- Bottom Navigation ---
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        setupBottomNavigation(bottomNav);

        // --- Domyślny fragment ---
        if (savedInstanceState == null) {
            activeFragment = dashboardFragment;
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, financeFragment, "finance").hide(financeFragment)
                    .add(R.id.fragment_container, scheduleFragment, "schedule").hide(scheduleFragment)
                    .add(R.id.fragment_container, dashboardFragment, "dashboard")
                    .commit();
        }

        // --- Alarms ---
        viewModel.getAllShifts().observe(this, shifts -> {
            if (shifts != null) {
                com.asystent.kinowy.notifications.AlarmScheduler.scheduleAlarms(this, shifts);
            }
        });
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    /**
     * Konfiguruje obsługę kliknięć w BottomNavigationView.
     * Podmienia widoczny fragment bez niszczenia ukrytych.
     */
    private void setupBottomNavigation(@NonNull BottomNavigationView bottomNav) {
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_dashboard) {
                selectedFragment = dashboardFragment;
            } else if (itemId == R.id.nav_schedule) {
                selectedFragment = scheduleFragment;
            } else if (itemId == R.id.nav_finance) {
                selectedFragment = financeFragment;
            } else {
                return false;
            }

            if (selectedFragment != activeFragment) {
                getSupportFragmentManager().beginTransaction()
                        .hide(activeFragment)
                        .show(selectedFragment)
                        .commit();
                activeFragment = selectedFragment;
            }

            return true;
        });
    }
}
