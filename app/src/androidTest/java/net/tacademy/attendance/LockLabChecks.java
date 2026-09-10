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
        c=test.getTargetContext();Prefs prefs=new Prefs(c);attendanceSaved=new HashMap<>(prefs.raw.getAll());labSaved=new HashMap<>(LockLab.prefs(c).getAll());
        main(()->{prefs.raw.edit().putBoolean("enabled",false).putBoolean("auto",false).putStringSet("ssids",Set.of("__LOCK_LAB_NO_MATCH__")).commit();AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));LockLab.prefs(c).edit().clear().commit();});
        activity=test.startActivitySync(new Intent(c,LockLabActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
        check(find(activity.getWindow().getDecorView(),"잠금 상태 테스트")!=null,"Temporary lab opens");
        String token=prepare(LockTestPolicy.Mode.WAKE,false,15);
        check(LockLab.live(c,token)&&LockLab.readOnlyActive(c),"Scheduled trial holds attendance without changing saved automatic settings");
        main(()->LockLab.cancel(c));check(!LockLab.live(c,token)&&!LockLab.readOnlyActive(c),"Cancel releases the hold");
        String afterCancel=LockLab.report(c);main(()->new LockTestReceiver().onReceive(c,new Intent(c,LockTestReceiver.class).putExtra(LockLab.TOKEN,token)));
        check(afterCancel.equals(LockLab.report(c)),"Cancelled alarm cannot restart a trial");
        String actual=prepare(LockTestPolicy.Mode.AUTH,true,15);
        check(LockLab.actual(c)&&LockLab.readOnlyActive(c),"Actual-attendance opt-in still holds work until unlock");
        main(()->LockLab.cancel(c));
        String current=prepare(LockTestPolicy.Mode.NETWORK,true,0);
        check(!LockLab.actual(c),"Network diagnostics cannot enable real attendance");
        main(()->LockLab.finish(c,actual,"STALE RESULT"));check(!LockLab.report(c).contains("STALE RESULT"),"Old callbacks cannot overwrite a new trial");
        main(()->LockLab.fire(c,current,"test network"));LockTestReceiver.diagnose(c,current);
        check(!LockLab.live(c,current)&&LockLab.report(c).contains("서버 진단 보류"),"Network diagnosis respects registered Wi-Fi and never claims API success");
        main(()->LockLab.verdict(c,"부분 성공","에뮬레이터 테스트 메모"));prepare(LockTestPolicy.Mode.EXTEND,false,15);
        check(LockLab.allReports(c).contains("부분 성공")&&LockLab.allReports(c).contains("에뮬레이터 테스트 메모"),"Manual result and note survive the next trial");
        main(()->{LockLab.prefs(c).edit().putLong("expires",System.currentTimeMillis()-1).commit();LockLab.expire(c);});
        check(!LockLab.prefs(c).getBoolean("active",true)&&!LockLab.readOnlyActive(c),"Expired trials release the hold and remain stopped");
        prepare(LockTestPolicy.Mode.PLACE,false,15);main(()->LockLab.resetAfterRestart(c));
        check(!LockLab.readOnlyActive(c)&&!LockLab.prefs(c).getBoolean("active",true),"Reboot/update does not replay a pending trial");
        check(prefs.raw.getBoolean("enabled",true)==false && prefs.ssids().equals(new TreeSet<>(Set.of("__LOCK_LAB_NO_MATCH__"))),"Lab does not rewrite real attendance settings");
        main(()->{LockLab.prefs(c).edit().clear().commit();});test.waitForIdleSync();SystemClock.sleep(1300);capture("lock-lab.png");
        restore();result.putString("stream","PASS: "+checks+" lock-lab lifecycle checks. No attendance submission or server request.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable error){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+error+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private String prepare(LockTestPolicy.Mode mode,boolean actual,int delay){String[] token={""};main(()->token[0]=LockLab.prepare(c,mode,actual,delay));return token[0];}
    private void main(Runnable run){test.runOnMainSync(run);}
    private void check(boolean passed,String label){if(!passed)throw new AssertionError(label);checks++;}
    private TextView find(View view,String label){if(view instanceof TextView&&((TextView)view).getText().toString().equals(label))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=find(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
    private void capture(String name)throws Exception{File dir=new File(c.getExternalFilesDir(null),"lock-screens");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Screenshot directory");android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();if(image==null)throw new IOException("Screenshot unavailable");try(FileOutputStream out=new FileOutputStream(new File(dir,name))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    @SuppressWarnings("unchecked")private void restorePrefs(SharedPreferences p,Map<String,?> values){SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<>((Set<String>)v));}if(!e.commit())throw new AssertionError("Restore failed");}
    private void restore(){if(c==null||attendanceSaved==null)return;main(()->{LockLab.cancel(c);c.getSystemService(AlarmManager.class).cancel(LockLab.alarm(c,""));restorePrefs(new Prefs(c).raw,attendanceSaved);restorePrefs(LockLab.prefs(c),labSaved);c.getSystemService(NotificationManager.class).cancel(LockLab.NOTICE);if(activity!=null)activity.finish();AlarmScheduler.arm(c,false);});}
}