package net.tacademy.attendance;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** A bounded, visible wake bridge. Credentials are handled exclusively by Android. */
public final class ScheduledUnlockActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String token="";private boolean requested,handedOff;private TextView message;
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(!ScheduledUnlock.live(ScheduledUnlockActivity.this,token)){ScheduledUnlock.stop(ScheduledUnlockActivity.this,token,"WAKE_CANCELLED_OR_EXPIRED",false);finish();return;}
        if(!NetworkAccess.inspect(ScheduledUnlockActivity.this).allowed()){stop("WIFI_LOST");return;}
        if(!handedOff&&hasWindowFocus()){ScheduledUnlock.entered(ScheduledUnlockActivity.this,token,true);step();}
        if(!isFinishing())handler.postDelayed(this,400);
    }};
    @SuppressWarnings("deprecation") @Override public void onCreate(Bundle state){
        super.onCreate(state);token=getIntent().getStringExtra(ScheduledUnlock.TOKEN);
        if(token==null||!ScheduledUnlock.attach(this,token,state!=null)){finish();return;}
        requested=state!=null&&state.getBoolean("dismiss_requested",false);
        if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setGravity(Gravity.CENTER_VERTICAL);
        int pad=(int)(24*getResources().getDisplayMetrics().density);body.setPadding(pad,pad*2,pad,pad*2);body.setBackgroundColor(0xFF101B2B);
        TextView title=new TextView(this);title.setText(ScheduledUnlock.phase(this)==Policy.Phase.ENTRY?"입실 예약 실행":"퇴실 예약 실행");title.setTextSize(26);title.setTextColor(0xFFF3F6FC);body.addView(title);
        message=new TextView(this);message.setText(getString(R.string.unlock_initial_status,ScheduledUnlock.mode(this).label));message.setTextSize(17);message.setTextColor(0xFFACBCD0);message.setPadding(0,pad,0,pad);body.addView(message);
        Button stop=new Button(this);stop.setText("이번 화면 깨우기 중단");stop.setOnClickListener(v->stop("USER_CANCELLED"));body.addView(stop);setContentView(body);
        handler.post(tick);
    }
    private void step(){
        KeyguardManager keyguard=getSystemService(KeyguardManager.class);
        LockTestPolicy.Step next=UnlockPolicy.next(ScheduledUnlock.mode(this),getSystemService(PowerManager.class).isInteractive(),keyguard.isKeyguardLocked(),keyguard.isDeviceLocked());
        if(next==LockTestPolicy.Step.WAIT_SCREEN)return;
        if(next==LockTestPolicy.Step.NEED_AUTH){
            DiagnosticLog.add(this,DiagnosticLog.Event.WAKE_AUTH_REQUIRED,ScheduledUnlock.phase(this),"USER_AUTH_REQUIRED",ScheduledUnlock.details(this));
            stop("USER_AUTH_REQUIRED");return;
        }
        if(next==LockTestPolicy.Step.OPEN_CHROME){
            if(!ScheduledUnlock.handoff(this,token)){stop("HANDOFF_CONDITIONS_CHANGED");return;}
            handedOff=true;AttendanceEngine.request(this,true,null);finish();return;
        }
        if(next==LockTestPolicy.Step.REQUEST_DISMISS&&!requested){
            requested=true;message.setText("Android의 잠금 해제를 기다립니다.\n인증 화면이 나타나면 PIN·지문으로 직접 인증해 주세요.");
            DiagnosticLog.add(this,DiagnosticLog.Event.WAKE_DISMISS_REQUEST,ScheduledUnlock.phase(this),"SYSTEM_REQUEST",ScheduledUnlock.details(this));
            try{keyguard.requestDismissKeyguard(this,new KeyguardManager.KeyguardDismissCallback(){
                @Override public void onDismissSucceeded(){if(ScheduledUnlock.live(ScheduledUnlockActivity.this,token))DiagnosticLog.add(ScheduledUnlockActivity.this,DiagnosticLog.Event.WAKE_DISMISS_RESULT,ScheduledUnlock.phase(ScheduledUnlockActivity.this),"SYSTEM_DISMISSED",ScheduledUnlock.details(ScheduledUnlockActivity.this));}
                @Override public void onDismissCancelled(){stop("AUTH_CANCELLED");}
                @Override public void onDismissError(){stop("DISMISS_ERROR");}
            });}catch(RuntimeException e){stop("DISMISS_"+DiagnosticLog.failureCode(e));}
        }
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("dismiss_requested",requested);}
    private void stop(String reason){ScheduledUnlock.stop(this,token,reason,true);finish();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(isFinishing()&&!handedOff&&token!=null)ScheduledUnlock.stop(this,token,"ACTIVITY_CLOSED",false);super.onDestroy();}
}
