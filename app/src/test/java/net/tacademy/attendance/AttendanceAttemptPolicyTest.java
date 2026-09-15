package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.Set;
public final class AttendanceAttemptPolicyTest {
    private ZonedDateTime at(String time){return ZonedDateTime.parse("2026-09-15T"+time+"+09:00[Asia/Seoul]");}
    private String problem(Policy.Phase phase,String time){return AttendanceAttemptPolicy.manualProblem(phase,at(time),Set.of(2),Set.of(),"08:30","17:50",false);}
    @Test public void lateConfirmationGetsFullResultWait(){
        long start=1_000, button=start+31_919, confirmation=start+33_452;
        assertEquals(button+35_000,AttendanceAttemptPolicy.afterAction(start,button));
        assertEquals(confirmation+35_000,AttendanceAttemptPolicy.afterAction(start,confirmation));
        assertTrue(AttendanceAttemptPolicy.afterAction(start,confirmation)>start+35_000);
    }
    @Test public void repeatedActionsNeverCreateAnUnboundedSession(){assertEquals(106_000,AttendanceAttemptPolicy.afterAction(1_000,103_000));}
    @Test public void userCanCheckOutAfterAutomaticWindowEnds(){assertEquals("",problem(Policy.Phase.CHECKOUT,"18:15:00"));}
    @Test public void checkoutNeverStartsAt1749OrBeforeFiveSecondBuffer(){
        assertFalse(problem(Policy.Phase.CHECKOUT,"17:49:59").isEmpty());assertFalse(problem(Policy.Phase.CHECKOUT,"17:50:04").isEmpty());assertEquals("",problem(Policy.Phase.CHECKOUT,"17:50:05"));
    }
    @Test public void manualEntryAllowsLateArrivalButNotBeforeOpening(){assertFalse(problem(Policy.Phase.ENTRY,"08:29:59").isEmpty());assertEquals("",problem(Policy.Phase.ENTRY,"09:40:00"));}
    @Test public void holidayBlocksSubmissionButPermitsObservation(){
        assertFalse(AttendanceAttemptPolicy.manualProblem(Policy.Phase.CHECKOUT,at("18:15:00"),Set.of(2),Set.of("2026-09-15"),"08:30","17:50",false).isEmpty());
        assertEquals("",AttendanceAttemptPolicy.manualProblem(Policy.Phase.CHECKOUT,at("18:15:00"),Set.of(2),Set.of("2026-09-15"),"08:30","17:50",true));
    }
    @Test public void activationAfterCheckoutWindowSelectsCheckout(){
        assertEquals(Policy.Phase.CHECKOUT,activation("18:35:00",true,false));
        assertEquals(Policy.Phase.IDLE,activation("18:35:00",true,true));
    }
    @Test public void activationRespectsMorningWindowAndCheckoutBuffer(){
        assertEquals(Policy.Phase.IDLE,activation("08:29:59",false,false));
        assertEquals(Policy.Phase.ENTRY,activation("08:30:00",false,false));
        assertEquals(Policy.Phase.IDLE,activation("09:30:00",false,false));
        assertEquals(Policy.Phase.IDLE,activation("17:50:04",true,false));
        assertEquals(Policy.Phase.CHECKOUT,activation("17:50:05",true,false));
    }
    private Policy.Phase activation(String time,boolean in,boolean out){return AttendanceAttemptPolicy.activationPhase(at(time),Set.of(2),Set.of(),"08:30","09:30","17:50",in,out);}
}
