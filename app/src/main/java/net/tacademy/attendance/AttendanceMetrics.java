package net.tacademy.attendance;
import java.time.*;
import java.util.*;
import java.util.function.Function;
/** Local day-based estimate. Missing records are unknown, never silently counted as absent. */
final class AttendanceMetrics {
    record Day(boolean known,boolean absent,boolean late,boolean early){}
    static Day day(Policy.Record record,MonthAttendance.ManualStatus manual,String lateAfter,String checkout){
        if(manual!=null)return switch(manual){
            case PRESENT -> new Day(true,false,false,false);
            case ABSENT -> new Day(true,true,false,false);
            case EARLY_LEAVE -> new Day(true,false,false,true);
            case LATE -> new Day(true,false,true,false);
            case LATE_EARLY -> new Day(true,false,true,true);
        };
        if(record==null||!record.checkedOut())return new Day(false,false,false,false);
        return new Day(true,false,isLate(record.in(),lateAfter),isEarly(record.out(),checkout));
    }
    static boolean isLate(String in,String threshold){
        try{return !threshold.isEmpty()&&LocalTime.parse(in).isAfter(LocalTime.parse(threshold));}catch(RuntimeException e){return false;}
    }
    static boolean isEarly(String out,String checkout){
        // Compare against the permitted checkout minute, not the dispatch cushion.
        try{return LocalTime.parse(out).isBefore(LocalTime.parse(checkout));}catch(RuntimeException e){return false;}
    }
    record Summary(int total,int elapsed,int known,int missing,int absent,int late,int early){
        int converted(){return (late+early)/3;}
        int remainder(){return (late+early)%3;}
        int equivalent(){return absent+converted();}
        int allowance(){return total/5;}
        int required(){return total-allowance();}
        int future(){return total-elapsed;}
        int remaining(){return Math.min(future(),Math.max(0,allowance()-equivalent()));}
        int exceeded(){return Math.max(0,equivalent()-allowance());}
        double currentRate(){return known==0?Double.NaN:100.0*(known-equivalent())/known;}
        double projectedRate(){return total==0?Double.NaN:100.0*(total-equivalent())/total;}
    }
    static Summary summarize(LocalDate today,Set<Integer> weekdays,Set<String> excluded,
            Function<LocalDate,Policy.Record> records,Function<LocalDate,MonthAttendance.ManualStatus> manual,String lateAfter,String checkout){
        int total=0,elapsed=0,known=0,absent=0,late=0,early=0;
        for(LocalDate date=CourseCalendar.START;!date.isAfter(CourseCalendar.END);date=date.plusDays(1)){
            if(!CourseCalendar.classDay(date,weekdays,excluded))continue;
            total++;if(date.isAfter(today))continue;
            elapsed++;Day d=day(records.apply(date),manual.apply(date),lateAfter,CourseSchedule.checkout(date,checkout));
            if(!d.known())continue;
            known++;if(d.absent())absent++;if(d.late())late++;if(d.early())early++;
        }
        return new Summary(total,elapsed,known,elapsed-known,absent,late,early);
    }
    static String percent(double value){return Double.isNaN(value)?"—":String.format(Locale.KOREAN,"%.1f%%",value);}
}
