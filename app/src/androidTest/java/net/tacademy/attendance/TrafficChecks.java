package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Bitmap;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.*;
import java.util.*;
import org.json.*;

/** Exercises real persisted admission and creator/public UI without any permitted Chrome launch or HTTP. */
final class TrafficChecks {
    private final Instrumentation test;
    private Context c;
    private MainActivity activity;
    private Activity upload;
    private final Map<String,Map<String,?>> saved=new HashMap<>();
    private int checks;
    TrafficChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Build.HARDWARE.equals("ranchu")&&!Build.HARDWARE.equals("goldfish"))throw new AssertionError("Emulator only");
        c=test.getTargetContext();
        for(String name:new String[]{"attendance","attendance_access","attendance_diagnostics","creator_tools","lock_lab","github_log_upload"}){
            SharedPreferences p=c.getSharedPreferences(name,0);saved.put(name,new HashMap<>(p.getAll()));p.edit().clear().commit();
        }
        new Prefs(c).raw.edit().putBoolean("enabled",false).putBoolean("auto",false).putStringSet("ssids",Set.of("__TRAFFIC_NO_MATCH__")).commit();
        long wall=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();int boot=Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);
        check(claim(AccessPolicy.Kind.AUTOMATIC,Policy.Phase.ENTRY,wall,elapsed,boot).allowed(),"First automatic admission is available");
        check(claim(AccessPolicy.Kind.MANUAL,Policy.Phase.IDLE,wall+1,elapsed+1,boot).reason()==AccessPolicy.Reason.COOLDOWN,"Manual confirmation shares the automatic cooldown");
        check(claim(AccessPolicy.Kind.TEST,Policy.Phase.IDLE,wall+1,elapsed+1,boot).reason()==AccessPolicy.Reason.COOLDOWN,"Immediate tests share the same cooldown");
        // A hard cap keeps this real entry-point check offline even if instrumentation stalls beyond three seconds.
        boolean[] opened={true};main(()->{
            TrafficGuard.prefs(c).edit().putInt("total",AccessPolicy.DAILY_LIMIT).commit();
            opened[0]=ChromeAttendanceService.openChrome(c,SiteProtocol.PAGE);
        });
        check(!opened[0]&&TrafficGuard.prefs(c).getInt("total",0)==AccessPolicy.DAILY_LIMIT,"Real Chrome entry point refuses repeated navigation before launching an intent");
        TrafficGuard.prefs(c).edit().putInt("total",1).commit();
        check(claim(AccessPolicy.Kind.MANUAL,Policy.Phase.IDLE,wall+2999,elapsed+2999,boot).reason()==AccessPolicy.Reason.COOLDOWN,"Manual navigation waits through 2.999 seconds");
        check(claim(AccessPolicy.Kind.MANUAL,Policy.Phase.IDLE,wall+3000,elapsed+3000,boot).allowed(),"Manual navigation is admitted at exactly three seconds");
        check(claim(AccessPolicy.Kind.TEST,Policy.Phase.IDLE,wall+5999,elapsed+5999,boot).reason()==AccessPolicy.Reason.COOLDOWN,"Test navigation shares the latest manual navigation cooldown");
        check(claim(AccessPolicy.Kind.TEST,Policy.Phase.IDLE,wall+6000,elapsed+6000,boot).allowed(),"Test navigation is admitted three seconds after manual navigation");
        check(claim(AccessPolicy.Kind.AUTOMATIC,Policy.Phase.ENTRY,wall+60000,elapsed+60000,boot).reason()==AccessPolicy.Reason.COOLDOWN,"Automatic retries wait two minutes after the first admission");
        check(claim(AccessPolicy.Kind.AUTOMATIC,Policy.Phase.ENTRY,wall+120000,elapsed+120000,boot).allowed(),"Second automatic admission starts after two minutes");
        check(claim(AccessPolicy.Kind.AUTOMATIC,Policy.Phase.ENTRY,wall+420000,elapsed+420000,boot).allowed(),"Third automatic admission waits another five minutes");
        check(claim(AccessPolicy.Kind.AUTOMATIC,Policy.Phase.ENTRY,wall+900000,elapsed+900000,boot).reason()==AccessPolicy.Reason.AUTOMATIC_LIMIT,"No fourth automatic admission in one phase");
        check(claim(AccessPolicy.Kind.AUTOMATIC,Policy.Phase.CHECKOUT,wall+900000,elapsed+900000,boot).allowed(),"Checkout has its own phase budget");
        new Prefs(c).enabled(true);new Prefs(c).enabled(false);
        check(claim(AccessPolicy.Kind.AUTOMATIC,Policy.Phase.ENTRY,wall+1000000,elapsed+1000000,boot).reason()==AccessPolicy.Reason.AUTOMATIC_LIMIT,"Reservation toggles and fresh preference instances cannot reset the budget");
        TrafficGuard.pause(c,Policy.Phase.CHECKOUT);
        check(claim(AccessPolicy.Kind.AUTOMATIC,Policy.Phase.CHECKOUT,wall+1200000,elapsed+1200000,boot).reason()==AccessPolicy.Reason.PAUSED,"Unconfirmed result persistently pauses automatic reconnection");
        check(claim(AccessPolicy.Kind.MANUAL,Policy.Phase.IDLE,wall+1200000,elapsed+1200000,boot).allowed(),"Explicit manual browser access remains possible after the cooldown");
        TrafficGuard.prefs(c).edit().clear().commit();
        for(int i=0;i<20;i++)if(!claim(i%2==0?AccessPolicy.Kind.MANUAL:AccessPolicy.Kind.TEST,Policy.Phase.IDLE,wall+i*3000L,elapsed+i*3000L,boot).allowed())throw new AssertionError("Unexpected daily budget denial");
        check(claim(AccessPolicy.Kind.AUTOMATIC,Policy.Phase.CHECKOUT,wall+1800000,elapsed+1800000,boot).reason()==AccessPolicy.Reason.DAILY_LIMIT,"All routes share the hard daily navigation cap");
        check(claim(AccessPolicy.Kind.MANUAL,Policy.Phase.IDLE,wall+86400000,elapsed+86400000,boot).allowed()&&TrafficGuard.prefs(c).getInt("total",0)==1,"Next Korean day restores its budget");
        TrafficGuard.prefs(c).edit().clear().commit();claim(AccessPolicy.Kind.MANUAL,Policy.Phase.IDLE,wall,elapsed,boot);
        check(claim(AccessPolicy.Kind.TEST,Policy.Phase.IDLE,wall+1000,500,boot+1).reason()==AccessPolicy.Reason.COOLDOWN,"Reboot preserves the wall-clock cooldown");
        DiagnosticLog.prefs(c).edit().clear().commit();
        DiagnosticLog.add(c,DiagnosticLog.Event.CHROME_OPEN_REQUEST,Policy.Phase.ENTRY,"AUTOMATIC");
        DiagnosticLog.add(c,DiagnosticLog.Event.CHROME_OPEN_REQUEST,Policy.Phase.ENTRY,"AUTOMATIC");
        check(DiagnosticLog.records(c).length()==1,"Repeated identical status does not fill the diagnostic journal");
        JSONArray events=DiagnosticLog.records(c);long at=System.currentTimeMillis();
        JSONObject remembered=LogUploadWindow.remember(events,at);
        check(LogUploadWindow.select(events,remembered,at,at+1).records().length()==0,"Uploaded ordinary attendance events are excluded from the next period");
        JSONObject record=new JSONObject().put("test_id","old").put("events","08:00 event\n").put("evidence",new JSONObject().put("derived",true));
        JSONArray records=new JSONArray().put(record);JSONObject legacy=new JSONObject().put("test:old",new JSONObject().put("hash",LockLogExport.digest(LogUploadWindow.canonical(record))).put("at_ms",at));
        LogUploadWindow.Selection adopted=LogUploadWindow.select(records,legacy,at,at+1);
        check(adopted.records().length()==0,"Existing full-content upload hashes migrate without resending unchanged records");
        record.put("evidence",new JSONObject().put("derived",false));record.put("events","08:00 event\n   ");
        check(LogUploadWindow.select(records,adopted.observations(),at,at+2).records().length()==0,"Recomputed evidence and trailing whitespace do not resend old tests");
        record.put("user_note","changed");check(LogUploadWindow.select(records,adopted.observations(),at,at+3).records().length()==1,"A real edited verdict or note remains uploadable");
        for(int i=0;i<205;i++)DiagnosticLog.add(c,DiagnosticLog.Event.WAITING,Policy.Phase.ENTRY,"TEST_"+i);
        check(DiagnosticLog.records(c).length()==DiagnosticLog.LIMIT,"Local operational logs have a fixed retention count");
        activity=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).putExtra("open_tab",1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));settle();
        if(CreatorTools.enabled())creatorChecks();else publicChecks();
        restore();result.putString("stream","PASS: "+checks+" traffic and "+(BuildConfig.CREATOR_TOOLS?"creator":"public")+" distribution checks. No attendance HTTP or Chrome launch performed.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private void creatorChecks()throws Exception{
        check(find("로그 확인 · GitHub 업로드")!=null&&find("잠금 상태 테스트 열기")!=null,"Creator settings expose diagnostics directly");capture("creator-settings-testing.png");
        main(()->find("테스트 완료 · 임시 메뉴 숨기기").performClick());settle();
        check(find("잠금 상태 테스트 열기")==null&&find("로그 확인 · GitHub 업로드")!=null&&!CreatorTools.testsVisible(c),"Completing tests removes the temporary menu and keeps creator upload");
        boolean rejected=false;try{LockLab.prepare(c,LockTestPolicy.Mode.WAKE,false,15);}catch(IllegalStateException e){rejected=true;}
        check(rejected,"Hidden tests cannot be scheduled through their underlying API");capture("creator-settings-finished.png");
        Instrumentation.ActivityMonitor monitor=test.addMonitor(LogUploadActivity.class.getName(),null,false);
        main(()->find("로그 확인 · GitHub 업로드").performClick());upload=test.waitForMonitorWithTimeout(monitor,5000);test.removeMonitor(monitor);settle();
        check(upload!=null,"Creator can open upload after tests have ended");
        JSONObject payload=new JSONObject(LogUploadStore.prepare(c));boolean ordinary=false;JSONArray records=payload.getJSONArray("records");
        for(int i=0;i<records.length();i++)ordinary|="attendance_event".equals(records.getJSONObject(i).optString("record_type"));
        check(ordinary,"Upload includes ordinary attendance events even with no lock tests");capture("creator-upload.png");
        main(()->upload.finish());upload=null;settle();
        main(()->find("임시 테스트 메뉴 다시 켜기").performClick());settle();check(CreatorTools.testsVisible(c)&&find("잠금 상태 테스트 열기")!=null,"Creator can explicitly re-enable temporary diagnostics");
    }
    private void publicChecks()throws Exception{
        CreatorTools.prefs(c).edit().putBoolean("tests_visible",true).commit();
        check(find("로그 확인 · GitHub 업로드")==null&&find("잠금 상태 테스트 열기")==null&&!CreatorTools.testsVisible(c),"Public settings and preferences cannot enable creator tools");
        for(Class<?> type:new Class<?>[]{LogUploadActivity.class,LockLabActivity.class,LockTrialActivity.class}){
            ActivityInfo info=c.getPackageManager().getActivityInfo(new ComponentName(c,type),PackageManager.MATCH_DISABLED_COMPONENTS);
            if(info.enabled)throw new AssertionError("Public diagnostic component enabled");
        }
        check(true,"Public APK disables all diagnostic activities in its manifest");
        boolean prepareBlocked=false,startBlocked=false;
        try{LogUploadStore.prepare(c);}catch(IllegalStateException e){prepareBlocked=true;}
        try{LogUploadStore.start(c);}catch(IllegalStateException e){startBlocked=true;}
        check(prepareBlocked&&startBlocked,"Public upload APIs fail before preparing or sending a request");capture("public-settings.png");
    }
    private AccessPolicy.Decision claim(AccessPolicy.Kind kind,Policy.Phase phase,long wall,long elapsed,int boot){return TrafficGuard.claim(c,kind,phase,wall,elapsed,boot);}
    private TextView find(String title){return find(activity.getWindow().getDecorView(),title);}
    private TextView find(View view,String title){if(view instanceof TextView&&((TextView)view).getText().toString().equals(title))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView result=find(((ViewGroup)view).getChildAt(i),title);if(result!=null)return result;}return null;}
    private void main(Runnable action){Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    private void settle(){SystemClock.sleep(300);test.waitForIdleSync();}
    private void check(boolean passed,String message){if(!passed)throw new AssertionError(message);checks++;Bundle progress=new Bundle();progress.putString("stream","PASS: "+message+"\n");test.sendStatus(0,progress);}
    private void capture(String name)throws Exception{SystemClock.sleep(2000);Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("No screenshot");File dir=new File(c.getExternalFilesDir(null),"traffic-screens");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Screenshot directory");try(FileOutputStream out=new FileOutputStream(new File(dir,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
    @SuppressWarnings("unchecked")private void restore(){if(c==null)return;main(()->{if(upload!=null)upload.finish();if(activity!=null)activity.finish();for(Map.Entry<String,Map<String,?>> store:saved.entrySet()){
        SharedPreferences.Editor e=c.getSharedPreferences(store.getKey(),0).edit().clear();for(Map.Entry<String,?> item:store.getValue().entrySet()){
            String k=item.getKey();Object v=item.getValue();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<>((Set<String>)v));
        }if(!e.commit())throw new AssertionError("Restore failed");}});}
}
