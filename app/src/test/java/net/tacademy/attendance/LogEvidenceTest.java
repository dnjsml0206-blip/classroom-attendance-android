package net.tacademy.attendance;
import org.junit.Test;
import static org.junit.Assert.*;
public class LogEvidenceTest {
    @Test public void requestAloneIsNotChromeSuccess(){LogEvidence e=LogEvidence.read("10:00:00  Chrome 실행 요청 전송 · 접근성이 없어 실제 화면은 직접 확인\n10:00:01  테스트 화면 자동 진입 요청");assertFalse(e.chromeObserved);assertFalse(e.automaticEntry);}
    @Test public void scheduledAndManualEntriesStaySeparate(){LogEvidence e=LogEvidence.read("10:00:00  실행 당시: 화면 꺼짐 · 인증 필요 예\n10:00:01  테스트 화면 진입 · 알림에서 수동 진입");assertTrue(e.screenOffAtRun);assertTrue(e.manualEntry);assertTrue(e.credentialNeeded);assertFalse(e.automaticEntry);}
    @Test public void immediateTestIsNotScheduled(){LogEvidence e=LogEvidence.read("10:00:00  테스트 화면 진입 · 앱에서 즉시 실행");assertTrue(e.immediateEntry);assertFalse(e.automaticEntry);}
    @Test public void addressObservationIsNotAttendance(){LogEvidence e=LogEvidence.read("10:00:00  테스트 화면 진입 · 예약에서 자동 진입\n10:00:01  Chrome 출결 주소 표시 확인 · 사이트 인증과 출결 결과는 직접 확인");assertTrue(e.automaticEntry);assertTrue(e.chromeObserved);assertFalse(e.attendanceConfirmed);}
    @Test public void siteRecordIsAttendanceEvidence(){assertTrue(LogEvidence.read("10:00:00  사이트 기록으로 실제 출결 완료 확인 · 화면 켜짐").attendanceConfirmed);}
    @Test public void networkResponseIsNotAttendance(){LogEvidence e=LogEvidence.read("10:00:00  HTTPS 응답 403 · 종료 당시: 화면 꺼짐");assertEquals(Integer.valueOf(403),e.httpsStatus);assertFalse(e.attendanceConfirmed);assertFalse(e.chromeObserved);}
    @Test public void dismissalOnlyRecordedOnCallback(){LogEvidence e=LogEvidence.read("10:00:00  시스템 잠금 해제 요청 · 인증 필요 예\n10:00:01  시스템 잠금 해제 요청 오류");assertTrue(e.dismissRequested);assertFalse(e.dismissSucceeded);assertTrue(LogEvidence.read("10:00:01  시스템 잠금 해제 성공").dismissSucceeded);}
    @Test public void redactsKnownCredentialsAndQueryValues(){String s=LogEvidence.redact("github_pat_example_fake_value ghp_fake_value\nAuthorization: Bearer abcdef\nCookie: SESSION=hidden\nhttps://example.test/a?secret=hidden");assertFalse(s.contains("fake_value"));assertFalse(s.contains("abcdef"));assertFalse(s.contains("SESSION"));assertFalse(s.contains("secret=hidden"));assertTrue(s.contains("https://example.test/a?"));}
}
