package net.tacademy.attendance;

import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;

public final class MonthAttendanceTest {
    private final LocalDate today=LocalDate.of(2026,9,11);
    private final Set<Integer> weekdays=Set.of(1,2,3,4,5);
    @Test public void ConfirmedRecordsOverrideCurrentHolidayAndWeekdaySettings() {
        LocalDate saturday=LocalDate.of(2026,9,12);
        assertEquals(MonthAttendance.State.COMPLETE,MonthAttendance.state(saturday,today,new Policy.Record("08:42","17:50"),weekdays,Set.of(saturday.toString())));
        assertEquals(MonthAttendance.State.ENTERED,MonthAttendance.state(saturday,today,new Policy.Record("08:42",""),weekdays,Set.of()));
    }
    @Test public void NoRecordIsUnknownRatherThanAnInventedAbsence() {
        assertEquals(MonthAttendance.State.UNKNOWN,MonthAttendance.state(today.minusDays(1),today,null,weekdays,Set.of()));
        assertEquals(MonthAttendance.State.UNKNOWN,MonthAttendance.state(today,today,new Policy.Record("",""),weekdays,Set.of()));
        assertEquals(MonthAttendance.State.PLANNED,MonthAttendance.state(today.plusDays(3),today,null,weekdays,Set.of()));
        assertEquals(MonthAttendance.State.HOLIDAY,MonthAttendance.state(today,today,null,weekdays,Set.of(today.toString())));
        assertEquals(MonthAttendance.State.NO_CLASS,MonthAttendance.state(today.plusDays(1),today,null,weekdays,Set.of()));
    }
    @Test public void SundayGridSupportsSixWeeksAndLeapDayWithoutAdjacentMonthRecords() {
        List<LocalDate> august=MonthAttendance.cells(YearMonth.of(2026,8));
        assertEquals(42,august.size());assertNull(august.get(0));assertEquals(LocalDate.of(2026,8,1),august.get(6));
        assertEquals(31,august.stream().filter(Objects::nonNull).distinct().count());
        List<LocalDate> february=MonthAttendance.cells(YearMonth.of(2024,2));
        assertEquals(35,february.size());assertEquals(LocalDate.of(2024,2,29),february.get(32));assertNull(february.get(34));
    }
    @Test public void PastAnnotationsSupplyAllStatusesWithoutInventingTimes() {
        LocalDate start=LocalDate.of(2026,8,31);
        assertEquals(MonthAttendance.State.ATTENDED,MonthAttendance.state(start,today,null,MonthAttendance.ManualStatus.PRESENT,weekdays,Set.of()));
        assertEquals(MonthAttendance.State.EARLY_LEAVE,MonthAttendance.state(start,today,null,MonthAttendance.ManualStatus.EARLY_LEAVE,weekdays,Set.of()));
        assertEquals(MonthAttendance.State.ABSENT,MonthAttendance.state(start,today,null,MonthAttendance.ManualStatus.ABSENT,weekdays,Set.of()));
        assertEquals(MonthAttendance.State.UNKNOWN,MonthAttendance.state(start,today,null,null,weekdays,Set.of()));
    }
    @Test public void HistoricalCorrectionOverridesCurrentSettingsAndObservedCompletion() {
        LocalDate past=today.minusDays(1);
        Policy.Record observed=new Policy.Record("08:40","15:30");
        assertEquals(MonthAttendance.State.EARLY_LEAVE,MonthAttendance.state(past,today,observed,MonthAttendance.ManualStatus.EARLY_LEAVE,Set.of(),Set.of(past.toString())));
        assertEquals(MonthAttendance.State.COMPLETE,MonthAttendance.state(past,today,observed,null,Set.of(),Set.of(past.toString())));
        assertEquals("08:40",observed.in());assertEquals("15:30",observed.out());
    }
    @Test public void TodayAndFutureNeverUseManualAnnotations() {
        assertEquals(MonthAttendance.State.UNKNOWN,MonthAttendance.state(today,today,null,MonthAttendance.ManualStatus.ABSENT,weekdays,Set.of()));
        LocalDate future=today.plusDays(3);
        assertEquals(MonthAttendance.State.PLANNED,MonthAttendance.state(future,today,null,MonthAttendance.ManualStatus.PRESENT,weekdays,Set.of()));
    }

}
