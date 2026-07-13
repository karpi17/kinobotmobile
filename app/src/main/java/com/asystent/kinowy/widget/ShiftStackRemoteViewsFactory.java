package com.asystent.kinowy.widget;

import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.asystent.kinowy.R;
import com.asystent.kinowy.db.AppDatabase;
import com.asystent.kinowy.db.GlobalShiftDao;
import com.asystent.kinowy.db.ShiftDao;
import com.asystent.kinowy.models.GlobalShift;
import com.asystent.kinowy.models.Shift;
import com.asystent.kinowy.utils.ShiftUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RemoteViewsFactory — dostarcza dane kart dla StackView w widgecie 2x2.
 * <p>
 * Pobiera max 10 najbliższych zmian z Room, filtruje isReplacement=true.
 * Każda karta po kliknięciu wysyła EXTRA_OPEN_SHIFT_DATE do MainActivity.
 */
class ShiftStackRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

    private static final int MAX_SHIFTS = 10;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("d MMM (EEEE)", new Locale("pl", "PL"));

    private final Context context;
    private final List<Shift> items = new ArrayList<>();
    private GlobalShiftDao globalShiftDao;
    private String userName = "";

    ShiftStackRemoteViewsFactory(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate() {
        AppDatabase db = AppDatabase.getInstance(context);
        globalShiftDao = db.globalShiftDao();
        userName = context.getSharedPreferences("asystent_kinowy_prefs", Context.MODE_PRIVATE)
                .getString("user_name", "");
        loadData();
    }

    @Override
    public void onDataSetChanged() {
        // Musi zdjąć identity token bo wywoływane na remote wątku
        final long token = Binder.clearCallingIdentity();
        try {
            userName = context.getSharedPreferences("asystent_kinowy_prefs", Context.MODE_PRIVATE)
                    .getString("user_name", "");
            loadData();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void loadData() {
        items.clear();
        try {
            ShiftDao shiftDao = AppDatabase.getInstance(context).shiftDao();
            List<Shift> all = shiftDao.getAllShiftsSync();
            if (all == null) return;

            LocalDate today = LocalDate.now();
            int count = 0;
            for (Shift s : all) {
                if (count >= MAX_SHIFTS) break;
                // Pomijamy zmiany oddane i przeszłe
                if (s.isReplacement()) continue;
                if (s.getDate() == null) continue;
                try {
                    if (!LocalDate.parse(s.getDate()).isBefore(today)) {
                        items.add(s);
                        count++;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position < 0 || position >= items.size()) {
            return getLoadingView();
        }
        Shift shift = items.get(position);
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_stack_item);

        // Data
        rv.setTextViewText(R.id.widget_stack_date, formatDate(shift.getDate()));

        // Godziny
        String moon = shift.isClosingShift() ? "🌙 " : "";
        rv.setTextViewText(R.id.widget_stack_time, moon + shift.getStartTime() + " – " + shift.getEndTime());

        // Stanowisko (chip)
        String cat = shift.getCategory();
        if (cat != null && !cat.isEmpty() && !"UNKNOWN".equals(cat)) {
            rv.setTextViewText(R.id.widget_stack_category, cat);
            rv.setViewVisibility(R.id.widget_stack_category, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.widget_stack_category, View.GONE);
        }

        // Alarm icon
        boolean hasAlarm = false;
        try {
            GlobalShift gs = globalShiftDao.getShiftByDateAndStart(shift.getDate(), shift.getStartTime());
            hasAlarm = gs != null && gs.isHasAlarm();
        } catch (Exception ignored) {}
        rv.setViewVisibility(R.id.widget_stack_alarm, hasAlarm ? View.VISIBLE : View.GONE);

        // Ekipa (bez własnego imienia)
        try {
            List<GlobalShift> crew = userName.isEmpty()
                    ? globalShiftDao.getShiftsByDate(shift.getDate())
                    : globalShiftDao.getCrewByDateExcluding(shift.getDate(), userName);
            List<GlobalShift> overlapping = ShiftUtils.getOverlappingShifts(
                    shift.getStartTime(), shift.getEndTime(), crew);
            if (overlapping != null && !overlapping.isEmpty()) {
                rv.setTextViewText(R.id.widget_stack_crew, ShiftUtils.formatWidgetCrew(overlapping));
                rv.setViewVisibility(R.id.widget_stack_crew, View.VISIBLE);
            } else {
                rv.setViewVisibility(R.id.widget_stack_crew, View.GONE);
            }
        } catch (Exception ignored) {
            rv.setViewVisibility(R.id.widget_stack_crew, View.GONE);
        }

        // Fill-in Intent — po kliknięciu w kartę otwiera konkretną zmianę w apce
        Intent fillIn = new Intent();
        fillIn.putExtra(ShiftWidgetProvider.EXTRA_OPEN_SHIFT_DATE, shift.getDate());
        fillIn.putExtra(ShiftWidgetProvider.EXTRA_OPEN_SHIFT_ID, shift.getId()); // P1 fix: precyzyjne ID
        rv.setOnClickFillInIntent(R.id.widget_stack_item_root, fillIn);

        return rv;
    }

    @Override
    public RemoteViews getLoadingView() {
        return new RemoteViews(context.getPackageName(), R.layout.widget_stack_item);
    }

    @Override
    public int getViewTypeCount() { return 1; }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= items.size()) return position;
        Shift s = items.get(position);
        // P1 fix: stabilne ID oparte o datę+godzinę, nie pozycję na liście
        String key = (s.getDate() != null ? s.getDate() : "") + "|"
                   + (s.getStartTime() != null ? s.getStartTime() : "");
        return key.hashCode();
    }

    @Override
    public boolean hasStableIds() { return true; }

    @Override
    public void onDestroy() { items.clear(); }

    // ─── Formatowanie ────────────────────────────────────────────────────

    private String formatDate(String isoDate) {
        if (isoDate == null) return "—";
        try {
            LocalDate date = LocalDate.parse(isoDate);
            LocalDate today = LocalDate.now();
            if (date.equals(today)) return "Dziś";
            if (date.equals(today.plusDays(1))) return "Jutro";
            String fmt = date.format(DISPLAY_DATE);
            return fmt.substring(0, 1).toUpperCase(new Locale("pl")) + fmt.substring(1);
        } catch (Exception e) {
            return isoDate;
        }
    }
}
