package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.os.*;
import java.time.*;
import java.util.*;

/** Emulator-only lifecycle checks. Uses a deliberately unmatched SSID and never clicks Chrome. */
public final class DeviceChecks extends Instrumentation {
    private Bundle replicaArgs;private boolean design;
    private boolean tabLayout,readiness,networkArrival,management,timetable,orientation,shortDay,classification,revision,lockdown,setup;
    private Context context;
    private Map<String,?> saved;
    private MainActivity activity;
    private int checks;
    private boolean stand, manual, clickTest, wake, themes, locks, upload, settings, departure, calendar, traffic;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments);design=arguments!=null&&"design".equals(arguments.getString("suite"));tabLayout=arguments!=null&&"tab-layout".equals(arguments.getString("suite"));readiness=arguments!=null&&"readiness".equals(arguments.getString("suite"));setup=arguments!=null&&"setup".equals(arguments.getString("suite"));lockdown=arguments!=null&&"lockdown".equals(arguments.getString("suite"));revision=arguments!=null&&"revision".equals(arguments.getString("suite"));classification=arguments!=null&&"classification".equals(arguments.getString("suite"));shortDay=arguments!=null&&"short-day".equals(arguments.getString("suite"));orientation=arguments!=null&&"orientation".equals(arguments.getString("suite"));timetable=arguments!=null&&"timetable".equals(arguments.getString("suite"));management=arguments!=null&&"management".equals(arguments.getString("suite"));networkArrival=arguments!=null&&"network-arrival".equals(arguments.getString("suite"));if(arguments!=null&&"replica".equals(arguments.getString("suite")))replicaArgs=arguments;stand=arguments!=null&&"stand".equals(arguments.getString("suite"));manual=arguments!=null&&"manual".equals(arguments.getString("suite"));clickTest=arguments!=null&&"click".equals(arguments.getString("suite")); wake=arguments!=null&&"wake".equals(arguments.getString("suite")); traffic=arguments!=null&&"traffic".equals(arguments.getString("suite")); calendar=arguments!=null&&"calendar".equals(arguments.getString("suite")); departure=arguments!=null&&"departure".equals(arguments.getString("suite")); settings=arguments!=null&&"settings".equals(arguments.getString("suite")); upload = arguments != null && "upload".equals(arguments.getString("suite")); themes = arguments != null && "themes".equals(arguments.getString("suite")); locks = arguments != null && "locks".equals(arguments.getString("suite")); start(); }
    @Override public void onStart() {
        if(design){new DesignChecks(this).run();return;}
        if(tabLayout){new TabLayoutChecks(this).run();return;}
        if(readiness){new ReadinessChecks(this).run();return;}
        if(setup){new SetupGuideChecks(this).run();return;}
        if(lockdown){new LockdownChecks(this).run();return;}
        if(revision){new RevisionChecks(this).run();return;}
        if(classification){new ClassificationChecks(this).run();return;}
        if(shortDay){new ShortDayChecks(this).run();return;}
        if(orientation){new OrientationChecks(this).run();return;}
        if(timetable){new TimetableChecks(this).run();return;}
        if(management){new ManagementChecks(this).run();return;}
        if(networkArrival){new NetworkArrivalChecks(this).run();return;}
        if(replicaArgs!=null){new ReplicaChecks(this,replicaArgs).run();return;}
        if(stand){new StandTouchChecks(this).run();return;}
        if(manual){new ManualAttendanceChecks(this).run();return;}
        if(clickTest){new LocalClickChecks(this).run();return;}
        if(wake){new WakeChecks(this).run();return;}
        if(traffic){new TrafficChecks(this).run();return;}
        if(calendar){new CalendarChecks(this).run();return;}
        if(departure){new DepartureChecks(this).run();return;}
        if(settings){new SettingsChecks(this).run();return;}
        if (themes) { new ThemeChecks(this).run(); return; }
        if (upload) { new LogUploadChecks(this).run(); return; }
        if (locks) { new LockLabChecks(this).run(); return; }
        Bundle result = new Bundle();
        try {
            if (!Build.HARDWARE.equals("ranchu") && !Build.HARDWARE.equals("goldfish")) throw new AssertionError("Emulator only");
            context = getTargetContext();
            Prefs prefs = new Prefs(context);
            saved = new HashMap<>(prefs.raw.getAll());
            activity = (MainActivity) startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ZonedDateTime now = ZonedDateTime.now(Policy.ZONE);
            if (now.getHour() < 1 || now.getHour() >= 23) throw new AssertionError("Run these clock-relative checks between 01:00 and 23:00 Korea time");
            runOnMainSync(() -> {
                prefs.raw.edit().putBoolean("enabled", true).putBoolean("auto", false)
                    .putStringSet("ssids",Set.of("__EMULATOR_NO_MATCH__")).putStringSet("days",Set.of("1","2","3","4","5","6","7"))
                    .putStringSet("excluded",Set.of()).putString("start","00:00").putString("end","23:50")
                    .putString("checkout","23:51").putInt("checkout_retry_minutes",1)
                    .remove("record_day").remove("in").remove("out").apply();
                activity.moveTaskToBack(true);
                if (!AlarmScheduler.arm(context,true)) throw new AssertionError("Exact alarm unavailable");
            });
            awaitRunning(true);
            check(true,"Exact alarm starts entry monitoring while activity is in background");
            runOnMainSync(() -> { prefs.record(now.toLocalDate().toString(),new Policy.Record("08:45:00","")); ScheduleController.reconcile(context); });
            awaitRunning(false);
            check(prefs.enabled(),"Confirmed entry stops monitoring and keeps future reservations");
            check(prefs.raw.getLong("next_alarm",0)>System.currentTimeMillis(),"Next checkout reservation remains");
            String out = now.minusMinutes(1).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            runOnMainSync(() -> {
                prefs.raw.edit().putString("end","00:01").putString("checkout",out).putInt("checkout_retry_minutes",5).apply();
                if (!AlarmScheduler.arm(context,true)) throw new AssertionError("Checkout alarm unavailable");
            });
            awaitRunning(true);
            long until = SystemClock.elapsedRealtime()+5000;
            boolean wifiNotice=false;
            while(SystemClock.elapsedRealtime()<until) {
                for(android.service.notification.StatusBarNotification n:context.getSystemService(NotificationManager.class).getActiveNotifications())
                    if(n.getId()==14) wifiNotice=true;
                if(wifiNotice)break;
                SystemClock.sleep(100);
            }
            check(wifiNotice,"Failed checkout produces the Wi-Fi connection notification");
            runOnMainSync(() -> { prefs.record(now.toLocalDate().toString(),new Policy.Record("08:45:00",out)); ScheduleController.reconcile(context); });
            awaitRunning(false);
            boolean lingering=false;
            for(android.service.notification.StatusBarNotification n:context.getSystemService(NotificationManager.class).getActiveNotifications())
                if(n.getId()==14) lingering=true;
            check(!lingering,"Confirmed checkout stops monitoring and clears the Wi-Fi request");
            runOnMainSync(() -> {
                prefs.raw.edit().remove("record_day").remove("in").remove("out").putString("end","23:50")
                    .putString("checkout","23:51").putInt("checkout_retry_minutes",1)
                    .putStringSet("excluded",Set.of(now.toLocalDate().toString())).apply();
                ScheduleController.reconcile(context);
            });
            awaitRunning(false);
            check(prefs.raw.getLong("next_alarm",0)>now.toLocalDate().plusDays(1).atStartOfDay(Policy.ZONE).toInstant().toEpochMilli()-1,
                "Holiday skips today's work");
            notificationChecks();
            result.putString("stream","PASS: "+checks+" Android lifecycle and notification checks. No attendance request or Chrome click performed.\n");
            restore();
            finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            try { restore(); } catch(Throwable ignored) {}
            result.putString("stream","FAIL: "+failure.toString()+"\n");
            finish(Activity.RESULT_CANCELED,result);
        }
    }
    private void notificationChecks() throws Exception {
        String day = "2000-01-03", nextDay = "2000-01-04";
        Prefs prefs = new Prefs(context);
        runOnMainSync(() -> {
            prefs.raw.edit().putBoolean("result_notifications",false).apply();
            Notices.attendanceCompleted(context,day,Policy.Phase.ENTRY,new Policy.Record("08:45:00",""));
        });
        Notification entry = awaitNotice(day,Notices.ENTRY_RESULT);
        check(entry.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("08:45:00"),
            "Confirmed entry posts its time even when the legacy result toggle was off");
        runOnMainSync(() -> Notices.attendanceCompleted(context,day,Policy.Phase.CHECKOUT,new Policy.Record("08:45:00","17:50:00")));
        Notification exit = awaitNotice(day,Notices.CHECKOUT_RESULT);
        check(notification(day,Notices.ENTRY_RESULT)!=null && exit.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("퇴실 완료"),
            "Entry and exit remain as separate result notifications");
        runOnMainSync(() -> ScheduleController.reconcile(context));
        check(notification(day,Notices.ENTRY_RESULT)!=null && notification(day,Notices.CHECKOUT_RESULT)!=null,
            "Stopping scheduled monitoring does not clear completed attendance notifications");
        entry.contentIntent.send();
        waitForIdleSync();
        check((entry.flags & Notification.FLAG_AUTO_CANCEL)==0 && (exit.flags & Notification.FLAG_AUTO_CANCEL)==0
            && notification(day,Notices.ENTRY_RESULT)!=null && notification(day,Notices.CHECKOUT_RESULT)!=null,
            "Opening the app preserves both results with automatic removal disabled");
        runOnMainSync(() -> Notices.attendanceCompleted(context,nextDay,Policy.Phase.ENTRY,new Policy.Record("08:46:00","")));
        awaitNotice(nextDay,Notices.ENTRY_RESULT);
        check(notification(day,Notices.ENTRY_RESULT)!=null && notification(day,Notices.CHECKOUT_RESULT)!=null,
            "Another attendance date does not replace earlier result notifications");
        runOnMainSync(() -> {
            Notices.attendanceCompleted(context,"2000-01-05",Policy.Phase.ENTRY,new Policy.Record("",""));
            Notices.attendanceCompleted(context,"2000-01-05",Policy.Phase.CHECKOUT,new Policy.Record("08:45:00",""));
        });
        check(notification("2000-01-05",Notices.ENTRY_RESULT)==null && notification("2000-01-05",Notices.CHECKOUT_RESULT)==null,
            "Unconfirmed entry and exit never produce a completion notification");
    }
    private Notification notification(String day,int id) {
        for(android.service.notification.StatusBarNotification item:context.getSystemService(NotificationManager.class).getActiveNotifications())
            if(item.getId()==id && ("attendance:"+day).equals(item.getTag())) return item.getNotification();
        return null;
    }
    private Notification awaitNotice(String day,int id) {
        long until=SystemClock.elapsedRealtime()+5000;
        Notification notice;
        while((notice=notification(day,id))==null && SystemClock.elapsedRealtime()<until) SystemClock.sleep(100);
        if(notice==null) throw new AssertionError("Missing result notification: "+day+":"+id);
        return notice;
    }
    private void check(boolean value,String label) {
        if(!value)throw new AssertionError(label);
        checks++;
        Bundle progress=new Bundle(); progress.putString("stream","PASS: "+label+"\n"); sendStatus(0,progress);
    }
    private void awaitRunning(boolean expected) {
        long until=SystemClock.elapsedRealtime()+10000;
        while(MonitorService.running!=expected && SystemClock.elapsedRealtime()<until)SystemClock.sleep(100);
        if(MonitorService.running!=expected)throw new AssertionError("Monitor running state expected "+expected);
    }
    @android.annotation.SuppressLint("ApplySharedPref") // Finish instrumentation only after original test-device settings are durable.
    @SuppressWarnings("unchecked")
    private void restore() {
        if(context==null || saved==null)return;
        runOnMainSync(() -> {
            context.stopService(new Intent(context,MonitorService.class)); ChromeAttendanceService.cancelWork(); AlarmScheduler.cancel(context);
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
            if (!edit.commit()) throw new AssertionError("Could not restore emulator preferences"); Notices.checkoutAttention(context,false,""); Notices.chromeAction(context,false,"");
            for(String testDay:List.of("2000-01-03","2000-01-04","2000-01-05")) {
                context.getSystemService(NotificationManager.class).cancel("attendance:"+testDay,Notices.ENTRY_RESULT);
                context.getSystemService(NotificationManager.class).cancel("attendance:"+testDay,Notices.CHECKOUT_RESULT);
            }
            AlarmScheduler.arm(context,false); if(activity!=null)activity.finish();
        });
    }
}
