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
import com.asystent.kinowy.db.AppDatabase;
import com.asystent.kinowy.db.GlobalShiftDao;
import com.asystent.kinowy.db.ShiftDao;
import com.asystent.kinowy.models.GlobalShift;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.ui.MainActivity;
import com.asystent.kinowy.utils.ShiftUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AppWidgetProvider — widżet "Najbliższa Zmiana + Ekipa" na ekranie głównym.
 * <p>
 * Pobiera dane bezpośrednio z bazy Room (synchronicznie, na wątku w tle)
 * i aktualizuje RemoteViews. Kliknięcie otwiera {@link MainActivity}.
 * <p>
 * Odświeżanie:
 * <ul>
 *   <li><b>Automatyczne:</b> co 30 minut ({@code updatePeriodMillis} w XML)</li>
 *   <li><b>Po synchronizacji:</b> {@link #triggerUpdate(Context)} wywoływane z ViewModel</li>
 *   <li><b>Ręczne:</b> przycisk odświeżania na widżecie</li>
 * </ul>
 */
public class ShiftWidgetProvider extends AppWidgetProvider {

    /** Akcja dla przycisku odświeżania na widżecie. */
    private static final String ACTION_REFRESH = "com.asystent.kinowy.WIDGET_REFRESH";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        // Obsługa przycisku odświeżania
        if (ACTION_REFRESH.equals(intent.getAction())) {
            triggerUpdate(context);
        }
    }

    /**
     * Aktualizuje pojedynczy widżet.
     * Pobiera dane z bazy na wątku w tle, potem aktualizuje RemoteViews.
     */
    private void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
                ShiftDao shiftDao = db.shiftDao();
                GlobalShiftDao globalShiftDao = db.globalShiftDao();

                // 1. Znajdź najbliższą zmianę
                Shift nextShift = findNextShift(shiftDao.getAllShiftsSync());

                // 2. Zbuduj RemoteViews
                RemoteViews views = buildViews(context, nextShift, globalShiftDao);

                // 3. Aktualizuj widżet
                manager.updateAppWidget(widgetId, views);
            } catch (Exception e) {
                // Fallback — pokaż pustą kartę
                RemoteViews fallback = new RemoteViews(context.getPackageName(), R.layout.widget_shift);
                fallback.setTextViewText(R.id.widget_time, "Błąd odświeżania");
                manager.updateAppWidget(widgetId, fallback);
            }
        });
    }

    /**
     * Buduje RemoteViews z danymi o najbliższej zmianie i współpracownikach.
     */
    private RemoteViews buildViews(Context context, Shift shift, GlobalShiftDao globalShiftDao) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_shift);

        // ─── PendingIntent: kliknięcie → MainActivity ───
        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                context, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, openPending);

        // ─── PendingIntent: przycisk odświeżania ───
        Intent refreshIntent = new Intent(context, ShiftWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        PendingIntent refreshPending = PendingIntent.getBroadcast(
                context, 1, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_widget_refresh, refreshPending);

        if (shift == null) {
            // Brak zmian
            views.setTextViewText(R.id.widget_date, "—");
            views.setTextViewText(R.id.widget_time, "Brak zaplanowanych zmian");
            views.setViewVisibility(R.id.widget_category, View.GONE);
            views.setViewVisibility(R.id.widget_crew, View.GONE);
            return views;
        }

        // ─── Data ───
        views.setTextViewText(R.id.widget_date, formatDate(shift.getDate()));

        // ─── Godziny ───
        String moon = shift.isClosingShift() ? "🌙 " : "";
        views.setTextViewText(R.id.widget_time,
                moon + shift.getStartTime() + " – " + shift.getEndTime());

        // ─── Stanowisko ───
        String cat = shift.getCategory();
        if (cat != null && !cat.isEmpty() && !"UNKNOWN".equals(cat)) {
            views.setTextViewText(R.id.widget_category, cat);
            views.setViewVisibility(R.id.widget_category, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.widget_category, View.GONE);
        }

        // ─── Ekipa (overlap z global_shifts) ───
        try {
            List<GlobalShift> dailyShifts = globalShiftDao.getShiftsByDate(shift.getDate());
            List<GlobalShift> overlapping = ShiftUtils.getOverlappingShifts(
                    shift.getStartTime(), shift.getEndTime(), dailyShifts);

            if (overlapping != null && !overlapping.isEmpty()) {
                String crewText = ShiftUtils.formatWidgetCrew(overlapping);
                views.setTextViewText(R.id.widget_crew, crewText);
                views.setViewVisibility(R.id.widget_crew, View.VISIBLE);
            } else {
                views.setViewVisibility(R.id.widget_crew, View.GONE);
            }
        } catch (Exception e) {
            views.setViewVisibility(R.id.widget_crew, View.GONE);
        }

        return views;
    }

    /**
     * Znajduje najbliższą przyszłą zmianę z listy.
     */
    private Shift findNextShift(List<Shift> shifts) {
        if (shifts == null || shifts.isEmpty()) return null;

        LocalDateTime now = LocalDateTime.now();
        Shift upcoming = null;
        LocalDateTime upcomingTime = null;

        for (Shift shift : shifts) {
            if (shift.getDate() == null || shift.getStartTime() == null) continue;
            try {
                LocalDate date = LocalDate.parse(shift.getDate());
                LocalTime time = LocalTime.parse(shift.getStartTime(), TIME_FMT);
                LocalDateTime shiftStart = LocalDateTime.of(date, time);
                if (shiftStart.isAfter(now)) {
                    if (upcomingTime == null || shiftStart.isBefore(upcomingTime)) {
                        upcomingTime = shiftStart;
                        upcoming = shift;
                    }
                }
            } catch (Exception ignored) {}
        }
        return upcoming;
    }

    /**
     * Formatuje datę do czytelnej formy polskiej.
     */
    private String formatDate(String isoDate) {
        if (isoDate == null) return "—";
        try {
            LocalDate date = LocalDate.parse(isoDate);
            LocalDate today = LocalDate.now();
            if (date.equals(today)) return "Dziś";
            if (date.equals(today.plusDays(1))) return "Jutro";
            return date.format(DateTimeFormatter.ofPattern("d MMMM (EEEE)",
                    new java.util.Locale("pl", "PL")));
        } catch (Exception e) {
            return isoDate;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Statyczne API — wywoływane z zewnątrz (ViewModel, etc.)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Wymusza aktualizację wszystkich instancji widżetu.
     * <p>
     * Wywoływane po synchronizacji grafiku z Gmaila
     * oraz przez przycisk odświeżania na samym widżecie.
     *
     * @param context dowolny kontekst (Application, Activity, BroadcastReceiver)
     */
    public static void triggerUpdate(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, ShiftWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(widget);
        if (ids != null && ids.length > 0) {
            Intent intent = new Intent(context, ShiftWidgetProvider.class);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            context.sendBroadcast(intent);
        }
    }
}
