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
