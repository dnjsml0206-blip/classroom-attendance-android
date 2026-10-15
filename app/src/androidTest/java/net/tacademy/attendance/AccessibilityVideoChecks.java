package net.tacademy.attendance;
import android.app.*;import android.content.*;import android.os.*;import android.view.accessibility.*;import java.util.*;
/** Emulator consent recording. Chrome fixture must be externally intercepted, with DNS blocked. */
final class AccessibilityVideoChecks {
 final Instrumentation t;MainActivity a;Prefs p;AccessibilityVideoChecks(Instrumentation t){this.t=t;}
 UiAutomation u(){return t.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);}
 void run(){Bundle b=new Bundle();try{
 if(!Set.of("ranchu","goldfish").contains(Build.HARDWARE))throw new AssertionError("Emulator only");
 Context c=t.getTargetContext();p=new Prefs(c);
 t.runOnMainSync(()->{AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));p.raw.edit().putBoolean("first_use_guide_pending",false).putBoolean("enabled",false).putBoolean("auto",false).remove("record_day").remove("in").remove("out").remove("record:"+java.time.LocalDate.now(Policy.ZONE)).commit();});
 u();a=(MainActivity)t.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));pause(1800);click("자동출석");pause(1000);click("Chrome 접근성");pause(7000);click("동의하지 않음");pause(1800);
 if(c.checkSelfPermission("android.permission.BIND_ACCESSIBILITY_SERVICE")==0)throw new AssertionError("Unexpected app permission");
 click("Chrome 접근성");pause(3000);click("동의하고 설정 열기");pause(1400);click("출석 메이트 · Chrome 출석");pause(1500);click("Use 출석 메이트 · Chrome 출석","출석 메이트 · Chrome 출석 사용");pause(2000);click("Allow","허용");pause(2200);
 if(!ChromeAttendanceService.available())throw new AssertionError("Service not connected");
 b.putString("stream","PASS: declined, reopened, explicit consent and Android accessibility enable.\n");
 }catch(Throwable e){b.putString("stream","FAIL: "+e+"\n");}t.finish(Activity.RESULT_OK,b);}
 void pause(long ms){SystemClock.sleep(ms);}
 void click(String...texts){long until=SystemClock.elapsedRealtime()+6000;while(SystemClock.elapsedRealtime()<until){var r=u().getRootInActiveWindow();if(r!=null&&visit(r,texts))return;pause(150);}throw new AssertionError("Missing UI button "+Arrays.toString(texts));}
 boolean visit(AccessibilityNodeInfo n,String[] texts){for(String s:texts)if(s.contentEquals(n.getText()==null?"":n.getText())){var q=n;for(int i=0;q!=null&&i<5;i++,q=q.getParent())if(q.isClickable()&&q.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;}for(int i=0;i<n.getChildCount();i++){var child=n.getChild(i);if(child!=null&&visit(child,texts))return true;}return false;}
}
