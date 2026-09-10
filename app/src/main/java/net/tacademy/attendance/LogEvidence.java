package net.tacademy.attendance;

import java.util.regex.*;

/** Evidence comes only from app events, never the user's verdict or memo. */
public final class LogEvidence {
    public boolean screenOffAtRun, automaticEntry, manualEntry, immediateEntry, chromeObserved,
        credentialNeeded, dismissRequested, dismissSucceeded, attendanceConfirmed, cancelled;
    public Integer httpsStatus;
    public static LogEvidence read(String events){
        LogEvidence e=new LogEvidence();
        for(String raw:events.split("\n")){
            String s=raw.replaceFirst("^\\d{2}:\\d{2}:\\d{2}\\s+","");
            if(s.startsWith("실행 당시:")){e.screenOffAtRun=s.contains("화면 꺼짐");e.credentialNeeded=s.contains("인증 필요 예");}
            if(s.startsWith("테스트 화면 진입 · 예약에서 자동 진입"))e.automaticEntry=true;
            if(s.startsWith("테스트 화면 진입 · 알림에서 수동 진입"))e.manualEntry=true;
            if(s.startsWith("테스트 화면 진입 · 앱에서 즉시 실행"))e.immediateEntry=true;
            if(s.startsWith("Chrome 출결 주소 표시 확인 ·"))e.chromeObserved=true;
            if(s.startsWith("시스템 잠금 해제 요청 ·"))e.dismissRequested=true;
            if(s.equals("시스템 잠금 해제 성공"))e.dismissSucceeded=true;
            if(s.startsWith("사이트 기록으로 실제 출결 완료 확인"))e.attendanceConfirmed=true;
            if(s.startsWith("사용자가 테스트 취소"))e.cancelled=true;
            Matcher code=Pattern.compile("^HTTPS 응답 (\\d{3}) ·").matcher(s);
            if(code.find())e.httpsStatus=Integer.parseInt(code.group(1));
        }
        return e;
    }
    public static String redact(String value){
        return value.replaceAll("(?i)(github_pat_[a-z0-9_]+|gh[pousr]_[a-z0-9_]+)","[TOKEN REMOVED]")
            .replaceAll("(?im)(authorization|cookie|set-cookie)\\s*[:=][^\\r\\n]*","$1: [REMOVED]")
            .replaceAll("(?i)bearer\\s+[a-z0-9._~+/=-]+","Bearer [REMOVED]")
            .replaceAll("(?i)(https?://[^\\s?]+)\\?[^\\s]*","$1?[REMOVED]");
    }
}
