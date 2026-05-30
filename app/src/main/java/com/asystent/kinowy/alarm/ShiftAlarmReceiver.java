package com.asystent.kinowy.alarm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.asystent.kinowy.R;

/**
 * BroadcastReceiver wywoływany przez AlarmManager, gdy budzik się włącza.
 * <p>
 * Tworzy kanał powiadomień o najwyższym priorytecie z kategorią {@code CATEGORY_ALARM}
 * i przypisuje Full-Screen Intent kierujący do {@link AlarmRingingActivity}.
 * Dzięki temu system Android natychmiast wybudzi ekran i wyświetli aktywność alarmu.
 * <p>
 * Odbiera dane zmiany z extras ({@link AlarmScheduler#EXTRA_SHIFT_ID},
 * {@link AlarmScheduler#EXTRA_SHIFT_START}, etc.) i przekazuje je dalej do aktywności.
 *
 * @see AlarmScheduler
 * @see AlarmRingingActivity
 */
public class ShiftAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "ShiftAlarmReceiver";
    private static final String CHANNEL_ID = "shift_alarm_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        // ─── Odczyt danych zmiany z Intencji ────────────────────────────
        int shiftId = intent.getIntExtra(AlarmScheduler.EXTRA_SHIFT_ID, -1);
        String name = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_NAME);
        String date = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_DATE);
        String start = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_START);
        String category = intent.getStringExtra(AlarmScheduler.EXTRA_SHIFT_CATEGORY);

        Log.i(TAG, "🔔 ALARM! Shift #" + shiftId
                + " | " + date + " " + start
                + " | " + (category != null ? category : "?")
                + " | " + (name != null ? name : "?"));

        // ─── Tworzenie NotificationChannel ──────────────────────────────
        createAlarmNotificationChannel(context);

        // ─── Full-Screen Intent → AlarmRingingActivity ──────────────────
        Intent fullScreenIntent = new Intent(context, AlarmRingingActivity.class);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        fullScreenIntent.putExtra(AlarmRingingActivity.EXTRA_SHIFT_START, start);
        fullScreenIntent.putExtra(AlarmRingingActivity.EXTRA_SHIFT_DATE, date);
        fullScreenIntent.putExtra(AlarmRingingActivity.EXTRA_SHIFT_CATEGORY, category);
        fullScreenIntent.putExtra(AlarmRingingActivity.EXTRA_SHIFT_ID, shiftId);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                shiftId,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // ─── Budowanie Powiadomienia ────────────────────────────────────
        String notificationTitle = context.getString(R.string.alarm_notification_title);
        String notificationText = context.getString(
                R.string.alarm_notification_text,
                start != null ? start : "??:??",
                category != null ? category : "Zmiana"
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm_cinema)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setOngoing(true)
                // Full-Screen Intent — kluczowy mechanizm wybudzania ekranu
                .setFullScreenIntent(fullScreenPendingIntent, true)
                // Tap na powiadomieniu → też otwiera AlarmRingingActivity
                .setContentIntent(fullScreenPendingIntent);

        // ─── Wyświetlenie powiadomienia ─────────────────────────────────
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            // Używamy shiftId jako ID powiadomienia (unikalne per zmiana)
            notificationManager.notify(shiftId, builder.build());
            Log.i(TAG, "📢 Powiadomienie Full-Screen wysłane dla shift #" + shiftId);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NotificationChannel
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Tworzy kanał powiadomień o najwyższym priorytecie (IMPORTANCE_HIGH)
     * dla alarmów budzika. Kanał jest tworzony tylko raz — Android ignoruje
     * kolejne wywołania z tym samym ID.
     */
    private void createAlarmNotificationChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.alarm_channel_desc));
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[]{0, 500, 500, 500});
        channel.setBypassDnd(true); // Omijaj DND

        // Dźwięk kanału — systemowy alarm
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        channel.setSound(alarmSound, audioAttributes);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "✅ NotificationChannel '" + CHANNEL_ID + "' utworzony/zaktualizowany.");
        }
    }
}
