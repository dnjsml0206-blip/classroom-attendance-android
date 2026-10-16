package net.tacademy.attendance;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Guided setup only: opening steps never changes permissions, trust, or attendance reservations. */
public final class SetupGuideActivity extends Activity {
    private static final int INK=0xFF183631,MUTED=0xFF687D77,TEAL=0xFF007C78,BG=0xFFF4F7F6,AMBER=0xFF986019;
    static final int REQUEST=72;
    static final String CHROME="setup_chrome_confirmed",EXTEND="setup_extend_confirmed";
    private static final String[] NAMES={"Chrome 연결","버튼 자동 클릭","예약 실행 권한","잠금 중 처리","최종 확인"};
    private Prefs prefs;private int step;private LinearLayout root,body;private ScrollView scroll;
    private boolean quickMode=true,quickStarted,resumed,quickQueued,permissionPending,chromeOpened,extendOpened;
    private SetupGuidePolicy.QuickAction quickPending;
    private final Runnable quickContinue=()->{quickQueued=false;if(quickMode&&quickStarted&&resumed)performQuick(true);};
    private final Handler handler=new Handler(Looper.getMainLooper());private String lastState="";
    private final Runnable refresh=new Runnable(){public void run(){String now=fingerprint();if(!now.equals(lastState))render(true);maybeAdvanceQuick();handler.postDelayed(this,1500);}};
    static SetupGuidePolicy.Snapshot snapshot(Context c){
        Prefs p=new Prefs(c);boolean fine=c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        return new SetupGuidePolicy.Snapshot(p.raw.getBoolean(CHROME,false),!p.ssids().isEmpty(),!p.days().isEmpty()&&Policy.validSchedule(p.start(),p.end(),p.checkout(),p.retryMinutes()),
            ChromeAttendanceService.available(),fine,ScheduleController.backgroundLocationAllowed(c),Build.VERSION.SDK_INT<28||c.getSystemService(LocationManager.class).isLocationEnabled(),
            AlarmScheduler.allowed(c),Notices.attendanceNotificationsAllowed(c),c.getSystemService(KeyguardManager.class).isDeviceSecure(),
            p.raw.getBoolean(EXTEND,false)&&ScheduledUnlock.mode(c)==UnlockPolicy.Mode.TRUST,p.enabled(),p.auto(),p.useExtendUnlock());
    }
    static void open(Activity a){a.startActivityForResult(new Intent(a,SetupGuideActivity.class),REQUEST);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);prefs=new Prefs(this);Notices.channels(this);
        step=saved!=null?saved.getInt("step",0):prefs.raw.getInt("setup_step",SetupGuidePolicy.nextStep(snapshot(this)));step=Math.max(0,Math.min(4,step));
        if(getIntent().hasExtra("step"))step=Math.max(0,Math.min(4,getIntent().getIntExtra("step",0)));
        quickMode=saved!=null?saved.getBoolean("quick_mode",true):!getIntent().hasExtra("step");
        if(saved!=null){quickStarted=saved.getBoolean("quick_started",false);chromeOpened=saved.getBoolean("chrome_opened",false);extendOpened=saved.getBoolean("extend_opened",false);String pending=saved.getString("quick_pending","");try{quickPending=SetupGuidePolicy.QuickAction.valueOf(pending);}catch(IllegalArgumentException ignored){}}
        prefs.raw.edit().putBoolean("setup_visited",true).apply();
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v,i)->{if(Build.VERSION.SDK_INT>=30){var b=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);}else v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        setContentView(root);render(false);
    }
    @Override protected void onResume(){super.onResume();resumed=true;ChromeAttendanceService.cancelSettingsMenu();ChromeAttendanceService.returnedToAttendanceApp();render(true);handler.postDelayed(refresh,1500);}
    @Override protected void onPause(){resumed=false;quickQueued=false;handler.removeCallbacksAndMessages(null);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle b){b.putInt("step",step);b.putBoolean("quick_mode",quickMode);b.putBoolean("quick_started",quickStarted);b.putBoolean("chrome_opened",chromeOpened);b.putBoolean("extend_opened",extendOpened);if(quickPending!=null)b.putString("quick_pending",quickPending.name());super.onSaveInstanceState(b);}
    private String fingerprint(){return snapshot(this).toString()+getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName())+prefs.raw.getBoolean("setup_battery_confirmed",false)+prefs.hasLocation()+prefs.ssids()+prefs.start()+prefs.end()+prefs.checkout()+prefs.lockdownAfterAttendance()+prefs.screenOffAfterAttendance();}
    private void go(int value){step=Math.max(0,Math.min(4,value));prefs.raw.edit().putInt("setup_step",step).apply();render(false);}
    private void render(boolean preserve){
        if(root==null)return;if(quickMode){renderQuick(preserve);return;}int y=preserve&&scroll!=null?scroll.getScrollY():0;lastState=fingerprint();root.removeAllViews();var state=snapshot(this);boolean[] complete=SetupGuidePolicy.completed(state);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(12),dp(8),dp(16),dp(4));
        top.addView(button("‹",false,this::finish,"setup-close"),new LinearLayout.LayoutParams(dp(48),dp(48)));
        top.addView(text("자동출석 시작하기",22,INK,true),new LinearLayout.LayoutParams(0,-2,1));root.addView(top);
        Button quick=button("빠른 설정으로 돌아가기",false,()->{quickMode=true;render(false);},"setup-quick-return");root.addView(quick,new LinearLayout.LayoutParams(-1,-2));
        TextView progress=text("확인 "+SetupGuidePolicy.completedCount(state)+" / 5단계 · 나중에 이어서 할 수 있어요",12,MUTED,false);progress.setTag("setup-progress");progress.setPadding(dp(24),dp(6),dp(24),dp(12));root.addView(progress);
        LinearLayout steps=new LinearLayout(this);steps.setPadding(dp(18),0,dp(18),dp(12));
        for(int i=0;i<5;i++){final int index=i;Button b=button((complete[i]?"✓ ":"")+(i+1),i==step,()->go(index),"setup-step-"+i);b.setContentDescription((i+1)+"단계 "+NAMES[i]+(complete[i]?" 확인됨":" 미확인"));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(3),0,dp(3),0);steps.addView(b,lp);}root.addView(steps);
        scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(22),dp(12),dp(22),dp(20));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView title=text((step+1)+". "+NAMES[step],26,INK,true);title.setTag("setup-title");body.addView(title);space(body,8);
        String[] leads={"평소 쓰던 Chrome 인증을 그대로 연결해요.","앱이 Chrome의 입실·퇴실 버튼을 누를 수 있게 해요.","설정 화면에서 선택하고 돌아오면 자동으로 확인해요.","Extend Unlock 사용 여부를 선택하세요. 사용하지 않아도 예약할 수 있어요.","마지막으로 확인하고 출석 예약을 켜세요."};body.addView(text(leads[step],14,MUTED,false));space(body,16);
        switch(step){case 0->connection(state);case 1->access(state);case 2->permissions(state);case 3->trust(state);default->finishSetup(state);}
        LinearLayout bottom=new LinearLayout(this);bottom.setPadding(dp(18),dp(10),dp(18),dp(10));bottom.setBackgroundColor(0xFFFFFFFF);
        Button prev=button(step==0?"나중에":"이전",false,()->{if(step==0)finish();else go(step-1);},"setup-prev");bottom.addView(prev,new LinearLayout.LayoutParams(0,dp(52),1));
        Button next=button(step<4?"다음 단계":state.enabled()&&state.autoClick()&&SetupGuidePolicy.readyToEnable(state)?"오늘 화면으로":"설정 확인 후 예약 켜기",true,()->{if(step<4)go(step+1);else begin();},"setup-next");LinearLayout.LayoutParams nextSize=new LinearLayout.LayoutParams(0,-2,2);nextSize.leftMargin=dp(10);bottom.addView(next,nextSize);root.addView(bottom);
        scroll.post(()->scroll.scrollTo(0,y));
    }
    private SetupGuidePolicy.QuickAction quickAction(){return SetupGuidePolicy.nextQuick(snapshot(this),ScheduledUnlock.mode(this)==UnlockPolicy.Mode.TRUST);}
    private void renderQuick(boolean preserve){
        int y=preserve&&scroll!=null?scroll.getScrollY():0;lastState=fingerprint();root.removeAllViews();var state=snapshot(this);var action=quickAction();
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(12),dp(8),dp(16),dp(8));top.addView(button("‹",false,this::finish,"setup-close"),new LinearLayout.LayoutParams(dp(48),dp(48)));top.addView(text("자동출석 빠른 설정",22,INK,true),new LinearLayout.LayoutParams(0,-2,1));root.addView(top);
        scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(22),dp(12),dp(22),dp(20));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView title=text(quickStarted?quickTitle(action):"한 번 시작하면\n필요한 설정만 이어서",26,INK,true);title.setTag("setup-title");body.addView(title);space(body,10);
        TextView progress=text("확인 "+SetupGuidePolicy.completedCount(state)+" / 5단계 · 완료된 설정은 건너뛰어요",13,TEAL,true);progress.setTag("setup-progress");body.addView(progress);space(body,16);
        body.addView(new ExtendUnlockSettings(this,()->render(false)));
        if(!quickStarted){
            LinearLayout intro=card("설정 화면을 찾아다닐 필요 없어요","기존 설정 그대로 유지",true);
            note(intro,"시작하면 필요한 권한 화면만 순서대로 열어요. 허용 후 돌아오면 다음 설정으로 이어집니다.");
            note(intro,"휴대폰 권한은 직접 허용해야 해요. Chrome 인증과 선택한 Extend Unlock은 마지막에 직접 확인하며, 예약은 최종 확인 전까지 켜지지 않아요.");
            note(intro,"등록 Wi-Fi: "+(prefs.ssids().isEmpty()?"등록 필요":String.join(" · ",prefs.ssids()))+"\n입실 "+prefs.start()+"–"+prefs.end()+" · 기본 퇴실 "+Policy.checkoutStartTime(prefs.checkout()));
        }else{
            LinearLayout current=card(quickTitle(action),quickPending==action?"확인 중 · 미허용이면 다시 눌러 주세요":action==SetupGuidePolicy.QuickAction.CHROME||action==SetupGuidePolicy.QuickAction.EXTEND?"직접 확인 필요":"지금 필요한 설정",false);
            note(current,quickDescription(action));
            if(action==SetupGuidePolicy.QuickAction.EXTEND)ExtendUnlockSetupHelp.addSteps(this,current);
            if(action==SetupGuidePolicy.QuickAction.CHROME||action==SetupGuidePolicy.QuickAction.EXTEND){
                if(action==SetupGuidePolicy.QuickAction.CHROME?!chromeOpened:!extendOpened)add(current,"이미 설정했어요 · 직접 확인",false,()->confirmQuickManual(action),"setup-quick-already");
                if(action==SetupGuidePolicy.QuickAction.CHROME&&chromeOpened)add(current,"Chrome 다시 열기",false,()->openQuickChrome(),"setup-quick-reopen");
                if(action==SetupGuidePolicy.QuickAction.EXTEND&&extendOpened)add(current,"Extend Unlock 다시 열기",false,()->openQuickExtend(),"setup-quick-reopen");
            }
            if(action==SetupGuidePolicy.QuickAction.ACCESSIBILITY)add(current,"제한된 설정으로 막혔어요",false,()->help("접근성 연결 확인","설정 → 앱 → 출석 메이트 → 더보기(⋮) → 제한된 설정 허용 후 접근성으로 돌아가 사용을 켜세요. 직접 설치한 앱을 신뢰하는 경우에만 허용하세요. 메뉴는 기기마다 다릅니다. 사용을 켠 뒤 연결까지 잠시 걸릴 수 있어요."),null);
            if(action==SetupGuidePolicy.QuickAction.RESERVATION){
                note(current,"입실 "+prefs.start()+"–"+prefs.end()+" · 기본 퇴실 "+Policy.checkoutStartTime(prefs.checkout())+"\n휴강·단축수업은 시간표를 반영해요.");
                note(current,"Chrome·Extend Unlock은 사용자 확인이며 실제 자동출석 성공을 보장하지 않아요.");
                add(current,"권장 설정도 확인 · 배터리 등",false,()->showDetailed(2),"setup-quick-recommended");
            }
        }
        add(body,"자세한 설정 가이드",false,()->showDetailed(step),"setup-details");
        LinearLayout bottom=new LinearLayout(this);bottom.setPadding(dp(18),dp(10),dp(18),dp(10));bottom.setBackgroundColor(0xFFFFFFFF);
        Button primary=button(!quickStarted?"필요한 설정 자동 안내 시작":quickButton(action,state),true,()->{if(!quickStarted){if(busy())return;quickStarted=true;render(false);queueQuick();}else performQuick(false);},"setup-quick-primary");bottom.addView(primary,new LinearLayout.LayoutParams(-1,-2));root.addView(bottom);scroll.post(()->scroll.scrollTo(0,y));
    }
    private String quickTitle(SetupGuidePolicy.QuickAction a){return switch(a){
        case WIFI->"출석 Wi-Fi 등록";case SCHEDULE->"수업 일정 확인";case PRECISE_LOCATION->"정확한 위치 허용";case BACKGROUND_LOCATION->"위치를 항상 허용";case LOCATION_SERVICE->"휴대폰 위치 켜기";case ACCESSIBILITY->"Chrome 자동 클릭 연결";case ALARMS->"예약 알람 허용";case NOTIFICATIONS->"출결 알림 허용";case SECURE_LOCK->"휴대폰 잠금 설정";case TRUST_MODE->"앱의 잠금 처리 방식";case EXTEND->"Extend Unlock 확인";case CHROME->"기존 Chrome 인증 확인";case RESERVATION->"설정 준비가 끝났어요";};}
    private String quickDescription(SetupGuidePolicy.QuickAction a){return switch(a){
        case WIFI->"등록한 Wi-Fi가 없어요. 출석에 사용할 이름을 추가해 주세요. 현재 연결돼 있지 않아도 됩니다.";
        case SCHEDULE->"수업 요일과 입실·퇴실 시간을 확인해 주세요. 이미 저장된 값은 바꾸지 않아요.";
        case PRECISE_LOCATION->"Wi-Fi 이름을 읽을 수 있도록 ‘정확한 위치’를 허용하세요. 필요한 경우 알림 권한도 이어서 요청합니다.";
        case BACKGROUND_LOCATION->"앱 정보 → 권한 → 위치에서 ‘항상 허용’을 선택하세요. 돌아오면 자동으로 다음 설정을 확인해요.";
        case LOCATION_SERVICE->"휴대폰의 위치 기능을 켜 주세요. Wi-Fi 이름 확인에 필요해요.";
        case ACCESSIBILITY->"접근성 → 설치된 앱 → 출석 메이트 · Chrome 출석 → 사용을 켜 주세요. 돌아오면 실제 연결을 확인해요.";
        case ALARMS->"알람 및 리마인더를 허용하세요. 지정한 시간에 출석 감시를 시작하는 데 필요해요.";
        case NOTIFICATIONS->"입실·퇴실 결과를 남길 수 있도록 알림을 허용하세요. 이미 차단한 권한·채널은 앱 알림 화면의 안내를 확인하세요.";
        case SECURE_LOCK->"PIN·패턴·비밀번호 잠금을 설정하세요. Extend Unlock을 사용하려면 먼저 휴대폰 잠금이 필요해요.";
        case TRUST_MODE->"현재 앱의 처리 방식은 ‘"+ScheduledUnlock.mode(this).label+"’입니다. 빠른 설정은 Extend Unlock 방식을 사용해요. 아래 버튼을 눌렀을 때만 앱의 방식을 바꿉니다.";
        case EXTEND->"Extend Unlock에 강의실이나 신뢰하는 Bluetooth 기기를 등록하고 PIN 등으로 한 번 해제하세요. 앱은 설정 여부를 직접 읽을 수 없어 직접 확인이 필요해요.\n\n신뢰 상태는 최대 4시간이며, 완료 후 잠금 모드를 사용하면 다음에는 PIN 등으로 한 번 해제해야 해요.";
        case CHROME->"평소 사용하는 Chrome에서 본인 이름과 출결 화면이 보이는지 확인하세요. 이미 인증했다면 다시 열지 않고 직접 확인할 수 있어요. 앱은 로그인 상태를 자동 확인하지 않아요.";
        case RESERVATION->"필요한 설정을 확인했어요. 예약을 켜면 자동 클릭도 켜지고, 현재 시간·Wi-Fi 조건이 맞으면 5초 뒤 실제 미처리 출결을 시도할 수 있어요.";
    };}
    private String quickButton(SetupGuidePolicy.QuickAction a,SetupGuidePolicy.Snapshot s){return switch(a){
        case WIFI->"Wi-Fi 등록하기";case SCHEDULE->"수업 일정 확인하기";case PRECISE_LOCATION->"필요한 권한 허용하기";case BACKGROUND_LOCATION->"항상 허용 설정 열기";case LOCATION_SERVICE->"위치 설정 열기";case ACCESSIBILITY->"Chrome 접근성 연결하기";case ALARMS->"예약 알람 허용하기";case NOTIFICATIONS->"알림 허용하기";case SECURE_LOCK->"휴대폰 잠금 설정 열기";case TRUST_MODE->"Extend Unlock 방식으로 선택";case EXTEND->extendOpened?"신뢰 설정을 직접 확인했어요":"Extend Unlock 설정 열기";case CHROME->chromeOpened?"내 이름과 출결 화면을 확인했어요":"Chrome에서 인증 확인";case RESERVATION->s.enabled()&&s.autoClick()?"오늘 화면으로":"예약 켜기 · 마지막 확인";};}
    private void showDetailed(int at){quickMode=false;quickPending=null;quickQueued=false;handler.removeCallbacks(quickContinue);go(at);}
    private void queueQuick(){if(quickQueued)return;quickQueued=true;handler.postDelayed(quickContinue,300);}
    private void maybeAdvanceQuick(){
        if(!quickMode||!quickStarted||!resumed||permissionPending||quickPending==null||!getWindow().getDecorView().hasWindowFocus())return;
        if(!SetupGuidePolicy.canAutoAdvance(quickPending,snapshot(this),true))return;
        quickPending=null;render(false);queueQuick();
    }
    private void performQuick(boolean automatic){
        if(!quickMode||!quickStarted||permissionPending)return;
        var a=quickAction();
        boolean notificationGranted=Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
        if(!SetupGuidePolicy.canLaunchQuick(a,automatic,prefs.raw.getBoolean("notification_permission_requested",false),notificationGranted)){render(false);return;}
        if(busy())return;
        switch(a){
            case WIFI->{quickPending=a;setting("setup-wifi");}
            case SCHEDULE->{quickPending=a;setting("setup-schedule");}
            case PRECISE_LOCATION->{quickPending=a;requestQuickPermissions(true);}
            case BACKGROUND_LOCATION->{quickPending=a;open(PhoneSettings.Target.LOCATION_PERMISSION);}
            case LOCATION_SERVICE->{quickPending=a;open(PhoneSettings.Target.LOCATION_SERVICE);}
            case ACCESSIBILITY->{quickPending=a;open(PhoneSettings.Target.ACCESSIBILITY);}
            case ALARMS->{quickPending=a;open(PhoneSettings.Target.ALARMS);}
            case NOTIFICATIONS->{quickPending=a;requestQuickPermissions(false);}
            case SECURE_LOCK->{quickPending=a;try{startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));}catch(ActivityNotFoundException|SecurityException e){Toast.makeText(this,"휴대폰 설정에서 PIN·패턴·비밀번호 잠금을 설정해 주세요",Toast.LENGTH_LONG).show();}}
            case TRUST_MODE->{ScheduledUnlock.cancel(this,"SETUP_MODE_CHANGED");prefs.raw.edit().putString("scheduled_lock_mode","TRUST").apply();render(false);queueQuick();}
            case EXTEND->{if(extendOpened)confirmQuickManual(a);else openQuickExtend();}
            case CHROME->{if(chromeOpened)confirmQuickManual(a);else openQuickChrome();}
            case RESERVATION->begin();
        }
    }
    private void requestQuickPermissions(boolean location){
        if(location){LocationDisclosure.show(this,()->requestQuickPermissionsAfterDisclosure(true));return;}
        requestQuickPermissionsAfterDisclosure(false);
    }
    private void requestQuickPermissionsAfterDisclosure(boolean location){
        List<String> wanted=new ArrayList<>();
        if(location){
            if(prefs.raw.getBoolean("setup_location_requested",false)&&!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)){open(PhoneSettings.Target.LOCATION_PERMISSION);return;}
            wanted.add(Manifest.permission.ACCESS_COARSE_LOCATION);wanted.add(Manifest.permission.ACCESS_FINE_LOCATION);prefs.raw.edit().putBoolean("setup_location_requested",true).apply();
        }
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
            &&(!prefs.raw.getBoolean("notification_permission_requested",false)||shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))){wanted.add(Manifest.permission.POST_NOTIFICATIONS);prefs.raw.edit().putBoolean("notification_permission_requested",true).apply();}
        if(wanted.isEmpty()){open(PhoneSettings.Target.NOTIFICATIONS);return;}
        permissionPending=true;requestPermissions(wanted.toArray(new String[0]),42);
    }
    private void openQuickChrome(){if(busy())return;if(ChromeAttendanceService.openChrome(this,SiteProtocol.PAGE)){chromeOpened=true;render(false);}}
    private void openQuickExtend(){if(busy())return;extendOpened=true;open(PhoneSettings.Target.EXTEND);render(false);}
    private void confirmQuickManual(SetupGuidePolicy.QuickAction a){
        if(quickAction()!=a||busy())return;
        // The labelled user action is the confirmation. Never infer it from opening or returning.
        prefs.raw.edit().putBoolean(a==SetupGuidePolicy.QuickAction.CHROME?CHROME:EXTEND,true).apply();
        render(false);queueQuick();
    }
    private void connection(SetupGuidePolicy.Snapshot s){
        LinearLayout chrome=card("평소 Chrome에서 출결 화면 확인",s.chromeConfirmed()?"직접 확인함":"직접 확인 필요",s.chromeConfirmed());
        add(chrome,"기존 출석 주소 붙여넣기",false,()->startActivity(new Intent(this,MainActivity.class).putExtra("open_tab",3).putExtra("focus_setting","attendance-address")),"setup-shortcut-address");
        note(chrome,"본인 이름과 오늘 출결 화면이 보이는지 확인한 뒤 앱으로 돌아오세요.");
        add(chrome,"Chrome 출결 화면 열기",true,()->{if(!busy())ChromeAttendanceService.openChrome(this,SiteProtocol.PAGE);},"setup-chrome-open");
        check(chrome,"내 이름과 출결 화면을 확인했어요",CHROME,true);
        add(chrome,"인증 화면이 나와요",false,()->help("Chrome 인증","이미 사용하던 Chrome 일반 탭으로 열립니다. 인증 화면이 나오면 강의실 Wi-Fi에서 기존 인증 절차를 마친 뒤 돌아오세요. 앱은 Chrome 로그인 상태를 직접 확인할 수 없어 직접 확인 표시로 관리합니다."),null);
        LinearLayout wifi=card("출석 Wi-Fi",s.wifiRegistered()?"등록됨":"등록 필요",s.wifiRegistered());
        note(wifi,prefs.ssids().isEmpty()?"사용할 Wi-Fi를 등록해 주세요.":String.join(" · ",prefs.ssids()));
        note(wifi,"집에서는 연결하지 않아도 준비할 수 있어요. 강의실에서는 휴대폰에도 이 Wi-Fi를 저장하고 자동 연결을 켜 주세요.");
        add(wifi,"Wi-Fi 등록 · 변경",false,()->setting("setup-wifi"),"setup-wifi");
        add(wifi,"wi-fi 자동설정 ON",false,()->{if(!busy())ChromeAttendanceService.intelligentWifi(this);},null);
        LinearLayout schedule=card("수업 시간",s.scheduleValid()?"일정 확인됨":"일정 필요",s.scheduleValid());note(schedule,"입실 "+prefs.start()+"–"+prefs.end()+" · 기본 퇴실 "+Policy.checkoutStartTime(prefs.checkout()));note(schedule,"휴강·공휴일은 쉬고 단축수업일에는 시간표의 퇴실 시각을 사용해요.");add(schedule,"시간 · 수업일 확인",false,()->setting("setup-schedule"),null);
    }
    private void access(SetupGuidePolicy.Snapshot s){
        LinearLayout box=card("Chrome 접근성",s.accessibility()?"자동 확인 · 연결됨":ChromeAttendanceService.configured(this)?"켜짐 · 연결 대기":"설정 필요",s.accessibility());
        note(box,"접근성 → 설치된 앱 → 출석 메이트 · Chrome 출석 → 사용을 켜세요.");
        add(box,"Chrome 접근성 설정 열기",true,()->open(PhoneSettings.Target.ACCESSIBILITY),"setup-accessibility");
        note(box,"앱으로 돌아왔을 때 ‘연결됨’이면 준비됐어요. 자동 클릭은 마지막 단계에서 예약과 함께 켜집니다.");
        add(box,"항목이 없거나 연결이 안 돼요",false,()->help("접근성 연결 확인","설정에 출석 메이트가 보이는지 확인하세요. 사용을 켰는데 연결 대기라면 잠시 기다린 뒤 상태를 다시 확인하세요. ‘제한된 설정’으로 막혔다면 설치한 APK를 신뢰하는지 먼저 확인하세요. 해당 메뉴가 있는 기기는 설정 → 앱 → 출석 메이트 → 더보기(⋮) → 제한된 설정 허용 후 접근성으로 돌아가 사용을 켜세요. 기기별로 표시 여부가 다릅니다. 접근성 연결은 앱이 화면을 읽고 버튼을 누르는 데 사용합니다."),null);
    }
    private void permissions(SetupGuidePolicy.Snapshot s){
        LinearLayout location=card("위치 · 정확하게, 항상 허용",s.preciseLocation()&&s.backgroundLocation()?"자동 확인 · 완료":"설정 필요",s.preciseLocation()&&s.backgroundLocation());
        note(location,"예약 시간에 Wi-Fi 이름을 확인하는 데 필요해요.");
        add(location,s.preciseLocation()?"① 정확한 위치 허용됨":"① 정확한 위치 허용",!s.preciseLocation(),this::requestLocation,"setup-location-runtime");
        add(location,"② 위치 → 항상 허용 확인",false,()->open(PhoneSettings.Target.LOCATION_PERMISSION),null);
        if(!s.locationOn())add(location,"③ 휴대폰 위치 기능 켜기",true,()->open(PhoneSettings.Target.LOCATION_SERVICE),null);else note(location,"✓ 휴대폰 위치 켜짐");
        LinearLayout alarm=card("알람 및 리마인더",s.alarms()?"자동 확인 · 허용됨":"허용 필요",s.alarms());note(alarm,"설정한 시각에 앱이 실행될 수 있게 허용하세요.");add(alarm,"예약 알람 설정 열기",false,()->open(PhoneSettings.Target.ALARMS),"setup-alarms");
        LinearLayout notifications=card("입실·퇴실 결과 알림",s.notifications()?"자동 확인 · 허용됨":"허용 필요",s.notifications());add(notifications,"앱 안에서 알림 설정",false,()->open(PhoneSettings.Target.NOTIFICATIONS),"setup-notifications");
        boolean battery=getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());boolean owner=prefs.raw.getBoolean("setup_battery_confirmed",false);
        LinearLayout power=card("배터리 제한 해제 · 권장",battery?"자동 확인 · 최적화 제외":owner?"직접 확인함":"휴대폰에서 확인",battery||owner);
        note(power,"앱 정보 → 배터리 → 제한 없음(기기별 명칭이 달라요). 갤럭시는 절전 앱 목록에 포함됐는지도 확인하세요.");add(power,"앱 배터리 설정 열기",false,()->open(PhoneSettings.Target.BATTERY),null);
        if(!battery)check(power,"휴대폰에서 배터리 제한을 확인했어요","setup_battery_confirmed",true);
    }
    private void trust(SetupGuidePolicy.Snapshot s){
        body.addView(new ExtendUnlockSettings(this,()->render(false)));
        if(!prefs.useExtendUnlock())return;
        LinearLayout box=card("Extend Unlock · 예전 Smart Lock",s.extendConfirmed()?"직접 확인함":"직접 설정 필요",s.extendConfirmed());
        add(box,"그림으로 설정하는 법 보기",false,()->ExtendUnlockSetupHelp.show(this),"setup-extend-howto");
        note(box,"1  PIN·패턴·비밀번호 잠금을 설정하세요.\n2  Extend Unlock에서 강의실을 ‘신뢰할 수 있는 장소’로 등록하세요.\n3  PIN 등으로 한 번 해제한 뒤 신뢰 장소에서 시험하세요.");
        note(box,"신뢰하는 Bluetooth 기기를 사용할 수도 있어요. Wi-Fi 등록과 신뢰 장소 등록은 별개예요.");
        add(box,"Extend Unlock 설정 열기",true,()->open(PhoneSettings.Target.EXTEND),"setup-extend-open");
        if(!s.secureLock())add(box,"먼저 휴대폰 잠금 설정",false,()->startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)),"setup-secure-lock");else note(box,"✓ PIN·패턴·비밀번호 잠금 설정됨");
        if(ScheduledUnlock.mode(this)!=UnlockPolicy.Mode.TRUST)add(box,"앱 처리 방식도 Extend Unlock으로 선택",false,()->{if(busy())return;ScheduledUnlock.cancel(this,"SETUP_MODE_CHANGED");prefs.raw.edit().putString("scheduled_lock_mode","TRUST").apply();render(true);},"setup-trust-mode");else note(box,"✓ 앱 처리 방식: Extend Unlock · 신뢰 상태");
        check(box,"신뢰 장소·기기를 등록하고 한 번 잠금 해제했어요",EXTEND,s.secureLock()&&ScheduledUnlock.mode(this)==UnlockPolicy.Mode.TRUST);
        note(box,"이 표시는 사용자 확인이에요. 앱이 Extend Unlock의 실제 활성 여부를 읽을 수는 없어요.");
        add(box,"메뉴를 못 찾겠어요",false,()->help("Extend Unlock 찾기","갤럭시: 설정 → 잠금화면 및 AOD(또는 잠금화면) → 잠금 해제 유지 → 신뢰할 수 있는 장소. 다른 Android: 보안 및 개인 정보 보호 → 보안 설정 더보기 → Extend Unlock. 설정 검색에서 ‘Extend Unlock’ 또는 ‘Smart Lock’을 찾아보세요. 제조사·Android 버전별로 메뉴가 다를 수 있어요."),null);
        LinearLayout lock=card("이 두 가지는 기억해 주세요","사용 전 확인",false);
        note(lock,"신뢰 상태는 최대 4시간 유지돼요. PIN 재인증이 필요하면 자동출석을 이어갈 수 없어요.");
        note(lock,prefs.screenOffAfterAttendance()&&prefs.lockdownAfterAttendance()?"완료 후 잠금 모드가 켜져 있어요. 출결 완료 뒤에는 PIN 등으로 한 번 해제해야 하며 Wi-Fi 재연결만으로 풀리지 않아요.":"완료 후 화면·잠금 방식은 자동출석 탭에서 변경할 수 있어요.");
        add(lock,"완료 후 잠금 방식 확인",false,()->setting("setup-screen"),null);
        if(CreatorTools.testsVisible(this)){LinearLayout trial=card("실제 출결 없는 연습","선택 사항",false);note(trial,"15초 뒤 Chrome 내부 시험 버튼을 누르게 해요. 예약 후 화면을 끄고 기다리세요. 성공해도 실제 서버 인증까지 확인한 것은 아니에요.");add(trial,"잠금·자동 클릭 연습 열기",false,()->startActivity(new Intent(this,LockLabActivity.class).putExtra("setup_practice",true)),"setup-practice");}
    }
    private void finishSetup(SetupGuidePolicy.Snapshot s){
        boolean ready=SetupGuidePolicy.readyToEnable(s);boolean[] done=SetupGuidePolicy.completed(s);
        LinearLayout summary=card(ready?"설정 확인을 마쳤어요":"남은 설정을 확인하세요",ready?"예약 켜기 전 마지막 확인":"미완료 항목을 누르세요",ready);
        for(int i=0;i<4;i++){final int index=i;add(summary,(done[i]?"✓ ":"! ")+NAMES[i]+"  ›",false,()->go(index),"setup-review-"+i);}
        note(summary,"Chrome 인증·Extend Unlock은 직접 확인한 상태이며 실제 자동출석 성공을 보장하는 표시는 아니에요.");
        LinearLayout schedule=card("예약이 작동하는 시간",prefs.enabled()?"예약 켜짐":"예약 꺼짐",prefs.enabled());
        note(schedule,"입실 "+prefs.start()+"–"+prefs.end()+"\n기본 퇴실 "+Policy.checkoutStartTime(prefs.checkout())+"\n단축수업·휴강일은 시간표를 반영해요.");note(schedule,"설정한 시간에만 감시하고 출결 완료 후에는 해당 감시를 종료해요.");
        if(!prefs.hasLocation()){note(schedule,"강의실 위치를 등록하면 Wi-Fi 미연결·퇴실 이탈 알림도 받을 수 있어요. 출석 자체의 필수 설정은 아니에요.");add(schedule,"강의실 위치 등록",false,()->setting("setup-location"),null);}
        if(ready)note(schedule,"‘예약 켜기’는 자동 클릭도 함께 켭니다. 현재 조건이 맞으면 5초 뒤 미처리 출결을 시도할 수 있어요.");
    }
    private void begin(){
        var s=snapshot(this);if(!SetupGuidePolicy.readyToEnable(s)){go(SetupGuidePolicy.nextStep(s));return;}
        if(s.enabled()&&s.autoClick()){setResult(Activity.RESULT_FIRST_USER);finish();return;}
        new AlertDialog.Builder(this).setTitle("자동출석 예약을 켤까요?")
            .setMessage("출석 예약·자동 클릭·처리 안내 화면·완료 후 화면 끄기·잠금 모드를 함께 켭니다. 잠금 모드 후에는 PIN 등으로 한 번 해제해야 해요. 현재 출석 시간과 Wi-Fi 조건이 맞으면 5초 뒤 실제 입실·퇴실 처리를 시도할 수 있어요.")
            .setNegativeButton("아직",null).setPositiveButton("예약 켜기",(d,w)->{if(!SetupGuidePolicy.readyToEnable(snapshot(this))){go(SetupGuidePolicy.nextStep(snapshot(this)));return;}if(busy())return;setResult(Activity.RESULT_OK);finish();}).show();
    }
    private boolean busy(){if(ChromeAttendanceService.busy()||ChromeAttendanceService.otherUiBusy()||ScheduledUnlock.active(this)||ActivationRun.pending()){Toast.makeText(this,"진행 중인 출결·화면 작업이 끝난 뒤 다시 눌러 주세요",Toast.LENGTH_LONG).show();return true;}return false;}
    private void open(PhoneSettings.Target target){if(!busy())PhoneSettings.open(this,target);}
    private void setting(String tag){if(!busy())startActivity(new Intent(this,MainActivity.class).putExtra("open_tab",tag.equals("setup-schedule")||tag.equals("setup-days")||tag.equals("automation")?1:2).putExtra("focus_setting",tag));}
    private void requestLocation(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){open(PhoneSettings.Target.LOCATION_PERMISSION);return;}
        LocationDisclosure.show(this,this::requestLocationAfterDisclosure);
    }
    private void requestLocationAfterDisclosure(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){open(PhoneSettings.Target.LOCATION_PERMISSION);return;}
        if(prefs.raw.getBoolean("setup_location_requested",false)&&!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)){open(PhoneSettings.Target.LOCATION_PERMISSION);return;}
        prefs.raw.edit().putBoolean("setup_location_requested",true).apply();requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},41);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] values){super.onRequestPermissionsResult(code,permissions,values);if(code==41||code==42){permissionPending=false;render(true);maybeAdvanceQuick();}}
    private void help(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("확인",null).show();}
    private void check(LinearLayout box,String label,String key,boolean enabled){CheckBox check=new CheckBox(this);check.setTag(key);check.setText(label);check.setTextSize(14);check.setTextColor(INK);check.setMinHeight(dp(52));check.setChecked(prefs.raw.getBoolean(key,false));check.setEnabled(enabled);box.addView(check);check.setOnCheckedChangeListener((v,value)->{prefs.raw.edit().putBoolean(key,value).apply();render(true);});}
    private LinearLayout card(String title,String state,boolean ready){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(16),dp(18),dp(18));box.setBackground(shape(0xFFFFFFFF,22));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);body.addView(box,lp);box.addView(text(title,17,INK,true));TextView status=text((ready?"✓ ":"· ")+state,12,ready?TEAL:AMBER,false);status.setPadding(0,dp(6),0,dp(5));box.addView(status);return box;}
    private void note(LinearLayout parent,String value){TextView t=text(value,13,MUTED,false);t.setPadding(0,dp(7),0,dp(4));parent.addView(t);}
    private void add(LinearLayout parent,String label,boolean primary,Runnable action,String tag){space(parent,10);parent.addView(button(label,primary,action,tag),new LinearLayout.LayoutParams(-1,-2));}
    private Button button(String label,boolean primary,Runnable action,String tag){Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(48));b.setPadding(dp(8),dp(6),dp(8),dp(6));b.setTextColor(primary?0xFFFFFFFF:TEAL);b.setBackground(shape(primary?TEAL:0xFFE5F2EF,14));b.setStateListAnimator(null);b.setTag(tag);b.setOnClickListener(v->action.run());return b;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private void space(LinearLayout parent,int n){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
