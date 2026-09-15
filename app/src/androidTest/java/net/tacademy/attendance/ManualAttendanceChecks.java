package net.tacademy.attendance;
import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
/** Emulator-only manual controls and lifecycle/deadline checks; all real site access is blocked. */
final class ManualAttendanceChecks {
    private final Instrumentation test;private Context c;private Activity a;private ChromeAttendanceService service;
    private final Map<String,Map<String,?>> saved=new HashMap<>();private int count;
    ManualAttendanceChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Build.HARDWARE.equals("ranchu")&&!Build.HARDWARE.equals("goldfish"))throw new AssertionError("Emulator only");
        c=test.getTargetContext();
        for(String name:new String[]{"attendance","attendance_access","attendance_diagnostics"})saved.put(name,new HashMap<>(c.getSharedPreferences(name,0).getAll()));
        Prefs p=new Prefs(c);main(()->{p.raw.edit().putBoolean("enabled",false).putBoolean("auto",false).putStringSet("ssids",Set.of("__MANUAL_NO_MATCH__")).remove("record_day").remove("in").remove("out").remove("record:"+LocalDate.now(Policy.ZONE)).remove("chrome_pending").commit();AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));});
        TrafficGuard.prefs(c).edit().putString("day",LocalDate.now(Policy.ZONE).toString()).putInt("total",AccessPolicy.DAILY_LIMIT).commit();
        UiAutomation ui=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        ui.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);try{
            String services=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,"");SystemClock.sleep(300);Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,services);
        }finally{ui.dropShellPermissionIdentity();}
        long until=SystemClock.elapsedRealtime()+8000;while(!ChromeAttendanceService.available()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
        Field instance=ChromeAttendanceService.class.getDeclaredField("instance");instance.setAccessible(true);service=(ChromeAttendanceService)instance.get(null);check(service!=null,"Accessibility connected for bounded lifecycle checks");
        a=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        View entry=a.getWindow().getDecorView().findViewWithTag("attendance-enter-now"),exit=a.getWindow().getDecorView().findViewWithTag("attendance-leave-now");
        check(entry!=null&&exit!=null,"Today exposes entry and checkout buttons");
        int opens=DiagnosticLog.records(c).toString().split("CHROME_OPEN_REQUEST",-1).length;
        main(()->{entry.performClick();exit.performClick();});
        check(DiagnosticLog.records(c).toString().split("CHROME_OPEN_REQUEST",-1).length==opens&&!ChromeAttendanceService.busy(),"Missing target Wi-Fi blocks both direct actions before Chrome opens");
        main(()->{p.raw.edit().putString("chrome_pending",LocalDate.now(Policy.ZONE)+":CHECKOUT").putLong("chrome_pending_at",1).commit();exit.performClick();});
        SystemClock.sleep(200);android.view.accessibility.AccessibilityNodeInfo root=ui.getRootInActiveWindow();String dialog=tree(root);
        check(dialog.contains("기록 먼저 확인")&&dialog.contains("미처리 확인"),"Pending click requires record verification or explicit user-confirmed retry");
        ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);SystemClock.sleep(250);
        main(()->{
            set("phase",Policy.Phase.CHECKOUT);set("day",LocalDate.now(Policy.ZONE).toString());set("started",SystemClock.elapsedRealtime()-33_452);set("working",true);set("clicked",true);set("manualSession",true);
            invoke("waitForResult");
        });
        long left=(long)get("resultDeadline")-SystemClock.elapsedRealtime();check(left>33_000&&left<=35_000,"Late accepted confirmation reserves a fresh result observation window");
        main(()->ScheduleController.reconcile(c));check(ChromeAttendanceService.busy(),"Reservation reconciliation outside its window preserves the manual session");
        main(()->{set("manualSession",false);p.enabled(true);p.raw.edit().putStringSet("days",Set.of()).commit();ScheduleController.reconcile(c);});
        check(ChromeAttendanceService.busy(),"Ending scheduled monitoring preserves observation after an accepted click");
        main(()->{p.enabled(false);ScheduleController.reconcile(c);});check(!ChromeAttendanceService.busy(),"Explicit reservation-off cancels automatic result observation");
        check(TrafficGuard.prefs(c).getInt("total",0)==AccessPolicy.DAILY_LIMIT&&DiagnosticLog.records(c).toString().split("CHROME_OPEN_REQUEST",-1).length==opens,"Manual checks never bypass the daily cap or contact attendance");
        main(()->{
            p.raw.edit().putStringSet("days",Set.of("1","2","3","4","5","6","7")).putStringSet("excluded",Set.of()).putString("start","00:00").putString("end","00:01").putString("checkout","00:02").putBoolean("auto",true).commit();
            renderSettings();a.getWindow().getDecorView().findViewWithTag("reservation-on").performClick();
        });
        check(ActivationRun.pending()&&p.enabled()&&p.auto(),"Reservation ON schedules one five-second activation and enables automatic clicking");
        int fired=countEvent("ACTIVATION_FIRED");
        main(()->AttendanceEngine.request(c,true,null));SystemClock.sleep(4000);
        check(ActivationRun.pending()&&countEvent("ACTIVATION_FIRED")==fired,"No early activation or competing monitor run before five seconds");
        long activationUntil=SystemClock.elapsedRealtime()+4000;while(ActivationRun.pending()&&SystemClock.elapsedRealtime()<activationUntil)SystemClock.sleep(100);
        check(!ActivationRun.pending()&&countEvent("ACTIVATION_FIRED")==fired+1&&!ChromeAttendanceService.busy(),"Five-second activation fires exactly once and still blocks missing classroom Wi-Fi");
        main(()->{renderSettings();a.getWindow().getDecorView().findViewWithTag("reservation-on").performClick();});
        check(ActivationRun.pending(),"Pressing ON again explicitly schedules a fresh activation");
        main(()->{renderSettings();a.getWindow().getDecorView().findViewWithTag("reservation-off").performClick();});
        SystemClock.sleep(5300);check(!ActivationRun.pending()&&!p.enabled()&&countEvent("ACTIVATION_FIRED")==fired+1,"OFF cancels the countdown without a delayed hidden run");
        check(DiagnosticLog.records(c).toString().split("CHROME_OPEN_REQUEST",-1).length==opens,"Activation checks never contact the attendance site");
        restore();result.putString("stream","PASS: "+count+" manual attendance and result-deadline checks. No real site access.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private int countEvent(String name){int result=0;var records=DiagnosticLog.records(c);for(int i=0;i<records.length();i++){var e=records.optJSONObject(i);if(e!=null&&name.equals(e.optString("event_code")))result++;}return result;}
    private void renderSettings(){try{var method=MainActivity.class.getDeclaredMethod("render",int.class);method.setAccessible(true);method.invoke(a,1);}catch(Exception e){throw new AssertionError(e);}}
    private String tree(android.view.accessibility.AccessibilityNodeInfo n){if(n==null)return "";StringBuilder s=new StringBuilder().append(n.getText()).append(' ');for(int i=0;i<n.getChildCount();i++)s.append(tree(n.getChild(i)));return s.toString();}
    private void set(String key,Object value){try{Field f=ChromeAttendanceService.class.getDeclaredField(key);f.setAccessible(true);f.set(service,value);}catch(Exception e){throw new AssertionError(e);}}
    private Object get(String key){try{Field f=ChromeAttendanceService.class.getDeclaredField(key);f.setAccessible(true);return f.get(service);}catch(Exception e){throw new AssertionError(e);}}
    private void invoke(String key){try{Method m=ChromeAttendanceService.class.getDeclaredMethod(key);m.setAccessible(true);m.invoke(service);}catch(Exception e){throw new AssertionError(e);}}
    private void main(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void check(boolean value,String text){if(!value)throw new AssertionError(text);count++;Bundle b=new Bundle();b.putString("stream","PASS: "+text+"\n");test.sendStatus(0,b);}
    @SuppressWarnings("unchecked")private void restore(){if(c==null)return;main(()->{ActivationRun.cancel();ChromeAttendanceService.cancelWork();if(a!=null)a.finish();for(var group:saved.entrySet()){SharedPreferences.Editor e=c.getSharedPreferences(group.getKey(),0).edit().clear();for(var item:group.getValue().entrySet()){String k=item.getKey();Object v=item.getValue();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<>((Set<String>)v));}e.commit();}AlarmScheduler.arm(c,false);});}
}
