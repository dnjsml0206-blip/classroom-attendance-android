package net.tacademy.attendance;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int INK = 0xFF183631, MUTED = 0xFF687D77, TEAL = 0xFF007C78, BG = 0xFFF4F7F6;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private LinearLayout root, content, tabs;
    private int selected;
    private boolean pendingStart, pendingLocation;
    private TextView status, detail, wifiStatus, locationStatus, inTime, outTime, modeStatus;
    private Button monitorButton;
    private LocationManager locationManager;
    private LocationListener oneLocation;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { if (selected == 0) updateHome(); handler.postDelayed(this, 2000); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        Notices.channels(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        render(0);
    }
    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresh); handler.post(refresh);
        if (selected == 2) render(2);
        if (prefs.enabled()) AlarmScheduler.arm(this, false);
    }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (locationManager != null && oneLocation != null) locationManager.removeUpdates(oneLocation);
        super.onDestroy();
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setLineSpacing(dp(3), 1); if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }
    private void gap(LinearLayout parent, int amount) { View space = new View(this); parent.addView(space, new LinearLayout.LayoutParams(1, dp(amount))); }
    private Button button(String label, boolean primary, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setTextSize(15); button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : TEAL);
        button.setBackground(shape(primary ? TEAL : 0xFFE5F2EF, 14));
        button.setMinHeight(dp(50)); button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setOnClickListener(v -> action.run());
        return button;
    }
    private LinearLayout card(String label) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(20), dp(20), dp(20)); card.setBackground(shape(Color.WHITE, 22));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(14);
        content.addView(card, params);
        if (!label.isEmpty()) { card.addView(text(label, 13, TEAL, true)); gap(card, 10); }
        return card;
    }
    private void note(LinearLayout parent, String message) { TextView view = text(message, 13, MUTED, false); gap(parent, 8); parent.addView(view); }
    private void action(LinearLayout parent, String label, boolean primary, Runnable run) { gap(parent, 10); parent.addView(button(label, primary, run), new LinearLayout.LayoutParams(-1, -2)); }
    private EditText input(String hint, String value, boolean numeric) {
        EditText edit = new EditText(this); edit.setHint(hint); edit.setText(value); edit.setTextSize(15);
        edit.setTextColor(INK); edit.setSingleLine(true);
        if (numeric) edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        else edit.setInputType(InputType.TYPE_CLASS_TEXT);
        edit.setPadding(dp(10), dp(8), dp(10), dp(8));
        return edit;
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private void openSettings(String action) {
        try { startActivity(new Intent(action)); } catch (RuntimeException e) { toast("휴대폰 설정에서 확인해 주세요"); }
    }

    private void render(int tab) {
        selected = tab; root.removeAllViews();
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(22), dp(26), dp(22), dp(18));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(text("T  /  ATTENDANCE MATE", 12, TEAL, true)); gap(content, 10);
        content.addView(text(tab == 0 ? "출석, 잊지 않도록." : tab == 1 ? "나에게 맞게 설정" : "한 번 연결하고\n편하게 출석하세요.", 28, INK, true));
        gap(content, 8);
        content.addView(text(tab == 0 ? ZonedDateTime.now(Policy.ZONE).format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))
            : tab == 1 ? "Wi-Fi · 장소 · 수업 일정" : "사이트 인증과 휴대폰 권한을 확인하세요.", 14, MUTED, false));
        gap(content, 24);
        if (tab == 0) home(); else if (tab == 1) settings(); else connection();
        tabs = new LinearLayout(this); tabs.setPadding(dp(16), dp(10), dp(16), dp(10)); tabs.setBackgroundColor(Color.WHITE);
        String[] labels = {"오늘", "설정", "연결"};
        for (int i = 0; i < labels.length; i++) {
            final int target = i; Button item = button(labels[i], selected == i, () -> render(target));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1); p.setMargins(dp(4), 0, dp(4), 0);
            tabs.addView(item, p);
        }
        root.addView(tabs);
    }

    private void home() {
        LinearLayout state = card("TODAY'S STATUS");
        status = text("", 23, INK, true); detail = text("", 14, MUTED, false);
        state.addView(status); gap(state, 8); state.addView(detail); gap(state, 16);
        LinearLayout times = new LinearLayout(this);
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text("입실", 12, MUTED, false)); inTime = text("—", 25, TEAL, true); left.addView(inTime);
        LinearLayout right = new LinearLayout(this); right.setOrientation(LinearLayout.VERTICAL);
        right.addView(text("퇴실", 12, MUTED, false)); outTime = text("—", 25, TEAL, true); right.addView(outTime);
        times.addView(left, new LinearLayout.LayoutParams(0, -2, 1)); times.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        state.addView(times);
        note(state, "사이트에 기록된 결과를 확인한 뒤 표시합니다.");
        LinearLayout environment = card("CONNECTION");
        wifiStatus = text("", 17, INK, true); locationStatus = text("", 14, MUTED, false);
        environment.addView(wifiStatus); gap(environment, 8); environment.addView(locationStatus);
        action(environment, "Wi-Fi 설정 열기", false, () -> openSettings(Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS));
        LinearLayout control = card("AUTOMATION");
        modeStatus = text("", 16, INK, true); control.addView(modeStatus);
        note(control, "17:50 퇴실 · 한국 시간\n휴대폰에서 감시를 시작하면 알림바에 상태가 표시됩니다.");
        monitorButton = button("", true, this::toggleMonitor); gap(control, 14); control.addView(monitorButton);
        action(control, "지금 상태 확인", false, () -> {
            if (!prefs.enabled()) { toast("먼저 감시를 시작해 주세요"); return; }
            AttendanceEngine.request(this, true, null); toast("지정 Wi-Fi에서 출석 기록을 확인합니다");
        });
        LinearLayout log = card("RECENT ACTIVITY");
        String messages = prefs.raw.getString("log", "");
        log.addView(text(messages.isEmpty() ? "아직 활동 기록이 없습니다." : messages, 13, MUTED, false));
        updateHome();
    }

    private void setIfChanged(TextView view, String value) { if (!view.getText().toString().equals(value)) view.setText(value); }

    private void updateHome() {
        if (status == null || selected != 0) return;
        boolean active = prefs.enabled() && MonitorService.running;
        setIfChanged(status, !prefs.enabled() ? "감시를 시작해 주세요" : !active ? "감시를 다시 시작해 주세요" : prefs.raw.getString("status", "연결 확인 중"));
        setIfChanged(detail, !prefs.enabled() ? "위치와 Wi-Fi를 확인해 출석을 도와드려요." : !active ? "앱 중단 또는 재부팅 후에는 감시를 다시 시작해 주세요." : prefs.raw.getString("detail", ""));
        boolean today = LocalDate.now(Policy.ZONE).toString().equals(prefs.raw.getString("record_day", ""));
        String entry = today ? prefs.raw.getString("in", "") : "", exit = today ? prefs.raw.getString("out", "") : "";
        setIfChanged(inTime, entry.isEmpty() ? "—" : entry); setIfChanged(outTime, exit.isEmpty() ? "—" : exit);
        NetworkAccess.State network = NetworkAccess.inspect(this);
        setIfChanged(wifiStatus, network.allowed() ? "●  " + network.ssid() + " 연결됨" : network.ssid().isEmpty() ? "○  지정 Wi-Fi 연결 대기" : "○  " + network.ssid());
        setIfChanged(locationStatus, active ? prefs.raw.getString("proximity", "위치 확인 중") :
            prefs.hasLocation() ? "강의실 위치 등록됨 · 반경 " + prefs.radius() + "m" : "설정에서 강의실 위치를 등록해 주세요.");
        setIfChanged(modeStatus, prefs.auto() ? "자동 입실 · 자동 퇴실" : "조회 모드 · 자동 처리는 꺼짐");
        setIfChanged(monitorButton, active ? "감시 일시 중지" : "감시 시작");
    }

    private void toggleMonitor() {
        if (prefs.enabled() && MonitorService.running) {
            prefs.enabled(false); stopService(new Intent(this, MonitorService.class)); AlarmScheduler.cancel(this);
            Notices.wifi(this, false, ""); prefs.log("출석 감시 중지"); updateHome(); return;
        }
        if (prefs.ssids().isEmpty() || prefs.days().isEmpty()) { toast("Wi-Fi와 수업 요일을 먼저 등록해 주세요"); render(1); return; }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingStart = true; permissions(); return;
        }
        LocationManager lm = getSystemService(LocationManager.class);
        if (Build.VERSION.SDK_INT >= 28 && !lm.isLocationEnabled()) { toast("휴대폰의 위치 기능을 켜 주세요"); openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS); return; }
        prefs.enabled(true);
        try { startForegroundService(new Intent(this, MonitorService.class)); }
        catch (RuntimeException e) { prefs.enabled(false); toast("위치 권한을 확인한 뒤 다시 시작해 주세요"); }
        if (!AlarmScheduler.allowed(this)) toast("17:50 예약을 위해 연결 탭에서 정확한 알람을 허용해 주세요");
        updateHome();
    }

    private void settings() {
        LinearLayout notifications = card("알림");
        Switch resultAlerts = new Switch(this);
        resultAlerts.setText("입실 · 퇴실 알림"); resultAlerts.setTextSize(16); resultAlerts.setTextColor(INK);
        resultAlerts.setMinHeight(dp(48));
        resultAlerts.setChecked(prefs.resultNotificationsEnabled()); notifications.addView(resultAlerts);
        note(notifications, "입실·퇴실 결과와 출석 확인 요청을 알립니다. 기본값은 켜짐이며 앱을 다시 열어도 설정이 유지됩니다.");
        note(notifications, "Wi-Fi 미연결 알림과 감시 중 상태 알림은 별도로 표시됩니다.");
        if (!getSystemService(NotificationManager.class).areNotificationsEnabled())
            note(notifications, "휴대폰에서 앱 알림이 차단되어 있습니다. 연결 탭에서 알림 권한을 허용해 주세요.");
        resultAlerts.setOnCheckedChangeListener((v, checked) -> {
            Notices.setResultNotificationsEnabled(this, checked);
            prefs.log(checked ? "입실·퇴실 알림 켜짐" : "입실·퇴실 알림 꺼짐");
        });
        LinearLayout wifi = card("출석에 사용할 Wi-Fi");
        wifi.addView(text("등록한 네트워크에서만 처리합니다", 17, INK, true));
        note(wifi, "Wi-Fi 이름은 대소문자를 구분합니다. 실제 연결은 휴대폰 Wi-Fi 설정에서 저장해 주세요.");
        for (String ssid : prefs.ssids()) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); gap(wifi, 10);
            TextView name = text(ssid, 15, INK, true); name.setMaxLines(2);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(button("삭제", false, () -> {
                Set<String> values = prefs.ssids(); values.remove(ssid); prefs.ssids(values); prefs.log("Wi-Fi 목록 변경"); render(1);
            }), new LinearLayout.LayoutParams(dp(72), dp(48)));
            wifi.addView(row);
        }
        if (prefs.ssids().isEmpty()) note(wifi, "등록된 Wi-Fi가 없습니다.");
        action(wifi, "+ Wi-Fi 추가", true, this::addWifi);
        action(wifi, "현재 Wi-Fi 등록", false, () -> {
            String current = NetworkAccess.inspect(this).ssid();
            if (!Policy.validSsid(current)) { toast("현재 Wi-Fi 이름을 확인할 수 없습니다. 직접 입력해 주세요"); return; }
            Set<String> values = prefs.ssids(); values.add(current); prefs.ssids(values); render(1);
        });
        LinearLayout location = card("강의실 위치");
        location.addView(text(prefs.hasLocation() ? "주변 " + prefs.radius() + "m에서 감지" : "아직 등록하지 않았어요", 18, INK, true));
        note(location, "강의실 주변인데 Wi-Fi가 연결되지 않으면 알림바로 알려드립니다.");
        action(location, "현재 위치로 등록", true, this::currentLocation);
        action(location, "좌표와 반경 직접 입력", false, () -> editLocation(null));
        if (prefs.hasLocation()) action(location, "강의실 위치 삭제", false, () -> {
            prefs.raw.edit().remove("latitude").remove("longitude").apply(); Notices.wifi(this, false, ""); render(1);
        });
        LinearLayout schedule = card("수업 일정");
        schedule.addView(text("입실 " + prefs.start() + "–" + prefs.end() + "\n퇴실 17:50", 19, INK, true));
        note(schedule, "기본값: 월–금, 입실 08:00–17:45. 실제 수업 일정에 맞게 바꿔 주세요.");
        action(schedule, "요일 · 입실 시간 변경", false, this::editSchedule);
        action(schedule, "휴강일 등록 (" + prefs.excluded().size() + "일)", false, this::editExcluded);
        LinearLayout automation = card("자동 처리");
        Switch auto = new Switch(this); auto.setText("입실 · 퇴실 자동 처리"); auto.setTextSize(16); auto.setTextColor(INK);
        auto.setChecked(prefs.auto()); automation.addView(auto);
        note(automation, "처음에는 조회만 합니다. 사이트 인증 후 켜면 지정 Wi-Fi에서 입실하고 17:50에 퇴실합니다. 실제 계정·기기에서 검증이 필요한 초기 버전입니다.");
        auto.setOnCheckedChangeListener((v, checked) -> {
            if (checked && !new AuthVault(this).ready()) { auto.setChecked(false); toast("연결 탭에서 사이트 인증을 먼저 완료해 주세요"); return; }
            prefs.raw.edit().putBoolean("auto", checked).apply();
            prefs.log(checked ? "자동 입퇴실 켜짐" : "조회 모드로 변경");
            if (prefs.enabled()) AttendanceEngine.request(this, true, null);
        });
        LinearLayout help = card("Wi-Fi 자동 연결 안내");
        help.addView(text("휴대폰 자동 연결을 함께 사용하세요", 17, INK, true));
        note(help, "Android 일반 앱은 꺼진 Wi-Fi를 직접 켤 수 없습니다. Wi-Fi를 켜 두고 등록한 네트워크의 자동 연결을 켜 주세요. 지원되는 갤럭시에서는 모드 및 루틴에 ‘강의실 도착 → Wi-Fi 켜기’를 설정할 수 있습니다.");
        action(help, "휴대폰 Wi-Fi 설정", false, () -> openSettings(Settings.ACTION_WIFI_SETTINGS));
    }

    private void addWifi() {
        EditText name = input("예: TACADEMY_5G", "", false);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Wi-Fi 추가").setView(name)
            .setPositiveButton("추가", null).setNegativeButton("취소", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String value = Policy.normalizeSsid(name.getText().toString());
            if (!Policy.validSsid(value)) { name.setError("1–32바이트의 Wi-Fi 이름을 입력해 주세요"); return; }
            Set<String> values = prefs.ssids();
            if (!values.add(value)) { name.setError("이미 등록한 Wi-Fi입니다"); return; }
            prefs.ssids(values); prefs.log("Wi-Fi 추가"); dialog.dismiss(); render(1);
        }));
        dialog.show();
    }

    private LinearLayout form() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); view.setPadding(dp(22), dp(10), dp(22), dp(8)); return view; }
    private void editLocation(Location current) {
        LinearLayout view = form();
        EditText lat = input("위도", current != null ? String.valueOf(current.getLatitude()) : prefs.hasLocation() ? String.valueOf(prefs.latitude()) : "", true);
        EditText lon = input("경도", current != null ? String.valueOf(current.getLongitude()) : prefs.hasLocation() ? String.valueOf(prefs.longitude()) : "", true);
        EditText radius = input("반경(m)", String.valueOf(prefs.radius()), true);
        view.addView(lat); view.addView(lon); view.addView(radius);
        note(view, current != null ? "현재 위치 정확도: 약 " + Math.round(current.getAccuracy()) + "m. 강의실 안에서 등록해 주세요." : "위도 -90~90, 경도 -180~180, 반경 50~1000m");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("강의실 위치 등록").setView(view).setPositiveButton("저장", null).setNegativeButton("취소", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(b -> {
            try {
                double a = Double.parseDouble(lat.getText().toString()), o = Double.parseDouble(lon.getText().toString());
                int r = Integer.parseInt(radius.getText().toString());
                if (!Double.isFinite(a) || !Double.isFinite(o) || Math.abs(a) > 90 || Math.abs(o) > 180 || r < 50 || r > 1000) throw new IllegalArgumentException();
                prefs.location(a, o, r); prefs.log("강의실 위치 등록"); dialog.dismiss(); render(1);
            } catch (IllegalArgumentException e) { toast("올바른 좌표와 반경을 입력해 주세요"); }
        }));
        dialog.show();
    }
    private void currentLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingLocation = true; permissions(); return;
        }
        locationManager = getSystemService(LocationManager.class);
        String provider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER
            : locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null;
        if (provider == null) { toast("휴대폰 위치 기능을 켜 주세요"); openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS); return; }
        if (oneLocation != null) locationManager.removeUpdates(oneLocation);
        toast("현재 위치를 확인하고 있습니다");
        oneLocation = new LocationListener() {
            @Override public void onLocationChanged(Location value) {
                locationManager.removeUpdates(this); oneLocation = null;
                if (!isFinishing()) editLocation(value);
            }
            @Override public void onStatusChanged(String p, int s, Bundle e) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };
        try {
            locationManager.requestSingleUpdate(provider, oneLocation, Looper.getMainLooper());
            handler.postDelayed(() -> {
                if (oneLocation != null) { locationManager.removeUpdates(oneLocation); oneLocation = null; toast("위치 확인이 지연됩니다. 창가에서 다시 시도하거나 좌표를 입력해 주세요"); }
            }, 25000);
        } catch (SecurityException e) { toast("정확한 위치 권한을 허용해 주세요"); }
    }

    private void editSchedule() {
        LinearLayout view = form();
        Set<String> chosen = new HashSet<>();
        String[] days = {"월", "화", "수", "목", "금", "토", "일"};
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < 7; i++) {
            final String day = String.valueOf(i + 1);
            CheckBox check = new CheckBox(this); check.setText(days[i]); check.setTextSize(13); check.setPadding(0,0,0,0);
            check.setButtonDrawable((android.graphics.drawable.Drawable)null); check.setGravity(Gravity.CENTER);
            check.setChecked(prefs.days().contains(i + 1));
            check.setBackground(shape(check.isChecked() ? 0xFFCDEDE4 : 0xFFF0F3F2, 8));
            if (check.isChecked()) chosen.add(day);
            check.setOnCheckedChangeListener((v, yes) -> { if (yes) chosen.add(day); else chosen.remove(day); check.setBackground(shape(yes ? 0xFFCDEDE4 : 0xFFF0F3F2, 8)); });
            row.addView(check, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        view.addView(row); gap(view, 12);
        EditText start = input("입실 시작 HH:mm", prefs.start(), false);
        EditText end = input("입실 종료 HH:mm", prefs.end(), false);
        view.addView(start); view.addView(end); note(view, "퇴실은 한국 시간 17:50입니다. 입실 종료는 17:50 이전으로 설정해 주세요.");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("수업 일정").setView(view).setPositiveButton("저장", null).setNegativeButton("취소", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(b -> {
            try {
                LocalTime from = LocalTime.parse(start.getText().toString().trim()), to = LocalTime.parse(end.getText().toString().trim());
                if (chosen.isEmpty() || !from.isBefore(to) || to.isAfter(LocalTime.of(17, 50))) throw new IllegalArgumentException();
                prefs.raw.edit().putStringSet("days", chosen).putString("start", from.toString()).putString("end", to.toString()).apply();
                AlarmScheduler.arm(this, false); dialog.dismiss(); render(1);
            } catch (RuntimeException e) { toast("요일을 고르고 HH:mm 형식의 올바른 입실 시간을 입력해 주세요"); }
        }));
        dialog.show();
    }
    private void editExcluded() {
        EditText edit = input("2026-09-25, 2026-09-28", String.join(", ", new TreeSet<>(prefs.excluded())), false); edit.setSingleLine(false); edit.setMinLines(3);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("휴강일 · YYYY-MM-DD").setView(edit).setPositiveButton("저장", null).setNegativeButton("취소", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(b -> {
            try {
                Set<String> values = new HashSet<>();
                for (String date : edit.getText().toString().split("[,\\s]+")) if (!date.isEmpty()) values.add(LocalDate.parse(date).toString());
                prefs.raw.edit().putStringSet("excluded", values).apply(); AlarmScheduler.arm(this, false); dialog.dismiss(); render(1);
            } catch (RuntimeException e) { edit.setError("날짜를 YYYY-MM-DD 형식으로 입력해 주세요"); }
        }));
        dialog.show();
    }

    private void connection() {
        LinearLayout auth = card("01  사이트 인증");
        boolean ready = new AuthVault(this).ready();
        auth.addView(text(ready ? "인증 정보가 저장되어 있습니다" : "출석 사이트를 연결해 주세요", 18, INK, true));
        note(auth, "Chrome의 인증은 자동으로 공유되지 않습니다. 앱 안에서 발급받은 인증 링크를 열어 인증한 뒤 저장해 주세요.");
        action(auth, ready ? "출석 사이트 · 인증 다시 확인" : "출석 사이트 인증하기", true,
            () -> startActivity(new Intent(this, AuthActivity.class)));
        if (ready) action(auth, "저장한 인증 삭제", false, () -> {
            prefs.raw.edit().putBoolean("auto", false).remove("record_day").remove("in").remove("out").apply();
            new AuthVault(this).clear();
            android.webkit.CookieManager.getInstance().removeAllCookies(null);
            android.webkit.CookieManager.getInstance().flush();
            toast("인증 정보를 삭제했습니다"); render(2);
        });
        LinearLayout permissions = card("02  휴대폰 권한");
        permissions.addView(text(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ? "위치 권한 허용됨" : "정확한 위치 권한이 필요합니다", 17, INK, true));
        note(permissions, getSystemService(NotificationManager.class).areNotificationsEnabled()
            ? "알림이 허용되어 있습니다." : "알림이 꺼져 있어 Wi-Fi 미연결과 출석 결과를 알릴 수 없습니다.");
        action(permissions, "위치 · 알림 권한 허용", false, this::permissions);
        action(permissions, "앱 권한 설정", false, () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        LinearLayout alarm = card("03  17:50 예약");
        alarm.addView(text(AlarmScheduler.allowed(this) ? "정확한 알람 사용 가능" : "정확한 알람 허용 필요", 18, INK, true));
        long next = prefs.raw.getLong("next_alarm", 0);
        note(alarm, next > 0 ? "다음 예약: " + Instant.ofEpochMilli(next).atZone(Policy.ZONE).format(DateTimeFormatter.ofPattern("M/d HH:mm")) : "감시 시작 후 수업일에 맞춰 예약합니다.");
        if (Build.VERSION.SDK_INT >= 31) action(alarm, "알람 및 리마인더 설정", false,
            () -> startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))));
        LinearLayout battery = card("04  안정적인 감시");
        note(battery, "배터리 설정에서 이 앱의 백그라운드 실행을 허용해 주세요. 재부팅 또는 앱 강제 중지 후에는 앱을 열어 감시를 다시 시작해야 합니다. 위치 감시가 켜져 있는 동안 상태 알림이 유지됩니다.");
        action(battery, "배터리 최적화 설정", false, () -> openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        LinearLayout pending = card("처리 기록 점검");
        note(pending, "요청 후 통신이 끊기면 중복 처리를 막기 위해 다시 요청하지 않습니다. 사이트에서 실제 기록을 확인한 뒤에만 아래 보류 상태를 해제해 주세요.");
        action(pending, "사이트에서 기록 보기", false, () -> startActivity(new Intent(this, AuthActivity.class)));
        if (!prefs.raw.getString("pending", "").isEmpty()) action(pending, "확인 후 보류 해제", false, () ->
            new AlertDialog.Builder(this).setTitle("사이트 기록을 확인하셨나요?")
                .setMessage("보류를 해제하면 설정한 시간대에 자동 처리를 다시 시도할 수 있습니다.")
                .setNegativeButton("취소", null).setPositiveButton("확인했고 해제", (d,w) -> {
                    prefs.raw.edit().remove("pending").apply(); prefs.log("사용자가 요청 보류 해제"); render(2);
                }).show());
        LinearLayout info = card("ABOUT");
        info.addView(text("출석 메이트 0.1.1 · 초기 테스트 버전", 14, INK, true));
        note(info, "인증과 위치는 이 기기에 저장합니다. 별도의 분석 서버로 전송하지 않습니다. 실제 계정과 지정 Wi-Fi에서의 동작 검증은 아직 필요합니다.");
    }
    private void permissions() {
        java.util.ArrayList<String> wanted = new java.util.ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            wanted.add(Manifest.permission.ACCESS_COARSE_LOCATION); wanted.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        if (wanted.isEmpty()) { toast("필요한 권한이 허용되어 있습니다"); return; }
        requestPermissions(wanted.toArray(new String[0]), 10);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != 10) return;
        boolean precise = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean start = pendingStart, locate = pendingLocation; pendingStart = pendingLocation = false;
        if (!precise) { toast("정확한 위치 권한이 있어야 Wi-Fi 이름을 확인할 수 있습니다"); render(selected); return; }
        if (start) toggleMonitor(); else if (locate) currentLocation(); else render(selected);
    }
}
