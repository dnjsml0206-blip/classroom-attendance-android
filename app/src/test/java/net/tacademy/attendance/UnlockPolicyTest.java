package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;
public final class UnlockPolicyTest {
    @Test public void missingOrInvalidModeStaysNotificationOnly(){assertEquals(UnlockPolicy.Mode.NOTICE,UnlockPolicy.mode("unknown"));assertEquals(UnlockPolicy.Mode.NOTICE,UnlockPolicy.mode(null));}
    @Test public void wakeWaitsForScreenAndNeverDismissesASecureLock(){
        assertEquals(LockTestPolicy.Step.WAIT_SCREEN,UnlockPolicy.next(UnlockPolicy.Mode.WAKE,false,false,false));
        assertEquals(LockTestPolicy.Step.NEED_AUTH,UnlockPolicy.next(UnlockPolicy.Mode.WAKE,true,true,true));
        assertEquals(LockTestPolicy.Step.OPEN_CHROME,UnlockPolicy.next(UnlockPolicy.Mode.WAKE,true,false,false));
    }
    @Test public void trustedStateStillRequiresSystemDismissal(){
        assertEquals(LockTestPolicy.Step.REQUEST_DISMISS,UnlockPolicy.next(UnlockPolicy.Mode.TRUST,true,true,false));
        assertEquals(LockTestPolicy.Step.NEED_AUTH,UnlockPolicy.next(UnlockPolicy.Mode.TRUST,true,true,true));
        assertEquals(LockTestPolicy.Step.NEED_AUTH,UnlockPolicy.next(UnlockPolicy.Mode.TRUST,true,false,true));
    }
    @Test public void credentialsOnlyUseSystemPrompt(){assertEquals(LockTestPolicy.Step.REQUEST_DISMISS,UnlockPolicy.next(UnlockPolicy.Mode.AUTH,true,true,true));}
    @Test public void noModeCanOpenChromeWhileScreenOrAuthenticationIsBlocked(){
        for(UnlockPolicy.Mode mode:UnlockPolicy.Mode.values())for(boolean screen:new boolean[]{false,true})for(boolean keyguard:new boolean[]{false,true})for(boolean locked:new boolean[]{false,true}){
            if(UnlockPolicy.next(mode,screen,keyguard,locked)==LockTestPolicy.Step.OPEN_CHROME)assertTrue(screen&&!keyguard&&!locked);
        }
    }
    @Test public void oneAttemptSurvivesTogglesAndClockRollback(){assertTrue(UnlockPolicy.canAttempt("2026-09-15",""));assertFalse(UnlockPolicy.canAttempt("2026-09-15","2026-09-15"));assertFalse(UnlockPolicy.canAttempt("2026-09-14","2026-09-15"));assertTrue(UnlockPolicy.canAttempt("2026-09-16","2026-09-15"));}
    @Test public void sessionEndsAtExactDeadlineAndCannotReplayAcrossBoots(){
        assertTrue(UnlockPolicy.live("a","a",true,2,2,119999,0));assertFalse(UnlockPolicy.live("a","a",true,2,2,120000,0));
        assertFalse(UnlockPolicy.live("a","a",true,3,2,1,0));assertFalse(UnlockPolicy.live("a","a",true,-1,-1,1,0));assertFalse(UnlockPolicy.live("a","a",true,2,2,0,1));
        assertFalse(UnlockPolicy.live("old","new",true,2,2,1,0));assertFalse(UnlockPolicy.live("a","a",false,2,2,1,0));
    }
    @Test public void pendingClickKeepsDayAndPhaseWithoutInventingSuccess(){assertEquals("2026-09-15",PendingClick.parse("2026-09-15:ENTRY").day());assertEquals(Policy.Phase.CHECKOUT,PendingClick.parse("2026-09-14:CHECKOUT").phase());}
    @Test public void malformedPendingMarkersStayUnknown(){for(String value:new String[]{"","2026-02-30:ENTRY","2026-09-15:IDLE","2026-09-15:ENTRY:extra","arbitrary text"})assertNull(PendingClick.parse(value));}
}
