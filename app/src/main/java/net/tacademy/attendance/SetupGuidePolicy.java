package net.tacademy.attendance;

/** Setup evidence only; current Wi-Fi, class time and battery advice are execution-time concerns. */
final class SetupGuidePolicy {
    private SetupGuidePolicy() {}

    /** Chrome and Extend Unlock are user confirmations, not measured authentication guarantees. */
    record Snapshot(boolean chromeConfirmed, boolean wifiRegistered, boolean scheduleValid,
                    boolean accessibility, boolean preciseLocation, boolean backgroundLocation,
                    boolean locationOn, boolean alarms, boolean notifications, boolean secureLock,
                    boolean extendConfirmed, boolean enabled, boolean autoClick) {}

    static boolean[] completed(Snapshot s) {
        boolean first = s.chromeConfirmed() && s.wifiRegistered() && s.scheduleValid();
        boolean permissions = s.preciseLocation() && s.backgroundLocation() && s.locationOn()
            && s.alarms() && s.notifications();
        boolean trust = s.secureLock() && s.extendConfirmed();
        boolean ready = first && s.accessibility() && permissions && trust;
        return new boolean[]{first, s.accessibility(), permissions, trust,
            ready && s.enabled() && s.autoClick()};
    }

    static boolean readyToEnable(Snapshot s) {
        boolean[] steps = completed(s);
        return steps[0] && steps[1] && steps[2] && steps[3];
    }

    static int completedCount(Snapshot s) {
        int count = 0;
        for (boolean step : completed(s)) if (step) count++;
        return count;
    }

    static int nextStep(Snapshot s) {
        boolean[] steps = completed(s);
        for (int i = 0; i < steps.length; i++) if (!steps[i]) return i;
        return steps.length - 1;
    }

    enum QuickAction { WIFI, SCHEDULE, PRECISE_LOCATION, BACKGROUND_LOCATION, LOCATION_SERVICE,
        ACCESSIBILITY, ALARMS, NOTIFICATIONS, SECURE_LOCK, TRUST_MODE, EXTEND, CHROME, RESERVATION }

    /** Existing settings are preserved. Live classroom connection is deliberately not a setup gate. */
    static QuickAction nextQuick(Snapshot s, boolean trustMode) {
        if (!s.wifiRegistered()) return QuickAction.WIFI;
        if (!s.scheduleValid()) return QuickAction.SCHEDULE;
        if (!s.preciseLocation()) return QuickAction.PRECISE_LOCATION;
        if (!s.backgroundLocation()) return QuickAction.BACKGROUND_LOCATION;
        if (!s.locationOn()) return QuickAction.LOCATION_SERVICE;
        if (!s.accessibility()) return QuickAction.ACCESSIBILITY;
        if (!s.alarms()) return QuickAction.ALARMS;
        if (!s.notifications()) return QuickAction.NOTIFICATIONS;
        if (!s.secureLock()) return QuickAction.SECURE_LOCK;
        if (!trustMode) return QuickAction.TRUST_MODE;
        if (!s.extendConfirmed()) return QuickAction.EXTEND;
        if (!s.chromeConfirmed()) return QuickAction.CHROME;
        return QuickAction.RESERVATION;
    }

    static boolean canAutoOpen(QuickAction action) {
        return switch (action) {
            case PRECISE_LOCATION, BACKGROUND_LOCATION, LOCATION_SERVICE, ACCESSIBILITY,
                 ALARMS, NOTIFICATIONS, SECURE_LOCK -> true;
            default -> false;
        };
    }

    /** A denied bundled notification request is retried only by another explicit button press. */
    static boolean canLaunchQuick(QuickAction action, boolean automatic,
                                  boolean notificationRequested, boolean notificationGranted) {
        if (!automatic) return true;
        return canAutoOpen(action) && (action != QuickAction.NOTIFICATIONS
            || !notificationRequested || notificationGranted);
    }

    /** Returning from a menu is not success: only a newly measured satisfied condition advances. */
    static boolean canAutoAdvance(QuickAction action, Snapshot after, boolean explicitlyStarted) {
        if (!explicitlyStarted || action == null) return false;
        return switch (action) {
            case WIFI -> after.wifiRegistered();
            case SCHEDULE -> after.scheduleValid();
            case PRECISE_LOCATION -> after.preciseLocation();
            case BACKGROUND_LOCATION -> after.backgroundLocation();
            case LOCATION_SERVICE -> after.locationOn();
            case ACCESSIBILITY -> after.accessibility();
            case ALARMS -> after.alarms();
            case NOTIFICATIONS -> after.notifications();
            case SECURE_LOCK -> after.secureLock();
            default -> false;
        };
    }
}
