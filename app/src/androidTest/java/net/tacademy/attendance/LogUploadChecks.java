package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Emulator-only export, encryption and HTTP contract checks. No live token or server request. */
final class LogUploadChecks {
    private final Instrumentation test;private Context c;private Activity activity;private int count;
    private Map<String,?> savedLab,savedUpload;private GitHubTokenStore vault;
    LogUploadChecks(Instrumentation test){this.test=test;}
    private void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;}
    void run(){Bundle result=new Bundle();try{
        if(!Build.HARDWARE.equals("ranchu")&&!Build.HARDWARE.equals("goldfish"))throw new AssertionError("Emulator only");
        c=test.getTargetContext();savedLab=new HashMap<>(LockLab.prefs(c).getAll());savedUpload=new HashMap<>(LogUploadStore.prefs(c).getAll());
        vault=new GitHubTokenStore(c,"github_upload_test");
        String fake="github_pat_"+"syntheticForEncryptionTestOnly123456";
        vault.save(fake);check(fake.equals(vault.read())&&!c.getSharedPreferences("github_upload_test",0).getAll().toString().contains(fake),"Keystore encrypts and decrypts without storing token plaintext");
        vault.clear();check(!vault.configured(),"Disconnect removes local credential");
        String legacy="emulator\n방식: Extend Unlock\n실제 출결 시도: 꺼짐\n진행 결과: 현재 인증 필요\n사용자 확인: 실패\n메모: old\n\n08:40:00  실행 당시: 화면 꺼짐 · 인증 필요 예\n";
        LockLab.prefs(c).edit().clear().putString("id",UUID.randomUUID().toString()).putString("mode","WAKE").putLong("created",1000).putLong("due",16000)
            .putBoolean("active",false).putString("stage","Chrome 출결 주소 표시 확인").putString("verdict","부분 성공").putString("note",fake)
            .putString("events","08:40:00  실행 당시: 화면 꺼짐 · 인증 필요 아니요\n08:40:01  테스트 화면 진입 · 예약에서 자동 진입\n08:40:02  Chrome 출결 주소 표시 확인 · 사이트 인증과 출결 결과는 직접 확인\n")
            .putString("environment",LockLogExport.environment(c).toString()).putString("history",new JSONArray().put(legacy).toString()).commit();
        LogUploadStore.prefs(c).edit().clear().commit();
        String misleading=legacy.replace("메모: old\n\n", "메모: old\n\n08:00:00  Chrome 출결 주소 표시 확인 · 메모에 적은 예시\n\n");
        check(!LockLogExport.sanitize(LockLogExport.legacy(misleading)).getJSONObject("evidence").optBoolean("chrome_address_observed"),"Multiline legacy memo is not app event evidence");
        String first=LogUploadStore.prepare(c),path=LogUploadStore.prefs(c).getString("path","");
        check(first.equals(LogUploadStore.prepare(c))&&path.equals(LogUploadStore.prefs(c).getString("path","")),"Repeated export keeps identical reviewed payload and path");
        JSONObject doc=new JSONObject(first);JSONArray records=doc.getJSONArray("records");
        check(records.length()==2&&records.getJSONObject(1).optBoolean("legacy")&&records.getJSONObject(1).isNull("created_at_ms"),"Old text history is retained with unknown date");
        check(!first.contains(fake)&&records.getJSONObject(0).getJSONObject("evidence").optBoolean("chrome_address_observed")&&!records.getJSONObject(0).getJSONObject("evidence").optBoolean("attendance_record_confirmed"),"Secrets redacted and Chrome evidence separated from attendance");
        String sha=GitHubLogClient.blobSha(first);List<String> calls=new ArrayList<>();
        GitHubLogClient.Transport success=(method,url,body)->{
            calls.add(method+" "+url);
            if(url.equals(GitHubLogClient.ROOT))return response(200,new JSONObject().put("private",true).put("full_name",GitHubLogClient.REPO));
            if(url.contains("/git/ref/"))return response(200,new JSONObject());
            if(method.equals("GET"))return response(404,new JSONObject());
            if(!GitHubLogClient.BRANCH.equals(body.optString("branch"))||body.has("sha")||!first.equals(new String(Base64.getDecoder().decode(body.getString("content")),StandardCharsets.UTF_8)))throw new AssertionError("Unexpected PUT payload");
            return response(201,new JSONObject().put("content",new JSONObject().put("sha",sha)));
        };
        check(new GitHubLogClient(success).upload(path,first).equals(GitHubLogClient.webUrl(path))&&calls.stream().filter(s->s.startsWith("PUT")).count()==1,"Creates one immutable file with exactly reviewed bytes on diagnostics branch");
        GitHubLogClient.Transport retry=(method,url,body)->{if(url.equals(GitHubLogClient.ROOT))return response(200,new JSONObject().put("private",true).put("full_name",GitHubLogClient.REPO));if(url.contains("/git/ref/"))return response(200,new JSONObject());if(!method.equals("GET"))throw new AssertionError("Duplicate write");return response(200,new JSONObject().put("type","file").put("sha",sha));};
        check(new GitHubLogClient(retry).upload(path,first).equals(GitHubLogClient.webUrl(path)),"Retry after lost response detects existing identical file without another commit");
        boolean denied=false;try{new GitHubLogClient((m,u,b)->response(200,new JSONObject().put("private",false).put("full_name",GitHubLogClient.REPO))).upload(path,first);}catch(IOException e){denied=true;}check(denied,"Public repository receives no log upload");
        denied=false;try{new GitHubLogClient((m,u,b)->{if(u.equals(GitHubLogClient.ROOT))return response(200,new JSONObject().put("private",true).put("full_name",GitHubLogClient.REPO));if(u.contains("/git/ref/"))return response(200,new JSONObject());return response(200,new JSONObject().put("type","file").put("sha","different"));}).upload(path,first);}catch(IOException e){denied=true;}check(denied,"Existing different content is never overwritten");
        denied=false;try{new GitHubLogClient(success).upload("../unrelated.json",first);}catch(IOException e){denied=true;}check(denied,"Unexpected upload paths rejected before network");
        denied=false;try{new GitHubLogClient((m,u,b)->response(302,new JSONObject())).upload(path,first);}catch(IOException e){denied=true;}check(denied,"Redirect responses are not treated as successful upload");
        check(GitHubLogClient.message(401).contains("인증")&&GitHubLogClient.message(403).contains("권한")&&GitHubLogClient.message(429).contains("제한"),"Authentication and rate-limit failures actionable");
        activity=test.startActivitySync(new Intent(c,LogUploadActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
        check(find(activity.getWindow().getDecorView(),"GitHub에 로그 올리기")!=null,"Upload preview screen opens");
        test.runOnMainSync(()->{TextView button=find(activity.getWindow().getDecorView(),"GitHub 업로드 연결 설정");if(button==null)throw new AssertionError("Connection control");button.performClick();});test.waitForIdleSync();test.getUiAutomation().performGlobalAction(1);test.waitForIdleSync();
        // Synthetic export for host-side transport verification; explicitly not a user's device report.
        doc.put("verification_fixture",true);try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"upload-fixture.json"))){out.write((doc.toString(2)+"\n").getBytes(StandardCharsets.UTF_8));}
        restore();result.putString("stream","PASS: "+count+" upload/export/credential checks. Fake transport and synthetic data only.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private static GitHubLogClient.Response response(int code,JSONObject json){return new GitHubLogClient.Response(code,json);}
    private TextView find(View view,String label){if(view instanceof TextView&&((TextView)view).getText().toString().equals(label))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=find(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
    private void restore(){if(c==null)return;test.runOnMainSync(()->{if(activity!=null)activity.finish();restorePrefs(LockLab.prefs(c),savedLab);restorePrefs(LogUploadStore.prefs(c),savedUpload);});try{if(vault!=null)vault.clear();}catch(Exception ignored){}}
    @SuppressWarnings("unchecked")private void restorePrefs(SharedPreferences p,Map<String,?> values){if(values==null)return;SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<>((Set<String>)v));}e.commit();}
}
