package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.provider.Settings;

public final class Notices {
    public static final int MONITOR = 10, WIFI = 12, RESULT = 13, ENTRY_RESULT = 16, CHECKOUT_RESULT = 17, DEPARTURE = 18;
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
    public static boolean attendanceNotificationsAllowed(Context context) {
        channels(context);
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        return nm.areNotificationsEnabled() && nm.getNotificationChannel("result").getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    /** A confirmed entry and exit each keep their own notification, independently of monitoring. */
    public static void attendanceCompleted(Context context, String day, Policy.Phase phase, Policy.Record record) {
        if (phase == Policy.Phase.IDLE || !record.checkedIn() ||
                (phase == Policy.Phase.CHECKOUT && !record.checkedOut())) return;
        java.time.LocalDate date = java.time.LocalDate.parse(day);
        if (record.checkedOut()) clearDeparture(context,day);
        String title = phase == Policy.Phase.ENTRY ? "입실 완료" : AttendanceMetrics.isEarly(record.out(),new Prefs(context).checkoutFor(date)) ? "조퇴 완료" : "퇴실 완료";
        String time = phase == Policy.Phase.ENTRY ? record.in() : record.out();
        String detail = date + " · Chrome 사이트 기록 " + time;
        int id = phase == Policy.Phase.ENTRY ? ENTRY_RESULT : CHECKOUT_RESULT;
        channels(context);
        Notification notification = new Notification.Builder(context, "result").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title + " · " + time).setContentText(detail)
            .setStyle(new Notification.BigTextStyle().bigText(detail))
            .setContentIntent(open(context)).setAutoCancel(false).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS).build();
        // The date and phase avoid overwriting entry, exit, another day, or an error notice.
        try { context.getSystemService(NotificationManager.class).notify("attendance:" + date, id, notification); }
        catch (SecurityException ignored) {}
    }
    public static synchronized void alert(Context context, String title, String detail) {
        if (!new Prefs(context).guidanceNotifications()) return;
        channels(context);
        publish(context, RESULT, new Notification.Builder(context, "attention").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail))
            .setContentIntent(open(context)).setAutoCancel(true).setOnlyAlertOnce(true).build());
    }
    public static void wifi(Context context, boolean show, String detail) {
        if (!show || !new Prefs(context).guidanceNotifications()) { context.getSystemService(NotificationManager.class).cancel(WIFI); return; }
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
        if (!show || !new Prefs(context).guidanceNotifications()) { context.getSystemService(NotificationManager.class).cancel(id); return; }
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
    public static boolean attentionNotificationsAllowed(Context context) {
        channels(context);
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        return nm.areNotificationsEnabled() && nm.getNotificationChannel("attention").getImportance() != NotificationManager.IMPORTANCE_NONE;
    }
    public static boolean departure(Context context, String day) {
        channels(context);
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (!new Prefs(context).guidanceNotifications() || !attentionNotificationsAllowed(context)) return false;
        PendingIntent chrome = PendingIntent.getActivity(context, 7, new Intent(context, ChromeLaunchActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String detail = day + " · 강의실을 벗어났지만 퇴실 완료가 확인되지 않았습니다. 강의실 Wi-Fi와 Chrome 출결 기록을 확인해 주세요.";
        Notification notification = new Notification.Builder(context, "attention").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("퇴실 확인 필요 · 강의실 이탈 감지").setContentText(detail)
            .setStyle(new Notification.BigTextStyle().bigText(detail)).setContentIntent(open(context))
            .setAutoCancel(false).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_REMINDER)
            .addAction(new Notification.Action.Builder(null, "Chrome에서 확인", chrome).build()).build();
        try { nm.notify("departure:" + day, DEPARTURE, notification); return true; }
        catch (SecurityException e) { return false; }
    }
    public static void clearDeparture(Context context, String day) {
        context.getSystemService(NotificationManager.class).cancel("departure:" + day, DEPARTURE);
    }
    public static void chromeAction(Context context, boolean show, String detail) {
        final int id = 15;
        if (!show || !new Prefs(context).guidanceNotifications()) { context.getSystemService(NotificationManager.class).cancel(id); return; }
        channels(context);
        PendingIntent open = PendingIntent.getActivity(context, 6, new Intent(context, ChromeLaunchActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        publish(context, id, new Notification.Builder(context, "attention").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Chrome에서 출석 확인")
            .setContentText(detail).setStyle(new Notification.BigTextStyle().bigText(detail))
            .setContentIntent(open).setOnlyAlertOnce(true).setAutoCancel(true)
            .addAction(new Notification.Action.Builder(null, "Chrome 열기", open).build()).build());
    }
    /** Dismiss only optional guidance; keep attendance, foreground-service and test notifications. */
    public static void guidanceNotifications(Context context, boolean enabled) {
        new Prefs(context).raw.edit().putBoolean("guidance_notifications",enabled).apply();
        if (!enabled) {
            NotificationManager nm=context.getSystemService(NotificationManager.class);
            for (int id : new int[]{WIFI,RESULT,14,15,ShortDayReminder.ID}) nm.cancel(id);
            for (android.service.notification.StatusBarNotification notice : nm.getActiveNotifications())
                if (notice.getId()==DEPARTURE && notice.getTag()!=null && notice.getTag().startsWith("departure:"))
                    nm.cancel(notice.getTag(),DEPARTURE);
        }
        ShortDayReminder.reconcile(context);
    }
    private static void publish(Context context, int id, Notification notification) {
        if (!new Prefs(context).guidanceNotifications()) return;
        try { context.getSystemService(NotificationManager.class).notify(id, notification); }
        catch (SecurityException ignored) {}
    }
}
