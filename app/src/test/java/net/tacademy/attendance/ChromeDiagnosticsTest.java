package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ChromeDiagnosticsTest {
    private String diagnose(String owner,String address,String signal,String stage){return ChromeDiagnostics.diagnosis(owner,true,false,"NONE","ALLOWED",address,signal,12,"ABSENT",stage);}
    @Test public void missingOrOtherWindowIsNeverSiteBlock(){
        assertEquals("ACCESSIBILITY_ROOT_UNAVAILABLE",diagnose("ROOT_UNAVAILABLE","HIDDEN","ACCESS_DENIED","CHROME_NOT_VISIBLE"));
        assertEquals("CHROME_NOT_FOREGROUND",diagnose("OWN_APP","HIDDEN","ACCESS_DENIED","CHROME_NOT_VISIBLE"));
        assertEquals("ADDRESS_UNVERIFIED",diagnose("CHROME","HIDDEN","ACCESS_DENIED","ADDRESS_NOT_VISIBLE"));
    }
    @Test public void onlyCurrentTargetAddressCanSupplySiteEvidence(){
        assertEquals("NOT_OBSERVED",ChromeDiagnostics.pageSignal("OTHER_ADDRESS","403 Forbidden"));
        assertEquals("NOT_OBSERVED",ChromeDiagnostics.pageSignal("HIDDEN","Access denied"));
        assertEquals("SITE_RESTRICTION_SHOWN",diagnose("CHROME","ATTENDANCE","ACCESS_DENIED","DATE_UNREADABLE"));
    }
    @Test public void authenticationIsNotAutomationBlocking(){
        assertEquals("AUTH_REQUIRED",ChromeDiagnostics.pageSignal("ATTENDANCE","인증절차를 진행한 후 이용해주세요"));
        assertEquals("TRAINEE_REJECTED",ChromeDiagnostics.pageSignal("AUTHENTICATION","인증 진행중인 훈련생이 아닙니다"));
        assertEquals("SITE_AUTH_MESSAGE_SHOWN",diagnose("CHROME","AUTHENTICATION","TRAINEE_REJECTED","ADDRESS_NOT_VISIBLE"));
    }
    @Test public void visibleErrorsRemainDifferentObservations(){
        for(String[] row:new String[][]{{"HTTP ERROR 403","ACCESS_DENIED"},{"429 Too Many Requests","RATE_LIMIT"},{"Verify you are human","HUMAN_CHECK"},{"503 Service Unavailable","SERVER_ERROR"},{"ERR_CONNECTION_TIMED_OUT","NETWORK_ERROR"},{"ERR_CERT_AUTHORITY_INVALID","TLS_ERROR"},{"등록된 IP가 아닙니다","SITE_NETWORK_RESTRICTION"}})
            assertEquals(row[1],ChromeDiagnostics.pageSignal("ATTENDANCE",row[0]));
        assertEquals("BROWSER_CONNECTION_ERROR_SHOWN",diagnose("CHROME","ATTENDANCE","NETWORK_ERROR","SITE_ERROR_SCREEN"));
        assertEquals("SERVER_ERROR_SHOWN",diagnose("CHROME","ATTENDANCE","SERVER_ERROR","SITE_ERROR_SCREEN"));
    }
    @Test public void absenceOfErrorTextDoesNotProveNormalSite(){
        assertEquals("NONE_RECOGNIZED",ChromeDiagnostics.pageSignal("ATTENDANCE","출결일 : 2026-09-16 입실"));
        assertEquals("UNDETERMINED",diagnose("CHROME","ATTENDANCE","NONE_RECOGNIZED","PAGE_SETTLING"));
    }
    @Test public void lockWifiAndLocalErrorAreSeparated(){
        assertEquals("SCREEN_LOCKED_OR_OFF",ChromeDiagnostics.diagnosis("CHROME",false,true,"NONE","ALLOWED","ATTENDANCE","NONE_RECOGNIZED",1,"READY","SCREEN_LOCKED_DURING_WORK"));
        assertEquals("WIFI_PREREQUISITE",ChromeDiagnostics.diagnosis("CHROME",true,false,"NONE","NO_WIFI_TRANSPORT","ATTENDANCE","NONE_RECOGNIZED",1,"READY","WIFI_LOST_DURING_WORK"));
        assertEquals("LOCAL_READ_ERROR",ChromeDiagnostics.diagnosis("CHROME",true,false,"SECURITY_DENIED","ALLOWED","HIDDEN","NOT_OBSERVED",0,"ABSENT","LOCAL_RUNTIME_FAILURE"));
    }
    @Test public void missingButtonAndUnconfirmedResultAreDifferent(){
        assertEquals("BUTTON_NOT_EXPOSED",diagnose("CHROME","ATTENDANCE","NONE_RECOGNIZED","TIMEOUT_BUTTON_UNAVAILABLE"));
        assertEquals("CLICK_RESULT_UNCONFIRMED",diagnose("CHROME","ATTENDANCE","NONE_RECOGNIZED","TIMEOUT_RECORD_NOT_UPDATED"));
        assertEquals("UI_ACTION_REJECTED",diagnose("CHROME","ATTENDANCE","NONE_RECOGNIZED","BUTTON_ACTION_REJECTED"));
    }
    @Test public void addressClassificationNeverReturnsPersonalUrl(){
        assertEquals("ATTENDANCE",ChromeDiagnostics.address("skplanet.atosoft.net/Worknet/Present/Present.asp?token=secret"));
        assertEquals("AUTHENTICATION",ChromeDiagnostics.address(SiteProtocol.AUTH_PAGE));
        for(String s:new String[]{"https://skplanet.atosoft.net.evil.test/Worknet/Present/Present.asp","https://someone@skplanet.atosoft.net/Worknet/Present/Present.asp","http://skplanet.atosoft.net/Worknet/Present/Present.asp","private.invalid/user"})assertEquals("OTHER_ADDRESS",ChromeDiagnostics.address(s));
        assertEquals("HIDDEN",ChromeDiagnostics.address(null));assertEquals("EMPTY",ChromeDiagnostics.address(""));
    }
}
