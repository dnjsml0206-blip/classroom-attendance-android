package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.time.*;
import java.util.*;

/** Real editor actions against isolated emulator preferences; no attendance or network work. */
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
            check(value("calendar-month").equals("2026년 8월")&&view("calendar-edit")!=null,"Pre-install course start on August 31 is editable");
            check(value("calendar-selected-status").equals("미확인"),"Unrecorded past attendance stays unknown");
            edit();check(!dialog().getButton(AlertDialog.BUTTON_POSITIVE).isEnabled(),"No attendance status is silently preselected");
            choose(MonthAttendance.ManualStatus.PRESENT);main(()->dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick());settle();
            check(prefs.manualAttendance(start)==null,"Cancel does not persist a chosen status");
            edit();choose(MonthAttendance.ManualStatus.PRESENT);save();
            check(value("calendar-selected-status").equals("출석")&&description(start).contains("직접 입력"),"Saved presence appears in the selected day and month tile with its source");
            check(value("calendar-summary").contains("출석 1일")&&value("calendar-in").equals("—")&&value("calendar-out").equals("—"),"Monthly totals include manual presence without invented times");
            check(new Prefs(context).manualAttendance(start)==MonthAttendance.ManualStatus.PRESENT,"Saved annotation is available through a fresh preferences instance");
            close();open(start,HomeTheme.WARM);
            check(value("calendar-selected-status").equals("출석")&&value("calendar-source").contains("직접 입력"),"Reopening the calendar preserves the annotation");
            edit();check(((RadioButton)dialog().findViewById(checkedOptionId(MonthAttendance.ManualStatus.PRESENT))).isChecked(),"Editing preselects the saved status");
            choose(MonthAttendance.ManualStatus.EARLY_LEAVE);save();
            check(value("calendar-selected-status").equals("조퇴")&&value("calendar-summary").contains("조퇴 1일")&&value("calendar-summary").contains("출석 0일"),"Changing presence to early leave replaces the monthly category");
            edit();choose(MonthAttendance.ManualStatus.ABSENT);save();
            check(value("calendar-selected-status").equals("결석")&&value("calendar-summary").contains("결석 1일"),"Explicit absence is displayed and counted");
            edit();main(()->dialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick());settle();
            check(prefs.manualAttendance(start)==null&&value("calendar-selected-status").equals("미확인"),"Deleting manual-only attendance restores unknown status");
            edit();choose(MonthAttendance.ManualStatus.PRESENT);save();capture("calendar-manual-warm.png");
            edit();capture("calendar-manual-editor.png");main(()->dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick());settle();
            close();open(observed,HomeTheme.NIGHT);edit();choose(MonthAttendance.ManualStatus.EARLY_LEAVE);save();
            check(value("calendar-selected-status").equals("조퇴")&&value("calendar-in").equals("08:40:00")&&value("calendar-out").equals("15:30:00"),"Manual early leave preserves observed entry and exit times");capture("calendar-manual-night.png");
            edit();main(()->dialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick());settle();
            check(value("calendar-selected-status").equals("완료")&&prefs.history(observed).out().equals("15:30:00"),"Deleting an annotation restores the original observed record");
            close();open(today,HomeTheme.WARM);
            check(view("calendar-edit")==null,"Today does not expose historical editing");
            close();open(today.plusDays(1),HomeTheme.WARM);
            check(view("calendar-edit")==null,"Future dates do not expose historical editing");
            boolean[] rejected={false,false};main(()->{
                try{prefs.saveManualAttendance(today,MonthAttendance.ManualStatus.ABSENT);}catch(IllegalArgumentException e){rejected[0]=true;}
                try{prefs.saveManualAttendance(today.plusDays(1),MonthAttendance.ManualStatus.PRESENT);}catch(IllegalArgumentException e){rejected[1]=true;}
            });
            check(rejected[0]&&rejected[1]&&prefs.manualAttendance(today)==null,"Storage also rejects today and future dates");
            check(prefs.checkedInToday()&&!prefs.checkedOutToday()&&prefs.history(today).in().equals("08:41:00"),"Historical editing never changes today's confirmed attendance");
            check(prefs.raw.getLong("next_alarm",0)==123456&&prefs.raw.getString("chrome_pending","").equals("sentinel")&&!prefs.enabled()&&!prefs.auto(),"Historical editing leaves reservations and click state untouched");
            check(prefs.todayLog().equals("08:41  입실 완료")&&!MonitorService.running,"Historical edits do not add today's operational logs or start monitoring");
            restore();result.putString("stream","PASS: "+checks+" historical calendar checks. No attendance request performed.\n");test.finish(Activity.RESULT_OK,result);
        }catch(Throwable error){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+error+"\n");test.finish(Activity.RESULT_CANCELED,result);}
    }
    private void open(LocalDate date,HomeTheme theme){activity=(AttendanceCalendarActivity)test.startActivitySync(new Intent(context,AttendanceCalendarActivity.class).putExtra("initial_date",date.toString()).putExtra("theme",theme.name()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();}
    private void close(){if(activity!=null){main(()->activity.finish());activity=null;settle();}}
    private void edit(){main(()->view("calendar-edit").performClick());settle();}
    private void choose(MonthAttendance.ManualStatus status){main(()->((RadioButton)dialog().getWindow().getDecorView().findViewWithTag("manual-status:"+status.name())).performClick());settle();}
    private int checkedOptionId(MonthAttendance.ManualStatus status){return dialog().getWindow().getDecorView().findViewWithTag("manual-status:"+status.name()).getId();}
    private void save(){main(()->dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick());settle();}
    private AlertDialog dialog(){try{Field field=AttendanceCalendarActivity.class.getDeclaredField("manualDialog");field.setAccessible(true);return (AlertDialog)field.get(activity);}catch(Exception e){throw new AssertionError(e);}}
    private View view(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
    private String value(String tag){return ((TextView)view(tag)).getText().toString();}
    private String description(LocalDate date){return view("calendar-day:"+date).getContentDescription().toString();}
    private void settle(){SystemClock.sleep(250);test.waitForIdleSync();}
    private void main(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;Bundle progress=new Bundle();progress.putString("stream","PASS: "+message+"\n");test.sendStatus(0,progress);}
    private void capture(String name)throws Exception{
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
