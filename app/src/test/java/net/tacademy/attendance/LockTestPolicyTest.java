package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;
import static net.tacademy.attendance.LockTestPolicy.*;

public final class LockTestPolicyTest {
    @Test public void wakingAloneDoesNotDismissAKeyguard(){assertEquals(Step.NEED_AUTH,next(Mode.WAKE,true,true,false));}
    @Test public void displayMustBeAwakeBeforeAnyChromeTransition(){for(Mode m:Mode.values())if(m!=Mode.NETWORK)assertEquals(Step.WAIT_SCREEN,next(m,false,false,false));}
    @Test public void allTrustMethodsStopWhenCredentialsAreRequired(){for(Mode m:new Mode[]{Mode.EXTEND,Mode.DEVICE,Mode.PLACE,Mode.BODY})assertEquals(Step.NEED_AUTH,next(m,true,true,true));}
    @Test public void trustedKeyguardStillNeedsASystemDismissal(){for(Mode m:new Mode[]{Mode.EXTEND,Mode.DEVICE,Mode.PLACE,Mode.BODY})assertEquals(Step.REQUEST_DISMISS,next(m,true,true,false));}
    @Test public void credentialModeRequestsSystemAuthentication(){assertEquals(Step.REQUEST_DISMISS,next(Mode.AUTH,true,true,true));assertEquals(Step.OPEN_CHROME,next(Mode.AUTH,true,false,false));}
    @Test public void alreadyUnlockedStateAllowsChrome(){for(Mode m:Mode.values())if(m!=Mode.NETWORK)assertEquals(Step.OPEN_CHROME,next(m,true,false,false));}
    @Test public void networkDiagnosticsNeverRequireWakeOrDismiss(){assertEquals(Step.NETWORK,next(Mode.NETWORK,false,true,true));assertEquals(Step.NETWORK,next(Mode.NETWORK,true,false,false));}
    @Test public void staleCancelledAndExpiredTrialsCannotRun(){
        assertTrue(live("a","a",100,100,200,true));assertFalse(live("a","a",200,100,200,true));
        assertFalse(live("a","a",99,100,200,true));assertFalse(live("a","b",150,100,200,true));
        assertFalse(live("a","a",150,100,200,false));assertFalse(live(null,"a",150,100,200,true));assertFalse(live("","",150,100,200,true));
    }
}