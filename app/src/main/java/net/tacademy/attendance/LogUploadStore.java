package net.tacademy.attendance;

import android.content.*;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

/** A successful upload advances only through the immutable, reviewed snapshot. */
@android.annotation.SuppressLint("ApplySharedPref")
final class LogUploadStore {
    private static volatile boolean busy;
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("github_log_upload",Context.MODE_PRIVATE);}
    static boolean busy(){return busy;}
    private static SharedPreferences.Editor clearPending(SharedPreferences.Editor edit){
        return edit.remove("payload").remove("path").remove("fingerprint").remove("pending_from_ms").remove("pending_until_ms").remove("pending_attempted");
    }
    private static void migrate(Context c)throws Exception{
        SharedPreferences p=prefs(c);if(p.getInt("period_version",0)>=1)return;
        SharedPreferences.Editor edit=p.edit().putInt("period_version",1);
        String path=p.getString("path",""),payload=p.getString("payload","");
        if(GitHubLogClient.validPath(path)&&!payload.isEmpty()){
            JSONObject document=new JSONObject(payload);long through=Instant.parse(document.getString("exported_at")).toEpochMilli();
            edit.putString("observations",LogUploadWindow.remember(document.getJSONArray("records"),through).toString());
            if(GitHubLogClient.webUrl(path).equals(p.getString("last_url",""))){
                // This exact legacy snapshot has a confirmed success receipt; do not resend it after updating.
                clearPending(edit).putLong("uploaded_through_ms",through);
            }else{
                edit.putLong("pending_from_ms",0).putLong("pending_until_ms",through)
                    .putBoolean("pending_attempted",!p.getString("status","").startsWith("업로드 준비됨"));
            }
        }
        if(!edit.commit())throw new IOException("업로드 기간을 저장하지 못했습니다. 다시 시도해 주세요.");
    }
    static synchronized String prepare(Context c)throws Exception{
        CreatorTools.require();SharedPreferences p=prefs(c);if(busy)return p.getString("payload","");
        migrate(c);
        // An interrupted request must settle its original file before a newer snapshot replaces it.
        if(p.getBoolean("pending_attempted",false)&&!p.getString("payload","").isEmpty())return p.getString("payload","");
        LockLab.expire(c);if(LockLab.live(c,LockLab.id(c)))throw new IllegalStateException("진행 중인 테스트가 끝난 뒤 로그를 올려 주세요.");
        long after=p.getLong("uploaded_through_ms",0);
        LogUploadWindow.Selection selection=LogUploadWindow.select(LockLogExport.records(c),new JSONObject(p.getString("observations","{}")),after,System.currentTimeMillis());
        JSONArray records=selection.records();
        if(records.length()==0){
            SharedPreferences.Editor edit=clearPending(p.edit()).putString("observations",selection.observations().toString());
            if(!edit.commit())throw new IOException("로그 상태를 저장하지 못했습니다.");
            return "";
        }
        String fingerprint=LockLogExport.digest(LogUploadWindow.canonical(records));
        if(fingerprint.equals(p.getString("fingerprint",""))&&p.contains("payload"))return p.getString("payload","");
        String id=UUID.randomUUID().toString(),path="logs/lock-tests/"+LocalDate.now(Policy.ZONE)+"/"+id+".json";
        String payload=LockLogExport.document(c,records,id)
            .put("upload_window",new JSONObject().put("after_ms",after).put("through_ms",selection.through()).put("basis","observed_log_changes"))
            .toString(2)+"\n";
        if(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>GitHubLogClient.LIMIT)throw new IllegalStateException("로그가 너무 큽니다. 결과 전체 복사를 이용해 주세요.");
        if(!p.edit().putString("observations",selection.observations().toString()).putString("fingerprint",fingerprint).putString("path",path).putString("payload",payload)
                .putLong("pending_from_ms",after).putLong("pending_until_ms",selection.through()).putBoolean("pending_attempted",false)
                .putString("status","업로드 준비됨 · 내용을 확인한 뒤 로그 올리기를 누르세요.").commit())throw new IOException("업로드할 로그를 저장하지 못했습니다.");
        return payload;
    }
    static synchronized void start(Context context){start(context,null);}
    /** An injected client lets device tests exercise the same transaction without network or a real credential. */
    static synchronized void start(Context context,GitHubLogClient client){
        CreatorTools.require();if(busy)return;Context c=context.getApplicationContext();SharedPreferences p=prefs(c);
        String path=p.getString("path",""),payload=p.getString("payload","");long through=p.getLong("pending_until_ms",0);
        if(!GitHubLogClient.validPath(path)||payload.isEmpty()||through<=0)return;
        if(!p.edit().putBoolean("pending_attempted",true).putString("status","GitHub에 전송 중…").commit())return;
        busy=true;
        new Thread(()->{
            try{
                GitHubLogClient sender=client==null?new GitHubLogClient(GitHubLogClient.https(new GitHubTokenStore(c).read())):client;
                String url=sender.upload(path,payload);
                synchronized(LogUploadStore.class){
                    if(!clearPending(p.edit()).putString("last_url",url).putLong("uploaded_through_ms",through).putLong("last_uploaded_at_ms",System.currentTimeMillis())
                            .putString("status","업로드 완료 · 이 기간의 로그는 다음 업로드에서 제외됩니다.").commit())
                        throw new IOException("GitHub 저장은 완료했지만 휴대폰 기록 저장에 실패했습니다. 다시 눌러 확인해 주세요.");
                }
            }catch(java.net.SocketTimeoutException e){p.edit().putString("status","응답 시간이 초과되었습니다. 다시 누르면 같은 파일의 저장 여부부터 확인합니다.").commit();}
            catch(IOException e){p.edit().putString("status",e.getMessage()!=null&&e.getMessage().matches("(?s).*[가-힣].*")?e.getMessage():"인터넷 연결을 확인하고 다시 시도해 주세요. 같은 파일로 재시도합니다.").commit();}
            catch(Exception e){p.edit().putString("status","GitHub 연결을 다시 설정해 주세요. 로그는 휴대폰에 남아 있습니다.").commit();}
            finally{busy=false;}
        },"github-log-upload").start();
    }
    static String periodLabel(Context c){
        SharedPreferences p=prefs(c);long after=p.getLong("uploaded_through_ms",0),through=p.getLong("pending_until_ms",0);
        if(through==0)return after==0?"아직 업로드한 기간이 없습니다.":"업로드 완료 범위: "+time(after)+"까지";
        return "대상 기간: "+(after==0?"미업로드 기록 전체":time(after)+" 이후")+" ~ "+time(through)+"\n로그를 불러온 시각 기준 · 한국 시간";
    }
    private static String time(long millis){return Instant.ofEpochMilli(millis).atZone(Policy.ZONE).format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"));}
}
