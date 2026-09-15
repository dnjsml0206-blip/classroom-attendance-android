package net.tacademy.attendance;
import android.content.*;
import java.time.ZonedDateTime;
public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        Prefs p=new Prefs(context);long expected=intent!=null&&intent.hasExtra(AlarmScheduler.EXPECTED_AT)?intent.getLongExtra(AlarmScheduler.EXPECTED_AT,0):p.raw.getLong("next_alarm",0);
        org.json.JSONObject details=DiagnosticLog.alarmDetails(expected);
        try{if(expected>0)details.put("delay_ms",System.currentTimeMillis()-expected);}catch(org.json.JSONException ignored){}
        DiagnosticLog.add(context,DiagnosticLog.Event.ALARM_FIRED,p.phase(ZonedDateTime.now(Policy.ZONE)),expected>0?"RECEIVED":"EXPECTED_TIME_UNKNOWN",details);
        LockLab.fireCheckoutIfDue(context);ScheduleController.reconcile(context);
    }
}
