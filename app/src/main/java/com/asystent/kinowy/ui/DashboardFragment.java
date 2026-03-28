package com.asystent.kinowy.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.asystent.kinowy.R;
import com.asystent.kinowy.viewmodel.MainViewModel;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;

public class DashboardFragment extends Fragment {

    private static final String TAG = "DashboardFragment";
    private static final int RC_SIGN_IN = 2001;
    private static final String GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";

    private static final String PREFS_NAME = "asystent_kinowy_prefs";
    private static final String PREF_USER_NAME = "user_name";
    private static final String PREF_NOTIFY_BEFORE = "notify_before_minutes";
    private static final String PREF_MONTHLY_GOAL = "monthly_hours_goal";

    private MainViewModel viewModel;
    private GoogleSignInClient googleSignInClient;

    // UI - Authentication & Sync
    private SignInButton btnGoogleSignIn;
    private TextView tvSyncStatus;

    // Nazwisko
    private View layoutNameInput;
    private TextInputEditText etUserName;
    private MaterialButton btnSaveName;
    private TextView tvSavedName;

    // Carousel
    private androidx.viewpager2.widget.ViewPager2 vpDashboardCarousel;
    private com.google.android.material.tabs.TabLayout tabDashboardDots;
    private DashboardCarouselAdapter carouselAdapter;

    // Alert Buttons
    private MaterialButton btnUnknownShiftsAlert;
    private MaterialButton btnMissingReportAlert;

    // Progress Bar
    private TextView tvProgressText;
    private LinearProgressIndicator progressMonthlyHours;

    // Quick Actions
    private MaterialButton btnPayrollDetails;
    private MaterialButton btnSyncSchedule;
    private MaterialButton btnSetGoal;
    private MaterialButton btnSetNotification;
    private MaterialButton btnChangeNameAction;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // UI bindings
        btnGoogleSignIn = view.findViewById(R.id.btn_google_sign_in);
        tvSyncStatus = view.findViewById(R.id.tv_sync_status);
        btnUnknownShiftsAlert = view.findViewById(R.id.btn_unknown_shifts_alert);
        btnMissingReportAlert = view.findViewById(R.id.btn_missing_report_alert);

        layoutNameInput = view.findViewById(R.id.layout_name_input);
        etUserName = view.findViewById(R.id.et_user_name);
        btnSaveName = view.findViewById(R.id.btn_save_name);
        tvSavedName = view.findViewById(R.id.tv_saved_name);

        vpDashboardCarousel = view.findViewById(R.id.vp_dashboard_carousel);
        tabDashboardDots = view.findViewById(R.id.tab_dashboard_dots);
        carouselAdapter = new DashboardCarouselAdapter();
        vpDashboardCarousel.setAdapter(carouselAdapter);

        new com.google.android.material.tabs.TabLayoutMediator(tabDashboardDots, vpDashboardCarousel,
                (tab, position) -> {}
        ).attach();

        tvProgressText = view.findViewById(R.id.tv_progress_text);
        progressMonthlyHours = view.findViewById(R.id.progress_monthly_hours);

        btnPayrollDetails = view.findViewById(R.id.btn_payroll_details);
        btnSyncSchedule = view.findViewById(R.id.btn_sync_schedule);
        btnSetGoal = view.findViewById(R.id.btn_set_goal);
        btnSetNotification = view.findViewById(R.id.btn_set_notification);
        btnChangeNameAction = view.findViewById(R.id.btn_change_name_action);

        // Load goal
        int savedGoal = getPrefs().getInt(PREF_MONTHLY_GOAL, 100);
        viewModel.getMonthlyHoursGoal().setValue(savedGoal);

        setupNameInput();
        setupQuickActions();
        observeDashboardData();
        observeSyncStatus();

        configureGoogleSignIn();
        btnGoogleSignIn.setOnClickListener(v -> startSignIn());

        GoogleSignInAccount existingAccount = GoogleSignIn.getLastSignedInAccount(requireContext());
        if (existingAccount != null) {
            handleSignInSuccess(existingAccount);
        }
    }

    private SharedPreferences getPrefs() {
        return requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Nazwisko ────────────────────────────────────────────────────────
    private void setupNameInput() {
        String savedName = getPrefs().getString(PREF_USER_NAME, null);
        if (savedName != null && !savedName.isEmpty()) {
            showSavedNameState(savedName);
        } else {
            showNameInputState();
        }

        btnSaveName.setOnClickListener(v -> {
            String name = etUserName.getText() != null ? etUserName.getText().toString().trim() : "";
            if (name.isEmpty()) {
                etUserName.setError("Wpisz imię i nazwisko");
                return;
            }
            getPrefs().edit().putString(PREF_USER_NAME, name).apply();
            viewModel.setTargetUserName(name);
            showSavedNameState(name);
        });
    }

    private void showSavedNameState(String name) {
        layoutNameInput.setVisibility(View.GONE);
        tvSavedName.setVisibility(View.VISIBLE);
        tvSavedName.setText("👤 " + name);
        viewModel.setTargetUserName(name);
    }

    private void showNameInputState() {
        layoutNameInput.setVisibility(View.VISIBLE);
        tvSavedName.setVisibility(View.GONE);
        String current = getPrefs().getString(PREF_USER_NAME, "");
        if (etUserName != null && current != null) {
            etUserName.setText(current);
        }
    }

    // ─── Quick Actions ────────────────────────────────────────────────────
    private void setupQuickActions() {
        btnUnknownShiftsAlert.setOnClickListener(v -> {
            UnknownShiftsBottomSheet.newInstance().show(getParentFragmentManager(), "UnknownShiftsBottomSheet");
        });

        btnMissingReportAlert.setOnClickListener(v -> {
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.YearMonth lastMonth = java.time.YearMonth.from(today).minusMonths(1);
            String targetMonth = String.format(java.util.Locale.US, "%04d-%02d", lastMonth.getYear(), lastMonth.getMonthValue());
            
            double calcHours = 0.0;
            double calcLosses = 0.0;
            
            java.util.List<com.asystent.kinowy.models.Shift> shifts = viewModel.getAllShifts().getValue();
            if (shifts != null) {
                for (com.asystent.kinowy.models.Shift s : shifts) {
                    if (s.getDate() != null && s.getDate().startsWith(targetMonth) && !s.isReplacement()) {
                        String startStr = s.getStartTime();
                        String endStr = s.getEndTime();
                        if (startStr != null && endStr != null && startStr.contains(":") && endStr.contains(":")) {
                            String[] sP = startStr.split(":");
                            String[] eP = endStr.split(":");
                            try {
                                java.time.LocalTime t1 = java.time.LocalTime.of(Integer.parseInt(sP[0]), Integer.parseInt(sP[1]));
                                java.time.LocalTime t2 = java.time.LocalTime.of(Integer.parseInt(eP[0]), Integer.parseInt(eP[1]));
                                long min = java.time.Duration.between(t1, t2).toMinutes();
                                if (min < 0) min += 24 * 60;
                                calcHours += (min / 60.0);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            java.util.List<com.asystent.kinowy.models.Loss> losses = viewModel.getAllLosses().getValue();
            if (losses != null) {
                for (com.asystent.kinowy.models.Loss l : losses) {
                    if (l.getDate() != null && l.getDate().startsWith(targetMonth)) {
                        calcLosses += l.getAmount();
                    }
                }
            }
            
            Float rate = viewModel.getHourlyRateLive().getValue();
            if (rate == null) rate = 0f;
            
            double calcSalary = Math.max(0, calcHours * rate - calcLosses);

            MonthlyReportDialog dialog = new MonthlyReportDialog(targetMonth, calcHours, calcSalary);
            dialog.show(getParentFragmentManager(), "MonthlyReportDialog");
        });

        btnSyncSchedule.setOnClickListener(v -> viewModel.syncSchedule());
        btnSyncSchedule.setEnabled(false);

        btnChangeNameAction.setOnClickListener(v -> showNameInputState());

        btnPayrollDetails.setOnClickListener(v -> {
            MainViewModel.PayrollInfo payroll = viewModel.getMonthlyPayroll().getValue();
            Float rate = viewModel.getHourlyRateLive().getValue();
            double totalLosses = 0.0;
            java.util.List<com.asystent.kinowy.models.Loss> losses = viewModel.getAllLosses().getValue();
            if (losses != null) {
                for (com.asystent.kinowy.models.Loss l : losses) {
                    if (l.getDate() != null && l.getDate().startsWith(viewModel.getCurrentMonthPrefix())) {
                        totalLosses += l.getAmount();
                    }
                }
            }
            double hours = payroll != null ? payroll.getTotalHours() : 0.0;
            double actualRate = (rate != null) ? rate : 0.0;
            
            PayrollBottomSheet bottomSheet = new PayrollBottomSheet(hours, actualRate, totalLosses);
            bottomSheet.show(getParentFragmentManager(), "PayrollBottomSheet");
        });

        btnSetGoal.setOnClickListener(v -> {
            TextInputEditText input = new TextInputEditText(requireContext());
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            Integer currentGoal = viewModel.getMonthlyHoursGoal().getValue();
            if (currentGoal != null) input.setText(String.valueOf(currentGoal));

            new AlertDialog.Builder(requireContext())
                    .setTitle("Ustaw Cel Godzinowy")
                    .setView(input)
                    .setPositiveButton("Zapisz", (dialog, which) -> {
                        String val = input.getText() != null ? input.getText().toString().trim() : "";
                        if (!val.isEmpty()) {
                            try {
                                int goal = Integer.parseInt(val);
                                viewModel.getMonthlyHoursGoal().setValue(goal);
                                getPrefs().edit().putInt(PREF_MONTHLY_GOAL, goal).apply();
                                Toast.makeText(requireContext(), "Zapisano cel: " + goal + "h", Toast.LENGTH_SHORT).show();
                            } catch (NumberFormatException ignored) {}
                        }
                    })
                    .setNegativeButton("Anuluj", null)
                    .show();
        });

        btnSetNotification.setOnClickListener(v -> {
            TextInputEditText input = new TextInputEditText(requireContext());
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            int savedMinutes = getPrefs().getInt(PREF_NOTIFY_BEFORE, 60);
            input.setText(String.valueOf(savedMinutes));

            new AlertDialog.Builder(requireContext())
                    .setTitle("Czas Powiadomienia")
                    .setMessage("Ile minut przed zmianą przypomnieć?")
                    .setView(input)
                    .setPositiveButton("Zapisz", (dialog, which) -> {
                        String val = input.getText() != null ? input.getText().toString().trim() : "";
                        if (!val.isEmpty()) {
                            try {
                                int minutes = Integer.parseInt(val);
                                getPrefs().edit().putInt(PREF_NOTIFY_BEFORE, minutes).apply();
                                Toast.makeText(requireContext(), "Powiadomienia: " + minutes + " min", Toast.LENGTH_SHORT).show();
                            } catch (NumberFormatException ignored) {}
                        }
                    })
                    .setNegativeButton("Anuluj", null)
                    .show();
        });
    }

    // ─── Obserwatory (Widgety) ───────────────────────────────────────────
    private void observeDashboardData() {
        viewModel.getMissingReportAlert().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                btnMissingReportAlert.setVisibility(View.VISIBLE);
                btnMissingReportAlert.setText(msg);
            } else {
                btnMissingReportAlert.setVisibility(View.GONE);
            }
        });

        viewModel.getUnknownShifts().observe(getViewLifecycleOwner(), shifts -> {
            if (shifts != null && !shifts.isEmpty()) {
                btnUnknownShiftsAlert.setVisibility(View.VISIBLE);
                btnUnknownShiftsAlert.setText("Niesklasyfikowane: " + shifts.size());
            } else {
                btnUnknownShiftsAlert.setVisibility(View.GONE);
            }
        });

        viewModel.getNextShift().observe(getViewLifecycleOwner(), shift -> {
            carouselAdapter.updateNextShift(shift);
        });

        viewModel.getMonthlyHoursGoal().observe(getViewLifecycleOwner(), goal -> updateProgressUI());
        viewModel.getMonthlyPayroll().observe(getViewLifecycleOwner(), payroll -> updateProgressUI());
    }

    private void updateProgressUI() {
        Integer goal = viewModel.getMonthlyHoursGoal().getValue();
        if (goal == null || goal <= 0) goal = 100;
        
        MainViewModel.PayrollInfo payroll = viewModel.getMonthlyPayroll().getValue();
        double hours = (payroll != null) ? payroll.getTotalHours() : 0.0;
        
        int progress = (int) ((hours / goal) * 100);
        progressMonthlyHours.setMax(100);
        // Smooth transition could be added, but setProgress directly works too
        progressMonthlyHours.setProgress(progress > 100 ? 100 : progress);

        String text = String.format(Locale.getDefault(), "Przepracowano: %.1fh / Cel: %dh (%d%%)", hours, goal, progress);
        tvProgressText.setText(text);
        
        carouselAdapter.updateAnalytics(hours, goal);
    }

    // ─── Google Sign-In ──────────────────────────────────────────────────
    private void configureGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(GMAIL_READONLY_SCOPE))
                .build();
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);
    }

    private void startSignIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                handleSignInSuccess(account);
            } catch (ApiException e) {
                Log.e(TAG, "Google Sign-In failed: code=" + e.getStatusCode(), e);
                tvSyncStatus.setText(getString(R.string.sign_in_failed, e.getStatusCode()));
            }
        }
    }

    private void handleSignInSuccess(GoogleSignInAccount account) {
        String email = account.getEmail();
        btnGoogleSignIn.setVisibility(View.GONE);
        btnSyncSchedule.setEnabled(true);
        tvSyncStatus.setText(getString(R.string.signed_in_as, email));

        fetchAccessTokenInBackground(account);
    }

    private void fetchAccessTokenInBackground(GoogleSignInAccount account) {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String token = com.google.android.gms.auth.GoogleAuthUtil.getToken(
                        requireContext(),
                        account.getAccount(),
                        "oauth2:" + GMAIL_READONLY_SCOPE
                );
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        viewModel.setGmailAccessToken(token);
                        tvSyncStatus.setText(getString(R.string.signed_in_ready, account.getEmail()));
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to acquire AccessToken", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        tvSyncStatus.setText(R.string.token_error);
                        Toast.makeText(requireContext(), R.string.token_error, Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    // ─── Sync Status ─────────────────────────────────────────────────────
    private void observeSyncStatus() {
        viewModel.getSyncStatus().observe(getViewLifecycleOwner(), status -> {
            if (status == null) return;

            if (status.equals("syncing")) {
                btnSyncSchedule.setEnabled(false);
                tvSyncStatus.setText("Synchronizuję grafik...");
            } else if (status.startsWith("success:")) {
                int count = Integer.parseInt(status.split(":")[1]);
                btnSyncSchedule.setEnabled(true);
                tvSyncStatus.setText("✅ Zsynchronizowano: " + count + " zmian");
            } else if (status.startsWith("error:")) {
                String msg = status.substring(6);
                btnSyncSchedule.setEnabled(true);
                tvSyncStatus.setText("❌ " + msg);
            }
        });
    }
}
