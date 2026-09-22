package net.tacademy.attendance;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.location.*;
import android.net.*;
import android.os.*;
import java.time.*;

/** Attendance and location monitoring run only inside the configured attendance windows. */
public final class MonitorService extends Service implements LocationListener {
    public static volatile boolean running = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locations;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback callback, defaultCallback;
    private String networkTrigger = "LOCAL_CHECK", lastWifiCode = "";
    private long networkEventAt, networkEventElapsed;
    private int networkRechecks;
    private final Runnable networkCheck = new Runnable() {
        @Override public void run() {
            evaluate(networkTrigger);
            // Connectivity snapshots can lag the callback. Recheck locally for at most
            // three seconds; this never requests a network or contacts the attendance site.
            if (!stopping && !"ALLOWED".equals(lastWifiCode) && networkRechecks-- > 0)
                handler.postDelayed(this, 1000);
        }
    };
    private Location latest;
    private boolean started, stopping, locationUpdates;
    private final DepartureCheck departure = new DepartureCheck();
    private final Runnable loop = new Runnable() {
        @Override public void run() { evaluate(); if (!stopping) handler.postDelayed(this, 30000); }
    };
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Prefs prefs = new Prefs(this);
        if (intent != null && "STOP".equals(intent.getAction())) {
            prefs.enabled(false); ScheduledUnlock.cancel(this,"RESERVATION_DISABLED"); AlarmScheduler.cancel(this); Notices.checkoutAttention(this, false, ""); Notices.clearDeparture(this,LocalDate.now(Policy.ZONE).toString());
            stopSelf(); return START_NOT_STICKY;
        }
        ZonedDateTime now = ZonedDateTime.now(Policy.ZONE);
        Policy.Phase phase = prefs.phase(now);
        if(!started)DiagnosticLog.add(this,DiagnosticLog.Event.MONITOR_START_RECEIVED,phase,"ON_START_COMMAND");
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                throw new SecurityException();
            // A queued foreground start may arrive just after the window closes.
            // Complete Android's foreground handshake before stopping; do not request location or Chrome work.
            String line = switch (phase) {
                case ENTRY -> "입실 시간 · 연결 확인 중";
                case CHECKOUT -> "퇴실 시간 · 연결 확인 중";
                case IDLE -> "출석 시간 종료 · 감시 종료";
            };
            if (Build.VERSION.SDK_INT >= 29) startForeground(Notices.MONITOR, Notices.monitor(this, line), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(Notices.MONITOR, Notices.monitor(this, line));
        } catch (RuntimeException e) {
            DiagnosticLog.add(this,DiagnosticLog.Event.MONITOR_START_FAILED,phase,DiagnosticLog.failureCode(e));
            if (phase == Policy.Phase.IDLE) { stopSelf(); return START_NOT_STICKY; }
            prefs.status("위치 권한 확인 필요", "연결 탭에서 위치 항상 허용을 설정해 주세요");
            if (phase != Policy.Phase.ENTRY) Notices.checkoutAttention(this, true, "위치 항상 허용과 Wi-Fi 연결을 확인해 주세요");
            else Notices.alert(this, "입실 감시 권한을 확인해 주세요", "연결 탭에서 위치 항상 허용을 설정해 주세요");
            stopSelf(); return START_NOT_STICKY;
        }
        if (!prefs.monitoringNeeded(ZonedDateTime.now(Policy.ZONE))) { finishMonitoring(); return START_NOT_STICKY; }
        running = true;
        if (!started) {
            started = true; stopping = false;
            locations = getSystemService(LocationManager.class);
            connectivity = getSystemService(ConnectivityManager.class);
            callback = networkCallback("WIFI");
            defaultCallback = networkCallback("DEFAULT");
            try {
                connectivity.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback);
            } catch (RuntimeException e) { networkWatchFailed("WIFI", e); }
            try {
                // Wi-Fi can validate before Android selects it as Chrome's default route.
                connectivity.registerDefaultNetworkCallback(defaultCallback);
            } catch (RuntimeException e) { networkWatchFailed("DEFAULT", e); }
            DiagnosticLog.add(this,DiagnosticLog.Event.MONITOR_STARTED,phase,"SCHEDULE_WINDOW");
            prefs.log(phase == Policy.Phase.ENTRY ? "입실 시간 감시 시작" : "퇴실 시간 확인 시작");
            AlarmScheduler.arm(this, false);
            handler.post(loop);
        } else evaluate();
        // A scheduled alarm restores eligible work. Do not restart a continuous service.
        return START_NOT_STICKY;
    }
    private ConnectivityManager.NetworkCallback networkCallback(String source) {
        return new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) { changed("AVAILABLE"); }
            @Override public void onLost(Network n) { changed("LOST"); }
            @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { changed("CAPABILITIES"); }
            @Override public void onLinkPropertiesChanged(Network n, LinkProperties p) { changed("LINK"); }
            private void changed(String event) {
                long at = System.currentTimeMillis(), elapsed = SystemClock.elapsedRealtime();
                // Do not query synchronous network properties inside a system callback.
                handler.post(() -> {
                    if (stopping) return;
                    networkTrigger = source + "_" + event;
                    networkEventAt = at; networkEventElapsed = elapsed; networkRechecks = 3;
                    handler.removeCallbacks(networkCheck);
                    handler.post(networkCheck);
                });
            }
        };
    }
    private void networkWatchFailed(String source, RuntimeException e) {
        DiagnosticLog.add(this, DiagnosticLog.Event.NETWORK_WATCH_FAILED,
            new Prefs(this).phase(ZonedDateTime.now(Policy.ZONE)), source + ":" + DiagnosticLog.failureCode(e));
    }
    private void evaluate() { evaluate("LOCAL_CHECK"); }
    private void evaluate(String trigger) {
        if (stopping) return;
        Prefs prefs = new Prefs(this);
        ZonedDateTime now = ZonedDateTime.now(Policy.ZONE);
        Policy.Phase phase = prefs.phase(now);
        if (!prefs.monitoringNeeded(now)) { finishMonitoring(); return; }
        boolean departureWatch = prefs.departureWatching(now);
        updateLocationRequests(prefs.hasLocation() && (phase == Policy.Phase.ENTRY || departureWatch));
        NetworkAccess.State wifi = NetworkAccess.inspect(this);
        if (!wifi.code().equals(lastWifiCode)) {
            org.json.JSONObject details = new org.json.JSONObject();
            try {
                details.put("network_trigger", trigger).put("wifi_check_code", wifi.code());
                if (!"LOCAL_CHECK".equals(trigger)) details.put("network_event_at_ms", networkEventAt)
                    .put("elapsed_ms", Math.max(0, SystemClock.elapsedRealtime() - networkEventElapsed));
            } catch (org.json.JSONException ignored) {}
            DiagnosticLog.add(this, DiagnosticLog.Event.NETWORK_STATE_CHANGED, phase, wifi.code(), details);
            lastWifiCode = wifi.code();
        }
        if (wifi.allowed()) rememberClassroom(prefs,now);
        String proximity = "강의실 위치 미등록";
        boolean near = false;
        if (prefs.hasLocation() && (phase == Policy.Phase.ENTRY || departureWatch)) {
            if (latest == null || (SystemClock.elapsedRealtimeNanos() - latest.getElapsedRealtimeNanos()) > 300_000_000_000L ||
                !latest.hasAccuracy() || latest.getAccuracy() > prefs.radius()) {
                proximity = "위치 확인 중 · " + prefs.radius() + "m 이내 정확도가 필요합니다";
            } else {
                float[] distance = new float[1];
                Location.distanceBetween(prefs.latitude(), prefs.longitude(), latest.getLatitude(), latest.getLongitude(), distance);
                near = Policy.nearby(distance[0], latest.getAccuracy(), prefs.radius());
                proximity = near ? "강의실 주변에 있습니다" : "강의실까지 약 " + Math.round(distance[0]) + "m";
                if (near) rememberClassroom(prefs,now);
            }
        }
        prefs.raw.edit().putString("current_ssid", wifi.ssid()).putBoolean("wifi_allowed", wifi.allowed())
            .putString("proximity", proximity).putLong("monitor_seen", System.currentTimeMillis()).apply();
        Notices.wifi(this, phase == Policy.Phase.ENTRY && near && !wifi.allowed(), wifi.reason() + " · 알림을 눌러 연결하세요");
        if (departureWatch) checkDeparture(prefs,now);
        else departure.reset();
        if (phase == Policy.Phase.CHECKOUT && !wifi.allowed())
            Notices.checkoutAttention(this, true, wifi.reason() + " · 연결하면 예약 시간 안에서 다시 확인합니다");
        String line = (phase == Policy.Phase.ENTRY ? "입실" : "퇴실") + " 확인 · " +
            (wifi.allowed() ? wifi.ssid() : "Wi-Fi 연결 대기");
        getSystemService(NotificationManager.class).notify(Notices.MONITOR, Notices.monitor(this, line));
        AttendanceEngine.request(this, false, () -> handler.post(() -> {
            if (stopping) return;
            if (!new Prefs(this).monitoringNeeded(ZonedDateTime.now(Policy.ZONE))) finishMonitoring();
            else if (phase == Policy.Phase.CHECKOUT && !ChromeAttendanceService.busy())
                Notices.checkoutAttention(this, true, new Prefs(this).raw.getString("detail", "Wi-Fi 연결과 사이트 기록을 확인해 주세요"));
        }));
    }
    private void updateLocationRequests(boolean needed) {
        if (locations == null || needed == locationUpdates) return;
        try {
            locations.removeUpdates(this); locationUpdates = false; latest = null; departure.reset();
            if (!needed) return;
            for (String provider : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER}) {
                if (!locations.getAllProviders().contains(provider)) continue;
                locations.requestLocationUpdates(provider,20_000,0,this,Looper.getMainLooper());
                Location last = locations.getLastKnownLocation(provider);
                if (last != null && (latest == null || last.getElapsedRealtimeNanos() > latest.getElapsedRealtimeNanos())) latest = last;
            }
            locationUpdates = true;
        } catch (SecurityException e) { new Prefs(this).status("위치 권한 확인 필요", "위치 항상 허용을 켜야 강의실 이탈을 확인할 수 있습니다"); }
    }
    private void rememberClassroom(Prefs prefs, ZonedDateTime now) {
        String day = now.toLocalDate().toString();
        if (!day.equals(prefs.raw.getString("departure_seen_day",""))) prefs.raw.edit().putString("departure_seen_day",day).apply();
    }
    private void checkDeparture(Prefs prefs, ZonedDateTime now) {
        if (!prefs.classroomSeenToday() || latest == null || !latest.hasAccuracy()) { departure.reset(); return; }
        float[] distance = new float[1];
        Location.distanceBetween(prefs.latitude(),prefs.longitude(),latest.getLatitude(),latest.getLongitude(),distance);
        if (!departure.update(distance[0],latest.getAccuracy(),prefs.radius(),latest.getElapsedRealtimeNanos()/1_000_000,SystemClock.elapsedRealtime())) return;
        String day = now.toLocalDate().toString();
        if (Notices.departure(this,day)) {
            prefs.raw.edit().putString("departure_notified_day",day).apply();
            prefs.log("퇴실 미확인 · 강의실 이탈 알림");
            updateLocationRequests(false);
            AlarmScheduler.arm(this,false);
        }
    }
    private void finishMonitoring() {
        if (stopping) return;
        stopping = true;
        Prefs prefs = new Prefs(this);
        if (!prefs.enabled() || prefs.checkedOutToday()) Notices.checkoutAttention(this, false, "");
        AlarmScheduler.arm(this, false);
        DiagnosticLog.add(this,DiagnosticLog.Event.MONITOR_STOPPED,prefs.phase(ZonedDateTime.now(Policy.ZONE)),"WINDOW_OR_COMPLETION");
        prefs.log("출석 감시 종료 · 다음 예약 대기");
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override public void onLocationChanged(Location location) { latest = location; evaluate(); }
    @Override public void onProviderEnabled(String provider) { evaluate(); }
    @Override public void onProviderDisabled(String provider) { latest = null; evaluate(); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override public void onDestroy() {
        stopping = true; running = false; ChromeAttendanceService.cancelScheduledWork(); handler.removeCallbacksAndMessages(null);
        if (locations != null) { try { locations.removeUpdates(this); } catch (SecurityException ignored) {} }
        if (connectivity != null) {
            for (ConnectivityManager.NetworkCallback watcher : new ConnectivityManager.NetworkCallback[]{callback, defaultCallback})
                if (watcher != null) try { connectivity.unregisterNetworkCallback(watcher); } catch (RuntimeException ignored) {}
        }
        Notices.wifi(this, false, "");
        new Prefs(this).raw.edit().putLong("monitor_seen", 0).apply();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
