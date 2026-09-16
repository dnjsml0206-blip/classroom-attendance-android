package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;
public final class AttendanceRecoveryTest {
    @Test public void focusLossCanExtendOnlyBeforeAnyClickAndOnlyOnce(){
        for(String stage:new String[]{"CHROME_NOT_VISIBLE","ADDRESS_NOT_VISIBLE"}){
            assertTrue(AttendanceAttemptPolicy.extendBeforeClick(false,false,stage));
            assertFalse(AttendanceAttemptPolicy.extendBeforeClick(true,false,stage));
            assertFalse(AttendanceAttemptPolicy.extendBeforeClick(false,true,stage));
        }
        for(String stage:new String[]{"DATE_MISMATCH","DATE_UNREADABLE","BUTTON_UNAVAILABLE","RECORD_NOT_UPDATED","SITE_ERROR_SCREEN"})
            assertFalse(AttendanceAttemptPolicy.extendBeforeClick(false,false,stage));
    }
    @Test public void transientPreclickFailureDoesNotPauseTheWholePhase(){
        for(String reason:new String[]{"TIMEOUT_CHROME_NOT_VISIBLE","TIMEOUT_ADDRESS_NOT_VISIBLE","SCREEN_LOCKED_DURING_WORK","WIFI_LOST_DURING_WORK"}){
            assertFalse(AttendanceAttemptPolicy.pauseAfterFailure(false,reason));
            assertTrue(AttendanceAttemptPolicy.pauseAfterFailure(true,reason));
        }
    }
    @Test public void uncertainClickAndPageRejectionStillPause(){
        for(String reason:new String[]{"BUTTON_ACTION_REJECTED","CONFIRM_ACTION_REJECTED","PENDING_CLICK","TIMEOUT_RECORD_NOT_UPDATED","SITE_ERROR_SCREEN","CHROME_AUTH_REQUIRED","TIMEOUT_DATE_MISMATCH","UNKNOWN"})
            assertTrue(AttendanceAttemptPolicy.pauseAfterFailure(false,reason));
    }
    @Test public void recoveryReservesAFullResultWindowWithinTheTotalLimit(){
        assertEquals(35_000,AttendanceAttemptPolicy.MAX_ATTEMPT_MS-AttendanceAttemptPolicy.RECOVERY_PAGE_WAIT_MS);
        assertEquals(105_000,AttendanceAttemptPolicy.afterAction(0,70_000));
        assertEquals(AttendanceAttemptPolicy.MAX_ATTEMPT_MS,AttendanceAttemptPolicy.afterAction(0,100_000));
    }
}
