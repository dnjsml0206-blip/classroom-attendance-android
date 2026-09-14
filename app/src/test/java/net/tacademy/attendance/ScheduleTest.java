package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;

public final class ScheduleTest {
    private final Set<Integer> days = Set.of(1,2,3,4,5);
    private ZonedDateTime at(String time) { return ZonedDateTime.parse("2026-09-10T"+time+"+09:00[Asia/Seoul]"); }
    private Policy.Phase phase(String time, boolean in, boolean out) {
        return Policy.phase(at(time),days,Set.of(),"08:30","09:30","17:50",10,in,out);
    }
    @Test public void onlyEntryWindowRunsBeforeCheckout() {
        assertEquals(Policy.Phase.IDLE,phase("08:29:59",false,false));
        assertEquals(Policy.Phase.ENTRY,phase("08:30:00",false,false));
        assertEquals(Policy.Phase.ENTRY,phase("09:29:59",false,false));
        assertEquals(Policy.Phase.IDLE,phase("09:30:00",false,false));
        assertEquals(Policy.Phase.IDLE,phase("12:00:00",false,false));
    }
    @Test public void completedEntryStopsUntilCheckoutAndOutStopsEverything() {
        assertEquals(Policy.Phase.IDLE,phase("08:45:00",true,false));
        assertEquals(Policy.Phase.IDLE,phase("17:49:59",true,false));
        assertEquals(Policy.Phase.IDLE,phase("17:50:00",true,false));
        assertEquals(Policy.Phase.CHECKOUT,phase("17:50:10",true,false));
        assertEquals(Policy.Phase.IDLE,phase("17:51:00",true,true));
    }
    @Test public void checkoutRetryHasADeadline() {
        assertEquals(Policy.Phase.CHECKOUT,phase("17:59:59",true,false));
        assertEquals(Policy.Phase.IDLE,phase("18:00:00",true,false));
    }
    @Test public void nextWakeIncludesStopBoundaries() {
        assertEquals(at("09:30:00"),next(at("08:45:00"),false,false,Set.of()));
        assertEquals(at("17:50:10"),next(at("08:45:00"),true,false,Set.of()));
        assertEquals(at("18:00:00"),next(at("17:50:10"),true,false,Set.of()));
    }
    @Test public void completedDaySkipsToNextEntry() {
        assertEquals(at("08:30:00").plusDays(1),next(at("17:51:00"),true,true,Set.of()));
    }
    @Test public void holidaysAndWeekendsNeverStart() {
        ZonedDateTime friday = ZonedDateTime.parse("2026-09-11T18:00:00+09:00[Asia/Seoul]");
        assertEquals(ZonedDateTime.parse("2026-09-15T08:30:00+09:00[Asia/Seoul]"),
            next(friday,false,false,Set.of("2026-09-14")));
        assertEquals(Policy.Phase.IDLE,Policy.phase(at("08:45:00"),days,Set.of("2026-09-10"),"08:30","09:30","17:50",10,false,false));
    }
    @Test public void customTimesReplaceTheDefaults() {
        assertEquals(Policy.Phase.CHECKOUT,Policy.phase(at("16:20:10"),days,Set.of(),"10:00","11:00","16:20",5,true,false));
        assertFalse(Policy.checkoutDue(at("17:50:00"),"16:20",5));
    }
    @Test public void invalidAndCrossMidnightSchedulesAreRejected() {
        assertFalse(Policy.validSchedule("09:30","08:30","17:50",10));
        assertFalse(Policy.validSchedule("08:30","18:00","17:50",10));
        assertFalse(Policy.validSchedule("08:30","09:30","23:55",10));
        assertFalse(Policy.validSchedule("08:30","09:30","17:50",0));
        assertFalse(Policy.validSchedule("08:30","09:30","17:50",61));
        assertTrue(Policy.validSchedule("08:30","09:30","17:50",10));
    }
    @Test public void phoneTimezoneDoesNotChangeKoreanSchedule() {
        assertTrue(Policy.checkoutDue(ZonedDateTime.parse("2026-09-10T08:50:10Z"),"17:50",10));
    }
    @Test public void thirtyMeterRadiusRejectsImpreciseAndOutsideFixes() {
        assertTrue(Policy.nearby(29,5,30));
        assertFalse(Policy.nearby(31,5,30));
        assertFalse(Policy.nearby(10,80,30));
        assertFalse(Policy.nearby(Float.NaN,5,30));
    }
    @Test public void checkoutCannotStartBeforeTheTenSecondMargin() {
        for(String time:List.of("17:49:59","17:50:00","17:50:09.999999999"))
            assertEquals(Policy.Phase.IDLE,phase(time,true,false));
        assertEquals(Policy.Phase.CHECKOUT,phase("17:50:10",true,false));
        assertEquals(LocalTime.of(17,50,10),Policy.checkoutStartTime("17:50"));
    }
    @Test public void alarmWaitsThroughTheMarginAndDoesNotExtendTheDeadline() {
        assertEquals(at("17:50:10"),next(at("17:50:00"),true,false,Set.of()));
        assertEquals(at("17:50:10"),next(at("17:50:09.999999999"),true,false,Set.of()));
        assertEquals(at("18:00:00"),next(at("17:50:10"),true,false,Set.of()));
        assertFalse(Policy.checkoutDue(at("18:00:00"),"17:50",10));
    }
    @Test public void customTimeAndOneMinuteRetryKeepTheSameMargin() {
        assertFalse(Policy.checkoutDue(at("16:20:09"),"16:20",1));
        assertTrue(Policy.checkoutDue(at("16:20:10"),"16:20",1));
        assertTrue(Policy.checkoutDue(at("16:20:59"),"16:20",1));
        assertFalse(Policy.checkoutDue(at("16:21:00"),"16:20",1));
    }
    private ZonedDateTime next(ZonedDateTime now,boolean in,boolean out,Set<String> excluded) {
        return Policy.nextWake(now,days,excluded,"08:30","09:30","17:50",10,in,out);
    }
}
