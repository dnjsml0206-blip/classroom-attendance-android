package net.tacademy.attendance;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.time.*;
import java.util.*;

/** Emulator-only checks: no matching network, no automatic reservations, no attendance submission. */
final class ManagementChecks {
    private final Instrumentation test;private Context c;private Activity activity;private Prefs p;private int count;
    private final Map<String,Map<String,?>> saved=new HashMap<>();
    ManagementChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Set.of("ranchu","goldfish").contains(Build.HARDWARE))throw new AssertionError("Emulator only");
        c=test.getTargetContext();p=new Prefs(c);
        for(String name:new String[]{"attendance","attendance_access","attendance_diagnostics"})saved.put(name,new HashMap<>(c.getSharedPreferences(name,0).getAll()));
        ui(()->{ChromeAttendanceService.cancelWork();AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));
            p.raw.edit().clear().putBoolean("initialized",true).putInt("schedule_version",2).putBoolean("chrome_backend",true)
                .putBoolean(PersonalAttendanceCorrection.MARKER,true).putBoolean("late_disabled",true).putBoolean("enabled",false).putBoolean("auto",false).putStringSet("ssids",Set.of("__MANAGEMENT_NO_MATCH__"))
                .putStringSet("days",Set.of("1","2","3","4","5")).putStringSet("excluded",Set.of())
                .putString("start","00:00").putString("end","23:50").putString("checkout","23:51").commit();
            TrafficGuard.prefs(c).edit().putString("day",LocalDate.now(Policy.ZONE).toString()).putInt("total",AccessPolicy.DAILY_LIMIT).commit();});
        open(MainActivity.class);check(view("attendance-read-record")!=null&&view("attendance-management")!=null&&view("attendance-timetable")!=null,"Today exposes compact record lookup, management and timetable controls");
        capture("today.png");
        ui(()->view("attendance-timetable").requestRectangleOnScreen(new android.graphics.Rect(0,0,view("attendance-timetable").getWidth(),view("attendance-timetable").getHeight()),true));settle();capture("today-tools.png");
        ui(()->view("attendance-leave-now").performClick());settle();
        check(node("지금 조퇴하시겠어요?")!=null,"Checkout before schedule asks for explicit early-leave confirmation");
        capture("early-confirm.png");clickText("취소");check(!ChromeAttendanceService.busy()&&!p.checkedOutToday(),"Cancelling early leave neither launches nor records checkout");
        Instrumentation.ActivityMonitor monitor=test.addMonitor(TimetableActivity.class.getName(),null,false);
        ui(()->view("attendance-timetable").performClick());Activity timetable=test.waitForMonitorWithTimeout(monitor,5000);test.removeMonitor(monitor);
        check(timetable!=null&&timetable.getWindow().getDecorView().findViewWithTag("timetable-title")!=null,"Today timetable button opens the real offline schedule");capture("timetable.png");ui(timetable::finish);settle();
        close();open(AttendanceManagementActivity.class);
        check(value("course-total").contains("120일"),"Course weekdays exclude Korean holidays including substitutes");
        check(value("attendance-current-rate").equals("—")&&value("attendance-missing").contains("확인된 0일"),"Missing records are not silently counted as absence or confirmed presence");
        check(value("attendance-remaining").contains("24일")&&value("attendance-forecast-assumption").contains("모두 출석"),"Remaining absence budget displays its optimistic assumption");
        check(p.lateAfter().isEmpty(),"Unknown late threshold remains unset");
        ui(()->{p.saveManualAttendance(LocalDate.of(2026,8,31),MonthAttendance.ManualStatus.LATE);p.saveManualAttendance(LocalDate.of(2026,9,1),MonthAttendance.ManualStatus.LATE_EARLY);});settle();
        check(value("attendance-current-rate").equals("50.0%")&&value("attendance-counts").contains("지각 2회")&&value("attendance-counts").contains("조퇴 1회"),"Three combined occurrences convert to one absence across two known dates");
        check(value("attendance-remaining").contains("23일")&&value("attendance-equivalent").contains("환산 1일"),"Attendance budget updates after calendar corrections");
        capture("management-top.png");
        ui(()->view("attendance-late-threshold").performClick());settle();check(!activity.hasWindowFocus(),"Late threshold opens its modal time picker");test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);settle();check(p.lateAfter().isEmpty(),"Cancelling the picker never saves a guessed threshold");
        ui(()->{View v=view("attendance-late-threshold");while(v!=null&&!(v instanceof ScrollView))v=(v.getParent() instanceof View)?(View)v.getParent():null;if(v instanceof ScrollView)((ScrollView)v).fullScroll(View.FOCUS_DOWN);});settle();capture("management-bottom.png");
        check(!p.enabled()&&!MonitorService.running&&!ChromeAttendanceService.busy(),"Management UI never enables reservations or launches attendance");
        restore();result.putString("stream","PASS: "+count+" attendance-management UI checks. No attendance request performed.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable error){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+error+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private void open(Class<? extends Activity> type){activity=test.startActivitySync(new Intent(c,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();}
    private void close(){if(activity!=null){ui(()->activity.finish());activity=null;settle();}}
    private View view(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
    private String value(String tag){return ((TextView)view(tag)).getText().toString();}
    private AccessibilityNodeInfo node(String value){var root=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();if(root==null)return null;for(var n:root.findAccessibilityNodeInfosByText(value))if(value.contentEquals(n.getText()))return n;return null;}
    private void clickText(String value){var n=node(value);if(n==null||!n.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new AssertionError("Dialog button: "+value);settle();}
    private void settle(){SystemClock.sleep(500);test.waitForIdleSync();}
    private void ui(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);count++;Bundle status=new Bundle();status.putString("stream","PASS: "+message+"\n");test.sendStatus(0,status);}
    private void capture(String name)throws Exception{SystemClock.sleep(500);var bitmap=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();if(bitmap==null)throw new AssertionError("Screenshot unavailable");File dir=new File(c.getExternalFilesDir(null),"management-screens");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    @SuppressWarnings("unchecked")private void restore(){close();if(c==null)return;ui(()->{for(var group:saved.entrySet()){var edit=c.getSharedPreferences(group.getKey(),0).edit().clear();for(var item:group.getValue().entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));}edit.commit();}AlarmScheduler.arm(c,false);});}
}
