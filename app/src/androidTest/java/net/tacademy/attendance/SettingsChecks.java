package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;

/** Emulator-only navigation checks. No switch, permission grant or attendance button is clicked. */
final class SettingsChecks {
    private final Instrumentation test;private Context c;private Activity a;private Map<String,?> saved;private int checks;
    SettingsChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Build.HARDWARE.equals("ranchu")&&!Build.HARDWARE.equals("goldfish"))throw new AssertionError("Emulator only");
        c=test.getTargetContext();Prefs prefs=new Prefs(c);saved=new HashMap<>(prefs.raw.getAll());
        main(()->{prefs.raw.edit().putBoolean("enabled",false).putBoolean("auto",false).putStringSet("ssids",Set.of("__SETTINGS_NO_MATCH__")).commit();AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));});
        UiAutomation automation=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        if(Build.VERSION.SDK_INT>=29){automation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);try{
            String services=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,"");SystemClock.sleep(300);
            Settings.Secure.putString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,services);
        }finally{automation.dropShellPermissionIdentity();}}
        long until=SystemClock.elapsedRealtime()+8000;while(!ChromeAttendanceService.available()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
        check(ChromeAttendanceService.available(),"Service connected for menu-navigation checks");
        show();SetupStatusView[] view={null};main(()->{view[0]=new SetupStatusView(a,true);a.setContentView(view[0]);});
        check(description(view[0],"Chrome 자동 클릭: 꺼짐"),"Disabled auto-click is visibly marked off");
        main(()->{prefs.raw.edit().putBoolean("auto",true).commit();view[0].refresh();});
        check(description(view[0],"Chrome 자동 클릭: 켜짐"),"Status updates after a settings change");
        check(description(view[0],"Extend Unlock: 휴대폰에서 확인"),"Trust configuration is never falsely marked verified");
        check(PhoneSettings.forProblem("정확한 위치 권한이 필요합니다")==PhoneSettings.Target.LOCATION_PERMISSION&&PhoneSettings.forProblem("Chrome 접근성 연결이 필요합니다")==PhoneSettings.Target.ACCESSIBILITY,"Missing prerequisites route to their own settings");
        Intent channel=PhoneSettings.primary(c,PhoneSettings.Target.NOTIFICATIONS);
        check(c.getPackageName().equals(channel.getStringExtra(Settings.EXTRA_APP_PACKAGE))&&"result".equals(channel.getStringExtra(Settings.EXTRA_CHANNEL_ID)),"Result shortcut targets this app and its completion channel");
        main(()->PhoneSettings.open(a,PhoneSettings.Target.NOTIFICATIONS));
        check(awaitText("입실·퇴실 결과",7000),"System notification channel page opens");
        show();int beforeFine=c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION),beforeBackground=c.checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        main(()->PhoneSettings.open(a,PhoneSettings.Target.LOCATION_PERMISSION));
        boolean choices=awaitText("Allow all the time",12000)||awaitText("항상 허용",500);
        check(choices||shell("dumpsys activity activities").lines().anyMatch(line->line.contains("topResumedActivity")&&line.contains("ManagePermissionsActivity")),"Location shortcut reaches choices or the system-protected app permissions page");
        check(beforeFine==c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)&&beforeBackground==c.checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION),"Menu navigation does not change location grants");
        show();main(()->PhoneSettings.open(a,PhoneSettings.Target.ALARMS));
        check(awaitText("Alarms",7000)||awaitText("알람",500),"App alarm permission screen opens");
        show();main(()->PhoneSettings.open(a,PhoneSettings.Target.BATTERY));
        check(awaitText("Allow background usage",12000)||awaitText("Unrestricted",500)||awaitText("제한 없음",500),"App battery menu opens");
        show();main(()->PhoneSettings.open(a,PhoneSettings.Target.ACCESSIBILITY));
        check(awaitText("Accessibility",7000)||awaitText("접근성",500),"Public accessibility settings open without a protected API");
        restore();result.putString("stream","PASS: "+checks+" settings/navigation checks. No grants or attendance were changed.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private void show(){
        if(a==null)a=test.startActivitySync(new Intent(c,MainActivity.class).putExtra("open_tab",2).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        else for(int i=0;i<6;i++){
            UiAutomation automation=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);AccessibilityNodeInfo root=automation.getRootInActiveWindow();
            if(root!=null&&c.getPackageName().equals(String.valueOf(root.getPackageName())))break;
            automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);SystemClock.sleep(400);
        }
        test.waitForIdleSync();
    }
    private boolean awaitText(String expected,long timeout){long until=SystemClock.elapsedRealtime()+timeout;do{AccessibilityNodeInfo root=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();if(root!=null&&!c.getPackageName().equals(String.valueOf(root.getPackageName()))&&tree(root).contains(expected))return true;SystemClock.sleep(200);}while(SystemClock.elapsedRealtime()<until);return false;}
    private String shell(String command){try(java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand(command))){return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}catch(java.io.IOException e){throw new AssertionError(e);}}
    private String tree(AccessibilityNodeInfo node){if(node==null)return "";StringBuilder text=new StringBuilder().append(node.getText()).append(' ').append(node.getContentDescription()).append(' ');for(int i=0;i<node.getChildCount();i++)text.append(tree(node.getChild(i)));return text.toString();}
    private boolean description(View v,String target){if(target.equals(String.valueOf(v.getContentDescription())))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(description(((ViewGroup)v).getChildAt(i),target))return true;return false;}
    private void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;Bundle progress=new Bundle();progress.putString("stream","PASS: "+name+"\n");test.sendStatus(0,progress);}
    private void main(Runnable r){Throwable[] error={null};test.runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    @SuppressWarnings("unchecked")private void restore(){if(c==null||saved==null)return;main(()->{ChromeAttendanceService.cancelSettingsMenu();SharedPreferences.Editor e=new Prefs(c).raw.edit().clear();for(Map.Entry<String,?> item:saved.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<>((Set<String>)v));}e.commit();if(a!=null)a.finish();AlarmScheduler.arm(c,false);});}
}
