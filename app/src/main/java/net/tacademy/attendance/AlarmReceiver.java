package net.tacademy.attendance;
import android.content.*;
import java.time.ZonedDateTime;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Prefs prefs = new Prefs(context);
        AlarmScheduler.arm(context, false);
        ZonedDateTime now = ZonedDateTime.now(Policy.ZONE);
        if (!prefs.enabled() || !Policy.isClassDay(now, prefs.days(), prefs.excluded()) || !Policy.checkoutDue(now)) return;
        if (!AlarmScheduler.allowed(context)) {
            Notices.alert(context, "퇴실 확인이 필요합니다", "정확한 알람 권한을 다시 허용해 주세요"); return;
        }
        try { context.startForegroundService(new Intent(context, CheckoutService.class)); }
        catch (RuntimeException e) { Notices.alert(context, "퇴실 확인이 필요합니다", "앱을 열어 감시를 다시 시작해 주세요"); }
    }
}
