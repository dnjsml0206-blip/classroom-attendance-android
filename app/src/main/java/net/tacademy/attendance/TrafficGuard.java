package net.tacademy.attendance;

import android.content.*;
import android.os.SystemClock;
import android.provider.Settings;
import java.time.*;

/** Persisted admission gate shared by every app route to the attendance host. */
@android.annotation.SuppressLint("ApplySharedPref")
final class TrafficGuard {
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("attendance_access",Context.MODE_PRIVATE);}
    static synchronized AccessPolicy.Decision claim(Context c,AccessPolicy.Kind kind,Policy.Phase phase){
        return claim(c,kind,phase,System.currentTimeMillis(),SystemClock.elapsedRealtime(),Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1));
    }
    static synchronized AccessPolicy.Decision claim(Context c,AccessPolicy.Kind kind,Policy.Phase phase,long wall,long elapsed,int boot){
        return decide(c,kind,phase,wall,elapsed,boot,true);
    }
    static synchronized AccessPolicy.Decision peek(Context c,AccessPolicy.Kind kind,Policy.Phase phase){
        return decide(c,kind,phase,System.currentTimeMillis(),SystemClock.elapsedRealtime(),Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1),false);
    }
    private static AccessPolicy.Decision decide(Context c,AccessPolicy.Kind kind,Policy.Phase phase,long wall,long elapsed,int boot,boolean claim){
        SharedPreferences p=prefs(c);String today=Instant.ofEpochMilli(wall).atZone(Policy.ZONE).toLocalDate().toString();
        String previous=p.getString("day","");boolean newDay=today.compareTo(previous)>0;
        String key=phase.name();int total=newDay?0:p.getInt("total",0),count=newDay?0:p.getInt("count:"+key,0);
        boolean paused=!newDay&&p.getBoolean("paused:"+key,false);
        long common=AccessPolicy.remaining(wall,elapsed,boot,p.getLong("last_wall",0),p.getLong("last_elapsed",0),p.getInt("last_boot",-1),AccessPolicy.GLOBAL_GAP_MS);
        long automatic=newDay?0:AccessPolicy.remaining(wall,elapsed,boot,p.getLong("wall:"+key,0),p.getLong("elapsed:"+key,0),p.getInt("boot:"+key,-1),AccessPolicy.retryGap(count));
        if(!newDay)automatic=Math.max(automatic,AccessPolicy.remaining(wall,elapsed,boot,p.getLong("defer_wall:"+key,0),p.getLong("defer_elapsed:"+key,0),p.getInt("defer_boot:"+key,-1),AccessPolicy.retryGap(1)));
        AccessPolicy.Decision decision=AccessPolicy.decide(kind,total,count,paused,common,automatic);
        if(claim&&decision.allowed()){
            SharedPreferences.Editor edit=p.edit();
            if(newDay)edit.clear().putString("day",today);
            edit.putInt("total",total+1).putLong("last_wall",wall).putLong("last_elapsed",elapsed).putInt("last_boot",boot);
            if(kind==AccessPolicy.Kind.AUTOMATIC)edit.putInt("count:"+key,count+1).putLong("wall:"+key,wall).putLong("elapsed:"+key,elapsed).putInt("boot:"+key,boot);
            if(!edit.commit())decision=new AccessPolicy.Decision(AccessPolicy.Reason.STORAGE_ERROR,0);
        }
        if(claim&&!decision.allowed())DiagnosticLog.add(c,DiagnosticLog.Event.ACCESS_BLOCKED,phase,kind+":"+decision.reason());
        return decision;
    }
    /** A user returning before a click gets a bounded automatic cooldown; explicit manual actions remain available. */
    static synchronized void deferAfterReturn(Context c,Policy.Phase phase){
        if(phase==Policy.Phase.IDLE)return;
        prefs(c).edit().putLong("defer_wall:"+phase,System.currentTimeMillis()).putLong("defer_elapsed:"+phase,SystemClock.elapsedRealtime())
            .putInt("defer_boot:"+phase,Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1)).commit();
        DiagnosticLog.add(c,DiagnosticLog.Event.PRECLICK_DEFERRED,phase,"USER_RETURNED_TO_APP");
    }
    static void pause(Context c,Policy.Phase phase){pause(c,phase,"RESULT_UNCONFIRMED");}
    static synchronized void pause(Context c,Policy.Phase phase,String reason){
        if(phase==Policy.Phase.IDLE)return;
        SharedPreferences p=prefs(c);String today=LocalDate.now(Policy.ZONE).toString();
        if(today.equals(p.getString("day","")))p.edit().putBoolean("paused:"+phase.name(),true).commit();
        DiagnosticLog.add(c,DiagnosticLog.Event.AUTOMATIC_PAUSED,phase,reason);
    }
    static String message(AccessPolicy.Decision d){return switch(d.reason()){
        case COOLDOWN -> "연속 접속을 줄이기 위해 "+Math.max(1,(d.waitMillis()+999)/1000)+"초 뒤에 다시 확인할 수 있습니다.";
        case DAILY_LIMIT -> "오늘 앱의 사이트 열기 한도(20회)에 도달했습니다. Chrome에서 직접 출결을 확인해 주세요.";
        case AUTOMATIC_LIMIT -> "이 시간대의 자동 접속 3회를 마쳤습니다. Chrome에서 직접 출결을 확인해 주세요.";
        case PAUSED -> "이전 결과를 확인하지 못해 자동 재접속을 멈췄습니다. Chrome에서 직접 출결을 확인해 주세요.";
        case STORAGE_ERROR -> "접속 상태를 저장하지 못해 사이트 열기를 보류했습니다.";
        default -> "";
    };}
}
