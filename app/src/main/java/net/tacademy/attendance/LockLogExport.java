package net.tacademy.attendance;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.json.*;

/** Explicit allow-list export. No Chrome data, account, device ID, coordinates or Wi-Fi names. */
final class LockLogExport {
    static JSONObject environment(Context c){
        JSONObject out=new JSONObject();
        try{
            out.put("app_version",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);
            try{out.put("chrome_version",c.getPackageManager().getPackageInfo("com.android.chrome",0).versionName);}catch(PackageManager.NameNotFoundException ignored){}
            out.put("device",LockLab.device()).put("emulator",Build.HARDWARE.equals("ranchu")||Build.HARDWARE.equals("goldfish"));
            out.put("accessibility_enabled",ChromeAttendanceService.configured(c)).put("accessibility_connected",ChromeAttendanceService.available()).put("exact_alarm_allowed",AlarmScheduler.allowed(c));
            out.put("notifications_enabled",c.getSystemService(NotificationManager.class).areNotificationsEnabled());
            out.put("location_permission",c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED);
            out.put("background_location_permission",ScheduleController.backgroundLocationAllowed(c));
            out.put("location_enabled",Build.VERSION.SDK_INT<28||c.getSystemService(android.location.LocationManager.class).isLocationEnabled());
            KeyguardManager keyguard=c.getSystemService(KeyguardManager.class);
            out.put("screen_interactive",c.getSystemService(PowerManager.class).isInteractive()).put("keyguard_locked",keyguard.isKeyguardLocked()).put("device_locked",keyguard.isDeviceLocked());
            out.put("attendance_notifications_enabled",Notices.attendanceNotificationsAllowed(c));
            out.put("battery_optimization_exempt",c.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(c.getPackageName()));
            NetworkAccess.State wifi=NetworkAccess.inspect(c);out.put("registered_wifi_connected",wifi.allowed()).put("wifi_check",wifi.reason()).put("wifi_check_code",wifi.code()).put("wifi_transport_present",wifi.wifiTransportPresent()).put("ssid_visible",wifi.ssidVisible());
            Prefs p=new Prefs(c);out.put("schedule_enabled",p.enabled()).put("auto_click",p.auto()).put("attendance_phase",p.phase(ZonedDateTime.now(Policy.ZONE)).name());
            out.put("screen_off_after_attendance",p.screenOffAfterAttendance()).put("show_processing_screen",p.showProcessingScreen());
            out.put("automation",AutomationSnapshot.read(c));
        }catch(Exception ignored){}return out;
    }
    static JSONObject current(Context c)throws JSONException{
        Map<String,?> p=LockLab.prefs(c).getAll();JSONObject out=new JSONObject();
        String mode=String.valueOf(p.get("mode"));LockTestPolicy.Mode m;
        try{m=LockTestPolicy.Mode.valueOf(mode);}catch(Exception e){m=LockTestPolicy.Mode.WAKE;}
        out.put("test_id",p.get("id")).put("mode",m.name()).put("mode_label",m.label);
        out.put("created_at_ms",p.get("created")).put("due_at_ms",p.get("due"));
        out.put("active",Boolean.TRUE.equals(p.get("active"))).put("actual_attendance_requested",Boolean.TRUE.equals(p.get("actual")));
        out.put("stage",p.get("stage")).put("user_verdict",p.get("verdict")).put("user_note",p.get("note"));
        out.put("events",p.get("events")).put("user_interaction",p.getOrDefault("user_interaction",null));
        for(String key:new String[]{"purpose","machine_result","click_requested_at_ms","click_accepted","click_confirmed_at_ms","fired_at_ms","entry_at_ms","entry_delay_ms","entry_source","checkout_trial","target_phase"})out.put(key,p.containsKey(key)?p.get(key):JSONObject.NULL);
        for(String key:new String[]{"entry_environment","handoff_environment"}){String value=(String)p.get(key);out.put(key,value==null?JSONObject.NULL:new JSONObject(value));}
        String env=(String)p.get("environment");out.put("environment",env==null?JSONObject.NULL:new JSONObject(env));
        String execution=(String)p.get("execution_environment");out.put("execution_environment",execution==null?JSONObject.NULL:new JSONObject(execution));
        out.put("legacy",env==null);return out;
    }
    static String field(String report,String name){for(String s:report.split("\n"))if(s.startsWith(name+": "))return s.substring(name.length()+2);return "";}
    static JSONObject legacy(String report)throws JSONException{
        String label=field(report,"방식"),mode="UNKNOWN";for(LockTestPolicy.Mode m:LockTestPolicy.Mode.values())if(m.label.equals(label))mode=m.name();
        int start=report.lastIndexOf("\n\n");String events=start<0?"":report.substring(start+2);
        JSONObject out=new JSONObject().put("legacy",true).put("test_id",JSONObject.NULL).put("created_at_ms",JSONObject.NULL).put("due_at_ms",JSONObject.NULL);
        out.put("mode",mode).put("mode_label",label).put("user_verdict",field(report,"사용자 확인")).put("stage",field(report,"진행 결과"));
        out.put("actual_attendance_requested",field(report,"실제 출결 시도").equals("켜짐"));
        out.put("events",events).put("legacy_report",report).put("active",false);return out;
    }

    static JSONArray records(Context c)throws JSONException{
        synchronized(LockLab.class){
            JSONArray result=new JSONArray();if(!LockLab.id(c).isEmpty())result.put(sanitize(current(c)));
            JSONArray old=new JSONArray(LockLab.prefs(c).getString("history","[]"));
            JSONArray details=new JSONArray(LockLab.prefs(c).getString("history_details","[]"));
            for(int i=0;i<Math.min(old.length(),10);i++){JSONObject item=details.optJSONObject(i);result.put(sanitize(item==null?legacy(old.getString(i)):item));}
            JSONArray events=DiagnosticLog.records(c);for(int i=0;i<events.length();i++)result.put(events.get(i));
            return result;
        }
    }
    static JSONObject sanitize(JSONObject original)throws JSONException{
        // Traverse values instead of applying regex to encoded JSON; preserve valid escaping.
        JSONObject out=cleanObject(original);LogEvidence e=LogEvidence.read(out.optString("events"));
        out.put("evidence",new JSONObject().put("screen_off_at_run",e.screenOffAtRun).put("automatic_entry",false).put("scheduled_entry",e.scheduledEntry).put("automatic_unlock_confirmed",false)
            .put("manual_notification_entry",e.manualEntry).put("immediate_entry",e.immediateEntry).put("chrome_address_observed",e.chromeObserved)
            .put("credential_needed_at_run",e.credentialNeeded).put("dismiss_requested",e.dismissRequested).put("dismiss_succeeded",e.dismissSucceeded)
            .put("attendance_record_confirmed",e.attendanceConfirmed).put("cancelled",e.cancelled).put("https_status",e.httpsStatus==null?JSONObject.NULL:e.httpsStatus));
        return out;
    }
    private static JSONObject cleanObject(JSONObject in)throws JSONException{JSONObject out=new JSONObject();Iterator<String> keys=in.keys();while(keys.hasNext()){String k=keys.next();Object v=in.get(k);out.put(k,v instanceof String?LogEvidence.redact((String)v):v instanceof JSONObject?cleanObject((JSONObject)v):v);}return out;}
    static String summary(JSONArray records){
        int eventCount=0;for(int i=0;i<records.length();i++)if("attendance_event".equals(records.optJSONObject(i).optString("record_type")))eventCount++;
        StringBuilder out=new StringBuilder("일반 출결 동작 · "+eventCount+"건\n");for(LockTestPolicy.Mode m:LockTestPolicy.Mode.values()){
            int count=0,good=0,partial=0,bad=0;for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);if(r!=null&&m.name().equals(r.optString("mode"))){count++;switch(r.optString("user_verdict")){case "성공":good++;break;case "부분 성공":partial++;break;case "실패":bad++;break;}}}
            out.append(m.label).append(" · ").append(count==0?"기록 없음":count+"회 / 성공 "+good+" · 부분 "+partial+" · 실패 "+bad+" · 미확인 "+(count-good-partial-bad)).append('\n');
        }return out.toString().trim();
    }
    static String digest(String value){try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    static JSONObject document(Context c,JSONArray records,String id)throws JSONException{
        return new JSONObject().put("schema_version",2).put("report_id",id).put("exported_at",Instant.now().toString()).put("timezone","Asia/Seoul")
            .put("export_environment",environment(c)).put("records",records)
            .put("analysis_note","attendance_event는 앱 동작 기록이며 Chrome 실제 HTTP 요청 건수가 아닙니다. 과거 버전의 일반 출결 동작은 복원할 수 없습니다. 사용자 판정과 앱 관찰을 구분하세요. 예약 경로의 화면 진입은 자동 잠금 해제 증거가 아닙니다. user_interaction과 entry_delay_ms를 함께 확인하세요. Chrome 주소 확인은 출결 성공이 아니며, 서버 응답은 인증 성공이 아닙니다. 과거 legacy 기록의 날짜와 기기 설정은 알 수 없습니다. 메모는 사용자 입력입니다.");
    }
}
