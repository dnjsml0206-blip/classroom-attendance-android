package net.tacademy.attendance;
import android.app.*;import android.content.*;import android.os.*;import java.util.*;
/** Emulator video, preconfigured test location/schedule. Real UI reservation buttons and service. */
final class ForegroundVideoChecks {
 final Instrumentation t;MainActivity a;Prefs p;Context c;ForegroundVideoChecks(Instrumentation t){this.t=t;}
 UiAutomation u(){return t.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);}
 void run(){Bundle b=new Bundle();try{
 if(!Set.of("ranchu","goldfish").contains(Build.HARDWARE))throw new AssertionError("Emulator only");
 c=t.getTargetContext();p=new Prefs(c);var automation=u();
 automation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS);try{android.provider.Settings.Secure.putString(c.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,"");pause(300);android.provider.Settings.Secure.putString(c.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,new ComponentName(c,ChromeAttendanceService.class).flattenToString());}finally{automation.dropShellPermissionIdentity();}
 long connected=SystemClock.elapsedRealtime()+8000;while(!ChromeAttendanceService.available()&&SystemClock.elapsedRealtime()<connected)pause(100);
 t.runOnMainSync(()->{p.enabled(false);AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));c.getSystemService(NotificationManager.class).cancelAll();p.raw.edit().putBoolean("first_use_guide_pending",false).putBoolean("setup_finished",true).putBoolean("auto",false).putStringSet("ssids",Set.of("TACADEMY_5G")).putString("start","00:00").putString("end","23:50").putString("checkout","23:51").putStringSet("days",Set.of("1","2","3","4","5","6","7")).remove("record_day").remove("in").remove("out").remove("record:"+java.time.LocalDate.now(Policy.ZONE)).commit();p.location(37.5665,126.9780,30);});
 a=(MainActivity)t.startActivitySync(new Intent(c,MainActivity.class).putExtra("open_tab",1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
 pause(2200);if(!ChromeAttendanceService.available())throw new AssertionError("Accessibility not connected");
 t.runOnMainSync(()->a.getWindow().getDecorView().findViewWithTag("reservation-on").performClick());pause(1500);
 if(!p.enabled())throw new AssertionError("Reservation not enabled by UI");u().performGlobalAction(2);pause(8000);
 if(!MonitorService.running)throw new AssertionError("No foreground service");
 if(!p.raw.getString("proximity","").equals("강의실 주변에 있습니다"))throw new AssertionError("GPS callback missing");
 checkNotices(true);u().performGlobalAction(4);pause(5500);u().performGlobalAction(1);pause(300);
 a=(MainActivity)t.startActivitySync(new Intent(c,MainActivity.class).putExtra("open_tab",1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));pause(1500);
 t.runOnMainSync(()->a.getWindow().getDecorView().findViewWithTag("reservation-off").performClick());pause(2000);
 if(p.enabled()||MonitorService.running)throw new AssertionError("Service did not stop");checkNotices(false);u().performGlobalAction(4);pause(3500);
 b.putString("stream","PASS: UI reservation ON, background service and real location Wi-Fi notification, UI OFF stops service and removes both notifications. No attendance server requests.\n");
 }catch(Throwable e){b.putString("stream","FAIL: "+e+"\n");}finally{if(c!=null)t.runOnMainSync(()->{p.enabled(false);AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));});}t.finish(Activity.RESULT_OK,b);}
 void checkNotices(boolean expected){boolean monitor=false,wifi=false;for(var n:c.getSystemService(NotificationManager.class).getActiveNotifications()){if(n.getId()==Notices.MONITOR)monitor=true;if(n.getId()==Notices.WIFI)wifi=true;}if(monitor!=expected||wifi!=expected)throw new AssertionError("Notification state mismatch "+monitor+"/"+wifi);}
 void pause(long ms){SystemClock.sleep(ms);}
}
