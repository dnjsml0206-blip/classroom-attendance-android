package net.tacademy.attendance;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SetupGuidePolicyTest {
    private static SetupGuidePolicy.Snapshot snapshot(boolean[] flags) {
        return new SetupGuidePolicy.Snapshot(flags[0], flags[1], flags[2], flags[3], flags[4],
            flags[5], flags[6], flags[7], flags[8], flags[9], flags[10], flags[11], flags[12]);
    }
    private static boolean[] readyFlags() {
        boolean[] values = new boolean[13];
        java.util.Arrays.fill(values, true);
        return values;
    }
    private static SetupGuidePolicy.Snapshot without(int index) {
        boolean[] values = readyFlags();
        values[index] = false;
        return snapshot(values);
    }

    @Test public void newInstallStartsAtFirstStepWithoutImplicitConfirmations() {
        SetupGuidePolicy.Snapshot s = snapshot(new boolean[13]);
        assertArrayEquals(new boolean[]{false, false, false, false, false}, SetupGuidePolicy.completed(s));
        assertEquals(0, SetupGuidePolicy.completedCount(s));
        assertEquals(0, SetupGuidePolicy.nextStep(s));
        assertFalse(SetupGuidePolicy.readyToEnable(s));
    }

    @Test public void everyRequiredPrerequisitePreventsActivationReadiness() {
        for (int missing = 0; missing <= 10; missing++) {
            SetupGuidePolicy.Snapshot s = without(missing);
            assertFalse("Missing prerequisite " + missing, SetupGuidePolicy.readyToEnable(s));
            assertFalse("Reservation cannot hide missing prerequisite " + missing,
                SetupGuidePolicy.completed(s)[4]);
        }
    }

    @Test public void eachMissingPrerequisiteSelectsItsOwnFirstIncompleteStep() {
        int[] expected = {0, 0, 0, 1, 2, 2, 2, 2, 2, 3, 3, 4, 4};
        for (int missing = 0; missing < expected.length; missing++) {
            SetupGuidePolicy.Snapshot s = without(missing);
            assertEquals("Missing field " + missing, expected[missing], SetupGuidePolicy.nextStep(s));
        }
    }

    @Test public void readyConfigurationDoesNotRequireReservationsAlreadyEnabled() {
        boolean[] values = readyFlags();
        values[11] = false;
        values[12] = false;
        SetupGuidePolicy.Snapshot s = snapshot(values);
        assertTrue(SetupGuidePolicy.readyToEnable(s));
        assertEquals(4, SetupGuidePolicy.completedCount(s));
        assertEquals(4, SetupGuidePolicy.nextStep(s));
        assertFalse(SetupGuidePolicy.completed(s)[4]);
    }

    @Test public void bothReservationAndAutomaticClickMustBeOnToFinish() {
        for (int missing : new int[]{11, 12}) {
            assertTrue(SetupGuidePolicy.readyToEnable(without(missing)));
            assertFalse(SetupGuidePolicy.completed(without(missing))[4]);
            assertEquals(4, SetupGuidePolicy.completedCount(without(missing)));
        }
    }

    @Test public void futureReservationsCanBePreparedAwayFromTheClassroom() {
        // Snapshot intentionally contains registered Wi-Fi and schedule validity only: no live
        // connection, current attendance window, classroom coordinates or battery-exemption gate.
        boolean[] values = readyFlags();
        values[11] = false;
        values[12] = false;
        assertTrue(SetupGuidePolicy.readyToEnable(snapshot(values)));
        assertFalse(SetupGuidePolicy.readyToEnable(without(1)));
        assertFalse(SetupGuidePolicy.readyToEnable(without(2)));
    }

    @Test public void allCompletedKeepsFinalSummaryStepSelected() {
        SetupGuidePolicy.Snapshot s = snapshot(readyFlags());
        assertArrayEquals(new boolean[]{true, true, true, true, true}, SetupGuidePolicy.completed(s));
        assertTrue(SetupGuidePolicy.readyToEnable(s));
        assertEquals(5, SetupGuidePolicy.completedCount(s));
        assertEquals(4, SetupGuidePolicy.nextStep(s));
    }

    @Test public void revokingLocationAfterCompletionReopensPermissionsAndFinalStep() {
        SetupGuidePolicy.Snapshot s = without(5);
        assertArrayEquals(new boolean[]{true, true, false, true, false}, SetupGuidePolicy.completed(s));
        assertEquals(3, SetupGuidePolicy.completedCount(s));
        assertEquals(2, SetupGuidePolicy.nextStep(s));
    }

    @Test public void callerCannotChangeLaterStatusByMutatingReturnedArray() {
        SetupGuidePolicy.Snapshot s = snapshot(readyFlags());
        boolean[] first = SetupGuidePolicy.completed(s);
        first[0] = false;
        assertTrue(SetupGuidePolicy.completed(s)[0]);
        assertEquals(5, SetupGuidePolicy.completedCount(s));
    }

    @Test public void quickFlowSkipsAllSatisfiedPermissionsAndStopsAtReservation() {
        assertEquals(SetupGuidePolicy.QuickAction.RESERVATION,
            SetupGuidePolicy.nextQuick(snapshot(readyFlags()), true));
        assertFalse(SetupGuidePolicy.canAutoOpen(SetupGuidePolicy.QuickAction.RESERVATION));
    }
    @Test public void quickFlowFindsEachMissingRequirementWithoutManualNextSteps() {
        SetupGuidePolicy.QuickAction[] expected = {
            SetupGuidePolicy.QuickAction.CHROME, SetupGuidePolicy.QuickAction.WIFI,
            SetupGuidePolicy.QuickAction.SCHEDULE, SetupGuidePolicy.QuickAction.ACCESSIBILITY,
            SetupGuidePolicy.QuickAction.PRECISE_LOCATION, SetupGuidePolicy.QuickAction.BACKGROUND_LOCATION,
            SetupGuidePolicy.QuickAction.LOCATION_SERVICE, SetupGuidePolicy.QuickAction.ALARMS,
            SetupGuidePolicy.QuickAction.NOTIFICATIONS, SetupGuidePolicy.QuickAction.SECURE_LOCK,
            SetupGuidePolicy.QuickAction.EXTEND
        };
        for (int missing = 0; missing < expected.length; missing++)
            assertEquals("Missing requirement " + missing, expected[missing],
                SetupGuidePolicy.nextQuick(without(missing), true));
    }
    @Test public void appTrustModeNeedsItsOwnExplicitChoiceWithoutReplacingExistingSelection() {
        assertEquals(SetupGuidePolicy.QuickAction.TRUST_MODE,
            SetupGuidePolicy.nextQuick(snapshot(readyFlags()), false));
        assertFalse(SetupGuidePolicy.canAutoOpen(SetupGuidePolicy.QuickAction.TRUST_MODE));
    }
    @Test public void manualAuthenticationConfirmationsNeverAdvanceFromMenuReturnAlone() {
        for (SetupGuidePolicy.QuickAction a : new SetupGuidePolicy.QuickAction[]{
                SetupGuidePolicy.QuickAction.CHROME, SetupGuidePolicy.QuickAction.EXTEND,
                SetupGuidePolicy.QuickAction.TRUST_MODE, SetupGuidePolicy.QuickAction.RESERVATION}) {
            assertFalse(SetupGuidePolicy.canAutoAdvance(a, snapshot(readyFlags()), true));
            assertFalse(SetupGuidePolicy.canAutoOpen(a));
        }
    }
    @Test public void quickFlowMustBeStartedBeforeAnyAutomaticContinuation() {
        for (SetupGuidePolicy.QuickAction a : SetupGuidePolicy.QuickAction.values())
            assertFalse(SetupGuidePolicy.canAutoAdvance(a, snapshot(readyFlags()), false));
        assertFalse(SetupGuidePolicy.canAutoAdvance(null, snapshot(readyFlags()), true));
    }
    @Test public void rejectedPermissionsRemainPendingInsteadOfReopeningSettingsInLoop() {
        assertFalse(SetupGuidePolicy.canAutoAdvance(SetupGuidePolicy.QuickAction.PRECISE_LOCATION, without(4), true));
        assertFalse(SetupGuidePolicy.canAutoAdvance(SetupGuidePolicy.QuickAction.BACKGROUND_LOCATION, without(5), true));
        assertFalse(SetupGuidePolicy.canAutoAdvance(SetupGuidePolicy.QuickAction.ACCESSIBILITY, without(3), true));
        assertFalse(SetupGuidePolicy.canAutoAdvance(SetupGuidePolicy.QuickAction.NOTIFICATIONS, without(8), true));
    }
    @Test public void measuredGrantAdvancesToTheNextRequirement() {
        boolean[] flags = readyFlags(); flags[4]=false; flags[5]=false;
        assertEquals(SetupGuidePolicy.QuickAction.PRECISE_LOCATION, SetupGuidePolicy.nextQuick(snapshot(flags),true));
        flags[4]=true;
        assertTrue(SetupGuidePolicy.canAutoAdvance(SetupGuidePolicy.QuickAction.PRECISE_LOCATION,snapshot(flags),true));
        assertEquals(SetupGuidePolicy.QuickAction.BACKGROUND_LOCATION, SetupGuidePolicy.nextQuick(snapshot(flags),true));
    }
    @Test public void revocationDuringQuickFlowRoutesToTheNewMissingPermission() {
        SetupGuidePolicy.Snapshot revoked = without(4);
        assertEquals(SetupGuidePolicy.QuickAction.PRECISE_LOCATION,SetupGuidePolicy.nextQuick(revoked,true));
        assertFalse(SetupGuidePolicy.readyToEnable(revoked));
    }

    @Test public void deniedBundledNotificationRequestWaitsForExplicitRetry() {
        boolean[] flags=readyFlags();flags[8]=false;
        SetupGuidePolicy.Snapshot afterLocationGrant=snapshot(flags);
        assertTrue(SetupGuidePolicy.canAutoAdvance(SetupGuidePolicy.QuickAction.PRECISE_LOCATION,afterLocationGrant,true));
        SetupGuidePolicy.QuickAction next=SetupGuidePolicy.nextQuick(afterLocationGrant,true);
        assertEquals(SetupGuidePolicy.QuickAction.NOTIFICATIONS,next);
        assertFalse(SetupGuidePolicy.canLaunchQuick(next,true,true,false));
        assertTrue(SetupGuidePolicy.canLaunchQuick(next,false,true,false));
    }
    @Test public void firstNotificationRequestCanStillFollowQuickStartAutomatically() {
        assertTrue(SetupGuidePolicy.canLaunchQuick(SetupGuidePolicy.QuickAction.NOTIFICATIONS,true,false,false));
    }
    @Test public void grantedRuntimePermissionAllowsChannelGuidanceWithoutAnotherRuntimePrompt() {
        assertTrue(SetupGuidePolicy.canLaunchQuick(SetupGuidePolicy.QuickAction.NOTIFICATIONS,true,true,true));
    }
    @Test public void notificationRetryGuardDoesNotAlterOtherPermissionsOrManualConfirmationStops() {
        assertTrue(SetupGuidePolicy.canLaunchQuick(SetupGuidePolicy.QuickAction.BACKGROUND_LOCATION,true,true,false));
        assertFalse(SetupGuidePolicy.canLaunchQuick(SetupGuidePolicy.QuickAction.RESERVATION,true,false,true));
        assertFalse(SetupGuidePolicy.canLaunchQuick(SetupGuidePolicy.QuickAction.EXTEND,true,false,true));
        assertFalse(SetupGuidePolicy.canLaunchQuick(SetupGuidePolicy.QuickAction.CHROME,true,false,true));
    }

    @Test public void optionalExtendDoesNotBlockReadyOrQuickSetup() {
        var s=new SetupGuidePolicy.Snapshot(true,true,true,true,true,true,true,true,true,false,false,false,false,false);
        assertTrue(SetupGuidePolicy.readyToEnable(s));
        assertEquals(SetupGuidePolicy.QuickAction.RESERVATION,SetupGuidePolicy.nextQuick(s,false));
    }
    @Test public void selectingExtendStillRequiresSecureLockAndConfirmation() {
        var s=new SetupGuidePolicy.Snapshot(true,true,true,true,true,true,true,true,true,false,false,false,false,true);
        assertFalse(SetupGuidePolicy.readyToEnable(s));
        assertEquals(SetupGuidePolicy.QuickAction.SECURE_LOCK,SetupGuidePolicy.nextQuick(s,false));
    }
}
