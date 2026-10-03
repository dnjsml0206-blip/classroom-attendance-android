package net.tacademy.attendance;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.time.*;
final class LockdownChecks {
    private final Instrumentation test;private Context c;private Prefs p;private Activity activity;private Map<String,?> saved;private int count;
    LockdownChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Set.of("ranchu","goldfish").contains(Build.HARDWARE))throw new AssertionError("Emulator only");
        test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        c=test.getTargetContext();p=new Prefs(c);saved=new HashMap<>(p.raw.getAll());
        ui(()->{ChromeAttendanceService.cancelWork();AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));p.raw.edit().clear().putBoolean("initialized",true).putInt("schedule_version",2).putBoolean("chrome_backend",true).putBoolean(PersonalAttendanceCorrection.MARKER,true).putBoolean("enabled",false).putBoolean("auto",false).putBoolean("guidance_notifications",false).putStringSet("days",Set.of("1","2","3","4","5")).putString("chrome_pending","lockdown-sentinel").commit();DiagnosticLog.prefs(c).edit().clear().commit();});
        check(c.getSystemService(KeyguardManager.class).isDeviceSecure(),"Dedicated emulator has a test PIN; no physical phone involved");
        activity=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
        ui(()->{try{var render=MainActivity.class.getDeclaredMethod("render",int.class);render.setAccessible(true);render.invoke(activity,1);}catch(Exception e){throw new RuntimeException(e);}});settle();
        check(view("attendance-lockdown")!=null&&((Switch)view("attendance-lockdown")).isChecked(),"Approved post-attendance lockdown option is available and enabled");
        ui(()->view("attendance-lockdown").requestRectangleOnScreen(new android.graphics.Rect(0,0,view("attendance-lockdown").getWidth(),500),true));settle();capture("lockdown-settings.png");
        // Instrumentation restarts this process; explicitly rebind only in the guarded emulator test.
        UiAutomation automation=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        automation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        try{
            String services=android.provider.Settings.Secure.getString(c.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            android.provider.Settings.Secure.putString(c.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,"");SystemClock.sleep(300);
            android.provider.Settings.Secure.putString(c.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,services);
        }finally{automation.dropShellPermissionIdentity();}
        long until=SystemClock.elapsedRealtime()+8000;while(!ChromeAttendanceService.available()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
        check(ChromeAttendanceService.available(),"Production accessibility service is connected");
        boolean[] started={false};ui(()->started[0]=ChromeAttendanceService.testLockdown(activity));check(started[0],"Real system power menu test starts without an attendance request");
        until=SystemClock.elapsedRealtime()+17000;while(Set.of("","LOCKDOWN_RUNNING").contains(p.raw.getString("lockdown_last_result",""))&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
        String outcome=p.raw.getString("lockdown_last_result","");check(outcome.equals("LOCKDOWN_LOCK_OBSERVED"),"Native lockdown result: "+outcome);
        until=SystemClock.elapsedRealtime()+AttendanceScreen.SCREEN_OFF_TIMEOUT_MS+2000;
        while((ChromeAttendanceService.busy()||p.raw.getString("screen_off_last_result","").isEmpty())&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
        check(c.getSystemService(KeyguardManager.class).isDeviceLocked()&&!c.getSystemService(PowerManager.class).isInteractive(),"Actual system screen is off and device authentication is required");
        var events=DiagnosticLog.records(c);int requests=0,clicks=0,site=0;boolean stateLogged=false;
        for(int i=0;i<events.length();i++){var e=events.getJSONObject(i);if(e.optString("event_code").equals("LOCKDOWN_REQUEST"))requests++;if(e.optString("reason_code").equals("LOCKDOWN_CLICK_ACCEPTED"))clicks++;if(Set.of("CHROME_OPEN_REQUEST","BUTTON_REQUEST","CONFIRM_REQUEST").contains(e.optString("event_code")))site++;if(e.optString("reason_code").equals("LOCKDOWN_LOCK_OBSERVED"))stateLogged=e.getJSONObject("details").getBoolean("device_locked")&&e.getJSONObject("details").getBoolean("device_secure");}
        check(requests==1&&clicks==1&&site==0,"Power menu and lockdown are requested once; zero attendance page/button requests");
        check(stateLogged,"Diagnostic log preserves actual secure lock observations");
        var evidence=new org.json.JSONObject(p.raw.getString("lockdown_last_evidence","{}"));
        check(evidence.optInt("lock_menu_count")==1&&evidence.optInt("lock_candidate_count")==1&&evidence.optInt("lock_label_count")>0,
            "Menu, lockdown label and exactly one click target are recorded separately");
        check(evidence.optString("lock_root_source").equals("SYSTEM_WINDOWS")&&!evidence.optBoolean("lock_tree_incomplete",true),
            "Native power-menu window is fully read independently of the active application root");
        check(p.raw.getString("screen_off_last_result","").equals("SCREEN_OFF_OBSERVED")||p.raw.getString("screen_off_last_result","").equals("ALREADY_OFF"),
            "Screen-off result is recorded separately from secure locking");
        check(!ChromeAttendanceService.busy()&&p.raw.getString("chrome_pending","").equals("lockdown-sentinel"),"Bounded locking finishes and preserves attendance pending markers");
        restore();result.putString("stream","PASS: "+count+" native lockdown checks.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private void close(){if(activity!=null){ui(activity::finish);activity=null;settle();}}
    private View view(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
    private void settle(){SystemClock.sleep(450);test.waitForIdleSync();}
    private void ui(Runnable r){Throwable[] error={null};test.runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);count++;Bundle b=new Bundle();b.putString("stream","PASS: "+msg+"\n");test.sendStatus(0,b);}
    private void capture(String name)throws Exception{SystemClock.sleep(300);Bitmap b=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();if(b==null)throw new AssertionError("Screenshot unavailable");File dir=new File(c.getExternalFilesDir(null),"lockdown-screens");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @SuppressWarnings("unchecked")private void restore(){close();if(c==null||saved==null)return;ui(()->{var edit=p.raw.edit().clear();for(var item:saved.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));}edit.commit();AlarmScheduler.arm(c,false);});}
}
