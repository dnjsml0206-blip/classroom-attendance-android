package net.tacademy.attendance;
import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import java.io.*;
import java.time.*;
import java.util.*;
import org.json.*;

/** Real emulator USER_PRESENT delivery with a full persisted cap: no attendance navigation. */
final class ReadinessChecks {
    private final Instrumentation test;private Context c;private Activity activity;private UiAutomation automation;
    private int count;private final Map<String,Map<String,?>> saved=new HashMap<>();
    ReadinessChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Set.of("ranchu","goldfish").contains(Build.HARDWARE))throw new AssertionError("Emulator only");
        c=test.getTargetContext();automation=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        for(String name:List.of("attendance","attendance_access","attendance_diagnostics","scheduled_unlock","lock_lab"))saved.put(name,new HashMap<>(c.getSharedPreferences(name,0).getAll()));
        main(()->{new Prefs(c).raw.edit().putBoolean("enabled",false).putBoolean("auto",false).commit();ActivationRun.cancel();ScheduledUnlock.cancel(c,"TEST");AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));DiagnosticLog.prefs(c).edit().clear().commit();TrafficGuard.prefs(c).edit().clear().putString("day",LocalDate.now(Policy.ZONE).toString()).putInt("total",AccessPolicy.DAILY_LIMIT).commit();});
        activity=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        automation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        try{String enabled=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,"");SystemClock.sleep(300);Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,enabled);}finally{automation.dropShellPermissionIdentity();}
        await(ChromeAttendanceService::available,10000,"Accessibility connects");
        check(!c.getSystemService(KeyguardManager.class).isDeviceSecure(),"Synthetic screen-unlock test uses no PIN");
        await(()->!NetworkAccess.inspect(c).ssid().isBlank(),30000,"Emulator Wi-Fi finishes connecting before readiness checks");
        String ssid=NetworkAccess.inspect(c).ssid();check(!ssid.isBlank(),"Emulator Wi-Fi identity is readable");
        main(()->{new Prefs(c).raw.edit().putBoolean("enabled",true).putBoolean("auto",true).putStringSet("ssids",Set.of(ssid)).putStringSet("days",Set.of("1","2","3","4","5","6","7")).putStringSet("excluded",Set.of()).putString("start","00:00").putString("end","23:50").putString("checkout","23:51").putInt("checkout_retry_minutes",1).remove("record_day").remove("in").remove("out").remove("chrome_pending").commit();if(new Prefs(c).phase(ZonedDateTime.now(Policy.ZONE))!=Policy.Phase.ENTRY)throw new AssertionError("Synthetic entry window must be valid before starting monitor");c.startForegroundService(new Intent(c,MonitorService.class));});
        await(()->MonitorService.running&&events("AUTOMATIC_TRIGGER")>0,10000,"Monitor detects ready Wi-Fi");
        check(events("CHROME_OPEN_REQUEST")==0,"Persisted daily cap blocks attendance site before any UI action");
        SystemClock.sleep(1500);int attempts=events("AUTOMATIC_TRIGGER");int presence=events("USER_PRESENT_RECEIVED");
        turnOff();shell("input keyevent 224");SystemClock.sleep(500);shell("wm dismiss-keyguard");shell("input keyevent 82");
        await(()->events("USER_PRESENT_RECEIVED")>presence,10000,"Actual Android USER_PRESENT reaches the monitor");
        await(()->events("AUTOMATIC_TRIGGER")>attempts,3000,"Authentication readiness bypasses polling cooldown");
        check(events("READINESS_RESUMED")>=2,"Wi-Fi readiness and unlock readiness have distinct evidence");
        long received=last("USER_PRESENT_RECEIVED").getLong("occurred_at_ms"),trigger=last("AUTOMATIC_TRIGGER").getLong("occurred_at_ms");
        check(trigger>=received&&trigger-received<1500,"Real unlock-to-attendance handoff under 1.5 seconds: "+(trigger-received)+" ms");
        check(events("CHROME_OPEN_REQUEST")==0&&TrafficGuard.prefs(c).getInt("total",0)==AccessPolicy.DAILY_LIMIT,"Fast resume preserves navigation cap and sends no attendance request");
        main(()->{new Prefs(c).record(LocalDate.now(Policy.ZONE).toString(),new Policy.Record("08:45", ""));ScheduleController.reconcile(c);});
        await(()->!MonitorService.running,5000,"Completed entry stops monitoring");
        int after=events("USER_PRESENT_RECEIVED");turnOff();shell("input keyevent 224");SystemClock.sleep(500);shell("wm dismiss-keyguard");shell("input keyevent 82");SystemClock.sleep(1200);
        check(events("USER_PRESENT_RECEIVED")==after,"Completion unregisters the unlock receiver; no all-day monitoring");
        restore();result.putString("stream","PASS: "+count+" readiness integration checks. No attendance site requests.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){if(c!=null)result.putString("diagnostic_events",DiagnosticLog.records(c).toString());try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private void turnOff(){shell("input keyevent 223");await(()->!c.getSystemService(PowerManager.class).isInteractive(),8000,"Screen becomes non-interactive");}
    private int events(String name){int n=0;JSONArray a=DiagnosticLog.records(c);for(int i=0;i<a.length();i++)if(name.equals(a.optJSONObject(i).optString("event_code")))n++;return n;}
    private JSONObject last(String name){JSONArray a=DiagnosticLog.records(c);for(int i=a.length()-1;i>=0;i--)if(name.equals(a.optJSONObject(i).optString("event_code")))return a.optJSONObject(i);throw new AssertionError(name);}
    private void await(java.util.function.BooleanSupplier condition,long ms,String message){long end=SystemClock.elapsedRealtime()+ms;while(!condition.getAsBoolean()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);check(condition.getAsBoolean(),message);}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);count++;Bundle b=new Bundle();b.putString("stream","PASS: "+message+"\n");test.sendStatus(0,b);}
    private void main(Runnable task){Throwable[] error={null};test.runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void shell(String command){try(ParcelFileDescriptor fd=automation.executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){in.readAllBytes();}catch(IOException e){throw new AssertionError(e);}}
    @SuppressWarnings("unchecked") private void restore(){if(c==null)return;main(()->{new Prefs(c).raw.edit().putBoolean("enabled",false).putBoolean("auto",false).commit();ScheduledUnlock.cancel(c,"TEST_END");AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));if(activity!=null)activity.finish();});long end=SystemClock.elapsedRealtime()+5000;while(MonitorService.running&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);main(()->{for(var group:saved.entrySet()){var edit=c.getSharedPreferences(group.getKey(),0).edit().clear();for(var entry:group.getValue().entrySet()){String k=entry.getKey();Object v=entry.getValue();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));}edit.commit();}AlarmScheduler.arm(c,false);});}
}
