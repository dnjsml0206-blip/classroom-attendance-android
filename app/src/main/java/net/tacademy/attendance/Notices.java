package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.provider.Settings;

public final class Notices {
    public static final int MONITOR = 10, WIFI = 12, RESULT = 13;
    private Notices() {}
    public static void channels(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("monitor", "출석 감시 상태", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel("attention", "연결 및 확인 알림", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel("result", "입실·퇴실 결과", NotificationManager.IMPORTANCE_DEFAULT));
    }
    public static PendingIntent open(Context context) {
        return PendingIntent.getActivity(context, 1, new Intent(context, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static Notification monitor(Context context, String detail) {
        channels(context);
        Intent stop = new Intent(context, MonitorService.class).setAction("STOP");
        return new Notification.Builder(context, "monitor").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("출석 메이트 · 감시 중").setContentText(detail)
            .setContentIntent(open(context)).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null, "예약 끄기",
                PendingIntent.getService(context, 3, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build())
            .build();
    }
    public static synchronized void setResultNotificationsEnabled(Context context, boolean enabled) {
        new Prefs(context).resultNotificationsEnabled(enabled);
        if (!enabled) context.getSystemService(NotificationManager.class).cancel(RESULT);
    }
    public static synchronized void alert(Context context, String title, String detail) {
        if (!new Prefs(context).resultNotificationsEnabled()) return;
        channels(context);
        publish(context, RESULT, new Notification.Builder(context, "result").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail))
            .setContentIntent(open(context)).setAutoCancel(true).setOnlyAlertOnce(true).build());
    }
    public static void wifi(Context context, boolean show, String detail) {
        if (!show) { context.getSystemService(NotificationManager.class).cancel(WIFI); return; }
        channels(context);
        Intent settings = new Intent(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS);
        PendingIntent action = PendingIntent.getActivity(context, 4, settings, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        publish(context, WIFI, new Notification.Builder(context, "attention").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("강의실 Wi-Fi 미연결 · 출석 대기 중").setContentText(detail)
            .setContentIntent(action).setOnlyAlertOnce(true).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Wi-Fi 연결", action).build()).build());
    }
    public static void checkoutAttention(Context context, boolean show, String detail) {
        final int id = 14;
        if (!show) { context.getSystemService(NotificationManager.class).cancel(id); return; }
        channels(context);
        Intent settings = new Intent(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS);
        PendingIntent action = PendingIntent.getActivity(context, 5, settings, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        publish(context, id, new Notification.Builder(context, "attention").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("퇴실 미완료 · Wi-Fi 연결 확인")
            .setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail))
            .setContentIntent(action).setOnlyAlertOnce(true).setAutoCancel(true)
            .addAction(new Notification.Action.Builder(null, "Wi-Fi 연결", action).build())
            .addAction(new Notification.Action.Builder(null, "앱에서 확인", open(context)).build()).build());
    }
    public static void chromeAction(Context context, boolean show, String detail) {
        final int id = 15;
        if (!show) { context.getSystemService(NotificationManager.class).cancel(id); return; }
        channels(context);
        PendingIntent open = PendingIntent.getActivity(context, 6, new Intent(context, ChromeLaunchActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        publish(context, id, new Notification.Builder(context, "attention").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Chrome에서 출석 확인")
            .setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail))
            .setContentIntent(open).setOnlyAlertOnce(true).setAutoCancel(true)
            .addAction(new Notification.Action.Builder(null, "Chrome 열기", open).build()).build());
    }
    private static void publish(Context context, int id, Notification notification) {
        try { context.getSystemService(NotificationManager.class).notify(id, notification); }
        catch (SecurityException ignored) {}
    }
}
