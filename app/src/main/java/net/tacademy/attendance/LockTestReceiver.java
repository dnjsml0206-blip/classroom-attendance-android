package net.tacademy.attendance;

import android.content.*;
import android.os.*;

/** One alarm, one attempt. No recurring background service or lock-screen full-screen intent. */
public final class LockTestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String token=intent.getStringExtra(LockLab.TOKEN);
        if(!LockLab.live(context,token)){LockLab.expire(context);return;}
        boolean network=LockLab.mode(context)==LockTestPolicy.Mode.NETWORK;
        if(LockLab.prefs(context).getBoolean("fired",false))return;
        LockLab.fire(context,token,"예약 요청에서 진입 · 무조작 여부 미확인");
        if(network)diagnose(context,token);
    }
    static void diagnose(Context c,String token){
        if(!LockLab.live(c,token))return;
        NetworkAccess.State wifi=NetworkAccess.inspect(c);
        DiagnosticLog.add(c,DiagnosticLog.Event.NETWORK_DIAGNOSTIC_LOCAL,Policy.Phase.IDLE,wifi.code());
        LockLab.finish(c,token,wifi.allowed()?"기기 연결 조건 확인 · 서버 추가 접속 없이 종료":"서버 진단 보류: "+wifi.reason());
    }
}
