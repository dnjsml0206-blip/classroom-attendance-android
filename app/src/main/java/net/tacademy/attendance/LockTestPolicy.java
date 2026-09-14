package net.tacademy.attendance;

/** Decisions for one-shot lock-screen trials; a trusted device still needs Keyguard dismissal. */
public final class LockTestPolicy {
    public enum Mode {
        WAKE("화면 깨우기",false), EXTEND("Extend Unlock",true), DEVICE("신뢰하는 Bluetooth 기기",true),
        PLACE("신뢰하는 장소",true), BODY("신체 활동 감지",true), AUTH("PIN·지문 등 시스템 인증",false),
        NETWORK("잠금 유지 · 기기 연결 진단",false);
        public final String label; public final boolean trustedOnly;
        Mode(String label,boolean trustedOnly){this.label=label;this.trustedOnly=trustedOnly;}
    }
    public enum Step { WAIT_SCREEN, OPEN_CHROME, NEED_AUTH, REQUEST_DISMISS, NETWORK }
    private LockTestPolicy() {}
    public static Step next(Mode mode, boolean interactive, boolean keyguard, boolean deviceLocked) {
        if(mode==Mode.NETWORK)return Step.NETWORK;
        if(!interactive)return Step.WAIT_SCREEN;
        if(!keyguard && !deviceLocked)return Step.OPEN_CHROME;
        if(mode==Mode.WAKE || (mode.trustedOnly && deviceLocked))return Step.NEED_AUTH;
        return Step.REQUEST_DISMISS;
    }
    public static boolean live(String expected,String current,long now,long created,long expires,boolean active) {
        return active && expected!=null && !expected.isEmpty() && expected.equals(current) && now>=created && now<expires;
    }
}