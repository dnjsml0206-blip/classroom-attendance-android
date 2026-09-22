package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import java.io.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import org.json.*;

/** Emulator-only real Wi-Fi callbacks and admission regression. A persisted cap blocks every site launch. */
final class NetworkArrivalChecks {
    private final Instrumentation test;
    private Context c;
    private Activity activity;
    private UiAutomation ui;
    private ChromeAttendanceService chrome;
    private boolean wifiWasOn;
    private int checks;
    private final Map<String,Map<String,?>> saved = new HashMap<>();
    NetworkArrivalChecks(Instrumentation test) { this.test=test; }
    void run() {
        Bundle result = new Bundle();
        try {
            if (!Set.of("ranchu","goldfish").contains(Build.HARDWARE)) throw new AssertionError("Emulator only");
            c=test.getTargetContext(); ui=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            wifiWasOn=c.getSystemService(WifiManager.class).isWifiEnabled();
            for (String name:new String[]{"attendance","attendance_access","attendance_diagnostics","scheduled_unlock","lock_lab"})
                saved.put(name,new HashMap<>(c.getSharedPreferences(name,0).getAll()));
            DiagnosticLog.prefs(c).edit().clear().commit();
            Prefs p=new Prefs(c);
            main(()->{
                p.raw.edit().putBoolean("enabled",false).putBoolean("auto",false).commit();
                ActivationRun.cancel(); ScheduledUnlock.cancel(c,"EMULATOR_CHECK");
                AlarmScheduler.cancel(c); c.stopService(new Intent(c,MonitorService.class));
                TrafficGuard.prefs(c).edit().clear().putString("day",LocalDate.now(Policy.ZONE).toString())
                    .putInt("total",AccessPolicy.DAILY_LIMIT).commit();
            });
            activity=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            // Instrumentation restarts the target process; reconnect the real accessibility service.
            ui.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);
            try {
                String services=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,"");
                SystemClock.sleep(300);
                Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,services);
            } finally { ui.dropShellPermissionIdentity(); }
            await(ChromeAttendanceService::available,10_000,"Accessibility reconnects after instrumentation restart");
            Field instance=ChromeAttendanceService.class.getDeclaredField("instance"); instance.setAccessible(true);
            chrome=(ChromeAttendanceService)instance.get(null);
            check(chrome!=null,"Accessibility is connected; the persisted daily cap blocks Chrome");
            ZonedDateTime now=ZonedDateTime.now(Policy.ZONE);
            if(now.getHour()<1||now.getHour()>=23)throw new AssertionError("Run between 01:00 and 23:00 Korea time");
            shell("svc wifi disable"); awaitWifi(false);
            main(()->configureEntry(p));
            request(false);
            check(events("AUTOMATIC_TRIGGER")==0,"A disconnected check never starts an attendance attempt");
            long disconnectedAt=SystemClock.elapsedRealtime();
            shell("svc wifi enable"); awaitWifi(true);
            check(SystemClock.elapsedRealtime()-disconnectedAt<55_000,"Wi-Fi became ready inside the old 60-second delay");
            request(false);
            check(events("AUTOMATIC_TRIGGER")==1,"First ready Wi-Fi starts immediately after a disconnected check");
            check(events("ACCESS_BLOCKED")>0&&events("CHROME_OPEN_REQUEST")==0,"The real Chrome entry point still honors the daily navigation cap");
            for(int i=0;i<8;i++)request(false);
            check(events("AUTOMATIC_TRIGGER")==1,"Repeated callbacks do not repeat an admitted UI attempt");
            main(()->p.raw.edit().putString("end","00:01").putString("checkout",now.minusMinutes(1).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
                .putInt("checkout_retry_minutes",5).commit());
            request(false);
            check(events("AUTOMATIC_TRIGGER")==2,"A previous entry check cannot delay a new checkout window");
            main(()->p.raw.edit().putBoolean("enabled",false).commit()); request(true);
            check(events("AUTOMATIC_TRIGGER")==2,"Disabled reservations do not react to a ready network");
            main(()->{configureEntry(p);p.raw.edit().putStringSet("excluded",Set.of(now.toLocalDate().toString())).commit();}); request(true);
            check(events("AUTOMATIC_TRIGGER")==2,"A holiday cannot start automatic attendance");
            main(()->{configureEntry(p);setChrome("settingsTarget",PhoneSettings.Target.WIFI);}); request(false);
            check(events("AUTOMATIC_TRIGGER")==2,"An in-progress Wi-Fi helper does not consume the first attendance opportunity");
            main(()->invokeEndSettings());
            await(()->events("AUTOMATIC_TRIGGER")==3,3000,"Wi-Fi helper completion resumes attendance immediately");
            check(true,"Wi-Fi helper completion resumes without waiting for the 30-second monitor loop");
            shell("svc wifi disable"); awaitWifi(false);
            main(()->{
                configureEntry(p);resetReadWindow();
                c.startForegroundService(new Intent(c,MonitorService.class));
            });
            await(()->MonitorService.running&&latestState("NO_WIFI_TRANSPORT")!=null,5000,"Monitor starts disconnected");
            long before=events("AUTOMATIC_TRIGGER");
            shell("svc wifi enable");
            await(()->latestState("ALLOWED")!=null&&events("AUTOMATIC_TRIGGER")>before,55_000,"Real Wi-Fi callback starts attendance");
            JSONObject state=latestState("ALLOWED"),details=state.getJSONObject("details");
            JSONObject trigger=latest("AUTOMATIC_TRIGGER");
            long handoff=trigger.getLong("occurred_at_ms")-state.getLong("occurred_at_ms");
            check(!"LOCAL_CHECK".equals(details.optString("network_trigger")),"Real Wi-Fi readiness was detected by a network callback, not periodic polling");
            check(handoff>=0&&handoff<1500,"Readiness-to-attendance handoff is below 1.5 seconds (observed "+handoff+" ms)");
            check(details.has("network_event_at_ms")&&details.optLong("elapsed_ms",-1)>=0,"Callback receipt and local readiness timing are retained for next-device diagnosis");
            SystemClock.sleep(3500);
            check(events("AUTOMATIC_TRIGGER")==before+1,"Wi-Fi/default-route callback bursts produce one handoff");
            check(events("CHROME_OPEN_REQUEST")==0&&TrafficGuard.prefs(c).getInt("total",0)==AccessPolicy.DAILY_LIMIT,"No site navigation or budget reset occurred during connection tests");
            main(()->{p.record(now.toLocalDate().toString(),new Policy.Record("08:45:00",""));ScheduleController.reconcile(c);});
            await(()->!MonitorService.running,5000,"Confirmed entry stops monitor");
            int completed=events("AUTOMATIC_TRIGGER"),states=events("NETWORK_STATE_CHANGED");
            shell("svc wifi disable");SystemClock.sleep(1500);request(true);
            check(events("AUTOMATIC_TRIGGER")==completed&&events("NETWORK_STATE_CHANGED")==states,"Entry completion unregisters network callbacks and prevents further handoffs");
            restore();result.putString("stream","PASS: "+checks+" Wi-Fi arrival checks. No attendance site was opened.\n");
            test.finish(Activity.RESULT_OK,result);
        } catch(Throwable e) {
            try{restore();}catch(Throwable ignored){}
            result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);
        }
    }
    private void configureEntry(Prefs p) {
        p.raw.edit().putBoolean("enabled",true).putBoolean("auto",true).putStringSet("ssids",Set.of("AndroidWifi"))
            .putStringSet("days",Set.of("1","2","3","4","5","6","7")).putStringSet("excluded",Set.of())
            .putString("start","00:00").putString("end","23:50").putString("checkout","23:51").putInt("checkout_retry_minutes",1)
            .remove("record_day").remove("in").remove("out").remove("chrome_pending").commit();
    }
    private void request(boolean force){main(()->AttendanceEngine.request(c,force,null));}
    private void resetReadWindow(){try{Field f=AttendanceEngine.class.getDeclaredField("readWindow");f.setAccessible(true);f.set(null,"");}catch(Exception e){throw new AssertionError(e);}}
    private void setChrome(String key,Object value){try{Field f=ChromeAttendanceService.class.getDeclaredField(key);f.setAccessible(true);f.set(chrome,value);}catch(Exception e){throw new AssertionError(e);}}
    private void invokeEndSettings(){try{Method m=ChromeAttendanceService.class.getDeclaredMethod("endSettingsMenu",boolean.class);m.setAccessible(true);m.invoke(chrome,false);}catch(Exception e){throw new AssertionError(e);}}
    private void awaitWifi(boolean ready){await(()->ready?NetworkAccess.inspect(c).allowed():!NetworkAccess.inspect(c).wifiTransportPresent(),55_000,"Wi-Fi ready="+ready);}
    private void await(java.util.function.BooleanSupplier condition,long millis,String message){long end=SystemClock.elapsedRealtime()+millis;while(!condition.getAsBoolean()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(50);if(!condition.getAsBoolean())throw new AssertionError(message);}
    private int events(String event){int n=0;JSONArray a=DiagnosticLog.records(c);for(int i=0;i<a.length();i++)if(event.equals(a.optJSONObject(i).optString("event_code")))n++;return n;}
    private JSONObject latest(String event){JSONArray a=DiagnosticLog.records(c);for(int i=a.length()-1;i>=0;i--)if(event.equals(a.optJSONObject(i).optString("event_code")))return a.optJSONObject(i);return null;}
    private JSONObject latestState(String code){JSONArray a=DiagnosticLog.records(c);for(int i=a.length()-1;i>=0;i--){JSONObject r=a.optJSONObject(i);if("NETWORK_STATE_CHANGED".equals(r.optString("event_code"))&&code.equals(r.optString("reason_code")))return r;}return null;}
    private void shell(String command){try(ParcelFileDescriptor fd=ui.executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){in.readAllBytes();}catch(IOException e){throw new AssertionError(e);}}
    private void main(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;Bundle b=new Bundle();b.putString("stream","PASS: "+message+"\n");test.sendStatus(0,b);}
    @SuppressWarnings("unchecked") private void restore(){
        if(c==null)return;
        main(()->{new Prefs(c).raw.edit().putBoolean("enabled",false).putBoolean("auto",false).commit();AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));if(chrome!=null)setChrome("settingsTarget",null);if(activity!=null)activity.finish();});
        await(()->!MonitorService.running,5000,"Monitor cleanup");
        if(ui!=null)shell("svc wifi "+(wifiWasOn?"enable":"disable"));
        main(()->{for(var group:saved.entrySet()){
            SharedPreferences.Editor e=c.getSharedPreferences(group.getKey(),0).edit().clear();
            for(var entry:group.getValue().entrySet()){String k=entry.getKey();Object v=entry.getValue();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<>((Set<String>)v));}e.commit();
        }AlarmScheduler.arm(c,false);});
    }
}
