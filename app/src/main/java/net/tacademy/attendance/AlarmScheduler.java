package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.time.*;

public final class AlarmScheduler {
    static final String EXPECTED_AT="attendance_expected_alarm_ms";
    private static PendingIntent operation(Context context,long expectedAt) {
        // Keep the legacy identity so updating replaces the old fixed 17:50 alarm.
        return PendingIntent.getBroadcast(context, 1750, new Intent(context, AlarmReceiver.class).putExtra(EXPECTED_AT,expectedAt),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static boolean allowed(Context context) {
        return Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }
    public static void cancel(Context context) {
        PendingIntent existing=PendingIntent.getBroadcast(context,1750,new Intent(context,AlarmReceiver.class),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
        if(existing!=null)context.getSystemService(AlarmManager.class).cancel(existing);
        new Prefs(context).raw.edit().remove("next_alarm").apply();
    }
    public static boolean arm(Context context, boolean recover) {
        Prefs prefs = new Prefs(context);
        long previous=prefs.raw.getLong("next_alarm",0);
        cancel(context);
        if (prefs.passiveRecords()) return false;
        if(!allowed(context)){DiagnosticLog.add(context,DiagnosticLog.Event.ALARM_FAILED,prefs.phase(ZonedDateTime.now(Policy.ZONE)),"EXACT_ALARM_DENIED");return false;}
        ZonedDateTime now = ZonedDateTime.now(Policy.ZONE);
        ZonedDateTime next = Policy.nextWake(now, prefs.days(), prefs.automationExcluded(), prefs.start(), prefs.end(),
            prefs.checkout(), prefs.retryMinutes(), prefs.checkedInToday(), prefs.checkedOutToday());
        if (recover && prefs.monitoringNeeded(now)) next = now.plusSeconds(2);
        if (next == null) return false;
        try {
            context.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), operation(context,next.toInstant().toEpochMilli()));
            long expected=next.toInstant().toEpochMilli();
            prefs.raw.edit().putLong("next_alarm", expected).apply();
            if(previous!=expected)DiagnosticLog.add(context,DiagnosticLog.Event.ALARM_ARMED,prefs.phase(next),recover?"RECOVERY":"SCHEDULE_BOUNDARY",DiagnosticLog.alarmDetails(expected));
            return true;
        } catch (SecurityException e) { DiagnosticLog.add(context,DiagnosticLog.Event.ALARM_FAILED,prefs.phase(now),"SECURITY_DENIED");return false; }
    }
}
