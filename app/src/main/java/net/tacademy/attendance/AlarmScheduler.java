package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.time.*;

public final class AlarmScheduler {
    private static PendingIntent operation(Context context) {
        // Keep the legacy identity so updating replaces the old fixed 17:50 alarm.
        return PendingIntent.getBroadcast(context, 1750, new Intent(context, AlarmReceiver.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static boolean allowed(Context context) {
        return Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }
    public static void cancel(Context context) {
        context.getSystemService(AlarmManager.class).cancel(operation(context));
        new Prefs(context).raw.edit().remove("next_alarm").apply();
    }
    public static boolean arm(Context context, boolean recover) {
        Prefs prefs = new Prefs(context);
        cancel(context);
        if (!prefs.enabled() || !allowed(context)) return false;
        ZonedDateTime now = ZonedDateTime.now(Policy.ZONE);
        ZonedDateTime next = Policy.nextWake(now, prefs.days(), prefs.excluded(), prefs.start(), prefs.end(),
            prefs.checkout(), prefs.retryMinutes(), prefs.checkedInToday(), prefs.checkedOutToday());
        if (recover && prefs.phase(now) != Policy.Phase.IDLE) next = now.plusSeconds(2);
        if (next == null) return false;
        try {
            context.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), operation(context));
            prefs.raw.edit().putLong("next_alarm", next.toInstant().toEpochMilli()).apply();
            return true;
        } catch (SecurityException e) { return false; }
    }
}
