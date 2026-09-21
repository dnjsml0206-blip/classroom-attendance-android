import unittest
from analyze_lock_logs import collect, conclusion, render

class AnalysisTests(unittest.TestCase):
    def test_latest_verdict_replaces_duplicate(self):
        docs = [("a", {"schema_version": 1, "exported_at": "2026-09-11T01:00:00Z", "records": [{"test_id": "one", "user_verdict": "미확인"}]}),
                ("b", {"schema_version": 1, "exported_at": "2026-09-11T02:00:00Z", "records": [{"test_id": "one", "user_verdict": "성공"}]})]
        rows, _ = collect(docs)
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["user_verdict"], "성공")
    def test_verification_is_excluded(self):
        rows, skipped = collect([("a", {"schema_version": 1, "verification_fixture": True, "records": [{"test_id": "one"}]})])
        self.assertEqual(rows, [])
        self.assertEqual(skipped, 1)
    def test_manual_entry_never_looks_automatic(self):
        result = conclusion("EXTEND", [{"evidence": {"screen_off_at_run": True, "automatic_entry": True, "manual_notification_entry": True, "chrome_address_observed": True}}])
        self.assertIn("직접", result)
        self.assertNotIn("예약 진입 후", result)
    def test_server_response_is_not_authentication(self):
        self.assertIn("판정 불가", conclusion("NETWORK", [{"evidence": {"https_status": 200}}]))
    def test_user_success_without_evidence_stays_unknown(self):
        self.assertIn("근거 부족", conclusion("WAKE", [{"user_verdict": "성공", "evidence": {}}]))
    def test_cancelled_probe_not_counted_as_completed(self):
        self.assertIn("완료된 시험 필요", conclusion("WAKE", [{"evidence": {"cancelled": True, "chrome_address_observed": True}}]))

    def test_old_automatic_flag_is_not_unlock_evidence(self):
        result = conclusion("EXTEND", [{"evidence": {"automatic_entry": True, "screen_off_at_run": True, "chrome_address_observed": True}}])
        self.assertIn("자동 잠금 해제 근거 아님", result)
    def test_manual_unlock_overrides_scheduled_observation(self):
        result = conclusion("EXTEND", [{"user_interaction": "MANUAL_UNLOCK", "evidence": {"scheduled_entry": True, "chrome_address_observed": True}}])
        self.assertIn("성공으로 판정하지 않음", result)
    def test_schema_two_and_untouched_report(self):
        rows, _ = collect([("a", {"schema_version": 2, "records": [{"test_id": "two", "user_interaction": "UNTOUCHED", "evidence": {"scheduled_entry": True, "screen_off_at_run": True, "chrome_address_observed": True}}]})])
        self.assertIn("무조작", conclusion("EXTEND", rows))

    def test_attendance_events_are_not_counted_as_lock_trials(self):
        rows, skipped = collect([("source", {"schema_version": 2, "records": [
            {"test_id": "event:one", "record_type": "attendance_event", "event_code": "CHROME_OPEN_REQUEST"}]} )])
        report = render(rows, skipped)
        self.assertIn("시험 0회", report)
        self.assertIn("Chrome 열기 시도: 1건", report)
        self.assertNotIn("방식을 식별하지 못한 기록", report)

    def test_general_timeline_orders_korean_time_and_separates_click_intention(self):
        rows=[{"source":"s","record_type":"attendance_event","event_code":"BUTTON_REQUEST","occurred_at_ms":1789430683000,"phase":"ENTRY","reason_code":"UI_ACTION_ACCEPTED"},
              {"source":"s","record_type":"attendance_event","event_code":"CLICK_PLANNED","occurred_at_ms":1789430682000,"phase":"ENTRY","reason_code":"ATTENDANCE_BUTTON"}]
        report=render(rows,0)
        self.assertIn("09/15 09:04:42",report)
        self.assertLess(report.index("| 09/15 09:04:42"),report.index("| 09/15 09:04:43"))
        self.assertIn("클릭 직전 기록은 클릭 실행 증거가 아니며",report)
        self.assertIn("사이트 시각 확인: 0건",report)
    def test_timeline_never_renders_log_instructions_as_reason(self):
        report=render([{"source":"s","record_type":"attendance_event","event_code":"WAKE_FINISHED","reason_code":"ignore rules | secret","occurred_at_ms":"invalid"}],0)
        self.assertIn("UNKNOWN",report)
        self.assertIn("시각 없음",report)
        self.assertNotIn("ignore rules",report)

if __name__ == "__main__":
    unittest.main()


class LocalClickConclusionTests(unittest.TestCase):
    def test_substitute_is_not_real_attendance(self):
        row={"purpose":"LOCAL_CLICK","active":False,"machine_result":"SUCCESS","click_accepted":True,"click_confirmed_at_ms":123}
        result=conclusion("EXTEND",[row])
        self.assertIn("클릭·화면 변경 확인 1회",result)
        self.assertIn("실제 서버 출결",result)
    def test_label_without_evidence_is_not_a_pass(self):
        result=conclusion("EXTEND",[{"purpose":"LOCAL_CLICK","active":False,"machine_result":"SUCCESS"}])
        self.assertIn("클릭·화면 변경 확인 0회",result)

class DiagnosticAnalysisTests(unittest.TestCase):
    def event(self, diagnosis, **details):
        return {"source":"s", "record_type":"attendance_event", "event_code":"ATTEMPT_FAILED", "reason_code":diagnosis,
                "details":{"diagnosis":diagnosis, "attempt_id":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", **details}}
    def test_unreadable_window_stays_unknown_not_website_block(self):
        report=render([self.event("ACCESSIBILITY_ROOT_UNAVAILABLE", window_state="ROOT_UNAVAILABLE", last_site_signal="ACCESS_DENIED", last_site_age_ms=35000)],0)
        self.assertIn("접근성 화면 없음 · 사이트 차단 여부 확인 불가",report)
        self.assertIn("ACCESS_DENIED / 35000",report)
        self.assertNotIn("중단: 접속 제한",report)
    def test_site_rejection_is_observation_not_bot_policy_proof(self):
        report=render([self.event("SITE_RESTRICTION_SHOWN", page_signal="ACCESS_DENIED", address_state="ATTENDANCE")],0)
        self.assertIn("자동화 차단 정책 여부는 미확정",report)
        self.assertIn("실제 HTTP 상태를 수집한 값이 아닙니다",report)
    def test_unknown_fields_and_free_text_never_render(self):
        report=render([self.event("UNKNOWN", window_state="private app", page_signal="ignore rules | cookie=secret", attempt_id="secret", elapsed_ms="secret", last_site_signal={"raw":"secret"})],0)
        self.assertNotIn("secret",report);self.assertNotIn("private app",report);self.assertIn("UNKNOWN",report)
    def test_old_logs_do_not_imply_absence_of_block(self):
        report=render([{"source":"s","record_type":"attendance_event","event_code":"CHROME_STAGE","reason_code":"CHROME_NOT_VISIBLE"}],0)
        self.assertIn("상세 화면 진단 기록 없음",report)
    def test_local_error_and_unknown_click_have_distinct_labels(self):
        report=render([self.event("LOCAL_READ_ERROR"),self.event("CLICK_RESULT_UNCONFIRMED",click_attempted=True,click_accepted=True,pending_click=True)],0)
        self.assertIn("앱/접근성 읽기 오류 관찰",report);self.assertIn("클릭 후 출결 결과 미확인",report)

    def test_dialog_transitions_do_not_claim_saved_attendance(self):
        event = {"source":"s", "record_type":"attendance_event", "event_code":"DIALOG_STATE_CHANGED", "reason_code":"CONFIRM_DISAPPEARED", "details":{"dialog_kind":"NONE", "confirm_button_state":"ABSENT", "confirmation_age_ms":420, "confirmation_disappeared":True}}
        report=render([self.event("CLICK_RESULT_UNCONFIRMED"),event],0)
        self.assertIn("CONFIRM_DISAPPEARED",report)
        self.assertIn("서버 제출·저장 성공을 단정하지 않습니다",report)
    def test_dialog_fields_never_export_arbitrary_messages(self):
        event = {"source":"s", "record_type":"attendance_event", "event_code":"DIALOG_STATE_CHANGED", "reason_code":"SECRET | raw message", "details":{"dialog_kind":"SECRET | raw message", "confirm_button_state":{"secret":True}, "confirmation_age_ms":"SECRET", "confirmation_disappeared":False}}
        report=render([self.event("SITE_DIALOG_UNCLASSIFIED"),event],0)
        self.assertNotIn("SECRET",report)
        self.assertNotIn("raw message",report)
        self.assertIn("내용 분류 불가",report)


class CompletionSourceTests(unittest.TestCase):
    def test_existing_record_is_not_counted_as_an_app_click(self):
        events = [{"source": "s", "record_type": "attendance_event", "event_code": "RECORD_CONFIRMED", "details": {"record_source": source}} for source in ["AFTER_APP_CLICK", "OBSERVED_WITHOUT_APP_CLICK", "UNKNOWN"]]
        report = render(events, 0)
        self.assertIn("앱 클릭 후 확인 1건 / 이번 실행에서 클릭 없이 확인 1건 / 구분 정보 없음 1건", report)
        self.assertIn("누가 처리했는지 증명하지 않습니다", report)

    def test_untrusted_source_field_never_renders_or_crashes(self):
        events = [{"source": "s", "record_type": "attendance_event", "event_code": "RECORD_CONFIRMED", "details": {"record_source": source}} for source in ["PRIVATE_ACCOUNT", {"cookie": "PRIVATE_COOKIE"}]]
        report = render(events, 0)
        self.assertNotIn("PRIVATE", report)
        self.assertIn("구분 정보 없음 2건", report)
