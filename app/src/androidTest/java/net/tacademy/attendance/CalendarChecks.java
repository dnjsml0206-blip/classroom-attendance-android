package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.*;
import java.util.*;

/** Real toggle actions against isolated emulator preferences; no attendance or network work. */
final class CalendarChecks {
    private final Instrumentation test;
    private Context context;
    private Prefs prefs;
    private Map<String,?> saved;
    private AttendanceCalendarActivity activity;
    private int checks;
    CalendarChecks(Instrumentation test){this.test=test;}
    void run(){
        Bundle result=new Bundle();
        try{
            if(!Build.HARDWARE.equals("ranchu")&&!Build.HARDWARE.equals("goldfish"))throw new AssertionError("Emulator only");
            context=test.getTargetContext();prefs=new Prefs(context);saved=new HashMap<>(prefs.raw.getAll());
            LocalDate today=LocalDate.now(Policy.ZONE),start=LocalDate.of(2026,8,31),observed=today.minusDays(1);
            if(!start.isBefore(today))throw new AssertionError("Run after the course start");
            main(()->{
                context.stopService(new Intent(context,MonitorService.class));AlarmScheduler.cancel(context);
                prefs.raw.edit().clear().putBoolean("initialized",true).putInt("schedule_version",2).putBoolean("chrome_backend",true)
                    .putBoolean("enabled",false).putBoolean("auto",false).putStringSet("ssids",Set.of("__CALENDAR_NO_MATCH__"))
                    .putStringSet("days",Set.of("1","2","3","4","5")).putStringSet("excluded",Set.of())
                    .putString("record_day",today.toString()).putString("in","08:41:00").putString("out","")
                    .putString("record:"+today,"08:41:00|").putString("record:"+observed,"08:40:00|15:30:00")
                    .putString("log:"+today,"08:41  입실 완료").putString("chrome_pending","sentinel")
                    .putLong("next_alarm",123456).commit();
            });
            open(start,HomeTheme.WARM);
            check(value("calendar-month").equals("2026년 8월")&&view("calendar-status-toggles")!=null,"Course start before installation offers immediate status toggles");
            check(value("calendar-selected-status").equals("미확인")&&!view("manual-status:PRESENT").isSelected(),"No attendance is preselected");
            choose(MonthAttendance.ManualStatus.PRESENT);
            check(prefs.manualAttendance(start)==MonthAttendance.ManualStatus.PRESENT&&value("calendar-selected-status").equals("출석")&&view("manual-status:PRESENT").isSelected(),"One tap saves and selects presence without a dialog");
            check(value("calendar-summary").contains("출석 1일")&&view("calendar-times").getVisibility()==View.GONE,"Manual-only attendance counts without displaying invented times");
            check(view("manual-in")==null&&view("manual-out")==null&&view("calendar-edit")==null,"No time fields or separate save editor remain");
            close();open(start,HomeTheme.WARM);
            check(view("manual-status:PRESENT").isSelected()&&value("calendar-source").contains("직접 선택"),"Reopening preserves the toggle and source label");
            choose(MonthAttendance.ManualStatus.EARLY_LEAVE);
            check(!view("manual-status:PRESENT").isSelected()&&view("manual-status:EARLY_LEAVE").isSelected()&&value("calendar-summary").contains("조퇴 1일"),"Choosing a different status switches exclusively and updates the month");
            choose(MonthAttendance.ManualStatus.LATE);
            check(value("calendar-selected-status").equals("지각")&&value("calendar-summary").contains("지각 1일"),"Late attendance can be toggled without a time field");
            choose(MonthAttendance.ManualStatus.LATE_EARLY);
            check(value("calendar-selected-status").equals("지각·조퇴")&&value("calendar-summary").contains("지각 1일")&&value("calendar-summary").contains("조퇴 1일"),"Combined late and early counts both occurrences");
            choose(MonthAttendance.ManualStatus.ABSENT);
            check(value("calendar-selected-status").equals("결석")&&value("calendar-summary").contains("결석 1일"),"Absence saves immediately");
            choose(MonthAttendance.ManualStatus.ABSENT);
            check(prefs.manualAttendance(start)==null&&value("calendar-selected-status").equals("미확인"),"Tapping the active status again turns it off");
            main(()->prefs.raw.edit().putString("manual_attendance:"+start,"PRESENT").putString("manual_times:"+start,"08:30|17:50").commit());settle();
            check(view("calendar-times").getVisibility()==View.GONE,"Legacy manually entered times never become observed times");
            choose(MonthAttendance.ManualStatus.EARLY_LEAVE);
            check(!prefs.raw.contains("manual_times:"+start),"Changing a legacy status discards only its obsolete manual-time override");
            capture("calendar-toggles-warm.png");
            close();open(observed,HomeTheme.NIGHT);choose(MonthAttendance.ManualStatus.EARLY_LEAVE);
            check(value("calendar-in").equals("08:40:00")&&value("calendar-out").equals("15:30:00")&&view("calendar-times").getVisibility()==View.VISIBLE,"Observed times remain visible alongside a selected status");
            capture("calendar-toggles-night.png");
            choose(MonthAttendance.ManualStatus.EARLY_LEAVE);
            check(value("calendar-selected-status").equals("조퇴")&&prefs.history(observed).out().equals("15:30:00"),"Turning off the correction restores the observed status and keeps its times");
            close();open(today,HomeTheme.WARM);
            main(()->prefs.raw.edit().putString("manual_attendance:"+today,"PRESENT").putString("manual_times:"+today,"07:00|18:00").commit());settle();
            check(value("calendar-in").equals("08:41:00")&&value("calendar-out").equals("—"),"Legacy manual times cannot override actual app records");
            choose(MonthAttendance.ManualStatus.ABSENT);
            check(prefs.checkedInToday()&&!prefs.checkedOutToday()&&prefs.history(today).out().isEmpty(),"Changing today's local status never fabricates an automatic completion");
            choose(MonthAttendance.ManualStatus.ABSENT);
            check(prefs.manualAttendance(today)==null&&value("calendar-in").equals("08:41:00"),"Clearing today's local status preserves its observed time");
            close();open(today.plusDays(1),HomeTheme.WARM);
            check(view("calendar-status-toggles")==null,"Future days cannot be toggled");
            boolean[] rejected={false};main(()->{
                try{prefs.saveManualAttendance(today.plusDays(1),MonthAttendance.ManualStatus.PRESENT);}catch(IllegalArgumentException e){rejected[0]=true;}
            });
            check(rejected[0],"Storage rejects future manual dates");
            check(prefs.checkedInToday()&&!prefs.checkedOutToday()&&prefs.history(today).in().equals("08:41:00"),"Historical editing never changes today's confirmed attendance");
            check(prefs.raw.getLong("next_alarm",0)==123456&&prefs.raw.getString("chrome_pending","").equals("sentinel")&&!prefs.enabled()&&!prefs.auto(),"Historical editing leaves reservations and click state untouched");
            check(prefs.todayLog().equals("08:41  입실 완료")&&!MonitorService.running,"Historical edits do not add today's operational logs or start monitoring");
            restore();result.putString("stream","PASS: "+checks+" historical calendar checks. No attendance request performed.\n");test.finish(Activity.RESULT_OK,result);
        }catch(Throwable error){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+error+"\n");test.finish(Activity.RESULT_CANCELED,result);}
    }
    private void open(LocalDate date,HomeTheme theme){activity=(AttendanceCalendarActivity)test.startActivitySync(new Intent(context,AttendanceCalendarActivity.class).putExtra("initial_date",date.toString()).putExtra("theme",theme.name()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();}
    private void close(){if(activity!=null){main(()->activity.finish());activity=null;settle();}}
    private void choose(MonthAttendance.ManualStatus status){main(()->view("manual-status:"+status.name()).performClick());settle();}
    private View view(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
    private String value(String tag){return ((TextView)view(tag)).getText().toString();}
    private String description(LocalDate date){return view("calendar-day:"+date).getContentDescription().toString();}
    private void settle(){SystemClock.sleep(250);test.waitForIdleSync();}
    private void main(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;Bundle progress=new Bundle();progress.putString("stream","PASS: "+message+"\n");test.sendStatus(0,progress);}
    private void capture(String name)throws Exception{
        main(()->((ScrollView)view("calendar-detail").getParent().getParent()).fullScroll(View.FOCUS_DOWN));
        SystemClock.sleep(2300);test.waitForIdleSync();
        Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("Screenshot unavailable");
        File dir=new File(context.getExternalFilesDir(null),"calendar-screens");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Screenshot directory");
        try(FileOutputStream out=new FileOutputStream(new File(dir,name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
    @SuppressWarnings("unchecked")private void restore(){
        close();if(context==null||saved==null)return;
        main(()->{SharedPreferences.Editor edit=prefs.raw.edit().clear();for(Map.Entry<String,?> item:saved.entrySet()){
            String key=item.getKey();Object value=item.getValue();
            if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);
            else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);
            else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Set)edit.putStringSet(key,new HashSet<>((Set<String>)value));
        }if(!edit.commit())throw new AssertionError("Preference restore failed");AlarmScheduler.arm(context,false);});
    }
}
