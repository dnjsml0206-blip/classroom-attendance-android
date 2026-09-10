package net.tacademy.attendance;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.location.*;
import android.net.*;
import android.os.*;
import java.time.*;

public final class MonitorService extends Service implements LocationListener {
    public static volatile boolean running = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locations;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback callback;
    private Location latest;
    private boolean started;
    private final Runnable loop = new Runnable() {
        @Override public void run() { evaluate(); handler.postDelayed(this, 30000); }
    };
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Prefs prefs = new Prefs(this);
        if (intent != null && "STOP".equals(intent.getAction())) {
            prefs.enabled(false); AlarmScheduler.cancel(this); stopSelf(); return START_NOT_STICKY;
        }
        if (!prefs.enabled() || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return START_NOT_STICKY;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(Notices.MONITOR, Notices.monitor(this, "위치와 Wi-Fi 확인 중"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(Notices.MONITOR, Notices.monitor(this, "위치와 Wi-Fi 확인 중"));
        } catch (RuntimeException e) {
            Notices.alert(this, "감시를 다시 시작해 주세요", "앱에서 위치 권한과 위치 기능을 확인해 주세요");
            stopSelf(); return START_NOT_STICKY;
        }
        running = true;
        if (!started) {
            started = true;
            locations = getSystemService(LocationManager.class);
            connectivity = getSystemService(ConnectivityManager.class);
            try {
                for (String provider : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER}) {
                    if (locations.isProviderEnabled(provider)) {
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
            handler.post(loop);
            AlarmScheduler.arm(this, true);
            prefs.log("출석 감시 시작");
        } else {
            AlarmScheduler.arm(this, true);
            evaluate();
        }
        return START_STICKY;
    }
    private void evaluate() {
        Prefs prefs = new Prefs(this);
        if (!prefs.enabled()) { stopSelf(); return; }
        NetworkAccess.State wifi = NetworkAccess.inspect(this);
        String proximity = "강의실 위치 미등록";
        boolean near = false;
        if (prefs.hasLocation()) {
            if (latest == null || (SystemClock.elapsedRealtimeNanos() - latest.getElapsedRealtimeNanos()) > 300_000_000_000L ||
                !latest.hasAccuracy() || latest.getAccuracy() > Math.max(150, prefs.radius())) {
                proximity = "위치를 확인하는 중";
            } else {
                float[] distance = new float[1];
                Location.distanceBetween(prefs.latitude(), prefs.longitude(), latest.getLatitude(), latest.getLongitude(), distance);
                near = distance[0] <= prefs.radius();
                proximity = near ? "강의실 주변에 있습니다" : "강의실까지 약 " + Math.round(distance[0]) + "m";
            }
        }
        prefs.raw.edit().putString("current_ssid", wifi.ssid()).putBoolean("wifi_allowed", wifi.allowed())
            .putString("proximity", proximity).putLong("monitor_seen", System.currentTimeMillis()).apply();
        Notices.wifi(this, near && !wifi.allowed() && Policy.isClassDay(ZonedDateTime.now(Policy.ZONE), prefs.days(), prefs.excluded()),
            wifi.reason() + " · 알림을 눌러 연결하세요");
        String line = wifi.allowed() ? wifi.ssid() + " · " + prefs.raw.getString("status", "출석 확인 중") : proximity + " · Wi-Fi 연결 대기";
        getSystemService(NotificationManager.class).notify(Notices.MONITOR, Notices.monitor(this, line));
        AttendanceEngine.request(this, false, null);
    }
    @Override public void onLocationChanged(Location location) { latest = location; evaluate(); }
    @Override public void onProviderEnabled(String provider) { evaluate(); }
    @Override public void onProviderDisabled(String provider) { latest = null; evaluate(); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onDestroy() {
        running = false; handler.removeCallbacksAndMessages(null);
        if (locations != null) locations.removeUpdates(this);
        if (connectivity != null && callback != null) {
            try { connectivity.unregisterNetworkCallback(callback); } catch (RuntimeException ignored) {}
        }
        Notices.wifi(this, false, "");
        new Prefs(this).raw.edit().putLong("monitor_seen", 0).apply();
        super.onDestroy();
    }
}
