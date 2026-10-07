package net.tacademy.attendance;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.ZonedDateTime;

/** enabled means future reservations are enabled, not that a service is running. */
public final class ScheduleController {
    private ScheduleController() {}
    public static boolean backgroundLocationAllowed(Context context) {
        return Build.VERSION.SDK_INT < 29 ||
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    public static void reconcile(Context context) {
        Prefs prefs = new Prefs(context);
        ShortDayReminder.reconcile(context);
        AlarmScheduler.arm(context, false);
        if(ActivationRun.pending()&&prefs.enabled())return;
        if (!prefs.enabled() || prefs.checkedOutToday()) Notices.clearDeparture(context,java.time.LocalDate.now(Policy.ZONE).toString());
        if (!prefs.monitoringNeeded(ZonedDateTime.now(Policy.ZONE))) {
            ScheduledUnlock.cancel(context,"RESERVATION_IDLE");
            ChromeAttendanceService.cancelScheduledWork();
            context.stopService(new Intent(context, MonitorService.class));
            if (!prefs.enabled() || prefs.checkedOutToday()) Notices.checkoutAttention(context, false, "");
            Notices.chromeAction(context, false, "");
            return;
        }
        if(!prefs.auto())ScheduledUnlock.cancel(context,"AUTO_CLICK_DISABLED");
        try {
            if(!MonitorService.running)DiagnosticLog.add(context,DiagnosticLog.Event.MONITOR_START_REQUEST,prefs.phase(ZonedDateTime.now(Policy.ZONE)),"START_FOREGROUND_SERVICE");
            context.startForegroundService(new Intent(context, MonitorService.class));
        }
        catch (RuntimeException e) {
            DiagnosticLog.add(context,DiagnosticLog.Event.MONITOR_START_FAILED,prefs.phase(ZonedDateTime.now(Policy.ZONE)),DiagnosticLog.failureCode(e));
            prefs.status("예약 실행 권한 확인 필요", "자동출석 탭에서 위치 항상 허용과 알람 권한을 확인해 주세요");
            if (prefs.phase(ZonedDateTime.now(Policy.ZONE)) != Policy.Phase.ENTRY)
                Notices.checkoutAttention(context, true, "자동 실행 권한을 확인한 뒤 앱에서 다시 확인해 주세요");
            else Notices.alert(context, "입실 감시를 시작하지 못했습니다", "앱을 열어 위치 항상 허용과 알람 권한을 확인해 주세요");
        }
    }
}
