package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;
public final class AttendanceFractionTest {
    @Test public void eachOccurrenceChangesRateBeforeThreeAccumulate(){
        for(int events=1;events<=5;events++){
            var s=new AttendanceMetrics.Summary(120,20,20,0,0,events,0);
            assertEquals(events/3.0,s.equivalent(),1e-9);
            assertEquals((20-events/3.0)/20*100,s.currentRate(),1e-9);
            assertTrue(s.currentRate()<100);
        }
    }
    @Test public void reportedSeptemberCorrectionsImmediatelyReduceCurrentRate(){
        var manual=Map.of(LocalDate.of(2026,9,28),MonthAttendance.ManualStatus.EARLY_LEAVE,
                LocalDate.of(2026,9,30),MonthAttendance.ManualStatus.LATE);
        var s=AttendanceMetrics.summarize(LocalDate.of(2026,10,2).atTime(10,0).atZone(Policy.ZONE),
                Set.of(1,2,3,4,5),Set.of(),d->null,manual::get,"09:11","17:50");
        assertEquals(21,s.elapsed());assertEquals(1,s.late());assertEquals(1,s.early());
        assertEquals("96.8%",s.currentDisplay());assertEquals("99.4%",s.projectedDisplay());
        assertEquals("⅔",s.equivalentDisplay());assertEquals(23,s.remaining());assertEquals(70,s.remainingEvents());
    }
    @Test public void fractionLabelsNeverPresentOneOrTwoOccurrencesAsZero(){
        String[] values={"0","⅓","⅔","1","1 ⅓","1 ⅔","2"};
        for(int i=0;i<values.length;i++)assertEquals(values[i],AttendanceMetrics.thirds(i));
        assertEquals("24",AttendanceMetrics.days(24));assertEquals("24.2",AttendanceMetrics.days(24.2));
    }
    @Test public void eightyPercentBoundaryRejectsEvenOneAdditionalOccurrence(){
        var boundary=new AttendanceMetrics.Summary(120,100,100,0,24,0,0);
        assertEquals(80,boundary.projectedRate(),1e-9);assertEquals(0,boundary.remainingEvents());
        var over=new AttendanceMetrics.Summary(120,100,100,0,24,1,0);
        assertTrue(over.projectedRate()<80);assertEquals(1.0/3,over.exceeded(),1e-9);
        assertEquals(0,over.remaining());assertEquals(0,over.remainingEvents());
    }
    @Test public void budgetsMatchExactThresholdIncludingNonMultipleOfFiveCourses(){
        for(int total=118;total<=122;total++)for(int absent=0;absent<=26;absent++)for(int events=0;events<=6;events++){
            var s=new AttendanceMetrics.Summary(total,60,60,0,absent,events,0);
            int remaining=0,eventRoom=0;
            for(int n=1;n<=total-60;n++)if(5*(3*(absent+n)+events)<=3*total)remaining=n;
            for(int n=1;n<=2*(total-60);n++)if(5*(3*absent+events+n)<=3*total)eventRoom=n;
            assertEquals(remaining,s.remaining());assertEquals(eventRoom,s.remainingEvents());
        }
    }
}
