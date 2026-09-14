package net.tacademy.attendance;

import java.time.*;
import java.util.*;

/** Local observations only: a missing record is never proof of absence. */
final class MonthAttendance {
    enum State {
        COMPLETE("완료"), ENTERED("입실"), UNKNOWN("미확인"), HOLIDAY("휴강"), PLANNED("예정"), NO_CLASS("비수업");
        final String label;
        State(String label) { this.label=label; }
    }
    static State state(LocalDate date, LocalDate today, Policy.Record record, Set<Integer> days, Set<String> holidays) {
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
