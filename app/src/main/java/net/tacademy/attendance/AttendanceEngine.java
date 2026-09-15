package net.tacademy.attendance;

import android.content.Context;
import android.os.*;
import java.time.ZonedDateTime;

/** Uses the user's existing Chrome UI and session. No native attendance HTTP requests. */
public final class AttendanceEngine {
    private static long nextRead;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private AttendanceEngine() {}
    public static void request(Context context, boolean force, Runnable finished) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> request(context, force, finished)); return;
        }
        Prefs prefs = new Prefs(context);
        if(ScheduledUnlock.active(context)){if(finished!=null)finished.run();return;}
        if (prefs.phase(ZonedDateTime.now(Policy.ZONE)) == Policy.Phase.IDLE ||
                ChromeAttendanceService.busy() || (!force && SystemClock.elapsedRealtime() < nextRead)) {
            if (finished != null) finished.run(); return;
        }
        nextRead = SystemClock.elapsedRealtime() + 60000;
        NetworkAccess.State wifi = NetworkAccess.inspect(context);
        if (!wifi.allowed()) {
            DiagnosticLog.add(context,DiagnosticLog.Event.WAITING,prefs.phase(ZonedDateTime.now(Policy.ZONE)),wifi.code());
            prefs.status("Wi-Fi 연결 대기", wifi.reason());
            if (finished != null) finished.run(); return;
        }
        ChromeAttendanceService.begin(context, finished);
    }
}
