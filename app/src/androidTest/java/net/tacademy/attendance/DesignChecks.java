package net.tacademy.attendance;
import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.time.*;
import java.lang.reflect.*;
import java.io.*;

/** Emulator-only visual captures and preference transitions. No browser navigation. */
final class DesignChecks {
    private final Instrumentation t;private MainActivity a;private Prefs p;private Map<String,?> saved;private int checks;
    DesignChecks(Instrumentation t){this.t=t;}
    void run(){Bundle result=new Bundle();try{
        if(!Set.of("ranchu","goldfish").contains(Build.HARDWARE))throw new AssertionError("Emulator only");
        var c=t.getTargetContext();p=new Prefs(c);saved=new HashMap<>(p.raw.getAll());
        ui(()->{p.enabled(false);p.raw.edit().putBoolean("auto",false).putStringSet("ssids",Set.of("__NO_MATCH__")).putStringSet("days",Set.of()).putBoolean("setup_finished",true).remove("chrome_pending").commit();AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));});
        a=(MainActivity)t.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ui(()->p.raw.edit().putBoolean("use_extend_unlock",false).putString("scheduled_lock_mode","AUTH").commit());
        check(ScheduledUnlock.mode(c)==UnlockPolicy.Mode.WAKE,"Old AUTH choice cannot request credentials in normal automation");
        ui(()->p.raw.edit().putBoolean("use_extend_unlock",true).putString("scheduled_lock_mode","WAKE").commit());
        check(ScheduledUnlock.mode(c)==UnlockPolicy.Mode.TRUST,"Extend preference selects trusted handling without a second selector");
        ui(()->p.raw.edit().putBoolean("show_processing_screen",false).putBoolean("screen_off_after_attendance",false).putBoolean("lockdown_after_attendance",false).commit());
        ui(p::enableAutomaticClick);
        check(p.auto()&&p.showProcessingScreen()&&p.screenOffAfterAttendance()&&p.lockdownAfterAttendance(),"Enabling automatic clicks enables all presentation options");
        ui(()->p.raw.edit().putBoolean("auto",false).commit());
        clock("2026-10-13T08:45:00+09:00");tab(0);capture("01-today-day.png");
        clock("2026-10-13T17:30:00+09:00");tab(0);capture("02-today-night.png");
        tab(1);capture("03-settings.png");
        tab(2);capture("04-automatic.png");
        ui(()->{ScrollView scroll=(ScrollView)field("homeScroll");View card=a.getWindow().getDecorView().findViewWithTag("setup-readiness");scroll.scrollTo(0,card.getBottom()-scroll.getHeight()+24);});capture("08-integrated-click-settings.png");
        ui(()->{ScrollView scroll=(ScrollView)field("homeScroll");scroll.fullScroll(View.FOCUS_DOWN);});capture("05-processing-settings.png");
        ui(()->a.setContentView(new ProcessingPage(a,Policy.Phase.ENTRY,"출결 처리 중","완료되면 화면을 끄고 알려드릴게요","Chrome 화면 보기",()->{})));capture("06-entry-processing.png");
        ui(()->a.setContentView(new ProcessingPage(a,Policy.Phase.CHECKOUT,"출결 처리 중","완료되면 화면을 끄고 알려드릴게요","Chrome 화면 보기",()->{})));capture("07-checkout-processing.png");
        check(!p.enabled()&&!p.auto()&&!MonitorService.running,"Visual review never enables reservations or monitoring");
        restore();result.putString("stream","PASS: "+checks+" preference checks and 8 visual captures. No attendance requests.\n");t.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e);t.finish(Activity.RESULT_CANCELED,result);}}
    private Object field(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}catch(Exception e){throw new RuntimeException(e);}}
    private void clock(String instant){ui(()->{try{Field f=MainActivity.class.getDeclaredField("homeClock");f.setAccessible(true);f.set(a,Clock.fixed(ZonedDateTime.parse(instant).toInstant(),Policy.ZONE));}catch(Exception e){throw new RuntimeException(e);}});}
    private void tab(int index){ui(()->{try{Method m=MainActivity.class.getDeclaredMethod("render",int.class);m.setAccessible(true);m.invoke(a,index);}catch(Exception e){throw new RuntimeException(e);}});}
    private void capture(String name)throws Exception{SystemClock.sleep(650);t.waitForIdleSync();File dir=new File(t.getTargetContext().getExternalFilesDir(null),"design-v21");dir.mkdirs();Bitmap shot=t.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot();try(var out=new FileOutputStream(new File(dir,name))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}
    private void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
    private void ui(Runnable r){t.runOnMainSync(r);}
    @SuppressWarnings("unchecked") private void restore(){if(a!=null)ui(a::finish);if(saved==null)return;ui(()->{var e=p.raw.edit().clear();for(var item:saved.entrySet()){var v=item.getValue();String k=item.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<>((Set<String>)v));}e.commit();});}
}
