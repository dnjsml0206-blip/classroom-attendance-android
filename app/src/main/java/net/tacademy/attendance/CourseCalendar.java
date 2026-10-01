package net.tacademy.attendance;
import java.time.*;
import java.util.*;
/** Korean holidays within this course. Sources: docs/attendance-management.md. */
final class CourseCalendar {
    static final LocalDate START=LocalDate.of(2026,8,31), END=LocalDate.of(2027,2,26);
    static final Map<String,String> HOLIDAYS=Map.ofEntries(
        Map.entry("2026-09-24","추석 연휴"),Map.entry("2026-09-25","추석"),Map.entry("2026-09-26","추석 연휴"),
        Map.entry("2026-10-03","개천절"),Map.entry("2026-10-05","개천절 대체공휴일"),Map.entry("2026-10-09","한글날"),
        Map.entry("2026-12-25","성탄절"),Map.entry("2027-01-01","신정"),
        Map.entry("2027-02-06","설 연휴"),Map.entry("2027-02-07","설날"),Map.entry("2027-02-08","설 연휴"),Map.entry("2027-02-09","설날 대체공휴일"));
    static final Map<String,String> BREAKS=Map.of("2026-09-23","과정 휴강", "2027-02-05","과정 휴강");
    static String dayOff(LocalDate day){return HOLIDAYS.getOrDefault(day.toString(),BREAKS.getOrDefault(day.toString(),""));}
    static boolean classDay(LocalDate day,Set<Integer> weekdays,Set<String> excluded){
        return !day.isBefore(START)&&!day.isAfter(END)&&weekdays.contains(day.getDayOfWeek().getValue())
            &&!BREAKS.containsKey(day.toString())&&!HOLIDAYS.containsKey(day.toString())&&!excluded.contains(day.toString());
    }
}
