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
    private TextView status, detail, inTime, outTime, todayLog;
    private HomeTheme homeTheme;
    private LocalDate homeDay;
    private String homeCheckout, homeEntryEnd;
    private Clock homeClock = Clock.system(Policy.ZONE);
    private ScrollView homeScroll;
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
        if (selected != 0) render(selected);
        if (prefs.enabled()) ScheduleController.reconcile(this);
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
        if (selected == 0) {
            button.setStateListAnimator(null); button.setElevation(0);
            button.setTextColor(primary ? (homeTheme == HomeTheme.NIGHT ? 0xFF142338 : homeTheme.ink) : homeTheme.accent);
            button.setBackground(shape(primary ? homeTheme.button : homeTheme.soft, 14));
        } else {
            button.setTextColor(primary ? Color.WHITE : TEAL);
            button.setBackground(shape(primary ? TEAL : 0xFFE5F2EF, 14));
        }
        button.setMinHeight(dp(50)); button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setOnClickListener(v -> action.run());
        return button;
    }
    private LinearLayout card(String label) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        GradientDrawable background = shape(selected == 0 ? homeTheme.surface : Color.WHITE, 22);
        if (selected == 0) background.setStroke(dp(1), homeTheme.border);
        card.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(14);
        content.addView(card, params);
        if (!label.isEmpty()) { card.addView(text(label, 13, selected == 0 ? homeTheme.accent : TEAL, true)); gap(card, 10); }
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
        ZonedDateTime now = ZonedDateTime.now(homeClock).withZoneSameInstant(Policy.ZONE);
        homeDay = now.toLocalDate(); homeCheckout = prefs.checkout(); homeEntryEnd = prefs.end();
        homeTheme = HomeTheme.at(now, homeEntryEnd, homeCheckout);
        boolean night = tab == 0 && homeTheme == HomeTheme.NIGHT;
        int background = tab == 0 ? homeTheme.background : BG;
        root.setBackgroundColor(background);
        updateSystemBars(night, background);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        homeScroll = scroll;
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(22), dp(22), dp(18));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (tab == 0) {
            LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
            heading.addView(text("ATTENDANCE MATE", 11, homeTheme.accent, true), new LinearLayout.LayoutParams(0, -2, 1));
            TextView mode = text(night ? "NIGHT" : "DAYLIGHT", 10, homeTheme.accent, true);
            mode.setLetterSpacing(.12f); heading.addView(mode); content.addView(heading); gap(content, 8);
            content.addView(text("오늘 출결", 29, homeTheme.ink, true)); gap(content, 4);
            content.addView(text(now.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)), 13, homeTheme.muted, false));
            gap(content, 18); home();
        } else {
            content.addView(text("T  /  ATTENDANCE MATE", 12, TEAL, true)); gap(content, 10);
            content.addView(text(tab == 1 ? "나에게 맞게 설정" : "한 번 연결하고\n편하게 출석하세요.", 28, INK, true)); gap(content, 8);
            content.addView(text(tab == 1 ? "Wi-Fi · 장소 · 수업 일정" : "사이트 인증과 휴대폰 권한을 확인하세요.", 14, MUTED, false));
            gap(content, 24);
            if (tab == 1) settings(); else connection();
        }
        tabs = new LinearLayout(this); tabs.setPadding(dp(16), dp(10), dp(16), dp(10));
        tabs.setBackgroundColor(tab == 0 ? homeTheme.surface : Color.WHITE);
        String[] labels = {"오늘", "설정", "연결"};
        for (int i = 0; i < labels.length; i++) {
            final int target = i; Button item = button(labels[i], selected == i, () -> render(target));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1); p.setMargins(dp(4), 0, dp(4), 0);
            tabs.addView(item, p);
        }
        root.addView(tabs);
    }

    @SuppressWarnings("deprecation")
    private void updateSystemBars(boolean night, int background) {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(background);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(night ? 0 : light, light);
            }
        } else {
            int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(night ? decor.getSystemUiVisibility() & ~light : decor.getSystemUiVisibility() | light);
        }
    }

    private void home() {
        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(shape(homeTheme.soft, 22)); hero.setClipToOutline(true);
        ImageView illustration = new ImageView(this);
        illustration.setImageResource(homeTheme == HomeTheme.NIGHT ? R.drawable.hero_night : R.drawable.hero_warm);
        illustration.setScaleType(ImageView.ScaleType.MATRIX);
        final float verticalFocus = homeTheme == HomeTheme.WARM ? .82f : .5f;
        illustration.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            android.graphics.drawable.Drawable asset = illustration.getDrawable();
            float width = r-l, height = b-t;
            float scale = Math.max(width/asset.getIntrinsicWidth(), height/asset.getIntrinsicHeight());
            android.graphics.Matrix matrix = new android.graphics.Matrix(); matrix.setScale(scale, scale);
            matrix.postTranslate((width-asset.getIntrinsicWidth()*scale)/2, (height-asset.getIntrinsicHeight()*scale)*verticalFocus);
            illustration.setImageMatrix(matrix);
        });
        illustration.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hero.addView(illustration, new LinearLayout.LayoutParams(-1, dp(144)));
        TextView greeting = text(homeTheme == HomeTheme.NIGHT ? "차분하게 하루를 마무리해요." : "오늘도 좋은 배움이 기다려요.", 14, homeTheme.ink, true);
        greeting.setPadding(dp(16), dp(10), dp(16), dp(11)); hero.addView(greeting);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2); heroParams.bottomMargin = dp(16);
        content.addView(hero, heroParams);

        LinearLayout state = card("출결 현황");
        status = text("", 20, homeTheme.ink, true); detail = text("", 12, homeTheme.muted, false);
        state.addView(status); gap(state, 6); state.addView(detail); gap(state, 18);
        LinearLayout times = new LinearLayout(this);
        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text("입실", 12, homeTheme.muted, false)); gap(left, 3);
        inTime = text("—", 30, homeTheme.accent, true); inTime.setFontFeatureSettings("tnum"); left.addView(inTime);
        LinearLayout right = new LinearLayout(this); right.setOrientation(LinearLayout.VERTICAL); right.setPadding(dp(18), 0, 0, 0);
        right.addView(text("퇴실", 12, homeTheme.muted, false)); gap(right, 3);
        outTime = text("—", 30, homeTheme.accent, true); outTime.setFontFeatureSettings("tnum"); right.addView(outTime);
        times.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        View divider = new View(this); divider.setBackgroundColor(homeTheme.border);
        times.addView(divider, new LinearLayout.LayoutParams(dp(1), -1));
        times.addView(right, new LinearLayout.LayoutParams(0, -2, 1)); state.addView(times);
        gap(state, 10);
        state.addView(button("달력에서 출결 조회  ›", true, this::attendanceCalendar), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout log = card("오늘 로그");
        todayLog = text("", 13, homeTheme.muted, false); todayLog.setLineSpacing(dp(8), 1); log.addView(todayLog);
        updateHome();
    }

    private void attendanceCalendar() {
        LocalDate today = ZonedDateTime.now(homeClock).withZoneSameInstant(Policy.ZONE).toLocalDate();
        new DatePickerDialog(this, (view, y, m, d) -> showAttendance(LocalDate.of(y, m+1, d)),
            today.getYear(), today.getMonthValue()-1, today.getDayOfMonth()).show();
    }
    private void showAttendance(LocalDate day) {
        Policy.Record record = prefs.history(day);
        String body = record == null ? "앱에서 확인한 기록이 없습니다.\n이전 기록은 Chrome 출결 화면에서 확인해 주세요."
            : "입실  " + (record.in().isEmpty() ? "확인된 기록 없음" : record.in()) + "\n퇴실  " +
                (record.out().isEmpty() ? "확인된 기록 없음" : record.out());
        if (prefs.excluded().contains(day.toString())) body += "\n등록한 휴강일입니다.";
        new AlertDialog.Builder(this).setTitle(day + " 출결").setMessage(body)
            .setPositiveButton("닫기", null).setNeutralButton("다른 날짜", (d,w) -> attendanceCalendar())
            .setNegativeButton("Chrome 출결 조회", (d,w) -> ChromeAttendanceService.openChrome(this, SiteProtocol.PAGE)).show();
    }

    private void setIfChanged(TextView view, String value) { if (!view.getText().toString().equals(value)) view.setText(value); }

    private void updateHome() {
        if (status == null || selected != 0) return;
        ZonedDateTime now = ZonedDateTime.now(homeClock).withZoneSameInstant(Policy.ZONE);
        if (HomeTheme.at(now, prefs.end(), prefs.checkout()) != homeTheme || !now.toLocalDate().equals(homeDay) || !prefs.checkout().equals(homeCheckout) || !prefs.end().equals(homeEntryEnd)) {
            int position = homeScroll.getScrollY(); render(0); homeScroll.post(() -> homeScroll.scrollTo(0, position)); return;
        }
        Policy.Record record = prefs.history(now.toLocalDate());
        String entry = record == null ? "" : record.in(), exit = record == null ? "" : record.out();
        String title = !exit.isEmpty() ? "오늘 출결 완료" : !entry.isEmpty() ? "입실 완료"
            : MonitorService.running ? prefs.raw.getString("status", "입실 확인 중")
            : prefs.enabled() ? "출석 예약 대기" : "출석 예약 꺼짐";
        String description = prefs.enabled() ? "입실 " + prefs.start() + "–" + prefs.end() + " · 퇴실 " + prefs.checkout()
            : "설정에서 출석 예약을 켜 주세요.";
        if (MonitorService.running) description = prefs.raw.getString("detail", description);
        setIfChanged(status, title); setIfChanged(detail, description);
        setIfChanged(inTime, entry.isEmpty() ? "—" : entry); setIfChanged(outTime, exit.isEmpty() ? "—" : exit);
        String log = prefs.raw.getString("log:" + now.toLocalDate(), "");
        setIfChanged(todayLog, log.isEmpty() ? "아직 오늘 기록이 없습니다." : log);
    }

    private void toggleMonitor() {
        if (prefs.enabled()) {
            prefs.enabled(false); ScheduleController.reconcile(this);
            Notices.wifi(this, false, ""); prefs.log("출석 예약 꺼짐"); render(1); return;
        }
        if (prefs.ssids().isEmpty() || prefs.days().isEmpty()) { toast("Wi-Fi와 수업 요일을 먼저 등록해 주세요"); return; }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingStart = true; permissions(); return;
        }
        if (!ScheduleController.backgroundLocationAllowed(this) || !AlarmScheduler.allowed(this) || !ChromeAttendanceService.available()) {
            toast("연결 탭에서 Chrome 접근성, 위치 항상 허용, 정확한 알람을 설정해 주세요"); render(2); return;
        }
        LocationManager lm = getSystemService(LocationManager.class);
        if (Build.VERSION.SDK_INT >= 28 && !lm.isLocationEnabled()) { toast("휴대폰 위치 기능을 켜 주세요"); openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS); return; }
        prefs.enabled(true); prefs.log("출석 예약 켜짐");
        ScheduleController.reconcile(this); render(1);
    }

    private void settings() {
        LinearLayout lab = card("임시 메뉴");
        note(lab,"화면 깨우기 · Extend Unlock · 신뢰 방식 · 시스템 인증 · 서버 연결을 직접 시험하고 결과를 기록합니다.");
        action(lab,"잠금 상태 테스트 열기",true,() -> startActivity(new Intent(this,LockLabActivity.class)));
        LinearLayout schedule = card("출석 시간");
        schedule.addView(text("입실 " + prefs.start() + "–" + prefs.end() + "\n퇴실 " + prefs.checkout(), 21, INK, true));
        note(schedule, "입실 완료 또는 입실 종료 시 감시를 끕니다. 퇴실은 예약 시각에 다시 확인하고 완료하면 종료합니다.");
        note(schedule, "퇴실 재시도: 최대 " + prefs.retryMinutes() + "분 · 한국 시간");
        note(schedule, "오늘 화면: 따뜻한 일러스트 → " + HomeTheme.nightStarts(prefs.end(), prefs.checkout()) + "부터 야간 테마. 퇴실 1시간 전에 전환하며 입실 시간과 겹치면 입실 종료 후 전환합니다. 자정에 밝은 테마로 돌아옵니다.");
        action(schedule, "요일 · 입실 · 퇴실 시간 변경", false, this::editSchedule);
        action(schedule, "달력에서 휴강일 등록 (" + prefs.excluded().size() + "일)", false, this::editExcluded);
        LinearLayout automation = card("출석 예약");
        action(automation, prefs.enabled() ? "출석 예약 끄기" : "출석 예약 켜기", true, this::toggleMonitor);
        Switch auto = new Switch(this); auto.setText(R.string.automatic_chrome_click); auto.setTextSize(16); auto.setTextColor(INK); auto.setMinHeight(dp(48));
        auto.setChecked(prefs.auto()); automation.addView(auto);
        note(automation, "기존 Chrome 인증을 사용합니다. 화면 잠금 중에는 출석 알림을 눌러 잠금을 해제해 주세요. 자동 클릭을 끄면 조회만 합니다.");
        auto.setOnCheckedChangeListener((v, checked) -> {
            if (checked && !ChromeAttendanceService.available()) { auto.setChecked(false); toast("연결 탭에서 Chrome 접근성을 먼저 켜 주세요"); return; }
            prefs.raw.edit().putBoolean("auto", checked).apply(); prefs.log(checked ? "Chrome 자동 클릭 켜짐" : "Chrome 조회 모드");
            ScheduleController.reconcile(this);
        });
        action(automation, "Chrome에서 지금 확인", false, () -> startActivity(new Intent(this, ChromeLaunchActivity.class)));
        LinearLayout notifications = card("알림");
        notifications.addView(text("입실 · 퇴실 완료 알림 항상 표시",17,INK,true));
        note(notifications,"완료를 확인할 때마다 입실과 퇴실 알림을 따로 남깁니다. 앱을 열거나 감시가 끝나도 유지되며 직접 지울 수 있습니다.");
        note(notifications,Notices.attendanceNotificationsAllowed(this) ? "휴대폰에서 완료 알림을 허용하고 있습니다."
            : "휴대폰에서 알림이 차단되어 있습니다. 앱 알림과 ‘입실·퇴실 결과’ 알림을 허용해 주세요.");
        action(notifications,"휴대폰 알림 설정",false,() -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())));
        LinearLayout wifi = card("출석에 사용할 Wi-Fi");
        note(wifi, "등록한 Wi-Fi에서 Chrome 출석을 처리합니다. 이름은 대소문자를 구분합니다.");
        for (String ssid : prefs.ssids()) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(ssid, 15, INK, true); name.setMaxLines(2);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(button("삭제", false, () -> { Set<String> values = prefs.ssids(); values.remove(ssid); prefs.ssids(values); render(1); }),
                new LinearLayout.LayoutParams(dp(72), dp(48))); wifi.addView(row);
        }
        action(wifi, "+ Wi-Fi 추가", true, this::addWifi);
        action(wifi, "현재 Wi-Fi 등록", false, () -> {
            String current = NetworkAccess.inspect(this).ssid();
            if (!Policy.validSsid(current)) { toast("Wi-Fi 이름을 확인할 수 없습니다. 직접 입력해 주세요"); return; }
            Set<String> values = prefs.ssids(); values.add(current); prefs.ssids(values); render(1);
        });
        LinearLayout location = card("강의실 위치");
        location.addView(text(prefs.hasLocation() ? "반경 " + prefs.radius() + "m" : "기본 반경 30m · 위치 미등록", 19, INK, true));
        note(location, "입실 시간에 주변인데 Wi-Fi가 연결되지 않으면 알려드립니다. 위치 정확도가 반경보다 나쁘면 도착으로 판단하지 않습니다.");
        action(location, "현재 위치로 등록", true, this::currentLocation);
        action(location, "좌표와 반경 변경", false, () -> editLocation(null));
        if (prefs.hasLocation()) action(location, "강의실 위치 삭제", false, () -> {
            prefs.raw.edit().remove("latitude").remove("longitude").apply(); Notices.wifi(this, false, ""); render(1);
        });
        LinearLayout help = card("Wi-Fi 자동 연결");
        note(help, "갤럭시의 인텔리전트 Wi-Fi에서 자동 켜기/끄기를 설정할 수 있습니다. 출석에 쓸 네트워크는 휴대폰에도 저장해 주세요.");
        action(help, "인텔리전트 Wi-Fi 설정", false, () -> ChromeAttendanceService.intelligentWifi(this));
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
        note(view, current != null ? "현재 위치 정확도: 약 " + Math.round(current.getAccuracy()) + "m. 강의실 안에서 등록해 주세요." : "위도 -90~90, 경도 -180~180, 반경 10~1000m");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("강의실 위치 등록").setView(view).setPositiveButton("저장", null).setNegativeButton("취소", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(b -> {
            try {
                double a = Double.parseDouble(lat.getText().toString()), o = Double.parseDouble(lon.getText().toString());
                int r = Integer.parseInt(radius.getText().toString());
                if (!Double.isFinite(a) || !Double.isFinite(o) || Math.abs(a) > 90 || Math.abs(o) > 180 || r < 10 || r > 1000) throw new IllegalArgumentException();
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
        LinearLayout view = form(); Set<String> chosen = new HashSet<>();
        String[] days = {"월","화","수","목","금","토","일"};
        LinearLayout row = new LinearLayout(this);
        for (int i=0; i<7; i++) {
            final String day = String.valueOf(i+1);
            CheckBox check = new CheckBox(this); check.setText(days[i]); check.setTextSize(13);
            check.setButtonDrawable((android.graphics.drawable.Drawable)null); check.setGravity(Gravity.CENTER);
            check.setChecked(prefs.days().contains(i+1)); if (check.isChecked()) chosen.add(day);
            check.setBackground(shape(check.isChecked() ? 0xFFCDEDE4 : 0xFFF0F3F2, 8));
            check.setOnCheckedChangeListener((v, yes) -> { if (yes) chosen.add(day); else chosen.remove(day); check.setBackground(shape(yes ? 0xFFCDEDE4 : 0xFFF0F3F2, 8)); });
            row.addView(check,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        view.addView(row);
        String[] times = {prefs.start(),prefs.end(),prefs.checkout()};
        String[] labels = {"입실 시작","입실 종료","퇴실 시작"};
        for (int i=0; i<3; i++) {
            final int index = i;
            Button time = button(labels[i] + "  " + times[i], false, () -> {});
            time.setOnClickListener(v -> {
                LocalTime initial = LocalTime.parse(times[index]);
                new TimePickerDialog(this, (picker,h,m) -> {
                    times[index] = String.format(Locale.ROOT,"%02d:%02d",h,m);
                    time.setText(getString(R.string.time_picker_label, labels[index], times[index]));
                }, initial.getHour(), initial.getMinute(), true).show();
            });
            gap(view,10); view.addView(time,new LinearLayout.LayoutParams(-1,dp(50)));
        }
        note(view,"퇴실 실패 시 재시도할 시간 (1–60분)");
        EditText retry = input("재시도 분",String.valueOf(prefs.retryMinutes()),true); view.addView(retry);
        ScrollView scroll = new ScrollView(this); scroll.addView(view);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("수업 일정 · 한국 시간").setView(scroll)
            .setPositiveButton("저장",null).setNegativeButton("취소",null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(b -> {
            try {
                int minutes = Integer.parseInt(retry.getText().toString());
                if (chosen.isEmpty() || !Policy.validSchedule(times[0],times[1],times[2],minutes)) throw new IllegalArgumentException();
                prefs.raw.edit().putStringSet("days",chosen).putString("start",times[0]).putString("end",times[1])
                    .putString("checkout",times[2]).putInt("checkout_retry_minutes",minutes).apply();
                prefs.log("출석 시간 변경"); ScheduleController.reconcile(this); dialog.dismiss(); render(1);
            } catch (RuntimeException e) { toast("입실 시작 < 종료 ≤ 퇴실, 재시도 1–60분으로 자정 이전까지 설정해 주세요"); }
        }));
        dialog.show();
    }
    private void editExcluded() {
        Set<String> values = new TreeSet<>(prefs.excluded());
        LinearLayout view = form(), dates = new LinearLayout(this); dates.setOrientation(LinearLayout.VERTICAL);
        note(view,"달력에서 날짜를 골라 추가하고 저장해 주세요.");
        action(view,"달력에서 날짜 추가",true,() -> {
            LocalDate today = LocalDate.now(Policy.ZONE);
            new DatePickerDialog(this,(picker,y,m,d) -> { values.add(LocalDate.of(y,m+1,d).toString()); excludedRows(dates,values); },
                today.getYear(),today.getMonthValue()-1,today.getDayOfMonth()).show();
        });
        gap(view,12); view.addView(dates); excludedRows(dates,values);
        ScrollView scroll = new ScrollView(this); scroll.addView(view);
        new AlertDialog.Builder(this).setTitle("휴강일").setView(scroll).setNegativeButton("취소",null)
            .setPositiveButton("저장",(d,w) -> {
                prefs.raw.edit().putStringSet("excluded",new HashSet<>(values)).apply();
                prefs.log("휴강일 변경"); ScheduleController.reconcile(this); render(1);
            }).show();
    }
    private void excludedRows(LinearLayout list, Set<String> values) {
        list.removeAllViews();
        if (values.isEmpty()) { list.addView(text("등록한 휴강일이 없습니다.",14,MUTED,false)); return; }
        for (String date : new TreeSet<>(values)) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(text(date,16,INK,false),new LinearLayout.LayoutParams(0,-2,1));
            row.addView(button("삭제",false,() -> { values.remove(date); excludedRows(list,values); }),
                new LinearLayout.LayoutParams(dp(72),dp(48))); list.addView(row);
        }
    }

    private void connection() {
        LinearLayout chrome = card("01  기존 Chrome 연결");
        chrome.addView(text(ChromeAttendanceService.available() ? "Chrome 접근성 연결됨" : "Chrome 접근성을 켜 주세요",18,INK,true));
        note(chrome,"휴대폰 Chrome에 저장된 인증을 그대로 사용합니다. 지정한 출석 화면의 버튼과 기록 시각을 확인합니다.");
        note(chrome,"인텔리전트 Wi-Fi 버튼을 누를 때에는 휴대폰 설정 메뉴 이동에도 접근성을 사용합니다.");
        action(chrome,"Chrome 출결 화면 열기",true,() -> ChromeAttendanceService.openChrome(this,SiteProtocol.PAGE));
        action(chrome,"Chrome 접근성 설정",false,() -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        action(chrome,"Chrome 인증 페이지 열기",false,() -> ChromeAttendanceService.openChrome(this,SiteProtocol.AUTH_PAGE));
        note(chrome,"이미 출결 화면이 열리면 다시 인증하지 않아도 됩니다. 화면이 잠겨 있으면 출석 알림을 눌러 잠금을 해제해 주세요.");
        LinearLayout permissions = card("02  위치와 알림");
        permissions.addView(text(ScheduleController.backgroundLocationAllowed(this) ? "위치 항상 허용됨" : "예약 실행에 위치 항상 허용 필요",17,INK,true));
        note(permissions,"예약 시간에 Wi-Fi 이름을 확인하고 입실 시간에만 강의실 위치를 확인합니다.");
        action(permissions,"위치 · 알림 권한 허용",false,this::permissions);
        action(permissions,"위치 항상 허용 설정",false,() -> {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { permissions(); return; }
            if (Build.VERSION.SDK_INT == 29) requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},11);
            else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
        });
        note(permissions,getSystemService(NotificationManager.class).areNotificationsEnabled() ? "알림 권한이 허용되어 있습니다." : "출석 알림을 받으려면 앱 알림을 허용해 주세요.");
        LinearLayout alarm = card("03  예약 실행");
        alarm.addView(text(AlarmScheduler.allowed(this) ? "정확한 알람 사용 가능" : "정확한 알람 허용 필요",18,INK,true));
        long next = prefs.raw.getLong("next_alarm",0);
        note(alarm,next > 0 ? "다음 예약: "+Instant.ofEpochMilli(next).atZone(Policy.ZONE).format(DateTimeFormatter.ofPattern("M/d HH:mm"))
            : "설정에서 출석 예약을 켜면 수업일에 맞춰 예약합니다.");
        if (Build.VERSION.SDK_INT >= 31) action(alarm,"알람 및 리마인더 설정",false,
            () -> startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()))));
        action(alarm,"배터리 최적화 설정",false,() -> openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        note(alarm,"입실·퇴실 시간 밖에서는 감시를 종료합니다. 재부팅 후 예약을 복구하며 앱 강제 중지 후에는 앱을 다시 열어 주세요.");
        if (!prefs.raw.getString("chrome_pending","").isEmpty()) {
            LinearLayout pending = card("클릭 결과 확인");
            note(pending,"중복 클릭을 막기 위해 이전 요청을 보류했습니다. Chrome에서 실제 기록을 먼저 확인해 주세요.");
            action(pending,"Chrome에서 기록 확인",false,() -> ChromeAttendanceService.openChrome(this,SiteProtocol.PAGE));
            action(pending,"확인 후 보류 해제",false,() -> new AlertDialog.Builder(this).setTitle("Chrome에서 기록을 확인했나요?")
                .setMessage("보류를 해제하면 설정한 시간 안에서 다시 클릭할 수 있습니다.").setNegativeButton("취소",null)
                .setPositiveButton("확인했고 해제",(d,w) -> { prefs.raw.edit().remove("chrome_pending").apply(); render(2); }).show());
        }
        LinearLayout info = card("출석 메이트 0.4.1");
        note(info,"출결 달력에는 앱이 Chrome에서 확인한 기록을 표시합니다. 이전 전체 기록은 Chrome 출결 화면에서 확인하세요.");
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
