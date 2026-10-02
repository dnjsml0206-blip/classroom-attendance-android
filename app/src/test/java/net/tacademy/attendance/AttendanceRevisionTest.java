package net.tacademy.attendance;

import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;

/** User-facing October revision: course-end cutoffs, day budgets, and credited monthly leave. */
public final class AttendanceRevisionTest {
    private static final Set<Integer> WEEKDAYS=Set.of(1,2,3,4,5);
    private static LocalDate date(String value){return LocalDate.parse(value);}
    private static ZonedDateTime at(String day,String time){return date(day).atTime(LocalTime.parse(time)).atZone(Policy.ZONE);}
    private static AttendanceMetrics.Summary summary(ZonedDateTime now,Map<LocalDate,Policy.Record> records,
            Map<LocalDate,MonthAttendance.ManualStatus> manual){
        return AttendanceMetrics.summarize(now,WEEKDAYS,Set.of(),records::get,manual::get,"09:11","17:50");
    }
    private static AttendanceMetrics.Summary summary(ZonedDateTime now){return summary(now,Map.of(),Map.of());}
    private static AttendanceMetrics.Summary budget(int elapsed,int absent,int late,int early){
        return new AttendanceMetrics.Summary(120,elapsed,elapsed,0,absent,late,early);
    }

    @Test public void eachCourseDayEntersDenominatorAtItsActualEnd(){
        String[][] days={{"2026-08-31","16:00"},{"2026-09-01","17:00"},{"2026-09-02","18:00"}};
        for(String[] entry:days){
            LocalDate day=date(entry[0]);ZonedDateTime end=at(entry[0],entry[1]);
            assertEquals(entry[1],CourseSchedule.classEnd(day));
            var records=Map.of(day,new Policy.Record("08:40",CourseSchedule.checkout(day,"17:50")));
            var before=summary(end.minusNanos(1),records,Map.of());
            var after=summary(end,records,Map.of());
            assertEquals("End boundary for "+day,before.elapsed()+1,after.elapsed());
            assertEquals(0,before.known());assertEquals(1,after.known());
            assertEquals(120,after.total());
        }
    }

    @Test public void successfulEarlyDispatchDoesNotIncludeTodaysClassPrematurely(){
        for(String dayText:List.of("2026-08-31","2026-09-01","2026-09-02")){
            LocalDate day=date(dayText);
            var dispatch=day.atTime(CourseSchedule.dispatch(day,"17:50")).atZone(Policy.ZONE);
            var records=Map.of(day,new Policy.Record("08:40",CourseSchedule.checkout(day,"17:50")));
            var result=summary(dispatch,records,Map.of());
            assertEquals(0,result.known());
            assertEquals(summary(day.minusDays(1).atTime(LocalTime.MAX).atZone(Policy.ZONE)).elapsed(),result.elapsed());
        }
    }

    @Test public void cutoffUsesKoreaTimeEvenWhenCallerPassesUtc(){
        var end=at("2026-09-02","18:00");
        assertEquals(summary(end),summary(end.withZoneSameInstant(ZoneOffset.UTC)));
        assertEquals(summary(end).elapsed()-1,summary(end.minusSeconds(1).withZoneSameInstant(ZoneOffset.UTC)).elapsed());
    }

    @Test public void endedUnrecordedClassesCountAsPresentWithoutFabricatingRecords(){
        var result=summary(at("2026-09-02","18:00"));
        assertEquals(3,result.elapsed());assertEquals(0,result.known());assertEquals(3,result.missing());
        assertEquals(0,result.equivalent());assertEquals(100.0,result.currentRate(),0.00001);
        assertEquals(100.0,result.projectedRate(),0.00001);
        var partial=summary(at("2026-09-02","18:00"),Map.of(date("2026-09-02"),new Policy.Record("08:40","")),Map.of());
        assertEquals(100.0,partial.currentRate(),0.00001);assertEquals(3,partial.missing());
    }

    @Test public void currentRateIncludesAllEndedClassesAndOnlyConfirmedPenalties(){
        var result=summary(at("2026-09-02","18:00"),Map.of(),Map.of(date("2026-09-02"),MonthAttendance.ManualStatus.ABSENT));
        assertEquals(3,result.elapsed());assertEquals(1,result.known());assertEquals(2,result.missing());
        assertEquals(200.0/3,result.currentRate(),0.00001);
        assertEquals(11900.0/120,result.projectedRate(),0.00001);
    }

    @Test public void beforeFirstClassEndsCurrentRateIsUnavailable(){
        var result=summary(at("2026-08-31","15:59:59"));
        assertEquals(0,result.elapsed());assertTrue(Double.isNaN(result.currentRate()));
        assertEquals("—",result.currentDisplay());assertEquals(120,result.future());
    }

    @Test public void twoSelectionsOnOneDayAndOneOnAnotherMakeOneAbsence(){
        var result=summary(at("2026-09-01","17:00"),Map.of(),Map.of(
            date("2026-08-31"),MonthAttendance.ManualStatus.LATE_EARLY,
            date("2026-09-01"),MonthAttendance.ManualStatus.LATE));
        assertEquals(2,result.late());assertEquals(1,result.early());assertEquals(1,result.converted());
        assertEquals(0,result.remainder());assertEquals(50.0,result.currentRate(),0.00001);
        assertEquals(23,result.remaining());
        for(int events=0;events<=6;events++){
            var count=budget(50,0,events/2,events-events/2);
            assertEquals(events/3,count.converted());assertEquals(events%3,count.remainder());
        }
    }

    @Test public void eightyPercentBudgetRetainsZeroOneOrTwoUnconvertedEvents(){
        for(int remainder=0;remainder<=2;remainder++){
            var result=budget(100,24,remainder,0);
            assertEquals(24,result.allowance());assertEquals(96,result.required());
            assertEquals(0,result.remaining());assertEquals(2-remainder,result.remainingEvents());
            assertEquals(80.0,result.projectedRate(),0.00001);
        }
        var oneDayLeft=budget(100,23,1,1);
        assertEquals(1,oneDayLeft.remaining());assertEquals(3,oneDayLeft.remainingEvents());
        var converted=budget(100,23,2,1);
        assertEquals(0,converted.remaining());assertEquals(2,converted.remainingEvents());
    }

    @Test public void exceededBudgetNeverOffersAdditionalAbsencesOrEvents(){
        for(var result:List.of(budget(100,25,0,0),budget(100,24,1,2))){
            assertEquals(1,result.exceeded());assertEquals(0,result.remaining());assertEquals(0,result.remainingEvents());
            assertTrue(result.projectedRate()<80.0);
        }
    }

    @Test public void remainingDaysLimitBothAlternativeBudgets(){
        var lastDay=budget(119,0,0,0);
        assertEquals(1,lastDay.remaining());assertEquals(2,lastDay.remainingEvents());
        var finished=budget(120,0,0,0);
        assertEquals(0,finished.remaining());assertEquals(0,finished.remainingEvents());
        var oddLength=new AttendanceMetrics.Summary(121,100,100,0,24,0,0);
        assertEquals(24,oddLength.allowance());assertEquals(97,oddLength.required());
        assertTrue(oddLength.projectedRate()>=80.0);
        assertTrue(new AttendanceMetrics.Summary(121,100,100,0,25,0,0).projectedRate()<80.0);
    }

    @Test public void futureAndNonClassAnnotationsDoNotAffectCurrentPenalties(){
        var result=summary(at("2026-09-02","17:59:59"),Map.of(),Map.of(
            date("2026-09-02"),MonthAttendance.ManualStatus.ABSENT,
            date("2026-09-03"),MonthAttendance.ManualStatus.LATE_EARLY,
            date("2026-09-23"),MonthAttendance.ManualStatus.ABSENT));
        assertEquals(2,result.elapsed());assertEquals(0,result.equivalent());
        assertEquals(0,result.late());assertEquals(0,result.early());
    }

    @Test public void vacationCreditsAttendanceWithoutDiscardingObservedTimes(){
        var record=new Policy.Record("09:30","14:00");
        var credited=AttendanceMetrics.day(record,MonthAttendance.ManualStatus.VACATION,"09:11","17:50");
        assertEquals(new AttendanceMetrics.Day(true,false,false,false),credited);
        assertEquals("09:30",record.in());assertEquals("14:00",record.out());
        var result=summary(at("2026-09-02","18:00"),Map.of(date("2026-09-02"),record),
            Map.of(date("2026-09-02"),MonthAttendance.ManualStatus.VACATION));
        assertEquals(1,result.known());assertEquals(0,result.equivalent());assertEquals(100.0,result.currentRate(),0.00001);
        assertEquals(new AttendanceMetrics.Day(true,false,false,false),AttendanceMetrics.day(null,MonthAttendance.ManualStatus.VACATION,"09:11","17:50"));
    }

    @Test public void independentLateAndEarlySelectionsRemainCompatibleWithLegacyValues(){
        var value=MonthAttendance.toggle(null,MonthAttendance.ManualStatus.LATE);
        assertEquals(MonthAttendance.ManualStatus.LATE,value);
        value=MonthAttendance.toggle(value,MonthAttendance.ManualStatus.EARLY_LEAVE);
        assertEquals(MonthAttendance.ManualStatus.LATE_EARLY,value);
        assertTrue(MonthAttendance.selected(value,MonthAttendance.ManualStatus.LATE));
        assertTrue(MonthAttendance.selected(value,MonthAttendance.ManualStatus.EARLY_LEAVE));
        assertFalse(MonthAttendance.selected(value,MonthAttendance.ManualStatus.ABSENT));
        value=MonthAttendance.toggle(value,MonthAttendance.ManualStatus.LATE);
        assertEquals(MonthAttendance.ManualStatus.EARLY_LEAVE,value);
        assertEquals(MonthAttendance.ManualStatus.PRESENT,MonthAttendance.toggle(value,MonthAttendance.ManualStatus.EARLY_LEAVE));
        assertEquals(MonthAttendance.ManualStatus.LATE,MonthAttendance.toggle(MonthAttendance.ManualStatus.LATE_EARLY,MonthAttendance.ManualStatus.EARLY_LEAVE));
    }

    @Test public void vacationAndAbsenceReplaceLateEarlySelectionAndCanBeCleared(){
        assertEquals(MonthAttendance.ManualStatus.VACATION,MonthAttendance.toggle(MonthAttendance.ManualStatus.LATE_EARLY,MonthAttendance.ManualStatus.VACATION));
        assertEquals(MonthAttendance.ManualStatus.ABSENT,MonthAttendance.toggle(MonthAttendance.ManualStatus.LATE_EARLY,MonthAttendance.ManualStatus.ABSENT));
        assertEquals(MonthAttendance.ManualStatus.LATE,MonthAttendance.toggle(MonthAttendance.ManualStatus.VACATION,MonthAttendance.ManualStatus.LATE));
        assertNull(MonthAttendance.toggle(MonthAttendance.ManualStatus.VACATION,MonthAttendance.ManualStatus.VACATION));
        assertEquals(MonthAttendance.State.VACATION,MonthAttendance.state(date("2026-10-06"),date("2026-10-02"),null,
            MonthAttendance.ManualStatus.VACATION,WEEKDAYS,Set.of()));
    }

    @Test public void monthlyAnniversaryClampsWithoutDriftingAfterSeptember(){
        var rule=VacationLedger.GrantRule.COURSE_START;
        assertEquals(0,VacationLedger.earned(date("2026-08-30"),rule));
        assertEquals(1,VacationLedger.earned(date("2026-08-31"),rule));
        assertEquals(1,VacationLedger.earned(date("2026-09-29"),rule));
        assertEquals(2,VacationLedger.earned(date("2026-09-30"),rule));
        assertEquals(2,VacationLedger.earned(date("2026-10-30"),rule));
        assertEquals(3,VacationLedger.earned(date("2026-10-31"),rule));
        assertEquals(4,VacationLedger.earned(date("2026-11-30"),rule));
        assertEquals(4,VacationLedger.earned(date("2026-12-30"),rule));
        assertEquals(5,VacationLedger.earned(date("2026-12-31"),rule));
    }

    @Test public void otherGrantRulesHaveDistinctFirstGrantAndMonthEndBoundaries(){
        var completed=VacationLedger.GrantRule.MONTH_COMPLETED;
        assertEquals(0,VacationLedger.earned(date("2026-08-31"),completed));
        assertEquals(0,VacationLedger.earned(date("2026-09-29"),completed));
        assertEquals(1,VacationLedger.earned(date("2026-09-30"),completed));
        assertEquals(1,VacationLedger.earned(date("2026-10-30"),completed));
        assertEquals(2,VacationLedger.earned(date("2026-10-31"),completed));
        var first=VacationLedger.GrantRule.MONTH_FIRST;
        assertEquals(0,VacationLedger.earned(date("2026-08-31"),first));
        assertEquals(1,VacationLedger.earned(date("2026-09-01"),first));
        assertEquals(1,VacationLedger.earned(date("2026-09-30"),first));
        assertEquals(2,VacationLedger.earned(date("2026-10-01"),first));
    }

    @Test public void noVacationAccruesAfterCourseEndsBeforeFebruaryAnniversary(){
        int[] expected={6,5,6};int index=0;
        for(var rule:VacationLedger.GrantRule.values()){
            assertEquals(expected[index++],VacationLedger.earned(CourseCalendar.END,rule));
            assertEquals(VacationLedger.earned(CourseCalendar.END,rule),VacationLedger.earned(date("2028-12-31"),rule));
        }
    }

    @Test public void futureGrantsCannotPayForEarlierVacation(){
        var rule=VacationLedger.GrantRule.COURSE_START;
        assertTrue(VacationLedger.valid(Set.of(date("2026-08-31"),date("2026-10-01")),rule));
        assertFalse(VacationLedger.valid(Set.of(date("2026-08-31"),date("2026-09-01")),rule));
        assertFalse(VacationLedger.valid(Set.of(date("2026-08-31")),VacationLedger.GrantRule.MONTH_COMPLETED));
        assertFalse(VacationLedger.valid(Set.of(CourseCalendar.START.minusDays(1)),rule));
        assertFalse(VacationLedger.valid(Set.of(CourseCalendar.END.plusDays(1)),rule));
        assertTrue(VacationLedger.valid(Set.of(),rule));
    }

    @Test public void backdatedVacationMustAlsoPreserveLaterReservations(){
        var rule=VacationLedger.GrantRule.COURSE_START;
        Set<LocalDate> reserved=Set.of(date("2026-10-01"),date("2026-10-02"));
        assertTrue(VacationLedger.valid(reserved,rule));
        assertTrue(VacationLedger.valid(Set.of(date("2026-09-02")),rule));
        var withBackdate=new HashSet<>(reserved);withBackdate.add(date("2026-09-02"));
        assertFalse(VacationLedger.valid(withBackdate,rule));
    }

    @Test public void balanceSeparatesUsedAndReservedAndCancellationReturnsCredit(){
        var rule=VacationLedger.GrantRule.COURSE_START;
        Set<LocalDate> leave=Set.of(date("2026-09-02"),date("2026-10-01"));
        var before=VacationLedger.balance(date("2026-09-30"),leave,rule);
        assertEquals(2,before.earned());assertEquals(1,before.used());assertEquals(1,before.reserved());assertEquals(0,before.available());
        var onDate=VacationLedger.balance(date("2026-10-01"),leave,rule);
        assertEquals(2,onDate.used());assertEquals(0,onDate.reserved());assertEquals(0,onDate.available());
        var cancelled=VacationLedger.balance(date("2026-09-30"),Set.of(date("2026-09-02")),rule);
        assertEquals(1,cancelled.available());
        var future=VacationLedger.balance(date("2026-09-02"),Set.of(date("2026-10-01"),date("2026-10-02")),rule);
        assertEquals(1,future.earned());assertEquals(2,future.reserved());assertEquals(0,future.available());
    }
}
