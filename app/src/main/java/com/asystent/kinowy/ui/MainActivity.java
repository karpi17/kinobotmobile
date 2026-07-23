package com.asystent.kinowy.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.asystent.kinowy.R;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.notifications.NotificationScheduler;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.asystent.kinowy.widget.ShiftWidgetProvider;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;

import java.util.List;

/**
 * Główna aktywność aplikacji KinoBot.
 * <p>
 * Host nawigacji — zarządza fragmentami za pomocą {@link DrawerLayout} + {@link NavigationView}.
 * Fragmenty przełączane przez hide/show (bez re-create przy każdym tapie).
 * <p>
 * Fragmenty:
 * <ul>
 *   <li>{@link DashboardFragment}  — Pulpit (sync, carousel zmian)</li>
 *   <li>{@link ScheduleFragment}   — Grafik (lista zmian + dialogi)</li>
 *   <li>{@link FinanceFragment}    — Finanse (payroll, napiwki, straty)</li>
 *   <li>{@link ProfileFragment}    — Profil (imię, stawka, ustawienia)</li>
 * </ul>
 */
public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private MainViewModel viewModel;

    // ─── Fragmenty (trzymamy referencje — nie re-tworzymy przy każdym tapie) ───
    private final DashboardFragment dashboardFragment = new DashboardFragment();
    private final ScheduleFragment  scheduleFragment  = new ScheduleFragment();
    private final FinanceFragment   financeFragment   = new FinanceFragment();
    private final ProfileFragment   profileFragment   = new ProfileFragment();
    private Fragment activeFragment;

    // ─── Drawer ─────────────────────────────────────────────────────────────────
    private DrawerLayout    drawerLayout;
    private NavigationView  navigationView;

    // ─── Easter Egg: 7 kliknięć w TYTUŁ toolbara ────────────────────────────────
    private long[]  mHits     = new long[7];
    private int     mHitCount = 0;
    private Toast   easterEggToast;

    // ────────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            savedInstanceState.remove("android:support:fragments");
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ─── ViewModel ──────────────────────────────────────────────────────────
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        // ─── Toolbar ────────────────────────────────────────────────────────────
        Toolbar toolbar = findViewById(R.id.toolbar_main);
        setSupportActionBar(toolbar);

        // ─── Drawer + Toggle (hamburger ☰) ──────────────────────────────────────
        drawerLayout   = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);

        // ─── Easter Egg — klik w TYTUŁ toolbara (TextView w środku) ────────────
        // MaterialToolbar nie eksponuje tytułu jako osobnego View, więc szukamy
        // pierwszego TextView wewnątrz toolbara (jest nim tytuł).
        setupEasterEgg(toolbar);

        // ─── Domyślny fragment ───────────────────────────────────────────────────
        if (savedInstanceState == null) {
            activeFragment = dashboardFragment;
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, profileFragment,   "profile").hide(profileFragment)
                    .add(R.id.fragment_container, financeFragment,   "finance").hide(financeFragment)
                    .add(R.id.fragment_container, scheduleFragment,  "schedule").hide(scheduleFragment)
                    .add(R.id.fragment_container, dashboardFragment, "dashboard")
                    .commit();
            navigationView.setCheckedItem(R.id.nav_dashboard);
        }

        // ─── Alarms — obserwuj zmiany i przelicz alarmy ─────────────────────────
        viewModel.getAllShifts().observe(this, shifts -> {
            if (shifts != null) {
                NotificationScheduler.scheduleAlarms(this, shifts);
            }
        });

        // ─── Uprawnienie do powiadomień (Android 13+) ────────────────────────────
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // ─── Deep-link przy zimnym starcie (widget kliknięty gdy apka była zamknięta) ──
        // P1 fix: obsługa nie tylko w onNewIntent (singleTop), ale też w onCreate
        String coldStartDate = getIntent().getStringExtra(ShiftWidgetProvider.EXTRA_OPEN_SHIFT_DATE);
        int coldStartId = getIntent().getIntExtra(ShiftWidgetProvider.EXTRA_OPEN_SHIFT_ID, -1);
        if (coldStartDate != null && !coldStartDate.isEmpty()) {
            // Czekamy aż ViewModell załaduje dane z Room, potem otwieramy dialog
            viewModel.getAllShifts().observe(this, shifts -> {
                if (shifts != null && !shifts.isEmpty()) {
                    openShiftByIdOrDate(coldStartId, coldStartDate);
                    // Obsłużyliśmy raz — wyczyść Intent żeby nie otwieral się po rotacji
                    getIntent().removeExtra(ShiftWidgetProvider.EXTRA_OPEN_SHIFT_DATE);
                }
            });
        }
    }

    // ─── NavigationView.OnNavigationItemSelectedListener ────────────────────────

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment selected;
        int id = item.getItemId();

        if      (id == R.id.nav_dashboard) selected = dashboardFragment;
        else if (id == R.id.nav_schedule)  selected = scheduleFragment;
        else if (id == R.id.nav_finance)   selected = financeFragment;
        else if (id == R.id.nav_profile)   selected = profileFragment;
        else return false;

        showFragment(selected);
        drawerLayout.closeDrawers();
        return true;
    }

    /** Podmienia widoczny fragment bez re-tworzenia ukrytych. */
    private void showFragment(Fragment fragment) {
        if (fragment == activeFragment) return;
        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .show(fragment)
                .commit();
        activeFragment = fragment;
    }

    // ─── Back — zamknij szufladę zanim wyjdziesz z apki ────────────────────────

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    // ─── Easter Egg ─────────────────────────────────────────────────────────────

    /**
     * Podpina Easter Egg pod pierwszy TextView w toolbarze (= tytuł "KinoBot").
     * Wymaga 7 kliknięć w ciągu 3 sekund.
     */
    private void setupEasterEgg(Toolbar toolbar) {
        // Szukamy TextView z tytułem wewnątrz toolbara
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof TextView) {
                child.setOnClickListener(v -> handleEasterEggTap());
                break;
            }
        }
    }

    private void handleEasterEggTap() {
        System.arraycopy(mHits, 1, mHits, 0, mHits.length - 1);
        mHits[mHits.length - 1] = SystemClock.uptimeMillis();
        mHitCount++;

        buzzEasterEgg(mHitCount);

        final int REQUIRED = 7;
        if (mHitCount > 0 && mHitCount < REQUIRED) {
            if (easterEggToast != null) easterEggToast.cancel();
            easterEggToast = Toast.makeText(
                    this,
                    "Jeszcze " + (REQUIRED - mHitCount) + " kliknięć...",
                    Toast.LENGTH_SHORT);
            easterEggToast.show();
        }

        if (mHits[0] >= (SystemClock.uptimeMillis() - 3000)) {
            mHits = new long[7];
            mHitCount = 0;
            if (easterEggToast != null) easterEggToast.cancel();
            new Handler(Looper.getMainLooper()).postDelayed(this::checkEasterEgg, 600);
        }
    }

    /** Wibracja feedbacku — buzz rośnie z każdym kliknięciem po 4. */
    private void buzzEasterEgg(int count) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return;
        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vib == null || !vib.hasVibrator()) return;
        long duration = count >= 4 ? 60 : 20;
        vib.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    private void checkEasterEgg() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && account.getEmail() != null
                && "kacperwerner05@gmail.com".equals(account.getEmail())) {
            showHallOfFame();
        }
    }

    private void showHallOfFame() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_hall_of_fame, null);
        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("Dzięki!", null)
                .show();
    }

    // ─── Deep-link z widgetu stack ───────────────────────────────────────────────

    /**
     * Wywoływane gdy widget stack kliknie kartę, a apka jest już otwarta (singleTop).
     * Przełącza na Grafik i otwiera dialog konkretnej zmiany.
     */
    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String shiftDate = intent.getStringExtra(ShiftWidgetProvider.EXTRA_OPEN_SHIFT_DATE);
        int shiftId = intent.getIntExtra(ShiftWidgetProvider.EXTRA_OPEN_SHIFT_ID, -1);
        if (shiftDate != null && !shiftDate.isEmpty()) {
            openShiftByIdOrDate(shiftId, shiftDate);
        }
    }

    /**
     * P1 fix: Otwiera dialog zmiany, preferując lookup po ID (precyzyjny),
     * z fallbackiem na pierwsze dopasowanie po dacie.
     */
    private void openShiftByIdOrDate(int shiftId, String date) {
        // Przełącz na Grafik przez drawer
        showFragment(scheduleFragment);
        navigationView.setCheckedItem(R.id.nav_schedule);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            List<Shift> shifts = viewModel.getAllShifts().getValue();
            if (shifts == null) return;

            // Próba 1: szukaj po unikalnym ID bazy danych
            if (shiftId != -1) {
                for (Shift shift : shifts) {
                    if (shift.getId() == shiftId) {
                        scheduleFragment.openShiftDialog(shift);
                        return;
                    }
                }
            }
            // Próba 2 (fallback): pierwsze dopasowanie po dacie
            for (Shift shift : shifts) {
                if (date.equals(shift.getDate()) && !shift.isReplacement()) {
                    scheduleFragment.openShiftDialog(shift);
                    return;
                }
            }
        }, 300);
    }
}
