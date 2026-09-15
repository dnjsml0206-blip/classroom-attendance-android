package net.tacademy.attendance;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** One nonce-scoped, loopback-only page. No attendance URL, credentials, redirect or outbound connection. */
final class LocalClickPage implements AutoCloseable {
    static final String BUTTON="자동 클릭 시험";
    private final ServerSocket socket;
    private final String token;
    private final long deadline=System.nanoTime()+40_000_000_000L;
    private volatile boolean closed;
    LocalClickPage(String token)throws IOException {
        if(!UUID.fromString(token).toString().equals(token))throw new IllegalArgumentException("Invalid token");
        this.token=token;socket=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));socket.setSoTimeout(1000);
        Thread worker=new Thread(this::serve,"local-click-page");worker.setDaemon(true);worker.start();
    }
    String url(){return "http://127.0.0.1:"+socket.getLocalPort()+"/click-"+token;}
    String ready(){return "시험 준비";}
    String confirmed(){return "시험 클릭 감지";}
    boolean matches(String value){
        try{URI uri=URI.create(value.startsWith("127.0.0.1:")?"http://"+value:value);
            return "http".equals(uri.getScheme())&&"127.0.0.1".equals(uri.getHost())&&uri.getPort()==socket.getLocalPort()
                &&uri.getRawUserInfo()==null&&uri.getRawQuery()==null&&uri.getRawFragment()==null&&("/click-"+token).equals(uri.getRawPath());
        }catch(RuntimeException e){return false;}
    }
    private String html(){return """
        <!doctype html><html lang="ko" translate="no"><meta charset="utf-8"><meta name="google" content="notranslate"><meta name="viewport" content="width=device-width,initial-scale=1">
        <link rel="icon" href="data:,"><title>출석 메이트 · 대체 클릭 시험</title>
        <style>body{margin:0;padding:32px 24px;background:#f3f7f6;color:#183631;font:18px sans-serif}main{max-width:480px;margin:auto}h1{font-size:27px}p{line-height:1.7}button{width:100%;padding:22px;border:0;border-radius:16px;background:#007c78;color:white;font-size:22px}small{display:block;word-break:break-all;margin-top:28px;color:#536f67;font-size:13px}#result{font-weight:bold;color:#007c78}</style>
        <main><h1>대체 클릭 시험</h1><p>실제 출결에 영향을 주지 않는 휴대폰 내부 시험 화면입니다.</p>
        <p>버튼을 직접 누르지 말고 기다려 주세요.</p><button id="test">자동 클릭 시험</button>
        <p id="result" aria-live="polite">자동 클릭 대기 중</p><small id="marker">시험 화면 준비 중</small>
        <p>앱에서 클릭 요청과 화면 변경을 확인하면 결과 알림을 남깁니다. 실제 서버 출결 성공과는 별개입니다.</p></main>
        <script>document.getElementById('test').addEventListener('click',function(){this.disabled=true;this.textContent='__CONFIRMED__';document.getElementById('result').textContent='버튼 클릭이 화면에 반영됐습니다';document.getElementById('marker').textContent='__CONFIRMED__';});document.getElementById('marker').textContent='__READY__';</script></html>
        """.replace("__READY__",ready()).replace("__CONFIRMED__",confirmed());}
    private void serve(){int requests=0;try{
        while(!closed&&System.nanoTime()<deadline&&requests<24){
            try(Socket client=socket.accept()){
                requests++;client.setSoTimeout(1500);
                ByteArrayOutputStream input=new ByteArrayOutputStream();int b,last=0;
                while(input.size()<8192&&System.nanoTime()<deadline&&(b=client.getInputStream().read())!=-1){input.write(b);last=(last<<8)|b;if(last==0x0d0a0d0a)break;}
                String headers=new String(input.toByteArray(),StandardCharsets.US_ASCII);String[] lines=headers.split("\r\n");
                boolean ok=last==0x0d0a0d0a&&lines.length>0&&lines[0].equals("GET /click-"+token+" HTTP/1.1");
                boolean host=false;for(String line:lines)if(line.equalsIgnoreCase("Host: 127.0.0.1:"+socket.getLocalPort()))host=true;
                byte[] body=(ok&&host?html():"Not found").getBytes(StandardCharsets.UTF_8);
                String response="HTTP/1.1 "+(ok&&host?"200 OK":"404 Not Found")+"\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; frame-ancestors 'none'; form-action 'none'; base-uri 'none'\r\nContent-Length: "+body.length+"\r\n\r\n";
                client.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));client.getOutputStream().write(body);client.getOutputStream().flush();
            }catch(SocketTimeoutException ignored){}catch(IOException e){if(closed)break;}
        }
    }finally{close();}}
    @Override public void close(){closed=true;try{socket.close();}catch(IOException ignored){}}
}
