package net.tacademy.attendance;
import android.content.*;
public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { ScheduleController.reconcile(context); }
}
