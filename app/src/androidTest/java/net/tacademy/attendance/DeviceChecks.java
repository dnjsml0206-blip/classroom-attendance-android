package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.os.*;
import java.time.*;
import java.util.*;

/** Emulator-only lifecycle checks. Uses a deliberately unmatched SSID and never clicks Chrome. */
public final class DeviceChecks extends Instrumentation {
    private Context context;
    private Map<String,?> saved;
    private MainActivity activity;
    private int checks;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
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
            result.putString("stream","PASS: "+checks+" Android lifecycle checks. No attendance request or Chrome click performed.\n");
            restore();
            finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            try { restore(); } catch(Throwable ignored) {}
            result.putString("stream","FAIL: "+failure.toString()+"\n");
            finish(Activity.RESULT_CANCELED,result);
        }
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
            AlarmScheduler.arm(context,false); if(activity!=null)activity.finish();
        });
    }
}
