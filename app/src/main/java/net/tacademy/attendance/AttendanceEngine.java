package net.tacademy.attendance;

import android.content.Context;
import android.os.*;
import java.time.ZonedDateTime;

/** Uses the user's existing Chrome UI and session. No native attendance HTTP requests. */
public final class AttendanceEngine {
    private static long nextRead;
    private static String readWindow = "";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private AttendanceEngine() {}
    public static void request(Context context, boolean force, Runnable finished) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> request(context, force, finished)); return;
        }
        Prefs prefs = new Prefs(context);
        if(prefs.passiveRecords()){if(finished!=null)finished.run();return;}
        if(ActivationRun.pending()||ScheduledUnlock.active(context)){if(finished!=null)finished.run();return;}
        ZonedDateTime now = ZonedDateTime.now(Policy.ZONE);
        Policy.Phase phase = prefs.phase(now);
        if (phase == Policy.Phase.IDLE || ChromeAttendanceService.busy() || ChromeAttendanceService.otherUiBusy()) {
            if (finished != null) finished.run(); return;
        }
        String window = now.toLocalDate() + ":" + phase;
        if (!window.equals(readWindow)) { readWindow = window; nextRead = 0; }
        NetworkAccess.State wifi = NetworkAccess.inspect(context);
        if (!wifi.allowed()) {
            DiagnosticLog.add(context,DiagnosticLog.Event.WAITING,phase,wifi.code());
            prefs.status("Wi-Fi 연결 대기", wifi.reason());
            if (finished != null) finished.run(); return;
        }
        // An unavailable Wi-Fi is not an attendance attempt. In particular, never make
        // the first eligible connection wait for the previous disconnected check.
        if (!force && SystemClock.elapsedRealtime() < nextRead) {
            if (finished != null) finished.run(); return;
        }
        nextRead = SystemClock.elapsedRealtime() + 60000;
        DiagnosticLog.add(context, DiagnosticLog.Event.AUTOMATIC_TRIGGER, phase, "WIFI_READY");
        // TrafficGuard still admits every actual navigation, including forced wake handoffs.
        ChromeAttendanceService.begin(context, finished);
    }
}
