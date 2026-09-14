package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;

public final class AccessPolicyTest {
    @Test public void everyKindSharesCooldownAndDailyCap(){
        for(AccessPolicy.Kind kind:AccessPolicy.Kind.values()){
            assertEquals(AccessPolicy.Reason.COOLDOWN,AccessPolicy.decide(kind,1,0,false,1,0).reason());
            assertTrue(AccessPolicy.decide(kind,1,0,false,0,0).allowed());
            assertEquals(AccessPolicy.Reason.DAILY_LIMIT,AccessPolicy.decide(kind,20,0,false,0,0).reason());
        }
    }
    @Test public void manualAndTestNavigationOpenAtThreeSeconds(){
        assertEquals(3000,AccessPolicy.GLOBAL_GAP_MS);
        long before=AccessPolicy.remaining(1002999,102999,3,1000000,100000,3,AccessPolicy.GLOBAL_GAP_MS);
        long at=AccessPolicy.remaining(1003000,103000,3,1000000,100000,3,AccessPolicy.GLOBAL_GAP_MS);
        for(AccessPolicy.Kind kind:new AccessPolicy.Kind[]{AccessPolicy.Kind.MANUAL,AccessPolicy.Kind.TEST}){
            assertEquals(AccessPolicy.Reason.COOLDOWN,AccessPolicy.decide(kind,1,1,false,before,0).reason());
            assertTrue(AccessPolicy.decide(kind,1,1,false,at,0).allowed());
        }
        assertEquals(AccessPolicy.Reason.COOLDOWN,AccessPolicy.decide(AccessPolicy.Kind.AUTOMATIC,1,1,false,at,117000).reason());
    }
    @Test public void automaticRetriesBackOffAndStopAtThree(){
        assertEquals(0,AccessPolicy.retryGap(0));assertEquals(120000,AccessPolicy.retryGap(1));assertEquals(300000,AccessPolicy.retryGap(2));
        assertEquals(AccessPolicy.Reason.COOLDOWN,AccessPolicy.decide(AccessPolicy.Kind.AUTOMATIC,2,1,false,0,1000).reason());
        assertEquals(AccessPolicy.Reason.AUTOMATIC_LIMIT,AccessPolicy.decide(AccessPolicy.Kind.AUTOMATIC,3,3,false,0,0).reason());
        assertTrue(AccessPolicy.decide(AccessPolicy.Kind.MANUAL,3,3,false,0,1000).allowed());
    }
    @Test public void uncertainResultStopsOnlyAutomaticWork(){
        assertEquals(AccessPolicy.Reason.PAUSED,AccessPolicy.decide(AccessPolicy.Kind.AUTOMATIC,1,1,true,0,0).reason());
        assertTrue(AccessPolicy.decide(AccessPolicy.Kind.MANUAL,1,1,true,0,0).allowed());
    }
    @Test public void clockChangesCannotSkipAnIntervalWithinOneBoot(){
        assertEquals(2000,AccessPolicy.remaining(999999999,101000,3,1000000,100000,3,AccessPolicy.GLOBAL_GAP_MS));
        assertEquals(2000,AccessPolicy.remaining(1,101000,3,1000000,100000,3,AccessPolicy.GLOBAL_GAP_MS));
        assertEquals(0,AccessPolicy.remaining(1000001,103000,3,1000000,100000,3,AccessPolicy.GLOBAL_GAP_MS));
    }
    @Test public void rebootUsesPersistedWallClockAndRollbackDoesNotBypass(){
        assertEquals(2000,AccessPolicy.remaining(1001000,1000,4,1000000,100000,3,AccessPolicy.GLOBAL_GAP_MS));
        assertEquals(4000,AccessPolicy.remaining(999000,1000,4,1000000,100000,3,AccessPolicy.GLOBAL_GAP_MS));
        assertEquals(0,AccessPolicy.remaining(1000000,1000,4,0,0,-1,AccessPolicy.GLOBAL_GAP_MS));
    }
    @Test public void serverErrorRecognitionDoesNotMatchAttendanceNumbers(){
        assertTrue(ChromePage.serverUnavailable("HTTP ERROR 503"));assertTrue(ChromePage.serverUnavailable("ERR_CONNECTION_TIMED_OUT"));
        assertFalse(ChromePage.serverUnavailable("총출석시간 503시간 입실시간 : 08:50 퇴실"));
    }
}
