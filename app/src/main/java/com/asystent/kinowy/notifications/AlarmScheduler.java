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

            // Jeśli oddałeś tę zmianę komuś innemu, nie ustawiamy dla niej alarmu!
            if (shift.isReplacement()) continue;

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
                // Jeśli parsowanie czasu jakimś cudem zawiedzie, logujemy błąd i ignorujemy tę zmianę.
                // Dzięki temu aplikacja nie zcrashuje się przy starcie!
                Log.e(TAG, "Błąd parsowania czasu dla zmiany ID: " + shift.getId() + ". Start: " + startStr, e);
            }
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