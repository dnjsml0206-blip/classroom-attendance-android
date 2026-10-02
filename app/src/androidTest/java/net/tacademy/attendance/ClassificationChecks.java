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
final class ClassificationChecks {
    private final Instrumentation test;private Context c;private Prefs p;private Activity activity;private Map<String,?> saved;private int count;
    ClassificationChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Set.of("ranchu","goldfish").contains(Build.HARDWARE))throw new AssertionError("Emulator only");
        c=test.getTargetContext();p=new Prefs(c);saved=new HashMap<>(p.raw.getAll());
        ui(()->{ChromeAttendanceService.cancelWork();AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));p.raw.edit().clear().putBoolean("initialized",true).putInt("schedule_version",2).putBoolean("chrome_backend",true).putBoolean("enabled",false).putBoolean("auto",false).putBoolean("guidance_notifications",false).putStringSet("days",Set.of("1","2","3","4","5")).putString("checkout","17:50").putString("chrome_pending","classification-sentinel").commit();});
        check(PersonalAttendanceCorrection.apply(p.raw,false)&&p.manualAttendance(LocalDate.of(2026,9,28))==null,"General builds do not receive the owner's personal records");
        activity=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
        View timetable=view("attendance-timetable"),orientation=view("attendance-orientation"),row=view("attendance-materials-row");
        check(timetable.getParent()==row&&orientation.getParent()==row&&timetable.getRight()<orientation.getLeft()&&timetable.getTop()==orientation.getTop()&&timetable.getHeight()==orientation.getHeight(),"Material buttons form an equal-height non-overlapping left/right row");
        ui(()->row.requestRectangleOnScreen(new android.graphics.Rect(0,0,row.getWidth(),row.getHeight()),true));settle();capture("00-material-buttons.png");
        check(p.manualAttendance(LocalDate.of(2026,9,28))==MonthAttendance.ManualStatus.EARLY_LEAVE&&p.manualAttendance(LocalDate.of(2026,9,30))==MonthAttendance.ManualStatus.LATE,"Opening the creator update applies both user-confirmed corrections");
        check(p.history(LocalDate.of(2026,9,28))==null&&p.history(LocalDate.of(2026,9,30))==null,"Missing entry and exit times are never invented");
        check(p.lateAfter().equals("09:11"),"User-confirmed late cutoff is installed as 09:11");
        var summary=p.attendanceSummary();check(summary.early()==1&&summary.late()==1&&summary.known()==2&&summary.remainder()==2,"Confirmed late and early days appear in attendance totals");
        close();openCalendar("2026-09-28");check(value("calendar-selected-status").equals("조퇴")&&view("calendar-times").getVisibility()==View.GONE,"September 28 shows early leave without fabricated times");check(value("calendar-source").contains("사용자 확인"),"Calendar identifies the user's confirmation as the source");capture("01-early-leave.png");
        close();openCalendar("2026-09-30");check(value("calendar-selected-status").equals("지각"),"September 30 shows late attendance");capture("02-late.png");
        ui(()->p.saveManualAttendance(LocalDate.of(2026,9,28),MonthAttendance.ManualStatus.PRESENT));PersonalAttendanceCorrection.apply(p.raw,true);
        check(p.manualAttendance(LocalDate.of(2026,9,28))==MonthAttendance.ManualStatus.PRESENT&&!p.raw.contains("manual_source:2026-09-28"),"Later manual edits are not overwritten by reopening or reapplying the update");
        LocalDate sample=LocalDate.of(2026,9,29);
        ui(()->p.raw.edit().putString("record:"+sample,"09:10:59|17:50").commit());check(p.calendarState(sample,LocalDate.of(2026,10,1))==MonthAttendance.State.ATTENDED,"09:10:59 is normal attendance");
        ui(()->p.raw.edit().putString("record:"+sample,"09:11:00|17:50").commit());check(p.calendarState(sample,LocalDate.of(2026,10,1))==MonthAttendance.State.LATE,"09:11:00 is late without requiring an extra second");
        close();openCalendar(sample.toString());check(value("calendar-classification-basis").contains("09:11부터")&&value("calendar-classification-basis").contains("17:50 이전"),"Calendar shows the actual late and early thresholds");capture("03-classification-basis.png");
        ui(()->p.raw.edit().putBoolean("late_disabled",true).commit());settle();check(value("calendar-selected-status").equals("판정대기"),"Disabled late classification does not label timestamps as completed attendance");
        ui(()->p.raw.edit().putString("record:"+sample,"08:50|14:00").commit());settle();check(value("calendar-selected-status").equals("조퇴"),"Early leave remains visible even when late classification is disabled");
        check(!p.enabled()&&!p.auto()&&!MonitorService.running&&!ChromeAttendanceService.busy()&&p.raw.getString("chrome_pending","").equals("classification-sentinel"),"Record corrections preserve automation and pending requests");
        restore();result.putString("stream","PASS: "+count+" classification checks; no attendance network access.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private void openCalendar(String day){activity=test.startActivitySync(new Intent(c,AttendanceCalendarActivity.class).putExtra("initial_date",day).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();}
    private void close(){if(activity!=null){ui(activity::finish);activity=null;settle();}}
    private View view(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
    private String value(String tag){return ((TextView)view(tag)).getText().toString();}
    private void click(String tag){ui(()->view(tag).performClick());settle();}
    private void settle(){SystemClock.sleep(450);test.waitForIdleSync();}
    private void ui(Runnable r){Throwable[] error={null};test.runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);count++;Bundle b=new Bundle();b.putString("stream","PASS: "+msg+"\n");test.sendStatus(0,b);}
    private void capture(String name)throws Exception{SystemClock.sleep(300);Bitmap b=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();if(b==null)throw new AssertionError("Screenshot unavailable");File dir=new File(c.getExternalFilesDir(null),"classification-screens");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @SuppressWarnings("unchecked")private void restore(){close();if(c==null||saved==null)return;ui(()->{var edit=p.raw.edit().clear();for(var item:saved.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));}edit.commit();AlarmScheduler.arm(c,false);});}
}
