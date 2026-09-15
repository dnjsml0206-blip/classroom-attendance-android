package net.tacademy.attendance;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Emulator-only real Chrome tests. Reservations are OFF and the attendance access budget is exhausted. */
final class LocalClickChecks {
    private final Instrumentation test;private Context c;private MainActivity main;private Activity lab;private int count;
    private final Map<String,Map<String,?>> saved=new HashMap<>();
    LocalClickChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Build.HARDWARE.equals("ranchu")&&!Build.HARDWARE.equals("goldfish"))throw new AssertionError("Emulator only");
        c=test.getTargetContext();if(!CreatorTools.enabled())throw new AssertionError("Creator only");
        for(String name:new String[]{"attendance","attendance_access","attendance_diagnostics","scheduled_unlock","creator_tools","lock_lab"}){SharedPreferences p=c.getSharedPreferences(name,0);saved.put(name,new HashMap<>(p.getAll()));p.edit().clear().commit();}
        Prefs p=new Prefs(c);p.raw.edit().putBoolean("enabled",false).putBoolean("auto",false).putStringSet("ssids",Set.of("__CLICK_NO_MATCH__")).commit();
        TrafficGuard.prefs(c).edit().putString("day",java.time.LocalDate.now(Policy.ZONE).toString()).putInt("total",AccessPolicy.DAILY_LIMIT).commit();
        UiAutomation a=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        if(!ChromeAttendanceService.available()){
            a.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);try{
                String services=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,"");SystemClock.sleep(300);
                Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,services);
            }finally{a.dropShellPermissionIdentity();}
        }
        long connected=SystemClock.elapsedRealtime()+8000;while(!ChromeAttendanceService.available()&&SystemClock.elapsedRealtime()<connected)SystemClock.sleep(100);
        check(ChromeAttendanceService.available(),"Real accessibility service is connected");
        main=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).putExtra("open_tab",1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        View diagnostics=main.getWindow().getDecorView().findViewWithTag("creator-diagnostics");ViewGroup parent=(ViewGroup)diagnostics.getParent();
        check(parent.getChildAt(parent.getChildCount()-1)==diagnostics,"Creator diagnostics is the last Settings card");
        check(find(main.getWindow().getDecorView(),"알림 후 직접 해제")==null,"Manual-notification-only mode is absent");
        p.raw.edit().putString("scheduled_lock_mode","NOTICE").commit();check(ScheduledUnlock.mode(c)==UnlockPolicy.Mode.TRUST,"Existing notification-only setting migrates to automatic trust mode");
        ui(()->find(main.getWindow().getDecorView(),"오늘").performClick());SystemClock.sleep(500);
        TextView footer=main.getWindow().getDecorView().findViewWithTag("today-wifi");
        check(footer.getText().toString().endsWith("와이파이 미연결"),"Unregistered Wi-Fi displays the requested disconnected text");
        parent=(ViewGroup)footer.getParent();check(parent.getChildAt(parent.getChildCount()-1)==footer,"Wi-Fi status is at the end of Today");
        String observed=NetworkAccess.inspect(c).ssid();check(!observed.isEmpty(),"Emulator Wi-Fi can be observed even when mobile data is the default");
        p.raw.edit().putStringSet("ssids",Set.of(observed)).commit();SystemClock.sleep(2500);
        check(footer.getText().toString().endsWith("대기 중"),"Matching Wi-Fi refreshes to waiting without reopening Today");
        NetworkAccess.State matching=NetworkAccess.inspect(c);if("INTERNET_UNVALIDATED".equals(matching.code())||"WIFI_NOT_DEFAULT".equals(matching.code()))check(!matching.allowed(),"Recognizing Wi-Fi without a usable default route still blocks real attendance");
        p.raw.edit().putStringSet("ssids",Set.of("__CLICK_NO_MATCH__")).commit();SystemClock.sleep(2500);
        check(footer.getText().toString().endsWith("와이파이 미연결"),"Removing the target Wi-Fi refreshes the footer");
        lab=test.startActivitySync(new Intent(c,LockLabActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        check(find(lab.getWindow().getDecorView(),"실제 입실·퇴실 시험")==null,"Test screen does not expose a real attendance action");
        ui(()->lab.getWindow().getDecorView().findViewWithTag("click-test-now").performClick());
        awaitResult();
        check("SUCCESS".equals(LockLab.prefs(c).getString("machine_result","")),"Immediate test clicks the real Chrome substitute button and confirms the page change");
        check(LockLab.prefs(c).getBoolean("click_accepted",false)&&LockLab.prefs(c).getLong("click_confirmed_at_ms",0)>0,"Successful result has UI acceptance and page-change evidence");
        snap("substitute-chrome-success.png");
        check(!p.auto()&&!p.enabled()&&p.raw.getString("record_day","").isEmpty(),"Test works outside attendance settings and creates no attendance record");
        check(LockLogExport.current(c).getString("purpose").equals("LOCAL_CLICK"),"Export distinguishes the substitute test from real attendance");
        final String[] token={""};ui(()->token[0]=LockLab.prepareClick(c,LockTestPolicy.Mode.EXTEND,15));
        try(ParcelFileDescriptor fd=a.executeShellCommand("input keyevent 223");FileInputStream in=new FileInputStream(fd.getFileDescriptor())){in.readAllBytes();}
        SystemClock.sleep(800);check(!c.getSystemService(PowerManager.class).isInteractive(),"Screen is off before the real scheduled alarm");
        awaitResult();
        check("SUCCESS".equals(LockLab.prefs(c).getString("machine_result","")),"Scheduled screen-off test reaches the Chrome click and page-change confirmation");
        JSONObject exported=LockLogExport.current(c);
        check(!exported.getJSONObject("execution_environment").getBoolean("screen_interactive"),"Log records that the alarm fired with the screen off");
        check(LockLab.prefs(c).getString("entry_source","").contains("예약"),"The scheduled test entered through the alarm path");
        check(TrafficGuard.prefs(c).getInt("total",0)==AccessPolicy.DAILY_LIMIT&&DiagnosticLog.records(c).toString().indexOf("CHROME_OPEN_REQUEST")<0,"Substitute tests never consume or bypass the attendance-site budget");
        boolean notice=false;for(android.service.notification.StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==LockLab.NOTICE&&n.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT,"").toString().startsWith("성공"))notice=true;
        check(notice,"Completed test leaves a success notification");
        ui(()->{LockLab.prepareClick(c,LockTestPolicy.Mode.EXTEND,15);LockLab.cancel(c);});
        check(!LockLab.prefs(c).getBoolean("active",true)&&"FAILED".equals(LockLab.prefs(c).getString("machine_result","")),"Cancelled test cannot retain the previous success result");
        result.putString("stream","PASS: "+count+" local Chrome click and requested UI checks. No attendance submission.\n");restore();test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){String evidence=c==null?"":LockLab.report(c);try{snap("click-failure.png");restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n"+evidence+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private void awaitResult(){long until=SystemClock.elapsedRealtime()+48000;while(LockLab.prefs(c).getBoolean("active",false)&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(150);if(LockLab.prefs(c).getBoolean("active",false))throw new AssertionError("Test timed out: "+LockLab.report(c));}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);count++;Bundle b=new Bundle();b.putString("stream","PASS: "+label+"\n");test.sendStatus(0,b);}
    private void ui(Runnable run){test.runOnMainSync(run);test.waitForIdleSync();}
    private View find(View root,String text){if(root instanceof TextView t&&text.contentEquals(t.getText()))return root;if(root instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View v=find(g.getChildAt(i),text);if(v!=null)return v;}return null;}
    private void snap(String name)throws IOException{if(c==null)return;Bitmap b=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();if(b==null)return;File dir=new File(c.getExternalFilesDir(null),"verification");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
    @SuppressWarnings("unchecked") private void restore(){if(c==null)return;ui(()->{LockLab.cancel(c);new Prefs(c).enabled(false);c.stopService(new Intent(c,MonitorService.class));if(lab!=null)lab.finish();if(main!=null)main.finish();for(var group:saved.entrySet()){var edit=c.getSharedPreferences(group.getKey(),0).edit().clear();for(var item:group.getValue().entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));}edit.commit();}});}
}
