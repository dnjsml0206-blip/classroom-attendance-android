package net.tacademy.attendance;
import android.content.*;

public final class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) &&
            !Intent.ACTION_TIME_CHANGED.equals(action) && !Intent.ACTION_TIMEZONE_CHANGED.equals(action) &&
            !"android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)) return;
        Prefs prefs = new Prefs(context);
        if (!prefs.enabled()) return;
        AlarmScheduler.arm(context, true);
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))
            Notices.alert(context, "출석 감시를 다시 시작해 주세요", "퇴실 예약을 복구했습니다. 앱을 열어 위치·Wi-Fi 감시를 시작해 주세요");
    }
}
