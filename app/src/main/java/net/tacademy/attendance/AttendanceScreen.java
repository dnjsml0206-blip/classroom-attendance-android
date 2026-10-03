package net.tacademy.attendance;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.widget.*;
import android.os.*;
import android.view.*;
import org.json.*;

/** A temporary processing page; Chrome stays awake until confirmed attendance permits sleep. */
final class AttendanceScreen {
    static final int SCREEN_OFF_TIMEOUT_MS=6000;
    private final ChromeAttendanceService service;
    private final LockdownController lockdown;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private View processingPage;
    private Policy.Phase phase=Policy.Phase.IDLE;
    private String attempt="";
    private boolean lockPending,chromeSeen;
    private long lockRequestedAt;
    AttendanceScreen(ChromeAttendanceService service){this.service=service;this.lockdown=new LockdownController(service);}
    boolean busy(){return lockPending||lockdown.active();}
    void testLockdown(){cancel();phase=Policy.Phase.IDLE;attempt="lockdown-test:"+java.util.UUID.randomUUID();lockdown.start(phase,attempt,this::screenOff,this::screenOff);}
    private JSONObject details(){JSONObject d=new JSONObject();try{d.put("attempt_id",attempt).put("processing_page_visible",processingPage!=null);
            KeyguardManager k=service.getSystemService(KeyguardManager.class);
            Prefs p=new Prefs(service);
            d.put("screen_interactive",service.getSystemService(PowerManager.class).isInteractive()).put("keyguard_locked",k.isKeyguardLocked()).put("device_locked",k.isDeviceLocked()).put("device_secure",k.isDeviceSecure())
                .put("screen_off_enabled",p.screenOffAfterAttendance()).put("lockdown_enabled",p.lockdownAfterAttendance());}catch(JSONException ignored){}return d;}
    private void event(DiagnosticLog.Event e,String reason){JSONObject d=details();
        if((e==DiagnosticLog.Event.SCREEN_OFF_REQUEST||e==DiagnosticLog.Event.SCREEN_OFF_RESULT)&&lockRequestedAt>0)
            try{d.put("elapsed_ms",SystemClock.elapsedRealtime()-lockRequestedAt);}catch(JSONException ignored){}
        DiagnosticLog.add(service,e,phase,reason,d);
        if(e==DiagnosticLog.Event.SCREEN_OFF_RESULT)new Prefs(service).raw.edit().putString("screen_off_last_result",reason).putLong("screen_off_last_at",System.currentTimeMillis()).apply();}
    void begin(boolean automatic,Policy.Phase phase,String attempt){
        cancel();this.phase=phase;this.attempt=attempt;chromeSeen=false;
        if(!automatic||!new Prefs(service).showProcessingScreen())return;
        KeyguardManager keyguard=service.getSystemService(KeyguardManager.class);
        if(keyguard.isKeyguardLocked()||keyguard.isDeviceLocked()||!service.getSystemService(PowerManager.class).isInteractive())return;
        LinearLayout view=new LinearLayout(service);view.setOrientation(LinearLayout.VERTICAL);view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(Color.rgb(248,247,244));view.setPadding(dp(32),dp(24),dp(32),dp(24));
        // Only the explicit reveal button dismisses this page; a touch never passes through to Chrome.
        view.setClickable(true);
        ProgressBar progress=new ProgressBar(service);view.addView(progress,new LinearLayout.LayoutParams(dp(32),dp(32)));
        TextView title=new TextView(service);title.setText("출결 처리 중");title.setTextSize(22);title.setTextColor(Color.rgb(51,58,66));
        title.setGravity(Gravity.CENTER);title.setPadding(0,dp(24),0,dp(12));view.addView(title);
        TextView hint=new TextView(service);hint.setText(new Prefs(service).screenOffAfterAttendance()?"완료되면 화면이 꺼져요":"잠시만 기다려 주세요");
        hint.setTextSize(15);hint.setTextColor(Color.rgb(111,118,125));hint.setGravity(Gravity.CENTER);view.addView(hint);
        Button reveal=new Button(service);reveal.setText("Chrome 화면 보기");reveal.setAllCaps(false);
        LinearLayout.LayoutParams button=new LinearLayout.LayoutParams(-2,-2);button.topMargin=dp(32);view.addView(reveal,button);
        reveal.setOnClickListener(v->reveal("USER_REVEALED"));
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,PixelFormat.OPAQUE);
        p.gravity=Gravity.TOP|Gravity.START;p.setTitle("출석 메이트 · 출결 처리 중");
        try{service.getSystemService(WindowManager.class).addView(view,p);processingPage=view;event(DiagnosticLog.Event.SCREEN_PRESENTATION,"PROCESSING_PAGE_SHOWN");
            handler.postDelayed(()->reveal("PROCESSING_PAGE_TIME_LIMIT"),AttendanceAttemptPolicy.MAX_ATTEMPT_MS+3000);
        }catch(RuntimeException e){event(DiagnosticLog.Event.SCREEN_PRESENTATION,"PROCESSING_PAGE_"+DiagnosticLog.failureCode(e));}
    }
    private int dp(int value){return Math.round(value*service.getResources().getDisplayMetrics().density);}
    void foreground(boolean chrome,boolean launchingApp){
        if(chrome){chromeSeen=true;return;}
        // Chrome may need a moment to replace the launch activity. Retain the page during that handoff.
        if(chromeSeen||!launchingApp)reveal("CHROME_NOT_FOREGROUND");
    }
    void reveal(String reason){
        if(processingPage==null)return;
        View old=processingPage;processingPage=null;
        try{service.getSystemService(WindowManager.class).removeViewImmediate(old);}catch(RuntimeException ignored){}
        event(DiagnosticLog.Event.SCREEN_PRESENTATION,reason);
    }
    void confirmed(boolean eligible){
        if(!eligible){event(DiagnosticLog.Event.SCREEN_OFF_RESULT,"RECORD_LOOKUP_NO_LOCK");finish();return;}
        if(!new Prefs(service).screenOffAfterAttendance()){event(DiagnosticLog.Event.SCREEN_OFF_RESULT,"SCREEN_OFF_DISABLED");finish();return;}
        if(new Prefs(service).lockdownAfterAttendance()){
            reveal("LOCKDOWN_POWER_MENU");lockdown.start(phase,attempt,this::screenOff,this::screenOff);return;
        }
        screenOff();
    }
    private void screenOff(){
        if(Build.VERSION.SDK_INT<28){event(DiagnosticLog.Event.SCREEN_OFF_RESULT,"UNSUPPORTED_ANDROID_VERSION");finish();return;}
        if(!service.getSystemService(PowerManager.class).isInteractive()){event(DiagnosticLog.Event.SCREEN_OFF_RESULT,"ALREADY_OFF");finish();return;}
        lockRequestedAt=SystemClock.elapsedRealtime();event(DiagnosticLog.Event.SCREEN_OFF_REQUEST,"ATTENDANCE_CONFIRMED");
        boolean accepted;
        try{accepted=service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);}
        catch(RuntimeException e){event(DiagnosticLog.Event.SCREEN_OFF_RESULT,"LOCK_"+DiagnosticLog.failureCode(e));finish();return;}
        if(!accepted){event(DiagnosticLog.Event.SCREEN_OFF_RESULT,"SYSTEM_ACTION_REJECTED");finish();return;}
        lockPending=true;handler.post(observeOff);
    }
    private final Runnable observeOff=new Runnable(){@Override public void run(){
        if(!lockPending)return;
        boolean off=!service.getSystemService(PowerManager.class).isInteractive();
        if(off||SystemClock.elapsedRealtime()-lockRequestedAt>=SCREEN_OFF_TIMEOUT_MS){
            event(DiagnosticLog.Event.SCREEN_OFF_RESULT,off?"SCREEN_OFF_OBSERVED":"SCREEN_OFF_UNCONFIRMED");
            lockPending=false;finish();return;
        }
        handler.postDelayed(this,100);
    }};
    void finish(){if(!busy()){handler.removeCallbacksAndMessages(null);reveal("PROCESSING_PAGE_REMOVED");}}
    void cancel(){lockdown.cancel();lockPending=false;lockRequestedAt=0;handler.removeCallbacksAndMessages(null);reveal("PROCESSING_PAGE_CANCELLED");}
}
