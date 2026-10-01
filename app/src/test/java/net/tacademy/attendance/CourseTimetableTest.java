package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;
public final class CourseTimetableTest {
    @Test public void everySourceClassDayIsPresentExactlyOnce(){
        assertEquals(120,CourseTimetable.DAYS.size());assertEquals(940,CourseTimetable.totalHours());
        for(LocalDate d=CourseCalendar.START;!d.isAfter(CourseCalendar.END);d=d.plusDays(1))assertEquals(d.toString(),CourseCalendar.classDay(d,Set.of(1,2,3,4,5),Set.of()),CourseTimetable.on(d)!=null);
        assertNull(CourseTimetable.on(LocalDate.of(2026,9,23)));assertNull(CourseTimetable.on(LocalDate.of(2027,2,5)));
        assertNull(CourseTimetable.on(LocalDate.of(2026,10,5)));assertNull(CourseTimetable.on(LocalDate.of(2027,2,9)));
    }
    @Test public void monthTotalsFollowActualMonthsInsteadOfPdfCarryoverColumns(){
        int[] days={1,19,20,21,22,20,17},hours={6,149,152,168,175,160,130};
        for(int i=0;i<days.length;i++){var entries=CourseTimetable.month(YearMonth.of(2026,8).plusMonths(i));assertEquals(days[i],entries.size());assertEquals(hours[i],entries.stream().mapToInt(CourseTimetable.Lesson::hours).sum());}
    }
    @Test public void shortDaysAndSubjectTransitionsMatchOriginal(){
        lesson("2026-08-31","OT · 특강",6);lesson("2026-09-01","AI+DA 입문 · 취업 특강",7);
        lesson("2026-09-18","데이터 서비스 기획",6);lesson("2026-10-06","태블로 시각화",6);
        lesson("2026-10-29","ML/DL 프로젝트",7);lesson("2026-10-30","LLM",8);
        lesson("2026-11-05","LLM",8);lesson("2026-11-06","컴퓨터 비전",8);
        lesson("2026-12-08","알고리즘 · 코딩 테스트",7);lesson("2026-12-09","실전 프로젝트",8);
        lesson("2027-02-18","최종 발표",8);lesson("2027-02-19","실전 프로젝트",6);lesson("2027-02-23","실전 프로젝트",6);
        lesson("2027-02-24","취업 컨설팅",8);lesson("2027-02-26","현직자 멘토링",8);
    }
    @Test public void groupingPreservesAllLessonsAndBoundaries(){
        var modules=CourseTimetable.modules();assertEquals(120,modules.stream().mapToInt(m->m.lessons().size()).sum());assertEquals(940,modules.stream().mapToInt(CourseTimetable.Module::hours).sum());
        assertEquals(CourseCalendar.START,CourseTimetable.clamp(LocalDate.of(2026,1,1)));assertEquals(CourseCalendar.END,CourseTimetable.clamp(LocalDate.of(2027,5,1)));
        assertTrue(CourseTimetable.month(YearMonth.of(2027,3)).isEmpty());
        var project=modules.stream().filter(m->m.title().equals("실전 프로젝트")).findFirst().orElseThrow();assertEquals(LocalDate.of(2026,12,9),project.first());assertEquals(LocalDate.of(2027,2,23),project.last());
    }
    private void lesson(String date,String title,int hours){var l=CourseTimetable.on(LocalDate.parse(date));assertNotNull(date,l);assertEquals(title,l.title());assertEquals(hours,l.hours());}
}
