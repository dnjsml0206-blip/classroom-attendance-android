package net.tacademy.attendance;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
/** Offline emulator checks using isolated schedule preferences. */
final class TimetableChecks {
    private final Instrumentation test;private Context c;private Prefs p;private Activity activity;private Map<String,?> saved;private int count;
    TimetableChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Set.of("ranchu","goldfish").contains(Build.HARDWARE))throw new AssertionError("Emulator only");
        c=test.getTargetContext();p=new Prefs(c);saved=new HashMap<>(p.raw.getAll());
        ui(()->{ChromeAttendanceService.cancelWork();AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));p.raw.edit().putBoolean("enabled",false).putBoolean("auto",false).putString("end","09:30").putString("checkout","17:50").putStringSet("days",Set.of("1","2","3","4","5")).putStringSet("excluded",Set.of()).putString("chrome_pending","timetable-sentinel").commit();});
        open("2026-10-01");check(value("timetable-subject").equals("머신러닝")&&value("timetable-hours").contains("8시간"),"October first shows the actual subject and source hours");
        check(value("timetable-month-summary").contains("20일 · 152시간"),"Monthly source totals match October");capture("01-monthly.png");
        click("timetable-day:2026-10-05");check(value("timetable-subject").equals("개천절 대체공휴일"),"Substitute holiday is visible as a day off");capture("02-holiday.png");
        click("timetable-day:2026-10-06");check(value("timetable-subject").equals("태블로 시각화")&&value("timetable-hours").contains("6시간"),"Short special-class hours are not replaced with eight hours");
        check(value("timetable-checkout").contains("16:00 종료")&&value("timetable-checkout").contains("15:50:02"),"October sixth displays the short-day checkout boundary");capture("06-short-day.png");
        click("timetable-day:2026-10-07");check(value("timetable-checkout").contains("15:50:02"),"October seventh uses the same short-day rule");
        click("timetable-day:2026-10-08");check(value("timetable-checkout").contains("17:50:05"),"Normal-day display returns to the configured checkout time");
        ui(()->p.raw.edit().putString("record:2026-10-06","08:50|15:50").commit());
        check(p.calendarState(java.time.LocalDate.of(2026,10,6),java.time.LocalDate.of(2026,10,8))==MonthAttendance.State.COMPLETE,"Calendar does not mark normal shortened-day exit as early leave");
        click("timetable-day:2026-10-01");ui(()->p.raw.edit().putStringSet("excluded",Set.of("2026-10-01")).commit());settle();
        check(value("timetable-subject").equals("추가로 등록한 휴강일"),"User holidays update the timetable without erasing the original lesson");ui(()->p.raw.edit().putStringSet("excluded",Set.of()).commit());settle();
        click("timetable-outline");check(view("timetable-module:LLM")!=null&&view("timetable-module:실전 프로젝트")!=null,"All-course list groups teaching days and project phases");capture("03-outline.png");
        click("timetable-module:LLM");check(value("timetable-subject").equals("LLM")&&view("timetable-monthly").isSelected(),"A course opens its dated monthly lesson");
        close();open("2027-02-05");check(value("timetable-subject").equals("과정 휴강"),"February fifth course break is loaded from the PDF");
        click("timetable-day:2027-02-26");check(value("timetable-subject").equals("현직자 멘토링")&&!view("timetable-next").isEnabled(),"Last course date and navigation boundary match the original");capture("04-final-month.png");
        close();open("2026-08-31");check(!view("timetable-prev").isEnabled(),"First-month navigation cannot leave the course range");
        click("timetable-source");clickText("원본 PDF 보기");
        Field field=TimetableActivity.class.getDeclaredField("sourcePdf");field.setAccessible(true);Dialog dialog=(Dialog)field.get(activity);
        long until=SystemClock.elapsedRealtime()+10000;while(findPdf(dialog.getWindow().getDecorView())==null&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(150);
        View pdf=findPdf(dialog.getWindow().getDecorView());check(pdf!=null,"Bundled PDF renders offline inside the app");capture("05-original.png");
        Field scale=pdf.getClass().getDeclaredField("scale");scale.setAccessible(true);float initial=scale.getFloat(pdf);
        ui(()->{long now=SystemClock.uptimeMillis();for(int i=0;i<2;i++){MotionEvent down=MotionEvent.obtain(now+i*100,now+i*100,MotionEvent.ACTION_DOWN,pdf.getWidth()/2f,pdf.getHeight()/2f,0),up=MotionEvent.obtain(now+i*100,now+i*100+40,MotionEvent.ACTION_UP,pdf.getWidth()/2f,pdf.getHeight()/2f,0);pdf.dispatchTouchEvent(down);pdf.dispatchTouchEvent(up);down.recycle();up.recycle();}});settle();
        check(scale.getFloat(pdf)>initial,"Original PDF supports double-tap zoom");ui(dialog::dismiss);
        check(!p.enabled()&&!p.auto()&&!MonitorService.running&&!ChromeAttendanceService.busy()&&"timetable-sentinel".equals(p.raw.getString("chrome_pending","")),"Browsing never changes attendance requests, reservations or pending clicks");
        restore();result.putString("stream","PASS: "+count+" timetable checks; no attendance network access.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private View findPdf(View v){if(v.getClass().getName().endsWith("$PdfImage"))return v;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View child=findPdf(g.getChildAt(i));if(child!=null)return child;}return null;}
    private void clickText(String text){var root=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();boolean clicked=false;for(var node:root.findAccessibilityNodeInfosByText(text))if(text.equals(String.valueOf(node.getText()))){clicked=node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);if(clicked)break;}if(!clicked)throw new AssertionError("Missing dialog button: "+text);settle();}
    private void open(String date){activity=test.startActivitySync(new Intent(c,TimetableActivity.class).putExtra("initial_date",date).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();}
    private void close(){if(activity!=null){ui(activity::finish);activity=null;settle();}}
    private View view(String tag){return activity.getWindow().getDecorView().findViewWithTag(tag);}
    private String value(String tag){return ((TextView)view(tag)).getText().toString();}
    private void click(String tag){ui(()->view(tag).performClick());settle();}
    private void settle(){SystemClock.sleep(450);test.waitForIdleSync();}
    private void ui(Runnable r){Throwable[] error={null};test.runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);count++;Bundle b=new Bundle();b.putString("stream","PASS: "+msg+"\n");test.sendStatus(0,b);}
    private void capture(String name)throws Exception{SystemClock.sleep(300);Bitmap b=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();if(b==null)throw new AssertionError("Screenshot unavailable");File dir=new File(c.getExternalFilesDir(null),"timetable-screens");dir.mkdirs();try(var out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @SuppressWarnings("unchecked")private void restore(){close();if(c==null||saved==null)return;ui(()->{var edit=p.raw.edit().clear();for(var item:saved.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));}edit.commit();AlarmScheduler.arm(c,false);});}
}
