package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;
public final class AttendanceMetricsTest {
    private final Set<Integer> weekdays=Set.of(1,2,3,4,5);
    private AttendanceMetrics.Summary summary(LocalDate now,Map<LocalDate,Policy.Record> records,Map<LocalDate,MonthAttendance.ManualStatus> manual,Set<String> excluded){
        return AttendanceMetrics.summarize(now,weekdays,excluded,records::get,manual::get,"09:00","17:50");
    }
    @Test public void courseExcludesPublishedHolidaysOnce(){
        var s=summary(CourseCalendar.START,Map.of(),Map.of(),Set.of());
        assertEquals(120,s.total());assertEquals(96,s.required());assertEquals(24,s.allowance());
        assertTrue(CourseCalendar.classDay(LocalDate.of(2026,9,28),weekdays,Set.of()));
        assertFalse(CourseCalendar.classDay(LocalDate.of(2026,10,5),weekdays,Set.of()));
        assertFalse(CourseCalendar.classDay(LocalDate.of(2027,2,9),weekdays,Set.of()));
        assertEquals(119,summary(CourseCalendar.START,Map.of(),Map.of(),Set.of("2026-09-24","2026-09-01")).total());
    }
    @Test public void unrecordedDatesAndPartialEntryAreUnknown(){
        var s=summary(LocalDate.of(2026,9,2),Map.of(CourseCalendar.START,new Policy.Record("08:40","")),Map.of(),Set.of());
        assertEquals(0,s.known());assertEquals(3,s.missing());assertEquals(0,s.equivalent());assertEquals(100.0,s.currentRate(),0.001);
    }
    @Test public void mixedLateAndEarlyAccumulateAcrossDates(){
        Map<LocalDate,Policy.Record> records=Map.of(CourseCalendar.START,new Policy.Record("09:01","17:50"),
            CourseCalendar.START.plusDays(1),new Policy.Record("08:50","15:00"),CourseCalendar.START.plusDays(2),new Policy.Record("09:01","17:50"));
        var s=summary(LocalDate.of(2026,9,2),records,Map.of(),Set.of());
        assertEquals(2,s.late());assertEquals(1,s.early());assertEquals(1,s.converted());assertEquals(0,s.remainder());assertEquals(23,s.remaining());
        assertEquals(200.0/3,s.currentRate(),0.0001);assertEquals(11900.0/120,s.projectedRate(),0.0001);
    }
    @Test public void twoOccurrencesCarryWithoutPrematureRounding(){
        var s=summary(CourseCalendar.START,Map.of(CourseCalendar.START,new Policy.Record("09:01","15:00")),Map.of(),Set.of());
        assertEquals(0,s.converted());assertEquals(2,s.remainder());assertEquals(24,s.remaining());
    }
    @Test public void manualOverrideWinsWithoutInventingTimes(){
        var record=new Policy.Record("09:01","15:00");
        assertEquals(new AttendanceMetrics.Day(true,false,false,false),AttendanceMetrics.day(record,MonthAttendance.ManualStatus.PRESENT,"09:00","17:50"));
        assertEquals(new AttendanceMetrics.Day(true,true,false,false),AttendanceMetrics.day(record,MonthAttendance.ManualStatus.ABSENT,"09:00","17:50"));
        assertEquals(new AttendanceMetrics.Day(true,false,true,true),AttendanceMetrics.day(null,MonthAttendance.ManualStatus.LATE_EARLY,"09:00","17:50"));
    }
    @Test public void classificationUsesClassEndNotFiveSecondLaunchCushion(){
        assertFalse(AttendanceMetrics.isEarly("17:50:00","17:50"));assertTrue(AttendanceMetrics.isEarly("17:49:59","17:50"));
        assertFalse(AttendanceMetrics.isLate("09:10:59","09:11"));assertTrue(AttendanceMetrics.isLate("09:11:00","09:11"));
        assertFalse(AttendanceMetrics.isLate("09:30",""));
    }
    @Test public void eightyPercentThresholdUsesIntegerDaysAndCannotOfferMoreThanFutureDays(){
        var s=new AttendanceMetrics.Summary(122,30,30,0,24,0,0);
        assertEquals(0,s.remaining());assertEquals(0,s.exceeded());assertTrue(s.projectedRate()>=80);
        var failed=new AttendanceMetrics.Summary(122,30,30,0,25,0,0);assertEquals(1,failed.exceeded());assertTrue(failed.projectedRate()<80);
        assertEquals(1,new AttendanceMetrics.Summary(122,121,121,0,0,0,0).remaining());
        assertEquals(0,new AttendanceMetrics.Summary(122,122,122,0,0,0,0).remaining());
    }
    @Test public void recordsOutsideCourseOrOnHolidayDoNotChangeDenominatorOrCounts(){
        var s=summary(CourseCalendar.END.plusDays(2),Map.of(LocalDate.of(2026,10,5),new Policy.Record("09:30","15:00")),
            Map.of(CourseCalendar.START.minusDays(1),MonthAttendance.ManualStatus.ABSENT),Set.of());
        assertEquals(120,s.elapsed());assertEquals(120,s.missing());assertEquals(0,s.equivalent());
    }
    @Test public void noClassesIsNotOneHundredPercent(){
        var s=AttendanceMetrics.summarize(CourseCalendar.START,Set.of(),Set.of(),d->null,d->null,"","17:50");
        assertEquals(0,s.total());assertEquals(0,s.remaining());assertTrue(Double.isNaN(s.projectedRate()));
    }

    @Test public void completedTimesAloneDoNotCertifyNormalAttendance(){
        assertEquals(MonthAttendance.State.COMPLETE,AttendanceMetrics.observedState(new Policy.Record("09:10","18:00"),"","17:50"));
        assertEquals("판정대기",MonthAttendance.State.COMPLETE.label);
        assertEquals(MonthAttendance.State.LATE,AttendanceMetrics.observedState(new Policy.Record("09:10","18:00"),"09:00","17:50"));
        assertEquals(MonthAttendance.State.EARLY_LEAVE,AttendanceMetrics.observedState(new Policy.Record("08:50","14:00"),"","17:50"));
        assertEquals(MonthAttendance.State.ATTENDED,AttendanceMetrics.observedState(new Policy.Record("08:50","18:00"),"09:00","17:50"));
        assertEquals(MonthAttendance.State.LATE_EARLY,AttendanceMetrics.observedState(new Policy.Record("09:10","14:00"),"09:00","17:50"));
    }
}
