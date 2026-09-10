package net.tacademy.attendance;

import android.content.*;
import java.time.*;
import java.util.*;
import org.json.*;

/** Persist the exact reviewed payload so retries cannot create duplicate files. */
@android.annotation.SuppressLint("ApplySharedPref")
final class LogUploadStore {
    private static volatile boolean busy;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("github_log_upload",Context.MODE_PRIVATE);}
    static boolean busy(){return busy;}
    static synchronized String prepare(Context c)throws Exception{
        if(busy)return prefs(c).getString("payload","");
        LockLab.expire(c);if(LockLab.live(c,LockLab.id(c)))throw new IllegalStateException("진행 중인 테스트가 끝난 뒤 로그를 올려 주세요.");
        JSONArray records=LockLogExport.records(c);if(records.length()==0)throw new IllegalStateException("아직 테스트 기록이 없습니다. 테스트 후 결과를 저장해 주세요.");
        String fingerprint=LockLogExport.digest(records.toString()),previous=prefs(c).getString("fingerprint","");
        if(fingerprint.equals(previous)&&prefs(c).contains("payload"))return prefs(c).getString("payload","");
        String id=UUID.randomUUID().toString(),path="logs/lock-tests/"+LocalDate.now(Policy.ZONE)+"/"+id+".json";
        String payload=LockLogExport.document(c,records,id).toString(2)+"\n";
        if(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>GitHubLogClient.LIMIT)throw new IllegalStateException("로그가 너무 큽니다. 결과 전체 복사를 이용해 주세요.");
        if(!prefs(c).edit().putString("fingerprint",fingerprint).putString("path",path).putString("payload",payload).putString("status","업로드 준비됨 · 내용을 확인한 뒤 로그 올리기를 누르세요.").commit())throw new java.io.IOException();
        return payload;
    }
    static synchronized void start(Context context){
        if(busy)return;Context c=context.getApplicationContext();String path=prefs(c).getString("path",""),payload=prefs(c).getString("payload","");
        if(!GitHubLogClient.validPath(path)||payload.isEmpty())return;
        busy=true;prefs(c).edit().putString("status","GitHub에 전송 중…").apply();
        new Thread(()->{
            try{
                String token=new GitHubTokenStore(c).read();
                String url=new GitHubLogClient(GitHubLogClient.https(token)).upload(path,payload);
                prefs(c).edit().putString("last_url",url).putString("status","업로드 완료 · 파일 링크를 복사해 Codex 대화에 알려 주세요.").commit();
            }catch(java.net.SocketTimeoutException e){prefs(c).edit().putString("status","응답 시간이 초과되었습니다. 다시 누르면 같은 파일의 저장 여부부터 확인합니다.").commit();}
            catch(java.io.IOException e){prefs(c).edit().putString("status",e.getMessage()!=null&&e.getMessage().matches("(?s).*[가-힣].*")?e.getMessage():"인터넷 연결을 확인하고 다시 시도해 주세요. 같은 파일로 재시도합니다.").commit();}
            catch(Exception e){prefs(c).edit().putString("status","GitHub 연결을 다시 설정해 주세요. 로그는 휴대폰에 남아 있습니다.").commit();}
            finally{busy=false;}
        },"github-log-upload").start();
    }
}
