package com.asystent.kinowy.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.asystent.kinowy.models.Shift;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Harmonogram powiadomień push (nie mylić z {@code alarm.AlarmScheduler}, który
 * obsługuje dźwiękowy budzik pełnoekranowy).
 *
 * <p>Odpowiada za:
 * <ul>
 *   <li>Zaplanowanie powiadomienia START — X minut przed zmianą,</li>
 *   <li>Zaplanowanie powiadomienia END — dokładnie na koniec zmiany,</li>
 *   <li>Anulowanie obu powiadomień.</li>
 * </ul>
 *
 * <p>Poprzednia nazwa klasy: {@code AlarmScheduler} (w pakiecie notifications).
 * Zmieniona na {@code NotificationScheduler} by uniknąć konfliktu nazw
 * z {@code alarm.AlarmScheduler}.
 */
public class NotificationScheduler {

    private static final String PREFS_NAME = "asystent_kinowy_prefs";
    private static final String PREF_NOTIFY_BEFORE = "notify_before_minutes";
    private static final String TAG = "NotificationScheduler";

    public static void scheduleAlarms(Context context, List<Shift> shifts) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int notifyBeforeMinutes = prefs.getInt(PREF_NOTIFY_BEFORE, 60);

        LocalDateTime now = LocalDateTime.now();

        for (Shift shift : shifts) {
            if (shift.getDate() == null || shift.getStartTime() == null || shift.getEndTime() == null) continue;

            if (shift.isReplacement()) {
                cancelAlarmsForShift(context, shift);
                continue;
            }

            String startStr = shift.getStartTime().trim();
            String endStr = shift.getEndTime().trim();

            if (startStr.isEmpty() || endStr.isEmpty()) {
                Log.w(TAG, "Pusta godzina dla zmiany ID: " + shift.getId()
                        + " (start='" + startStr + "', end='" + endStr + "') — pomijam.");
                continue;
            }

            if (startStr.length() == 4 && startStr.charAt(1) == ':') startStr = "0" + startStr;
            if (endStr.length() == 4 && endStr.charAt(1) == ':') endStr = "0" + endStr;

            try {
                LocalDate date = LocalDate.parse(shift.getDate());
                LocalTime start = LocalTime.parse(startStr);
                LocalTime end = LocalTime.parse(endStr);

                LocalDateTime shiftStart = LocalDateTime.of(date, start);
                LocalDateTime shiftEnd = LocalDateTime.of(date, end);

                if (end.isBefore(start)) {
                    shiftEnd = shiftEnd.plusDays(1);
                }

                LocalDateTime notifyStart = shiftStart.minusMinutes(notifyBeforeMinutes);
                if (notifyStart.isAfter(now)) {
                    scheduleExactAlarm(context, alarmManager, shift, "START", notifyStart);
                }

                if (shiftEnd.isAfter(now)) {
                    scheduleExactAlarm(context, alarmManager, shift, "END", shiftEnd);
                }

            } catch (Exception e) {
                Log.e(TAG, "Błąd parsowania czasu dla zmiany ID: " + shift.getId() + ". Start: " + startStr, e);
            }
        }
    }

    /**
     * Planuje powiadomienia dla jednej edytowanej zmiany — najpierw anuluje stare,
     * a następnie planuje nowe na zaktualizowany czas.
     */
    public static void rescheduleForShift(Context context, Shift shift) {
        cancelAlarmsForShift(context, shift);

        if (shift.isReplacement()) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int notifyBeforeMinutes = prefs.getInt(PREF_NOTIFY_BEFORE, 60);
        LocalDateTime now = LocalDateTime.now();

        if (shift.getDate() == null || shift.getStartTime() == null || shift.getEndTime() == null) return;

        String startStr = shift.getStartTime().trim();
        String endStr = shift.getEndTime().trim();
        if (startStr.length() == 4 && startStr.charAt(1) == ':') startStr = "0" + startStr;
        if (endStr.length() == 4 && endStr.charAt(1) == ':') endStr = "0" + endStr;

        try {
            LocalDate date = LocalDate.parse(shift.getDate());
            LocalTime start = LocalTime.parse(startStr);
            LocalTime end = LocalTime.parse(endStr);

            LocalDateTime shiftStart = LocalDateTime.of(date, start);
            LocalDateTime shiftEnd = LocalDateTime.of(date, end);
            if (end.isBefore(start)) shiftEnd = shiftEnd.plusDays(1);

            LocalDateTime notifyStart = shiftStart.minusMinutes(notifyBeforeMinutes);
            if (notifyStart.isAfter(now)) {
                scheduleExactAlarm(context, alarmManager, shift, "START", notifyStart);
            }
            if (shiftEnd.isAfter(now)) {
                scheduleExactAlarm(context, alarmManager, shift, "END", shiftEnd);
            }
        } catch (Exception e) {
            Log.e(TAG, "Błąd reschedule dla zmiany ID: " + shift.getId(), e);
        }
    }

    /**
     * Anuluje oba alarmy (START + END) dla danej zmiany.
     */
    public static void cancelAlarmsForShift(Context context, Shift shift) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        cancelSingleAlarm(context, alarmManager, shift, "START");
        cancelSingleAlarm(context, alarmManager, shift, "END");

        Log.d(TAG, "Anulowano powiadomienia dla zmiany ID: " + shift.getId());
    }

    private static void cancelSingleAlarm(Context context, AlarmManager alarmManager, Shift shift, String type) {
        Intent intent = new Intent(context, ShiftNotificationReceiver.class);
        int requestCode = (int) shift.getId() + type.hashCode();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    private static void scheduleExactAlarm(Context context, AlarmManager alarmManager, Shift shift, String type, LocalDateTime time) {
        Intent intent = new Intent(context, ShiftNotificationReceiver.class);
        intent.putExtra(ShiftNotificationReceiver.EXTRA_TYPE, type);
        intent.putExtra(ShiftNotificationReceiver.EXTRA_DESC, shift.getDescription());
        intent.putExtra(ShiftNotificationReceiver.EXTRA_SHIFT_ID, shift.getId());

        int requestCode = (int) shift.getId() + type.hashCode();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerAtMillis = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        } catch (SecurityException e) {
            Log.e(TAG, "Brak uprawnień do ustawienia precyzyjnego alarmu.", e);
        }
    }
}
