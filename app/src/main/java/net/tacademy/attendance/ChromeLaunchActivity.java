package net.tacademy.attendance;
import android.app.Activity;
import android.os.Bundle;
import java.time.ZonedDateTime;
@android.annotation.SuppressLint("CustomSplashScreen") // Notification click entry point; this activity immediately opens Chrome.
public final class ChromeLaunchActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (new Prefs(this).phase(ZonedDateTime.now(Policy.ZONE)) != Policy.Phase.IDLE && ChromeAttendanceService.available()) {
            ScheduleController.reconcile(this);
            AttendanceEngine.request(this, true, null);
        } else ChromeAttendanceService.openChrome(this, SiteProtocol.PAGE);
        finish();
    }
}
