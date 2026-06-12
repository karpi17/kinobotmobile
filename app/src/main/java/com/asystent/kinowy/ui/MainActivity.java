package com.asystent.kinowy.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.asystent.kinowy.widget.ShiftWidgetProvider;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.os.SystemClock;
import java.util.List;

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

    // --- Easter Egg: 7 kliknięć w toolbar ---
    private long[] mHits = new long[7];
    private int mHitCount = 0; // licznik do feedbacku wibracji

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            savedInstanceState.remove("android:support:fragments");
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- ViewModel ---
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // --- Bottom Navigation ---
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        setupBottomNavigation(bottomNav);

        // --- Easter Egg (Konami Code na Toolbarze) ---
        View toolbar = findViewById(R.id.toolbar_main);
        if (toolbar != null) {
            toolbar.setOnClickListener(v -> {
                System.arraycopy(mHits, 1, mHits, 0, mHits.length - 1);
                mHits[mHits.length - 1] = SystemClock.uptimeMillis();
                mHitCount++;

                // Wibracja: krótki buzz na każde kliknięcie, mocniejszy po 4.
                buzzEasterEgg(mHitCount);

                if (mHits[0] >= (SystemClock.uptimeMillis() - 3000)) {
                    mHits = new long[7];
                    mHitCount = 0;
                    // Opóźnienie 600ms — ostatni tap nie trafia odruchowo w "Dzięki!"
                    new Handler(Looper.getMainLooper()).postDelayed(this::checkEasterEgg, 600);
                }
            });
        }

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

    /** Wibracja feedbacku — buzz rośnie z każdym kliknięciem po 4. */
    private void buzzEasterEgg(int count) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return;
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib == null || !vib.hasVibrator()) return;
        long duration = count >= 4 ? 60 : 20; // po połowie dłuższy buzz
        vib.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    private void checkEasterEgg() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && account.getEmail() != null) {
            String email = account.getEmail();
            if ("kacperwerner05@gmail.com".equals(email)) {
                showHallOfFame();
            }
        }
    }

    private void showHallOfFame() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_hall_of_fame, null);
        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("Dzięki!", null)
                .show();
    }

    // ─── Deep-link z widgetu stack ────────────────────────────────────────

    /**
     * Wywoływane gdy widget stack kliknie w kartę i apka jest już otwarta (singleTop).
     * Przełącza na zakładkę Grafik i otwiera dialog konkretnej zmiany.
     */
    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String shiftDate = intent.getStringExtra(ShiftWidgetProvider.EXTRA_OPEN_SHIFT_DATE);
        if (shiftDate != null && !shiftDate.isEmpty()) {
            openShiftByDate(shiftDate);
        }
    }

    private void openShiftByDate(String date) {
        // Przełącz na zakładkę Grafik
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_schedule);
        }
        // Znajdź zmianę po dacie i otwórz dialog — na wątku głównym po krótkim opóźenieniu
        // (fragment musi się wyrenderować zanim pokażemy dialog)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            List<Shift> shifts = viewModel.getAllShifts().getValue();
            if (shifts == null) return;
            for (Shift shift : shifts) {
                if (date.equals(shift.getDate()) && !shift.isReplacement()) {
                    scheduleFragment.openShiftDialog(shift);
                    return;
                }
            }
        }, 300);
    }

}
