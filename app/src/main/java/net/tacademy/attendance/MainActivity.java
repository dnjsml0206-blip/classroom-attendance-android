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
    private SetupStatusView setupStatus;
    private TextView status, detail, inTime, outTime, todayLog, todayWifi, todayWifiHint, todayWifiIcon;
    private LinearLayout todayWifiPanel;
    private HomeTheme homeTheme;
    private ReservationLampView reservationLamp;
    private TextView reservationLampState;
    private Button enterNow, leaveNow;
    private LocalDate homeDay;
    private String homeCheckout, homeEntryEnd;
    private Clock homeClock = Clock.system(Policy.ZONE);
    private ScrollView homeScroll;
    private LocationManager locationManager;
    private LocationListener oneLocation;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { if (selected == 0) updateHome(); else if(setupStatus!=null)setupStatus.refresh(); handler.postDelayed(this, 2000); }
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
        render(Math.max(0,Math.min(2,getIntent().getIntExtra("open_tab",0))));
    }
    @Override protected void onResume() {
        super.onResume(); ChromeAttendanceService.cancelSettingsMenu();
        handler.removeCallbacks(refresh); handler.post(refresh);
        if (selected != 0) render(selected);
        if (prefs.enabled()) ScheduleController.reconcile(this);
    }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() {
        ActivationRun.cancel(this);
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
        selected = tab; setupStatus=null; root.removeAllViews();
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
        if(tab==1&&"automation".equals(getIntent().getStringExtra("focus_setting"))){
            View focus=content.findViewWithTag("automation");if(focus!=null)scroll.post(()->{if(homeScroll==scroll){getIntent().removeExtra("focus_setting");scroll.smoothScrollTo(0,Math.max(0,focus.getTop()-dp(16)));}});
        }
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
        LinearLayout caption=new LinearLayout(this);caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.setPadding(dp(16),dp(10),dp(10),dp(10));
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);words.addView(greeting);gap(words,5);
        reservationLampState=text("",12,homeTheme.muted,true);reservationLampState.setTag("reservation-lamp-state");
        reservationLampState.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);words.addView(reservationLampState);
        caption.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        reservationLamp=new ReservationLampView(this,homeTheme);reservationLamp.setTag("reservation-lamp");
        LinearLayout.LayoutParams lampParams=new LinearLayout.LayoutParams(dp(74),dp(68));lampParams.leftMargin=dp(8);
        caption.addView(reservationLamp,lampParams);hero.addView(caption);
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
        gap(state, 16);
        LinearLayout quick=new LinearLayout(this);
        enterNow=attendanceButton("☀  입실하기",Policy.Phase.ENTRY);enterNow.setTag("attendance-enter-now");
        leaveNow=attendanceButton("☾  퇴실하기",Policy.Phase.CHECKOUT);leaveNow.setTag("attendance-leave-now");
        LinearLayout.LayoutParams enterParams=new LinearLayout.LayoutParams(0,dp(60),1);enterParams.rightMargin=dp(8);
        quick.addView(enterNow,enterParams);quick.addView(leaveNow,new LinearLayout.LayoutParams(0,dp(60),1));state.addView(quick);
        TextView quickHint=text("버튼을 누르면 Chrome에서 한 번 처리해요.",11,homeTheme.muted,false);quickHint.setPadding(0,dp(7),0,0);state.addView(quickHint);
        gap(state, 10);
        state.addView(button("달력에서 출결 조회  ›", true, this::attendanceCalendar), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout log = card("오늘 출석 기록");
        todayLog = text("", 13, homeTheme.muted, false); todayLog.setLineSpacing(dp(8), 1); log.addView(todayLog);
        todayWifiPanel=card("");todayWifiPanel.setTag("today-wifi-panel");
        todayWifiPanel.setOrientation(LinearLayout.HORIZONTAL);todayWifiPanel.setGravity(Gravity.CENTER_VERTICAL);
        todayWifiPanel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        todayWifiIcon=text("!",24,homeTheme.ink,true);todayWifiIcon.setGravity(Gravity.CENTER);
        todayWifiIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(44),dp(44));iconParams.rightMargin=dp(14);
        todayWifiPanel.addView(todayWifiIcon,iconParams);
        LinearLayout wifiLabels=new LinearLayout(this);wifiLabels.setOrientation(LinearLayout.VERTICAL);
        todayWifi=text("",20,homeTheme.ink,true);todayWifi.setTag("today-wifi");wifiLabels.addView(todayWifi);
        todayWifiHint=text("",13,homeTheme.muted,false);gap(wifiLabels,4);wifiLabels.addView(todayWifiHint);
        todayWifiPanel.addView(wifiLabels,new LinearLayout.LayoutParams(0,-2,1));
        updateHome();
    }

    private Button attendanceButton(String label,Policy.Phase phase){
        Button button=button(label,false,()->manualAttendance(phase));boolean night=homeTheme==HomeTheme.NIGHT;
        button.setTextSize(16);button.setTypeface(null,Typeface.BOLD);button.setBackground(shape(phase==Policy.Phase.ENTRY?(night?0xFF624B35:0xFFFFDEB8):(night?0xFF354665:0xFFE2E6FB),22));
        button.setTextColor(phase==Policy.Phase.ENTRY?(night?0xFFFFDEA3:0xFF794C24):(night?0xFFDAE2FF:0xFF515D91));return button;
    }
    private void manualAttendance(Policy.Phase phase){
        if(ChromeAttendanceService.busy()){toast("출결 결과를 확인하고 있어요. 잠시 기다려 주세요.");return;}
        String key=LocalDate.now(Policy.ZONE)+":"+phase;
        if(key.equals(prefs.raw.getString("chrome_pending",""))){
            String name=phase==Policy.Phase.ENTRY?"입실":"퇴실";
            new AlertDialog.Builder(this).setTitle("이전 "+name+" 요청을 확인해요")
                .setMessage("이전에 버튼을 눌렀지만 결과가 확인되지 않았어요. 먼저 Chrome 기록을 확인해 주세요. 실제 미처리를 확인했다면 한 번 다시 실행할 수 있어요.")
                .setPositiveButton("기록 먼저 확인",(d,w)->ChromeAttendanceService.beginManual(this,phase,true,false))
                .setNeutralButton("미처리 확인 · 재시도",(d,w)->ChromeAttendanceService.beginManual(this,phase,false,true))
                .setNegativeButton("취소",null).show();return;
        }
        ChromeAttendanceService.beginManual(this,phase,false,false);
    }
    private void attendanceCalendar() {
        LocalDate today = ZonedDateTime.now(homeClock).withZoneSameInstant(Policy.ZONE).toLocalDate();
        startActivity(new Intent(this,AttendanceCalendarActivity.class)
            .putExtra("initial_date",today.toString()).putExtra("theme",homeTheme.name()));
    }

    private void setIfChanged(TextView view, String value) { if (!view.getText().toString().equals(value)) view.setText(value); }

    private void updateHome() {
        if (status == null || selected != 0) return;
        ZonedDateTime now = ZonedDateTime.now(homeClock).withZoneSameInstant(Policy.ZONE);
        if (HomeTheme.at(now, prefs.end(), prefs.checkout()) != homeTheme || !now.toLocalDate().equals(homeDay) || !prefs.checkout().equals(homeCheckout) || !prefs.end().equals(homeEntryEnd)) {
            int position = homeScroll.getScrollY(); render(0); homeScroll.post(() -> homeScroll.scrollTo(0, position)); return;
        }
        boolean reservationEnabled=prefs.enabled();
        reservationLamp.setLit(reservationEnabled);
        setIfChanged(reservationLampState,reservationEnabled?"출석 예약 켜짐":"출석 예약 꺼짐");
        reservationLampState.setTextColor(reservationEnabled?(homeTheme==HomeTheme.NIGHT?0xFFF5D294:0xFF875024):homeTheme.muted);
        Policy.Record record = prefs.history(now.toLocalDate());
        String entry = record == null ? "" : record.in(), exit = record == null ? "" : record.out();
        String title = !exit.isEmpty() ? "오늘 출결 완료" : !entry.isEmpty() ? "입실 완료"
            : MonitorService.running ? prefs.raw.getString("status", "입실 확인 중")
            : prefs.enabled() ? "출석 예약 대기" : "출석 예약 꺼짐";
        String description = prefs.enabled() ? "입실 " + prefs.start() + "–" + prefs.end() + " · 퇴실 " + Policy.checkoutStartTime(prefs.checkout())
            : "설정에서 출석 예약을 켜 주세요.";
        if (MonitorService.running) description = prefs.raw.getString("detail", description);
        boolean busy=ChromeAttendanceService.busy()||ActivationRun.pending();
        if(ActivationRun.pending()){title=ActivationRun.label();description="완료 기록을 먼저 확인하고, 미처리이면 한 번 눌러요.";}
        setIfChanged(enterNow,!entry.isEmpty()?"✓  입실 완료":"☀  입실하기");enterNow.setEnabled(entry.isEmpty()&&!busy);enterNow.setAlpha(enterNow.isEnabled()?1f:.55f);
        setIfChanged(leaveNow,!exit.isEmpty()?"✓  퇴실 완료":"☾  퇴실하기");leaveNow.setEnabled(exit.isEmpty()&&!busy);leaveNow.setAlpha(leaveNow.isEnabled()?1f:.55f);
        setIfChanged(status, title); setIfChanged(detail, description);
        setIfChanged(inTime, entry.isEmpty() ? "—" : entry); setIfChanged(outTime, exit.isEmpty() ? "—" : exit);
        // Show confirmed attendance times; operational diagnostics stay in their existing log.
        String log = exit.isEmpty() ? "" : exit + "  퇴실 완료";
        if (!entry.isEmpty()) log += (log.isEmpty() ? "" : "\n") + entry + "  입실 완료";
        setIfChanged(todayLog, log.isEmpty() ? "아직 확인된 출석 기록이 없습니다." : log);
        NetworkAccess.State wifi=NetworkAccess.inspect(this);
        boolean matched=!wifi.ssid().isEmpty()&&prefs.ssids().contains(wifi.ssid());
        boolean night=homeTheme==HomeTheme.NIGHT;
        int wifiInk=matched?(night?0xFFA4E6CB:0xFF146448):(night?0xFFFFD6B5:0xFF913810);
        int wifiBackground=matched?(night?0xFF193A37:0xFFE5F3EC):(night?0xFF432D24:0xFFFFE7D2);
        int wifiBorder=matched?(night?0xFF3D7468:0xFF9ACCB8):(night?0xFFE89756:0xFFD97A39);
        setIfChanged(todayWifi,matched?"대기 중":"와이파이 미연결");
        setIfChanged(todayWifiHint,matched?"대상 Wi-Fi 연결됨":"등록한 강의실 Wi-Fi를 연결해 주세요");
        setIfChanged(todayWifiIcon,matched?"✓":"!");
        todayWifi.setTextColor(wifiInk);todayWifiHint.setTextColor(wifiInk);todayWifiIcon.setTextColor(wifiInk);
        todayWifiIcon.setBackground(shape(matched?(night?0xFF28564B:0xFFC7E6D9):(night?0xFF66402B:0xFFF9C49A),22));
        GradientDrawable wifiShape=shape(wifiBackground,20);wifiShape.setStroke(dp(matched?1:2),wifiBorder);todayWifiPanel.setBackground(wifiShape);
        todayWifiPanel.setContentDescription("대상 와이파이 인식 상태 · "+todayWifi.getText()+" · "+todayWifiHint.getText());
    }

    private void setAttendanceReservation(boolean enabled) {
        pendingStart = false;
        if (!enabled && !prefs.enabled()) { render(1); return; }
        if(enabled&&ChromeAttendanceService.busy()){toast("출결 결과를 확인 중이에요. 잠시 기다려 주세요.");return;}
        if (!enabled) {
            ActivationRun.cancel();prefs.enabled(false); ScheduleController.reconcile(this);
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
        prefs.enabled(true);prefs.raw.edit().putBoolean("auto",true).apply();prefs.log("출석 예약 켜짐");
        boolean immediate=ActivationRun.start(this);ScheduleController.reconcile(this);render(immediate?0:1);
    }

    private void reservationSettings() {
        LinearLayout automation = card("출석 예약");automation.setTag("automation");
        LinearLayout choices = new LinearLayout(this);
        for (boolean enabled : new boolean[]{true,false}) {
            boolean active = prefs.enabled() == enabled;
            String label = enabled ? "켜기" : "끄기";
            Button choice = button(label,active,() -> setAttendanceReservation(enabled));
            choice.setTag(enabled ? "reservation-on" : "reservation-off");
            choice.setSelected(active);
            choice.setContentDescription("출석 예약 " + label + (active ? " · 선택됨" : ""));
            choice.setStateListAnimator(null); choice.setElevation(0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,-2,1);
            if (!enabled) params.leftMargin = dp(8);
            choices.addView(choice,params);
        }
        automation.addView(choices);
        note(automation,ActivationRun.pending()?ActivationRun.label():prefs.enabled() ? "예약 켜짐 · 출석 시간에 자동 시작" : "예약 꺼짐");
        note(automation,"켜기를 누르면 자동 클릭도 켜지고, 지금 가능한 미처리 출결을 5초 뒤 한 번 실행해요. 이전 요청이 있어도 실제 완료 기록을 먼저 확인해요. 예약이 이미 켜져 있어도 다시 누를 수 있어요.");
        String checkoutEnd = LocalTime.parse(prefs.checkout()).plusMinutes(prefs.retryMinutes()).format(DateTimeFormatter.ofPattern("HH:mm"));
        note(automation,"자동 감시는 수업일의 아래 시간에만 작동합니다.\n입실 " + prefs.start() + "–" + prefs.end()
            + " · 퇴실 " + Policy.checkoutStartTime(prefs.checkout()) + "–" + checkoutEnd + "\n출석 완료 또는 시간 종료 시 GPS 감시도 멈춥니다.");
        Switch auto = new Switch(this); auto.setText(R.string.automatic_chrome_click); auto.setTextSize(16); auto.setTextColor(INK); auto.setMinHeight(dp(48));
        auto.setChecked(prefs.auto()); automation.addView(auto);
        note(automation, "기존 Chrome 인증을 사용합니다. 화면이 꺼지거나 잠긴 경우 아래 ‘잠금 중 처리 방식’을 사용합니다. 자동 클릭을 끄면 조회만 합니다.");
        auto.setOnCheckedChangeListener((v, checked) -> {
            if (checked && !ChromeAttendanceService.available()) { auto.setChecked(false); toast("연결 탭에서 Chrome 접근성을 먼저 켜 주세요"); return; }
            if(!checked)ActivationRun.cancel();
            prefs.raw.edit().putBoolean("auto", checked).apply(); prefs.log(checked ? "Chrome 자동 클릭 켜짐" : "Chrome 조회 모드");
            ScheduleController.reconcile(this);
        });
        action(automation, "Chrome에서 지금 확인", false, () -> startActivity(new Intent(this, ChromeLaunchActivity.class)));
    }

    private void unlockSettings(){
        LinearLayout box=card("잠금 중 처리 방식");box.setTag("scheduled-unlock-settings");
        note(box,"일반 입실·퇴실 예약에 적용됩니다. 임시 테스트 메뉴를 숨겨도 유지됩니다.");
        RadioGroup modes=new RadioGroup(this);UnlockPolicy.Mode selected=ScheduledUnlock.mode(this);
        for(UnlockPolicy.Mode mode:UnlockPolicy.Mode.values()){
            RadioButton option=new RadioButton(this);option.setId(View.generateViewId());option.setTag(mode.name());option.setText(mode.label);option.setTextColor(INK);option.setTextSize(15);option.setMinHeight(dp(48));modes.addView(option);
            if(mode==selected)modes.check(option.getId());
        }
        box.addView(modes);
        TextView state=text("선택됨 · "+selected.label,14,TEAL,true);box.addView(state);
        modes.setOnCheckedChangeListener((group,id)->{
            RadioButton chosen=group.findViewById(id);if(chosen==null)return;
            prefs.raw.edit().putString("scheduled_lock_mode",chosen.getTag().toString()).apply();
            ScheduledUnlock.cancel(this,"MODE_CHANGED");state.setText(getString(R.string.unlock_selected,ScheduledUnlock.mode(this).label));
        });
        note(box,"출석 예약·자동 클릭·등록 Wi-Fi 조건이 맞을 때 입실/퇴실마다 하루 한 번 화면을 깨웁니다. 최대 2분 안에 종료하며, PIN·지문이 필요하면 직접 인증해야 합니다. 휴대폰이 화면 표시를 막으면 알림으로 안내합니다.");
        action(box,"Extend Unlock 설정 열기",false,()->PhoneSettings.open(this,PhoneSettings.Target.EXTEND));
        action(box,"앱 배터리 설정 열기",false,()->PhoneSettings.open(this,PhoneSettings.Target.BATTERY));
        note(box,"기본 방식은 Extend Unlock · 신뢰 상태입니다. 휴대폰 신뢰 설정을 완료해 주세요. PIN·지문 재인증이 필요하면 자동 진행이 중단되고 결과를 알립니다.");
    }

    private void creatorPanel(){
        if(CreatorTools.enabled()){
            LinearLayout diagnostics=card("진단 · 제작자용");diagnostics.setTag("creator-diagnostics");
            note(diagnostics,"일반 출결 동작과 잠금 테스트 기록을 확인하고 직접 업로드합니다.");
            action(diagnostics,"로그 확인 · GitHub 업로드",true,()->startActivity(new Intent(this,LogUploadActivity.class)));
            if(CreatorTools.testsVisible(this)){
                action(diagnostics,"잠금 상태 테스트 열기",false,()->startActivity(new Intent(this,LockLabActivity.class)));
                action(diagnostics,"테스트 완료 · 임시 메뉴 숨기기",false,()->{CreatorTools.completeTests(this);render(1);});
            } else {
                note(diagnostics,"테스트 메뉴 숨김 · 제작자 로그 업로드는 계속 사용할 수 있습니다.");
                action(diagnostics,"임시 테스트 메뉴 다시 켜기",false,()->{CreatorTools.enableTests(this);render(1);});
            }
        }
    }

    private void settings() {
        reservationSettings();
        unlockSettings();
        LinearLayout checks=card("설정 확인");setupStatus=new SetupStatusView(this,false);checks.addView(setupStatus);
        LinearLayout schedule = card("출석 시간");
        note(schedule, "변경할 항목을 선택하세요. 모든 시간은 한국 시간입니다.");
        action(schedule, "입실 시간  ›\n" + prefs.start() + "–" + prefs.end(), false, () -> editSchedule(ScheduleSection.ENTRY));
        action(schedule, "퇴실 시간  ›\n" + prefs.checkout() + " · 최대 " + prefs.retryMinutes() + "분 재시도", false, () -> editSchedule(ScheduleSection.CHECKOUT));
        note(schedule, getString(R.string.checkout_delay_notice,Policy.CHECKOUT_DELAY_SECONDS));
        note(schedule,"자동 접속은 입실·퇴실 각 최대 3회입니다. 오류나 결과 확인 시간 초과 시 자동 재접속을 멈춥니다. 모든 사이트 열기는 최소 3초 간격, 하루 최대 20회입니다.");
        note(schedule, "입실·퇴실 완료를 확인하면 해당 감시를 종료합니다.");
        note(schedule, "퇴실 재시도 시간 안에서, 퇴실 미확인 상태로 강의실을 벗어나면 알립니다.");
        note(schedule, "오늘 화면은 " + HomeTheme.nightStarts(prefs.end(), prefs.checkout()) + "부터 야간 테마로 전환합니다.");
        LinearLayout classDays = card("수업일");
        String[] weekdayLabels = {"월","화","수","목","금","토","일"};
        List<String> weekdays = new ArrayList<>();
        for (int i=0; i<weekdayLabels.length; i++) if (prefs.days().contains(i+1)) weekdays.add(weekdayLabels[i]);
        action(classDays, "수업 요일 선택  ›\n" + (weekdays.isEmpty() ? "선택 필요" : String.join(" · ", weekdays)), false, () -> editSchedule(ScheduleSection.DAYS));
        action(classDays, "달력에서 휴강일 등록 (" + prefs.excluded().size() + "일)", false, this::editExcluded);
        LinearLayout notifications = card("알림");
        notifications.addView(text("입실 · 퇴실 완료 알림 항상 표시",17,INK,true));
        note(notifications,"완료를 확인할 때마다 입실과 퇴실 알림을 따로 남깁니다. 앱을 열거나 감시가 끝나도 유지되며 직접 지울 수 있습니다.");
        note(notifications,Notices.attendanceNotificationsAllowed(this) ? "휴대폰에서 완료 알림을 허용하고 있습니다."
            : "휴대폰에서 알림이 차단되어 있습니다. 앱 알림과 ‘입실·퇴실 결과’ 알림을 허용해 주세요.");
        action(notifications,"앱 알림 설정",false,() -> PhoneSettings.open(this,PhoneSettings.Target.NOTIFICATIONS));
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
        note(location, "입실 시간에는 주변에서 Wi-Fi 미연결을 알리고, 퇴실 재시도 시간에는 미확인 상태로 강의실을 벗어나면 알립니다. GPS 오차가 큰 위치는 판단에 사용하지 않습니다.");
        action(location, "현재 위치로 등록", true, this::currentLocation);
        action(location, "좌표와 반경 변경", false, () -> editLocation(null));
        if (prefs.hasLocation()) action(location, "강의실 위치 삭제", false, () -> {
            prefs.raw.edit().remove("latitude").remove("longitude").apply(); Notices.wifi(this, false, ""); render(1);
        });
        LinearLayout help = card("Wi-Fi 자동 연결");
        note(help, "갤럭시의 인텔리전트 Wi-Fi에서 자동 켜기/끄기를 설정할 수 있습니다. 출석에 쓸 네트워크는 휴대폰에도 저장해 주세요.");
        action(help, "인텔리전트 Wi-Fi 설정", false, () -> ChromeAttendanceService.intelligentWifi(this));
        creatorPanel();
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

    private enum ScheduleSection {
        ENTRY("입실 시간"), CHECKOUT("퇴실 시간"), DAYS("수업 요일");
        final String title;
        ScheduleSection(String title) { this.title = title; }
    }
    private void editSchedule(ScheduleSection section) {
        LinearLayout view = form(); Set<String> chosen = new HashSet<>();
        for (int day : prefs.days()) chosen.add(String.valueOf(day));
        note(view, switch (section) {
            case ENTRY -> "입실을 처리할 시작·종료 시각을 선택하세요. 이 시간 안에서 입실이 확인되면 감시를 종료합니다.";
            case CHECKOUT -> "퇴실 시각에 처리를 시작합니다. 완료를 확인하면 종료하고, 실패하면 선택한 시간 동안 다시 시도합니다.";
            case DAYS -> "입실·퇴실을 처리할 수업 요일을 선택하세요. 등록한 휴강일은 제외합니다.";
        });
        if (section == ScheduleSection.DAYS) {
            String[] days = {"월","화","수","목","금","토","일"};
            LinearLayout row = new LinearLayout(this);
            for (int i=0; i<7; i++) {
                final String day = String.valueOf(i+1);
                CheckBox check = new CheckBox(this); check.setText(days[i]); check.setTextSize(13);
                check.setButtonDrawable((android.graphics.drawable.Drawable)null); check.setGravity(Gravity.CENTER);
                check.setChecked(chosen.contains(day));
                check.setBackground(shape(check.isChecked() ? 0xFFCDEDE4 : 0xFFF0F3F2, 8));
                check.setOnCheckedChangeListener((v, yes) -> { if (yes) chosen.add(day); else chosen.remove(day); check.setBackground(shape(yes ? 0xFFCDEDE4 : 0xFFF0F3F2, 8)); });
                row.addView(check,new LinearLayout.LayoutParams(0,dp(48),1));
            }
            gap(view,10); view.addView(row);
        }
        String[] times = {prefs.start(),prefs.end(),prefs.checkout()};
        String[] labels = {"입실 시작","입실 종료","퇴실 시각"};
        int[] indices = switch (section) {
            case ENTRY -> new int[]{0,1};
            case CHECKOUT -> new int[]{2};
            case DAYS -> new int[]{};
        };
        for (int index : indices) {
            Button time = button(getString(R.string.time_picker_label, labels[index], times[index]), false, () -> {});
            time.setOnClickListener(v -> {
                LocalTime initial = LocalTime.parse(times[index]);
                TimePickerDialog picker = new TimePickerDialog(this, (selectedTime,h,m) -> {
                    times[index] = String.format(Locale.ROOT,"%02d:%02d",h,m);
                    time.setText(getString(R.string.time_picker_label, labels[index], times[index]));
                }, initial.getHour(), initial.getMinute(), true);
                picker.setTitle(labels[index]); picker.show();
            });
            gap(view,10); view.addView(time,new LinearLayout.LayoutParams(-1,-2));
        }
        Spinner retry = new Spinner(this);
        if (section == ScheduleSection.CHECKOUT) {
            note(view,getString(R.string.checkout_delay_notice,Policy.CHECKOUT_DELAY_SECONDS));
            gap(view,10); note(view,"퇴실 실패 시 재시도할 시간");
            String[] options = new String[60];
            for (int i=0; i<options.length; i++) options[i] = (i+1) + "분";
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            retry.setAdapter(adapter); retry.setSelection(prefs.retryMinutes()-1);
            retry.setContentDescription("퇴실 재시도 시간"); view.addView(retry,new LinearLayout.LayoutParams(-1,dp(50)));
            note(view,"이 시간 안에서만 퇴실 재시도와 강의실 이탈 알림을 확인합니다. 퇴실 완료 또는 시간 종료 시 감시를 멈춥니다.");
            note(view,"이탈 알림에는 강의실 위치와 위치 항상 허용이 필요합니다.");
            if (!Notices.attentionNotificationsAllowed(this)) action(view,"이탈 알림 허용 설정",false,() -> PhoneSettings.open(this,PhoneSettings.Target.TEST_NOTIFICATIONS));
        }
        TextView error = text("",13,0xFFB3261E,false); error.setVisibility(View.GONE); view.addView(error);
        ScrollView scroll = new ScrollView(this); scroll.addView(view);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(section.title + " · 한국 시간").setView(scroll)
            .setPositiveButton("저장",null).setNegativeButton("취소",null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(b -> {
            String entryStart = section == ScheduleSection.ENTRY ? times[0] : prefs.start();
            String entryEnd = section == ScheduleSection.ENTRY ? times[1] : prefs.end();
            String checkout = section == ScheduleSection.CHECKOUT ? times[2] : prefs.checkout();
            int minutes = section == ScheduleSection.CHECKOUT ? retry.getSelectedItemPosition()+1 : prefs.retryMinutes();
            if (chosen.isEmpty() || !Policy.validSchedule(entryStart,entryEnd,checkout,minutes)) {
                error.setText(switch (section) {
                    case ENTRY -> "입실 시작은 종료보다 빨라야 하며, 종료는 퇴실 " + checkout + "보다 늦을 수 없습니다.";
                    case CHECKOUT -> "퇴실은 입실 종료 " + entryEnd + "와 같거나 늦어야 하며, 재시도는 자정 전에 끝나야 합니다.";
                    case DAYS -> "수업 요일을 하나 이상 선택해 주세요.";
                });
                error.setVisibility(View.VISIBLE); return;
            }
            SharedPreferences.Editor edit = prefs.raw.edit();
            switch (section) {
                case ENTRY -> edit.putString("start",entryStart).putString("end",entryEnd);
                case CHECKOUT -> edit.putString("checkout",checkout).putInt("checkout_retry_minutes",minutes);
                case DAYS -> edit.putStringSet("days",chosen);
            }
            edit.apply(); prefs.log(section.title + " 변경"); ScheduleController.reconcile(this); dialog.dismiss(); render(1);
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
        LinearLayout checks=card("설정 확인");setupStatus=new SetupStatusView(this,true);checks.addView(setupStatus);
        LinearLayout chrome = card("01  기존 Chrome 연결");
        chrome.addView(text(ChromeAttendanceService.available() ? "Chrome 접근성 연결됨" : "Chrome 접근성을 켜 주세요",18,INK,true));
        note(chrome,"휴대폰 Chrome에 저장된 인증을 그대로 사용합니다. 지정한 출석 화면의 버튼과 기록 시각을 확인합니다.");
        note(chrome,"설정 항목을 누르면 해당 휴대폰 메뉴로 이동합니다. 권한 허용과 보안 설정 변경은 직접 선택합니다.");
        action(chrome,"Chrome 출결 화면 열기",true,() -> ChromeAttendanceService.openChrome(this,SiteProtocol.PAGE));
        action(chrome,"Chrome 접근성 설정",false,() -> PhoneSettings.open(this,PhoneSettings.Target.ACCESSIBILITY));
        action(chrome,"Chrome 인증 페이지 열기",false,() -> ChromeAttendanceService.openChrome(this,SiteProtocol.AUTH_PAGE));
        note(chrome,"이미 출결 화면이 열리면 다시 인증하지 않아도 됩니다. 화면이 잠겨 있으면 출석 알림을 눌러 잠금을 해제해 주세요.");
        LinearLayout permissions = card("02  위치와 알림");
        permissions.addView(text(ScheduleController.backgroundLocationAllowed(this) ? "위치 항상 허용됨" : "예약 실행에 위치 항상 허용 필요",17,INK,true));
        note(permissions,"예약 시간에 Wi-Fi 이름을 확인하고 입실 시간에만 강의실 위치를 확인합니다.");
        action(permissions,"위치 · 알림 권한 허용",false,this::permissions);
        action(permissions,"위치 항상 허용 설정",false,() -> PhoneSettings.open(this,PhoneSettings.Target.LOCATION_PERMISSION));
        note(permissions,getSystemService(NotificationManager.class).areNotificationsEnabled() ? "알림 권한이 허용되어 있습니다." : "출석 알림을 받으려면 앱 알림을 허용해 주세요.");
        LinearLayout alarm = card("03  예약 실행");
        alarm.addView(text(AlarmScheduler.allowed(this) ? "정확한 알람 사용 가능" : "정확한 알람 허용 필요",18,INK,true));
        long next = prefs.raw.getLong("next_alarm",0);
        note(alarm,next > 0 ? "다음 예약: "+Instant.ofEpochMilli(next).atZone(Policy.ZONE).format(DateTimeFormatter.ofPattern("M/d HH:mm"))
            : "설정에서 출석 예약을 켜면 수업일에 맞춰 예약합니다.");
        if (Build.VERSION.SDK_INT >= 31) action(alarm,"알람 및 리마인더 설정",false,
            () -> PhoneSettings.open(this,PhoneSettings.Target.ALARMS));
        action(alarm,"배터리 최적화 설정",false,() -> PhoneSettings.open(this,PhoneSettings.Target.BATTERY));
        note(alarm,"입실·퇴실 시간 밖에서는 감시를 종료합니다. 재부팅 후 예약을 복구하며 앱 강제 중지 후에는 앱을 다시 열어 주세요.");
        if (!prefs.raw.getString("chrome_pending","").isEmpty()) {
            LinearLayout pending = card("클릭 결과 확인");
            PendingClick saved=PendingClick.parse(prefs.raw.getString("chrome_pending",""));
            note(pending,saved==null?"날짜·출결 단계 미확인":saved.label()+" 요청 · 결과 확인 필요");
            long at=prefs.raw.getLong("chrome_pending_at",0);
            note(pending,at>0?"클릭 전 저장: "+Instant.ofEpochMilli(at).atZone(Policy.ZONE).format(DateTimeFormatter.ofPattern("M/d HH:mm:ss")):"이전 버전의 요청 시각은 기록되지 않았습니다.");
            note(pending,"클릭 직전 저장한 표시이며 클릭 성공·서버 처리 완료를 뜻하지 않습니다. Chrome에서 실제 기록을 먼저 확인해 주세요.");
            action(pending,"Chrome에서 기록 확인",false,() -> ChromeAttendanceService.openChrome(this,SiteProtocol.PAGE));
            action(pending,"확인 후 보류 해제",false,() -> new AlertDialog.Builder(this).setTitle("Chrome에서 기록을 확인했나요?")
                .setMessage("이전 클릭 보류 표시를 지웁니다. 오류로 중단된 오늘의 자동 접속과 접속 횟수 한도는 유지됩니다. 필요한 출결은 Chrome에서 직접 확인해 주세요.").setNegativeButton("취소",null)
                .setPositiveButton("확인했고 해제",(d,w) -> { prefs.raw.edit().remove("chrome_pending").remove("chrome_pending_at").apply(); render(2); }).show());
        }
        LinearLayout info = card("출석 메이트 " + BuildConfig.VERSION_NAME + (BuildConfig.CREATOR_TOOLS ? " · 제작자용" : ""));
        note(info,"출결 달력에서 사이트 확인 기록을 보고, 지난 날짜는 출석·조퇴·결석을 직접 입력할 수 있습니다. 직접 입력은 앱에만 저장됩니다.");
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
        if (start) setAttendanceReservation(true); else if (locate) currentLocation(); else render(selected);
    }
}
