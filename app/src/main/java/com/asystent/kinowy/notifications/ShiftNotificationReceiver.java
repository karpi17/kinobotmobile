package com.asystent.kinowy.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.asystent.kinowy.R;
import com.asystent.kinowy.ui.MainActivity;

public class ShiftNotificationReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "shift_notifications";
    public static final String EXTRA_TYPE = "type"; // "START" lub "END"
    public static final String EXTRA_DESC = "description";
    public static final String EXTRA_SHIFT_ID = "shift_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String type = intent.getStringExtra(EXTRA_TYPE);
        String desc = intent.getStringExtra(EXTRA_DESC);
        int shiftId = intent.getIntExtra(EXTRA_SHIFT_ID, 0);

        if (type == null) return;

        createNotificationChannel(context);

        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                (int) shiftId, 
                mainIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = "";
        String contentText = "";

        if ("START".equals(type)) {
            title = "Powiadomienie Grafiku";
            contentText = "Zbliża się zmiana: " + (desc != null ? desc : "");
        } else if ("END".equals(type)) {
            title = "Powiadomienie Grafiku";
            contentText = "Czy zmiana skończona? Kliknij, aby edytować.";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // Zostanie zmienione na właściwą app icon później, używamy systemowej vector
                .setContentTitle(title)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify((int) shiftId + type.hashCode(), builder.build());
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Powiadomienia Grafiku";
            String description = "Powiadomienia o zbliżających się i zakończonych zmianach";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
