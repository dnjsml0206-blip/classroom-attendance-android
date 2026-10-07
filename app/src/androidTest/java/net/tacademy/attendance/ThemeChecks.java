package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;

/** Emulator-only Today-screen integration checks with a UI clock; attendance stays disabled. */
public final class ThemeChecks {
    private final Instrumentation instrumentation;
    ThemeChecks(Instrumentation instrumentation) { this.instrumentation=instrumentation; }
    private Context context;
    private MainActivity activity;
    private Activity calendar;
    private Map<String,?> saved;
    private MutableClock clock;
    private int checks;
    private final LocalDate day = LocalDate.of(2026,9,10);
    void run() {
        Bundle result = new Bundle();
        try {
            if (!Build.HARDWARE.equals("ranchu") && !Build.HARDWARE.equals("goldfish")) throw new AssertionError("Emulator only");
            context = instrumentation.getTargetContext(); Prefs prefs = new Prefs(context); saved = new HashMap<>(prefs.raw.getAll());
            runOnMainSync(() -> {
                context.stopService(new Intent(context,MonitorService.class)); AlarmScheduler.cancel(context);
                prefs.raw.edit().putBoolean("enabled",false).putBoolean("auto",false)
                    .putStringSet("ssids",Set.of("__EMULATOR_NO_MATCH__"))
                    .putString("start","08:30").putString("end","09:30").putString("checkout","17:50")
                    .putString("record:"+day,"08:42:00|")
                    .putString("log:"+day,"08:42  입실 완료\n08:30  입실 감시 시작\n08:20  Wi-Fi 추가\n08:10  출석 시간 변경")
                    .putStringSet("days",Set.of("1","2","3","4","5")).putStringSet("excluded",Set.of(day.minusDays(2).toString()))
                    .putString("record:"+day.minusDays(1),"08:40:00|17:50:00")
                    .remove("record:"+day.minusDays(2)).remove("record:"+day.minusDays(3)).remove("record_day").remove("in").remove("out")
                    .remove("record:"+day.plusDays(1)).remove("log:"+day.plusDays(1)).commit();
            });
            activity = (MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            clock = new MutableClock(day.atTime(8,45).atZone(Policy.ZONE).toInstant());
            runOnMainSync(() -> setField("homeClock",clock)); settle();
            check(theme()==HomeTheme.WARM && value("inTime").equals("08:42:00") && value("outTime").equals("—"),
                "Morning theme preserves confirmed entry and does not invent an exit");
            check(!value("todayLog").contains("감시")&&!value("todayLog").contains("변경")&&!value("todayLog").contains("Wi-Fi"),"Today lists confirmed attendance only, excluding operational and setting logs");
            artworkStates("warm");
            capture("today-warm.png");
            check(value("todayWifi").equals("와이파이 미연결"),"Unmatched Wi-Fi shows the warning label");
            runOnMainSync(()->((ScrollView)field("homeScroll")).fullScroll(View.FOCUS_DOWN));SystemClock.sleep(300);capture("wifi-warning-warm.png");
            View panel=activity.getWindow().getDecorView().findViewWithTag("today-wifi-panel");ViewGroup parent=(ViewGroup)panel.getParent();
            check("today-header".equals(parent.getTag())&&parent.getChildAt(parent.getChildCount()-1)==panel,"Wi-Fi status sits at the right of the Today title");
            runOnMainSync(()->((ScrollView)field("homeScroll")).fullScroll(View.FOCUS_UP));SystemClock.sleep(250);
            android.graphics.Rect bounds=new android.graphics.Rect();check(panel.getGlobalVisibleRect(bounds)&&bounds.right<=activity.getWindow().getDecorView().getWidth(),"Header Wi-Fi badge stays visible within the screen");
            TextView receipt=(TextView)activity.getWindow().getDecorView().findViewWithTag("attendance-entry-time");
            check(receipt.getVisibility()==View.VISIBLE&&receipt.getText().toString().equals("입실  08:42:00")&&receipt.getCurrentTextColor()==0xFF75736F,"Confirmed entry appears in quiet gray directly under the button");
            String observed=NetworkAccess.inspect(context).ssid();check(!observed.isEmpty(),"Emulator Wi-Fi is visible for refresh verification");
            runOnMainSync(()->prefs.ssids(Set.of(observed)));settle();check(value("todayWifi").equals("대기 중"),"Registering connected Wi-Fi refreshes warning to waiting");capture("wifi-waiting.png");
            runOnMainSync(()->{prefs.ssids(Set.of("__EMULATOR_NO_MATCH__"));((ScrollView)field("homeScroll")).fullScroll(View.FOCUS_UP);});settle();
            at(day,16,49,59); check(theme()==HomeTheme.WARM,"Warm immediately before the boundary");
            at(day,16,50,0);
            check(theme()==HomeTheme.NIGHT && value("inTime").equals("08:42:00") && value("todayLog").contains("입실 완료"),
                "Visible screen switches automatically and preserves records and logs");
            check(((TextView)activity.getWindow().getDecorView().findViewWithTag("attendance-entry-time")).getCurrentTextColor()==0xFFA8ADB6,"Night receipt uses a readable soft gray");
            artworkStates("night");
            capture("today-night.png");
            runOnMainSync(()->((ScrollView)field("homeScroll")).fullScroll(View.FOCUS_DOWN));SystemClock.sleep(300);capture("wifi-warning-night.png");
            runOnMainSync(()->((ScrollView)field("homeScroll")).fullScroll(View.FOCUS_UP));
            android.app.Instrumentation.ActivityMonitor calendarMonitor=instrumentation.addMonitor(AttendanceCalendarActivity.class.getName(),null,false);
            runOnMainSync(() -> clickText("달력에서 출결 조회  ›"));
            calendar=instrumentation.waitForMonitorWithTimeout(calendarMonitor,5000);instrumentation.removeMonitor(calendarMonitor);settle();
            check(calendar!=null && calendarValue("calendar-month").equals("2026년 9월"),"Monthly attendance calendar opens from Today");
            check(String.valueOf(calendar.getWindow().getDecorView().findViewWithTag("calendar-day:"+day).getContentDescription()).contains("입실")
                && String.valueOf(calendar.getWindow().getDecorView().findViewWithTag("calendar-day:"+day.minusDays(1)).getContentDescription()).contains("완료")
                && String.valueOf(calendar.getWindow().getDecorView().findViewWithTag("calendar-day:"+day.minusDays(2)).getContentDescription()).contains("휴강"),"Calendar dates show entry, completion and holiday states");
            capture("calendar-night.png");
            runOnMainSync(()->calendar.getWindow().getDecorView().findViewWithTag("calendar-day:"+day.minusDays(1)).performClick());settle();
            check(calendarValue("calendar-in").equals("08:40:00")&&calendarValue("calendar-out").equals("17:50:00"),"Selecting a completed date shows its actual entry and exit times");
            capture("calendar-detail.png");
            runOnMainSync(()->calendar.getWindow().getDecorView().findViewWithTag("calendar-day:"+day.minusDays(3)).performClick());settle();
            check(calendarValue("calendar-in").equals("—")&&calendarValue("calendar-out").equals("—")&&calendarValue("calendar-selected-status").equals("미확인"),"Missing observations show unknown status without fabricated times or absence");
            runOnMainSync(()->calendar.getWindow().getDecorView().findViewWithTag("calendar-prev").performClick());settle();
            check(calendarValue("calendar-month").equals("2026년 8월"),"Previous month navigation works");
            runOnMainSync(()->calendar.getWindow().getDecorView().findViewWithTag("calendar-next").performClick());settle();
            check(calendarValue("calendar-month").equals("2026년 9월"),"Next month navigation works");
            runOnMainSync(()->calendar.finish());calendar=null;settle();
            runOnMainSync(() -> prefs.raw.edit().putString("checkout","19:20").commit()); settle();
            check(theme()==HomeTheme.WARM,"A changed checkout time updates the visible theme");
            at(day,18,20,0); check(theme()==HomeTheme.NIGHT,"Custom checkout boundary switches to night");
            at(day.plusDays(1),0,0,0);
            check(theme()==HomeTheme.WARM && value("inTime").equals("—") && value("todayLog").equals("아직 확인된 출석 기록이 없습니다."),
                "Midnight resets the theme and loads the new day's records");
            check(activity.getWindow().getDecorView().findViewWithTag("attendance-entry-time").getVisibility()==View.GONE,"Next day does not show yesterday's receipt below the button");
            runOnMainSync(() -> activity.moveTaskToBack(true));
            clock.instant=day.atTime(20,0).atZone(Policy.ZONE).toInstant();
            context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); settle();
            check(theme()==HomeTheme.NIGHT,"Returning from the background refreshes the theme");
            runOnMainSync(() -> { prefs.raw.edit().putString("checkout","17:50").commit(); clickText("설정"); }); settle();
            check(findText(activity.getWindow().getDecorView(),"나에게 맞게 설정")!=null
                && (activity.getWindow().getInsetsController().getSystemBarsAppearance() & WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)!=0,
                "Settings restores its light palette and system-bar icons");
            capture("settings.png");
            runOnMainSync(() -> clickText("자동출석")); settle(); capture("connection.png");
            runOnMainSync(() -> clickText("오늘")); settle();
            check(theme()==HomeTheme.NIGHT && findText(activity.getWindow().getDecorView(),"오늘 출결")!=null,"Returning to Today restores the night palette");
            restore(); result.putString("stream","PASS: "+checks+" Today theme checks. Screenshots use synthetic emulator records; no attendance action performed.\n");
            instrumentation.finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            try { restore(); } catch(Throwable ignored) {}
            result.putString("stream","FAIL: "+failure+"\n"); instrumentation.finish(Activity.RESULT_CANCELED,result);
        }
    }
    private void artworkStates(String palette) throws Exception {
        Prefs prefs=new Prefs(context);
        // Change only saved UI state while visible. Auto-click is off, SSID is unmatched and alarms are cancelled.
        runOnMainSync(()->prefs.enabled(true));settle();
        check(value("reservationState").equals("출석 예약 켜짐")
            && "출석 예약 스탠드 켜짐 · 눌러 끄기".contentEquals(activity.getWindow().getDecorView().findViewWithTag("reservation-stand").getContentDescription())
            && ((StandSceneView)field("reservationScene")).isLit()
            && activity.getWindow().getDecorView().findViewWithTag("reservation-lamp")==null
            && !MonitorService.running,"Pictured stand is ON and separate icon is absent: "+palette);
        int onResource=((StandSceneView)field("reservationScene")).imageResource();
        capture("reservation-"+palette+"-on.png");
        runOnMainSync(()->prefs.enabled(false));settle();
        check(value("reservationState").equals("출석 예약 꺼짐")
            && "출석 예약 스탠드 꺼짐 · 눌러 켜기".contentEquals(activity.getWindow().getDecorView().findViewWithTag("reservation-stand").getContentDescription())
            && !((StandSceneView)field("reservationScene")).isLit(),
            "Pictured stand switches to the OFF lighting image: "+palette);
        check(onResource!=((StandSceneView)field("reservationScene")).imageResource(),"ON and OFF load distinct stand-lighting assets: "+palette);
        capture("reservation-"+palette+"-off.png");
    }
    private void runOnMainSync(Runnable action) { instrumentation.runOnMainSync(action); }
    private void at(LocalDate date,int h,int m,int s) { clock.instant=date.atTime(h,m,s).atZone(Policy.ZONE).toInstant(); settle(); }
    private void settle() { SystemClock.sleep(2400); instrumentation.waitForIdleSync(); }
    private Object field(String name) { try { Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); } catch(Exception e) { throw new AssertionError(e); } }
    private void setField(String name,Object value) { try { Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(activity,value); } catch(Exception e) { throw new AssertionError(e); } }
    private HomeTheme theme() { return (HomeTheme)field("homeTheme"); }
    private String calendarValue(String tag) { return ((TextView)calendar.getWindow().getDecorView().findViewWithTag(tag)).getText().toString(); }
    private String value(String name) { return ((TextView)field(name)).getText().toString(); }
    private void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); checks++; Bundle progress=new Bundle();progress.putString("stream","PASS: "+message+"\n");instrumentation.sendStatus(0,progress); }
    private TextView findText(View view,String label) {
        if(view instanceof TextView && ((TextView)view).getText().toString().equals(label))return (TextView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=findText(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}
        return null;
    }
    private void clickText(String label) { TextView view=findText(activity.getWindow().getDecorView(),label); if(view==null)throw new AssertionError("Missing "+label); view.performClick(); }
    private void capture(String name) throws Exception {
        Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot(); if(bitmap==null)throw new AssertionError("Screenshot unavailable");
        File dir=new File(context.getExternalFilesDir(null),"theme-screens"); if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("No screenshot directory");
        try(FileOutputStream out=new FileOutputStream(new File(dir,name))){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Screenshot failed");} finally {bitmap.recycle();}
    }
    @SuppressWarnings("unchecked") private void restore() {
        if(context==null || saved==null)return;
        runOnMainSync(() -> {
            context.stopService(new Intent(context,MonitorService.class)); AlarmScheduler.cancel(context);
            SharedPreferences.Editor edit=new Prefs(context).raw.edit().clear();
            for(Map.Entry<String,?> item:saved.entrySet()){
                Object v=item.getValue(); String k=item.getKey();
                if(v instanceof String)edit.putString(k,(String)v);
                else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);
                else if(v instanceof Integer)edit.putInt(k,(Integer)v);
                else if(v instanceof Long)edit.putLong(k,(Long)v);
                else if(v instanceof Float)edit.putFloat(k,(Float)v);
                else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));
            }
            if(!edit.commit())throw new AssertionError("Could not restore emulator preferences");
            if(calendar!=null)calendar.finish();if(activity!=null)activity.finish(); AlarmScheduler.arm(context,false);
        });
    }
    private static final class MutableClock extends Clock {
        volatile Instant instant;
        MutableClock(Instant initial) { instant=initial; }
        @Override public ZoneId getZone(){return Policy.ZONE;}
        @Override public Clock withZone(ZoneId zone){return Clock.fixed(instant,zone);}
        @Override public Instant instant(){return instant;}
    }
}