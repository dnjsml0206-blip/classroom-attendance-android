package net.tacademy.attendance;
import android.app.*;import android.content.*;import android.os.*;import android.view.accessibility.*;import java.util.*;
/** Emulator recording only. Uses test GPS coordinates and unmatched SSID, no attendance server calls. */
final class LocationVideoChecks {
 final Instrumentation t;MainActivity a;Prefs p;LocationVideoChecks(Instrumentation t){this.t=t;}
 void run(){Bundle b=new Bundle();try{
 if(!Set.of("ranchu","goldfish").contains(Build.HARDWARE))throw new AssertionError("Emulator only");
 Context c=t.getTargetContext();p=new Prefs(c);
 t.runOnMainSync(()->{AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));p.raw.edit().clear().commit();p=new Prefs(c);p.raw.edit().putBoolean("first_use_guide_pending",false).putBoolean("setup_finished",true).putBoolean("enabled",false).putBoolean("auto",false).putStringSet("ssids",Set.of("TACADEMY_5G")).commit();});
 a=(MainActivity)t.startActivitySync(new Intent(c,MainActivity.class).putExtra("open_tab",2).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));pause(1200);
 t.runOnMainSync(()->{try{var m=MainActivity.class.getDeclaredMethod("permissions");m.setAccessible(true);m.invoke(a);}catch(Exception e){throw new RuntimeException(e);}});
 pause(5000);click("계속");pause(2200);click("While using the app","앱 사용 중에만 허용");pause(1200);
 t.runOnMainSync(()->PhoneSettings.open(a,PhoneSettings.Target.LOCATION_PERMISSION));pause(1200);click("계속");pause(1000);
 click("Permissions","권한");pause(800);click("Location","위치");pause(1800);click("Allow all the time","항상 허용");pause(1800);
 if(!ScheduleController.backgroundLocationAllowed(c))throw new AssertionError("Background permission missing");
 t.getUiAutomation().performGlobalAction(1);pause(400);t.getUiAutomation().performGlobalAction(1);pause(400);t.getUiAutomation().performGlobalAction(1);pause(600);
 t.runOnMainSync(()->{p.location(37.5665,126.9780,30);p.raw.edit().putBoolean("enabled",true).putBoolean("auto",true).putString("start","00:00").putString("end","23:50").putString("checkout","23:51").putInt("checkout_retry_minutes",1).putStringSet("days",Set.of("1","2","3","4","5","6","7")).commit();a.startActivity(new Intent(c,MainActivity.class).putExtra("open_tab",2).putExtra("focus_setting","setup-location"));});pause(2000);
 t.getUiAutomation().performGlobalAction(2);pause(500);t.runOnMainSync(()->{if(!AlarmScheduler.arm(c,true))throw new AssertionError("Alarm denied");});pause(6000);
 if(!MonitorService.running)throw new AssertionError("Monitor not running");
 if(!p.raw.getString("proximity","").equals("강의실 주변에 있습니다"))throw new AssertionError("No location callback: "+p.raw.getString("proximity",""));
 boolean notified=false;for(var n:c.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==Notices.WIFI)notified=true;if(!notified)throw new AssertionError("Location notice missing");
 t.getUiAutomation().performGlobalAction(4);pause(5500);b.putString("stream","PASS: real location disclosure and permission UI; background alarm + GPS callback generated Wi-Fi alert. Emulator test coordinates.\n");
 }catch(Throwable e){b.putString("stream","FAIL: "+e+"\n");}finally{try{t.runOnMainSync(()->{Context c=t.getTargetContext();p.enabled(false);AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));});}catch(Throwable ignored){}}
 t.finish(Activity.RESULT_OK,b);}
 void pause(long ms){SystemClock.sleep(ms);}
 void click(String...texts){long until=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<until){var r=t.getUiAutomation().getRootInActiveWindow();if(r!=null&&visit(r,texts))return;pause(150);}throw new AssertionError("Missing UI button "+Arrays.toString(texts));}
 boolean visit(AccessibilityNodeInfo n,String[] texts){for(String s:texts)if(s.contentEquals(n.getText()==null?"":n.getText())){var q=n;for(int i=0;q!=null&&i<5;i++,q=q.getParent())if(q.isClickable()&&q.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;}for(int i=0;i<n.getChildCount();i++){var child=n.getChild(i);if(child!=null&&visit(child,texts))return true;}return false;}
}
