package net.tacademy.attendance;

import android.content.*;
import java.time.*;
import java.util.*;
import org.json.*;

/** Bounded local events containing fixed app codes and timestamps, never page text or credentials. */
final class DiagnosticLog {
    static final int LIMIT=200;
    enum Event { MONITOR_STARTED, MONITOR_STOPPED, WAITING, ACCESS_BLOCKED, CHROME_OPEN_REQUEST, CHROME_OPEN_DISPATCHED,
        CHROME_OPEN_FAILED, BUTTON_REQUEST, BUTTON_FAILED, CONFIRM_REQUEST, RECORD_CONFIRMED, AUTOMATIC_PAUSED, NETWORK_DIAGNOSTIC_LOCAL }
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("attendance_diagnostics",Context.MODE_PRIVATE);}
    static synchronized void add(Context c,Event event,Policy.Phase phase,String reason){
        try{
            long now=System.currentTimeMillis();SharedPreferences p=prefs(c);
            String signature=LocalDate.now(Policy.ZONE)+":"+event+":"+phase+":"+reason;
            if(signature.equals(p.getString("last_signature","")))return;
            JSONArray old=records(c),next=new JSONArray();
            for(int i=Math.max(0,old.length()-LIMIT+1);i<old.length();i++)next.put(old.get(i));
            next.put(new JSONObject().put("record_type","attendance_event").put("test_id","event:"+UUID.randomUUID())
                .put("occurred_at_ms",now).put("event_code",event.name()).put("phase",phase.name()).put("reason_code",reason)
                .put("app_version",BuildConfig.VERSION_NAME));
            p.edit().putString("events",next.toString()).putString("last_signature",signature).apply();
        }catch(JSONException ignored){}
    }
    static synchronized JSONArray records(Context c){
        JSONArray result=new JSONArray();long cutoff=System.currentTimeMillis()-7L*24*60*60*1000;
        try{JSONArray old=new JSONArray(prefs(c).getString("events","[]"));for(int i=0;i<old.length();i++){
            JSONObject item=old.optJSONObject(i);if(item!=null&&item.optLong("occurred_at_ms",0)>=cutoff)result.put(item);
        }}catch(JSONException ignored){}return result;
    }
}
