package net.tacademy.attendance;

import android.content.Context;
import java.time.*;
import java.util.*;

/** Checked at reservation and again before handoff; never silently enables attendance or permissions. */
final class TrialReadiness {
    static List<String> problems(Context c,LockTestPolicy.Mode mode,boolean actual,long at,boolean scheduled){
        List<String> issues=new ArrayList<>();Prefs p=new Prefs(c);
        if(mode!=LockTestPolicy.Mode.NETWORK&&!ChromeAttendanceService.available())issues.add("Chrome 접근성 연결이 필요합니다. 자동출석 탭에서 켜 주세요.");
        if(scheduled&&!AlarmScheduler.allowed(c))issues.add("알람 및 리마인더를 허용해 주세요.");
        if(scheduled&&!ScheduleController.backgroundLocationAllowed(c))issues.add("예약 중 Wi-Fi 이름 확인을 위해 위치를 항상 허용해 주세요.");
        if(actual&&mode!=LockTestPolicy.Mode.NETWORK){
            if(!p.enabled())issues.add("설정에서 출석 예약을 켜 주세요.");
            if(!p.auto())issues.add("설정에서 Chrome 입실 · 퇴실 자동 클릭을 켜 주세요.");
            if(p.enabled()&&p.phase(Instant.ofEpochMilli(at).atZone(Policy.ZONE))==Policy.Phase.IDLE)
                issues.add("시험 시각이 출석 시간 밖이거나 휴강일·출결 완료 상태입니다. 출석 시간을 확인해 주세요.");
            if(!Notices.attendanceNotificationsAllowed(c))issues.add("입실·퇴실 결과 알림을 허용해 주세요.");
        }
        if(actual&&mode!=LockTestPolicy.Mode.NETWORK){
            NetworkAccess.State wifi=NetworkAccess.inspect(c);
            // A later reservation can be made away from class; execution always rechecks the real network.
            if(at-System.currentTimeMillis()<=60000&&!wifi.allowed())issues.add(wifi.reason());
        }
        return issues;
    }
    static String status(Context c,boolean actual){
        Prefs p=new Prefs(c);NetworkAccess.State wifi=NetworkAccess.inspect(c);
        return (actual?"실제 출결 시도 · 조건을 만족하면 버튼을 누릅니다":"조회 전용 · 입실·퇴실 버튼을 누르지 않습니다")+
            "\nChrome 접근성: "+(ChromeAttendanceService.available()?"연결됨":"연결 안 됨")+
            "\n위치 항상 허용: "+(ScheduleController.backgroundLocationAllowed(c)?"허용":"필요")+
            "\n출석 예약: "+(p.enabled()?"켜짐":"꺼짐")+" · 자동 클릭: "+(p.auto()?"켜짐":"꺼짐")+
            "\n결과 알림: "+(Notices.attendanceNotificationsAllowed(c)?"허용":"필요")+
            "\nWi-Fi: "+(wifi.allowed()?"등록 Wi-Fi 확인됨":wifi.reason());
    }
}
