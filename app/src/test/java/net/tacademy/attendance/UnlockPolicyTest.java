package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;
public final class UnlockPolicyTest {
    @Test public void ordinaryAutomationNeverSelectsInteractiveAuthentication(){
        assertEquals(UnlockPolicy.Mode.WAKE,UnlockPolicy.automaticMode(false));
        assertEquals(UnlockPolicy.Mode.TRUST,UnlockPolicy.automaticMode(true));
    }

    @Test public void missingLegacyOrInvalidModeUsesTrustedAutomation(){assertEquals(UnlockPolicy.Mode.TRUST,UnlockPolicy.mode("unknown"));assertEquals(UnlockPolicy.Mode.TRUST,UnlockPolicy.mode(null));assertEquals(UnlockPolicy.Mode.TRUST,UnlockPolicy.mode("NOTICE"));}
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
    @Test public void trustGetsBoundedLocalSettleTimeInsteadOfHalfSecondFailure(){
        assertTrue(UnlockPolicy.waitingForTrust(UnlockPolicy.Mode.TRUST,true,true,515));
        assertTrue(UnlockPolicy.waitingForTrust(UnlockPolicy.Mode.TRUST,true,true,7999));
        assertFalse(UnlockPolicy.waitingForTrust(UnlockPolicy.Mode.TRUST,true,true,8000));
        assertFalse(UnlockPolicy.waitingForTrust(UnlockPolicy.Mode.TRUST,true,true,-1));
    }
    @Test public void trustBecomingReadyNeverWaitsOutTheRemainingGracePeriod(){
        assertFalse(UnlockPolicy.waitingForTrust(UnlockPolicy.Mode.TRUST,true,false,700));
        assertEquals(LockTestPolicy.Step.REQUEST_DISMISS,UnlockPolicy.next(UnlockPolicy.Mode.TRUST,true,true,false));
        assertEquals(LockTestPolicy.Step.NEED_AUTH,UnlockPolicy.next(UnlockPolicy.Mode.TRUST,true,true,true));
    }
    @Test public void graceDoesNotChangeOtherModesOrWakeTheScreenItself(){
        assertFalse(UnlockPolicy.waitingForTrust(UnlockPolicy.Mode.AUTH,true,true,100));
        assertFalse(UnlockPolicy.waitingForTrust(UnlockPolicy.Mode.WAKE,true,true,100));
        assertFalse(UnlockPolicy.waitingForTrust(UnlockPolicy.Mode.TRUST,false,true,100));
    }
    @Test public void newAllowedWifiBypassesPollingDelayButRepeatedCallbacksDoNot(){
        assertTrue(UnlockPolicy.expedite(false,true,false,false,true,true));
        assertFalse(UnlockPolicy.expedite(true,true,false,true,false,false));
        assertFalse(UnlockPolicy.expedite(false,false,true,true,false,false));
    }
    @Test public void userPresentResumesOnlyWithWifiAndRealUnlockedScreen(){
        assertTrue(UnlockPolicy.expedite(true,true,true,true,false,false));
        assertFalse(UnlockPolicy.expedite(true,true,true,true,true,false));
        assertFalse(UnlockPolicy.expedite(true,true,true,true,false,true));
        assertFalse(UnlockPolicy.expedite(true,true,true,false,false,false));
        assertFalse(UnlockPolicy.expedite(true,false,true,true,false,false));
    }
}
