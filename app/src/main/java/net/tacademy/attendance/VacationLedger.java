package net.tacademy.attendance;
import java.time.*;
import java.util.*;
/** Local credited leave. Grants accumulate; a later grant cannot fund an earlier absence. */
final class VacationLedger {
    enum GrantRule { COURSE_START("수업월 시작일"), MONTH_COMPLETED("한 달 수료 후"), MONTH_FIRST("매달 1일");
        final String label;GrantRule(String label){this.label=label;}
    }
    static int earned(LocalDate date,GrantRule rule){
        LocalDate end=date.isAfter(CourseCalendar.END)?CourseCalendar.END:date;if(end.isBefore(CourseCalendar.START))return 0;
        int count=0;
        for(int n=0;n<12;n++){
            LocalDate grant=switch(rule){case COURSE_START -> CourseCalendar.START.plusMonths(n);
                case MONTH_COMPLETED -> CourseCalendar.START.plusMonths(n+1);
                case MONTH_FIRST -> YearMonth.from(CourseCalendar.START).plusMonths(n).atDay(1);};
            if(rule==GrantRule.MONTH_FIRST&&grant.isBefore(CourseCalendar.START))continue;
            if(grant.isAfter(end))break;count++;
        }return count;
    }
    record Balance(int earned,int used,int reserved){int available(){return Math.max(0,earned-used-reserved);}}
    static Balance balance(LocalDate today,Set<LocalDate> leave,GrantRule rule){
        int used=0,reserved=0;for(LocalDate date:leave){if(date.isAfter(today))reserved++;else used++;}
        return new Balance(earned(today,rule),used,reserved);
    }
    static boolean valid(Set<LocalDate> leave,GrantRule rule){
        int used=0;for(LocalDate date:new TreeSet<>(leave))if(date.isBefore(CourseCalendar.START)||date.isAfter(CourseCalendar.END)||++used>earned(date,rule))return false;
        return true;
    }
}
