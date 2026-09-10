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
}
