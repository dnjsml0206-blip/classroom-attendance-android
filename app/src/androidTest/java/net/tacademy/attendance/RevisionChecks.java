package net.tacademy.attendance;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.time.*;
final class RevisionChecks {
    private final Instrumentation test;private Context c;private Prefs p;private Activity activity;private Map<String,?> saved;private int count;
    RevisionChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Set.of("ranchu","goldfish").contains(Build.HARDWARE))throw new AssertionError("Emulator only");
        c=test.getTargetContext();p=new Prefs(c);saved=new HashMap<>(p.raw.getAll());
        String today=LocalDate.now(Policy.ZONE).toString();
        ui(()->{ChromeAttendanceService.cancelWork();AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));p.raw.edit().clear().putBoolean("initialized",true).putInt("schedule_version",2).putBoolean("chrome_backend",true).putBoolean(PersonalAttendanceCorrection.MARKER,true).putBoolean("enabled",false).putBoolean("auto",false).putBoolean("guidance_notifications",false).putStringSet("days",Set.of("1","2","3","4","5")).putString("record:"+today,"08:42|").putString("chrome_pending","revision-sentinel").commit();});
        activity=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
        check(view("attendance-read-record").getParent()==view("today-attendance-status").getParent(),"Record lookup is next to the attendance status");
        check(view("attendance-calendar").getParent()==view("today-attendance-heading"),"Calendar link is in the Today attendance heading");
        ui(()->view("today-attendance-heading").requestRectangleOnScreen(new android.graphics.Rect(0,0,view("today-attendance-heading").getWidth(),500),true));settle();capture("01-today.png");
        close();LocalDate day=LocalDate.of(2026,9,2);ui(()->p.raw.edit().putString("record:"+day,"09:12|15:00").commit());openCalendar(day.toString());
        check(view("manual-status:LATE_EARLY")==null&&view("manual-status:LATE").isSelected()&&view("manual-status:EARLY_LEAVE").isSelected(),"Observed combined status selects two separate buttons; no combined button");
        click("manual-status:LATE");check(p.manualAttendance(day)==MonthAttendance.ManualStatus.EARLY_LEAVE,"Late toggle off retains early leave");
        click("manual-status:LATE");check(p.manualAttendance(day)==MonthAttendance.ManualStatus.LATE_EARLY,"Late toggle on combines with early leave");capture("02-calendar-multiple.png");
        click("manual-status:EARLY_LEAVE");click("manual-status:LATE");check(p.manualAttendance(day)==MonthAttendance.ManualStatus.PRESENT,"Turning both off explicitly clears the inferred penalties");
        click("manual-status:VACATION");check(p.manualAttendance(day)==MonthAttendance.ManualStatus.VACATION&&p.automationExcluded().contains(day.toString())&&!p.excluded().contains(day.toString()),"Leave is credited in class totals and excluded only from automation");
        check(p.history(day).in().equals("09:12")&&p.history(day).out().equals("15:00"),"Leave does not invent or delete observed times");
        check(!p.saveManualAttendance(day.plusDays(1),MonthAttendance.ManualStatus.VACATION),"A second leave before the next grant is rejected");capture("03-calendar-vacation.png");
        click("manual-status:VACATION");check(!p.vacations().contains(day)&&p.saveManualAttendance(day.plusDays(1),MonthAttendance.ManualStatus.VACATION),"Cancelling leave returns the allowance");
        close();ui(()->p.raw.edit().putString("record:2026-09-04","09:30|").commit());openCalendar("2026-09-04");
        check(view("manual-status:LATE").isSelected(),"Known late entry stays selected when exit time is missing");click("manual-status:EARLY_LEAVE");
        check(p.manualAttendance(LocalDate.of(2026,9,4))==MonthAttendance.ManualStatus.LATE_EARLY,"Adding early leave preserves a late entry from partial records");
        close();openCalendar("2027-02-26");check(view("manual-status:VACATION")!=null&&view("manual-status:LATE")==null,"Future class days offer only leave booking");
        click("manual-status:VACATION");check(p.manualAttendance(CourseCalendar.END)==MonthAttendance.ManualStatus.VACATION&&value("calendar-selected-status").equals("휴가"),"Future leave is visibly booked");
        ui(()->p.raw.edit().putStringSet("excluded",Set.of(CourseCalendar.END.toString())).commit());settle();
        check(view("manual-status:VACATION")!=null&&value("calendar-selected-status").equals("휴강"),"A leave booking turned into a holiday remains cancellable without using allowance");
        click("manual-status:VACATION");check(p.manualAttendance(CourseCalendar.END)==null,"Future leave can be cancelled");
        ui(()->p.raw.edit().putStringSet("excluded",Set.of()).commit());
        close();activity=test.startActivitySync(new Intent(c,AttendanceManagementActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
        check(value("attendance-current-rate").equals(p.attendanceSummary().currentDisplay())&&value("attendance-missing").contains("종료된 수업"),"Management uses ended class days as denominator");
        check(value("attendance-remaining").contains("또는")&&value("attendance-remaining").contains("지각·조퇴"),"Absence and combined-event limits are alternative maxima");capture("04-management.png");
        ui(()->view("vacation-rule").requestRectangleOnScreen(new android.graphics.Rect(0,0,view("vacation-rule").getWidth(),view("vacation-rule").getHeight()),true));settle();
        check(value("vacation-counts").contains("사용 1일"),"Vacation management displays used allowance");capture("05-vacation-management.png");
        check(!p.enabled()&&!MonitorService.running&&!ChromeAttendanceService.busy()&&p.raw.getString("chrome_pending","").equals("revision-sentinel"),"Editing records never submits attendance or enables automation");
        restore();result.putString("stream","PASS: "+count+" revision checks; no attendance network access.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private void openCalendar(String day){activity=test.startActivitySync(new Intent(c,AttendanceCalendarActivity.class).putExtra("initial_date",day).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();}
    private void close(){if(activity!=null){ui(activity::finish);activity=null;settle();}}
    private View view(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
    private String value(String tag){return ((TextView)view(tag)).getText().toString();}
    private void click(String tag){ui(()->view(tag).performClick());settle();}
    private void settle(){SystemClock.sleep(450);test.waitForIdleSync();}
    private void ui(Runnable r){Throwable[] error={null};test.runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);count++;Bundle b=new Bundle();b.putString("stream","PASS: "+msg+"\n");test.sendStatus(0,b);}
    private void capture(String name)throws Exception{SystemClock.sleep(300);Bitmap b=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();if(b==null)throw new AssertionError("Screenshot unavailable");File dir=new File(c.getExternalFilesDir(null),"revision-screens");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @SuppressWarnings("unchecked")private void restore(){close();if(c==null||saved==null)return;ui(()->{var edit=p.raw.edit().clear();for(var item:saved.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));}edit.commit();AlarmScheduler.arm(c,false);});}
}
