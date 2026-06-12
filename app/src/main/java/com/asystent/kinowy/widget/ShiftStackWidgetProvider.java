package com.asystent.kinowy.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import com.asystent.kinowy.R;
import com.asystent.kinowy.ui.MainActivity;

/**
 * AppWidgetProvider dla widgetu Stack (2x2) — stos kart z najbliższymi zmianami.
 * <p>
 * Karty przesuwa się gestem swipe. Kliknięcie w kartę otwiera apkę
 * i przechodzi do konkretnej zmiany w zakładce Grafik.
 */
public class ShiftStackWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_REFRESH = "com.asystent.kinowy.STACK_WIDGET_REFRESH";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            triggerUpdate(context);
        }
    }

    private void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stack);

        // Intent serwisu dostarczającego karty
        Intent serviceIntent = new Intent(context, ShiftStackRemoteViewsService.class);
        views.setRemoteAdapter(R.id.sv_shifts, serviceIntent);
        views.setEmptyView(R.id.sv_shifts, R.id.widget_stack_empty);

        // Template Intent — MainActivity dostanie EXTRA_OPEN_SHIFT_DATE dla konkretnej karty
        Intent openShiftIntent = new Intent(context, MainActivity.class);
        openShiftIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingTemplate = PendingIntent.getActivity(
                context, 2, openShiftIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.sv_shifts, pendingTemplate);

        manager.updateAppWidget(widgetId, views);
        // Powiedz systemowi żeby factory odświeżyła dane
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.sv_shifts);
    }

    public static void triggerUpdate(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, ShiftStackWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(widget);
        if (ids != null && ids.length > 0) {
            manager.notifyAppWidgetViewDataChanged(ids, R.id.sv_shifts);
        }
    }
}
