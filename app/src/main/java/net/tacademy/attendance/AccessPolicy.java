package net.tacademy.attendance;

/** Limits app-initiated Chrome navigations, not individual HTTP requests. */
final class AccessPolicy {
    static final int DAILY_LIMIT=20, AUTOMATIC_LIMIT=3;
    static final long GLOBAL_GAP_MS=3_000;
    enum Kind { AUTOMATIC, MANUAL, TEST }
    enum Reason { ALLOWED, COOLDOWN, DAILY_LIMIT, AUTOMATIC_LIMIT, PAUSED, STORAGE_ERROR }
    record Decision(Reason reason,long waitMillis){boolean allowed(){return reason==Reason.ALLOWED;}}
    static long retryGap(int previousAttempts){return previousAttempts==0?0:previousAttempts==1?120_000:300_000;}
    static Decision decide(Kind kind,int total,int phaseCount,boolean paused,long commonWait,long automaticWait){
        if(total>=DAILY_LIMIT)return new Decision(Reason.DAILY_LIMIT,0);
        if(kind==Kind.AUTOMATIC&&paused)return new Decision(Reason.PAUSED,0);
        if(kind==Kind.AUTOMATIC&&phaseCount>=AUTOMATIC_LIMIT)return new Decision(Reason.AUTOMATIC_LIMIT,0);
        long wait=Math.max(commonWait,kind==Kind.AUTOMATIC?automaticWait:0);
        return wait>0?new Decision(Reason.COOLDOWN,wait):new Decision(Reason.ALLOWED,0);
    }
    static long remaining(long nowWall,long nowElapsed,int boot,long thenWall,long thenElapsed,int thenBoot,long gap){
        if(thenWall<=0)return 0;
        long since=boot>=0&&boot==thenBoot&&nowElapsed>=thenElapsed?nowElapsed-thenElapsed:nowWall-thenWall;
        return Math.max(0,gap-since);
    }
}
