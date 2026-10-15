package net.tacademy.attendance;
import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.Bitmap;
import java.time.*;
import java.util.*;
import java.lang.reflect.*;
import java.io.*;
/** Emulator only. Example records, no network or attendance actions. */
final class StoreCaptureChecks {
    final Instrumentation t;MainActivity main;Activity calendar;Prefs prefs;Map<String,?> saved;
    StoreCaptureChecks(Instrumentation t){this.t=t;}
    void run(){Bundle result=new Bundle();try{
        if(!Set.of("ranchu","goldfish").contains(Build.HARDWARE))throw new AssertionError("Emulator only");
        Context c=t.getTargetContext();prefs=new Prefs(c);saved=new HashMap<>(prefs.raw.getAll());
        ui(()->{
            prefs.raw.edit().clear().commit();prefs=new Prefs(c);
            prefs.raw.edit().putBoolean("first_use_guide_pending",false).putBoolean("setup_finished",true)
                .putBoolean("enabled",false).putBoolean("auto",false).putStringSet("ssids",Set.of("TACADEMY_5G")).commit();
            AlarmScheduler.cancel(c);c.stopService(new Intent(c,MonitorService.class));
            prefs.record("2026-10-13",new Policy.Record("08:48","17:50"));
            prefs.record("2026-10-14",new Policy.Record("08:52","17:50"));
        });
        main=(MainActivity)t.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        clock("2026-10-15T08:45:00+09:00");tab(0);capture("01-today.jpg");
        clock("2026-10-15T17:30:00+09:00");tab(0);capture("02-evening.jpg");
        calendar=t.startActivitySync(new Intent(c,AttendanceCalendarActivity.class).putExtra("initial_date","2026-10-15").putExtra("theme","WARM").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        capture("03-calendar.jpg");ui(calendar::finish);calendar=null;t.waitForIdleSync();
        tab(2);capture("04-setup.jpg");
        tab(3);capture("05-first-use.jpg");
        result.putString("stream","PASS: 5 actual app screenshots; example records; no attendance requests.\n");
        restore();t.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e);t.finish(Activity.RESULT_CANCELED,result);}}
    void ui(Runnable r){t.runOnMainSync(r);}
    void clock(String value){ui(()->{try{Field f=MainActivity.class.getDeclaredField("homeClock");f.setAccessible(true);f.set(main,Clock.fixed(ZonedDateTime.parse(value).toInstant(),Policy.ZONE));}catch(Exception e){throw new RuntimeException(e);}});}
    void tab(int index){ui(()->{try{Method m=MainActivity.class.getDeclaredMethod("render",int.class);m.setAccessible(true);m.invoke(main,index);}catch(Exception e){throw new RuntimeException(e);}});}
    void capture(String name)throws Exception{
        SystemClock.sleep(900);t.waitForIdleSync();
        File dir=new File(t.getTargetContext().getExternalFilesDir(null),"play-store");dir.mkdirs();
        Bitmap b=t.getUiAutomation().takeScreenshot();
        try(var out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.JPEG,96,out);}b.recycle();
    }
    @SuppressWarnings("unchecked") void restore(){
        if(calendar!=null)ui(calendar::finish);if(main!=null)ui(main::finish);if(saved==null)return;
        ui(()->{var e=prefs.raw.edit().clear();for(var item:saved.entrySet()){
            Object v=item.getValue();String k=item.getKey();
            if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);
            else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);
            else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<>((Set<String>)v));
        }e.commit();});
    }
}
