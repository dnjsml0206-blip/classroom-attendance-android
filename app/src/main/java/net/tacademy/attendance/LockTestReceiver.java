package net.tacademy.attendance;

import android.content.*;
import android.os.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;

/** One alarm, one attempt. No recurring background service or lock-screen full-screen intent. */
public final class LockTestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String token=intent.getStringExtra(LockLab.TOKEN);
        if(!LockLab.live(context,token)){LockLab.expire(context);return;}
        boolean network=LockLab.mode(context)==LockTestPolicy.Mode.NETWORK;
        if(LockLab.prefs(context).getBoolean("fired",false))return;
        LockLab.fire(context,token,"예약 요청에서 진입 · 무조작 여부 미확인");
        if(network){PendingResult result=goAsync();new Thread(() -> {try{diagnose(context,token);}finally{result.finish();}},"lock-network-diagnostic").start();}
    }
    static void diagnose(Context c,String token){
        if(!LockLab.live(c,token))return;
        PowerManager.WakeLock cpu=c.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"attendance:lock-network-test");
        HttpsURLConnection connection=null;
        try{
            cpu.acquire(12000);
            NetworkAccess.State wifi=NetworkAccess.inspect(c);
            if(!wifi.allowed()){LockLab.finish(c,token,"서버 진단 보류: "+wifi.reason());return;}
            // Public authentication landing page only. Never submit attendance or reuse Chrome cookies.
            connection=(HttpsURLConnection)wifi.network().openConnection(new URL(SiteProtocol.AUTH_PAGE));
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(4000);connection.setReadTimeout(4000);
            connection.setUseCaches(false);connection.setRequestMethod("GET");
            int code=connection.getResponseCode();
            if(LockLab.live(c,token)){
                LockLab.event(c,token,"HTTPS 응답 "+code+" · 종료 당시: "+LockLab.snapshot(c));
                LockLab.finish(c,token,"서버 연결 응답 확인 · 앱 인증/API 출결은 미연동");
            }
        }catch(Exception e){LockLab.finish(c,token,"서버 연결 진단 실패: "+e.getClass().getSimpleName());}
        finally{if(connection!=null)connection.disconnect();if(cpu.isHeld())cpu.release();}
    }
}