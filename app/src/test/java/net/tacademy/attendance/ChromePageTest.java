package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.LocalDate;

public final class ChromePageTest {
    @Test public void onlyExactAttendancePageCanEnableClicks() {
        assertTrue(ChromePage.attendanceAddress(SiteProtocol.PAGE));
        assertTrue(ChromePage.attendanceAddress("skplanet.atosoft.net/Worknet/Present/Present.asp"));
        for(String value:new String[]{"https://evil.example/Worknet/Present/Present.asp","https://skplanet.atosoft.net.evil.example/Worknet/Present/Present.asp",
            "https://skplanet.atosoft.net/Worknet/Present/PAuthWay.asp","http://skplanet.atosoft.net/Worknet/Present/Present.asp",
            "https://user@skplanet.atosoft.net/Worknet/Present/Present.asp","skplanet.atosoft.net"})
            assertFalse(value,ChromePage.attendanceAddress(value));
    }
    @Test public void recordsUseLabeledVisibleTimes() {
        Policy.Record record=ChromePage.record("출결일 :\n2026-09-10\n입실시간 : 08:45:12\n퇴실시간 : 17:50:03");
        assertEquals("08:45:12",record.in()); assertEquals("17:50:03",record.out());
        assertFalse(ChromePage.record("08:45 현재 시각\n입실\n퇴실").checkedIn());
    }
    @Test public void malformedRecordsNeverReportSuccess() {
        assertThrows(IllegalArgumentException.class,()->ChromePage.record("퇴실시간 : 17:50"));
        assertThrows(IllegalArgumentException.class,()->ChromePage.record("입실시간 : 25:00"));
        assertThrows(IllegalArgumentException.class,()->ChromePage.record("입실시간 : 09:30:99"));
    }
    @Test public void dateNeedsTheAttendanceLabel() {
        assertEquals(LocalDate.of(2026,9,10),ChromePage.date("출결일 :\n2026-09-10"));
        assertThrows(IllegalArgumentException.class,()->ChromePage.date("현재 2026-09-10"));
    }
    @Test public void onlyKnownCheckoutConfirmationIsAccepted() {
        assertTrue(ChromePage.checkoutConfirmation("정말로 퇴실 하시겠습니까?"));
        assertFalse(ChromePage.checkoutConfirmation("삭제 하시겠습니까?"));
    }
    @Test public void authenticationLinkUpgradesOnlyTheSchoolOrigin() {
        assertEquals(SiteProtocol.AUTH_PAGE,SiteProtocol.secureNavigation("http://skplanet.atosoft.net/Worknet/Present/PAuthWay.asp"));
        assertNull(SiteProtocol.secureNavigation("http://evil.example/"));
        assertNull(SiteProtocol.secureNavigation("http://user@skplanet.atosoft.net/"));
    }
}
