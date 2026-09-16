package net.tacademy.attendance;

import android.accessibilityservice.*;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.accessibility.*;
import android.widget.Toast;
import java.time.*;
import java.util.*;

@android.annotation.SuppressLint("ApplySharedPref") // Durable click marker prevents duplicate attendance after interrupted UI work.
public final class ChromeAttendanceService extends AccessibilityService {
    private static ChromeAttendanceService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable finished;
    private String lockProbe="", lockAttendanceToken="";
    private final Set<String> lockTrace=new HashSet<>();
    private long lockProbeUntil;
    private LocalClickPage localClickPage;
    private boolean localClickRequested,localClickAccepted;
    private PhoneSettings.Target settingsTarget;
    private int settingsStep, settingsScrolls;
    private long settingsUntil, settingsActionAt;
    private boolean clickAttempted, recoveryExtended;
    private boolean working, clicked, confirmationExpected, confirmationClicked, navigatingWifi, menuOpened;
    private Policy.Phase phase;
    private String day, key;
    private long started, pageSeen, resultDeadline;
    private boolean manualSession, manualReadOnly, manualRetryApproved;
    private String attemptId="",lastStage="CHROME_NOT_VISIBLE";
    private final Set<String> seenStages=new HashSet<>();
    private ReadEvidence evidence=new ReadEvidence();
    private String lastDiagnostic="",lastSiteSignal="NOT_OBSERVED",lastSiteAddress="HIDDEN";
    private long lastSiteAt,lastChromeEventAt;
    private int diagnosticCount,pollCount;
    private static final class ReadEvidence {
        String owner="ROOT_UNAVAILABLE",address="HIDDEN",signal="NOT_OBSERVED",button="NOT_OBSERVED";
        String readState="NOT_STARTED",readError="NONE",wifi="NOT_OBSERVED",focusedOwner="ROOT_UNAVAILABLE";
        boolean interactive=true,locked,deviceLocked,chromeWindow,truncated;
        String operation="NOT_STARTED";
        int nodes,visible,webNodes,windows;
    }
    private final Runnable timeout = this::onPageTimeout;
    // Read finished labels promptly; continuous Chrome events must not indefinitely postpone the poll.
    private boolean eventReadQueued;
    private final Runnable eventInspect = ()->{eventReadQueued=false;inspect();};
    private void onPageTimeout(){
        if(!working)return;
        Prefs p=new Prefs(this);
        boolean attempted=clickAttempted||key.equals(p.raw.getString("chrome_pending",""));
        if(AttendanceAttemptPolicy.extendBeforeClick(attempted,recoveryExtended,lastStage)&&observationAllowed(p)){
            recoveryExtended=true;
            resultDeadline=started+AttendanceAttemptPolicy.RECOVERY_PAGE_WAIT_MS;
            long remaining=resultDeadline-SystemClock.elapsedRealtime();
            if(remaining>0){
                DiagnosticLog.add(this,DiagnosticLog.Event.PAGE_RECOVERY_WAIT,phase,"PRE_CLICK_WINDOW_RECOVERY",attemptDetails());
                p.status("Chrome 화면 복구 대기", "출결 화면이 다시 보이면 이어서 확인합니다");
                handler.postDelayed(timeout,remaining);return;
            }
        }
        fail("Chrome에서 확인이 필요합니다", "출석 페이지와 기록을 확인해 주세요. 확인되지 않은 클릭은 반복하지 않습니다","TIMEOUT_"+lastStage);
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (working) { inspect(); if (working) handler.postDelayed(this, 700); }
        }
    };
    public static boolean available() { return instance != null; }
    public static boolean configured(Context c) {
        String enabled=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(enabled==null)return false;
        ComponentName target=new ComponentName(c,ChromeAttendanceService.class);
        for(String name:enabled.split(":"))if(target.equals(ComponentName.unflattenFromString(name)))return true;
        return false;
    }
    public static boolean busy() { return instance != null && instance.working; }
    @Override protected void onServiceConnected() {
        instance = this; events(false);
        Prefs p=new Prefs(this);DiagnosticLog.add(this,DiagnosticLog.Event.ACCESSIBILITY_CONNECTED,p.phase(ZonedDateTime.now(Policy.ZONE)),"SERVICE_CONNECTED");
        if(p.auto()&&p.monitoringNeeded(ZonedDateTime.now(Policy.ZONE)))handler.post(()->AttendanceEngine.request(this,true,null));
    }
    private void events(boolean enabled) {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = enabled ? AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED | AccessibilityEvent.TYPE_VIEW_SCROLLED | AccessibilityEvent.TYPE_WINDOWS_CHANGED : 0;
            setServiceInfo(info);
        }
    }
    public static boolean openChrome(Context context, String url) {
        return openChrome(context,url,AccessPolicy.Kind.MANUAL);
    }
    private static boolean openChrome(Context context,String url,AccessPolicy.Kind kind){
        AccessPolicy.Decision decision=TrafficGuard.claim(context,kind,Policy.Phase.IDLE);
        if(!decision.allowed()){Toast.makeText(context,TrafficGuard.message(decision),Toast.LENGTH_LONG).show();return false;}
        return launchChrome(context,url,kind,Policy.Phase.IDLE);
    }
    private static boolean launchChrome(Context context,String url,AccessPolicy.Kind kind,Policy.Phase phase){
        DiagnosticLog.add(context,DiagnosticLog.Event.CHROME_OPEN_REQUEST,phase,kind.name());
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.android.chrome")
                .putExtra("com.android.browser.application_id", context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent); DiagnosticLog.add(context,DiagnosticLog.Event.CHROME_OPEN_DISPATCHED,phase,kind.name()); return true;
        } catch (ActivityNotFoundException e) {
            DiagnosticLog.add(context,DiagnosticLog.Event.CHROME_OPEN_FAILED,phase,"CHROME_UNAVAILABLE");
            Toast.makeText(context, "Google Chrome을 설치하거나 사용 설정해 주세요", Toast.LENGTH_LONG).show(); return false;
        }
    }
    public static void begin(Context context, Runnable done) {
        if(ActivationRun.pending()){if(done!=null)done.run();return;}
        begin(context,done,new Prefs(context).phase(ZonedDateTime.now(Policy.ZONE)),false,false,false);
    }
    public static void beginManual(Activity context,Policy.Phase phase,boolean readOnly,boolean retryApproved) {
        Prefs p=new Prefs(context);
        String problem=AttendanceAttemptPolicy.manualProblem(phase,ZonedDateTime.now(Policy.ZONE),p.days(),p.excluded(),p.start(),p.checkout(),readOnly);
        if(problem.isEmpty()&&!NetworkAccess.inspect(context).allowed())problem=NetworkAccess.inspect(context).reason();
        if(problem.isEmpty()&&!available())problem="Chrome 접근성 연결이 필요해요. 연결 탭에서 확인해 주세요.";
        if(problem.isEmpty()&&(LockLab.readOnlyActive(context)||ScheduledUnlock.active(context)||instance!=null&&(instance.working||instance.navigatingWifi||instance.settingsTarget!=null||!instance.lockProbe.isEmpty())))problem="진행 중인 테스트나 화면 작업을 마친 뒤 다시 눌러 주세요.";
        if(!problem.isEmpty()){p.status("직접 출결 확인 필요",problem);DiagnosticLog.add(context,DiagnosticLog.Event.MANUAL_BLOCKED,phase,"PREREQUISITE");Toast.makeText(context,problem,Toast.LENGTH_LONG).show();return;}
        DiagnosticLog.add(context,DiagnosticLog.Event.MANUAL_REQUEST,phase,readOnly?"READ_RECORD":retryApproved?"USER_CONFIRMED_RETRY":"ONE_SHOT");
        begin(context,null,phase,true,readOnly,retryApproved);
    }
    private static void begin(Context context,Runnable done,Policy.Phase target,boolean manual,boolean readOnly,boolean retryApproved) {
        if (LockLab.readOnlyActive(context)) { DiagnosticLog.add(context,DiagnosticLog.Event.WAITING,new Prefs(context).phase(ZonedDateTime.now(Policy.ZONE)),"READ_ONLY_TRIAL_ACTIVE");if (done != null) done.run(); return; }
        Prefs prefs = new Prefs(context);
        if (instance == null) {
            DiagnosticLog.add(context,DiagnosticLog.Event.WAITING,prefs.phase(ZonedDateTime.now(Policy.ZONE)),"ACCESSIBILITY_UNAVAILABLE");
            prefs.status("Chrome 접근성 연결 필요", "연결 탭에서 출석 메이트 접근성을 켜 주세요");
            Notices.chromeAction(context, true, "접근성을 켜면 기존 Chrome 인증으로 출석 버튼을 처리합니다");
            if (done != null) done.run(); return;
        }
        if(ScheduledUnlock.active(context)){if(done!=null)done.run();return;}
        if (instance.working || instance.navigatingWifi || instance.settingsTarget!=null || !instance.lockProbe.isEmpty()) { DiagnosticLog.add(context,DiagnosticLog.Event.WAITING,prefs.phase(ZonedDateTime.now(Policy.ZONE)),"OTHER_UI_WORK");if (done != null) done.run(); return; }
        if (context.getSystemService(KeyguardManager.class).isKeyguardLocked() ||
                context.getSystemService(KeyguardManager.class).isDeviceLocked() || !context.getSystemService(PowerManager.class).isInteractive()) {
            DiagnosticLog.add(context,DiagnosticLog.Event.WAITING,prefs.phase(ZonedDateTime.now(Policy.ZONE)),"SCREEN_LOCKED");
            prefs.status("잠금 해제 후 Chrome에서 확인", "출석 알림을 눌러 화면 잠금을 해제해 주세요");
            Notices.chromeAction(context, true, "잠금을 해제하고 알림을 누르면 기존 Chrome으로 출석을 확인합니다");
            ScheduledUnlock.request(context,prefs.phase(ZonedDateTime.now(Policy.ZONE)));
            if (done != null) done.run(); return;
        }
        ChromeAttendanceService service = instance;
        service.phase = target;
        if (service.phase == Policy.Phase.IDLE) { if (done != null) done.run(); return; }
        AccessPolicy.Kind kind=manual?AccessPolicy.Kind.MANUAL:AccessPolicy.Kind.AUTOMATIC;
        AccessPolicy.Decision admission=TrafficGuard.claim(context,kind,service.phase);
        if(!admission.allowed()){
            String message=TrafficGuard.message(admission);prefs.status(manual?"직접 실행 대기":"자동 접속 제한",message);
            if(manual)Toast.makeText(context,message,Toast.LENGTH_LONG).show();
            if(admission.reason()!=AccessPolicy.Reason.COOLDOWN)Notices.chromeAction(context,true,message);
            if(done!=null)done.run();return;
        }
        service.manualSession=manual;service.manualReadOnly=readOnly;service.manualRetryApproved=retryApproved;
        service.day = LocalDate.now(Policy.ZONE).toString();
        service.key = service.day + ":" + service.phase.name();
        prefs.attendanceHint(service.day,service.phase,"");
        service.lockAttendanceToken = LockLab.live(context,LockLab.id(context)) && LockLab.prefs(context).getBoolean("allow_attendance",false) ? LockLab.id(context) : "";
        service.attemptId=UUID.randomUUID().toString();service.lastStage="CHROME_NOT_VISIBLE";service.seenStages.clear();
        service.evidence=new ReadEvidence();service.lastDiagnostic="";service.diagnosticCount=0;service.pollCount=0;
        service.lastSiteAt=0;service.lastChromeEventAt=0;service.lastSiteSignal="NOT_OBSERVED";service.lastSiteAddress="HIDDEN";
        service.lockTrace.clear(); service.finished = done; service.working = true; service.clicked = false; service.clickAttempted=false; service.recoveryExtended=false;
        service.confirmationExpected = false; service.confirmationClicked = false;
        service.started = SystemClock.elapsedRealtime(); service.pageSeen = 0;
        service.events(true);
        prefs.status("Chrome 출석 화면 확인 중", "기존 Chrome의 인증 상태를 사용합니다");
        Context launch = context instanceof Activity ? context : service;
        if (!launchChrome(launch,SiteProtocol.PAGE,kind,service.phase)) { service.fail("Chrome 실행 실패", "Google Chrome 사용 설정을 확인해 주세요"); return; }
        service.resultDeadline=service.started+AttendanceAttemptPolicy.PAGE_WAIT_MS;
        service.handler.postDelayed(service.timeout, AttendanceAttemptPolicy.PAGE_WAIT_MS);
        service.handler.postDelayed(service.tick, 1200);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(settingsTarget!=null){return;} // The bounded settings poll owns only explicitly requested menu navigation.
        if (navigatingWifi && "com.android.settings".equals(string(event.getPackageName()))) { navigateWifi(); return; }
        if (working && "com.android.chrome".equals(string(event.getPackageName()))) {
            lastChromeEventAt=SystemClock.elapsedRealtime();
            if(!eventReadQueued){eventReadQueued=true;handler.postDelayed(eventInspect,80);}
        }
    }
    private void inspect() {
        if(!working)return;
        try { inspectAttendance(); }
        catch(RuntimeException e){
            evidence.readError=DiagnosticLog.failureCode(e);
            fail("화면 판독 오류", "휴대폰 화면 접근 중 오류가 발생했습니다. 진단 로그에서 확인할 수 있습니다.","LOCAL_RUNTIME_FAILURE");
        }
    }
    private void inspectAttendance() {
        if (!working) return;
        if (LockLab.readOnlyActive(this) || (!lockAttendanceToken.isEmpty() && !LockLab.live(this,lockAttendanceToken))) { finishWork(); return; }
        Prefs prefs = new Prefs(this);
        if (!observationAllowed(prefs)) { finishWork(); return; }
        evidence=new ReadEvidence();pollCount++;evidence.operation="ENVIRONMENT_READ";
        evidence.interactive=getSystemService(PowerManager.class).isInteractive();
        evidence.locked=getSystemService(KeyguardManager.class).isKeyguardLocked();
        evidence.deviceLocked=getSystemService(KeyguardManager.class).isDeviceLocked();
        NetworkAccess.State network=NetworkAccess.inspect(this);evidence.wifi=network.code();
        if (!network.allowed()) { fail("Wi-Fi 연결 대기", "지정 Wi-Fi에 연결한 뒤 직접 확인해 주세요","WIFI_LOST_DURING_WORK"); return; }
        if (evidence.locked || !evidence.interactive) {
            fail("잠금 해제 후 확인", "잠금을 해제하고 Chrome 출석 알림을 눌러 주세요","SCREEN_LOCKED_DURING_WORK"); return;
        }
        evidence.operation="ROOT_READ";
        AccessibilityNodeInfo root = freshChromeRoot();
        if (root == null || !"com.android.chrome".equals(string(root.getPackageName()))) { windowStage(root);traceLock("Chrome 화면 노드 대기 · 현재 화면에서 Chrome을 읽지 못함"); return; }
        evidence.operation="NODE_COLLECTION";
        List<AccessibilityNodeInfo> nodes = new ArrayList<>(); collect(root, nodes, 0);
        String text = allText(nodes);
        readPageEvidence(nodes,text);evidence.operation="PAGE_INSPECTION";
        if (confirmationExpected && !confirmationClicked && ChromePage.checkoutConfirmation(text)) {
            if ((!manualSession&&!prefs.auto()) || !observationAllowed(prefs)) { finishWork(); return; }
            AccessibilityNodeInfo confirm = exactButton(nodes, "확인", "OK");
            if (confirm != null) {
                confirmationClicked = true; // A rejected UI action may still have reached Chrome; never repeat it.
                DiagnosticLog.add(this,DiagnosticLog.Event.CLICK_PLANNED,phase,"CHECKOUT_CONFIRMATION",attemptDetails());
                evidence.operation="CONFIRM_CLICK";
                boolean accepted=click(confirm);
                DiagnosticLog.add(this,accepted?DiagnosticLog.Event.CONFIRM_REQUEST:DiagnosticLog.Event.BUTTON_FAILED,phase,accepted?"UI_ACTION_ACCEPTED":"CONFIRM_ACTION_REJECTED",attemptDetails());
                if(accepted)traceLock("퇴실 확인 대화상자 클릭 요청 전달됨");
                else fail("퇴실 확인 결과 미확인","확인 버튼을 다시 누르지 않습니다. Chrome에서 출결 기록을 확인해 주세요.","CONFIRM_ACTION_REJECTED");
                if(accepted){waitForResult();stage("RECORD_NOT_UPDATED");}
            } else stage("CONFIRM_BUTTON_UNAVAILABLE");
            return;
        }
        boolean correctAddress = false;
        for (AccessibilityNodeInfo node : nodes)
            if ("com.android.chrome:id/url_bar".equals(node.getViewIdResourceName()) &&
                    ChromePage.attendanceAddress(string(node.getText()))) correctAddress = true;
        if (!correctAddress) { stage("ADDRESS_NOT_VISIBLE");traceLock("Chrome 주소 확인 대기 · 지정 출결 주소를 읽지 못함"); return; }
        if(ChromePage.serverUnavailable(text)){fail("사이트 오류 화면 확인","자동 재접속을 멈췄습니다. 사이트 복구 후 Chrome에서 직접 확인해 주세요.","SITE_ERROR_SCREEN");return;}
        traceLock("Chrome 출결 주소 표시 확인 · 실제 출결 기록 확인 중");
        if (text.contains("인증절차를 진행한후") || text.contains("인증절차를 진행한 후")) {
            fail("Chrome 인증 확인 필요", "평소 인증해 둔 Chrome 일반 탭에서 출석 화면이 열리는지 확인해 주세요","CHROME_AUTH_REQUIRED"); return;
        }
        if (pageSeen == 0) pageSeen = SystemClock.elapsedRealtime();
        try { if (!ChromePage.date(text).toString().equals(day)) { stage("DATE_MISMATCH");traceLock("출결일 불일치 · 오늘 기록이 아니어서 클릭 보류"); return; } }
        catch (RuntimeException e) { stage("DATE_UNREADABLE");traceLock("출결일 읽기 대기 · 사이트 화면 형식 확인 필요"); return; }
        Policy.Record record;
        try { record = ChromePage.record(text); } catch (RuntimeException e) { stage("RECORD_UNREADABLE");traceLock("입실·퇴실 기록 읽기 대기 · 사이트 화면 형식 확인 필요"); return; }
        if (record.checkedIn()) {
            prefs.record(day, record);
            boolean completed = phase == Policy.Phase.ENTRY || record.checkedOut();
            if (completed) {
                prefs.raw.edit().remove("chrome_pending").remove("chrome_pending_at").apply();
                String title = phase == Policy.Phase.ENTRY ? "입실 완료" : "퇴실 완료";
                String time = phase == Policy.Phase.ENTRY ? record.in() : record.out();
                prefs.attendanceHint(day,phase,"");
                prefs.status(title + " · 감시 종료", "Chrome 사이트 기록 " + time);
                prefs.log(title + " " + time);
                diagnostic("SITE_TIME_OBSERVED",false);
                DiagnosticLog.add(this,DiagnosticLog.Event.RECORD_CONFIRMED,phase,"SITE_TIME_OBSERVED",attemptDetails());
                Notices.attendanceCompleted(this, day, phase, record);
                Notices.chromeAction(this, false, "");
                if (record.checkedOut()) Notices.checkoutAttention(this, false, "");
                finishWork(); ScheduleController.reconcile(this); return;
            }
        }
        // Existing confirmed records can be read immediately. Only a NEW click needs settling.
        if (SystemClock.elapsedRealtime() - pageSeen < 1800) {stage("PAGE_SETTLING");return;}
        if(manualSession&&manualReadOnly){
            prefs.status("출결 기록 확인",phase==Policy.Phase.ENTRY?"아직 입실 완료 시간을 확인하지 못했어요.":"아직 퇴실 완료 시간을 확인하지 못했어요. 실제 미처리를 확인한 뒤 다시 실행해 주세요.");
            DiagnosticLog.add(this,DiagnosticLog.Event.MANUAL_FINISHED,phase,"RECORD_NOT_CONFIRMED");finishWork();return;
        }
        if (!manualSession&&!prefs.auto()) { stage("AUTO_CLICK_DISABLED");prefs.status("Chrome 조회 모드", "설정에서 자동 처리를 켜면 출석 버튼을 누릅니다"); finishWork(); return; }
        if (phase == Policy.Phase.CHECKOUT && !record.checkedIn()) { stage("ENTRY_RECORD_MISSING");traceLock("오늘 입실 기록 없음 · 퇴실 클릭 보류"); return; }
        if (clicked) {stage(confirmationExpected&&!confirmationClicked?"CONFIRMATION_NOT_VISIBLE":"RECORD_NOT_UPDATED");return;}
        if (key.equals(prefs.raw.getString("chrome_pending", "")) && !(manualSession&&manualRetryApproved)) {
            fail("이전 클릭 결과 확인 필요", "Chrome에서 실제 기록을 확인한 뒤 연결 탭에서 보류를 해제해 주세요","PENDING_CLICK"); return;
        }
        AccessibilityNodeInfo button = exactButton(nodes, phase == Policy.Phase.ENTRY ? "입실" : "퇴실");
        if (button == null || !button.isEnabled()) { stage("BUTTON_UNAVAILABLE");traceLock("출결 버튼 대기 · 클릭 가능한 "+(phase==Policy.Phase.ENTRY?"입실":"퇴실")+" 버튼을 읽지 못함"); return; }
        if (!newClickAllowed(prefs) || !NetworkAccess.inspect(this).allowed()) return;
        if (!prefs.raw.edit().putString("chrome_pending", key).putLong("chrome_pending_at",System.currentTimeMillis()).commit()) { fail("처리 상태 저장 실패", "앱에서 상태를 확인해 주세요"); return; }
        // Recheck after the durable write in case the phone clock changed while saving.
        if (!newClickAllowed(prefs)) { prefs.raw.edit().remove("chrome_pending").remove("chrome_pending_at").apply(); finishWork(); return; }
        diagnostic("BEFORE_ATTENDANCE_CLICK",false);
        DiagnosticLog.add(this,DiagnosticLog.Event.CLICK_PLANNED,phase,"ATTENDANCE_BUTTON",attemptDetails());
        manualRetryApproved=false;
        clickAttempted=true;
        evidence.operation="ATTENDANCE_CLICK";
        clicked = click(button);
        DiagnosticLog.add(this,clicked?DiagnosticLog.Event.BUTTON_REQUEST:DiagnosticLog.Event.BUTTON_FAILED,phase,clicked?"UI_ACTION_ACCEPTED":"UI_ACTION_REJECTED",attemptDetails());
        diagnostic(clicked?"UI_ACTION_ACCEPTED":"BUTTON_ACTION_REJECTED",false);
        if(clicked)waitForResult();
        traceLock((phase==Policy.Phase.ENTRY?"입실":"퇴실")+(clicked?" 버튼 클릭 요청 전달됨 · 사이트 기록 확인 전":" 버튼 클릭 요청 실패"));
        confirmationExpected = clicked && phase == Policy.Phase.CHECKOUT;
        if (!clicked) fail("출결 버튼 결과 미확인","버튼을 다시 누르지 않습니다. Chrome에서 출결 기록을 확인해 주세요.","BUTTON_ACTION_REJECTED");
    }
    private boolean newClickAllowed(Prefs p){
        ZonedDateTime now=ZonedDateTime.now(Policy.ZONE);
        if(!day.equals(now.toLocalDate().toString()))return false;
        return manualSession?!manualReadOnly&&AttendanceAttemptPolicy.manualProblem(phase,now,p.days(),p.excluded(),p.start(),p.checkout(),false).isEmpty():p.phase(now)==phase;
    }
    private boolean observationAllowed(Prefs p){
        if(!day.equals(LocalDate.now(Policy.ZONE).toString()))return false;
        return manualSession || (p.enabled()&&(clicked||p.phase(ZonedDateTime.now(Policy.ZONE))==phase));
    }
    private void waitForResult(){
        resultDeadline=AttendanceAttemptPolicy.afterAction(started,SystemClock.elapsedRealtime());
        handler.removeCallbacks(timeout);handler.postDelayed(timeout,Math.max(0,resultDeadline-SystemClock.elapsedRealtime()));
        DiagnosticLog.add(this,DiagnosticLog.Event.RESULT_WAIT_STARTED,phase,"AFTER_UI_ACTION",attemptDetails());
    }
    /** Ending a GPS reservation must not discard a manually requested run or an already submitted result. */
    public static void cancelScheduledWork(){
        if(instance==null||!instance.working||instance.manualSession)return;
        if(instance.clicked&&new Prefs(instance).enabled())return;
        instance.finishWork();
    }
    private AccessibilityNodeInfo freshChromeRoot() {
        // Dynamic input buttons and window focus can outlive the cached virtual tree.
        if(Build.VERSION.SDK_INT>=33){boolean cleared=clearCache();if(working)evidence.readState=cleared?"CACHE_CLEARED":"CACHE_CLEAR_REJECTED";}
        AccessibilityNodeInfo root=focusedRoot();
        if(root!=null&&Build.VERSION.SDK_INT<33){boolean refreshed=root.refresh();if(working)evidence.readState=refreshed?"ROOT_REFRESHED":"ROOT_REFRESH_FAILED";if(!refreshed)root=null;}
        if(working){evidence.owner=owner(root);observeWindows();}
        return root;
    }
    private AccessibilityNodeInfo focusedRoot() {
        for (AccessibilityWindowInfo window : getWindows()) if (window.isFocused()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null) return root;
        }
        return getRootInActiveWindow();
    }
    private static String string(CharSequence value) { return value == null ? "" : value.toString(); }
    private static void collect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> nodes, int depth) {
        if (node == null || depth > 40 || nodes.size() >= 1200) return;
        nodes.add(node);
        for (int i=0; i<node.getChildCount(); i++) collect(node.getChild(i), nodes, depth+1);
    }
    private static String allText(List<AccessibilityNodeInfo> nodes) {
        StringBuilder text = new StringBuilder();
        for (AccessibilityNodeInfo node : nodes) {
            if (node.isVisibleToUser()) text.append(string(node.getText())).append('\n').append(string(node.getContentDescription())).append('\n');
        }
        return text.toString();
    }
    private static AccessibilityNodeInfo exactButton(List<AccessibilityNodeInfo> nodes, String... names) {
        for (AccessibilityNodeInfo node : nodes) if (node.isVisibleToUser() && node.isEnabled()) {
            for (String name : names) if (name.equals(string(node.getText()).trim()) || name.equals(string(node.getContentDescription()).trim())) {
                AccessibilityNodeInfo target = node;
                for (int depth=0; target != null && depth<3; depth++, target=target.getParent())
                    if (target.isClickable() && target.isEnabled()) return target;
            }
        }
        return null;
    }
    private static boolean click(AccessibilityNodeInfo node) { return node.performAction(AccessibilityNodeInfo.ACTION_CLICK); }
    private org.json.JSONObject attemptDetails(){org.json.JSONObject d=new org.json.JSONObject();try{d.put("attempt_id",attemptId).put("diagnostic_version",3).put("page_visible_ms",pageSeen==0?-1:Math.max(0,SystemClock.elapsedRealtime()-pageSeen));}catch(org.json.JSONException ignored){}return d;}
    private String owner(AccessibilityNodeInfo node){
        if(node==null)return "ROOT_UNAVAILABLE";
        String pkg=string(node.getPackageName());
        if("com.android.chrome".equals(pkg))return "CHROME";
        if("com.android.systemui".equals(pkg))return "SYSTEM_UI";
        return getPackageName().equals(pkg)?"OWN_APP":"OTHER_APP";
    }
    private void observeWindows(){
        // Inspect package categories only. Never traverse or store another application's content.
        try{List<AccessibilityWindowInfo> windows=getWindows();evidence.windows=windows.size();
            for(int i=0;i<Math.min(16,windows.size());i++){
                AccessibilityWindowInfo w=windows.get(i);String category=owner(w.getRoot());
                if(w.isFocused())evidence.focusedOwner=category;
                if("CHROME".equals(category))evidence.chromeWindow=true;
            }
        }catch(RuntimeException e){evidence.readError=DiagnosticLog.failureCode(e);}
    }
    private void readPageEvidence(List<AccessibilityNodeInfo> nodes,String text){
        evidence.nodes=nodes.size();evidence.truncated=nodes.size()>=1200;
        for(AccessibilityNodeInfo n:nodes){
            if(n.isVisibleToUser()){
                evidence.visible++;
                if("android.webkit.WebView".equals(string(n.getClassName())))evidence.webNodes++;
                if("com.android.chrome:id/url_bar".equals(n.getViewIdResourceName()))evidence.address=ChromeDiagnostics.address(string(n.getText()));
            }
        }
        evidence.signal=ChromeDiagnostics.pageSignal(evidence.address,text);
        // Do not inspect labels on unrelated tabs; only an enum leaves this method.
        if(ChromeDiagnostics.siteAddress(evidence.address)){
            String label=phase==Policy.Phase.ENTRY?"입실":"퇴실";evidence.button="ABSENT";
            for(AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser()&&(label.equals(string(n.getText()).trim())||label.equals(string(n.getContentDescription()).trim())))evidence.button=n.isEnabled()?"LABEL_ONLY":"DISABLED";
            if(exactButton(nodes,label)!=null)evidence.button="READY";
            lastSiteAt=SystemClock.elapsedRealtime();lastSiteSignal=evidence.signal;lastSiteAddress=evidence.address;
        }
    }
    private void diagnostic(String stage,boolean failure){
        String diagnosis=ChromeDiagnostics.diagnosis(evidence.owner,evidence.interactive,evidence.locked||evidence.deviceLocked,
            evidence.readError,evidence.wifi,evidence.address,evidence.signal,evidence.visible,evidence.button,stage);
        String fingerprint=stage+":"+diagnosis+":"+evidence.owner+":"+evidence.address+":"+evidence.signal+":"+evidence.button+":"+evidence.readState;
        if(!failure&&(diagnosticCount>=14||fingerprint.equals(lastDiagnostic)))return;
        lastDiagnostic=fingerprint;if(!failure)diagnosticCount++;
        org.json.JSONObject d=attemptDetails();long now=SystemClock.elapsedRealtime();
        try{d.put("diagnosis",diagnosis).put("stage",stage).put("window_state",evidence.owner).put("focused_owner",evidence.focusedOwner)
            .put("screen_interactive",evidence.interactive).put("keyguard_locked",evidence.locked).put("device_locked",evidence.deviceLocked)
            .put("chrome_window_present",evidence.chromeWindow).put("window_count",evidence.windows).put("read_state",evidence.readState).put("read_error",evidence.readError).put("read_operation",evidence.operation)
            .put("address_state",evidence.address).put("page_signal",evidence.signal).put("button_state",evidence.button)
            .put("node_count",evidence.nodes).put("visible_node_count",evidence.visible).put("web_node_count",evidence.webNodes).put("tree_limit_reached",evidence.truncated)
            .put("wifi_check_code",evidence.wifi).put("click_attempted",clickAttempted).put("click_accepted",clicked).put("confirmation_attempted",confirmationClicked)
            .put("pending_click",key!=null&&key.equals(new Prefs(this).raw.getString("chrome_pending","")))
            .put("elapsed_ms",Math.max(0,now-started)).put("poll_count",pollCount).put("chrome_event_age_ms",lastChromeEventAt==0?-1:now-lastChromeEventAt)
            .put("last_site_signal",lastSiteSignal).put("last_site_address",lastSiteAddress).put("last_site_age_ms",lastSiteAt==0?-1:now-lastSiteAt);
        }catch(org.json.JSONException ignored){}
        DiagnosticLog.add(this,failure?DiagnosticLog.Event.ATTEMPT_FAILED:DiagnosticLog.Event.CHROME_DIAGNOSTIC,phase,diagnosis,d);
    }
    private void windowStage(AccessibilityNodeInfo root){
        String owner=root==null?"ROOT_UNAVAILABLE":
            "com.android.systemui".equals(string(root.getPackageName()))?"SYSTEM_UI":"OTHER_APP";
        lastStage="CHROME_NOT_VISIBLE";
        diagnostic(lastStage,false);
        if(seenStages.add(lastStage+":"+owner)){
            org.json.JSONObject details=attemptDetails();
            try{details.put("window_state",owner);}catch(org.json.JSONException ignored){}
            DiagnosticLog.add(this,DiagnosticLog.Event.CHROME_STAGE,phase,lastStage,details);
        }
    }
    private void stage(String reason){lastStage=reason;diagnostic(reason,false);if(seenStages.add(reason))DiagnosticLog.add(this,DiagnosticLog.Event.CHROME_STAGE,phase,reason,attemptDetails());}
    static void launchScheduledUnlock(Intent intent){
        if(instance==null)throw new IllegalStateException("Accessibility unavailable");
        instance.startActivity(intent);
    }
    private void traceLock(String message) {
        if(!lockAttendanceToken.isEmpty()&&LockLab.live(this,lockAttendanceToken)&&lockTrace.add(message))LockLab.event(this,lockAttendanceToken,message);
    }
    private void fail(String title,String detail){fail(title,detail,"RESULT_UNCONFIRMED");}
    private void fail(String title, String detail,String reason) {
        if (!working) return;
        if (LockLab.readOnlyActive(this) || (!lockAttendanceToken.isEmpty() && !LockLab.live(this,lockAttendanceToken))) { finishWork(); return; }
        Prefs prefs = new Prefs(this);
        diagnostic(reason,true);
        boolean attempted=clickAttempted||key.equals(prefs.raw.getString("chrome_pending",""));
        if(AttendanceAttemptPolicy.pauseAfterFailure(attempted,reason))TrafficGuard.pause(this,phase,reason);
        else {
            DiagnosticLog.add(this,DiagnosticLog.Event.PRECLICK_DEFERRED,phase,reason,attemptDetails());
            title="클릭 전 화면 확인 대기";
            detail="아직 버튼을 누르지 않았습니다. 예약 시간 안에서 접속 제한에 따라 다시 확인합니다";
        }
        traceLock("Chrome 처리 중단: "+title+" · "+detail);
        prefs.status(title, detail);
        String failureKey = day + ":" + phase + ":" + title;
        if (!failureKey.equals(prefs.raw.getString("last_failure", ""))) {
            prefs.log(title); prefs.raw.edit().putString("last_failure", failureKey).apply();
        }
        Notices.chromeAction(this, true, detail);
        finishWork();
    }
    private void finishWork() {
        working = false; manualSession=false;manualReadOnly=false;manualRetryApproved=false;lockAttendanceToken=""; handler.removeCallbacks(timeout); handler.removeCallbacks(tick); handler.removeCallbacks(eventInspect);eventReadQueued=false;
        if (!navigatingWifi && settingsTarget==null && lockProbe.isEmpty()) events(false);
        Runnable done = finished; finished = null;
        if (done != null) done.run();
    }
    /** Returning to our own activity ends a now-unreadable Chrome attempt instead of waiting 70 seconds. */
    public static void returnedToAttendanceApp(){
        ChromeAttendanceService s=instance;if(s==null||!s.working)return;
        // A queued Chrome event may already have confirmed the record; never replace that time with a hint.
        Prefs p=new Prefs(s);Policy.Record record=p.history(java.time.LocalDate.parse(s.day));
        boolean confirmed=record!=null&&(s.phase==Policy.Phase.ENTRY?record.checkedIn():record.checkedOut());
        if(!confirmed){
            boolean submitted=s.clickAttempted||s.key.equals(p.raw.getString("chrome_pending",""));
            String hint=submitted?"결과 미확인 · Chrome 기록 확인 필요":"시각 미확인 · Chrome에서 잠시 기다려 주세요";
            p.attendanceHint(s.day,s.phase,hint);p.status("출결 시각 미확인",hint);
            s.evidence=new ReadEvidence();s.evidence.owner="OWN_APP";s.evidence.focusedOwner="OWN_APP";
            s.evidence.operation="ACTIVITY_RESUMED";s.evidence.readState="APP_RETURNED";
            s.diagnostic("RETURNED_TO_APP_BEFORE_RESULT",true);
            DiagnosticLog.add(s,DiagnosticLog.Event.ATTEMPT_CANCELLED,s.phase,"USER_RETURNED_TO_APP",s.attemptDetails());
            if(submitted)TrafficGuard.pause(s,s.phase,"RETURNED_AFTER_CLICK");else TrafficGuard.deferAfterReturn(s,s.phase);
        }
        s.finishWork();
    }
    /** An explicit OFF stops manual/activation work too, while retaining any durable click marker. */
    public static void cancelForReservationOff(){
        if(instance==null||!instance.working)return;
        ChromeAttendanceService s=instance;org.json.JSONObject d=s.attemptDetails();
        try{d.put("click_attempted",s.clickAttempted||s.clicked).put("click_accepted",s.clicked)
            .put("pending_click",s.key!=null&&s.key.equals(new Prefs(s).raw.getString("chrome_pending","")));}catch(org.json.JSONException ignored){}
        DiagnosticLog.add(s,DiagnosticLog.Event.ATTEMPT_CANCELLED,s.phase,"RESERVATION_OFF",d);
        s.finishWork();
    }
    public static void cancelWork() { if (instance != null) instance.finishWork(); }
    public static void launchLockTest(Context context, Intent intent) {
        Context source=instance!=null?instance:context;
        LockLab.event(context,intent.getStringExtra(LockLab.TOKEN),instance!=null?"접근성 서비스에서 화면 진입 시도":"앱에서 화면 진입 시도 · Android 제한 가능");
        source.startActivity(intent);
    }
    public static void probeLocalClick(Context context,String token){
        if(!LockLab.live(context,token)||!LockLab.localClick(context))return;
        if(instance==null){LockLab.finish(context,token,"접근성 연결이 없어 시험 버튼을 누르지 못했습니다");return;}
        ChromeAttendanceService s=instance;
        if(s.working||s.navigatingWifi||s.settingsTarget!=null){LockLab.finish(context,token,"다른 작업이 진행 중입니다");return;}
        s.endLockProbe(null);s.lockProbe=token;s.localClickRequested=false;s.localClickAccepted=false;
        LockLab.stage(context,token,"대체 클릭 시험 준비 · 실제 출결 없음");
        new Thread(()->{
            try{LocalClickPage page=new LocalClickPage(token);s.handler.post(()->{
                if(instance!=s||!token.equals(s.lockProbe)||!LockLab.live(s,token)){page.close();return;}
                s.localClickPage=page;s.lockProbeUntil=SystemClock.elapsedRealtime()+30000;s.events(true);
                try{
                    s.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(page.url())).setPackage("com.android.chrome")
                        .putExtra("com.android.browser.application_id",s.getPackageName()+".click-test").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    LockLab.stage(s,token,"Chrome 시험 화면과 자동 클릭 대기 · 실제 출결 없음");s.handler.postDelayed(s.lockProbeTick,600);
                }catch(RuntimeException e){s.endLockProbe("Chrome 시험 화면 실행 실패 · "+DiagnosticLog.failureCode(e));}
            });}catch(Exception e){s.handler.post(()->{if(token.equals(s.lockProbe))s.endLockProbe("휴대폰 내부 시험 페이지 준비 실패");});}
        },"prepare-click-test").start();
    }
    public static void probeLockChrome(Context context,String token) {
        if(!LockLab.live(context,token))return;
        if(instance==null){
            LockLab.finish(context,token,"Chrome 접근성 연결 끊김 · 화면 확인과 클릭을 시작하지 않았습니다");return;
        }
        ChromeAttendanceService service=instance;
        if(service.working||service.navigatingWifi||service.settingsTarget!=null){LockLab.finish(context,token,"다른 Chrome/설정 작업 진행 중 · 테스트 보류");return;}
        service.endLockProbe(null);service.lockProbe=token;service.lockProbeUntil=SystemClock.elapsedRealtime()+20000;service.events(true);
        LockLab.stage(context,token,"Chrome 실행 요청 · 출결 주소 표시 확인 중");
        if(!openChrome(context,SiteProtocol.PAGE,AccessPolicy.Kind.TEST)){service.endLockProbe("Chrome 접속 제한 또는 실행 불가 · 연속 테스트를 멈춰 주세요");return;}
        service.handler.postDelayed(service.lockProbeTick,600);
    }
    private final Runnable lockProbeTick=new Runnable(){@Override public void run(){
        if(lockProbe.isEmpty())return;
        if(!LockLab.live(ChromeAttendanceService.this,lockProbe)){LockLab.expire(ChromeAttendanceService.this);endLockProbe(null);return;}
        if(getSystemService(KeyguardManager.class).isKeyguardLocked() || !getSystemService(PowerManager.class).isInteractive()){
            endLockProbe("Chrome 연결 중 다시 잠김 또는 화면 꺼짐");return;
        }
        if(localClickPage!=null){inspectLocalClick();return;}
        AccessibilityNodeInfo root=focusedRoot();
        if(root!=null && "com.android.chrome".equals(string(root.getPackageName()))){
            List<AccessibilityNodeInfo> nodes=new ArrayList<>();collect(root,nodes,0);
            for(AccessibilityNodeInfo node:nodes)if("com.android.chrome:id/url_bar".equals(node.getViewIdResourceName()) && ChromePage.attendanceAddress(string(node.getText()))){
                endLockProbe("Chrome 출결 주소 표시 확인 · 사이트 인증과 출결 결과는 직접 확인");return;
            }
        }
        if(SystemClock.elapsedRealtime()>=lockProbeUntil){endLockProbe("Chrome 출결 화면 미확인 · 실행 제한/주소 표시/인증 페이지를 확인하세요");return;}
        handler.postDelayed(this,600);
    }};
    private void inspectLocalClick(){
        if(SystemClock.elapsedRealtime()>=lockProbeUntil){endLockProbe(localClickRequested?"시험 클릭 후 화면 변경을 확인하지 못했습니다":"Chrome 시험 페이지·버튼을 확인하지 못했습니다");return;}
        AccessibilityNodeInfo root=freshChromeRoot();
        if(root!=null&&"com.android.chrome".equals(string(root.getPackageName()))){
            List<AccessibilityNodeInfo> nodes=new ArrayList<>();collect(root,nodes,0);boolean address=false;
            for(AccessibilityNodeInfo node:nodes)if("com.android.chrome:id/url_bar".equals(node.getViewIdResourceName())&&localClickPage.matches(string(node.getText())))address=true;
            String text=allText(nodes);
            if(address){
                if(text.contains(localClickPage.confirmed())){
                    if(localClickRequested&&localClickAccepted){
                        LockLab.prefs(this).edit().putLong("click_confirmed_at_ms",System.currentTimeMillis()).commit();
                        endLockProbe("대체 버튼 자동 클릭과 화면 변경 확인 · 실제 출결 없음");
                    }else endLockProbe("자동 클릭 전에 버튼이 이미 눌렸습니다. 직접 누르지 말고 다시 시험하세요");
                    return;
                }
                if(!localClickRequested&&text.contains(localClickPage.ready())){
                    AccessibilityNodeInfo button=exactButton(nodes,LocalClickPage.BUTTON);
                    if(button!=null&&button.isEnabled()){
                        localClickRequested=true;LockLab.prefs(this).edit().putLong("click_requested_at_ms",System.currentTimeMillis()).commit();
                        localClickAccepted=click(button);LockLab.prefs(this).edit().putBoolean("click_accepted",localClickAccepted).commit();
                        LockLab.event(this,lockProbe,localClickAccepted?"대체 버튼 자동 클릭 요청 접수 · 화면 변경 확인 중":"대체 버튼 클릭 요청 거절");
                        if(!localClickAccepted){endLockProbe("시험 버튼 클릭 요청이 거절됐습니다");return;}
                    }
                }
            }
        }
        handler.postDelayed(lockProbeTick,400);
    }
    private void endLockProbe(String result){
        String token=lockProbe;lockProbe="";handler.removeCallbacks(lockProbeTick);
        if(localClickPage!=null){localClickPage.close();localClickPage=null;}
        if(!working&&!navigatingWifi&&settingsTarget==null)events(false);
        if(result!=null&&!token.isEmpty())LockLab.finish(this,token,result);
    }
    public static void cancelLockProbe(){if(instance!=null)instance.endLockProbe(null);}
    public static boolean settingsMenu(Activity activity,PhoneSettings.Target target){
        if(instance==null||instance.working||instance.navigatingWifi||!instance.lockProbe.isEmpty())return false;
        if(target!=PhoneSettings.Target.LOCATION_PERMISSION&&target!=PhoneSettings.Target.BATTERY&&target!=PhoneSettings.Target.EXTEND)return false;
        cancelSettingsMenu();ChromeAttendanceService s=instance;s.settingsTarget=target;s.settingsStep=0;s.settingsScrolls=0;
        s.settingsUntil=SystemClock.elapsedRealtime()+15000;s.settingsActionAt=SystemClock.elapsedRealtime();s.events(true);s.handler.postDelayed(s.settingsTick,700);return true;
    }
    public static void cancelSettingsMenu(){if(instance!=null)instance.endSettingsMenu(false);}
    private void endSettingsMenu(boolean guide){
        PhoneSettings.Target target=settingsTarget;settingsTarget=null;handler.removeCallbacks(settingsTick);
        if(!working&&!navigatingWifi&&lockProbe.isEmpty())events(false);
        if(guide&&target!=null)Toast.makeText(this,PhoneSettings.guide(target),Toast.LENGTH_LONG).show();
    }
    private final Runnable settingsTick=new Runnable(){@Override public void run(){
        if(settingsTarget==null)return;
        if(SystemClock.elapsedRealtime()>=settingsUntil){endSettingsMenu(true);return;}
        navigateSettingsMenu();if(settingsTarget!=null)handler.postDelayed(this,700);
    }};
    private void navigateSettingsMenu(){
        if(getSystemService(KeyguardManager.class).isDeviceLocked()){endSettingsMenu(true);return;}
        AccessibilityNodeInfo root=focusedRoot();if(root==null)return;
        String pkg=string(root.getPackageName());
        if(!Set.of("com.android.settings","com.android.permissioncontroller","com.google.android.permissioncontroller").contains(pkg)){
            if(!getPackageName().equals(pkg))endSettingsMenu(false);return;
        }
        if(SystemClock.elapsedRealtime()-settingsActionAt<900)return;
        List<AccessibilityNodeInfo> nodes=new ArrayList<>();collect(root,nodes,0);
        boolean ownApp=allText(nodes).contains(getString(R.string.app_name));
        String[] names;boolean last;
        if(settingsTarget==PhoneSettings.Target.LOCATION_PERMISSION){
            if(!ownApp)return;
            names=settingsStep==0?new String[]{"권한","Permissions"}:new String[]{"위치","Location"};last=settingsStep==1;
        }else if(settingsTarget==PhoneSettings.Target.BATTERY){
            if(!ownApp)return;names=new String[]{"배터리","앱 배터리 사용량","Battery","App battery usage"};last=true;
        }else{
            AccessibilityNodeInfo extend=menuButton(nodes,"Extend Unlock","Smart Lock","잠금 해제 유지");
            if(extend!=null&&click(extend)){endSettingsMenu(false);return;}
            if(settingsStep>0)return;
            names=Build.MANUFACTURER.equalsIgnoreCase("samsung")?new String[]{"잠금화면 및 AOD","잠금화면","Lock screen and AOD","Lock screen"}
                :new String[]{"보안 및 개인정보 보호 더보기","기타 보안 설정","More security & privacy","More security settings"};last=false;
        }
        AccessibilityNodeInfo button=menuButton(nodes,names);
        if(button!=null){
            // A newly composed row may reject its first click. Keep it in view and retry.
            if(click(button)){settingsActionAt=SystemClock.elapsedRealtime();settingsStep++;settingsScrolls=0;if(last)endSettingsMenu(false);}
            return;
        }
        if(settingsScrolls<3)for(AccessibilityNodeInfo node:nodes)if(node.isScrollable()&&node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)){
            settingsScrolls++;settingsActionAt=SystemClock.elapsedRealtime();return;
        }
    }
    private static AccessibilityNodeInfo menuButton(List<AccessibilityNodeInfo> nodes,String... names){
        AccessibilityNodeInfo button=exactButton(nodes,names);
        // Old Android permission lists sometimes expose a switch instead of a submenu. Never toggle it.
        if(button==null)return null;
        List<AccessibilityNodeInfo> children=new ArrayList<>();collect(button,children,0);
        for(AccessibilityNodeInfo child:children)if(child.isCheckable()||string(child.getClassName()).contains("Switch")||string(child.getClassName()).contains("CheckBox"))return null;
        return button;
    }
    public static void intelligentWifi(Activity activity) {
        cancelSettingsMenu();
        // Discover only exported activities supplied by the device's trusted Settings package.
        try {
            PackageInfo settings = activity.getPackageManager().getPackageInfo("com.android.settings", PackageManager.GET_ACTIVITIES);
            for (ActivityInfo info : settings.activities) {
                String name = info.name.toLowerCase(Locale.ROOT);
                String label = string(info.loadLabel(activity.getPackageManager())).toLowerCase(Locale.ROOT);
                if (info.exported && info.enabled && name.contains("wifi") &&
                        (name.contains("intelligent") || label.contains("intelligent") || label.contains("인텔리전"))) {
                    try { activity.startActivity(new Intent().setComponent(new ComponentName(info.packageName, info.name))); return; }
                    catch (RuntimeException ignored) {}
                }
            }
        } catch (Exception ignored) {}
        if (instance != null && Build.MANUFACTURER.equalsIgnoreCase("samsung")) {
            instance.finishWork(); instance.navigatingWifi = true; instance.menuOpened = false; instance.events(true);
            ChromeAttendanceService service = instance;
            service.handler.postDelayed(() -> {
                if (service.navigatingWifi) Toast.makeText(service, "Wi-Fi의 ⋮ 메뉴에서 인텔리전트 Wi-Fi를 선택해 주세요", Toast.LENGTH_LONG).show();
                service.navigatingWifi = false; if (!service.working) service.events(false);
            }, 15000);
        } else {
            Toast.makeText(activity, "Wi-Fi 화면의 ⋮ 메뉴에서 ‘인텔리전트 Wi-Fi’를 선택해 주세요", Toast.LENGTH_LONG).show();
        }
        activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
    }
    private void navigateWifi() {
        AccessibilityNodeInfo root = focusedRoot();
        if (root == null || !"com.android.settings".equals(string(root.getPackageName()))) return;
        List<AccessibilityNodeInfo> nodes = new ArrayList<>(); collect(root, nodes, 0);
        AccessibilityNodeInfo item = exactButton(nodes, "인텔리전트 Wi-Fi", "인텔리전스 Wi-Fi", "Intelligent Wi-Fi");
        if (item != null && click(item)) { navigatingWifi = false; events(false); return; }
        if (!menuOpened) {
            AccessibilityNodeInfo menu = exactButton(nodes, "더보기", "옵션 더보기", "More options");
            if (menu != null) menuOpened = click(menu);
        }
    }
    @Override public void onInterrupt() { if(working)diagnostic("ACCESSIBILITY_INTERRUPTED",true);endSettingsMenu(false); endLockProbe("접근성 연결 중단"); finishWork(); }
    @Override public void onDestroy() {
        if(working)diagnostic("ACCESSIBILITY_SERVICE_DESTROYED",true);
        endSettingsMenu(false); endLockProbe("접근성 연결 종료"); finishWork(); navigatingWifi = false; handler.removeCallbacksAndMessages(null); instance = null; super.onDestroy();
    }
}
