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

public class AlarmScheduler {

    private static final String PREFS_NAME = "asystent_kinowy_prefs";
    private static final String PREF_NOTIFY_BEFORE = "notify_before_minutes";
    private static final String TAG = "AlarmScheduler";

    public static void scheduleAlarms(Context context, List<Shift> shifts) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // Pobierzmy ustawienie z SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int notifyBeforeMinutes = prefs.getInt(PREF_NOTIFY_BEFORE, 60);

        LocalDateTime now = LocalDateTime.now();

        // Harmonogram powiadomień
        for (Shift shift : shifts) {
            // Walidacja podstawowa
            if (shift.getDate() == null || shift.getStartTime() == null || shift.getEndTime() == null) continue;

            // Jeśli oddałeś tę zmianę komuś innemu, anuluj i nie ustawiamy alarmu!
            if (shift.isReplacement()) {
                cancelAlarmsForShift(context, shift);
                continue;
            }

            String startStr = shift.getStartTime().trim();
            String endStr = shift.getEndTime().trim();

            // Kuloodporny fix na jednocyfrowe godziny wpadające z bazy/Excela (np. "0:00" -> "00:00")
            if (startStr.length() == 4 && startStr.charAt(1) == ':') startStr = "0" + startStr;
            if (endStr.length() == 4 && endStr.charAt(1) == ':') endStr = "0" + endStr;

            try {
                LocalDate date = LocalDate.parse(shift.getDate());
                LocalTime start = LocalTime.parse(startStr);
                LocalTime end = LocalTime.parse(endStr);

                LocalDateTime shiftStart = LocalDateTime.of(date, start);
                LocalDateTime shiftEnd = LocalDateTime.of(date, end);

                // Obsługa zmian nocnych (przechodzących przez północ)
                if (end.isBefore(start)) {
                    shiftEnd = shiftEnd.plusDays(1);
                }

                // Powiadomienie START
                LocalDateTime notifyStart = shiftStart.minusMinutes(notifyBeforeMinutes);
                if (notifyStart.isAfter(now)) {
                    scheduleExactAlarm(context, alarmManager, shift, "START", notifyStart);
                }

                // Powiadomienie END (np. "Koniec zmiany, wpisz manko!")
                if (shiftEnd.isAfter(now)) {
                    scheduleExactAlarm(context, alarmManager, shift, "END", shiftEnd);
                }

            } catch (Exception e) {
                Log.e(TAG, "Błąd parsowania czasu dla zmiany ID: " + shift.getId() + ". Start: " + startStr, e);
            }
        }
    }

    /**
     * Planuje alarm dla jednej edytowanej zmiany — najpierw anuluje stare alarmy,
     * a następnie planuje nowe na zaktualizowany czas.
     *
     * @param context kontekst
     * @param shift   zmiana po edycji
     */
    public static void rescheduleForShift(Context context, Shift shift) {
        // 1. Anuluj stare alarmy (START + END)
        cancelAlarmsForShift(context, shift);

        // 2. Nie planujemy alarmów dla oddanych zmian
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
     * Używa tego samego requestCode co scheduleExactAlarm, więc PendingIntent się zgadza.
     *
     * @param context kontekst
     * @param shift   zmiana do anulowania
     */
    public static void cancelAlarmsForShift(Context context, Shift shift) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // Anuluj alarm START
        cancelSingleAlarm(context, alarmManager, shift, "START");
        // Anuluj alarm END
        cancelSingleAlarm(context, alarmManager, shift, "END");

        Log.d(TAG, "Anulowano alarmy dla zmiany ID: " + shift.getId());
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

        // Unikalny requestCode zapobiega nadpisywaniu się różnych alarmów
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
            // Brak uprawnień do SCHEDULE_EXACT_ALARM w Android 14+
            Log.e(TAG, "Brak uprawnień do ustawienia precyzyjnego alarmu.", e);
        }
    }
}