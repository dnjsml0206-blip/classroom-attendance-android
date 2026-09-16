package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.provider.Settings;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.json.*;

/** Emulator-only full production-service checks against mandatory CDP-fulfilled responses. */
final class ReplicaChecks {
    private final Instrumentation test; private final Bundle args;
    private Context c; private Activity main; private int count; private String scenario,runId;
    private final Map<String,Map<String,?>> saved=new HashMap<>();
    ReplicaChecks(Instrumentation test,Bundle args){this.test=test;this.args=args;}
    void run(){Bundle result=new Bundle();try{
        if(!Build.HARDWARE.equals("ranchu")&&!Build.HARDWARE.equals("goldfish"))throw new AssertionError("Emulator only");
        c=test.getTargetContext();scenario=args.getString("case","entry");runId=args.getString("replica","");
        JSONObject guard=control("/reset?case="+scenario);
        check(guard.optBoolean("armed")&&runId.equals(guard.optString("runId")),"CDP fixture is armed before any attendance navigation");
        for(String name:new String[]{"attendance","attendance_access","attendance_diagnostics","scheduled_unlock","lock_lab"}){var p=c.getSharedPreferences(name,0);saved.put(name,new HashMap<>(p.getAll()));p.edit().clear().commit();}
        Prefs p=new Prefs(c);p.raw.edit().putBoolean("enabled",false).putBoolean("auto",false).putStringSet("ssids",Set.of("__REPLICA_NOT_READY__")).commit();
        ui(()->{AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));ChromeAttendanceService.cancelWork();});
        UiAutomation automation=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        automation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);try{
            Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,"");SystemClock.sleep(250);
            Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,new ComponentName(c,ChromeAttendanceService.class).flattenToString());
        }finally{automation.dropShellPermissionIdentity();}
        await(()->ChromeAttendanceService.available(),8000,"Accessibility connected");
        main=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        String ssid=NetworkAccess.inspect(c).ssid();check(!ssid.isEmpty(),"Observed emulator Wi-Fi name");
        boolean checkout=scenario.startsWith("checkout");
        p.raw.edit().putBoolean("enabled",true).putBoolean("auto",true).putStringSet("ssids",Set.of(ssid))
            .putStringSet("days",Set.of("1","2","3","4","5","6","7")).putStringSet("excluded",Set.of())
            .putString("start","00:00").putString("end",checkout?"00:01":"23:50").putString("checkout",checkout?"00:02":"23:51")
            .putInt("checkout_retry_minutes",1).commit();
        check(NetworkAccess.inspect(c).allowed(),"Real network guard passes using the emulator Wi-Fi");
        if(checkout)p.raw.edit().putString("record_day",LocalDate.now(Policy.ZONE).toString()).putString("in","08:45:00").commit();
        // Automatic entry uses unchanged production launch/address/date/click/result code.
        ui(()->{if(checkout)ChromeAttendanceService.beginManual(main,Policy.Phase.CHECKOUT,false,false);else ChromeAttendanceService.begin(main,null);});
        await(()->ChromeAttendanceService.busy(),3000,"Production attendance session started");
        if(scenario.startsWith("focus")){
            await(()->hasReason("PAGE_SETTLING"),10000,"Replica page visible before focus interruption");
            ui(()->c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP)));
            await(()->hasReason("CHROME_NOT_VISIBLE")||hasReason("FOREGROUND_OTHER_APP"),8000,"Focus interruption is observed");
            boolean baseline="true".equals(args.getString("baseline"));
            if(baseline)await(()->!ChromeAttendanceService.busy(),42000,"Initial observation deadline completes");
            else await(()->countEvent("PAGE_RECOVERY_WAIT")==1,42000,"One bounded pre-click recovery interval begins");
            check(control("/state").getInt("entries")==0,"No entry while another app covers Chrome");
            boolean paused=TrafficGuard.prefs(c).getBoolean("paused:ENTRY",false);
            if(baseline){check(paused,"Baseline reproduces permanent automatic pause before any click");}
            else{
                check(!paused,"Transient pre-click focus loss does not permanently pause automatic attendance");
                if(scenario.equals("focus-expire")){
                    await(()->!ChromeAttendanceService.busy(),42000,"Extended observation also ends at its deadline");
                    check(countEvent("PAGE_RECOVERY_WAIT")==1&&countEvent("PRECLICK_DEFERRED")==1,"Extension is bounded and expiry is logged as pre-click deferral");
                    check(!TrafficGuard.prefs(c).getBoolean("paused:ENTRY",false)&&control("/state").getInt("entries")==0,"Recovery expiry neither submits nor permanently pauses");
                    check(TrafficGuard.peek(c,AccessPolicy.Kind.AUTOMATIC,Policy.Phase.ENTRY).reason()==AccessPolicy.Reason.COOLDOWN,"Existing automatic retry cooldown remains enforced");
                }else{
                control("/return");
                ui(()->c.startActivity(c.getPackageManager().getLaunchIntentForPackage("com.android.chrome")));
                // Recovery resumes the existing page without another page-open claim.
                await(()->p.checkedInToday(),18000,"Returning to Chrome resumes entry without reopening the page");
                JSONObject state=control("/state");check(state.getInt("entries")==1,"Recovery submits exactly once");check(countEvent("CHROME_OPEN_REQUEST")==1,"Recovery does not launch or reload a second page");
                }
            }
        }else{
            await(()->!ChromeAttendanceService.busy(),85000,"Attendance observation finished");
            JSONObject state=control("/state");
            if(Set.of("auth","wrong-date","server-error","no-course","auth-message","denied","rate-limit","human-check","network-error").contains(scenario)){
                check(state.getInt("entries")==0,"Invalid page never submits entry");check(!p.checkedInToday(),"Invalid page never reports success");
            }else if(scenario.equals("no-result")){
                check(state.getInt("entries")==1,"Unconfirmed result is submitted only once");check(!p.checkedInToday(),"Missing server time is not success");
                check(TrafficGuard.prefs(c).getBoolean("paused:ENTRY",false)&&!p.raw.getString("chrome_pending","").isEmpty(),"Unconfirmed click retains pause and durable pending marker");
            }else{
                check(checkout?p.checkedOutToday():p.checkedInToday(),"Server time is read back after the replica reload");
                check((checkout?state.getInt("exits"):state.getInt("entries"))==1,"Exactly one simulated attendance submission");
                if(checkout)check(state.getInt("dialogs")==1&&countEvent("CONFIRM_REQUEST")==1,"Actual Chrome confirmation dialog clicked exactly once");
                check(countEvent("RECORD_CONFIRMED")==1,"Completion event requires the observed server time");
            }
        }
        JSONObject last=control("/state");check(last.getJSONArray("errors").length()==0&&last.getBoolean("armed"),"Interception stayed active and every request was fulfilled locally");
        if(!"true".equals(args.getString("baseline"))){
            check(countEvent("CHROME_DIAGNOSTIC")+countEvent("ATTEMPT_FAILED")>0&&countEvent("CHROME_DIAGNOSTIC")<=14,"Bounded detailed observations are recorded");
            String expected=switch(scenario){case "auth","auth-message"->"AUTH_REQUIRED";case "server-error"->"SERVER_ERROR";case "no-course"->"TRAINEE_REJECTED";case "denied"->"ACCESS_DENIED";case "rate-limit"->"RATE_LIMIT";case "human-check"->"HUMAN_CHECK";case "network-error"->"NETWORK_ERROR";default->"";};
            JSONArray all=DiagnosticLog.records(c);boolean observed=expected.isEmpty(),failure=false,focus=false;String failureCode="";
            for(int i=0;i<all.length();i++){JSONObject row=all.getJSONObject(i),d=row.optJSONObject("details");if(d==null)continue;
                if(expected.equals(d.optString("page_signal")))observed=true;
                if("CHROME_NOT_FOREGROUND".equals(d.optString("diagnosis")))focus=true;
                if("ATTEMPT_FAILED".equals(row.optString("event_code"))){failure=true;failureCode=d.optString("diagnosis");check(d.has("click_attempted")&&d.has("read_state")&&d.has("last_site_age_ms")&&!d.optString("attempt_id").isEmpty(),"Failure retains cause, click and prior-site-age evidence");}
            }
            // Real Chrome can omit a dynamically replaced form subtree even when the text is painted.
            // The original auth fixture then MUST remain unknown, not be guessed from the fixture case.
            if(scenario.equals("auth")&&!observed){
                check(failure&&failureCode.equals("PAGE_FORMAT_UNVERIFIED"),"Unexposed authentication form remains explicitly unverified, never guessed as site blocking");
            }else check(observed,"Expected site message is classified without exporting its text");
            if(!expected.isEmpty())check(failure,"Site error includes a final diagnostic");
            if(scenario.startsWith("focus"))check(focus,"Focus loss is classified as local window state, not website restriction");
            if(scenario.equals("focus-expire"))check(failure&&!failureCode.equals("SITE_RESTRICTION_SHOWN"),"Unreadable final window never claims site blocking");
            if(scenario.equals("no-result"))check(failure&&failureCode.equals("CLICK_RESULT_UNCONFIRMED"),"Accepted click without time is explicitly unconfirmed");
            String export=LockLogExport.records(c).toString();check(export.contains("diagnostic_version")&&!export.contains("시험 수강생")&&!export.contains("replica-student")&&!export.contains("ERR_CONNECTION_TIMED_OUT"),"Upload carries diagnostic codes without page text or credentials");
        }
        save();restore();result.putString("stream","PASS: "+count+" replica checks ("+scenario+"). Production service, local responses only.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{save();restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+scenario+": "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private boolean hasReason(String reason){JSONArray a=DiagnosticLog.records(c);for(int i=0;i<a.length();i++)if(reason.equals(a.optJSONObject(i).optString("reason_code")))return true;return false;}
    private int countEvent(String name){int n=0;JSONArray a=DiagnosticLog.records(c);for(int i=0;i<a.length();i++)if(name.equals(a.optJSONObject(i).optString("event_code")))n++;return n;}
    private interface Condition{boolean get() throws Exception;}
    private void await(Condition condition,long ms,String text)throws Exception{long until=SystemClock.elapsedRealtime()+ms;while(!condition.get()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);check(condition.get(),text);}
    private JSONObject control(String path)throws Exception{
        try(Socket socket=new Socket("127.0.0.1",9333)){socket.setSoTimeout(5000);String query=path+(path.contains("?")?"&":"?")+"id="+runId;
            socket.getOutputStream().write(("GET "+query+" HTTP/1.1\r\nHost: 127.0.0.1:9333\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String response=new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);int start=response.indexOf("\r\n\r\n");if(!response.startsWith("HTTP/1.1 200")||start<0)throw new IOException("Fixture unavailable");return new JSONObject(response.substring(start+4));}
    }
    private void ui(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);count++;Bundle b=new Bundle();b.putString("stream","PASS: "+text+"\n");test.sendStatus(0,b);}
    private void save()throws Exception{if(c==null)return;File dir=new File(c.getExternalFilesDir(null),"replica");dir.mkdirs();
        StringBuilder tree=new StringBuilder();
        ui(()->{try{var field=ChromeAttendanceService.class.getDeclaredField("instance");field.setAccessible(true);Object service=field.get(null);if(service!=null){var method=ChromeAttendanceService.class.getDeclaredMethod("focusedRoot");method.setAccessible(true);dump((android.view.accessibility.AccessibilityNodeInfo)method.invoke(service),tree,0);}}catch(Exception e){tree.append(e);}});
        try(var f=new FileOutputStream(new File(dir,scenario+"-nodes.txt"))){f.write(tree.toString().getBytes(StandardCharsets.UTF_8));}
        try(var f=new FileOutputStream(new File(dir,scenario+"-events.json"))){f.write(DiagnosticLog.records(c).toString(2).getBytes(StandardCharsets.UTF_8));}
        Bitmap b=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();if(b!=null){try(var f=new FileOutputStream(new File(dir,scenario+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}b.recycle();}
    }
    private void dump(android.view.accessibility.AccessibilityNodeInfo n,StringBuilder out,int depth){if(n==null||depth>40)return;out.append(" ".repeat(depth)).append(n.getClassName()).append(" text=[").append(n.getText()).append("] desc=[").append(n.getContentDescription()).append("] visible=").append(n.isVisibleToUser()).append(" enabled=").append(n.isEnabled()).append(" clickable=").append(n.isClickable()).append(" children=").append(n.getChildCount()).append('\n');for(int i=0;i<n.getChildCount();i++)dump(n.getChild(i),out,depth+1);}
    @SuppressWarnings("unchecked") private void restore(){if(c==null||saved.isEmpty())return;ui(()->{ChromeAttendanceService.cancelWork();new Prefs(c).enabled(false);AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));if(main!=null)main.finish();for(var group:saved.entrySet()){var edit=c.getSharedPreferences(group.getKey(),0).edit().clear();for(var item:group.getValue().entrySet()){var v=item.getValue();var k=item.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));}edit.commit();}});}
}