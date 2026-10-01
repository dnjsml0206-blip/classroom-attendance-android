package net.tacademy.attendance;
import android.content.*;
public final class ShortDayReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){ShortDayReminder.reconcile(context);}
}
