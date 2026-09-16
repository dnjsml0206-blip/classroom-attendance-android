package net.tacademy.attendance;

import android.content.*;
import java.time.*;
import java.util.*;
import org.json.*;

/** Bounded local events containing fixed app codes and timestamps, never page text or credentials. */
@android.annotation.SuppressLint("ApplySharedPref") // Preserve click intention and UI return evidence before the next operation.
final class DiagnosticLog {
    static final int LIMIT=200, DETAIL_BYTES=120*1024;
    enum Event { MONITOR_STARTED, MONITOR_STOPPED, WAITING, ACCESS_BLOCKED, CHROME_OPEN_REQUEST, CHROME_OPEN_DISPATCHED,
        CHROME_OPEN_FAILED, BUTTON_REQUEST, BUTTON_FAILED, CONFIRM_REQUEST, RECORD_CONFIRMED, AUTOMATIC_PAUSED, NETWORK_DIAGNOSTIC_LOCAL,
        ALARM_ARMED, ALARM_FIRED, ALARM_FAILED, MONITOR_START_REQUEST, MONITOR_START_RECEIVED, MONITOR_START_FAILED,
        CHROME_STAGE, CLICK_PLANNED, WAKE_REQUESTED, WAKE_VISIBLE, WAKE_BLOCKED, WAKE_AUTH_REQUIRED,
        ACTIVATION_QUEUED, ACTIVATION_FIRED, WAKE_DISMISS_REQUEST, WAKE_DISMISS_RESULT, WAKE_FINISHED, ACCESSIBILITY_CONNECTED, RESULT_WAIT_STARTED, MANUAL_REQUEST, MANUAL_BLOCKED, MANUAL_FINISHED, PAGE_RECOVERY_WAIT, PRECLICK_DEFERRED, CHROME_DIAGNOSTIC, ATTEMPT_FAILED, ATTEMPT_CANCELLED, DIALOG_STATE_CHANGED, SCREEN_PRESENTATION, SCREEN_OFF_REQUEST, SCREEN_OFF_RESULT }
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("attendance_diagnostics",Context.MODE_PRIVATE);}
    static synchronized void add(Context c,Event event,Policy.Phase phase,String reason){
        add(c,event,phase,reason,new JSONObject());
    }
    static synchronized void add(Context c,Event event,Policy.Phase phase,String reason,JSONObject details){
        try{
            JSONObject safe=new JSONObject();
            for(String key:new String[]{"attempt_id","scheduled_at_ms","delay_ms","window_state","diagnostic_version","diagnosis","stage","focused_owner",
                "screen_interactive","keyguard_locked","device_locked","chrome_window_present","window_count","read_state","read_error","read_operation",
                "address_state","page_signal","button_state","node_count","visible_node_count","web_node_count","tree_limit_reached","wifi_check_code",
                "click_attempted","click_accepted","confirmation_attempted","pending_click","elapsed_ms","poll_count","chrome_event_age_ms",
                "last_site_signal","last_site_address","last_site_age_ms","page_visible_ms","dialog_kind","confirm_button_state","confirm_target",
                "confirmation_disappeared","confirmation_age_ms","confirmation_visible_ms","processing_page_visible"})if(details.has(key))safe.put(key,details.get(key));
            long now=System.currentTimeMillis();SharedPreferences p=prefs(c);
            String signature=LocalDate.now(Policy.ZONE)+":"+event+":"+phase+":"+reason+":"+safe;
            if(signature.equals(p.getString("last_signature","")))return;
            JSONArray old=records(c),next=new JSONArray();
            for(int i=Math.max(0,old.length()-LIMIT+1);i<old.length();i++)next.put(old.get(i));
            next.put(new JSONObject().put("record_type","attendance_event").put("test_id","event:"+UUID.randomUUID())
                .put("occurred_at_ms",now).put("event_code",event.name()).put("phase",phase.name()).put("reason_code",reason)
                .put("app_version",BuildConfig.VERSION_NAME).put("details",safe));
            JSONArray bounded=new JSONArray();int bytes=0,start=next.length();
            while(start>0){int size=next.getJSONObject(start-1).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8).length+16;if(bytes+size>DETAIL_BYTES)break;bytes+=size;start--;}
            for(int i=start;i<next.length();i++)bounded.put(next.get(i));
            SharedPreferences.Editor edit=p.edit().putString("events",bounded.toString()).putString("last_signature",signature);
            if(event==Event.ATTEMPT_FAILED||event==Event.CLICK_PLANNED||event==Event.BUTTON_REQUEST||event==Event.BUTTON_FAILED||event==Event.CONFIRM_REQUEST)edit.commit();
            else edit.apply();
        }catch(JSONException ignored){}
    }
    static JSONObject alarmDetails(long scheduledAt){
        JSONObject d=new JSONObject();try{if(scheduledAt>0)d.put("scheduled_at_ms",scheduledAt);}catch(JSONException ignored){}return d;
    }
    static String failureCode(Throwable e){
        if(e instanceof SecurityException)return "SECURITY_DENIED";
        if(e instanceof android.content.ActivityNotFoundException)return "ACTIVITY_UNAVAILABLE";
        if(e instanceof IllegalStateException)return "START_NOT_ALLOWED";
        return "RUNTIME_FAILURE";
    }
    static synchronized JSONArray records(Context c){
        JSONArray result=new JSONArray();long cutoff=System.currentTimeMillis()-7L*24*60*60*1000;
        try{JSONArray old=new JSONArray(prefs(c).getString("events","[]"));for(int i=0;i<old.length();i++){
            JSONObject item=old.optJSONObject(i);if(item!=null&&item.optLong("occurred_at_ms",0)>=cutoff)result.put(item);
        }}catch(JSONException ignored){}return result;
    }
}
