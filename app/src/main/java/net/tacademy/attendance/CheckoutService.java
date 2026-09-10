package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;

public final class CheckoutService extends Service {
    private PowerManager.WakeLock wakeLock;
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 34) startForeground(Notices.CHECKOUT, Notices.checking(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        else startForeground(Notices.CHECKOUT, Notices.checking(this));
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "attendance:checkout");
            wakeLock.acquire(90000);
        }
        AttendanceEngine.request(this, true, () -> {
            Prefs prefs = new Prefs(this);
            if (prefs.enabled()) Notices.alert(this, prefs.raw.getString("status", "퇴실 확인이 필요합니다"),
                prefs.raw.getString("detail", "앱에서 상태를 확인해 주세요"));
            stopSelf(startId);
        });
        return START_NOT_STICKY;
    }
    @Override public void onTimeout(int startId) { stopSelf(startId); }
    @Override public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}
