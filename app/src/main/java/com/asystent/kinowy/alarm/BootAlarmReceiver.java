package com.asystent.kinowy.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.asystent.kinowy.db.AppDatabase;
import com.asystent.kinowy.db.GlobalShiftDao;
import com.asystent.kinowy.models.GlobalShift;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BroadcastReceiver nasłuchujący na {@code BOOT_COMPLETED} i {@code QUICKBOOT_POWERON}.
 * <p>
 * Android kasuje <b>wszystkie</b> zarejestrowane alarmy po każdym restarcie telefonu.
 * Ten receiver automatycznie ponownie planuje budziki dla zmian,
 * które miały ustawioną flagę {@code hasAlarm = true} w bazie danych.
 * <p>
 * Mechanizm:
 * <ol>
 *   <li>System wywołuje {@code onReceive()} po starcie telefonu</li>
 *   <li>Pobieramy z DAO wszystkie GlobalShift z {@code has_alarm = 1}</li>
 *   <li>Dla każdej — wywołujemy {@link AlarmScheduler#scheduleOneOffAlarm(Context, GlobalShift)}</li>
 *   <li>AlarmScheduler sam pominie alarmy, które są już w przeszłości</li>
 * </ol>
 *
 * @see AlarmScheduler
 * @see GlobalShiftDao#getShiftsWithAlarms()
 */
public class BootAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "BootAlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }

        Log.i(TAG, "📱 Boot wykryty — rescheduluję alarmy budzika...");

        // goAsync() daje nam do ~30s zamiast ~10s na pracę w BroadcastReceiver
        final PendingResult pendingResult = goAsync();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
                GlobalShiftDao dao = db.globalShiftDao();

                List<GlobalShift> alarmedShifts = dao.getShiftsWithAlarms();
                Log.i(TAG, "🔍 Znaleziono " + alarmedShifts.size() + " zmian z aktywnym alarmem.");

                int scheduled = 0;
                for (GlobalShift shift : alarmedShifts) {
                    AlarmScheduler.scheduleOneOffAlarm(context.getApplicationContext(), shift);
                    scheduled++;
                }

                Log.i(TAG, "✅ Reschedule zakończony. Przetworzone: " + scheduled
                        + " (przeszłe/nieprawidłowe pominięte automatycznie przez AlarmScheduler).");
            } catch (Exception e) {
                Log.e(TAG, "❌ Błąd podczas reschedulowania alarmów: " + e.getMessage(), e);
            } finally {
                pendingResult.finish();
                executor.shutdown();
            }
        });
    }
}
