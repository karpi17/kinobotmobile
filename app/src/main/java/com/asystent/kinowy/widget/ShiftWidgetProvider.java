package com.asystent.kinowy.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

import java.time.Duration;
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
 * Pobiera dane bezpośrednio z bazy Room (synchronicznie, na wątku w tle).
 * Odświeżanie: co 30 min (XML), po syncu (triggerUpdate), ręczne (przycisk).
 * <p>
 * v2.0 — dodano: countdown, status "Trwa teraz", ikona alarmu,
 * filtrowanie isReplacement, wykluczanie własnego imienia z ekipy.
 */
public class ShiftWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "com.asystent.kinowy.WIDGET_REFRESH";
    /** Klucz Intent extra do deep-linku konkretnej zmiany z widgetu stack. */
    public static final String EXTRA_OPEN_SHIFT_DATE = "open_shift_date";
    /** P1 fix: dodatkowy klucz do precyzyjnej identyfikacji zmiany po ID bazy danych. */
    public static final String EXTRA_OPEN_SHIFT_ID   = "open_shift_id";

    private static final String PREFS_NAME = "asystent_kinowy_prefs";
    private static final String PREF_USER_NAME = "user_name";

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
        if (ACTION_REFRESH.equals(intent.getAction())) {
            triggerUpdate(context);
        }
    }

    private void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
                ShiftDao shiftDao = db.shiftDao();
                GlobalShiftDao globalShiftDao = db.globalShiftDao();

                String userName = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(PREF_USER_NAME, "");

                // Filtrujemy zmiany oddane (isReplacement=true) — to nie Twoje zmiany
                Shift nextShift = findNextShift(shiftDao.getAllShiftsSync());
                RemoteViews views = buildViews(context, nextShift, globalShiftDao, userName);
                manager.updateAppWidget(widgetId, views);
            } catch (Exception e) {
                RemoteViews fallback = new RemoteViews(context.getPackageName(), R.layout.widget_shift);
                fallback.setTextViewText(R.id.widget_time, "Błąd odświeżania");
                manager.updateAppWidget(widgetId, fallback);
            }
        });
    }

    private RemoteViews buildViews(Context context, Shift shift, GlobalShiftDao globalShiftDao, String userName) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_shift);

        // Kliknięcie w tło widgetu → otwórz MainActivity
        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                context, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, openPending);

        // Przycisk odświeżania
        Intent refreshIntent = new Intent(context, ShiftWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        PendingIntent refreshPending = PendingIntent.getBroadcast(
                context, 1, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btn_widget_refresh, refreshPending);

        if (shift == null) {
            views.setTextViewText(R.id.widget_date, "—");
            views.setTextViewText(R.id.widget_time, "Brak zaplanowanych zmian");
            views.setViewVisibility(R.id.widget_countdown, View.GONE);
            views.setViewVisibility(R.id.widget_category_row, View.GONE);
            views.setViewVisibility(R.id.widget_crew, View.GONE);
            return views;
        }

        // Data
        views.setTextViewText(R.id.widget_date, formatDate(shift.getDate()));

        // Godziny + ikona zamka
        String moon = shift.isClosingShift() ? "🌙 " : "";
        views.setTextViewText(R.id.widget_time, moon + shift.getStartTime() + " – " + shift.getEndTime());

        // ═══ Countdown / status "Trwa teraz" ═══
        String countdown = buildCountdown(shift);
        if (countdown != null) {
            views.setTextViewText(R.id.widget_countdown, countdown);
            views.setViewVisibility(R.id.widget_countdown, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.widget_countdown, View.GONE);
        }

        // ═══ Stanowisko + ikona alarmu ═══
        String cat = shift.getCategory();
        boolean hasCategory = cat != null && !cat.isEmpty() && !"UNKNOWN".equals(cat);
        if (hasCategory) {
            views.setTextViewText(R.id.widget_category, cat);
            views.setViewVisibility(R.id.widget_category_row, View.VISIBLE);

            // Alarm — sprawdź czy GlobalShift ma alarm
            boolean hasAlarm = checkAlarm(globalShiftDao, shift.getDate(), shift.getStartTime());
            views.setViewVisibility(R.id.widget_alarm_icon, hasAlarm ? View.VISIBLE : View.GONE);
        } else {
            views.setViewVisibility(R.id.widget_category_row, View.GONE);
        }

        // ═══ Ekipa (bez siebie samego) ═══
        try {
            String resolvedUser = userName.isEmpty() ? null : userName;
            List<GlobalShift> crew = resolvedUser != null
                    ? globalShiftDao.getCrewByDateExcluding(shift.getDate(), resolvedUser)
                    : globalShiftDao.getShiftsByDate(shift.getDate());

            List<GlobalShift> overlapping = ShiftUtils.getOverlappingShifts(
                    shift.getStartTime(), shift.getEndTime(), crew);
            if (overlapping != null && !overlapping.isEmpty()) {
                views.setTextViewText(R.id.widget_crew, ShiftUtils.formatWidgetCrew(overlapping));
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
     * Szuka najbliższej zmiany spośród listy — pomija zmiany oddane (isReplacement=true).
     */
    private Shift findNextShift(List<Shift> shifts) {
        if (shifts == null || shifts.isEmpty()) return null;
        LocalDateTime now = LocalDateTime.now();
        Shift upcoming = null;
        Shift ongoing = null;
        LocalDateTime upcomingTime = null;

        for (Shift shift : shifts) {
            // Zmiany oddane = nietwoje, ignoruj
            if (shift.isReplacement()) continue;
            if (shift.getDate() == null || shift.getStartTime() == null) continue;
            try {
                LocalDate date = LocalDate.parse(shift.getDate());
                LocalTime startTime = LocalTime.parse(shift.getStartTime(), TIME_FMT);
                LocalDateTime shiftStart = LocalDateTime.of(date, startTime);

                // Oblicz koniec (uwzględnij zamki przechodzące przez północ)
                LocalDateTime shiftEnd = buildShiftEnd(date, shift.getStartTime(), shift.getEndTime(), shift.isClosingShift());

                if (now.isAfter(shiftStart) && now.isBefore(shiftEnd)) {
                    // Trwa teraz — priorytet
                    ongoing = shift;
                } else if (shiftStart.isAfter(now)) {
                    if (upcomingTime == null || shiftStart.isBefore(upcomingTime)) {
                        upcomingTime = shiftStart;
                        upcoming = shift;
                    }
                }
            } catch (Exception ignored) {}
        }
        // "Trwa teraz" bierze pierwszeństwo nad "najbliższy"
        return ongoing != null ? ongoing : upcoming;
    }

    /**
     * Buduje tekst countdownu:
     * - "🟢 Trwa teraz" gdy zmiana w toku
     * - "za Xh Ymin" gdy zmiana w przyszłości
     * - null gdy nie da się obliczyć
     */
    private String buildCountdown(Shift shift) {
        try {
            LocalDate date = LocalDate.parse(shift.getDate());
            LocalTime startTime = LocalTime.parse(shift.getStartTime(), TIME_FMT);
            LocalDateTime shiftStart = LocalDateTime.of(date, startTime);
            LocalDateTime shiftEnd = buildShiftEnd(date, shift.getStartTime(), shift.getEndTime(), shift.isClosingShift());
            LocalDateTime now = LocalDateTime.now();

            if (now.isAfter(shiftStart) && now.isBefore(shiftEnd)) {
                return "🟢 Trwa teraz";
            }
            if (shiftStart.isAfter(now)) {
                Duration diff = Duration.between(now, shiftStart);
                long hours = diff.toHours();
                long minutes = diff.toMinutesPart();
                if (hours > 0) {
                    return "za " + hours + "h " + minutes + "min";
                } else {
                    return "za " + minutes + " min";
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Sprawdza czy GlobalShift powiązany z tą zmianą ma aktywny alarm.
     */
    private boolean checkAlarm(GlobalShiftDao dao, String date, String startTime) {
        try {
            GlobalShift gs = dao.getShiftByDateAndStart(date, startTime);
            return gs != null && gs.isHasAlarm();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Oblicza LocalDateTime końca zmiany z uwzględnieniem zamków (koniec po północy = +1 dzień).
     * <p>
     * P1 fix: Jeśli endTime < startTime (np. 22:00–02:00), zmiana przez północ jest wykrywana
     * automatycznie — niezależnie od flagi isClosingShift.
     */
    private LocalDateTime buildShiftEnd(LocalDate date, String startTimeStr,
                                        String endTimeStr, boolean isClosing) {
        if (endTimeStr == null || endTimeStr.isEmpty()) {
            return LocalDateTime.of(date, LocalTime.MAX);
        }
        LocalTime endTime = LocalTime.parse(endTimeStr, TIME_FMT);
        // Warunek 1: flaga + koniec przed 05:00 (klasyczny zamek)
        if (isClosing && endTime.isBefore(LocalTime.of(5, 0))) {
            return LocalDateTime.of(date.plusDays(1), endTime);
        }
        // Warunek 2 (P1): endTime < startTime — zmiana przez północ bez flagi
        if (startTimeStr != null && !startTimeStr.isEmpty()) {
            try {
                LocalTime startTime = LocalTime.parse(startTimeStr, TIME_FMT);
                if (endTime.isBefore(startTime)) {
                    return LocalDateTime.of(date.plusDays(1), endTime);
                }
            } catch (Exception ignored) {}
        }
        return LocalDateTime.of(date, endTime);
    }

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
