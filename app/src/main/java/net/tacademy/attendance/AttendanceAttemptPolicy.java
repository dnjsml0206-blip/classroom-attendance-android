package net.tacademy.attendance;
import java.time.*;
import java.util.Set;
/** Bounded result observation and explicit user-initiated attendance outside automatic retry windows. */
final class AttendanceAttemptPolicy {
    static final long PAGE_WAIT_MS=35_000, RESULT_WAIT_MS=35_000, MAX_ATTEMPT_MS=105_000, RECOVERY_PAGE_WAIT_MS=70_000;
    /** A single local observation extension leaves 35 seconds for the eventual result. */
    static boolean extendBeforeClick(boolean attempted,boolean extended,String stage){
        return !attempted&&!extended&&(stage.equals("CHROME_NOT_VISIBLE")||stage.equals("ADDRESS_NOT_VISIBLE"));
    }
    static boolean pauseAfterFailure(boolean attempted,String reason){
        if(attempted)return true;
        return !java.util.Set.of("TIMEOUT_CHROME_NOT_VISIBLE","TIMEOUT_ADDRESS_NOT_VISIBLE",
            "SCREEN_LOCKED_DURING_WORK","WIFI_LOST_DURING_WORK").contains(reason);
    }
    static long afterAction(long started,long action){return Math.min(started+MAX_ATTEMPT_MS,action+RESULT_WAIT_MS);}
    static Policy.Phase activationPhase(ZonedDateTime now,Set<Integer> days,Set<String> excluded,String start,String end,String checkout,boolean checkedIn,boolean checkedOut){
        if(checkedOut||!Policy.isClassDay(now,days,excluded))return Policy.Phase.IDLE;
        if(!now.withZoneSameInstant(Policy.ZONE).toLocalTime().isBefore(Policy.checkoutStartTime(now.withZoneSameInstant(Policy.ZONE).toLocalDate(),checkout)))return Policy.Phase.CHECKOUT;
        return !checkedIn&&Policy.inWindow(now,start,end)?Policy.Phase.ENTRY:Policy.Phase.IDLE;
    }
    record ManualGate(String code,String problem){}
    static String manualProblem(Policy.Phase phase,ZonedDateTime now,Set<Integer> days,Set<String> excluded,String start,String checkout,boolean readOnly){
        return manualGate(phase,now,days,excluded,start,checkout,readOnly).problem();
    }
    static ManualGate manualGate(Policy.Phase phase,ZonedDateTime now,Set<Integer> days,Set<String> excluded,String start,String checkout,boolean readOnly){
        return manualGate(phase,now,days,excluded,start,checkout,readOnly,false);
    }
    static ManualGate manualGate(Policy.Phase phase,ZonedDateTime now,Set<Integer> days,Set<String> excluded,String start,String checkout,boolean readOnly,boolean earlyApproved){
        if(phase==Policy.Phase.IDLE)return new ManualGate("PHASE_REQUIRED","입실 또는 퇴실을 선택해 주세요.");
        if(readOnly)return new ManualGate("ALLOWED","");
        if(!Policy.isClassDay(now,days,excluded))return new ManualGate("NON_CLASS_DAY","오늘은 등록된 수업일이 아니에요. 수업 요일과 휴강일을 확인해 주세요.");
        LocalTime t=now.withZoneSameInstant(Policy.ZONE).toLocalTime();
        if(phase==Policy.Phase.CHECKOUT&&!earlyApproved&&t.isBefore(Policy.checkoutStartTime(now.withZoneSameInstant(Policy.ZONE).toLocalDate(),checkout)))return new ManualGate("CHECKOUT_NOT_OPEN","퇴실은 "+Policy.checkoutStartTime(now.withZoneSameInstant(Policy.ZONE).toLocalDate(),checkout)+"부터 할 수 있어요.");
        if(phase==Policy.Phase.ENTRY&&t.isBefore(LocalTime.parse(start)))return new ManualGate("ENTRY_NOT_OPEN","입실은 "+start+"부터 할 수 있어요.");
        return new ManualGate("ALLOWED","");
    }
}
