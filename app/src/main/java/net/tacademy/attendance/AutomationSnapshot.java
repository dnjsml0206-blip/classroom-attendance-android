package net.tacademy.attendance;

import android.content.Context;
import java.time.*;
import org.json.*;

final class AutomationSnapshot {
    static JSONObject read(Context c){
        JSONObject result=new JSONObject();Prefs p=new Prefs(c);
        try{
            result.put("entry_start",p.start()).put("entry_end",p.end()).put("checkout_start",Policy.checkoutStartTime(p.checkout()).toString())
                .put("checkout_retry_minutes",p.retryMinutes()).put("class_day",Policy.isClassDay(ZonedDateTime.now(Policy.ZONE),p.days(),p.excluded()))
                .put("entry_confirmed_today",p.checkedInToday()).put("checkout_confirmed_today",p.checkedOutToday())
                .put("monitor_running",MonitorService.running).put("monitor_seen_at_ms",p.raw.getLong("monitor_seen",0)).put("next_alarm_at_ms",p.raw.getLong("next_alarm",0))
                .put("scheduled_lock_mode",ScheduledUnlock.mode(c).name()).put("unlock_active",ScheduledUnlock.active(c))
                .put("unlock_requested_at_ms",ScheduledUnlock.store(c).getLong("requested_at_ms",0)).put("unlock_result",ScheduledUnlock.store(c).getString("result","NONE"));
            PendingClick pending=PendingClick.parse(p.raw.getString("chrome_pending",""));
            if(pending!=null)result.put("pending_click",new JSONObject().put("day",pending.day()).put("phase",pending.phase().name()).put("prepared_at_ms",p.raw.getLong("chrome_pending_at",0)));
            else result.put("pending_click",JSONObject.NULL);
        }catch(JSONException|RuntimeException ignored){}return result;
    }
}
