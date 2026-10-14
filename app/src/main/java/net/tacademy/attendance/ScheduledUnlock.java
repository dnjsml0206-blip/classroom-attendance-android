package net.tacademy.attendance;

import android.content.*;
import android.os.*;
import android.provider.Settings;
import java.time.*;
import java.util.UUID;
import org.json.*;

/** Normal reservations own this session independently of creator-only lock trials. */
@android.annotation.SuppressLint("ApplySharedPref")
final class ScheduledUnlock {
    static final String TOKEN="scheduled_unlock_token";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    static SharedPreferences store(Context c){return c.getSharedPreferences("scheduled_unlock",Context.MODE_PRIVATE);}
    static UnlockPolicy.Mode mode(Context c){return UnlockPolicy.automaticMode(new Prefs(c).useExtendUnlock());}
    private static int boot(Context c){return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
    static String token(Context c){return store(c).getString("token","");}
    static Policy.Phase phase(Context c){try{return Policy.Phase.valueOf(store(c).getString("phase","IDLE"));}catch(RuntimeException e){return Policy.Phase.IDLE;}}
    static boolean live(Context c,String token){
        SharedPreferences s=store(c);Prefs p=new Prefs(c);
        return UnlockPolicy.live(token,s.getString("token",""),s.getBoolean("active",false),boot(c),s.getInt("boot",-2),SystemClock.elapsedRealtime(),s.getLong("started_elapsed",0))
            &&LocalDate.now(Policy.ZONE).toString().equals(s.getString("day",""))&&p.auto()
            &&phase(c)!=Policy.Phase.IDLE&&p.phase(ZonedDateTime.now(Policy.ZONE))==phase(c)
            &&mode(c).name().equals(s.getString("mode",""))&&!LockLab.readOnlyActive(c);
    }
    static boolean active(Context c){return live(c,token(c));}
    static long visibleAge(Context c){
        long entered=store(c).getLong("entered_elapsed",0);
        return entered>0?Math.max(0,SystemClock.elapsedRealtime()-entered):-1;
    }
    static JSONObject details(Context c){
        JSONObject d=new JSONObject();try{d.put("attempt_id",token(c)).put("scheduled_lock_mode",mode(c).name());long entered=store(c).getLong("entered_elapsed",0);
            if(entered>0)d.put("delay_ms",Math.max(0,entered-store(c).getLong("started_elapsed",entered)));
            android.app.KeyguardManager keyguard=c.getSystemService(android.app.KeyguardManager.class);
            d.put("elapsed_ms",visibleAge(c)).put("screen_interactive",c.getSystemService(PowerManager.class).isInteractive())
                .put("keyguard_locked",keyguard.isKeyguardLocked()).put("device_locked",keyguard.isDeviceLocked())
                .put("device_secure",keyguard.isDeviceSecure());
        }catch(JSONException ignored){}return d;
    }
    static void request(Context context,Policy.Phase phase){
        Context c=context.getApplicationContext();Prefs p=new Prefs(c);
        if(p.passiveRecords())return;
        if(active(c))return;
        if(store(c).getBoolean("active",false))stop(c,token(c),"WAKE_EXPIRED",false);
        if(!p.auto()||phase==Policy.Phase.IDLE)return;
        if(p.phase(ZonedDateTime.now(Policy.ZONE))!=phase||LockLab.readOnlyActive(c)||!NetworkAccess.inspect(c).allowed())return;
        AccessPolicy.Decision access=TrafficGuard.peek(c,AccessPolicy.Kind.AUTOMATIC,phase);
        if(!access.allowed()){DiagnosticLog.add(c,DiagnosticLog.Event.WAKE_BLOCKED,phase,"ACCESS_"+access.reason());return;}
        String day=LocalDate.now(Policy.ZONE).toString();
        if((day+":"+phase).equals(p.raw.getString("chrome_pending",""))){DiagnosticLog.add(c,DiagnosticLog.Event.WAKE_BLOCKED,phase,"PENDING_CLICK");return;}
        String id=claim(c,phase,day,mode(c));
        if(id.isEmpty())return;
        DiagnosticLog.add(c,DiagnosticLog.Event.WAKE_REQUESTED,phase,mode(c).name(),details(c));
        try{ChromeAttendanceService.launchScheduledUnlock(new Intent(c,ScheduledUnlockActivity.class).putExtra(TOKEN,id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(RuntimeException e){stop(c,id,"WAKE_LAUNCH_"+DiagnosticLog.failureCode(e),true);return;}
        MAIN.postDelayed(()->{if(live(c,id)&&store(c).getLong("entered_elapsed",0)==0)stop(c,id,"WAKE_ACTIVITY_NOT_VISIBLE",true);},UnlockPolicy.VISIBLE_TIMEOUT_MS);
        MAIN.postDelayed(()->{if(id.equals(token(c))&&store(c).getBoolean("active",false))stop(c,id,"WAKE_EXPIRED",true);},UnlockPolicy.SESSION_TIMEOUT_MS);
    }
    /** Persist before dispatch. Changing modes, toggling reservations or restarting never replenishes a day. */
    static synchronized String claim(Context c,Policy.Phase phase,String day,UnlockPolicy.Mode mode){
        if(phase==Policy.Phase.IDLE)return "";
        if(boot(c)<0){DiagnosticLog.add(c,DiagnosticLog.Event.WAKE_BLOCKED,phase,"BOOT_COUNT_UNAVAILABLE");return "";}
        SharedPreferences s=store(c);String key="attempted:"+phase.name();
        if(!UnlockPolicy.canAttempt(day,s.getString(key,"")))return "";
        String token=UUID.randomUUID().toString();
        if(!s.edit().putString(key,day).putString("token",token).putBoolean("active",true).putString("day",day).putString("phase",phase.name())
                .putString("mode",mode.name()).putInt("boot",boot(c)).putLong("started_elapsed",SystemClock.elapsedRealtime())
                .putLong("requested_at_ms",System.currentTimeMillis()).remove("entered_elapsed").putBoolean("attached",false).putString("result","REQUESTED").commit())return "";
        return token;
    }
    static synchronized boolean attach(Context c,String token,boolean recreated){
        if(!live(c,token))return false;
        if(store(c).getBoolean("attached",false))return recreated;
        return store(c).edit().putBoolean("attached",true).commit();
    }
    static synchronized boolean entered(Context c,String token,boolean recreated){
        if(!live(c,token))return false;
        if(store(c).getLong("entered_elapsed",0)>0)return recreated;
        if(!store(c).edit().putLong("entered_elapsed",SystemClock.elapsedRealtime()).commit())return false;
        DiagnosticLog.add(c,DiagnosticLog.Event.WAKE_VISIBLE,phase(c),"ACTIVITY_VISIBLE",details(c));return true;
    }
    static synchronized void stop(Context c,String token,String reason,boolean notify){
        if(!token.equals(token(c))||!store(c).getBoolean("active",false))return;
        Policy.Phase phase=phase(c);store(c).edit().putBoolean("active",false).putString("result",reason).commit();
        DiagnosticLog.add(c,DiagnosticLog.Event.WAKE_FINISHED,phase,reason,details(c));
        if(notify&&new Prefs(c).phase(ZonedDateTime.now(Policy.ZONE))==phase)
            Notices.chromeAction(c,true,"화면 깨우기·잠금 해제를 완료하지 못했습니다. 알림을 눌러 잠금을 해제하고 출결을 확인해 주세요.");
    }
    static void cancel(Context c,String reason){stop(c,token(c),reason,false);}
    static boolean handoff(Context c,String token){
        if(!live(c,token)||!NetworkAccess.inspect(c).allowed())return false;
        android.app.KeyguardManager k=c.getSystemService(android.app.KeyguardManager.class);
        if(k.isKeyguardLocked()||k.isDeviceLocked()||!c.getSystemService(PowerManager.class).isInteractive())return false;
        stop(c,token,"UNLOCK_READY",false);return true;
    }
}
