package net.tacademy.attendance;
import android.content.*;
public final class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) &&
            !Intent.ACTION_TIME_CHANGED.equals(action) && !Intent.ACTION_TIMEZONE_CHANGED.equals(action) &&
            !"android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)) return;
        // Restore an alarm only. Location monitoring starts at the next eligible alarm.
        if (new Prefs(context).enabled()) AlarmScheduler.arm(context, true);
    }
}
