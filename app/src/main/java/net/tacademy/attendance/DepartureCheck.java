package net.tacademy.attendance;

/** Requires distinct, recent and accurate outside fixes before reporting a classroom departure. */
final class DepartureCheck {
    private long lastFix = -1, outsideSince = -1;
    static boolean reliable(float distance, float accuracy, int radius, long ageMillis) {
        return Float.isFinite(distance) && Float.isFinite(accuracy) && distance >= 0 && accuracy >= 0
            && radius > 0 && accuracy <= radius && ageMillis >= 0 && ageMillis <= 120_000;
    }
    boolean update(float distance, float accuracy, int radius, long fixMillis, long nowMillis) {
        if (!reliable(distance, accuracy, radius, nowMillis - fixMillis) || distance - accuracy <= radius) {
            outsideSince = -1; return false;
        }
        if (fixMillis <= lastFix) return false;
        boolean gap = lastFix >= 0 && fixMillis - lastFix > 120_000;
        lastFix = fixMillis;
        if (outsideSince < 0 || gap) { outsideSince = fixMillis; return false; }
        return fixMillis - outsideSince >= 10_000;
    }
    void reset() { lastFix = -1; outsideSince = -1; }
}
