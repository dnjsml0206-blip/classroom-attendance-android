package net.tacademy.attendance;
import android.app.Activity;
import android.os.*;
import android.widget.Toast;
import java.time.*;
/** One explicit activation, five seconds to cancel. Never repeats or resumes after activity destruction. */
final class ActivationRun {
    static final long DELAY_MS=5_000;
    private static final Handler handler=new Handler(Looper.getMainLooper());
    private static Activity owner;private static Runnable task;private static long due;private static Policy.Phase phase=Policy.Phase.IDLE;
    static boolean pending(){return task!=null;}
    static String label(){return pending()?Math.max(1,(due-SystemClock.elapsedRealtime()+999)/1000)+"초 뒤 "+(phase==Policy.Phase.ENTRY?"입실":"퇴실")+" 시작":"";}
    static boolean start(Activity activity){
        if(pending()){Toast.makeText(activity,label(),Toast.LENGTH_SHORT).show();return true;}
        Prefs p=new Prefs(activity);ZonedDateTime now=ZonedDateTime.now(Policy.ZONE);
        phase=AttendanceAttemptPolicy.activationPhase(now,p.days(),p.excluded(),p.start(),p.end(),p.checkout(),p.checkedInToday(),p.checkedOutToday());
        if(phase==Policy.Phase.IDLE)return false;
        owner=activity;due=SystemClock.elapsedRealtime()+DELAY_MS;
        final String day=now.toLocalDate().toString();final Policy.Phase selected=phase;
        task=()->{
            task=null;owner=null;
            Prefs current=new Prefs(activity);
            if(activity.isFinishing()||activity.isDestroyed()||!current.enabled()||!current.auto()||!day.equals(LocalDate.now(Policy.ZONE).toString()))return;
            DiagnosticLog.add(activity,DiagnosticLog.Event.ACTIVATION_FIRED,selected,"EXPLICIT_ENABLE");
            // Enabling a reservation must not authorize a duplicate pending click after an OFF/ON cycle.
            boolean pendingClick=(day+":"+selected).equals(current.raw.getString("chrome_pending",""));
            ChromeAttendanceService.beginManual(activity,selected,pendingClick,false);
            ScheduleController.reconcile(activity);
        };
        DiagnosticLog.add(activity,DiagnosticLog.Event.ACTIVATION_QUEUED,phase,"FIVE_SECOND_DELAY");
        handler.postDelayed(task,DELAY_MS);Toast.makeText(activity,label()+" · Chrome에서 완료 기록을 먼저 확인해요",Toast.LENGTH_LONG).show();return true;
    }
    static void cancel(){if(task!=null)handler.removeCallbacks(task);task=null;owner=null;phase=Policy.Phase.IDLE;}
    static void cancel(Activity activity){if(owner==activity)cancel();}
}
