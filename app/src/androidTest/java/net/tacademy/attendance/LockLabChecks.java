package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Local emulator checks with attendance disabled and a deliberately unmatched SSID. */
public final class LockLabChecks {
    private final Instrumentation test;
    private Context c;
    private Activity activity;
    private Map<String,?> attendanceSaved,labSaved;
    private int checks;
    LockLabChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Build.HARDWARE.equals("ranchu")&&!Build.HARDWARE.equals("goldfish"))throw new AssertionError("Emulator only");
        test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        c=test.getTargetContext();Prefs prefs=new Prefs(c);attendanceSaved=new HashMap<>(prefs.raw.getAll());labSaved=new HashMap<>(LockLab.prefs(c).getAll());
        main(()->{prefs.raw.edit().putBoolean("enabled",false).putBoolean("auto",false).putStringSet("ssids",Set.of("__LOCK_LAB_NO_MATCH__")).commit();AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));LockLab.prefs(c).edit().clear().commit();});
        activity=test.startActivitySync(new Intent(c,LockLabActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
        // Instrumentation restarts the target process, so reconnect its service before checking the lab.
        if(Build.VERSION.SDK_INT>=29){
            UiAutomation automation=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            automation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);
            try{
                String services=android.provider.Settings.Secure.getString(c.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                android.provider.Settings.Secure.putString(c.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,"");
                SystemClock.sleep(300);
                android.provider.Settings.Secure.putString(c.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,services);
            }finally{automation.dropShellPermissionIdentity();}
        }
        long connectedUntil=SystemClock.elapsedRealtime()+8000;
        while(!ChromeAttendanceService.available()&&SystemClock.elapsedRealtime()<connectedUntil)SystemClock.sleep(100);
        check(ChromeAttendanceService.available(),"Emulator accessibility service is connected for lifecycle checks");
        check(find(activity.getWindow().getDecorView(),"잠금 상태 테스트")!=null,"Temporary lab opens");
        String token=prepare(LockTestPolicy.Mode.WAKE,false,15);
        check(LockLab.live(c,token)&&LockLab.readOnlyActive(c),"Scheduled trial holds attendance without changing saved automatic settings");
        main(()->LockLab.cancel(c));check(!LockLab.live(c,token)&&!LockLab.readOnlyActive(c),"Cancel releases the hold");
        String afterCancel=LockLab.report(c);main(()->new LockTestReceiver().onReceive(c,new Intent(c,LockTestReceiver.class).putExtra(LockLab.TOKEN,token)));
        check(afterCancel.equals(LockLab.report(c)),"Cancelled alarm cannot restart a trial");
        boolean[] blocked={false};
        main(()->{try{LockLab.prepare(c,LockTestPolicy.Mode.AUTH,true,15);}catch(IllegalStateException e){blocked[0]=e.getMessage().contains("출석 예약")&&e.getMessage().contains("자동 클릭");}});
        check(blocked[0]&&!LockLab.live(c,LockLab.id(c)),"Missing schedule and auto-click block real attendance before creating a trial");
        String actual=token;
        String checkoutTime=java.time.ZonedDateTime.now(Policy.ZONE).plusMinutes(15).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        main(()->prefs.raw.edit().putBoolean("enabled",true).putBoolean("auto",true).putString("start","00:00").putString("end","00:01").putString("checkout",checkoutTime)
            .putStringSet("days",Set.of("1","2","3","4","5","6","7")).putStringSet("excluded",Set.of()).remove("record_day").remove("in").remove("out").commit());
        String[] checkoutId={""};main(()->checkoutId[0]=LockLab.prepareCheckout(c,LockTestPolicy.Mode.EXTEND));
        check(LockLab.actual(c)&&LockLab.prefs(c).getBoolean("checkout_trial",false)&&LockLab.prefs(c).getLong("due",0)>System.currentTimeMillis()+120000,"Today checkout uses configured time and explicit real-attendance mode");
        long safeDue=java.time.LocalDate.now(Policy.ZONE).atTime(Policy.checkoutStartTime(checkoutTime)).atZone(Policy.ZONE).toInstant().toEpochMilli();
        main(()->AlarmScheduler.arm(c,false));
        check(LockLab.prefs(c).getLong("due",0)==safeDue&&prefs.raw.getLong("next_alarm",0)==safeDue,"Normal reservation and checkout trial both use the same five-second margin");
        check(!LockLab.readOnlyActive(c),"Future checkout reservation leaves normal attendance running");
        main(()->LockLab.fireCheckoutIfDue(c));
        check(!LockLab.prefs(c).getBoolean("fired",true),"Other attendance alarms cannot fire a future checkout test early");
        main(()->LockLab.prefs(c).edit().putLong("due",System.currentTimeMillis()-1).commit());
        check(LockLab.readOnlyActive(c),"Checkout test holds competing work only at its due time");
        main(()->LockLab.cancel(c));
        main(()->prefs.raw.edit().putBoolean("enabled",false).putBoolean("auto",false).commit());
        String current=prepare(LockTestPolicy.Mode.NETWORK,true,0);
        check(!LockLab.actual(c),"Network diagnostics cannot enable real attendance");
        main(()->LockLab.finish(c,actual,"STALE RESULT"));check(!LockLab.report(c).contains("STALE RESULT"),"Old callbacks cannot overwrite a new trial");
        main(()->LockLab.fire(c,current,"test network"));LockTestReceiver.diagnose(c,current);
        check(!LockLab.live(c,current)&&LockLab.report(c).contains("서버 진단 보류"),"Network diagnosis respects registered Wi-Fi and never claims API success");
        main(()->LockLab.verdict(c,"부분 성공","에뮬레이터 테스트 메모","MANUAL_UNLOCK"));prepare(LockTestPolicy.Mode.EXTEND,false,15);
        check(LockLab.allReports(c).contains("부분 성공")&&LockLab.allReports(c).contains("에뮬레이터 테스트 메모")&&LockLab.allReports(c).contains("직접 잠금을 해제함"),"Manual result and note survive the next trial");
        main(()->{LockLab.prefs(c).edit().putLong("expires",System.currentTimeMillis()-1).commit();LockLab.expire(c);});
        check(!LockLab.prefs(c).getBoolean("active",true)&&!LockLab.readOnlyActive(c),"Expired trials release the hold and remain stopped");
        prepare(LockTestPolicy.Mode.PLACE,false,15);main(()->LockLab.resetAfterRestart(c));
        check(!LockLab.readOnlyActive(c)&&!LockLab.prefs(c).getBoolean("active",true),"Reboot/update does not replay a pending trial");
        check(prefs.raw.getBoolean("enabled",true)==false && prefs.ssids().equals(new TreeSet<>(Set.of("__LOCK_LAB_NO_MATCH__"))),"Lab does not rewrite real attendance settings");
        main(()->{LockLab.prefs(c).edit().clear().commit();});
        NetworkAccess.State wifi=NetworkAccess.inspect(c);
        check(!wifi.allowed()&&!wifi.code().isEmpty(),"Wi-Fi diagnostics supply a reason code without treating unknown SSID as allowed");
        test.waitForIdleSync();SystemClock.sleep(1300);capture("lock-lab.png");
        restore();result.putString("stream","PASS: "+checks+" lock-lab lifecycle checks. No attendance submission or server request.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable error){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+error+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private String prepare(LockTestPolicy.Mode mode,boolean actual,int delay){String[] token={""};main(()->token[0]=LockLab.prepare(c,mode,actual,delay));return token[0];}
    private void main(Runnable run){Throwable[] failure={null};test.runOnMainSync(()->{try{run.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new AssertionError(failure[0]);}
    private void check(boolean passed,String label){if(!passed)throw new AssertionError(label);checks++;}
    private TextView find(View view,String label){if(view instanceof TextView&&((TextView)view).getText().toString().equals(label))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=find(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
    private void capture(String name)throws Exception{File dir=new File(c.getExternalFilesDir(null),"lock-screens");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Screenshot directory");android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();if(image==null)throw new IOException("Screenshot unavailable");try(FileOutputStream out=new FileOutputStream(new File(dir,name))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    @SuppressWarnings("unchecked")private void restorePrefs(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<>((Set<String>)v));}if(!e.commit())throw new AssertionError("Restore failed");}
    private void restore(){if(c==null||attendanceSaved==null)return;main(()->{LockLab.cancel(c);c.getSystemService(AlarmManager.class).cancel(LockLab.alarm(c,""));restorePrefs(new Prefs(c).raw,attendanceSaved);restorePrefs(LockLab.prefs(c),labSaved);c.getSystemService(NotificationManager.class).cancel(LockLab.NOTICE);if(activity!=null)activity.finish();AlarmScheduler.arm(c,false);});}
}