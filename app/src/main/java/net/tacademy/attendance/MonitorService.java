package net.tacademy.attendance;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.location.*;
import android.net.*;
import android.os.*;
import java.time.*;

/** Runs only inside a configured attendance window, until its record is confirmed. */
public final class MonitorService extends Service implements LocationListener {
    public static volatile boolean running = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locations;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback callback;
    private Location latest;
    private boolean started, stopping;
    private final Runnable loop = new Runnable() {
        @Override public void run() { evaluate(); if (!stopping) handler.postDelayed(this, 30000); }
    };
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Prefs prefs = new Prefs(this);
        if (intent != null && "STOP".equals(intent.getAction())) {
            prefs.enabled(false); AlarmScheduler.cancel(this); Notices.checkoutAttention(this, false, "");
            stopSelf(); return START_NOT_STICKY;
        }
        Policy.Phase phase = prefs.phase(ZonedDateTime.now(Policy.ZONE));
        if (phase == Policy.Phase.IDLE) { stopSelf(); return START_NOT_STICKY; }
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                throw new SecurityException();
            if (Build.VERSION.SDK_INT >= 29) startForeground(Notices.MONITOR, Notices.monitor(this, phase == Policy.Phase.ENTRY ? "입실 시간 · 연결 확인 중" : "퇴실 시간 · 연결 확인 중"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(Notices.MONITOR, Notices.monitor(this, "출석 시간 · 연결 확인 중"));
        } catch (RuntimeException e) {
            prefs.status("위치 권한 확인 필요", "연결 탭에서 위치 항상 허용을 설정해 주세요");
            if (phase == Policy.Phase.CHECKOUT) Notices.checkoutAttention(this, true, "위치 항상 허용과 Wi-Fi 연결을 확인해 주세요");
            else Notices.alert(this, "입실 감시 권한을 확인해 주세요", "연결 탭에서 위치 항상 허용을 설정해 주세요");
            stopSelf(); return START_NOT_STICKY;
        }
        running = true;
        if (!started) {
            started = true; stopping = false;
            locations = getSystemService(LocationManager.class);
            connectivity = getSystemService(ConnectivityManager.class);
            try {
                if (phase == Policy.Phase.ENTRY) {
                    for (String provider : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER}) {
                        if (!locations.getAllProviders().contains(provider)) continue;
                        locations.requestLocationUpdates(provider, 60000, 0, this, Looper.getMainLooper());
                        Location last = locations.getLastKnownLocation(provider);
                        if (last != null && (latest == null || last.getElapsedRealtimeNanos() > latest.getElapsedRealtimeNanos())) latest = last;
                    }
                }
                callback = new ConnectivityManager.NetworkCallback() {
                    @Override public void onAvailable(Network n) { handler.post(() -> evaluate()); }
                    @Override public void onLost(Network n) { handler.post(() -> evaluate()); }
                    @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { handler.post(() -> evaluate()); }
                };
                connectivity.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback);
            } catch (SecurityException e) { prefs.status("권한 확인 필요", "위치 권한을 다시 확인해 주세요"); }
            prefs.log(phase == Policy.Phase.ENTRY ? "입실 시간 감시 시작" : "퇴실 시간 확인 시작");
            AlarmScheduler.arm(this, false);
            handler.post(loop);
        } else evaluate();
        // A scheduled alarm restores eligible work. Do not restart a continuous service.
        return START_NOT_STICKY;
    }
    private void evaluate() {
        if (stopping) return;
        Prefs prefs = new Prefs(this);
        Policy.Phase phase = prefs.phase(ZonedDateTime.now(Policy.ZONE));
        if (phase == Policy.Phase.IDLE) { finishMonitoring(); return; }
        NetworkAccess.State wifi = NetworkAccess.inspect(this);
        String proximity = "강의실 위치 미등록";
        boolean near = false;
        if (phase == Policy.Phase.ENTRY && prefs.hasLocation()) {
            if (latest == null || (SystemClock.elapsedRealtimeNanos() - latest.getElapsedRealtimeNanos()) > 300_000_000_000L ||
                !latest.hasAccuracy() || latest.getAccuracy() > prefs.radius()) {
                proximity = "위치 확인 중 · " + prefs.radius() + "m 이내 정확도가 필요합니다";
            } else {
                float[] distance = new float[1];
                Location.distanceBetween(prefs.latitude(), prefs.longitude(), latest.getLatitude(), latest.getLongitude(), distance);
                near = Policy.nearby(distance[0], latest.getAccuracy(), prefs.radius());
                proximity = near ? "강의실 주변에 있습니다" : "강의실까지 약 " + Math.round(distance[0]) + "m";
            }
        }
        prefs.raw.edit().putString("current_ssid", wifi.ssid()).putBoolean("wifi_allowed", wifi.allowed())
            .putString("proximity", proximity).putLong("monitor_seen", System.currentTimeMillis()).apply();
        Notices.wifi(this, phase == Policy.Phase.ENTRY && near && !wifi.allowed(), wifi.reason() + " · 알림을 눌러 연결하세요");
        if (phase == Policy.Phase.CHECKOUT && !wifi.allowed())
            Notices.checkoutAttention(this, true, wifi.reason() + " · 연결하면 예약 시간 안에서 다시 확인합니다");
        String line = (phase == Policy.Phase.ENTRY ? "입실" : "퇴실") + " 확인 · " +
            (wifi.allowed() ? wifi.ssid() : "Wi-Fi 연결 대기");
        getSystemService(NotificationManager.class).notify(Notices.MONITOR, Notices.monitor(this, line));
        AttendanceEngine.request(this, false, () -> handler.post(() -> {
            if (stopping) return;
            if (new Prefs(this).phase(ZonedDateTime.now(Policy.ZONE)) == Policy.Phase.IDLE) finishMonitoring();
            else if (phase == Policy.Phase.CHECKOUT && !ChromeAttendanceService.busy())
                Notices.checkoutAttention(this, true, new Prefs(this).raw.getString("detail", "Wi-Fi 연결과 사이트 기록을 확인해 주세요"));
        }));
    }
    private void finishMonitoring() {
        if (stopping) return;
        stopping = true;
        Prefs prefs = new Prefs(this);
        if (!prefs.enabled() || prefs.checkedOutToday()) Notices.checkoutAttention(this, false, "");
        AlarmScheduler.arm(this, false);
        prefs.log("출석 감시 종료 · 다음 예약 대기");
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override public void onLocationChanged(Location location) { latest = location; evaluate(); }
    @Override public void onProviderEnabled(String provider) { evaluate(); }
    @Override public void onProviderDisabled(String provider) { latest = null; evaluate(); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onDestroy() {
        stopping = true; running = false; ChromeAttendanceService.cancelWork(); handler.removeCallbacksAndMessages(null);
        if (locations != null) { try { locations.removeUpdates(this); } catch (SecurityException ignored) {} }
        if (connectivity != null && callback != null) {
            try { connectivity.unregisterNetworkCallback(callback); } catch (RuntimeException ignored) {}
        }
        Notices.wifi(this, false, "");
        new Prefs(this).raw.edit().putLong("monitor_seen", 0).apply();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
