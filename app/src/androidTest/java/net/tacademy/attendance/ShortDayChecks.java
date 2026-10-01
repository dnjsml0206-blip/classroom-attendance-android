package net.tacademy.attendance;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
final class ShortDayChecks {
    private final Instrumentation test;private Context c;private Prefs p;private Activity activity;private Map<String,?> saved,reminders;private int count;
    ShortDayChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Set.of("ranchu","goldfish").contains(Build.HARDWARE))throw new AssertionError("Emulator only");
        c=test.getTargetContext();p=new Prefs(c);saved=new HashMap<>(p.raw.getAll());reminders=new HashMap<>(ShortDayReminder.store(c).getAll());
        ui(()->{ChromeAttendanceService.cancelWork();AlarmScheduler.cancel(c);ShortDayReminder.cancel(c);c.stopService(new Intent(c,MonitorService.class));ShortDayReminder.store(c).edit().clear().commit();p.raw.edit().putBoolean("enabled",false).putBoolean("auto",false).putBoolean("guidance_notifications",true).putString("start","08:30").putString("end","09:30").putString("checkout","17:50").putStringSet("days",Set.of("1","2","3","4","5")).putStringSet("excluded",Set.of()).putString("chrome_pending","short-day-sentinel").commit();});
        activity=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
        date("2026-10-06");check(view("today-short-day").getVisibility()==View.VISIBLE&&value("today-short-day-text").contains("16:00")&&value("today-short-day-text").contains("15:50:02"),"Six-hour Today banner shows class end and checkout");check(value("today-short-day-text").contains("꺼짐"),"Disabled automation is disclosed without hiding the class reminder");capture("01-six-hour-today.png");
        date("2026-10-26");check(value("today-short-day-text").contains("17:00")&&value("today-short-day-text").contains("16:50:02"),"Seven-hour Today banner applies across the full course");capture("02-seven-hour-today.png");
        Instrumentation.ActivityMonitor monitor=test.addMonitor(TimetableActivity.class.getName(),null,false);click("today-short-day-link");Activity timetable=test.waitForMonitorWithTimeout(monitor,5000);test.removeMonitor(monitor);check(timetable!=null&&((TextView)timetable.getWindow().getDecorView().findViewWithTag("timetable-selected-date")).getText().toString().contains("10월 26일"),"Banner opens the matching dated lesson");ui(timetable::finish);settle();
        date("2026-10-08");check(view("today-short-day").getVisibility()==View.GONE,"Normal days hide the short-day card");
        ui(()->p.raw.edit().putStringSet("excluded",Set.of("2026-10-06")).commit());date("2026-10-06");check(view("today-short-day").getVisibility()==View.GONE,"Extra holidays hide the short-day card");ui(()->p.raw.edit().putStringSet("excluded",Set.of()).commit());
        var due=LocalDate.of(2026,10,6).atTime(8,30).atZone(Policy.ZONE);
        check(!ShortDayReminder.postIfDue(c,p,due.minusSeconds(1)),"Notice is not sent before entry start");
        check(ShortDayReminder.postIfDue(c,p,due),"Short-day notification posts with reservations disabled");
        Notification posted=null;for(var n:c.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==ShortDayReminder.ID)posted=n.getNotification();
        check(posted!=null&&posted.extras.getString(Notification.EXTRA_TITLE).contains("16:00")&&posted.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("15:50:02")&&posted.contentIntent!=null,"Notification contains the correct times and an app entry point");
        check(!ShortDayReminder.postIfDue(c,p,due.plusHours(1)),"Repeated entry events cannot duplicate the day notice");c.getSystemService(NotificationManager.class).cancel(ShortDayReminder.ID);
        check(!ShortDayReminder.postIfDue(c,p,due.plusHours(2)),"Dismissed notices are not repeatedly reposted");
        check(ShortDayReminder.postIfDue(c,p,due.plusDays(1)),"The next short day receives its own reminder");
        ui(()->Notices.guidanceNotifications(c,false));check(!ShortDayReminder.postIfDue(c,p,due.plusDays(20)),"Guidance off suppresses short-day notifications");check(ShortDayReminder.store(c).getLong("next_at",0)==0,"Guidance off cancels the separate reminder alarm");
        ui(()->Notices.guidanceNotifications(c,true));check(ShortDayReminder.store(c).getLong("next_at",0)>System.currentTimeMillis(),"Reminder alarm is scheduled independently of attendance reservations");
        new ShortDayReceiver().onReceive(c,new Intent(c,ShortDayReceiver.class));
        check(!p.enabled()&&!p.auto()&&!MonitorService.running&&!ChromeAttendanceService.busy()&&"short-day-sentinel".equals(p.raw.getString("chrome_pending","")),"Reminder delivery never changes attendance or launches Chrome");
        restore();result.putString("stream","PASS: "+count+" short-day checks; no attendance network access.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private void date(String date)throws Exception{Field clock=MainActivity.class.getDeclaredField("homeClock");clock.setAccessible(true);Method render=MainActivity.class.getDeclaredMethod("render",int.class);render.setAccessible(true);ui(()->{try{clock.set(activity,Clock.fixed(LocalDate.parse(date).atTime(8,45).atZone(Policy.ZONE).toInstant(),Policy.ZONE));render.invoke(activity,0);}catch(Exception e){throw new RuntimeException(e);}});settle();}
    private void close(){if(activity!=null){ui(activity::finish);activity=null;settle();}}
    private View view(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
    private String value(String tag){return ((TextView)view(tag)).getText().toString();}
    private void click(String tag){ui(()->view(tag).performClick());settle();}
    private void settle(){SystemClock.sleep(450);test.waitForIdleSync();}
    private void ui(Runnable r){Throwable[] error={null};test.runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);count++;Bundle b=new Bundle();b.putString("stream","PASS: "+msg+"\n");test.sendStatus(0,b);}
    private void capture(String name)throws Exception{SystemClock.sleep(300);Bitmap b=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();if(b==null)throw new AssertionError("Screenshot unavailable");File dir=new File(c.getExternalFilesDir(null),"short-day-screens");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}

    @SuppressWarnings("unchecked")private void restoreMap(SharedPreferences prefs,Map<String,?> values){var edit=prefs.edit().clear();for(var item:values.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));}edit.commit();}
    private void restore(){close();if(c==null||saved==null)return;ui(()->{ShortDayReminder.cancel(c);c.getSystemService(NotificationManager.class).cancel(ShortDayReminder.ID);restoreMap(p.raw,saved);restoreMap(ShortDayReminder.store(c),reminders);AlarmScheduler.arm(c,false);ShortDayReminder.reconcile(c);});}
}
