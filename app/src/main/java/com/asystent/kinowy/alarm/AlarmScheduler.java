package com.asystent.kinowy.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.asystent.kinowy.models.GlobalShift;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Menedżer harmonogramu alarmów (budzików) dla zmian.
 * <p>
 * Alarmy są <b>jednorazowe</b> — użytkownik sam decyduje, do której zmiany
 * chce dodać budzik i ustala indywidualny czas wyprzedzenia
 * ({@link GlobalShift#getAlarmOffsetMinutes()}).
 * <p>
 * Używa {@link AlarmManager#setExactAndAllowWhileIdle(int, long, PendingIntent)}
 * dla dokładnego wyzwalania nawet w trybie Doze.
 * <p>
 * Każdy alarm jest identyfikowany przez unikalny {@code requestCode}
 * wyliczany z {@link GlobalShift#getId()}, co umożliwia anulowanie.
 *
 * @see ShiftAlarmReceiver
 */
public class AlarmScheduler {

    private static final String TAG = "AlarmScheduler";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // Extras przekazywane do ShiftAlarmReceiver
    public static final String EXTRA_SHIFT_ID = "extra_shift_id";
    public static final String EXTRA_SHIFT_NAME = "extra_shift_name";
    public static final String EXTRA_SHIFT_DATE = "extra_shift_date";
    public static final String EXTRA_SHIFT_START = "extra_shift_start";
    public static final String EXTRA_SHIFT_CATEGORY = "extra_shift_category";

    /**
     * Ustawia jednorazowy alarm (budzik) dla podanej zmiany.
     * <p>
     * Czas alarmu = {@code (date + startTime) - alarmOffsetMinutes}.
     * <p>
     * Jeśli obliczony czas jest w przeszłości, alarm <b>nie zostanie ustawiony</b>.
     *
     * @param context kontekst aplikacji
     * @param shift   zmiana z ustawionymi hasAlarm=true i alarmOffsetMinutes
     */
    public static void scheduleOneOffAlarm(Context context, GlobalShift shift) {
        if (context == null || shift == null) return;
        if (!shift.isHasAlarm()) {
            Log.w(TAG, "Shift #" + shift.getId() + " nie ma aktywnego alarmu — pomijam.");
            return;
        }

        // Walidacja: data i godzina nie mogą być null/puste
        if (shift.getDate() == null || shift.getDate().trim().isEmpty()) {
            Log.w(TAG, "⚠️ Shift #" + shift.getId() + " ma pustą datę — alarm nie zostanie ustawiony.");
            return;
        }
        if (shift.getStartTime() == null || shift.getStartTime().trim().isEmpty()) {
            Log.w(TAG, "⚠️ Shift #" + shift.getId() + " ma pusty startTime — alarm nie zostanie ustawiony.");
            return;
        }

        long triggerAtMillis = calculateTriggerTime(shift);
        if (triggerAtMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "Alarm dla #" + shift.getId() + " jest w przeszłości — pomijam.");
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager niedostępny!");
            return;
        }

        PendingIntent pi = buildPendingIntent(context, shift);

        // Android 12+ wymaga sprawdzenia canScheduleExactAlarms()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Brak uprawnień do exact alarms (Android 12+). Używam inexact.");
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
                return;
            }
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);

        Log.i(TAG, "🔔 Alarm ustawiony: shift #" + shift.getId()
                + " | " + shift.getDate() + " " + shift.getStartTime()
                + " | offset: -" + shift.getAlarmOffsetMinutes() + "min"
                + " | trigger: " + triggerAtMillis);
    }

    /**
     * Anuluje alarm dla podanej zmiany.
     *
     * @param context kontekst aplikacji
     * @param shift   zmiana, której alarm ma być anulowany
     */
    public static void cancelAlarm(Context context, GlobalShift shift) {
        if (context == null || shift == null) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent pi = buildPendingIntent(context, shift);
        alarmManager.cancel(pi);
        pi.cancel();

        Log.i(TAG, "🔕 Alarm anulowany: shift #" + shift.getId()
                + " | " + shift.getDate() + " " + shift.getStartTime());
    }

    // ─── Pomocnicze ──────────────────────────────────────────────────────

    /**
     * Oblicza czas wyzwolenia alarmu w milisekundach (epoch).
     * <p>
     * {@code triggerTime = (date + startTime) - alarmOffsetMinutes}
     */
    private static long calculateTriggerTime(GlobalShift shift) {
        // Dodatkowa warstwa ochrony przed pustymi stringami
        String dateStr = shift.getDate();
        String timeStr = shift.getStartTime();
        if (dateStr == null || dateStr.trim().isEmpty()
                || timeStr == null || timeStr.trim().isEmpty()) {
            Log.w(TAG, "⚠️ calculateTriggerTime: pusty date='" + dateStr
                    + "' lub startTime='" + timeStr + "' — zwracam 0.");
            return 0;
        }

        try {
            LocalDate date = LocalDate.parse(dateStr.trim(), DATE_FMT);
            LocalTime time = LocalTime.parse(timeStr.trim(), TIME_FMT);
            LocalDateTime shiftStart = LocalDateTime.of(date, time);

            // Odejmij offset
            LocalDateTime alarmTime = shiftStart.minusMinutes(shift.getAlarmOffsetMinutes());

            return alarmTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            Log.e(TAG, "Błąd parsowania daty/czasu zmiany: date='" + dateStr
                    + "', startTime='" + timeStr + "' — " + e.getMessage());
            return 0;
        }
    }

    /**
     * Buduje PendingIntent kierujący do ShiftAlarmReceiver.
     * <p>
     * {@code requestCode} = {@link GlobalShift#getId()} — unikalny per zmianę,
     * pozwala na anulowanie konkretnego alarmu.
     */
    private static PendingIntent buildPendingIntent(Context context, GlobalShift shift) {
        Intent intent = new Intent(context, ShiftAlarmReceiver.class);
        intent.putExtra(EXTRA_SHIFT_ID, shift.getId());
        intent.putExtra(EXTRA_SHIFT_NAME, shift.getName());
        intent.putExtra(EXTRA_SHIFT_DATE, shift.getDate());
        intent.putExtra(EXTRA_SHIFT_START, shift.getStartTime());
        intent.putExtra(EXTRA_SHIFT_CATEGORY, shift.getCategory());

        return PendingIntent.getBroadcast(
                context,
                shift.getId(), // requestCode = ID zmiany
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    // ─── Snooze ─────────────────────────────────────────────────────────

    /**
     * Ustawia alarm „drzemki" za dokładnie 10 minut od teraz.
     * <p>
     * Wywoływane przez {@link AlarmRingingActivity} po naciśnięciu przycisku DRZEMKA.
     * Używa {@code requestCode = Integer.MAX_VALUE - 1}, aby nie kolidować
     * z normalnymi alarmami opartymi na shift ID.
     *
     * @param context       kontekst aplikacji
     * @param shiftStart    godzina startu zmiany (HH:mm) — do wyświetlenia w UI
     * @param shiftDate     data zmiany (yyyy-MM-dd) — do wyświetlenia w UI
     * @param shiftCategory kategoria zmiany (BAR/OW) — do wyświetlenia w UI
     */
    public static void scheduleSnoozeAlarm(Context context, String shiftStart,
                                           String shiftDate, String shiftCategory) {
        Log.i(TAG, "💤 scheduleSnoozeAlarm() ENTERED | start=" + shiftStart
                + " | date=" + shiftDate + " | category=" + shiftCategory);

        if (context == null) {
            Log.e(TAG, "💤 ABORT: context == null!");
            return;
        }

        long triggerAtMillis = System.currentTimeMillis() + (10 * 60 * 1000); // +10 minut

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "💤 ABORT: AlarmManager niedostępny — nie można ustawić drzemki!");
            return;
        }

        Intent intent = new Intent(context, ShiftAlarmReceiver.class);
        intent.putExtra(EXTRA_SHIFT_ID, Integer.MAX_VALUE - 1); // specjalny ID dla snooze
        intent.putExtra(EXTRA_SHIFT_NAME, "Drzemka");
        intent.putExtra(EXTRA_SHIFT_DATE, shiftDate);
        intent.putExtra(EXTRA_SHIFT_START, shiftStart);
        intent.putExtra(EXTRA_SHIFT_CATEGORY, shiftCategory);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                Integer.MAX_VALUE - 1, // unikalny requestCode
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Android 12+ wymaga sprawdzenia canScheduleExactAlarms()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean canExact = alarmManager.canScheduleExactAlarms();
            Log.i(TAG, "💤 canScheduleExactAlarms() = " + canExact
                    + " | SDK=" + Build.VERSION.SDK_INT);
            if (!canExact) {
                Log.w(TAG, "💤 FALLBACK: Używam inexact alarm (set) zamiast exact!");
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
                Log.i(TAG, "💤 Drzemka INEXACT ustawiona: trigger=" + triggerAtMillis
                        + " (za ~10 min od teraz=" + System.currentTimeMillis() + ")");
                return;
            }
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);

        Log.i(TAG, "💤 Drzemka EXACT ustawiona: +10 min | trigger: " + triggerAtMillis
                + " (teraz=" + System.currentTimeMillis() + ")");
    }
}
