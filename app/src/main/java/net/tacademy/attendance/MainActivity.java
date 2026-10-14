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
    private boolean pendingStart, pendingLocation, creatorExpanded;
    private LinearLayout creatorBody;
    private Button creatorToggle;
    private AlertDialog creatorPasswordDialog, firstUseDialog;
    private SetupStatusView setupStatus;
    private TextView passiveStatus;
    private TextView status, detail, inTime, outTime, todayLog, todayWifi, todayWifiHint, todayWifiIcon;
    private LinearLayout todayWifiPanel,shortDayCard;
    private TextView shortDayText;
    private HomeTheme homeTheme;
    private StandSceneView reservationScene;
    private TextView reservationState, entryReceipt, exitReceipt;
    private Button enterNow, leaveNow, readAttendance;
    private TextView attendanceOverview;
    private TextView lockdownResult;
    private LocalDate homeDay;
    private String homeCheckout, homeEntryEnd;
    private Clock homeClock = Clock.system(Policy.ZONE);
    private ScrollView homeScroll;
    private LocationManager locationManager;
    private LocationListener oneLocation;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { if (selected == 0) updateHome(); else {if(setupStatus!=null)setupStatus.refresh();if(lockdownResult!=null)lockdownResult.setText(lockdownStatus());} handler.postDelayed(this, 2000); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        if(!PersonalAttendanceCorrection.apply(prefs.raw,BuildConfig.CREATOR_TOOLS))toast("출결 보정을 저장하지 못했어요. 앱을 다시 열어 주세요.");
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
        if(prefs.raw.getBoolean("first_use_guide_pending",false))showUsageGuide();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=SetupGuideActivity.REQUEST)return;
        if(result==Activity.RESULT_FIRST_USER){prefs.raw.edit().putBoolean("setup_finished",true).apply();render(0);return;}
        if(result==Activity.RESULT_OK){
            if(SetupGuidePolicy.readyToEnable(SetupGuideActivity.snapshot(this))){setAttendanceReservation(true);if(prefs.enabled()&&prefs.auto())prefs.raw.edit().putBoolean("setup_finished",true).apply();}
            else {toast("필요한 설정을 다시 확인해 주세요");SetupGuideActivity.open(this);}
        }
    }

    @Override protected void onResume() {
        super.onResume(); ChromeAttendanceService.cancelSettingsMenu();
        ChromeAttendanceService.returnedToAttendanceApp();
        handler.removeCallbacks(refresh); handler.post(refresh);
        if (selected != 0) render(selected);
        ShortDayReminder.reconcile(this);
        if (prefs.enabled()) ScheduleController.reconcile(this);
    }
    @Override protected void onPause() { handler.removeCallbacks(refresh); closeCreatorPanel(); if(creatorPasswordDialog!=null)creatorPasswordDialog.dismiss(); super.onPause(); }
    @Override protected void onDestroy() {
        ActivationRun.cancel(this);
        if(firstUseDialog!=null)firstUseDialog.dismiss();
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
    private LinearLayout settingsCard(String title, String key, java.util.function.Supplier<String> help) {
        LinearLayout box = card(""); box.setPadding(dp(20), dp(10), dp(20), dp(18));
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text(title, 14, TEAL, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button info = new Button(this); info.setText("도움말"); info.setTextSize(12); info.setAllCaps(false);
        info.setTextColor(TEAL); info.setMinWidth(0); info.setMinimumWidth(0); info.setMinHeight(dp(48));
        info.setPadding(dp(8), 0, dp(8), 0); info.setStateListAnimator(null); info.setElevation(0);
        info.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22007C78),shape(0xFFF0F7F5,16),null));
        info.setTag("settings-help-" + key); info.setContentDescription(title + " 도움말 보기");
        info.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(title + " 도움말")
            .setMessage(help.get()).setPositiveButton("닫기", null).show());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(72), -2); params.leftMargin = dp(8);
        heading.addView(info, params); box.addView(heading); gap(box, 4);
        return box;
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
        if(tab!=selected)closeCreatorPanel();
        creatorBody=null;creatorToggle=null;
        selected = tab; setupStatus=null; lockdownResult=null; root.removeAllViews();
        ZonedDateTime now = ZonedDateTime.now(homeClock).withZoneSameInstant(Policy.ZONE);
        homeDay = now.toLocalDate(); homeCheckout = prefs.checkoutFor(homeDay); homeEntryEnd = prefs.end();
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
            LinearLayout titleRow=new LinearLayout(this);titleRow.setTag("today-header");titleRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout titles=new LinearLayout(this);titles.setTag("today-attendance-heading");titles.setOrientation(LinearLayout.VERTICAL);
            TextView todayTitle=text(now.format(DateTimeFormatter.ofPattern("M월 d일",Locale.KOREAN)),25,homeTheme.ink,true);
            todayTitle.setTag("today-date");todayTitle.setSingleLine(true);todayTitle.setAutoSizeTextTypeUniformWithConfiguration(17,25,1,android.util.TypedValue.COMPLEX_UNIT_SP);
            titles.addView(todayTitle,new LinearLayout.LayoutParams(-1,dp(35)));
            titles.addView(text(now.format(DateTimeFormatter.ofPattern("EEEE",Locale.KOREAN)),13,homeTheme.muted,false));
            titleRow.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
            Button calendarLink=compactHomeButton("출결·시간표\n보기 ›",this::attendanceCalendar);calendarLink.setTag("attendance-calendar");
            calendarLink.setContentDescription("달력에서 출결과 수업 시간표 함께 보기");calendarLink.setBackground(shape(homeTheme.soft,14));
            calendarLink.setPadding(dp(4),0,dp(4),0);calendarLink.setTextSize(11);
            LinearLayout.LayoutParams calendarParams=new LinearLayout.LayoutParams(dp(78),dp(52));calendarParams.setMargins(dp(6),0,dp(8),0);titleRow.addView(calendarLink,calendarParams);
            titleRow.addView(wifiBadge(),new LinearLayout.LayoutParams(dp(118),-2));content.addView(titleRow);
            gap(content, 18);
            if(!prefs.enabled()&&!prefs.raw.getBoolean("setup_finished",false)){Button guide=button(prefs.raw.getBoolean("setup_visited",false)?"자동출석 준비 · 빠른 설정 이어하기  ›":"자동출석을 원하면 · 빠른 설정 시작하기  ›",true,()->SetupGuideActivity.open(this));guide.setTag("setup-guide-home");content.addView(guide,new LinearLayout.LayoutParams(-1,-2));gap(content,14);}
            home();
        } else {
            content.addView(text("T  /  ATTENDANCE MATE", 12, TEAL, true)); gap(content, 10);
            content.addView(text(tab == 1 ? "나에게 맞게 설정" : "자동출석 준비", 28, INK, true)); gap(content, 8);
            content.addView(text(tab == 1 ? "출석 주소 · 알림 · 제작자 진단" : "예약부터 Wi-Fi·장소·잠금까지 한곳에서", 14, MUTED, false));
            gap(content, 24);
            if (tab == 1) settings(); else connection();
        }
        tabs = new LinearLayout(this); tabs.setPadding(dp(16), dp(10), dp(16), dp(10));
        tabs.setBackgroundColor(tab == 0 ? homeTheme.surface : Color.WHITE);
        String[] labels = {"오늘", "설정", "자동출석"};
        for (int i = 0; i < labels.length; i++) {
            final int target = i; Button item = button(labels[i], selected == i, () -> render(target));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1); p.setMargins(dp(4), 0, dp(4), 0);
            tabs.addView(item, p);
        }
        root.addView(tabs);
        if(tab!=0&&getIntent().hasExtra("focus_setting")){
            View focus=content.findViewWithTag(getIntent().getStringExtra("focus_setting"));if(focus!=null)scroll.post(()->{if(homeScroll==scroll){getIntent().removeExtra("focus_setting");scroll.smoothScrollTo(0,Math.max(0,focus.getTop()-dp(16)));}});
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
        hero.setBackgroundColor(Color.TRANSPARENT);
        reservationScene=new StandSceneView(this,homeTheme,()->setAttendanceReservation(!prefs.enabled()));
        reservationScene.setBackground(shape(homeTheme.soft,22));reservationScene.setClipToOutline(true);
        hero.addView(reservationScene,new LinearLayout.LayoutParams(-1,dp(190)));
        boolean nightScene=homeTheme==HomeTheme.NIGHT;
        int sceneInk=nightScene?0xFFF5F2E9:0xFF3A251C;
        int sceneShadow=nightScene?0xDD101B2B:0xDDFFF9EF;
        LinearLayout sceneWords=new LinearLayout(this);sceneWords.setOrientation(LinearLayout.VERTICAL);sceneWords.setTag("hero-text");
        shortDayCard=new LinearLayout(this);shortDayCard.setOrientation(LinearLayout.VERTICAL);shortDayCard.setTag("today-short-day");
        TextView shortTitle=text("오늘은 단축수업이에요",16,sceneInk,true);shortTitle.setShadowLayer(dp(3),0,0,sceneShadow);shortDayCard.addView(shortTitle);sceneWords.addView(shortDayCard);
        TextView greeting=text(nightScene?"차분하게 하루를 마무리해요.":"오늘도 좋은 배움이 기다려요.",16,nightScene?0xFFF3F0E8:0xFF422C23,true);
        greeting.setTag("hero-greeting");greeting.setSingleLine(true);greeting.setAutoSizeTextTypeUniformWithConfiguration(13,16,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        greeting.setPadding(0,dp(4),0,dp(9));greeting.setShadowLayer(dp(3),0,0,sceneShadow);sceneWords.addView(greeting,new LinearLayout.LayoutParams(-1,-2));
        shortDayText=text("",11,sceneInk,false);shortDayText.setTag("today-short-day-text");shortDayText.setLineSpacing(dp(2),1);shortDayText.setShadowLayer(dp(3),0,0,sceneShadow);sceneWords.addView(shortDayText);
        FrameLayout.LayoutParams sceneParams=new FrameLayout.LayoutParams(dp(250),-2,Gravity.TOP|Gravity.START);sceneParams.setMargins(dp(18),dp(14),dp(12),dp(12));reservationScene.addView(sceneWords,sceneParams);
        reservationScene.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int width=Math.min(dp(250),(r-l)*70/100);if(sceneWords.getLayoutParams().width!=width){sceneWords.getLayoutParams().width=width;sceneWords.requestLayout();}});
        LinearLayout caption=new LinearLayout(this);caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.setPadding(dp(16),dp(10),dp(10),dp(10));
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);
        reservationState=text("",12,homeTheme.muted,true);reservationState.setTag("reservation-state");
        reservationState.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);words.addView(reservationState);
        words.addView(text("스탠드를 눌러 출석 예약을 켜고 꺼요",11,homeTheme.muted,false));
        caption.addView(words,new LinearLayout.LayoutParams(0,-2,1));hero.addView(caption);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2); heroParams.bottomMargin = dp(16);
        content.addView(hero, heroParams);

        LinearLayout state = card("출결 현황");
        status = text("", 20, homeTheme.ink, true);status.setTag("today-attendance-status");detail = text("", 12, homeTheme.muted, false);
        LinearLayout statusRow=new LinearLayout(this);statusRow.setGravity(Gravity.CENTER_VERTICAL);statusRow.setTag("today-attendance-status-row");
        statusRow.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        readAttendance=compactHomeButton("출결 기록 확인 ↻",()->ChromeAttendanceService.beginManual(this,Policy.Phase.CHECKOUT,true,false));
        readAttendance.setTag("attendance-read-record");statusRow.addView(readAttendance,new LinearLayout.LayoutParams(-2,dp(48)));
        state.addView(statusRow);gap(state,6);state.addView(detail);
        passiveStatus=text("",12,homeTheme.muted,false);passiveStatus.setTag("today-passive-status");state.addView(passiveStatus);gap(state,18);
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
        LinearLayout enterColumn=new LinearLayout(this);enterColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout leaveColumn=new LinearLayout(this);leaveColumn.setOrientation(LinearLayout.VERTICAL);
        enterColumn.addView(enterNow,new LinearLayout.LayoutParams(-1,dp(60)));
        leaveColumn.addView(leaveNow,new LinearLayout.LayoutParams(-1,dp(60)));
        entryReceipt=receiptLabel("attendance-entry-time");exitReceipt=receiptLabel("attendance-exit-time");
        enterColumn.addView(entryReceipt,new LinearLayout.LayoutParams(-1,-2));leaveColumn.addView(exitReceipt,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams enterParams=new LinearLayout.LayoutParams(0,-2,1);enterParams.rightMargin=dp(8);
        quick.addView(enterColumn,enterParams);quick.addView(leaveColumn,new LinearLayout.LayoutParams(0,-2,1));state.addView(quick);
        LinearLayout management=card("출결 관리");
        attendanceOverview=text("",13,homeTheme.muted,false);attendanceOverview.setTag("attendance-overview");management.addView(attendanceOverview);
        gap(management,8);
        Button manage=button("출결률 · 남은 결석 확인  ›",true,()->startActivity(new Intent(this,AttendanceManagementActivity.class)));
        manage.setTag("attendance-management");management.addView(manage,new LinearLayout.LayoutParams(-1,-2));
        gap(management,10);
        LinearLayout materials=new LinearLayout(this);materials.setTag("attendance-materials-row");materials.setGravity(Gravity.CENTER_VERTICAL);
        Button timetable=button("시간표 보기",false,()->startActivity(new Intent(this,TimetableActivity.class)));
        timetable.setTag("attendance-timetable");
        Button orientation=button("오리엔테이션 자료",false,()->startActivity(new Intent(this,OrientationActivity.class)));
        orientation.setTag("attendance-orientation");
        for(Button material:new Button[]{timetable,orientation}){
            material.setMinHeight(0);material.setMinimumHeight(0);material.setMinWidth(0);material.setMinimumWidth(0);
            material.setPadding(dp(8),dp(8),dp(8),dp(8));material.setMaxLines(2);material.setGravity(Gravity.CENTER);
            material.setAutoSizeTextTypeUniformWithConfiguration(10,13,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        }
        LinearLayout.LayoutParams leftMaterial=new LinearLayout.LayoutParams(0,dp(64),1);leftMaterial.rightMargin=dp(10);
        materials.addView(timetable,leftMaterial);materials.addView(orientation,new LinearLayout.LayoutParams(0,dp(64),1));
        management.addView(materials,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout log = card("오늘 출석 기록");
        todayLog = text("", 13, homeTheme.muted, false); todayLog.setLineSpacing(dp(8), 1); log.addView(todayLog);
        updateHome();
    }

    private LinearLayout wifiBadge(){
        todayWifiPanel=new LinearLayout(this);todayWifiPanel.setTag("today-wifi-panel");
        todayWifiPanel.setOrientation(LinearLayout.HORIZONTAL);todayWifiPanel.setGravity(Gravity.CENTER_VERTICAL);
        todayWifiPanel.setPadding(dp(8),dp(9),dp(8),dp(9));todayWifiPanel.setMinimumHeight(dp(52));
        todayWifiPanel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        todayWifiPanel.setFocusable(true);todayWifiPanel.setClickable(true);todayWifiPanel.setOnClickListener(v->connectAttendanceWifi());
        todayWifiIcon=text("!",13,homeTheme.ink,true);todayWifiIcon.setGravity(Gravity.CENTER);
        todayWifiIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(dp(18),dp(18));iconParams.rightMargin=dp(5);
        todayWifiPanel.addView(todayWifiIcon,iconParams);
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        todayWifiHint=text("WI-FI",9,homeTheme.muted,true);todayWifiHint.setLetterSpacing(.08f);labels.addView(todayWifiHint);
        todayWifi=text("",12,homeTheme.ink,true);todayWifi.setTag("today-wifi");todayWifi.setSingleLine(true);todayWifi.setAutoSizeTextTypeUniformWithConfiguration(10,12,1,android.util.TypedValue.COMPLEX_UNIT_SP);labels.addView(todayWifi,new LinearLayout.LayoutParams(-1,dp(21)));
        todayWifiPanel.addView(labels,new LinearLayout.LayoutParams(0,-2,1));return todayWifiPanel;
    }
    private Button compactHomeButton(String label,Runnable action){
        Button b=button(label,false,action);b.setTextSize(11);b.setTextColor(homeTheme.muted);b.setBackgroundColor(Color.TRANSPARENT);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(8),0,dp(8),0);return b;
    }
    private TextView receiptLabel(String tag){
        TextView label=text("",12,homeTheme==HomeTheme.NIGHT?0xFFA8ADB6:0xFF75736F,false);
        label.setTag(tag);label.setGravity(Gravity.CENTER);label.setFontFeatureSettings("tnum");
        label.setPadding(0,dp(7),0,0);label.setVisibility(View.GONE);return label;
    }
    private void showReceipt(TextView label,String name,String time){
        String hint=time.isEmpty()?prefs.attendanceHint(homeDay.toString(),name.equals("입실")?Policy.Phase.ENTRY:Policy.Phase.CHECKOUT):"";
        String caption=time.isEmpty()?hint:name+"  "+time;
        setIfChanged(label,caption);label.setVisibility(caption.isEmpty()?View.GONE:View.VISIBLE);
        label.setContentDescription(time.isEmpty()?hint:name+" 확인 시각 "+time);
    }
    private Button attendanceButton(String label,Policy.Phase phase){
        Button button=button(label,false,()->manualAttendance(phase));boolean night=homeTheme==HomeTheme.NIGHT;
        button.setTextSize(16);button.setTypeface(null,Typeface.BOLD);button.setBackground(shape(phase==Policy.Phase.ENTRY?(night?0xFF624B35:0xFFFFDEB8):(night?0xFF354665:0xFFE2E6FB),22));
        button.setTextColor(phase==Policy.Phase.ENTRY?(night?0xFFFFDEA3:0xFF794C24):(night?0xFFDAE2FF:0xFF515D91));return button;
    }
    private void manualAttendance(Policy.Phase phase){
        Policy.Record confirmed=prefs.history(LocalDate.now(Policy.ZONE));
        if(confirmed!=null&&(phase==Policy.Phase.ENTRY?confirmed.checkedIn():confirmed.checkedOut())){updateHome();return;}
        if(ChromeAttendanceService.busy()){toast("출결 결과를 확인하고 있어요. 잠시 기다려 주세요.");return;}
        if(phase==Policy.Phase.CHECKOUT&&LocalTime.now(Policy.ZONE).isBefore(LocalTime.parse(prefs.checkoutToday()))){
            new AlertDialog.Builder(this).setTitle("지금 조퇴하시겠어요?")
                .setMessage("예정 퇴실 시각은 "+prefs.checkoutToday()+"입니다. 확인하면 Chrome에서 퇴실을 진행하고, 사이트의 퇴실 시각을 확인한 뒤 조퇴로 기록해요.")
                .setNegativeButton("취소",null).setPositiveButton("조퇴하기",(dialog,which)->submitManualAttendance(phase,true)).show();return;
        }
        submitManualAttendance(phase,false);
    }
    private void submitManualAttendance(Policy.Phase phase,boolean earlyApproved){
        String key=LocalDate.now(Policy.ZONE)+":"+phase;
        if(key.equals(prefs.raw.getString("chrome_pending",""))){
            String name=phase==Policy.Phase.ENTRY?"입실":"퇴실";
            new AlertDialog.Builder(this).setTitle("이전 "+name+" 요청을 확인해요")
                .setMessage("이전에 버튼을 눌렀지만 결과가 확인되지 않았어요. 먼저 Chrome 기록을 확인해 주세요. 실제 미처리를 확인했다면 한 번 다시 실행할 수 있어요.")
                .setPositiveButton("기록 먼저 확인",(d,w)->ChromeAttendanceService.beginManual(this,phase,true,false))
                .setNeutralButton("미처리 확인 · 재시도",(d,w)->{if(earlyApproved)ChromeAttendanceService.beginEarlyCheckout(this,true);else ChromeAttendanceService.beginManual(this,phase,false,true);})
                .setNegativeButton("취소",null).show();return;
        }
        if(earlyApproved)ChromeAttendanceService.beginEarlyCheckout(this,false);else ChromeAttendanceService.beginManual(this,phase,false,false);
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
        if (HomeTheme.at(now, prefs.end(), prefs.checkoutFor(now.toLocalDate())) != homeTheme || !now.toLocalDate().equals(homeDay) || !prefs.checkoutFor(now.toLocalDate()).equals(homeCheckout) || !prefs.end().equals(homeEntryEnd)) {
            int position = homeScroll.getScrollY(); render(0); homeScroll.post(() -> homeScroll.scrollTo(0, position)); return;
        }
        boolean shortDay=CourseSchedule.activeShortDay(now.toLocalDate(),prefs.days(),prefs.excluded());
        shortDayCard.setVisibility(shortDay?View.VISIBLE:View.GONE);shortDayText.setVisibility(shortDay?View.VISIBLE:View.GONE);
        if(shortDay)setIfChanged(shortDayText,CourseSchedule.summary(now.toLocalDate())+(prefs.enabled()&&prefs.auto()?"":"\n자동 출결 꺼짐"));
        boolean reservationEnabled=prefs.enabled();
        reservationScene.setLit(reservationEnabled);
        setIfChanged(reservationState,reservationEnabled?"출석 예약 켜짐":"출석 예약 꺼짐");
        if(passiveStatus!=null){passiveStatus.setVisibility(prefs.passiveRecords()?View.VISIBLE:View.GONE);setIfChanged(passiveStatus,passiveRecordMessage());}
        reservationState.setTextColor(reservationEnabled?(homeTheme==HomeTheme.NIGHT?0xFFF5D294:0xFF875024):homeTheme.muted);
        Policy.Record record = prefs.history(now.toLocalDate());
        String entry = record == null ? "" : record.in(), exit = record == null ? "" : record.out();
        boolean earlyExit=AttendanceMetrics.isEarly(exit,prefs.checkoutFor(now.toLocalDate()));
        String title = !exit.isEmpty() ? (earlyExit?"오늘 조퇴 완료":"오늘 출결 완료") : !entry.isEmpty() ? "입실 완료"
            : MonitorService.running ? prefs.raw.getString("status", "입실 확인 중")
            : prefs.enabled() ? "출석 예약 대기" : "출석 예약 꺼짐";
        String description = prefs.enabled() ? "입실 " + prefs.start() + "–" + prefs.end() + " · 퇴실 " + Policy.checkoutStartTime(now.toLocalDate(),prefs.checkout())
            : "자동출석 탭에서 출석 예약을 켜 주세요.";
        if(prefs.enabled()&&!Policy.isClassDay(now,prefs.days(),prefs.automationExcluded()))description=prefs.manualAttendance(now.toLocalDate())==MonthAttendance.ManualStatus.VACATION?"오늘은 휴가 · 자동 출결 쉬어요":"오늘은 수업 없는 날 · 자동 출결 쉬어요";
        else if(prefs.enabled()&&CourseSchedule.shortened(now.toLocalDate()))description+="\n단축수업 · "+CourseSchedule.classEnd(now.toLocalDate())+" 종료";
        if (MonitorService.running) description = prefs.raw.getString("detail", description);
        boolean busy=ChromeAttendanceService.busy()||ActivationRun.pending();
        if(ActivationRun.pending()){title=ActivationRun.label();description="완료 기록을 먼저 확인해요. 이전 요청이 있으면 중복으로 누르지 않아요.";}
        setIfChanged(enterNow,!entry.isEmpty()?"✓  입실 완료":"☀  입실하기");enterNow.setEnabled(entry.isEmpty()&&!busy);enterNow.setAlpha(enterNow.isEnabled()?1f:.55f);
        setIfChanged(leaveNow,!exit.isEmpty()?"✓  퇴실 완료":"☾  퇴실하기");leaveNow.setEnabled(exit.isEmpty()&&!busy);leaveNow.setAlpha(leaveNow.isEnabled()?1f:.55f);
        readAttendance.setEnabled(!busy);
        AttendanceMetrics.Summary summary=prefs.attendanceSummary();
        setIfChanged(attendanceOverview,"현재 "+summary.currentDisplay()+" · 종료된 수업 "+summary.elapsed()+"일 기준"
            +"\n"+(summary.exceeded()>0?"80% 기준 초과 "+summary.exceededDisplay()+"일":"80% 기준 · 결석 "+summary.absenceBudget()+"일 또는 지각·조퇴 "+summary.eventBudget()+"회 가능")
            +(summary.missing()>0?"\n미기록 "+summary.missing()+"일은 출석으로 계산":"")
            +"\n휴가 잔여 "+prefs.vacationBalance().available()+"일 · 예약 "+prefs.vacationBalance().reserved()+"일");
        showReceipt(entryReceipt,"입실",entry);showReceipt(exitReceipt,"퇴실",exit);
        setIfChanged(status, title); setIfChanged(detail, description);
        setIfChanged(inTime, entry.isEmpty() ? "—" : entry); setIfChanged(outTime, exit.isEmpty() ? "—" : exit);
        // Show confirmed attendance times; operational diagnostics stay in their existing log.
        String log = exit.isEmpty() ? "" : exit + (earlyExit?"  조퇴 완료":"  퇴실 완료");
        if (!entry.isEmpty()) log += (log.isEmpty() ? "" : "\n") + entry + "  입실 완료";
        setIfChanged(todayLog, log.isEmpty() ? "아직 확인된 출석 기록이 없습니다." : log);
        NetworkAccess.State wifi=NetworkAccess.inspect(this);
        boolean matched=wifi.allowed();
        boolean night=homeTheme==HomeTheme.NIGHT;
        int wifiInk=matched?(night?0xFFA4E6CB:0xFF146448):(night?0xFFFFD6B5:0xFF913810);
        int wifiBackground=matched?(night?0xFF193A37:0xFFE5F3EC):(night?0xFF432D24:0xFFFFE7D2);
        int wifiBorder=matched?(night?0xFF3D7468:0xFF9ACCB8):(night?0xFFE89756:0xFFD97A39);
        setIfChanged(todayWifi,matched?"대기 중":wifi.wifiTransportPresent()?"연결 확인 필요":"Wi-Fi 미연결");
        setIfChanged(todayWifiHint,matched?"WI-FI 연결됨":"눌러서 연결 · 확인");
        setIfChanged(todayWifiIcon,matched?"✓":"!");
        todayWifi.setTextColor(wifiInk);todayWifiHint.setTextColor(wifiInk);todayWifiIcon.setTextColor(wifiInk);
        todayWifiIcon.setBackground(shape(matched?(night?0xFF28564B:0xFFC7E6D9):(night?0xFF66402B:0xFFF9C49A),22));
        GradientDrawable wifiShape=shape(wifiBackground,16);wifiShape.setStroke(dp(1),wifiBorder);todayWifiPanel.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22007C78),wifiShape,null));
        todayWifiPanel.setContentDescription("대상 와이파이 인식 상태 · "+todayWifi.getText()+(matched?"":" · 눌러서 출석 Wi-Fi 연결 또는 필요한 설정 확인"));
    }

    private void connectAttendanceWifi(){
        NetworkAccess.State state=NetworkAccess.inspect(this);
        if(state.allowed()){toast("출석용 Wi-Fi에 연결되어 있어요");return;}
        if(Set.of("FINE_LOCATION_REQUIRED","PERMISSION_DENIED","SSID_UNAVAILABLE_BACKGROUND_PERMISSION").contains(state.code())){
            toast(state.reason());PhoneSettings.open(this,PhoneSettings.Target.LOCATION_PERMISSION);return;
        }
        if("LOCATION_DISABLED".equals(state.code())){toast(state.reason());PhoneSettings.open(this,PhoneSettings.Target.LOCATION_SERVICE);return;}
        if("VPN_ACTIVE".equals(state.code())){new AlertDialog.Builder(this).setTitle("Wi-Fi 연결 확인").setMessage(state.reason()).setPositiveButton("확인",null).show();return;}
        List<String> targets=new ArrayList<>(prefs.ssids());
        if(targets.isEmpty()){toast("출석에 사용할 Wi-Fi를 먼저 등록해 주세요");render(2);return;}
        if(targets.size()==1){ChromeAttendanceService.connectWifi(this,targets.get(0));return;}
        new AlertDialog.Builder(this).setTitle("연결할 출석 Wi-Fi").setItems(targets.toArray(new String[0]),
            (dialog,which)->ChromeAttendanceService.connectWifi(this,targets.get(which))).setNegativeButton("취소",null).show();
    }

    private void setAttendanceReservation(boolean enabled) {
        int returnTab=selected;
        pendingStart = false;
        if (!enabled) {
            ActivationRun.cancel();prefs.enabled(false);
            ChromeAttendanceService.cancelForReservationOff();ScheduleController.reconcile(this);
            Notices.wifi(this, false, ""); prefs.log("출석 예약 꺼짐"); render(returnTab); return;
        }
        if (prefs.ssids().isEmpty() || prefs.days().isEmpty()) { toast("Wi-Fi와 수업 요일을 먼저 등록해 주세요"); return; }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingStart = true; permissions(); return;
        }
        if (!ScheduleController.backgroundLocationAllowed(this) || !AlarmScheduler.allowed(this) || !ChromeAttendanceService.available()) {
            toast("자동출석 탭에서 Chrome 접근성, 위치 항상 허용, 정확한 알람을 설정해 주세요"); render(2); return;
        }
        LocationManager lm = getSystemService(LocationManager.class);
        if (Build.VERSION.SDK_INT >= 28 && !lm.isLocationEnabled()) { toast("휴대폰 위치 기능을 켜 주세요"); openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS); return; }
        prefs.enableAutomaticClick();prefs.enabled(true);prefs.log("출석 예약 켜짐");
        boolean immediate=!ChromeAttendanceService.busy()&&ActivationRun.start(this);
        ScheduleController.reconcile(this);render(immediate?0:returnTab);
    }

    private void reservationSettings() {
        LinearLayout automation = settingsCard("출석 예약", "reservation", () -> {
            String checkoutEnd=LocalTime.parse(prefs.checkoutToday()).plusMinutes(prefs.retryMinutes()).format(DateTimeFormatter.ofPattern("HH:mm"));
            return "켜기를 누르면 자동 클릭도 켜지고, 처리 안내 화면·완료 후 화면 끄기·잠금 모드가 켜져요. 잠금 모드 후에는 PIN 등으로 한 번 해제해야 해요. 지금 가능한 미처리 출결을 5초 뒤 한 번 실행해요. 이전 요청이 있으면 완료 기록만 먼저 확인해요. 예약이 이미 켜져 있어도 다시 누를 수 있어요."
                + "\n\n자동 감시는 수업일의 아래 시간에만 작동합니다.\n입실 " + prefs.start() + "–" + prefs.end()
                + "\n퇴실 " + prefs.checkoutStartToday() + "–" + checkoutEnd + "\n출석 완료 또는 시간 종료 시 GPS 감시도 멈춥니다."
                + "\n\n기존 Chrome 인증을 사용합니다. 화면이 꺼지면 한 번 깨우고, Extend Unlock을 선택한 경우 신뢰 상태의 잠금 해제를 요청합니다. PIN·지문 인증은 자동으로 통과할 수 없습니다. 자동 클릭을 끄면 직접 연 Chrome의 완료 기록만 저장합니다.";
        });automation.setTag("automation");
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
        note(automation,ActivationRun.pending()?ActivationRun.label():prefs.enabled() ? "예약 켜짐 · 출석 시간에만 작동" : "예약 꺼짐 · Chrome 출결 기록만 자동 저장");
        TextView passiveNote=text(passiveRecordMessage(),13,MUTED,false);passiveNote.setTag("passive-record-status");automation.addView(passiveNote);
        action(automation, "Chrome에서 지금 확인", false, () -> startActivity(new Intent(this, ChromeLaunchActivity.class)));
    }

    private String passiveRecordMessage(){
        if(!prefs.passiveRecords())return "자동출결을 끄면 직접 처리한 Chrome 출결 기록만 자동 저장해요.";
        return ChromeAttendanceService.available()?"기록 수집 중 · Chrome에서 완료 시각이 표시될 때까지 기다려 주세요. 자동 조작은 하지 않아요.":"기록 자동 저장에는 Chrome 접근성 연결이 필요해요. 예약·위치·Extend Unlock 설정은 필요 없어요.";
    }

    private void screenSettings(){
        LinearLayout box=settingsCard("출결 처리 화면", "screen", () -> "자동 처리 중에는 ‘출결 처리 중’ 화면이 보입니다. ‘Chrome 화면 보기’를 누르면 진행 화면을 확인할 수 있습니다. 완료 시각을 확인하면 화면을 끄고, 오류가 나면 진행 화면을 보여줍니다."
            + "\n\nChrome 처리 중에는 화면이 켜지고 잠금이 해제되어 있어야 합니다. 직접 실행하거나 기록만 조회할 때는 Chrome을 보여주며, 이미 완료된 기록만 조회하면 화면을 끄지 않습니다."
            + "\n\n잠금 모드를 사용하면 전원 메뉴에서 ‘잠금 모드’만 확인해 한 번 누릅니다. 다음 사용 때 지문 대신 PIN·패턴·비밀번호가 필요하며, Wi-Fi 재연결만으로 풀리지 않습니다. PIN 해제 후 신뢰 조건에서 Extend Unlock이 다시 적용될 수 있습니다(최대 4시간). 다음 출석 시각이 되어도 앱이 강한 잠금을 자동 해제할 수는 없습니다. 완료 후에는 다음 예약까지 앱이 화면을 깨우지 않으며, 직접 해제한 휴대폰을 강제로 다시 잠그지 않습니다."
            + "\n\n잠금 항목을 찾지 못하면 일반 화면 끄기로 마무리하고 결과를 기록합니다. 휴대폰에서 잠금 모드 표시를 켜야 할 수 있습니다. 완료 후 화면 끄기는 Android 9 이상에서 지원합니다.");
        box.setTag("setup-screen");
        Switch off=new Switch(this);off.setTag("attendance-screen-off");off.setText("입실·퇴실 완료 후 화면 끄기");off.setTextSize(16);off.setTextColor(INK);off.setMinHeight(dp(48));
        off.setChecked(prefs.screenOffAfterAttendance());off.setEnabled(Build.VERSION.SDK_INT>=28);box.addView(off);
        off.setOnCheckedChangeListener((v,yes)->prefs.raw.edit().putBoolean("screen_off_after_attendance",yes).apply());
        Switch lockdown=new Switch(this);lockdown.setTag("attendance-lockdown");lockdown.setText("완료 후 잠금 모드 · 다음 해제는 PIN");lockdown.setTextSize(15);lockdown.setTextColor(INK);lockdown.setMinHeight(dp(48));
        lockdown.setChecked(prefs.lockdownAfterAttendance());lockdown.setEnabled(prefs.screenOffAfterAttendance());box.addView(lockdown);
        lockdown.setOnCheckedChangeListener((v,yes)->prefs.raw.edit().putBoolean("lockdown_after_attendance",yes).apply());
        off.setOnCheckedChangeListener((v,yes)->{prefs.raw.edit().putBoolean("screen_off_after_attendance",yes).apply();lockdown.setEnabled(yes);});
        action(box,"잠금 모드만 테스트",false,()->new AlertDialog.Builder(this).setTitle("잠금 모드를 시험할까요?")
            .setMessage("실제 출결 없이 전원 메뉴의 잠금 모드를 실행합니다. 다음에 휴대폰을 열 때 PIN·패턴·비밀번호가 필요합니다.")
            .setNegativeButton("취소",null).setPositiveButton("테스트",(d,w)->{if(!ChromeAttendanceService.testLockdown(this))toast("접근성 연결을 확인하고 진행 중인 작업을 마친 뒤 다시 눌러 주세요.");}).show());
        lockdownResult=text(lockdownStatus(),13,MUTED,false);lockdownResult.setTag("lockdown-result");lockdownResult.setPadding(0,dp(10),0,dp(10));box.addView(lockdownResult);
        action(box,"잠금 항목이 안 보여요",false,()->new AlertDialog.Builder(this).setTitle("전원 메뉴에 잠금 모드 표시")
            .setMessage("갤럭시는 잠금화면 및 AOD(또는 잠금화면) → 보안 잠금 상세설정 → 잠금 모드 표시를 확인하세요. PIN 입력이 필요할 수 있고, 기기별로 메뉴가 다릅니다.\n\n다시 앱으로 돌아와 ‘잠금 모드만 테스트’를 누르면 실제 출결 없이 확인할 수 있어요.")
            .setNegativeButton("닫기",null).setPositiveButton("휴대폰 설정",(d,w)->{try{startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));}catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_SETTINGS));}}).show());
        Switch hide=new Switch(this);hide.setTag("attendance-processing-page");hide.setText("자동 처리 중 간단한 안내 화면 표시");hide.setTextSize(16);hide.setTextColor(INK);hide.setMinHeight(dp(48));
        hide.setChecked(prefs.showProcessingScreen());box.addView(hide);
        hide.setOnCheckedChangeListener((v,yes)->prefs.raw.edit().putBoolean("show_processing_screen",yes).apply());
        if(Build.VERSION.SDK_INT<28)note(box,"완료 후 화면 끄기는 Android 9 이상에서 지원합니다.");
    }

    private String lockdownStatus(){
        String mode=LockdownPolicy.resultText(prefs.raw.getString("lockdown_last_result",""));
        String screen=switch(prefs.raw.getString("screen_off_last_result","")){
            case "SCREEN_OFF_OBSERVED","ALREADY_OFF" -> "꺼짐 확인";
            case "SCREEN_OFF_UNCONFIRMED" -> "끄기 요청 후 꺼짐 미확인";
            case "SYSTEM_ACTION_REJECTED" -> "휴대폰이 화면 끄기 요청을 거절함";
            case "SCREEN_OFF_DISABLED" -> "설정이 꺼져 있어 실행하지 않음";
            case "RECORD_LOOKUP_NO_LOCK" -> "기록 조회만 하여 실행하지 않음";
            case "" -> "아직 확인 전";
            default -> "실행 오류 · 진단 로그 확인";
        };
        return "잠금 모드 · "+mode+"\n화면 끄기 · "+screen;
    }

    private void closeCreatorPanel(){
        creatorExpanded=false;
        if(creatorBody!=null)creatorBody.setVisibility(View.GONE);
        if(creatorToggle!=null){creatorToggle.setText("비밀번호로 열기  ▾");creatorToggle.setContentDescription("제작자 진단 잠김 · 비밀번호로 펼치기");}
    }
    private void creatorPanel(){
        if(!CreatorTools.enabled())return;
        LinearLayout diagnostics=card("진단 · 제작자용");diagnostics.setTag("creator-diagnostics");
        creatorToggle=button("비밀번호로 열기  ▾",false,()->{
            if(creatorExpanded){closeCreatorPanel();return;}
            EditText password=input("비밀번호 4자리","",false);password.setTag("creator-password");
            password.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            password.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
            password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            LinearLayout form=form();form.addView(password);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("제작자 진단 열기").setView(form).setNegativeButton("취소",null).setPositiveButton("열기",null).create();creatorPasswordDialog=dialog;
            dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{
                if(!"7671".contentEquals(password.getText())){password.setError("비밀번호가 맞지 않아요");password.setText("");return;}
                creatorExpanded=true;creatorBody.setVisibility(View.VISIBLE);creatorToggle.setText("진단 메뉴 접기  ▴");creatorToggle.setContentDescription("제작자 진단 펼쳐짐 · 눌러 접고 잠그기");password.setText("");dialog.dismiss();
            }));
            dialog.setOnDismissListener(d->{password.setText("");creatorPasswordDialog=null;});dialog.show();
        });creatorToggle.setTag("creator-toggle");diagnostics.addView(creatorToggle,new LinearLayout.LayoutParams(-1,-2));
        creatorBody=new LinearLayout(this);creatorBody.setOrientation(LinearLayout.VERTICAL);creatorBody.setTag("creator-body");diagnostics.addView(creatorBody);
        action(creatorBody,"로그 확인 · GitHub 업로드",true,()->startActivity(new Intent(this,LogUploadActivity.class)));
        if(CreatorTools.testsVisible(this)){
            action(creatorBody,"잠금 상태 테스트 열기",false,()->startActivity(new Intent(this,LockLabActivity.class)));
            action(creatorBody,"테스트 완료 · 임시 메뉴 숨기기",false,()->{CreatorTools.completeTests(this);render(selected);});
        }else{
            note(creatorBody,"임시 테스트 메뉴 숨김");
            action(creatorBody,"임시 테스트 메뉴 다시 켜기",false,()->{CreatorTools.enableTests(this);render(selected);});
        }
        if(creatorExpanded){creatorToggle.setText("진단 메뉴 접기  ▴");creatorBody.setVisibility(View.VISIBLE);}else closeCreatorPanel();
    }

    private void setupGuideCard(){
        LinearLayout guide=card("자동출석 준비 확인");guide.setTag("setup-readiness");
        guide.addView(text("필요한 설정만 자동으로 안내해요",19,INK,true));
        setupStatus=new SetupStatusView(this,true);guide.addView(setupStatus);
        Button start=button("빠른 설정 "+(prefs.raw.getBoolean("setup_visited",false)?"이어하기":"시작하기")+"  ›",true,()->SetupGuideActivity.open(this));start.setTag("setup-guide-open");gap(guide,10);guide.addView(start,new LinearLayout.LayoutParams(-1,-2));
    }
    private void automationSettings() {
        reservationSettings();
        LinearLayout schedule = settingsCard("출석 시간", "schedule", () -> "모든 시간은 한국 시간입니다. 입실 시간과 퇴실 시간을 눌러 각각 변경할 수 있습니다."
            + "\n\n입실 시간대에 등록 Wi-Fi의 인터넷 연결이 확인되면 바로 입실 확인을 시작합니다."
            + "\n\n" + getString(R.string.checkout_delay_notice,Policy.CHECKOUT_DELAY_SECONDS) + " 단축수업일은 시간표별 시각에 2초 여유를 적용합니다."
            + "\n\n자동 접속은 입실·퇴실 각 최대 3회입니다. 오류나 결과 확인 시간 초과 시 자동 재접속을 멈춥니다. 모든 사이트 열기는 최소 3초 간격, 하루 최대 20회입니다."
            + "\n\n입실·퇴실 완료를 확인하면 해당 감시를 종료합니다. 퇴실 재시도 시간 안에서, 퇴실 미확인 상태로 강의실을 벗어나면 알립니다."
            + "\n\n오늘 화면은 " + HomeTheme.nightStarts(prefs.end(), prefs.checkoutToday()) + "부터 야간 테마로 전환합니다.");
        schedule.setTag("setup-schedule");
        action(schedule, "입실 시간  ›\n" + prefs.start() + "–" + prefs.end(), false, () -> editSchedule(ScheduleSection.ENTRY));
        action(schedule, "기본 퇴실 시간  ›\n" + Policy.checkoutStartTime(prefs.checkout()) + " · 최대 " + prefs.retryMinutes() + "분 재시도", false, () -> editSchedule(ScheduleSection.CHECKOUT));
        action(schedule,"시간표별 출결 시간  ›",false,()->new AlertDialog.Builder(this).setTitle("시간표 자동 반영")
            .setMessage("휴강·공휴일은 자동 출결을 쉬어요.\n\n전체 기간의 6시간 수업: 16:00 종료 · 15:50:02 퇴실\n7시간 수업: 17:00 종료 · 16:50:02 퇴실\n\n단축수업 당일 입실 시작 시각에 한 번 안내하며, 오늘 탭에도 표시합니다. 안내 알림 설정을 따릅니다.\n\n단축수업일은 시간표의 퇴실 시간이 기본 설정보다 우선합니다. 입실 시간과 재시도 기간은 설정대로 유지됩니다.")
            .setPositiveButton("시간표 보기",(dialog,which)->startActivity(new Intent(this,TimetableActivity.class))).setNegativeButton("닫기",null).show());
        LinearLayout classDays = card("수업일");classDays.setTag("setup-days");
        String[] weekdayLabels = {"월","화","수","목","금","토","일"};
        List<String> weekdays = new ArrayList<>();
        for (int i=0; i<weekdayLabels.length; i++) if (prefs.days().contains(i+1)) weekdays.add(weekdayLabels[i]);
        action(classDays, "수업 요일 선택  ›\n" + (weekdays.isEmpty() ? "선택 필요" : String.join(" · ", weekdays)), false, () -> editSchedule(ScheduleSection.DAYS));
        action(classDays, "추가 휴강일 등록 (" + prefs.userExcluded().size() + "일)", false, this::editExcluded);
        LinearLayout wifi = settingsCard("출석에 사용할 Wi-Fi", "wifi", () -> "등록한 Wi-Fi에서 Chrome 출석을 처리합니다. 이름은 대소문자를 구분합니다.");
        wifi.setTag("setup-wifi");
        for (String ssid : prefs.ssids()) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(ssid, 15, INK, true); name.setMaxLines(2);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(button("삭제", false, () -> { Set<String> values = prefs.ssids(); values.remove(ssid); prefs.ssids(values); render(selected); }),
                new LinearLayout.LayoutParams(dp(72), dp(48))); wifi.addView(row);
        }
        action(wifi, "+ Wi-Fi 추가", true, this::addWifi);
        action(wifi, "현재 Wi-Fi 등록", false, () -> {
            String current = NetworkAccess.inspect(this).ssid();
            if (!Policy.validSsid(current)) { toast("Wi-Fi 이름을 확인할 수 없습니다. 직접 입력해 주세요"); return; }
            Set<String> values = prefs.ssids(); values.add(current); prefs.ssids(values); render(selected);
        });
        LinearLayout location = settingsCard("강의실 위치", "location", () -> "입실 시간에는 주변에서 Wi-Fi 미연결을 알리고, 퇴실 재시도 시간에는 미확인 상태로 강의실을 벗어나면 알립니다. GPS 오차가 큰 위치는 판단에 사용하지 않습니다.");
        location.setTag("setup-location");
        location.addView(text(prefs.hasLocation() ? "반경 " + prefs.radius() + "m" : "기본 반경 30m · 위치 미등록", 19, INK, true));
        action(location, "현재 위치로 등록", true, this::currentLocation);
        action(location, "좌표와 반경 변경", false, () -> editLocation(null));
        if (prefs.hasLocation()) action(location, "강의실 위치 삭제", false, () -> {
            prefs.raw.edit().remove("latitude").remove("longitude").apply(); Notices.wifi(this, false, ""); render(selected);
        });
        action(wifi, "인텔리전트 Wi-Fi 설정", false, () -> ChromeAttendanceService.intelligentWifi(this));
    }

    private void attendanceAddressSettings(){
        LinearLayout box=settingsCard("출석 주소", "attendance-address-help", () -> "Chrome 바로가기에서 사용하던 출석 주소를 붙여넣으세요. 현재 SK플래닛 출결·인증 페이지를 지원하며 HTTP 주소는 HTTPS로 바꿉니다.\n\n같은 휴대폰 Chrome의 기존 인증을 사용합니다. 주소 저장은 인증 완료를 뜻하지 않으며, Chrome에서 본인 출결 화면이 열리는지 확인해 주세요.\n\n수동 입실·퇴실은 자동출석 예약을 켜지 않아도 사용할 수 있어요. 앱의 버튼 자동 조작에는 Chrome 접근성·위치 권한과 지정 Wi-Fi가 필요합니다. Extend Unlock·정확한 알람·강의실 위치 등록은 수동 실행의 필수 조건이 아닙니다.");box.setTag("attendance-address");
        TextView address=text(prefs.shortcutAddress(),13,INK,false);address.setTextIsSelectable(true);address.setTag("attendance-address-value");box.addView(address);
        note(box,prefs.raw.contains("chrome_shortcut_url")?"주소 저장됨 · 인증 여부는 Chrome에서 확인":"기본 출석 주소 · 기존 Chrome 인증을 사용해요");
        action(box,"주소 붙여넣기 · 변경",false,this::editAttendanceAddress);
        action(box,"Chrome에서 출석 연결 확인",true,()->ChromeAttendanceService.openChrome(this,prefs.shortcutAddress()));
    }
    private void editAttendanceAddress(){
        LinearLayout body=form();EditText address=input("Chrome 바로가기 주소",prefs.shortcutAddress(),false);address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);address.setSelectAllOnFocus(true);address.setTag("attendance-address-input");body.addView(address);
        action(body,"클립보드에서 붙여넣기",false,()->{android.content.ClipboardManager clipboard=getSystemService(android.content.ClipboardManager.class);ClipData clip=clipboard.getPrimaryClip();if(clip==null||clip.getItemCount()==0){toast("복사된 주소가 없어요");return;}CharSequence value=clip.getItemAt(0).coerceToText(this);address.setText(value==null?"":value);});
        note(body,"주소를 저장한 뒤 Chrome에서 본인 출결 화면을 확인하세요. 예약은 자동으로 켜지지 않아요.");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("출석 주소 연결").setView(body).setNegativeButton("취소",null).setNeutralButton("저장만",null).setPositiveButton("저장 후 Chrome 열기",null).create();
        dialog.setOnShowListener(v->{for(int which:new int[]{AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_POSITIVE})dialog.getButton(which).setOnClickListener(b->{
            String url=SiteProtocol.shortcutAddress(address.getText().toString());
            if(url==null){address.setError("SK플래닛의 Present.asp 또는 PAuthWay.asp 주소를 붙여넣어 주세요");return;}
            if(!prefs.raw.edit().putString("chrome_shortcut_url",url).remove(SetupGuideActivity.CHROME).commit()){address.setError("주소를 저장하지 못했어요. 다시 시도해 주세요");return;}
            dialog.dismiss();render(selected);if(which==AlertDialog.BUTTON_POSITIVE)ChromeAttendanceService.openChrome(this,url);else toast("출석 주소를 저장했어요");
        });});dialog.show();
    }
    private void showUsageGuide() {
        LinearLayout body=form();
        body.addView(text("자동출석은 선택이에요",18,TEAL,true));
        note(body,"편한 방법으로 출석 메이트를 사용하세요.");
        gap(body,16);body.addView(text("① Extended Unlock으로 자동출석",16,INK,true));
        note(body,"예약 시간·출석 Wi-Fi에서 신뢰 상태로 잠금 해제가 가능하면 앱이 출석해요. PIN 재인증이 필요하면 직접 풀어 주세요.");
        gap(body,14);body.addView(text("② 직접 잠금해제 후 자동출석",16,INK,true));
        note(body,"Extended Unlock 없이도, 예약 시간·출석 Wi-Fi에서 직접 잠금을 풀면 앱이 출석해요.");
        gap(body,14);body.addView(text("③ 평소처럼 Chrome에서 직접 출석",16,INK,true));
        note(body,"자동출석 설정 없이 기존 바로가기로 입실·퇴실하세요. 앱에도 기록을 저장하려면 Chrome 접근성만 연결해 주세요.");
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        firstUseDialog=new AlertDialog.Builder(this).setTitle("출석 방법, 세 가지만 알아두세요")
            .setView(scroll).setPositiveButton("알겠어요",(d,w)->prefs.raw.edit().putBoolean("first_use_guide_pending",false).apply())
            .setNeutralButton("자동출석 설정",(d,w)->{prefs.raw.edit().putBoolean("first_use_guide_pending",false).apply();render(2);})
            .create();
        firstUseDialog.setCanceledOnTouchOutside(false);
        firstUseDialog.setOnCancelListener(d->prefs.raw.edit().putBoolean("first_use_guide_pending",false).apply());
        firstUseDialog.show();
    }

    private void settings() {
        LinearLayout usage=card("사용 안내");
        action(usage,"세 가지 출석 방법 보기",false,this::showUsageGuide);
        attendanceAddressSettings();
        LinearLayout notifications = settingsCard("알림", "notifications", () -> "완료를 확인할 때마다 입실과 퇴실 알림을 따로 남깁니다. 앱을 열거나 감시가 끝나도 유지되며 직접 지울 수 있습니다."
            + "\n\n휴대폰에서 알림이 차단된 경우 앱 알림과 ‘입실·퇴실 결과’ 알림을 허용해 주세요. ‘앱 알림 설정’에서 안내 알림과 현재 허용 상태를 확인할 수 있습니다.");
        notifications.setTag("general-notifications");
        notifications.addView(text("입실 · 퇴실 완료 알림 항상 표시",17,INK,true));
        note(notifications,Notices.attendanceNotificationsAllowed(this) ? "완료 알림 허용됨" : "알림 차단됨 · 설정 필요");
        action(notifications,"앱 알림 설정",false,() -> PhoneSettings.open(this,PhoneSettings.Target.NOTIFICATIONS));
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
            prefs.ssids(values); prefs.log("Wi-Fi 추가"); dialog.dismiss(); render(selected);
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
                prefs.location(a, o, r); prefs.log("강의실 위치 등록"); dialog.dismiss(); render(selected);
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
            note(view,getString(R.string.checkout_delay_notice,Policy.CHECKOUT_DELAY_SECONDS) + " 단축수업일은 시간표별 시각에 2초 여유를 적용합니다.");
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
            org.json.JSONObject before=DiagnosticLog.scheduleDetails(prefs);
            boolean changed=switch(section){
                case ENTRY -> !entryStart.equals(prefs.start())||!entryEnd.equals(prefs.end());
                case CHECKOUT -> !checkout.equals(prefs.checkout())||minutes!=prefs.retryMinutes();
                case DAYS -> !chosen.equals(prefs.raw.getStringSet("days",Set.of()));
            };
            SharedPreferences.Editor edit = prefs.raw.edit();
            switch (section) {
                case ENTRY -> edit.putString("start",entryStart).putString("end",entryEnd);
                case CHECKOUT -> edit.putString("checkout",checkout).putInt("checkout_retry_minutes",minutes);
                case DAYS -> edit.putStringSet("days",chosen);
            }
            edit.apply(); ScheduleController.reconcile(this);
            if(changed)DiagnosticLog.scheduleChanged(this,section.name(),before);
            dialog.dismiss(); render(selected);
        }));
        dialog.show();
    }
    private void editExcluded() {
        Set<String> values = new TreeSet<>(prefs.userExcluded());
        LinearLayout view = form(), dates = new LinearLayout(this); dates.setOrientation(LinearLayout.VERTICAL);
        note(view,"공휴일·대체공휴일과 과정명세서의 휴강일은 자동 제외됩니다. 추가 휴강일만 골라 저장해 주세요.");
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
                prefs.log("휴강일 변경"); ScheduleController.reconcile(this); render(selected);
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

    void refreshAutomaticSettings(){ if(!isFinishing()&&!isDestroyed())render(selected); }

    private void connection() {
        setupGuideCard();
        automationSettings();
        LinearLayout chrome = settingsCard("Chrome 인증", "chrome-auth", () -> "휴대폰 Chrome에 저장된 인증을 그대로 사용합니다. 이미 출결 화면이 열리면 다시 인증하지 않아도 됩니다.");chrome.setTag("setup-chrome");
        chrome.addView(text(ChromeAttendanceService.available() ? "Chrome 접근성 연결됨" : "Chrome 접근성을 켜 주세요",18,INK,true));
        action(chrome,"Chrome 출결 화면 열기",true,() -> ChromeAttendanceService.openChrome(this,SiteProtocol.PAGE));
        action(chrome,"Chrome 인증 페이지 열기",false,() -> ChromeAttendanceService.openChrome(this,SiteProtocol.AUTH_PAGE));
        long next=prefs.raw.getLong("next_alarm",0);
        note(chrome,next>0?"다음 예약: "+Instant.ofEpochMilli(next).atZone(Policy.ZONE).format(DateTimeFormatter.ofPattern("M/d HH:mm")):"출석 예약을 켜면 수업일에 맞춰 실행해요.");
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
        screenSettings();
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
