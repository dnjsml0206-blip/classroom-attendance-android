package net.tacademy.attendance;

import java.time.*;
import java.util.*;

/** Observed times and explicit local annotations; missing records never imply absence. */
final class MonthAttendance {
    enum State {
        ATTENDED("출석"), EARLY_LEAVE("조퇴"), LATE("지각"), LATE_EARLY("지각·조퇴"), ABSENT("결석"), VACATION("휴가"), COMPLETE("판정대기"), ENTERED("입실"), UNKNOWN("미확인"), HOLIDAY("휴강"), PLANNED("예정"), NO_CLASS("비수업");
        final String label;
        State(String label) { this.label=label; }
    }
    enum ManualStatus {
        PRESENT(State.ATTENDED), EARLY_LEAVE(State.EARLY_LEAVE), ABSENT(State.ABSENT), LATE(State.LATE), LATE_EARLY(State.LATE_EARLY), VACATION(State.VACATION);
        final State state;
        ManualStatus(State state) { this.state=state; }
    }
    /** Keep legacy combined storage readable, but expose independent late/early toggles. */
    static boolean selected(ManualStatus value,ManualStatus option){
        return value==option || value==ManualStatus.LATE_EARLY&&(option==ManualStatus.LATE||option==ManualStatus.EARLY_LEAVE);
    }
    static ManualStatus toggle(ManualStatus value,ManualStatus option){
        if(option==ManualStatus.LATE||option==ManualStatus.EARLY_LEAVE){
            boolean late=selected(value,ManualStatus.LATE),early=selected(value,ManualStatus.EARLY_LEAVE);
            if(option==ManualStatus.LATE)late=!late;else early=!early;
            return late&&early?ManualStatus.LATE_EARLY:late?ManualStatus.LATE:early?ManualStatus.EARLY_LEAVE:value==ManualStatus.LATE_EARLY||value==ManualStatus.LATE||value==ManualStatus.EARLY_LEAVE?ManualStatus.PRESENT:option;
        }
        return value==option?null:option;
    }
    static State state(LocalDate date, LocalDate today, Policy.Record record, Set<Integer> days, Set<String> holidays) {
        return state(date,today,record,null,days,holidays);
    }
    static State state(LocalDate date, LocalDate today, Policy.Record record, ManualStatus manual, Set<Integer> days, Set<String> holidays) {
        if (manual == ManualStatus.VACATION || (!date.isAfter(today) && manual != null)) return manual.state;
        if (record != null && record.checkedOut()) return State.COMPLETE;
        if (record != null && record.checkedIn()) return State.ENTERED;
        if (holidays.contains(date.toString())) return State.HOLIDAY;
        if (!days.contains(date.getDayOfWeek().getValue())) return State.NO_CLASS;
        return date.isAfter(today) ? State.PLANNED : State.UNKNOWN;
    }
    static List<LocalDate> cells(YearMonth month) {
        List<LocalDate> cells=new ArrayList<>();
        for(int i=0;i<month.atDay(1).getDayOfWeek().getValue()%7;i++) cells.add(null);
        for(int day=1;day<=month.lengthOfMonth();day++) cells.add(month.atDay(day));
        while(cells.size()%7!=0) cells.add(null);
        return cells;
    }
}
