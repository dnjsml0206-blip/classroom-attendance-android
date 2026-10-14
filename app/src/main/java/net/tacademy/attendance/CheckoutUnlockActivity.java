package net.tacademy.attendance;
import android.app.*;
import android.os.*;
import android.view.*;
import java.time.*;

/** Opened only by the dated checkout notification. Authentication is handled by Android. */
public final class CheckoutUnlockActivity extends Activity {
    static final String DAY="checkout_day";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean requested,submitted;private long started;private ProcessingPage page;
    static boolean eligible(android.content.Context c,String day) {
        Prefs p=new Prefs(c);ZonedDateTime now=ZonedDateTime.now(Policy.ZONE);
        return CheckoutResumePolicy.allowed(day,now,p.enabled(),p.auto(),
            Policy.isClassDay(now,p.days(),p.automationExcluded()),p.checkedOutToday(),p.checkoutStartToday());
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        submitted=state!=null&&state.getBoolean("submitted",false);
        requested=state!=null&&state.getBoolean("requested",false);
        started=state==null?SystemClock.elapsedRealtime():state.getLong("started",SystemClock.elapsedRealtime());
        if(submitted||!eligible(this,getIntent().getStringExtra(DAY))){Notices.checkoutUnlock(this,false);finish();return;}
        ScheduledUnlock.cancel(this,"USER_NOTIFICATION_RESUME");
        if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        page=new ProcessingPage(this,Policy.Phase.CHECKOUT,"잠금 해제 후 퇴실",
            "자동 퇴실을 완료하지 못했어요.\n휴대폰 인증을 마치면 바로 퇴실을 진행합니다.","취소",this::finish);
        setContentView(page);
        DiagnosticLog.add(this,DiagnosticLog.Event.CHECKOUT_UNLOCK_NOTICE,Policy.Phase.CHECKOUT,"NOTIFICATION_OPENED");
        handler.post(tick);
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(isFinishing()||isDestroyed()||submitted)return;
        if(!eligible(CheckoutUnlockActivity.this,getIntent().getStringExtra(DAY))){Notices.checkoutUnlock(CheckoutUnlockActivity.this,false);finish();return;}
        if(SystemClock.elapsedRealtime()-started>=120000){finish();return;}
        KeyguardManager k=getSystemService(KeyguardManager.class);
        if(getSystemService(PowerManager.class).isInteractive()&&!k.isKeyguardLocked()&&!k.isDeviceLocked()&&hasWindowFocus()){
            if(!NetworkAccess.inspect(CheckoutUnlockActivity.this).allowed()){
                page.setMessage("등록된 강의실 Wi-Fi가 필요해요.\n연결되면 퇴실을 이어갑니다.");
            }else if(!ChromeAttendanceService.available()){
                page.setMessage("Chrome 접근성 연결이 필요해요.\n앱의 자동출석 설정에서 연결해 주세요.");
            }else if(ChromeAttendanceService.otherUiBusy()){
                page.setMessage("진행 중인 화면 처리가 끝나면 퇴실을 이어갑니다.");
            }else{
                submitted=true;
                DiagnosticLog.add(CheckoutUnlockActivity.this,DiagnosticLog.Event.CHECKOUT_UNLOCK_NOTICE,Policy.Phase.CHECKOUT,"AUTHENTICATED_RESUME");
                // A simultaneous USER_PRESENT may already have started automatic checkout.
                if(!ChromeAttendanceService.busy()&&!ChromeAttendanceService.otherUiBusy())
                    ChromeAttendanceService.beginManual(CheckoutUnlockActivity.this,Policy.Phase.CHECKOUT,false,false);
                finish();return;
            }
        }else if(hasWindowFocus()&&!requested){
            requested=true;
            try{k.requestDismissKeyguard(CheckoutUnlockActivity.this,new KeyguardManager.KeyguardDismissCallback(){
                @Override public void onDismissSucceeded(){handler.removeCallbacks(tick);handler.post(tick);}
                @Override public void onDismissCancelled(){finish();}
                @Override public void onDismissError(){requested=false;page.setMessage("잠금 해제를 요청하지 못했어요.\n직접 잠금을 해제한 뒤 다시 눌러 주세요.");finish();}
            });}catch(RuntimeException e){finish();}
        }
        handler.postDelayed(this,250);
    }};
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("submitted",submitted);state.putBoolean("requested",requested);state.putLong("started",started);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
