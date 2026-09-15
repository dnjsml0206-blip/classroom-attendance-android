package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
public final class LocalClickPageTest {
    @Test public void onlyThisLocalNonceIsAccepted()throws Exception{
        try(LocalClickPage page=new LocalClickPage(UUID.randomUUID().toString())){
            assertTrue(page.matches(page.url()));assertTrue(page.matches(page.url().substring(7)));
            for(String bad:new String[]{page.url()+"/",page.url()+"?x=1",page.url()+"#x",page.url().replace("127.0.0.1","localhost"),page.url().replace("http:","https:"),page.url().replace("127.0.0.1","127.0.0.1.evil.test"),page.url().replace("/click-","/other-"),"http://skplanet.atosoft.net/Worknet/Present/Present.asp"})assertFalse(bad,page.matches(bad));
        }
    }
    private String request(LocalClickPage page,String method,String host,String path)throws Exception{
        URI uri=URI.create(page.url());try(Socket socket=new Socket("127.0.0.1",uri.getPort())){
            socket.setSoTimeout(2000);socket.getOutputStream().write((method+" "+path+" HTTP/1.1\r\nHost: "+host+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    @Test public void chromePageIsLocalAndContainsBothMarkers()throws Exception{
        try(LocalClickPage p=new LocalClickPage(UUID.randomUUID().toString())){URI u=URI.create(p.url());String response=request(p,"GET",u.getAuthority(),u.getPath());
            assertTrue(response.startsWith("HTTP/1.1 200"));assertTrue(response.contains(p.ready()));assertTrue(response.contains(p.confirmed()));assertTrue(response.contains("width:100%"));assertTrue(response.contains("notranslate"));assertFalse(response.contains(u.getPath().substring(7)));
            assertTrue(response.contains("default-src 'none'"));assertFalse(response.contains("atosoft"));assertFalse(response.contains("fetch("));
        }
    }
    @Test public void otherPathsMethodsAndHostsNeverServeThePage()throws Exception{
        try(LocalClickPage p=new LocalClickPage(UUID.randomUUID().toString())){URI u=URI.create(p.url());
            assertTrue(request(p,"GET",u.getAuthority(),"/").startsWith("HTTP/1.1 404"));assertTrue(request(p,"POST",u.getAuthority(),u.getPath()).startsWith("HTTP/1.1 404"));assertTrue(request(p,"GET","example.test",u.getPath()).startsWith("HTTP/1.1 404"));
        }
    }
    @Test public void closingPageClosesTheListener()throws Exception{
        LocalClickPage p=new LocalClickPage(UUID.randomUUID().toString());int port=URI.create(p.url()).getPort();p.close();
        try(Socket ignored=new Socket("127.0.0.1",port)){fail("Listener still open");}catch(IOException expected){}
    }
}
