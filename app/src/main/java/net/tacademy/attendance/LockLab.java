package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.os.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

/** Bounded, explicit trials. Separate storage and notification identity from real attendance. */
@android.annotation.SuppressLint("ApplySharedPref")
public final class LockLab {
    static final int NOTICE=24, ALARM=2401;
    static final String TOKEN="lock_test_token", SOURCE="lock_test_source";
    private LockLab() {}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("lock_lab",Context.MODE_PRIVATE);}
    static String id(Context c){return prefs(c).getString("id","");}
    static LockTestPolicy.Mode mode(Context c){try{return LockTestPolicy.Mode.valueOf(prefs(c).getString("mode","WAKE"));}catch(Exception e){return LockTestPolicy.Mode.WAKE;}}
    static boolean actual(Context c){return prefs(c).getBoolean("actual",false) && mode(c)!=LockTestPolicy.Mode.NETWORK;}
    static boolean live(Context c,String id){SharedPreferences p=prefs(c);return LockTestPolicy.live(id,p.getString("id",""),System.currentTimeMillis(),p.getLong("created",0),p.getLong("expires",0),p.getBoolean("active",false));}
    public static boolean readOnlyActive(Context c){return live(c,id(c)) && (!prefs(c).getBoolean("checkout_trial",false) || System.currentTimeMillis()>=prefs(c).getLong("due",0)) && !prefs(c).getBoolean("allow_attendance",false);}
    static String snapshot(Context c){
        KeyguardManager k=c.getSystemService(KeyguardManager.class);
        return "화면 "+(c.getSystemService(PowerManager.class).isInteractive()?"켜짐":"꺼짐")+
            " · 잠금 화면 "+(k.isKeyguardLocked()?"표시":"없음")+" · 인증 필요 "+(k.isDeviceLocked()?"예":"아니요")+
            " · 보안 잠금 "+(k.isDeviceSecure()?"설정됨":"없음");
    }
    static String device(){return Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT;}
    static PendingIntent alarm(Context c,String token){return PendingIntent.getBroadcast(c,ALARM,new Intent(c,LockTestReceiver.class).putExtra(TOKEN,token),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static synchronized String prepare(Context c,LockTestPolicy.Mode mode,boolean actual,int delaySeconds){
        return prepareAt(c,mode,actual,System.currentTimeMillis()+delaySeconds*1000L,delaySeconds>0,false);
    }
    static synchronized String prepareCheckout(Context c,LockTestPolicy.Mode mode){
        if(mode==LockTestPolicy.Mode.NETWORK)throw new IllegalStateException("서버 연결 진단은 퇴실을 처리하지 않습니다. Chrome 테스트 방법을 선택해 주세요.");
        ZonedDateTime now=ZonedDateTime.now(Policy.ZONE);
        long due=now.toLocalDate().atTime(Policy.checkoutStartTime(new Prefs(c).checkout())).atZone(Policy.ZONE).toInstant().toEpochMilli();
        if(due<=System.currentTimeMillis())throw new IllegalStateException("오늘 퇴실 시각이 지났습니다. 시간 설정을 확인해 주세요.");
        return prepareAt(c,mode,true,due,true,true);
    }
    private static synchronized String prepareAt(Context c,LockTestPolicy.Mode mode,boolean actual,long due,boolean scheduled,boolean checkout){
        expire(c);
        List<String> issues=TrialReadiness.problems(c,mode,actual,due,scheduled);
        if(!issues.isEmpty())throw new IllegalStateException(String.join("\n",issues));
        if(ChromeAttendanceService.busy())throw new IllegalStateException("출결 처리 중입니다. 완료 후 테스트해 주세요.");
        if(scheduled && !AlarmScheduler.allowed(c))throw new IllegalStateException("휴대폰에서 알람 및 리마인더를 허용해 주세요.");
        if(live(c,id(c)))finish(c,id(c),"다음 테스트로 교체됨");
        archive(c); c.getSystemService(AlarmManager.class).cancel(alarm(c,""));
        String token=UUID.randomUUID().toString(); long now=System.currentTimeMillis();
        if(!prefs(c).edit().putString("id",token).putString("mode",mode.name()).putLong("created",now).putLong("due",due)
            .putLong("expires",due+120000).putBoolean("checkout_trial",checkout).putString("target_phase",checkout?"CHECKOUT":"").putBoolean("active",true).remove("execution_environment")
            .remove("fired_at_ms").remove("fired_elapsed_ms").remove("entry_at_ms").remove("entry_delay_ms").remove("entry_environment").remove("handoff_environment").putString("entry_source","").putString("user_interaction","UNKNOWN").putBoolean("allow_attendance",false).putBoolean("started",false).putBoolean("fired",false)
            .putBoolean("actual",actual && mode!=LockTestPolicy.Mode.NETWORK).putString("events","").putString("stage","실행 대기")
            .putString("verdict","미확인").putString("note","").putString("environment",LockLogExport.environment(c).toString()).commit())throw new IllegalStateException("테스트 저장 실패");
        event(c,token,mode.label+" 예약 · "+(actual(c)?"조건 충족 시 실제 출결 시도":"조회 테스트"));
        event(c,token,"예약 당시: "+snapshot(c));
        if(scheduled){
            try{c.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,alarm(c,token));}
            catch(SecurityException e){finish(c,token,"정확한 알람 권한 없음");throw new IllegalStateException("알람 및 리마인더를 허용해 주세요.");}
            notice(c,checkout?"오늘 퇴실 테스트 예약":"잠금 테스트 예약",checkout?"오늘 "+Policy.checkoutStartTime(new Prefs(c).checkout())+" · "+mode.label+" · 실제 퇴실을 한 번 시험합니다. 실행 시 설정과 Wi-Fi를 다시 확인합니다.":"예약 시각 전에 화면을 꺼 주세요. 기존 출결 예약과 별도인 일회성 테스트입니다.",false);
        }
        return token;
    }
    static synchronized void event(Context c,String token,String message){
        if(!token.equals(id(c)))return;
        String stamp=ZonedDateTime.now(Policy.ZONE).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String log=prefs(c).getString("events","");
        String[] lines=(log+stamp+"  "+message+"\n").split("\n");
        if(lines.length>70)lines=Arrays.copyOfRange(lines,lines.length-70,lines.length);
        prefs(c).edit().putString("events",String.join("\n",lines)+"\n").commit();
    }
    static synchronized void stage(Context c,String token,String stage){if(live(c,token)){prefs(c).edit().putString("stage",stage).commit();event(c,token,stage);}}
    static synchronized void finish(Context c,String token,String result){
        if(!live(c,token))return;
        event(c,token,result+" · "+snapshot(c));
        prefs(c).edit().putBoolean("active",false).putString("stage",result).commit();
        c.getSystemService(AlarmManager.class).cancel(alarm(c,token));
        notice(c,"잠금 테스트 결과",result,false);
    }
    static synchronized void expire(Context c){
        if(prefs(c).getBoolean("active",false)&&!live(c,id(c))){
            String reason=prefs(c).getBoolean("started",false)?"확인 시간 만료":"자동 진입 미확인 · 실행 제한 또는 알람 지연 가능";
            event(c,id(c),reason);prefs(c).edit().putBoolean("active",false).putString("stage",reason).commit();
            c.getSystemService(AlarmManager.class).cancel(alarm(c,id(c)));notice(c,"잠금 테스트 종료",reason,false);
        }
    }
    static void cancel(Context c){boolean owns=live(c,id(c))&&prefs(c).getBoolean("allow_attendance",false);expire(c);if(live(c,id(c)))finish(c,id(c),"사용자가 테스트 취소");ChromeAttendanceService.cancelLockProbe();if(owns)ChromeAttendanceService.cancelWork();}
    static void resetAfterRestart(Context c){if(prefs(c).getBoolean("active",false)){event(c,id(c),"재부팅 또는 앱 업데이트로 테스트 종료");prefs(c).edit().putBoolean("active",false).putString("stage","재부팅/업데이트로 종료").commit();}}
    static synchronized void fireCheckoutIfDue(Context c){
        if(live(c,id(c))&&prefs(c).getBoolean("checkout_trial",false)&&System.currentTimeMillis()>=prefs(c).getLong("due",Long.MAX_VALUE))
            fire(c,id(c),"예약 요청에서 진입 · 무조작 여부 미확인");
    }
    static synchronized void fire(Context c,String token,String source){
        if(!live(c,token)){expire(c);return;}
        if(prefs(c).getBoolean("fired",false))return;
        prefs(c).edit().putBoolean("fired",true).putLong("fired_at_ms",System.currentTimeMillis()).putLong("fired_elapsed_ms",SystemClock.elapsedRealtime()).putString("execution_environment",LockLogExport.environment(c).toString()).commit();event(c,token,"실행 당시: "+snapshot(c));
        if(mode(c)==LockTestPolicy.Mode.NETWORK){stage(c,token,"화면을 깨우지 않고 서버 연결 진단");return;}
        if(!ChromeAttendanceService.available()){finish(c,token,"Chrome 접근성 연결 끊김 · 연결 탭에서 다시 켜 주세요");return;}
        stage(c,token,"테스트 화면 실행 요청 · 실제 표시 여부는 별도 확인");
        notice(c,"잠금 테스트 진행","화면이 열리지 않으면 알림을 눌러 비교하세요. 수동 진입은 따로 기록합니다.",true);
        try{ChromeAttendanceService.launchLockTest(c,new Intent(c,LockTrialActivity.class).putExtra(TOKEN,token).putExtra(SOURCE,source).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(RuntimeException e){finish(c,token,"테스트 화면 실행 거절: "+e.getClass().getSimpleName());}
    }
    static synchronized boolean entered(Context c,String token,String source){
        if(!live(c,token))return false;
        if(prefs(c).getBoolean("started",false))return false;
        long elapsed=prefs(c).getLong("fired_elapsed_ms",-1);
        long delay=elapsed<0?-1:Math.max(0,SystemClock.elapsedRealtime()-elapsed);
        prefs(c).edit().putBoolean("started",true).putLong("entry_at_ms",System.currentTimeMillis()).putLong("entry_delay_ms",delay)
            .putString("entry_source",source==null?"UNKNOWN":source).putString("entry_environment",LockLogExport.environment(c).toString()).commit();
        stage(c,token,"테스트 화면 진입 · "+source);
        event(c,token,"화면 진입 지연: "+(delay<0?"측정 불가":delay+"ms")+" · 예약 경로는 자동 잠금 해제 성공을 뜻하지 않음");
        event(c,token,snapshot(c));return true;
    }
    static void notice(Context c,String title,String text,boolean resume){
        Notices.channels(c);
        Intent intent=resume?new Intent(c,LockTrialActivity.class).putExtra(TOKEN,id(c)).putExtra(SOURCE,"알림에서 수동 진입") : new Intent(c,LockLabActivity.class);
        PendingIntent open=PendingIntent.getActivity(c,resume?2402:2403,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,"attention").setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(false).setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build();
        try{c.getSystemService(NotificationManager.class).notify(NOTICE,n);}catch(SecurityException ignored){}
    }
    static synchronized void verdict(Context c,String verdict,String note){verdict(c,verdict,note,"UNKNOWN");}
    static synchronized void verdict(Context c,String verdict,String note,String interaction){
        prefs(c).edit().putString("verdict",verdict).putString("user_interaction",interaction).putString("note",note.substring(0,Math.min(1000,note.length()))).commit();
    }
    static String report(Context c){
        SharedPreferences p=prefs(c);if(id(c).isEmpty())return "아직 테스트 기록이 없습니다.";
        return device()+"\n방식: "+mode(c).label+"\n실제 출결 시도: "+(actual(c)?"켜짐":"꺼짐")+"\n진행 결과: "+p.getString("stage","")+
            "\n직접 조작: "+interactionLabel(p.getString("user_interaction","UNKNOWN"))+"\n사용자 확인: "+p.getString("verdict","미확인")+"\n메모: "+p.getString("note","")+"\n\n"+p.getString("events","");
    }
    static String interactionLabel(String value){return switch(value){case "UNTOUCHED"->"아무 조작 없이 기다림";case "MANUAL_WAKE"->"직접 화면을 켬";case "MANUAL_UNLOCK"->"직접 잠금을 해제함";case "NOTIFICATION"->"알림을 직접 누름";case "SYSTEM_AUTH"->"앱의 시스템 인증 요청에 응답함";default->"아직 기록하지 않음";};}
    private static void archive(Context c){
        if(id(c).isEmpty())return;
        try{
            JSONArray old=new JSONArray(prefs(c).getString("history","[]")), next=new JSONArray();
            JSONArray details=new JSONArray(prefs(c).getString("history_details","[]")), nextDetails=new JSONArray();
            next.put(report(c));nextDetails.put(LockLogExport.current(c));
            for(int i=0;i<Math.min(9,old.length());i++){next.put(old.getString(i));JSONObject detail=details.optJSONObject(i);nextDetails.put(detail==null?LockLogExport.legacy(old.getString(i)):detail);}
            prefs(c).edit().putString("history",next.toString()).putString("history_details",nextDetails.toString()).commit();
        }catch(JSONException ignored){}
    }
    static String allReports(Context c){StringBuilder out=new StringBuilder(report(c));try{JSONArray history=new JSONArray(prefs(c).getString("history","[]"));for(int i=0;i<history.length();i++)out.append("\n\n──────── 이전 테스트 ────────\n").append(history.getString(i));}catch(JSONException ignored){}return out.toString();}
}
