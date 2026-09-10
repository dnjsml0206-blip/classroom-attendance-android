package net.tacademy.attendance;

import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public final class HomeThemeTest {
    private ZonedDateTime at(String time) { return LocalDate.of(2026,9,10).atTime(LocalTime.parse(time)).atZone(Policy.ZONE); }
    @Test public void entryAndDaytimeStayWarm() {
        for(String time : new String[]{"00:00", "08:30", "09:29:59", "12:00", "16:49:59"})
            assertEquals(HomeTheme.WARM,HomeTheme.at(at(time),"09:30","17:50"));
    }
    @Test public void switchesExactlyOneHourBeforeCheckout() {
        assertEquals(HomeTheme.NIGHT,HomeTheme.at(at("16:50"),"09:30","17:50"));
        assertEquals(HomeTheme.NIGHT,HomeTheme.at(at("17:50"),"09:30","17:50"));
        assertEquals(HomeTheme.NIGHT,HomeTheme.at(at("23:59:59"),"09:30","17:50"));
    }
    @Test public void midnightReturnsToWarm() {
        assertEquals(HomeTheme.WARM,HomeTheme.at(at("23:59:59").plusSeconds(1),"09:30","17:50"));
    }
    @Test public void checkoutChangesTheSwitchTime() {
        assertEquals(HomeTheme.WARM,HomeTheme.at(at("18:19:59"),"09:30","19:20"));
        assertEquals(HomeTheme.NIGHT,HomeTheme.at(at("18:20"),"09:30","19:20"));
        assertEquals(HomeTheme.NIGHT,HomeTheme.at(at("15:20"),"09:30","16:20"));
    }
    @Test public void followsKoreaTimeEvenOnAnotherPhoneTimezone() {
        assertEquals(HomeTheme.WARM,HomeTheme.at(ZonedDateTime.parse("2026-09-10T07:49:59Z"),"09:30","17:50"));
        assertEquals(HomeTheme.NIGHT,HomeTheme.at(ZonedDateTime.parse("2026-09-10T07:50:00Z"),"09:30","17:50"));
    }
    @Test public void entryWindowStaysWarmWhenCheckoutIsClose() {
        assertEquals(HomeTheme.WARM,HomeTheme.at(at("09:45"),"10:00","10:15"));
        assertEquals(HomeTheme.NIGHT,HomeTheme.at(at("10:00"),"10:00","10:15"));
    }
    @Test public void earlyCheckoutDoesNotWrapToYesterday() {
        assertEquals(LocalTime.of(0,15),HomeTheme.nightStarts("00:15","00:45"));
        assertEquals(HomeTheme.WARM,HomeTheme.at(at("00:00"),"00:15","00:45"));
        assertEquals(HomeTheme.NIGHT,HomeTheme.at(at("00:15"),"00:15","00:45"));
    }
}