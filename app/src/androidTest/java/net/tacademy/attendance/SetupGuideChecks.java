package net.tacademy.attendance;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Isolated UI checks with reservations disabled; never opens a real attendance page. */
final class SetupGuideChecks {
    private final Instrumentation test;private Context c;private Prefs prefs;private Activity activity;private Map<String,?> saved;private int count;
    SetupGuideChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Set.of("ranchu","goldfish").contains(Build.HARDWARE))throw new AssertionError("Emulator only");
        test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        c=test.getTargetContext();prefs=new Prefs(c);saved=new HashMap<>(prefs.raw.getAll());
        ui(()->{ChromeAttendanceService.cancelWork();AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));prefs.raw.edit().clear().putBoolean("initialized",true).putInt("schedule_version",2).putBoolean("chrome_backend",true).putBoolean(PersonalAttendanceCorrection.MARKER,true).putBoolean("enabled",false).putBoolean("auto",false).putStringSet("ssids",Set.of("TACADEMY_5G")).putStringSet("days",Set.of("1","2","3","4","5")).commit();});
        open(MainActivity.class,new Intent());check(view("setup-guide-home")!=null,"First-use Today has an obvious guide entry without enabling attendance");
        close();open(MainActivity.class,new Intent().putExtra("open_tab",2));check(view("setup-guide-open")!=null,"Automatic attendance places the guide before ordinary configuration");capture("settings-entry.png");
        Instrumentation.ActivityMonitor monitor=test.addMonitor(SetupGuideActivity.class.getName(),null,false);click("setup-guide-open");Activity guide=test.waitForMonitorWithTimeout(monitor,5000);test.removeMonitor(monitor);check(guide!=null,"Automatic attendance guide button opens the real activity");Activity parent=activity;activity=guide;
        check(view("setup-quick-primary")!=null&&view("setup-next")==null,"The initial screen offers one quick-start action instead of five Next steps");capture("quick-start.png");
        check(!prefs.enabled()&&!prefs.auto(),"Opening quick setup preserves reservation and automatic-click switches");
        ui(()->prefs.ssids(Set.of()));click("setup-quick-primary");
        check(value("setup-title").contains("Wi-Fi")&&view("setup-quick-primary")!=null,"Quick start selects only the missing Wi-Fi registration action");capture("quick-needed.png");
        check(prefs.ssids().isEmpty(),"Quick setup never silently replaces an empty or custom Wi-Fi list");
        check(!prefs.raw.getBoolean(SetupGuideActivity.CHROME,false)&&!prefs.raw.getBoolean(SetupGuideActivity.EXTEND,false),"Quick start does not invent Chrome or Extend Unlock confirmation");
        ui(()->prefs.ssids(Set.of("TACADEMY_5G")));click("setup-details");
        check(value("setup-title").contains("Chrome")&&view("setup-quick-return")!=null,"The detailed guide remains available from quick setup");capture("step-1.png");
        check(!prefs.raw.getBoolean(SetupGuideActivity.CHROME,false),"Viewing a page never marks Chrome authentication as verified");
        click(SetupGuideActivity.CHROME);check(prefs.raw.getBoolean(SetupGuideActivity.CHROME,false),"Only the user's checkbox records Chrome self-confirmation");
        click("setup-next");check(value("setup-title").contains("자동 클릭"),"Next advances one step");capture("step-2.png");
        click("setup-next");check(value("setup-title").contains("권한")&&view("setup-notifications")!=null,"Location, alarm and in-app notifications are grouped in one permission step");capture("step-3.png");
        click("setup-next");check(view("setup-extend-open")!=null&&view(SetupGuideActivity.EXTEND)!=null,"Extend Unlock has a direct setting action and separate user confirmation");
        check(!prefs.raw.getBoolean(SetupGuideActivity.EXTEND,false),"No implicit Extend Unlock success when entering its step");
        if(!c.getSystemService(KeyguardManager.class).isDeviceSecure())check(!view(SetupGuideActivity.EXTEND).isEnabled(),"Trust confirmation requires a secure system lock");
        capture("step-4.png");
        close();open(SetupGuideActivity.class,new Intent());check(view("setup-quick-primary")!=null,"Reopening begins safely at quick setup without launching settings automatically");click("setup-details");check(value("setup-title").contains("잠금"),"Detailed guide resumes at its saved step after closing and reopening");
        click("setup-next");check(value("setup-title").contains("최종"),"Last step shows final review");capture("step-5.png");
        click("setup-next");check(value("setup-title").contains("자동 클릭")||value("setup-title").contains("권한")||value("setup-title").contains("잠금"),"Missing requirements route to the first unfinished step instead of starting attendance");
        check(!prefs.enabled()&&!prefs.auto()&&!MonitorService.running&&!ChromeAttendanceService.busy(),"Browsing every step does not enable reservations, click attendance or run monitoring");
        close();ui(parent::finish);restore();result.putString("stream","PASS: "+count+" setup-guide UI checks. No attendance request performed.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private void open(Class<? extends Activity> type,Intent extras){activity=test.startActivitySync(new Intent(c,type).putExtras(extras).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();}
    private void close(){if(activity!=null){ui(activity::finish);activity=null;settle();}}
    private View view(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
    private String value(String tag){return ((TextView)view(tag)).getText().toString();}
    private void click(String tag){ui(()->view(tag).performClick());settle();}
    private void ui(Runnable r){Throwable[] error={null};test.runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void settle(){SystemClock.sleep(400);test.waitForIdleSync();}
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);count++;Bundle b=new Bundle();b.putString("stream","PASS: "+text+"\n");test.sendStatus(0,b);}
    private void capture(String name)throws Exception{Bitmap b=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();if(b==null)throw new AssertionError("No screenshot");File dir=new File(c.getExternalFilesDir(null),"setup-screens");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @SuppressWarnings("unchecked")private void restore(){close();if(c==null||saved==null)return;ui(()->{var edit=prefs.raw.edit().clear();for(var e:saved.entrySet()){Object v=e.getValue();String k=e.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));}edit.commit();AlarmScheduler.arm(c,false);});}
}
