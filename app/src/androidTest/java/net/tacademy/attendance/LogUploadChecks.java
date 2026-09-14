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
    private void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;Bundle progress=new Bundle();progress.putString("stream","PASS: "+name+"\n");test.sendStatus(0,progress);}
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
        JSONObject env=records.getJSONObject(0).getJSONObject("environment");
        check(doc.getInt("schema_version")==2&&env.has("background_location_permission")&&env.has("wifi_transport_present")&&env.has("ssid_visible")&&env.has("accessibility_enabled"),"Schema 2 includes permission and Wi-Fi visibility diagnostics");
        check(records.getJSONObject(0).getJSONObject("evidence").optBoolean("scheduled_entry")&&!records.getJSONObject(0).getJSONObject("evidence").optBoolean("automatic_entry"),"Old scheduled-entry strings never claim automatic unlock");
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
        periodChecks(first,path);
        activity=test.startActivitySync(new Intent(c,LogUploadActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
        check(find(activity.getWindow().getDecorView(),"GitHub에 로그 올리기")!=null,"Upload preview screen opens");
        check(find(activity.getWindow().getDecorView(),"새로 올릴 로그 없음")!=null&&!find(activity.getWindow().getDecorView(),"새로 올릴 로그 없음").isEnabled(),"No-new-log screen disables uploading while retaining the last link");
        test.runOnMainSync(()->{LockLab.verdict(c,"성공","UI refresh fixture");find(activity.getWindow().getDecorView(),"최신 기록 불러오기").performClick();});
        check(find(activity.getWindow().getDecorView(),"로그 올리기")!=null&&find(activity.getWindow().getDecorView(),"로그 올리기").isEnabled(),"Loading a revised test enables the next period on the visible screen");
        String uiPayload=LogUploadStore.prefs(c).getString("payload","");LogUploadStore.start(c,accepting(uiPayload,false,new int[]{0}));awaitUpload();
        SystemClock.sleep(1400);test.waitForIdleSync();
        check(find(activity.getWindow().getDecorView(),"새로 올릴 로그 없음")!=null&&!find(activity.getWindow().getDecorView(),"새로 올릴 로그 없음").isEnabled(),"Successful upload refreshes the visible screen without reopening it");
        android.graphics.Bitmap screenshot=test.getUiAutomation().takeScreenshot();
        if(screenshot!=null){try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"log-upload-period.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{screenshot.recycle();}}
        test.runOnMainSync(()->{TextView button=find(activity.getWindow().getDecorView(),"GitHub 업로드 연결 설정");if(button==null)throw new AssertionError("Connection control");button.performClick();});test.waitForIdleSync();test.getUiAutomation().performGlobalAction(1);test.waitForIdleSync();
        // Synthetic export for host-side transport verification; explicitly not a user's device report.
        doc.put("verification_fixture",true);try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"upload-fixture.json"))){out.write((doc.toString(2)+"\n").getBytes(StandardCharsets.UTF_8));}
        restore();result.putString("stream","PASS: "+count+" upload/export/credential checks. Fake transport and synthetic data only.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private void periodChecks(String first,String firstPath)throws Exception{
        Map<String,?> initialLab=new HashMap<>(LockLab.prefs(c).getAll());
        JSONObject a=new JSONObject().put("test_id","a").put("created_at_ms",1).put("user_note","old");
        LogUploadWindow.Selection one=LogUploadWindow.select(new JSONArray().put(a),new JSONObject(),0,2000);
        JSONObject reordered=new JSONObject().put("user_note","old").put("created_at_ms",1).put("test_id","a");
        check(LogUploadWindow.select(new JSONArray().put(reordered),one.observations(),2000,3000).records().length()==0,"Already acknowledged records stay excluded regardless of JSON field order");
        JSONObject changed=new JSONObject(a.toString()).put("user_note","edited");
        LogUploadWindow.Selection two=LogUploadWindow.select(new JSONArray().put(changed),one.observations(),2000,2000);
        check(two.records().length()==1&&two.through()>2000,"Changed results in the same millisecond remain after the period boundary");
        JSONObject b=new JSONObject().put("test_id","b").put("created_at_ms",500);
        check(LogUploadWindow.select(new JSONArray().put(b),one.observations(),2000,1000).through()>2000,"Clock rollback cannot silently skip a newly observed test");
        JSONObject legacy=new JSONObject().put("test_id",JSONObject.NULL).put("legacy_report","undated example");
        check(LogUploadWindow.select(new JSONArray().put(legacy),LogUploadWindow.remember(new JSONArray().put(legacy),2000),2000,3000).records().length()==0,"Undated legacy records are acknowledged once without inventing a test date");
        long firstThrough=LogUploadStore.prefs(c).getLong("pending_until_ms",0);
        check(firstThrough>0&&new JSONObject(first).getJSONObject("upload_window").getLong("after_ms")==0,"First report records its exact upload period");
        LogUploadStore.start(c,new GitHubLogClient((m,u,body)->response(503,new JSONObject())));awaitUpload();
        check(LogUploadStore.prefs(c).getLong("uploaded_through_ms",0)==0&&LogUploadStore.prefs(c).getBoolean("pending_attempted",false),"Failed upload does not advance the successful period");
        JSONArray oldReports=new JSONArray(LockLab.prefs(c).getString("history","[]"));
        JSONObject original=LockLogExport.current(c);
        LockLab.prefs(c).edit().putString("history",new JSONArray().put(LockLab.report(c)).put(oldReports.getString(0)).toString())
            .putString("history_details",new JSONArray().put(original).put(LockLogExport.legacy(oldReports.getString(0))).toString())
            .putString("id","new-test-b").putLong("created",System.currentTimeMillis()).putString("events","new test event").putString("note","").commit();
        check(first.equals(LogUploadStore.prepare(c))&&firstPath.equals(LogUploadStore.prefs(c).getString("path","")),"A new test cannot replace an upload whose result is still unconfirmed");
        int[] writes={0};
        LogUploadStore.start(c,accepting(first,true,writes));awaitUpload();
        check(writes[0]==0&&LogUploadStore.prefs(c).getLong("uploaded_through_ms",0)==firstThrough,"Retry finds the existing identical file and acknowledges only its original period");
        String second=LogUploadStore.prepare(c);JSONArray secondRecords=new JSONObject(second).getJSONArray("records");
        check(secondRecords.length()==1&&secondRecords.getJSONObject(0).getString("test_id").equals("new-test-b"),"The next upload contains only B, excluding uploaded A and legacy history");
        check(new JSONObject(second).getJSONObject("upload_window").getLong("after_ms")==firstThrough,"Next report starts after the last successfully uploaded snapshot");
        check(second.equals(LogUploadStore.prepare(c)),"Refreshing unchanged new records preserves reviewed bytes");
        java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        GitHubLogClient.Transport accepted=acceptingTransport(second,false,writes);
        LogUploadStore.start(c,new GitHubLogClient((m,u,body)->{if(m.equals("PUT")){entered.countDown();if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("Test release timeout");}return accepted.call(m,u,body);}));
        try{
            if(!entered.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Upload did not start");
            LockLab.verdict(c,"성공","late edited note","MANUAL_UNLOCK");
            check(second.equals(LogUploadStore.prepare(c)),"Changes made during transfer do not alter the in-flight report");
        }finally{release.countDown();}
        awaitUpload();String revised=LogUploadStore.prepare(c);
        check(new JSONObject(revised).getJSONArray("records").length()==1&&revised.contains("late edited note"),"A note edited after preview or during transfer is included in the next period");
        LogUploadStore.start(c,accepting(revised,false,writes));awaitUpload();
        check(LogUploadStore.prepare(c).isEmpty()&&!LogUploadStore.prefs(c).contains("payload"),"Successful upload leaves no pending report when nothing new exists");
        String last=LogUploadStore.prefs(c).getString("last_url","");
        check(!last.isEmpty()&&LogUploadStore.prefs(c).getLong("last_uploaded_at_ms",0)>0,"Completion retains the last file link and successful upload time");
        int before=writes[0];LogUploadStore.start(c,accepting(revised,false,writes));
        check(!LogUploadStore.busy()&&before==writes[0],"An empty period cannot submit a duplicate upload");
        // Upgrade an exact saved success receipt from the old uploader.
        restorePrefs(LockLab.prefs(c),initialLab);
        LogUploadStore.prefs(c).edit().clear().putString("payload",first).putString("path",firstPath)
            .putString("last_url",GitHubLogClient.webUrl(firstPath)).putString("status","업로드 완료").commit();
        check(LogUploadStore.prepare(c).isEmpty()&&LogUploadStore.prefs(c).getLong("uploaded_through_ms",0)>0,"Updating the app imports a confirmed legacy receipt without reuploading it");
        LockLab.verdict(c,"실패","edited after previous-version upload");
        String afterUpgrade=LogUploadStore.prepare(c);
        check(new JSONObject(afterUpgrade).getJSONArray("records").length()==1&&afterUpgrade.contains("edited after previous-version upload"),"An edited old test is still included after migrating a successful receipt");
        // An older interrupted upload is not a success receipt.
        restorePrefs(LockLab.prefs(c),initialLab);
        LogUploadStore.prefs(c).edit().clear().putString("payload",first).putString("path",firstPath).putString("status","인터넷 연결을 확인해 주세요.").commit();
        check(first.equals(LogUploadStore.prepare(c))&&LogUploadStore.prefs(c).getLong("uploaded_through_ms",0)==0,"An unconfirmed legacy request remains retryable after updating");
        LogUploadStore.start(c,accepting(first,true,writes));awaitUpload();
        check(LogUploadStore.prepare(c).isEmpty(),"A confirmed legacy retry also removes its logs from future uploads");
    }
    private GitHubLogClient accepting(String payload,boolean existing,int[] writes)throws Exception{return new GitHubLogClient(acceptingTransport(payload,existing,writes));}
    private GitHubLogClient.Transport acceptingTransport(String payload,boolean existing,int[] writes)throws Exception{
        String sha=GitHubLogClient.blobSha(payload);
        return (method,url,body)->{
            if(url.equals(GitHubLogClient.ROOT))return response(200,new JSONObject().put("private",true).put("full_name",GitHubLogClient.REPO));
            if(url.contains("/git/ref/"))return response(200,new JSONObject());
            if(method.equals("GET"))return response(existing?200:404,existing?new JSONObject().put("type","file").put("sha",sha):new JSONObject());
            writes[0]++;if(!payload.equals(new String(Base64.getDecoder().decode(body.getString("content")),StandardCharsets.UTF_8)))throw new AssertionError("Changed upload payload");
            return response(201,new JSONObject().put("content",new JSONObject().put("sha",sha)));
        };
    }
    private void awaitUpload(){long until=SystemClock.elapsedRealtime()+10000;while(LogUploadStore.busy()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(20);if(LogUploadStore.busy())throw new AssertionError("Upload thread did not finish");}
    private static GitHubLogClient.Response response(int code,JSONObject json){return new GitHubLogClient.Response(code,json);}
    private TextView find(View view,String label){if(view instanceof TextView&&((TextView)view).getText().toString().equals(label))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=find(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
    private void restore(){if(c==null)return;test.runOnMainSync(()->{if(activity!=null)activity.finish();restorePrefs(LockLab.prefs(c),savedLab);restorePrefs(LogUploadStore.prefs(c),savedUpload);});try{if(vault!=null)vault.clear();}catch(Exception ignored){}}
    @SuppressWarnings("unchecked")private void restorePrefs(SharedPreferences p,Map<String,?> values){if(values==null)return;SharedPreferences.Editor e=p.edit().clear();for(Map.Entry<String,?> item:values.entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<>((Set<String>)v));}e.commit();}
}
