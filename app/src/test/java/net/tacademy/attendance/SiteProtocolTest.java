package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.LocalDate;

public final class SiteProtocolTest {
    @Test public void onlyKnownPermissionResponsesAreAccepted() {
        assertTrue(SiteProtocol.permissionFlag("\uFEFF 1\r\n"));
        assertFalse(SiteProtocol.permissionFlag("0"));
        assertThrows(IllegalArgumentException.class, () -> SiteProtocol.permissionFlag("<html>Login</html>"));
        assertThrows(IllegalArgumentException.class, () -> SiteProtocol.permissionFlag(""));
        assertThrows(IllegalArgumentException.class, () -> SiteProtocol.permissionFlag("2"));
    }
    @Test public void dateComesFromLabeledSiteField() {
        String html = "<th height=\"35\">출결일 :</th>\r\n<th>2026-09-10</th>";
        assertEquals(LocalDate.of(2026,9,10), SiteProtocol.serverDate(html));
        assertThrows(IllegalArgumentException.class, () -> SiteProtocol.serverDate("2026-09-10"));
        assertThrows(IllegalArgumentException.class, () -> SiteProtocol.serverDate("<th>다른 날짜 :</th><th>2026-09-10</th>"));
    }
    @Test public void navigationOnlyAllowsTheHttpsAttendanceOrigin() {
        assertTrue(SiteProtocol.allowedNavigation(SiteProtocol.PAGE));
        assertTrue(SiteProtocol.allowedNavigation("https://skplanet.atosoft.net/Worknet/verify.asp?sample=1"));
        for (String bad : new String[]{
            "http://skplanet.atosoft.net/", "https://skplanet.atosoft.net.evil.example/",
            "https://skplanet.atosoft.net@evil.example/", "javascript:alert(1)",
            "https://user:password@skplanet.atosoft.net/", "https://skplanet.atosoft.net:444/",
            "file:///sdcard/test.html"}) assertFalse(bad, SiteProtocol.allowedNavigation(bad));
    }
    @Test public void shortcutAcceptsChromeShareTextAndUpgradesHttp(){
        assertEquals(SiteProtocol.PAGE,SiteProtocol.shortcutAddress("SK플래닛 출결관리 - http://skplanet.atosoft.net/Worknet/Present/Present.asp"));
        assertEquals(SiteProtocol.PAGE,SiteProtocol.shortcutAddress("skplanet.atosoft.net/Worknet/Present/Present.asp"));
        assertEquals(SiteProtocol.AUTH_PAGE,SiteProtocol.shortcutAddress(SiteProtocol.AUTH_PAGE));
    }
    @Test public void shortcutPreservesQueryButDropsFragment(){
        assertEquals(SiteProtocol.PAGE+"?course=1",SiteProtocol.shortcutAddress(SiteProtocol.PAGE+"?course=1#top"));
    }
    @Test public void shortcutRejectsOtherSitesAndActionEndpoints(){
        for(String url:new String[]{null,"","https://example.com/","https://skplanet.atosoft.net.evil.example/Worknet/Present/Present.asp","https://user@skplanet.atosoft.net/Worknet/Present/Present.asp","https://skplanet.atosoft.net:444/Worknet/Present/Present.asp",SiteProtocol.BASE+"save.asp",SiteProtocol.PAGE+" "+SiteProtocol.AUTH_PAGE})assertNull(url,SiteProtocol.shortcutAddress(url));
    }
}
