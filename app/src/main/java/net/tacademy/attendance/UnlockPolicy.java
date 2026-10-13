package net.tacademy.attendance;

/** Pure rules for one bounded wake attempt per Korean day and attendance phase. */
final class UnlockPolicy {
    static final long VISIBLE_TIMEOUT_MS=8_000, SESSION_TIMEOUT_MS=120_000, TRUST_SETTLE_MS=8_000;
    enum Mode {
        WAKE("화면 깨우기"), TRUST("Extend Unlock · 신뢰 상태"), AUTH("시스템 인증 요청");
        final String label; Mode(String label){this.label=label;}
    }
    static Mode automaticMode(boolean extend) { return extend?Mode.TRUST:Mode.WAKE; }
    static Mode mode(String value){try{return Mode.valueOf(value);}catch(RuntimeException e){return Mode.TRUST;}}
    static boolean canAttempt(String today,String previous){return today.compareTo(previous)>0;}
    static boolean live(String expected,String actual,boolean active,int boot,int savedBoot,long now,long started){
        return active&&expected!=null&&!expected.isEmpty()&&expected.equals(actual)&&boot>=0&&boot==savedBoot&&now>=started&&now-started<SESSION_TIMEOUT_MS;
    }
    static boolean waitingForTrust(Mode mode, boolean interactive, boolean locked, long visibleAge) {
        return mode==Mode.TRUST && interactive && locked && visibleAge>=0 && visibleAge<TRUST_SETTLE_MS;
    }
    /** Readiness bypasses only local polling delay; navigation limits remain enforced. */
    static boolean expedite(boolean wasWifiAllowed, boolean wifiAllowed, boolean userPresent,
                            boolean interactive, boolean keyguard, boolean locked) {
        return wifiAllowed && (!wasWifiAllowed || (userPresent && interactive && !keyguard && !locked));
    }
    static LockTestPolicy.Step next(Mode mode,boolean interactive,boolean keyguard,boolean locked){
        return LockTestPolicy.next(mode==Mode.WAKE?LockTestPolicy.Mode.WAKE:mode==Mode.TRUST?LockTestPolicy.Mode.EXTEND:LockTestPolicy.Mode.AUTH,interactive,keyguard,locked);
    }
}
