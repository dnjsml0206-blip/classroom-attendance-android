package net.tacademy.attendance;
import java.time.*;
import java.util.Set;
/** Source hours with the user's course-wide short-day rule: 6h ends at 16:00, 7h at 17:00. */
final class CourseSchedule {
    private CourseSchedule(){}
    static boolean shortened(LocalDate day){var lesson=CourseTimetable.on(day);return lesson!=null&&(lesson.hours()==6||lesson.hours()==7);}
    static String classEnd(LocalDate day){var lesson=CourseTimetable.on(day);return shortened(day)?LocalTime.of(lesson.hours()+10,0).toString():"18:00";}
    static String checkout(LocalDate day,String configured){return shortened(day)?LocalTime.parse(classEnd(day)).minusMinutes(10).toString():configured;}
    static int cushion(LocalDate day){return shortened(day)?2:Policy.CHECKOUT_DELAY_SECONDS;}
    static LocalTime dispatch(LocalDate day,String configured){return LocalTime.parse(checkout(day,configured)).plusSeconds(cushion(day));}
    static boolean activeShortDay(LocalDate day,Set<Integer> days,Set<String> excluded){return shortened(day)&&CourseCalendar.classDay(day,days,excluded);}
    static String summary(LocalDate day){return CourseTimetable.on(day).hours()+"시간 수업 · "+classEnd(day)+" 종료\n자동 퇴실 "+dispatch(day,"17:50")+"부터";}
    static boolean noticeDue(ZonedDateTime now,Set<Integer> days,Set<String> excluded,String entryStart){var local=now.withZoneSameInstant(Policy.ZONE);return activeShortDay(local.toLocalDate(),days,excluded)&&!local.toLocalTime().isBefore(LocalTime.parse(entryStart));}
    /** Strictly future starts only: opening later today delivers a catch-up notice, never a retry loop. */
    static ZonedDateTime nextNotice(ZonedDateTime now,Set<Integer> days,Set<String> excluded,String entryStart){
        for(var day:CourseTimetable.DAYS.navigableKeySet())if(activeShortDay(day,days,excluded)){
            var due=day.atTime(LocalTime.parse(entryStart)).atZone(Policy.ZONE);if(due.isAfter(now))return due;
        }return null;
    }
}
