package net.tacademy.attendance;

import java.time.*;
import java.util.*;

/** Observed times and explicit local annotations; missing records never imply absence. */
final class MonthAttendance {
    enum State {
        ATTENDED("출석"), EARLY_LEAVE("조퇴"), ABSENT("결석"), COMPLETE("완료"), ENTERED("입실"), UNKNOWN("미확인"), HOLIDAY("휴강"), PLANNED("예정"), NO_CLASS("비수업");
        final String label;
        State(String label) { this.label=label; }
    }
    enum ManualStatus {
        PRESENT(State.ATTENDED), EARLY_LEAVE(State.EARLY_LEAVE), ABSENT(State.ABSENT);
        final State state;
        ManualStatus(State state) { this.state=state; }
    }
    static State state(LocalDate date, LocalDate today, Policy.Record record, Set<Integer> days, Set<String> holidays) {
        return state(date,today,record,null,days,holidays);
    }
    static State state(LocalDate date, LocalDate today, Policy.Record record, ManualStatus manual, Set<Integer> days, Set<String> holidays) {
        if (date.isBefore(today) && manual != null) return manual.state;
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
