package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.*;
import java.util.*;
import org.json.*;

/** Emulator-only integration checks. Unregistered Wi-Fi and a full access cap prevent attendance HTTP. */
final class WakeChecks {
    private final Instrumentation test;private Context c;private MainActivity main;private Activity lab;
    private final Map<String,Map<String,?>> saved=new HashMap<>();private int checks;
    WakeChecks(Instrumentation test){this.test=test;}
    void run(){Bundle out=new Bundle();try{
        if(!Build.HARDWARE.equals("ranchu")&&!Build.HARDWARE.equals("goldfish"))throw new AssertionError("Emulator only");
        c=test.getTargetContext();
        for(String name:new String[]{"attendance","attendance_access","attendance_diagnostics","scheduled_unlock","creator_tools","lock_lab"}){
            SharedPreferences prefs=c.getSharedPreferences(name,0);saved.put(name,new HashMap<>(prefs.getAll()));prefs.edit().clear().commit();
        }
        Prefs p=new Prefs(c);configure(false);
        main=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).putExtra("open_tab",1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        check(ScheduledUnlock.mode(c)==UnlockPolicy.Mode.NOTICE,"Updating starts with the existing notification-only behavior");
        View mode=main.getWindow().getDecorView().findViewWithTag("TRUST");
        check(mode instanceof RadioButton,"Normal lock settings are available in this build");
        ui(mode::performClick);
        check(ScheduledUnlock.mode(c)==UnlockPolicy.Mode.TRUST,"Selecting trusted unlock persists the normal reservation mode");
        check(checkedRadios((RadioGroup)mode.getParent())==1,"Normal unlock settings show exactly one selected mode");
        check(((TextView)find(main.getWindow().getDecorView(),"선택됨 · Extend Unlock · 신뢰 상태")).getText().length()>0,"Settings immediately show the saved choice");
        scrollToTag(main,"scheduled-unlock-settings");snap("scheduled-unlock-settings.png");
        check(!p.auto()&&!p.enabled(),"Selecting a wake mode does not silently enable reservation or automatic clicking");
        android.content.pm.ActivityInfo info=c.getPackageManager().getActivityInfo(new ComponentName(c,ScheduledUnlockActivity.class),0);
        check(info.enabled&&!info.exported,"Normal wake activity is enabled and private in creator and public builds");
        if(CreatorTools.enabled()){
            lab=test.startActivitySync(new Intent(c,LockLabActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check(!find(lab.getWindow().getDecorView(),"실행 목적 선택 후 시험").isEnabled(),"No trial action is enabled until a purpose is selected");
            ui(()->find(lab.getWindow().getDecorView(),"Chrome 열기만 시험 · 출결 안 함").performClick());
            check(find(lab.getWindow().getDecorView(),"지금 Chrome 열기만 · 출결 안 함").isEnabled(),"Preview selection explicitly labels that no attendance is performed");
            ui(()->find(lab.getWindow().getDecorView(),"실제 입실·퇴실 시험").performClick());
            check(find(lab.getWindow().getDecorView(),"지금 실제 입실·퇴실 시험").isEnabled(),"Real attendance is a distinct explicit trial choice");
            check(checkedRadios((RadioGroup)lab.getWindow().getDecorView().findViewWithTag("trial-purpose"))==1,"Trial purpose shows exactly one selected action");
            scrollToTag(lab,"trial-purpose");snap("trial-purpose-attendance.png");
            check(LockLab.id(c).isEmpty(),"Choosing the trial purpose alone does not create or run a trial");
            ui(lab::finish);lab=null;
        }
        configure(true);
        TrafficGuard.prefs(c).edit().putString("day",LocalDate.now(Policy.ZONE).toString()).putInt("total",AccessPolicy.DAILY_LIMIT).commit();
        Map<String,?> budget=new HashMap<>(TrafficGuard.prefs(c).getAll());
        check(!TrafficGuard.peek(c,AccessPolicy.Kind.AUTOMATIC,Policy.Phase.ENTRY).allowed(),"Wake preflight respects the existing daily site limit");
        check(budget.equals(TrafficGuard.prefs(c).getAll()),"Wake preflight never consumes a Chrome request or modifies the budget");
        ui(()->ScheduledUnlock.request(c,Policy.Phase.ENTRY));
        check(ScheduledUnlock.token(c).isEmpty(),"An unregistered Wi-Fi cannot create or dispatch a wake request");
        String day=LocalDate.now(Policy.ZONE).toString();String a=ScheduledUnlock.claim(c,Policy.Phase.ENTRY,day,UnlockPolicy.Mode.TRUST);
        check(!a.isEmpty()&&ScheduledUnlock.live(c,a),"Persisted normal wake session belongs to today's eligible entry phase");
        check(ScheduledUnlock.claim(c,Policy.Phase.ENTRY,day,UnlockPolicy.Mode.AUTH).isEmpty(),"Changing the method cannot produce another automatic wake in the same phase");
        check(ScheduledUnlock.claim(c,Policy.Phase.ENTRY,LocalDate.now(Policy.ZONE).minusDays(1).toString(),UnlockPolicy.Mode.TRUST).isEmpty(),"Clock rollback cannot replenish a wake attempt");
        check(!ScheduledUnlock.handoff(c,a),"An unregistered Wi-Fi also blocks handoff after a wake");
        check(ScheduledUnlock.attach(c,a,false)&&!ScheduledUnlock.attach(c,a,false),"A duplicate activity cannot claim the same wake session");
        check(ScheduledUnlock.entered(c,a,false)&&ScheduledUnlock.store(c).getLong("entered_elapsed",0)>0,"Actual activity focus has a separately persisted timing record");
        check(has(DiagnosticLog.Event.WAKE_VISIBLE,"ACTIVITY_VISIBLE"),"Focus timing is exported as an observation rather than an unlock success");
        ScheduledUnlock.stop(c,"stale-token","SHOULD_NOT_APPEAR",true);
        check(ScheduledUnlock.live(c,a),"A stale callback cannot finish the current wake session");
        p.raw.edit().putBoolean("enabled",false).commit();
        check(!ScheduledUnlock.live(c,a),"Disabling reservations immediately invalidates a live wake session");
        ui(()->ScheduleController.reconcile(c));
        check(!ScheduledUnlock.store(c).getBoolean("active",true),"Reconciliation clears invalid sessions without renewing their budget");
        configure(true);
        check(ScheduledUnlock.claim(c,Policy.Phase.ENTRY,day,UnlockPolicy.Mode.TRUST).isEmpty(),"Reservation off/on and a fresh preference instance preserve the attempted phase");
        String b=ScheduledUnlock.claim(c,Policy.Phase.CHECKOUT,day,UnlockPolicy.Mode.TRUST);
        check(!b.isEmpty()&&!ScheduledUnlock.live(c,b),"Checkout has its own attempt but cannot run inside the entry window");
        ScheduledUnlock.cancel(c,"TEST_CANCELLED");
        ScheduledUnlock.store(c).edit().remove("attempted:ENTRY").commit();
        String expiring=ScheduledUnlock.claim(c,Policy.Phase.ENTRY,day,UnlockPolicy.Mode.TRUST);
        ScheduledUnlock.store(c).edit().putLong("started_elapsed",SystemClock.elapsedRealtime()-UnlockPolicy.SESSION_TIMEOUT_MS).commit();
        check(!ScheduledUnlock.live(c,expiring),"A 120-second-old session cannot reach Chrome");
        ScheduledUnlock.cancel(c,"TEST_EXPIRED");
        ScheduledUnlock.store(c).edit().remove("attempted:ENTRY").commit();
        String reboot=ScheduledUnlock.claim(c,Policy.Phase.ENTRY,day,UnlockPolicy.Mode.TRUST);
        ScheduledUnlock.store(c).edit().putInt("boot",Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1)-1).commit();
        check(!ScheduledUnlock.live(c,reboot),"A session from a previous boot is invalid");
        ScheduledUnlock.cancel(c,"TEST_RESTARTED");
        p.raw.edit().putString("chrome_pending",day+":ENTRY").putLong("chrome_pending_at",1234).commit();
        JSONObject snapshot=AutomationSnapshot.read(c);
        check(snapshot.getJSONObject("pending_click").getString("day").equals(day)&&snapshot.getJSONObject("pending_click").getLong("prepared_at_ms")==1234,"Snapshot identifies the pending day, phase and saved intention timestamp");
        check(!snapshot.has("ssid")&&!snapshot.has("latitude")&&!snapshot.has("cookies"),"Automation snapshots exclude Wi-Fi names, coordinates and Chrome credentials");
        p.raw.edit().putString("chrome_pending","legacy unknown").commit();
        check(AutomationSnapshot.read(c).isNull("pending_click"),"Unknown old pending markers cannot fabricate a date or phase");
        realWakeBridge();
        configure(false);
        long expected=System.currentTimeMillis()-250;
        p.raw.edit().putLong("next_alarm",expected+3600000).commit();
        ui(()->new AlarmReceiver().onReceive(c,new Intent(c,AlarmReceiver.class).putExtra(AlarmScheduler.EXPECTED_AT,expected)));
        JSONObject fired=last(DiagnosticLog.Event.ALARM_FIRED);
        check(fired!=null&&fired.getJSONObject("details").getLong("scheduled_at_ms")==expected&&fired.getJSONObject("details").getLong("delay_ms")>=250,"Alarm receipt retains the expected time before reconciliation rewrites it");
        check(!MonitorService.running,"A disabled reservation's received alarm cannot start attendance monitoring");
        check(LockLogExport.environment(c).getJSONObject("automation").getString("scheduled_lock_mode").equals("TRUST"),"Uploads include the separately saved normal wake setting");
        check(!has(DiagnosticLog.Event.CHROME_OPEN_REQUEST,null)&&!has(DiagnosticLog.Event.BUTTON_REQUEST,null),"Checks performed no Chrome open or attendance click");
        out.putString("stream","PASS: "+checks+" normal unlock and diagnostic checks. No attendance HTTP or Chrome launch.\n");restore();test.finish(Activity.RESULT_OK,out);
    }catch(Throwable failure){try{restore();}catch(Throwable ignored){}out.putString("stream","FAIL: "+failure+"\n");test.finish(Activity.RESULT_CANCELED,out);}}
    private void realWakeBridge()throws Exception{
        check(!c.getSystemService(KeyguardManager.class).isDeviceSecure(),"Screen-off integration uses an emulator without a PIN");
        android.app.UiAutomation automation=test.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        if(!ChromeAttendanceService.available()){
            automation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);
            try{
                String services=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,"");SystemClock.sleep(300);
                Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,services);
            }finally{automation.dropShellPermissionIdentity();}
        }
        long connect=SystemClock.elapsedRealtime()+8000;
        while(!ChromeAttendanceService.available()&&SystemClock.elapsedRealtime()<connect)SystemClock.sleep(100);
        check(ChromeAttendanceService.available(),"The real accessibility service is connected for the wake dispatch");
        String ssid=NetworkAccess.inspect(c).ssid();check(!ssid.isEmpty(),"The emulator Wi-Fi name can be verified for local bridge checks");
        new Prefs(c).raw.edit().putStringSet("ssids",Set.of(ssid)).remove("chrome_pending").commit();
        check(NetworkAccess.inspect(c).allowed(),"The synthetic emulator network satisfies the handoff gate");
        TrafficGuard.prefs(c).edit().putString("day",LocalDate.now(Policy.ZONE).toString()).putInt("total",AccessPolicy.DAILY_LIMIT).commit();
        ScheduledUnlock.store(c).edit().remove("attempted:ENTRY").commit();
        String id=ScheduledUnlock.claim(c,Policy.Phase.ENTRY,LocalDate.now(Policy.ZONE).toString(),UnlockPolicy.Mode.TRUST);
        try(ParcelFileDescriptor fd=automation.executeShellCommand("input keyevent 223");FileInputStream in=new FileInputStream(fd.getFileDescriptor())){in.readAllBytes();}
        SystemClock.sleep(300);
        check(!c.getSystemService(PowerManager.class).isInteractive(),"The screen is really off before the bounded wake Activity is dispatched");
        ui(()->ChromeAttendanceService.launchScheduledUnlock(new Intent(c,ScheduledUnlockActivity.class).putExtra(ScheduledUnlock.TOKEN,id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        long until=SystemClock.elapsedRealtime()+10000;
        while(ScheduledUnlock.store(c).getBoolean("active",false)&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
        check("UNLOCK_READY".equals(ScheduledUnlock.store(c).getString("result","")),"System-trusted wake reaches the normal attendance handoff");
        check(c.getSystemService(PowerManager.class).isInteractive(),"The bridge turned the emulator screen on");
        check(!c.getSystemService(KeyguardManager.class).isKeyguardLocked(),"The bridge waits for Android to dismiss Keyguard");
        check(TrafficGuard.prefs(c).getInt("total",0)==AccessPolicy.DAILY_LIMIT&&!has(DiagnosticLog.Event.CHROME_OPEN_REQUEST,null),"The real handoff preserves the full access cap and cannot open the attendance server");
        new Prefs(c).raw.edit().putStringSet("ssids",Set.of("__WAKE_NO_MATCH__")).commit();
    }
    private void configure(boolean enabled){new Prefs(c).raw.edit().putBoolean("enabled",enabled).putBoolean("auto",enabled).putStringSet("ssids",Set.of("__WAKE_NO_MATCH__"))
        .putStringSet("days",Set.of("1","2","3","4","5","6","7")).putStringSet("excluded",Set.of()).putString("start","00:00").putString("end","23:50").putString("checkout","23:51").putInt("checkout_retry_minutes",1).remove("record_day").remove("in").remove("out").commit();}
    private JSONObject last(DiagnosticLog.Event event){JSONArray all=DiagnosticLog.records(c);for(int i=all.length()-1;i>=0;i--){JSONObject r=all.optJSONObject(i);if(event.name().equals(r.optString("event_code")))return r;}return null;}
    private boolean has(DiagnosticLog.Event event,String reason){JSONArray all=DiagnosticLog.records(c);for(int i=0;i<all.length();i++){JSONObject r=all.optJSONObject(i);if(event.name().equals(r.optString("event_code"))&&(reason==null||reason.equals(r.optString("reason_code"))))return true;}return false;}
    private void check(boolean yes,String message){if(!yes)throw new AssertionError(message);checks++;Bundle status=new Bundle();status.putString("stream","PASS: "+message+"\n");test.sendStatus(0,status);}
    private void ui(Runnable task){test.runOnMainSync(task);test.waitForIdleSync();}
    private View find(View root,String text){if(root instanceof TextView&&text.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View value=find(g.getChildAt(i),text);if(value!=null)return value;}return null;}
    private int checkedRadios(RadioGroup group){int count=0;for(int i=0;i<group.getChildCount();i++)if(group.getChildAt(i) instanceof RadioButton option&&option.isChecked())count++;return count;}
    private void scrollToTag(Activity activity,String tag){ui(()->{View view=activity.getWindow().getDecorView().findViewWithTag(tag);if(view==null)return;
        Rect rect=new Rect(0,0,view.getWidth(),view.getHeight());ViewParent parent=view.getParent();
        while(parent instanceof View&&! (parent instanceof ScrollView))parent=parent.getParent();
        if(parent instanceof ScrollView scroll){scroll.offsetDescendantRectToMyCoords(view,rect);scroll.scrollTo(0,scroll.getScrollY()+rect.top-12);}
    });SystemClock.sleep(800);}
    private void snap(String name)throws IOException{Bitmap image=test.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();if(image==null)throw new AssertionError("No screenshot");File dir=new File(c.getExternalFilesDir(null),"verification");dir.mkdirs();try(FileOutputStream file=new FileOutputStream(new File(dir,name))){image.compress(Bitmap.CompressFormat.PNG,100,file);}finally{image.recycle();}}
    @SuppressWarnings("unchecked")private void restore(){if(c==null)return;ui(()->{new Prefs(c).enabled(false);ScheduledUnlock.cancel(c,"TEST_RESTORE");c.stopService(new Intent(c,MonitorService.class));if(lab!=null)lab.finish();if(main!=null)main.finish();
        for(Map.Entry<String,Map<String,?>> entry:saved.entrySet()){SharedPreferences.Editor edit=c.getSharedPreferences(entry.getKey(),0).edit().clear();for(Map.Entry<String,?> item:entry.getValue().entrySet()){
            String k=item.getKey();Object v=item.getValue();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));
        }if(!edit.commit())throw new AssertionError("Restore failed");}});}
}
