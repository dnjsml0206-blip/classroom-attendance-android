package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;

public final class PolicyTest {
    private final Set<Integer> weekdays = Set.of(1,2,3,4,5);
    private ZonedDateTime time(String value) { return ZonedDateTime.parse(value + "+09:00[Asia/Seoul]"); }

    @Test public void wifiNormalizationPreservesCaseAndUnderscores() {
        assertEquals("TACADEMY_5G", Policy.normalizeSsid("\"TACADEMY_5G\""));
        assertNotEquals("tacademy_5g", Policy.normalizeSsid("TACADEMY_5G"));
        assertEquals("", Policy.normalizeSsid("<unknown ssid>"));
    }
    @Test public void wifiNamesRespectUtf8LengthAndRejectControlCharacters() {
        assertTrue(Policy.validSsid("TACADEMY_5G"));
        assertTrue(Policy.validSsid("강의실 Wi-Fi"));
        assertFalse(Policy.validSsid(""));
        assertFalse(Policy.validSsid("test\nnetwork"));
        assertFalse(Policy.validSsid("한".repeat(11)));
        assertTrue(Policy.validSsid("a".repeat(32)));
    }
    @Test public void emptyAndPartialRecordsAreParsed() {
        assertFalse(Policy.parseRecord("").checkedIn());
        assertFalse(Policy.parseRecord("|").checkedIn());
        assertTrue(Policy.parseRecord("09:01:23|").checkedIn());
        assertFalse(Policy.parseRecord("09:01:23|").checkedOut());
        assertTrue(Policy.parseRecord("09:01|17:50").checkedOut());
        assertEquals("17:50:12", Policy.parseRecord("09:00:00|17:50:12").out());
    }
    @Test public void malformedOrUnexpectedRecordsCannotEnableActions() {
        for (String bad : new String[]{"0", "1", "<html>로그인</html>", "|17:50", "25:00|", "09:00|done", "09:00|17:50|extra", "09:60|", "오류"}) {
            assertThrows(IllegalArgumentException.class, () -> Policy.parseRecord(bad));
        }
    }
    @Test public void checkoutHasBoundedWindow() {
        assertFalse(Policy.checkoutDue(time("2026-09-10T17:49:59")));
        assertTrue(Policy.checkoutDue(time("2026-09-10T17:50:00")));
        assertTrue(Policy.checkoutDue(time("2026-09-10T17:54:59")));
        assertFalse(Policy.checkoutDue(time("2026-09-10T17:55:00")));
    }
    @Test public void datesUseSeoulEvenWhenPhoneZoneIsDifferent() {
        ZonedDateTime utc = ZonedDateTime.parse("2026-09-10T08:50:00Z");
        assertTrue(Policy.checkoutDue(utc));
        assertTrue(Policy.isClassDay(utc, weekdays, Set.of()));
        assertFalse(Policy.isClassDay(utc, weekdays, Set.of("2026-09-10")));
    }
    @Test public void nextAlarmSkipsWeekendsAndExcludedDays() {
        ZonedDateTime friday = time("2026-09-11T18:00:00");
        assertEquals(time("2026-09-15T17:50:00"), Policy.nextCheckout(friday, weekdays, Set.of("2026-09-14")));
        assertNull(Policy.nextCheckout(friday, Set.of(), Set.of()));
    }
    @Test public void checkoutDoesNotSchedulePastTimeForToday() {
        assertEquals(time("2026-09-11T17:50:00"), Policy.nextCheckout(time("2026-09-10T17:50:00"), weekdays, Set.of()));
        assertEquals(time("2026-09-10T17:50:00"), Policy.nextCheckout(time("2026-09-10T17:49:59"), weekdays, Set.of()));
    }
    @Test public void entryWindowIsStartInclusiveEndExclusive() {
        assertTrue(Policy.inWindow(time("2026-09-10T08:00:00"), "08:00", "17:45"));
        assertFalse(Policy.inWindow(time("2026-09-10T17:45:00"), "08:00", "17:45"));
    }
}
