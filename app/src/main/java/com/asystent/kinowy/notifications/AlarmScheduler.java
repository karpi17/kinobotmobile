package com.asystent.kinowy.notifications;

import android.content.Context;
import com.asystent.kinowy.models.Shift;
import java.util.List;

/**
 * @deprecated Używaj {@link NotificationScheduler} zamiast tej klasy.
 *             Klasa pozostawiona dla kompatybilności wstecznej — deleguje do NotificationScheduler.
 */
@Deprecated
public class AlarmScheduler {

    /** @deprecated Użyj {@link NotificationScheduler#scheduleAlarms(Context, List)} */
    @Deprecated
    public static void scheduleAlarms(Context context, List<Shift> shifts) {
        NotificationScheduler.scheduleAlarms(context, shifts);
    }

    /** @deprecated Użyj {@link NotificationScheduler#rescheduleForShift(Context, Shift)} */
    @Deprecated
    public static void rescheduleForShift(Context context, Shift shift) {
        NotificationScheduler.rescheduleForShift(context, shift);
    }

    /** @deprecated Użyj {@link NotificationScheduler#cancelAlarmsForShift(Context, Shift)} */
    @Deprecated
    public static void cancelAlarmsForShift(Context context, Shift shift) {
        NotificationScheduler.cancelAlarmsForShift(context, shift);
    }
}