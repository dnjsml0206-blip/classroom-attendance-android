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
    private boolean working, clicked, confirmationExpected, confirmationClicked, navigatingWifi, menuOpened;
    private Policy.Phase phase;
    private String day, key;
    private long started, pageSeen;
    private final Runnable timeout = () -> fail("Chrome에서 확인이 필요합니다", "출석 페이지와 기록을 확인해 주세요. 확인되지 않은 클릭은 반복하지 않습니다");
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
    @Override protected void onServiceConnected() { instance = this; events(false); }
    private void events(boolean enabled) {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = enabled ? AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED : 0;
            setServiceInfo(info);
        }
    }
    public static boolean openChrome(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.android.chrome")
                .putExtra("com.android.browser.application_id", context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent); return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "Google Chrome을 설치하거나 사용 설정해 주세요", Toast.LENGTH_LONG).show(); return false;
        }
    }
    public static void begin(Context context, Runnable done) {
        if (LockLab.readOnlyActive(context)) { if (done != null) done.run(); return; }
        Prefs prefs = new Prefs(context);
        if (instance == null) {
            prefs.status("Chrome 접근성 연결 필요", "연결 탭에서 출석 메이트 접근성을 켜 주세요");
            Notices.chromeAction(context, true, "접근성을 켜면 기존 Chrome 인증으로 출석 버튼을 처리합니다");
            if (done != null) done.run(); return;
        }
        if (instance.working || instance.navigatingWifi) { if (done != null) done.run(); return; }
        if (context.getSystemService(KeyguardManager.class).isKeyguardLocked() ||
                !context.getSystemService(PowerManager.class).isInteractive()) {
            prefs.status("잠금 해제 후 Chrome에서 확인", "출석 알림을 눌러 화면 잠금을 해제해 주세요");
            Notices.chromeAction(context, true, "잠금을 해제하고 알림을 누르면 기존 Chrome으로 출석을 확인합니다");
            if (done != null) done.run(); return;
        }
        ChromeAttendanceService service = instance;
        service.phase = prefs.phase(ZonedDateTime.now(Policy.ZONE));
        if (service.phase == Policy.Phase.IDLE) { if (done != null) done.run(); return; }
        service.day = LocalDate.now(Policy.ZONE).toString();
        service.key = service.day + ":" + service.phase.name();
        service.lockAttendanceToken = LockLab.live(context,LockLab.id(context)) && LockLab.prefs(context).getBoolean("allow_attendance",false) ? LockLab.id(context) : "";
        service.lockTrace.clear(); service.finished = done; service.working = true; service.clicked = false;
        service.confirmationExpected = false; service.confirmationClicked = false;
        service.started = SystemClock.elapsedRealtime(); service.pageSeen = 0;
        service.events(true);
        prefs.status("Chrome 출석 화면 확인 중", "기존 Chrome의 인증 상태를 사용합니다");
        Context launch = context instanceof Activity ? context : service;
        if (!openChrome(launch, SiteProtocol.PAGE)) { service.fail("Chrome 실행 실패", "Google Chrome 사용 설정을 확인해 주세요"); return; }
        service.handler.postDelayed(service.timeout, 35000);
        service.handler.postDelayed(service.tick, 1200);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (navigatingWifi && "com.android.settings".equals(string(event.getPackageName()))) { navigateWifi(); return; }
        if (working && "com.android.chrome".equals(string(event.getPackageName()))) {
            handler.removeCallbacks(tick); handler.postDelayed(tick, 300);
        }
    }
    private void inspect() {
        if (!working) return;
        if (LockLab.readOnlyActive(this) || (!lockAttendanceToken.isEmpty() && !LockLab.live(this,lockAttendanceToken))) { finishWork(); return; }
        Prefs prefs = new Prefs(this);
        if (!day.equals(LocalDate.now(Policy.ZONE).toString()) || prefs.phase(ZonedDateTime.now(Policy.ZONE)) != phase) { finishWork(); return; }
        if (!NetworkAccess.inspect(this).allowed()) { fail("Wi-Fi 연결 대기", "지정 Wi-Fi에 연결한 뒤 다시 확인합니다"); return; }
        if (getSystemService(KeyguardManager.class).isKeyguardLocked() || !getSystemService(PowerManager.class).isInteractive()) {
            fail("잠금 해제 후 확인", "잠금을 해제하고 Chrome 출석 알림을 눌러 주세요"); return;
        }
        AccessibilityNodeInfo root = focusedRoot();
        if (root == null || !"com.android.chrome".equals(string(root.getPackageName()))) { traceLock("Chrome 화면 노드 대기 · 현재 화면에서 Chrome을 읽지 못함"); return; }
        List<AccessibilityNodeInfo> nodes = new ArrayList<>(); collect(root, nodes, 0);
        String text = allText(nodes);
        if (confirmationExpected && !confirmationClicked && ChromePage.checkoutConfirmation(text)) {
            if (!prefs.auto()) { finishWork(); return; }
            AccessibilityNodeInfo confirm = exactButton(nodes, "확인", "OK");
            if (confirm != null && click(confirm)) { confirmationClicked = true; traceLock("퇴실 확인 대화상자 클릭 요청 전달됨"); }
            return;
        }
        boolean correctAddress = false;
        for (AccessibilityNodeInfo node : nodes)
            if ("com.android.chrome:id/url_bar".equals(node.getViewIdResourceName()) &&
                    ChromePage.attendanceAddress(string(node.getText()))) correctAddress = true;
        if (!correctAddress) { traceLock("Chrome 주소 확인 대기 · 지정 출결 주소를 읽지 못함"); return; }
        traceLock("Chrome 출결 주소 표시 확인 · 실제 출결 기록 확인 중");
        if (text.contains("인증절차를 진행한후") || text.contains("인증절차를 진행한 후")) {
            fail("Chrome 인증 확인 필요", "평소 인증해 둔 Chrome 일반 탭에서 출석 화면이 열리는지 확인해 주세요"); return;
        }
        if (pageSeen == 0) pageSeen = SystemClock.elapsedRealtime();
        // Wait for the site's asynchronous course and attendance checks before considering a button.
        if (SystemClock.elapsedRealtime() - pageSeen < 1800) return;
        try { if (!ChromePage.date(text).toString().equals(day)) { traceLock("출결일 불일치 · 오늘 기록이 아니어서 클릭 보류"); return; } }
        catch (RuntimeException e) { traceLock("출결일 읽기 대기 · 사이트 화면 형식 확인 필요"); return; }
        Policy.Record record;
        try { record = ChromePage.record(text); } catch (RuntimeException e) { traceLock("입실·퇴실 기록 읽기 대기 · 사이트 화면 형식 확인 필요"); return; }
        if (record.checkedIn()) {
            prefs.record(day, record);
            boolean completed = phase == Policy.Phase.ENTRY || record.checkedOut();
            if (completed) {
                prefs.raw.edit().remove("chrome_pending").apply();
                String title = phase == Policy.Phase.ENTRY ? "입실 완료" : "퇴실 완료";
                String time = phase == Policy.Phase.ENTRY ? record.in() : record.out();
                prefs.status(title + " · 감시 종료", "Chrome 사이트 기록 " + time);
                prefs.log(title + " " + time);
                Notices.attendanceCompleted(this, day, phase, record);
                Notices.chromeAction(this, false, "");
                if (record.checkedOut()) Notices.checkoutAttention(this, false, "");
                finishWork(); ScheduleController.reconcile(this); return;
            }
        }
        if (!prefs.auto()) { prefs.status("Chrome 조회 모드", "설정에서 자동 처리를 켜면 출석 버튼을 누릅니다"); finishWork(); return; }
        if (phase == Policy.Phase.CHECKOUT && !record.checkedIn()) { traceLock("오늘 입실 기록 없음 · 퇴실 클릭 보류"); return; }
        if (clicked) return;
        if (key.equals(prefs.raw.getString("chrome_pending", ""))) {
            fail("이전 클릭 결과 확인 필요", "Chrome에서 실제 기록을 확인한 뒤 연결 탭에서 보류를 해제해 주세요"); return;
        }
        AccessibilityNodeInfo button = exactButton(nodes, phase == Policy.Phase.ENTRY ? "입실" : "퇴실");
        if (button == null || !button.isEnabled()) { traceLock("출결 버튼 대기 · 클릭 가능한 "+(phase==Policy.Phase.ENTRY?"입실":"퇴실")+" 버튼을 읽지 못함"); return; }
        if (prefs.phase(ZonedDateTime.now(Policy.ZONE)) != phase || !NetworkAccess.inspect(this).allowed()) return;
        if (!prefs.raw.edit().putString("chrome_pending", key).commit()) { fail("처리 상태 저장 실패", "앱에서 상태를 확인해 주세요"); return; }
        clicked = click(button);
        traceLock((phase==Policy.Phase.ENTRY?"입실":"퇴실")+(clicked?" 버튼 클릭 요청 전달됨 · 사이트 기록 확인 전":" 버튼 클릭 요청 실패"));
        confirmationExpected = clicked && phase == Policy.Phase.CHECKOUT;
        if (!clicked) prefs.raw.edit().remove("chrome_pending").apply();
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
    private void traceLock(String message) {
        if(!lockAttendanceToken.isEmpty()&&LockLab.live(this,lockAttendanceToken)&&lockTrace.add(message))LockLab.event(this,lockAttendanceToken,message);
    }
    private void fail(String title, String detail) {
        if (!working) return;
        if (LockLab.readOnlyActive(this) || (!lockAttendanceToken.isEmpty() && !LockLab.live(this,lockAttendanceToken))) { finishWork(); return; }
        Prefs prefs = new Prefs(this);
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
        working = false; lockAttendanceToken=""; handler.removeCallbacks(timeout); handler.removeCallbacks(tick);
        if (!navigatingWifi && lockProbe.isEmpty()) events(false);
        Runnable done = finished; finished = null;
        if (done != null) done.run();
    }
    public static void cancelWork() { if (instance != null) instance.finishWork(); }
    public static void launchLockTest(Context context, Intent intent) {
        Context source=instance!=null?instance:context;
        LockLab.event(context,intent.getStringExtra(LockLab.TOKEN),instance!=null?"접근성 서비스에서 화면 진입 시도":"앱에서 화면 진입 시도 · Android 제한 가능");
        source.startActivity(intent);
    }
    public static void probeLockChrome(Context context,String token) {
        if(!LockLab.live(context,token))return;
        if(instance==null){
            LockLab.finish(context,token,"Chrome 접근성 연결 끊김 · 화면 확인과 클릭을 시작하지 않았습니다");return;
        }
        ChromeAttendanceService service=instance;
        if(service.working||service.navigatingWifi){LockLab.finish(context,token,"다른 Chrome/설정 작업 진행 중 · 테스트 보류");return;}
        service.endLockProbe(null);service.lockProbe=token;service.lockProbeUntil=SystemClock.elapsedRealtime()+20000;service.events(true);
        LockLab.stage(context,token,"Chrome 실행 요청 · 출결 주소 표시 확인 중");
        if(!openChrome(context,SiteProtocol.PAGE)){service.endLockProbe("Chrome 실행 실패");return;}
        service.handler.postDelayed(service.lockProbeTick,600);
    }
    private final Runnable lockProbeTick=new Runnable(){@Override public void run(){
        if(lockProbe.isEmpty())return;
        if(!LockLab.live(ChromeAttendanceService.this,lockProbe)){endLockProbe(null);return;}
        if(getSystemService(KeyguardManager.class).isKeyguardLocked() || !getSystemService(PowerManager.class).isInteractive()){
            endLockProbe("Chrome 연결 중 다시 잠김 또는 화면 꺼짐");return;
        }
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
    private void endLockProbe(String result){
        String token=lockProbe;lockProbe="";handler.removeCallbacks(lockProbeTick);
        if(!working&&!navigatingWifi)events(false);
        if(result!=null&&!token.isEmpty())LockLab.finish(this,token,result);
    }
    public static void cancelLockProbe(){if(instance!=null)instance.endLockProbe(null);}
    public static void intelligentWifi(Activity activity) {
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
    @Override public void onInterrupt() { endLockProbe("접근성 연결 중단"); finishWork(); }
    @Override public void onDestroy() {
        endLockProbe("접근성 연결 종료"); finishWork(); navigatingWifi = false; handler.removeCallbacksAndMessages(null); instance = null; super.onDestroy();
    }
}
