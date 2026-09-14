package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.location.*;
import android.os.*;
import java.time.*;
import java.util.*;

/** Emulator-only mock GPS checks. The caller grants this emulator app's mock-location app-op. */
@SuppressWarnings("deprecation")
final class DepartureChecks {
    private final Instrumentation test;
    private Context c; private Activity activity; private Map<String,?> saved; private LocationManager locations;
    private String day; private int count;
    private final List<String> providers=new ArrayList<>();
    DepartureChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Build.HARDWARE.equals("ranchu")&&!Build.HARDWARE.equals("goldfish"))throw new AssertionError("Emulator only");
        c=test.getTargetContext();Prefs p=new Prefs(c);saved=new HashMap<>(p.raw.getAll());
        ZonedDateTime now=ZonedDateTime.now(Policy.ZONE);day=now.toLocalDate().toString();
        if(now.getHour()<1||now.getHour()>=23)throw new AssertionError("Run between 01:00 and 23:00 Korea time");
        main(()->{p.enabled(false);ScheduleController.reconcile(c);});awaitRunning(false);
        activity=test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        locations=c.getSystemService(LocationManager.class);
        for(String provider:List.of(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER)){
            locations.addTestProvider(provider,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE);
            providers.add(provider);locations.setTestProviderEnabled(provider,true);
        }
        String checkout=now.minusMinutes(1).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        main(()->{
            p.location(37.0,127.0,30);
            p.raw.edit().putBoolean("enabled",true).putBoolean("auto",false).putString("start","00:00").putString("end","00:01")
                .putString("checkout",checkout).putInt("checkout_retry_minutes",5).putInt("departure_watch_minutes",1440)
                .putStringSet("days",Set.of("1","2","3","4","5","6","7")).putStringSet("excluded",Set.of())
                .putStringSet("ssids",Set.of("__DEPARTURE_NO_MATCH__")).remove("departure_notified_day").remove("departure_seen_day").commit();
            p.record(day,new Policy.Record("00:00:30",""));
            activity.moveTaskToBack(true);ScheduleController.reconcile(c);
        });awaitRunning(true);
        check(p.phase(ZonedDateTime.now(Policy.ZONE))==Policy.Phase.CHECKOUT&&p.departureWatching(ZonedDateTime.now(Policy.ZONE)),"Checkout window permits attendance and GPS monitoring together");
        long expectedEnd=now.toLocalDate().atTime(LocalTime.parse(checkout)).atZone(Policy.ZONE).plusMinutes(5).toInstant().toEpochMilli();
        check(p.raw.getLong("next_alarm",0)==expectedEnd,"Exact alarm stops monitoring at the checkout retry deadline, ignoring legacy long durations");
        fix(37.0007,SystemClock.elapsedRealtime()-25000);SystemClock.sleep(1200);
        main(()->ScheduleController.reconcile(c));SystemClock.sleep(400);
        check(notice()==null,"One outside fix and repeated evaluation cannot send an alert");
        fix(37.0008,SystemClock.elapsedRealtime());
        awaitNotice();check(notice().extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("이탈 감지"),"Distinct outside GPS fixes produce the checkout reminder in the background");
        check(day.equals(p.raw.getString("departure_notified_day",""))&&!p.departureWatching(ZonedDateTime.now(Policy.ZONE)),"Successful notification disables further GPS alerts for this day");
        long posted=notice().when;
        main(()->ScheduleController.reconcile(c));SystemClock.sleep(400);
        check(MonitorService.running&&notice().when==posted,"Attendance retries continue inside the window without repeating the departure alert");
        main(()->{p.record(day,new Policy.Record("00:00:30",checkout));ScheduleController.reconcile(c);});awaitRunning(false);
        check(notice()==null,"Confirmed checkout stops monitoring and clears the departure reminder");
        // End the window at the next minute boundary and let the real alarm stop the service.
        ZonedDateTime boundary=ZonedDateTime.now(Policy.ZONE).withSecond(0).withNano(0).plusMinutes(1);
        String endingCheckout=boundary.minusMinutes(5).toLocalTime().toString();
        main(()->{p.raw.edit().putString("checkout",endingCheckout).remove("departure_notified_day").commit();p.record(day,new Policy.Record("00:00:30",""));ScheduleController.reconcile(c);});awaitRunning(true);
        check(p.raw.getLong("next_alarm",0)==boundary.toInstant().toEpochMilli(),"Active monitor schedules the exact end boundary");
        long stopBy=boundary.toInstant().toEpochMilli()+10000;
        while(MonitorService.running&&System.currentTimeMillis()<stopBy)SystemClock.sleep(200);
        check(!MonitorService.running&&!p.monitoringNeeded(ZonedDateTime.now(Policy.ZONE)),"The scheduled deadline stops GPS and attendance without user interaction");
        main(()->{c.startForegroundService(new Intent(c,MonitorService.class));});SystemClock.sleep(12000);
        check(!MonitorService.running&&!p.departureWatching(ZonedDateTime.now(Policy.ZONE)),"Late service starts cannot extend monitoring beyond attendance hours");
        String futureCheckout=ZonedDateTime.now(Policy.ZONE).plusMinutes(5).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        main(()->{p.raw.edit().putString("checkout",futureCheckout).commit();ScheduleController.reconcile(c);});
        check(!p.monitoringNeeded(ZonedDateTime.now(Policy.ZONE))&&!MonitorService.running,"Between entry and checkout only the future reservation remains");
        String activeCheckout=ZonedDateTime.now(Policy.ZONE).minusMinutes(1).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        main(()->{p.raw.edit().putString("checkout",activeCheckout).putStringSet("excluded",Set.of(day)).commit();ScheduleController.reconcile(c);});
        check(!p.monitoringNeeded(ZonedDateTime.now(Policy.ZONE)),"Holidays do not activate attendance or GPS monitoring");
        main(()->{p.raw.edit().putStringSet("excluded",Set.of()).commit();ScheduleController.reconcile(c);});awaitRunning(true);
        main(()->reservation(true));
        check(p.enabled()&&MonitorService.running,"Selecting On again preserves an enabled reservation");
        main(()->reservation(false));awaitRunning(false);
        check(!p.enabled()&&!p.raw.contains("next_alarm"),"Off stops the active monitor and cancels future reservations");
        main(()->reservation(false));
        check(!p.enabled()&&!p.raw.contains("next_alarm"),"Selecting Off again does not turn reservations back on");
        restore();result.putString("stream","PASS: "+count+" departure GPS/notification checks. Synthetic locations only; no attendance request.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private void reservation(boolean enabled){try{java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("setAttendanceReservation",boolean.class);method.setAccessible(true);method.invoke(activity,enabled);}catch(Exception e){throw new AssertionError(e);}}
    private void fix(double latitude,long elapsed){for(String provider:providers){Location location=new Location(provider);location.setLatitude(latitude);location.setLongitude(127.0);location.setAccuracy(5);location.setTime(System.currentTimeMillis());location.setElapsedRealtimeNanos(elapsed*1_000_000);locations.setTestProviderLocation(provider,location);}}
    private Notification notice(){for(android.service.notification.StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==Notices.DEPARTURE&&("departure:"+day).equals(n.getTag()))return n.getNotification();return null;}
    private void awaitNotice(){long until=SystemClock.elapsedRealtime()+35000;while(notice()==null&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(200);if(notice()==null)throw new AssertionError("Missing departure notification");}
    private void awaitRunning(boolean expected){long until=SystemClock.elapsedRealtime()+8000;while(MonitorService.running!=expected&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);if(MonitorService.running!=expected)throw new AssertionError("Monitor running expected "+expected);}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);count++;Bundle b=new Bundle();b.putString("stream","PASS: "+label+"\n");test.sendStatus(0,b);}
    private void main(Runnable r){Throwable[] error={null};test.runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    @SuppressWarnings("unchecked") private void restore(){if(c==null||saved==null)return;main(()->{new Prefs(c).enabled(false);c.stopService(new Intent(c,MonitorService.class));AlarmScheduler.cancel(c);Notices.clearDeparture(c,day);});awaitRunning(false);
        if(locations!=null)for(String provider:providers)locations.removeTestProvider(provider);
        main(()->{SharedPreferences.Editor edit=new Prefs(c).raw.edit().clear();for(Map.Entry<String,?> e:saved.entrySet()){Object v=e.getValue();String k=e.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));}edit.commit();if(activity!=null)activity.finish();AlarmScheduler.arm(c,false);});
    }
}
