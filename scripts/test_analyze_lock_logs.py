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

if __name__ == "__main__":
    unittest.main()
