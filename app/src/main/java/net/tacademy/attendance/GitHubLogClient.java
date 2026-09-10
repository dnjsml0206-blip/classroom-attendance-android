package net.tacademy.attendance;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;
import org.json.*;

/** Creates immutable files only in the configured private repository and diagnostics branch. */
final class GitHubLogClient {
    static final String REPO="dnjsml0206-blip/classroom-attendance-android", BRANCH="codex/device-logs";
    static final String ROOT="/repos/"+REPO;
    static final int LIMIT=256*1024;
    record Response(int code,JSONObject json){}
    interface Transport {Response call(String method,String path,JSONObject body)throws Exception;}
    private final Transport transport;
    GitHubLogClient(Transport transport){this.transport=transport;}
    static String webUrl(String path){return "https://github.com/"+REPO+"/blob/"+BRANCH+"/"+path;}
    static boolean validPath(String path){return path.matches("logs/lock-tests/\\d{4}-\\d{2}-\\d{2}/[a-f0-9-]{36}\\.json");}
    static String blobSha(String value)throws Exception{
        byte[] data=value.getBytes(StandardCharsets.UTF_8);MessageDigest md=MessageDigest.getInstance("SHA-1");
        md.update(("blob "+data.length+"\0").getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();
        for(byte b:md.digest(data))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    String upload(String path,String json)throws Exception{
        if(!validPath(path)||json.getBytes(StandardCharsets.UTF_8).length>LIMIT)throw new IOException("로그 파일 형식 또는 크기를 확인해 주세요.");
        Response repo=transport.call("GET",ROOT,null);require(repo,200);
        if(!repo.json.optBoolean("private")||!REPO.equalsIgnoreCase(repo.json.optString("full_name")))throw new IOException("지정한 비공개 저장소인지 확인되지 않아 전송을 중단했습니다.");
        Response branch=transport.call("GET",ROOT+"/git/ref/heads/"+BRANCH,null);
        if(branch.code==404)throw new IOException("로그 보관 브랜치가 없습니다. 앱 개발자에게 알려 주세요.");require(branch,200);
        String resource=ROOT+"/contents/"+path;
        Response existing=transport.call("GET",resource+"?ref=codex%2Fdevice-logs",null);
        String sha=blobSha(json);
        if(existing.code==200){
            if(!"file".equals(existing.json.optString("type"))||!sha.equals(existing.json.optString("sha")))throw new IOException("같은 이름의 다른 파일이 있습니다. 기존 파일을 덮어쓰지 않았습니다.");
            return webUrl(path);
        }
        if(existing.code!=404)require(existing,200);
        JSONObject body=new JSONObject().put("message","Add device lock test log "+path.substring(path.lastIndexOf('/')+1))
            .put("branch",BRANCH).put("content",Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
        Response sent=transport.call("PUT",resource,body);require(sent,201);
        JSONObject content=sent.json.optJSONObject("content");
        if(content==null||!sha.equals(content.optString("sha")))throw new IOException("전송 결과가 불확실합니다. 다시 누르면 같은 파일을 확인합니다.");
        return webUrl(path);
    }
    private static void require(Response r,int code)throws IOException{if(r.code!=code)throw new IOException(message(r.code));}
    static String message(int code){return switch(code){
        case 401 -> "GitHub 인증이 만료되었거나 올바르지 않습니다. 연결을 다시 설정해 주세요.";
        case 403 -> "GitHub 권한 또는 요청 제한을 확인해 주세요. 해당 저장소의 Contents 쓰기 권한이 필요합니다.";
        case 404 -> "저장소에 접근할 수 없습니다. 계정과 선택한 저장소를 확인해 주세요.";
        case 409,422 -> "GitHub에서 저장을 완료하지 못했습니다. 잠시 후 다시 눌러 확인해 주세요.";
        case 429 -> "요청이 많아 잠시 제한되었습니다. 나중에 다시 시도해 주세요.";
        default -> "GitHub 응답을 확인하지 못했습니다 (HTTP "+code+"). 다시 누르면 같은 파일을 확인합니다.";
    };}
    static Transport https(String token){return (method,path,body)->{
        if(!path.startsWith(ROOT)||path.contains(".."))throw new IOException("허용되지 않은 업로드 경로입니다.");
        HttpsURLConnection c=(HttpsURLConnection)new URL("https://api.github.com"+path).openConnection();
        try{
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setUseCaches(false);c.setRequestMethod(method);
            c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Accept","application/vnd.github+json");
            c.setRequestProperty("X-GitHub-Api-Version","2026-03-10");c.setRequestProperty("User-Agent","AttendanceMate-LogUpload");
            if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setDoOutput(true);c.setFixedLengthStreamingMode(bytes.length);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
            int code=c.getResponseCode();if(code!=200&&code!=201)return new Response(code,new JSONObject());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>LIMIT*2)throw new IOException("GitHub 응답 크기 제한을 초과했습니다.");out.write(buffer,0,n);}
                return new Response(code,new JSONObject(out.toString(StandardCharsets.UTF_8.name())));
            }
        }finally{c.disconnect();}
    };}
}
