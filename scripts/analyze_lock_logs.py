"""Read uploaded lock-test reports as data; never execute log text or infer attendance from Chrome."""
import argparse
from collections import Counter
from datetime import datetime, timezone, timedelta
import re
import hashlib
import json
from pathlib import Path
import subprocess

REPO = "dnjsml0206-blip/classroom-attendance-android"
BRANCH = "codex/device-logs"
MODES = {"WAKE": "화면 깨우기", "EXTEND": "Extend Unlock", "DEVICE": "Bluetooth 기기",
         "PLACE": "신뢰 장소", "BODY": "신체 활동", "AUTH": "시스템 인증", "NETWORK": "연결 진단"}
ROOT = Path(__file__).resolve().parents[1]


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args], encoding="utf-8")


def remote_documents():
    remote = git("remote", "get-url", "origin").strip()
    if remote not in (f"https://github.com/{REPO}.git", f"git@github.com:{REPO}.git"):
        raise ValueError("Expected repository origin was not found")
    subprocess.run(["git", "-C", str(ROOT), "fetch", "origin", BRANCH], check=True)
    revision = git("rev-parse", "FETCH_HEAD").strip()
    for name in git("ls-tree", "-r", "--name-only", revision, "--", "logs/lock-tests").splitlines():
        if name.endswith(".json"):
            raw = git("show", f"{revision}:{name}")
            if len(raw.encode("utf-8")) > 256 * 1024:
                raise ValueError(f"Oversized report: {name}")
            yield f"https://github.com/{REPO}/blob/{revision}/{name}", json.loads(raw)


def collect(documents, include_verification=False):
    unique = {}
    skipped = 0
    for source, doc in sorted(documents, key=lambda pair: datetime.fromisoformat(pair[1]["exported_at"].replace("Z", "+00:00")) if pair[1].get("exported_at") else datetime.min.replace(tzinfo=timezone.utc)):
        if doc.get("schema_version") not in (1, 2):
            raise ValueError(f"Unknown schema: {source}")
        if not include_verification and (doc.get("verification_fixture") or doc.get("export_environment", {}).get("emulator")):
            skipped += 1
            continue
        for record in doc.get("records", []):
            if not include_verification and (record.get("environment") or {}).get("emulator"):
                continue
            key = record.get("test_id")
            if not key:
                # Legacy timestamps are absent; identical text can only be counted once.
                text = record.get("legacy_report", json.dumps(record, sort_keys=True, ensure_ascii=False))
                key = "legacy:" + hashlib.sha256(text.encode("utf-8")).hexdigest()
            unique[key] = {**record, "source": source}
    return list(unique.values()), skipped


def conclusion(mode, rows):
    if not rows:
        return "아직 시험 기록 없음"
    valid = [r for r in rows if not r.get("active") and not r.get("evidence", {}).get("cancelled")]
    if not valid:
        return "진행 중·취소 기록만 있음. 완료된 시험 필요"
    evidence = [r.get("evidence", {}) for r in valid]
    observed = lambda e: e.get("chrome_address_observed") or e.get("attendance_record_confirmed")
    if mode == "NETWORK":
        codes = sorted({e["https_status"] for e in evidence if e.get("https_status") is not None})
        return ("HTTPS 응답 " + ", ".join(map(str, codes)) + " 확인. 사이트 인증·출결 성공은 판정 불가") if codes else "서버 응답 근거 없음. Wi-Fi 조건·진단 오류 확인"
    manual = [r for r in valid if r.get("user_interaction") in ("MANUAL_WAKE", "MANUAL_UNLOCK", "NOTIFICATION")]
    if manual and len(manual) == len(valid):
        return "사용자가 화면 켬·잠금 해제·알림 조작을 기록함. 자동 잠금 해제 성공으로 판정하지 않음"
    if mode == "AUTH" and any(e.get("dismiss_succeeded") and observed(e) for e in evidence):
        return "시스템 해제 후 Chrome/출결 연결 근거 있음. 실제 인증 입력 여부는 메모 확인"
    if any(r.get("user_interaction") == "UNTOUCHED" and r.get("evidence", {}).get("screen_off_at_run")
           and r.get("evidence", {}).get("scheduled_entry") and observed(r.get("evidence", {}))
           and not r.get("evidence", {}).get("manual_notification_entry") for r in valid):
        return "사용자가 무조작을 기록했고 예약 후 Chrome/출결 연결이 관찰됨. 사이트 완료 기록은 별도 확인"
    if any(e.get("manual_notification_entry") and observed(e) for e in evidence):
        return "알림을 직접 누른 뒤 연결된 기록 있음. 자동 처리 성공 근거로 사용하지 않음"
    if any(e.get("immediate_entry") and observed(e) for e in evidence):
        return "지금 테스트에서 연결됨. 화면을 끈 예약 시험 필요"
    if any((r.get("execution_environment") or r.get("environment") or {}).get("accessibility_connected") is False for r in valid):
        return "Chrome 접근성 미연결 기록 있음. 화면 확인·클릭 전 필수 조건부터 확인"
    if any(e.get("scheduled_entry") or e.get("automatic_entry") for e in evidence):
        return "예약 요청에서 화면 진입 관찰. 자동 잠금 해제 근거 아님; 진입 지연과 직접 조작 여부 확인"
    if any(e.get("credential_needed_at_run") for e in evidence):
        return "실행 당시 재인증 필요. 신뢰 조건·PIN 인증 후 재시험 검토"
    return "완료 근거 부족. 자동 진입·Chrome 확인 단계와 사용자 메모 검토"


def render(rows, skipped):
    events = [r for r in rows if r.get("record_type") == "attendance_event"]
    rows = [r for r in rows if r.get("record_type") != "attendance_event"]
    lines = ["# 잠금 테스트 로그 분석", "", f"중복을 제외한 시험 {len(rows)}회. 검증용 업로드 {skipped}개 제외.", "",
             "| 방식 | 시험 | 사용자 판정: 성공 / 부분 / 실패 / 미확인 | 앱 관찰 해석 |",
             "|---|---:|---|---|"]
    for mode, label in MODES.items():
        group = [r for r in rows if r.get("mode") == mode]
        counts = Counter(r.get("user_verdict", "미확인") for r in group)
        values = " / ".join(str(counts[v]) for v in ("성공", "부분 성공", "실패", "미확인"))
        lines.append(f"| {label} | {len(group)} | {values} | {conclusion(mode, group)} |")
    if events:
        counts = Counter(r.get("event_code", "UNKNOWN") for r in events)
        labels = {"CHROME_OPEN_REQUEST": "Chrome 열기 시도", "CHROME_OPEN_DISPATCHED": "Chrome 실행 전달",
                  "ACCESS_BLOCKED": "반복 접속 차단", "BUTTON_REQUEST": "출결 버튼 동작 요청",
                  "RECORD_CONFIRMED": "사이트 시각 확인", "AUTOMATIC_PAUSED": "자동 재접속 중단",
                  "ALARM_ARMED": "다음 알람 예약", "ALARM_FIRED": "알람 수신", "ALARM_FAILED": "알람 예약 실패",
                  "MONITOR_START_REQUEST": "감시 시작 요청", "MONITOR_START_RECEIVED": "감시 서비스 진입", "MONITOR_START_FAILED": "감시 시작 실패",
                  "MONITOR_STARTED": "감시 시작", "MONITOR_STOPPED": "감시 종료", "WAITING": "조건 대기",
                  "CHROME_OPEN_DISPATCHED": "Chrome 실행 전달", "CHROME_OPEN_FAILED": "Chrome 실행 실패", "CHROME_STAGE": "Chrome 판독 단계",
                  "CLICK_PLANNED": "클릭 직전 기록", "BUTTON_FAILED": "클릭 요청 거절", "CONFIRM_REQUEST": "퇴실 확인 요청",
                  "WAKE_REQUESTED": "화면 깨우기 요청", "WAKE_VISIBLE": "깨우기 화면 포커스", "WAKE_BLOCKED": "화면 깨우기 차단",
                  "WAKE_AUTH_REQUIRED": "사용자 인증 필요", "WAKE_DISMISS_REQUEST": "시스템 잠금 해제 요청",
                  "WAKE_DISMISS_RESULT": "시스템 잠금 해제 응답", "WAKE_FINISHED": "잠금 처리 종료"}
        lines += ["", "## 일반 출결 동작", "", f"일반 동작 {len(events)}건. Chrome 내부 HTTP 요청 건수와 다릅니다."]
        for code, label in labels.items():
            lines.append(f"- {label}: {counts[code]}건")
        def event_time(row):
            value=row.get("occurred_at_ms")
            return value if isinstance(value,(int,float)) and not isinstance(value,bool) else 0
        lines += ["", "| 한국 시각 | 출결 단계 | 동작 | 사유 코드 |", "|---|---|---|---|"]
        for event in sorted(events,key=event_time):
            try: stamp=datetime.fromtimestamp(event_time(event)/1000,timezone.utc).astimezone(timezone(timedelta(hours=9))).strftime("%m/%d %H:%M:%S") if event_time(event)>0 else "시각 없음"
            except (ValueError,OverflowError,OSError): stamp="시각 없음"
            reason=event.get("reason_code","")
            if not isinstance(reason,str) or not re.fullmatch(r"[A-Z][A-Z0-9_:]{0,99}",reason): reason="UNKNOWN"
            phase={"ENTRY":"입실","CHECKOUT":"퇴실","IDLE":"대기"}.get(event.get("phase"),"미확인")
            lines.append(f"| {stamp} | {phase} | {labels.get(event.get('event_code'),'기타 동작')} | {reason} |")
        lines += ["", "클릭 직전 기록은 클릭 실행 증거가 아니며, UI 클릭 접수와 서버 출결 완료를 구분합니다. 화면 포커스·시스템 해제 응답만으로 무인 인증 성공을 판정하지 않습니다."]
    lines += ["", "## 시험별 조건", "", "| 방식 | 접근성 연결 | 위치 항상 허용 | 출석 예약 / 자동 클릭 / 실제 출결 | Wi-Fi 진단 | 진입 지연 | 직접 조작 기록 |", "|---|---|---|---|---|---|---|"]
    def flag(value):
        return "켜짐" if value is True else "꺼짐" if value is False else "기록 없음"
    for row in rows:
        env = row.get("execution_environment") or row.get("environment") or {}
        delay = row.get("entry_delay_ms")
        seconds = f"{delay/1000:.1f}초" if isinstance(delay, (int, float)) and delay >= 0 else "기록 없음"
        gates = " / ".join(flag(v) for v in (env.get("schedule_enabled"), env.get("auto_click"), row.get("actual_attendance_requested")))
        # Table cells only use app enums or booleans, never free-form notes or site text.
        wifi_codes = {"ALLOWED", "FINE_LOCATION_REQUIRED", "LOCATION_DISABLED", "VPN_ACTIVE", "INTERNET_UNVALIDATED", "NO_WIFI_TRANSPORT", "SSID_NOT_REGISTERED", "SSID_UNAVAILABLE", "SSID_UNAVAILABLE_BACKGROUND_PERMISSION", "PERMISSION_DENIED"}
        code = env.get("wifi_check_code")
        wifi = code if code in wifi_codes else "기록 없음"
        interactions = {"UNTOUCHED": "무조작", "MANUAL_WAKE": "직접 화면 켬", "MANUAL_UNLOCK": "직접 잠금 해제", "NOTIFICATION": "알림 누름", "SYSTEM_AUTH": "시스템 인증 응답"}
        lines.append(f"| {MODES.get(row.get('mode'), '알 수 없음')} | {flag(env.get('accessibility_connected'))} | {flag(env.get('background_location_permission'))} | {gates} | {wifi} | {seconds} | {interactions.get(row.get('user_interaction'), '미기록')} |")
    unknown = sum(r.get("mode") not in MODES for r in rows)
    if unknown:
        lines += ["", f"방식을 식별하지 못한 기록 {unknown}회: 원본 확인 필요."]
    lines += ["", "Chrome 주소 확인·HTTPS 응답을 실제 입실/퇴실 완료로 취급하지 않습니다. 시스템 해제 성공도 PIN·지문 입력을 직접 증명하지 않습니다.",
              "이전 automatic_entry 필드는 예약 호출 경로일 뿐 자동 잠금 해제 성공이 아닙니다. user_interaction은 사용자 진술이며 앱 관찰과 구분합니다.",
              "과거 legacy 기록은 날짜·환경 정보가 없으며 동일한 원문은 한 번만 집계합니다. 사용자 판정과 앱 관찰이 다르면 원본 메모·이벤트를 함께 확인하세요.", "", "## 원본"]
    for source in sorted({r["source"] for r in rows + events}):
        lines.append(f"- {source}")
    return "\n".join(lines) + "\n"


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("files", nargs="*")
    p.add_argument("--fetch", action="store_true", help="Read the dedicated branch through the existing git credential")
    p.add_argument("--include-verification", action="store_true")
    p.add_argument("--output", type=Path)
    args = p.parse_args()
    docs = []
    if args.fetch:
        docs.extend(remote_documents())
    for name in args.files:
        path = Path(name)
        if path.stat().st_size > 256 * 1024:
            raise ValueError(f"Oversized report: {path}")
        docs.append((str(path.resolve()), json.loads(path.read_text(encoding="utf-8"))))
    rows, skipped = collect(docs, args.include_verification)
    report = render(rows, skipped)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        print(report)


if __name__ == "__main__":
    main()
