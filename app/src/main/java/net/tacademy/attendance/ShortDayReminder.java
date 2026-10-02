package net.tacademy.attendance;
import android.app.*;
import android.content.*;
import java.time.*;
/** Independent local reminder. Never starts monitoring, Chrome, or an attendance request. */
final class ShortDayReminder {
    static final int ID=19;
    private ShortDayReminder(){}
    static SharedPreferences store(Context c){return c.getSharedPreferences("short_day_reminders",Context.MODE_PRIVATE);}
    private static PendingIntent operation(Context c){return PendingIntent.getBroadcast(c,1901,new Intent(c,ShortDayReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static void cancel(Context c){c.getSystemService(AlarmManager.class).cancel(operation(c));store(c).edit().remove("next_at").apply();}
    static synchronized void reconcile(Context c){
        Prefs p=new Prefs(c);ZonedDateTime now=ZonedDateTime.now(Policy.ZONE);cancel(c);
        if(!p.guidanceNotifications()||!CourseSchedule.activeShortDay(now.toLocalDate(),p.days(),p.automationExcluded()))c.getSystemService(NotificationManager.class).cancel(ID);
        if(!p.guidanceNotifications())return;
        postIfDue(c,p,now);
        ZonedDateTime next=CourseSchedule.nextNotice(now,p.days(),p.automationExcluded(),p.start());if(next==null)return;
        AlarmManager alarm=c.getSystemService(AlarmManager.class);long at=next.toInstant().toEpochMilli();
        try{
            if(AlarmScheduler.allowed(c))alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,operation(c));
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,operation(c));
            store(c).edit().putLong("next_at",at).apply();
        }catch(SecurityException e){
            // Permission can be revoked between the check and dispatch; reminders need no exact time.
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,operation(c));store(c).edit().putLong("next_at",at).apply();
        }
    }
    static synchronized boolean postIfDue(Context c,Prefs p,ZonedDateTime time){
        ZonedDateTime now=time.withZoneSameInstant(Policy.ZONE);LocalDate day=now.toLocalDate();String key="sent:"+day;
        if(!p.guidanceNotifications()||!CourseSchedule.noticeDue(now,p.days(),p.automationExcluded(),p.start())||store(c).getBoolean(key,false)||!Notices.attentionNotificationsAllowed(c))return false;
        String detail=day+" · "+CourseTimetable.on(day).title()+"\n"+CourseSchedule.summary(day)+(p.enabled()&&p.auto()?"":"\n자동 출결이 꺼져 있어요. 예약·자동 클릭 설정을 확인해 주세요.");
        PendingIntent open=PendingIntent.getActivity(c,1902,new Intent(c,MainActivity.class).putExtra("open_tab",0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notice=new Notification.Builder(c,"attention").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("오늘은 단축수업 · "+CourseSchedule.classEnd(day)+" 종료").setContentText(detail)
            .setStyle(new Notification.BigTextStyle().bigText(detail)).setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .setTimeoutAfter(Duration.between(now,day.plusDays(1).atStartOfDay(Policy.ZONE)).toMillis()).setCategory(Notification.CATEGORY_REMINDER).build();
        try{c.getSystemService(NotificationManager.class).notify(ID,notice);store(c).edit().putBoolean(key,true).commit();return true;}catch(SecurityException e){return false;}
    }
}
