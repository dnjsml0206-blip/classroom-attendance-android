package net.tacademy.attendance;

import android.app.*;
import android.os.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.time.*;

/** Own, minimal test screen above Keyguard. Android alone handles PIN/biometric authentication. */
public final class LockTrialActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String token;
    private boolean requested, handedOff, ownsTrial;
    private TextView message;
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(!LockLab.live(LockTrialActivity.this,token)){LockLab.expire(LockTrialActivity.this);finish();return;}
        if(!handedOff && hasWindowFocus())step();
        if(!isFinishing())handler.postDelayed(this,400);
    }};
    @SuppressWarnings("deprecation") @Override public void onCreate(Bundle state){
        super.onCreate(state);token=getIntent().getStringExtra(LockLab.TOKEN);
        if(!LockLab.live(this,token)){LockLab.expire(this);finish();return;}
        if(state==null && !LockLab.entered(this,token,getIntent().getStringExtra(LockLab.SOURCE))){finish();return;}
        ownsTrial=true;
        if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);}
        else getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setGravity(Gravity.CENTER_VERTICAL);
        int pad=(int)(24*getResources().getDisplayMetrics().density);layout.setPadding(pad,pad*2,pad,pad*2);layout.setBackgroundColor(0xFF101B2B);
        TextView title=new TextView(this);title.setText("잠금 상태 테스트");title.setTextColor(0xFFF3F6FC);title.setTextSize(26);title.setTypeface(null,Typeface.BOLD);layout.addView(title);
        message=new TextView(this);message.setText(getString(R.string.lock_trial_status,LockLab.mode(this).label));message.setTextSize(17);message.setTextColor(0xFFACBCD0);message.setPadding(0,pad,0,pad);layout.addView(message);
        Button stop=new Button(this);stop.setText("테스트 중단");stop.setOnClickListener(v -> {LockLab.cancel(this);finish();});layout.addView(stop);setContentView(layout);
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController bars=getWindow().getInsetsController();if(bars!=null)bars.setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}
        handler.postDelayed(tick,400);
    }
    private void step(){
        KeyguardManager keyguard=getSystemService(KeyguardManager.class);
        LockTestPolicy.Step step=LockTestPolicy.next(LockLab.mode(this),getSystemService(PowerManager.class).isInteractive(),keyguard.isKeyguardLocked(),keyguard.isDeviceLocked());
        if(step==LockTestPolicy.Step.WAIT_SCREEN)return;
        if(step==LockTestPolicy.Step.NEED_AUTH){
            LockLab.finish(this,token,LockLab.mode(this)==LockTestPolicy.Mode.WAKE?"화면 켜짐 · 잠금 해제가 필요해 Chrome 보류":"현재 인증 필요 · Extend Unlock 신뢰 활성 여부를 확인하세요");finish();return;
        }
        if(step==LockTestPolicy.Step.OPEN_CHROME){
            if(!requested)LockLab.event(this,token,"인증 입력 없이 진행 가능한 상태 · 신뢰 원인은 앱에서 판별하지 않음");
            handoff();return;
        }
        if(step==LockTestPolicy.Step.REQUEST_DISMISS && !requested){
            requested=true;LockLab.stage(this,token,"시스템 잠금 해제 요청 · "+LockLab.snapshot(this));
            message.setText("시스템 잠금 해제를 기다립니다.\n인증이 필요하면 휴대폰의 인증 화면을 완료해 주세요.");
            keyguard.requestDismissKeyguard(this,new KeyguardManager.KeyguardDismissCallback(){
                @Override public void onDismissSucceeded(){if(LockLab.live(LockTrialActivity.this,token))LockLab.event(LockTrialActivity.this,token,"시스템 잠금 해제 성공");}
                @Override public void onDismissCancelled(){LockLab.finish(LockTrialActivity.this,token,"시스템 인증 취소");finish();}
                @Override public void onDismissError(){LockLab.finish(LockTrialActivity.this,token,"시스템 잠금 해제 요청 오류");finish();}
            });
        }
    }
    private void handoff(){
        if(handedOff||!LockLab.live(this,token))return;
        KeyguardManager k=getSystemService(KeyguardManager.class);
        if(k.isKeyguardLocked()||k.isDeviceLocked()||!getSystemService(PowerManager.class).isInteractive())return;
        handedOff=true;LockLab.event(this,token,"Chrome 연결 전: "+LockLab.snapshot(this));
        LockLab.prefs(this).edit().putString("handoff_environment",LockLogExport.environment(this).toString()).apply();
        Prefs prefs=new Prefs(this);Policy.Phase phase=prefs.phase(ZonedDateTime.now(Policy.ZONE));
        if(LockLab.actual(this)){
            java.util.List<String> issues=TrialReadiness.problems(this,LockLab.mode(this),true,System.currentTimeMillis(),false);
            if(LockLab.prefs(this).getBoolean("checkout_trial",false)&&phase!=Policy.Phase.CHECKOUT)issues.add("예약한 퇴실 시간이 변경되었거나 퇴실이 이미 완료되었습니다.");
            if(!issues.isEmpty()){
                LockLab.finish(this,token,"실제 출결 중단: "+String.join(" / ",issues));
                if(phase==Policy.Phase.CHECKOUT)Notices.checkoutAttention(this,true,String.join(" / ",issues));
                finish();return;
            }
            LockLab.stage(this,token,"기존 시간·Wi-Fi 조건으로 실제 출결 요청");
            LockLab.prefs(this).edit().putBoolean("allow_attendance",true).apply();
            AttendanceEngine.request(this,true,() -> {
                Policy.Record record=prefs.history(LocalDate.now(Policy.ZONE));
                boolean completed=record!=null && (phase==Policy.Phase.ENTRY?record.checkedIn():record.checkedOut());
                LockLab.finish(this,token,completed?"사이트 기록으로 실제 출결 완료 확인":"실제 출결 미확인 · "+prefs.raw.getString("status","Chrome에서 확인해 주세요"));
            });
        } else {
            ChromeAttendanceService.probeLockChrome(this,token);
        }
        finish();
    }
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(ownsTrial && isFinishing() && !handedOff && LockLab.live(this,token))LockLab.cancel(this);super.onDestroy();}
}