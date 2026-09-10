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
                    .putString("log:"+day,"08:42  입실 완료\n08:30  입실 감시 시작")
                    .remove("record:"+day.plusDays(1)).remove("log:"+day.plusDays(1)).commit();
            });
            activity = (MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            clock = new MutableClock(day.atTime(8,45).atZone(Policy.ZONE).toInstant());
            runOnMainSync(() -> setField("homeClock",clock)); settle();
            check(theme()==HomeTheme.WARM && value("inTime").equals("08:42:00") && value("outTime").equals("—"),
                "Morning theme preserves confirmed entry and does not invent an exit");
            capture("today-warm.png");
            at(day,16,49,59); check(theme()==HomeTheme.WARM,"Warm immediately before the boundary");
            at(day,16,50,0);
            check(theme()==HomeTheme.NIGHT && value("inTime").equals("08:42:00") && value("todayLog").contains("입실 완료"),
                "Visible screen switches automatically and preserves records and logs");
            capture("today-night.png");
            runOnMainSync(() -> clickText("달력에서 출결 조회  ›")); settle();
            AccessibilityNodeInfo active = instrumentation.getUiAutomation().getRootInActiveWindow();
            check(active!=null && !active.findAccessibilityNodeInfosByText("2026").isEmpty(),"Calendar opens from the night screen");
            capture("calendar-night.png"); instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); settle();
            runOnMainSync(() -> prefs.raw.edit().putString("checkout","19:20").commit()); settle();
            check(theme()==HomeTheme.WARM,"A changed checkout time updates the visible theme");
            at(day,18,20,0); check(theme()==HomeTheme.NIGHT,"Custom checkout boundary switches to night");
            at(day.plusDays(1),0,0,0);
            check(theme()==HomeTheme.WARM && value("inTime").equals("—") && value("todayLog").equals("아직 오늘 기록이 없습니다."),
                "Midnight resets the theme and loads the new day's records");
            runOnMainSync(() -> activity.moveTaskToBack(true));
            clock.instant=day.atTime(20,0).atZone(Policy.ZONE).toInstant();
            context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); settle();
            check(theme()==HomeTheme.NIGHT,"Returning from the background refreshes the theme");
            runOnMainSync(() -> { prefs.raw.edit().putString("checkout","17:50").commit(); clickText("설정"); }); settle();
            check(findText(activity.getWindow().getDecorView(),"나에게 맞게 설정")!=null
                && (activity.getWindow().getInsetsController().getSystemBarsAppearance() & WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)!=0,
                "Settings restores its light palette and system-bar icons");
            capture("settings.png");
            runOnMainSync(() -> clickText("연결")); settle(); capture("connection.png");
            runOnMainSync(() -> clickText("오늘")); settle();
            check(theme()==HomeTheme.NIGHT && findText(activity.getWindow().getDecorView(),"오늘 출결")!=null,"Returning to Today restores the night palette");
            restore(); result.putString("stream","PASS: "+checks+" Today theme checks. Screenshots use synthetic emulator records; no attendance action performed.\n");
            instrumentation.finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            try { restore(); } catch(Throwable ignored) {}
            result.putString("stream","FAIL: "+failure+"\n"); instrumentation.finish(Activity.RESULT_CANCELED,result);
        }
    }
    private void runOnMainSync(Runnable action) { instrumentation.runOnMainSync(action); }
    private void at(LocalDate date,int h,int m,int s) { clock.instant=date.atTime(h,m,s).atZone(Policy.ZONE).toInstant(); settle(); }
    private void settle() { SystemClock.sleep(2400); instrumentation.waitForIdleSync(); }
    private Object field(String name) { try { Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); } catch(Exception e) { throw new AssertionError(e); } }
    private void setField(String name,Object value) { try { Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(activity,value); } catch(Exception e) { throw new AssertionError(e); } }
    private HomeTheme theme() { return (HomeTheme)field("homeTheme"); }
    private String value(String name) { return ((TextView)field(name)).getText().toString(); }
    private void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); checks++; }
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
            if(activity!=null)activity.finish(); AlarmScheduler.arm(context,false);
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