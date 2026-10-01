package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;
public final class CourseScheduleTest {
    private static final Set<Integer> DAYS=Set.of(1,2,3,4,5);
    private ZonedDateTime at(String date,String time){return LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(Policy.ZONE);}
    private Policy.Phase phase(String date,String time){return Policy.phase(at(date,time),DAYS,Set.of(),"08:30","09:30","17:50",10,true,false);}
    @Test public void shortDaysWaitUntilExactlyTwoSecondsPast1550(){
        for(String date:List.of("2026-10-06","2026-10-07")){
            assertEquals(Policy.Phase.IDLE,phase(date,"15:49:59"));
            assertEquals(Policy.Phase.IDLE,phase(date,"15:50:01.999999999"));
            assertEquals(Policy.Phase.CHECKOUT,phase(date,"15:50:02"));
            assertEquals(Policy.Phase.CHECKOUT,phase(date,"15:59:59"));
            assertEquals(Policy.Phase.IDLE,phase(date,"16:00:00"));
            assertEquals(Policy.Phase.IDLE,phase(date,"17:50:05"));
        }
    }
    @Test public void normalDaysKeepConfiguredTimeAndFiveSeconds(){
        assertEquals(LocalTime.of(17,50,5),Policy.checkoutStartTime(LocalDate.of(2026,10,8),"17:50"));
        assertEquals(LocalTime.of(16,20,5),Policy.checkoutStartTime(LocalDate.of(2026,10,8),"16:20"));
        assertEquals(LocalTime.of(15,50,2),Policy.checkoutStartTime(LocalDate.of(2026,10,6),"18:10"));
    }
    @Test public void nextAlarmUsesEachCandidateDateAndReturnsToNormalAfterShortDays(){
        assertEquals(at("2026-10-06","15:50:02"),Policy.nextWake(at("2026-10-06","09:01"),DAYS,Set.of(),"08:30","09:30","17:50",10,true,false));
        assertEquals(at("2026-10-06","16:00"),Policy.nextWake(at("2026-10-06","15:50:02"),DAYS,Set.of(),"08:30","09:30","17:50",10,true,false));
        assertEquals(at("2026-10-07","08:30"),Policy.nextWake(at("2026-10-06","16:00"),DAYS,Set.of(),"08:30","09:30","17:50",10,true,false));
        assertEquals(at("2026-10-08","17:50:05"),Policy.nextWake(at("2026-10-08","10:00"),DAYS,Set.of(),"08:30","09:30","17:50",10,true,false));
    }
    @Test public void holidaysNeverStartEntryOrCheckoutEvenWithoutUserExclusions(){
        for(String date:List.of("2026-09-23","2026-09-24","2026-10-05","2027-02-05")){
            assertEquals(Policy.Phase.IDLE,Policy.phase(at(date,"08:45"),DAYS,Set.of(),"08:30","09:30","17:50",10,false,false));
            assertEquals(Policy.Phase.IDLE,phase(date,"17:50:05"));
            assertEquals(Policy.Phase.IDLE,AttendanceAttemptPolicy.activationPhase(at(date,"17:51"),DAYS,Set.of(),"08:30","09:30","17:50",true,false));
        }
        assertEquals(at("2026-09-28","08:30"),Policy.nextWake(at("2026-09-22","18:00"),DAYS,Set.of(),"08:30","09:30","17:50",10,true,true));
    }
    @Test public void manualAndActivationPathsUseSameTwoSecondBoundary(){
        ZonedDateTime before=at("2026-10-06","15:50:01"),due=at("2026-10-06","15:50:02");
        assertEquals(Policy.Phase.IDLE,AttendanceAttemptPolicy.activationPhase(before,DAYS,Set.of(),"08:30","09:30","17:50",true,false));
        assertEquals(Policy.Phase.CHECKOUT,AttendanceAttemptPolicy.activationPhase(due,DAYS,Set.of(),"08:30","09:30","17:50",true,false));
        assertFalse(AttendanceAttemptPolicy.manualGate(Policy.Phase.CHECKOUT,before,DAYS,Set.of(),"08:30","17:50",false).problem().isEmpty());
        assertTrue(AttendanceAttemptPolicy.manualGate(Policy.Phase.CHECKOUT,due,DAYS,Set.of(),"08:30","17:50",false).problem().isEmpty());
    }
    @Test public void shortDayIsNotMisclassifiedWhenSummaryIsOpenedOnALaterNormalDay(){
        Map<LocalDate,Policy.Record> records=Map.of(LocalDate.of(2026,10,6),new Policy.Record("08:50","15:50"),LocalDate.of(2026,10,7),new Policy.Record("08:50","15:49"),LocalDate.of(2026,10,8),new Policy.Record("08:50","15:50"));
        var summary=AttendanceMetrics.summarize(LocalDate.of(2026,10,9),DAYS,Set.of(),records::get,d->null,"09:00","17:50");
        assertEquals(3,summary.known());assertEquals(2,summary.early());
    }
    @Test public void extraHolidayAndCompletedExitOverrideShortDay(){
        assertEquals(Policy.Phase.IDLE,Policy.phase(at("2026-10-06","15:50:02"),DAYS,Set.of("2026-10-06"),"08:30","09:30","17:50",10,true,false));
        assertEquals(Policy.Phase.IDLE,Policy.phase(at("2026-10-06","15:50:02"),DAYS,Set.of(),"08:30","09:30","17:50",10,true,true));
    }
    @Test public void timezoneAndCustomRetryArePreserved(){
        assertTrue(Policy.checkoutDue(at("2026-10-06","15:50:02").withZoneSameInstant(ZoneOffset.UTC),"17:50",1));
        assertFalse(Policy.checkoutDue(at("2026-10-06","15:51"),"17:50",1));
    }

    @Test public void allThirteenShortDaysMatchSourceHoursAndApplyAcrossTheCourse(){
        Set<LocalDate> six=new TreeSet<>(),seven=new TreeSet<>();
        for(var lesson:CourseTimetable.DAYS.values())if(CourseSchedule.shortened(lesson.date())){
            (lesson.hours()==6?six:seven).add(lesson.date());
            String end=lesson.hours()==6?"16:00":"17:00";String dispatch=lesson.hours()==6?"15:50:02":"16:50:02";
            assertEquals(end,CourseSchedule.classEnd(lesson.date()));assertEquals(LocalTime.parse(dispatch),CourseSchedule.dispatch(lesson.date(),"17:50"));
            var due=lesson.date().atTime(LocalTime.parse(dispatch)).atZone(Policy.ZONE);
            assertFalse(Policy.checkoutDue(due.minusNanos(1),"17:50",10));assertTrue(Policy.checkoutDue(due,"17:50",10));
            assertFalse(Policy.checkoutDue(lesson.date().atTime(LocalTime.parse(end)).atZone(Policy.ZONE),"17:50",10));
        }
        assertEquals(Set.of(LocalDate.parse("2026-08-31"),LocalDate.parse("2026-09-18"),LocalDate.parse("2026-10-06"),LocalDate.parse("2026-10-07"),LocalDate.parse("2027-02-19"),LocalDate.parse("2027-02-22"),LocalDate.parse("2027-02-23")),six);
        assertEquals(Set.of(LocalDate.parse("2026-09-01"),LocalDate.parse("2026-10-26"),LocalDate.parse("2026-10-27"),LocalDate.parse("2026-10-28"),LocalDate.parse("2026-10-29"),LocalDate.parse("2026-12-08")),seven);
    }
    @Test public void remindersRespectSchoolDaysAndEntryStartWithoutDependingOnAttendance(){
        assertFalse(CourseSchedule.noticeDue(at("2026-10-06","08:29:59"),DAYS,Set.of(),"08:30"));
        assertTrue(CourseSchedule.noticeDue(at("2026-10-06","08:30"),DAYS,Set.of(),"08:30"));
        assertTrue(CourseSchedule.noticeDue(at("2026-10-06","12:00"),DAYS,Set.of(),"08:30"));
        assertFalse(CourseSchedule.noticeDue(at("2026-10-06","12:00"),DAYS,Set.of("2026-10-06"),"08:30"));
        assertFalse(CourseSchedule.noticeDue(at("2026-10-08","08:30"),DAYS,Set.of(),"08:30"));
        assertEquals(at("2026-10-06","08:30"),CourseSchedule.nextNotice(at("2026-10-01","09:00"),DAYS,Set.of(),"08:30"));
        assertEquals(at("2026-10-07","08:30"),CourseSchedule.nextNotice(at("2026-10-06","08:30"),DAYS,Set.of(),"08:30"));
        assertEquals(at("2026-10-26","08:45"),CourseSchedule.nextNotice(at("2026-10-07","10:00"),DAYS,Set.of(),"08:45"));
        assertNull(CourseSchedule.nextNotice(at("2027-02-23","09:00"),DAYS,Set.of(),"08:30"));
    }
    @Test public void sevenHourHistoricalExitIsNotEarlyAndItsPreviousMinuteIs(){
        var date=LocalDate.of(2026,12,8);assertFalse(AttendanceMetrics.isEarly("16:50",CourseSchedule.checkout(date,"17:50")));
        assertTrue(AttendanceMetrics.isEarly("16:49",CourseSchedule.checkout(date,"17:50")));
        assertEquals(at("2026-10-26","16:50:02"),Policy.nextWake(at("2026-10-26","12:00"),DAYS,Set.of(),"08:30","09:30","17:50",10,true,false));
    }
}
