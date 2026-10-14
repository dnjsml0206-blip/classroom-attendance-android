# 2026-10-14 입실 지연 분석 및 수정

분석 대상: codex/device-logs의 logs/lock-tests/2026-10-14/ff25f707-30bd-4b5d-9c6e-9d2e70340b92.json, 앱 0.9.21.
업로드 당시 설정은 자동 클릭/예약 ON, 일반 잠금 방식 WAKE. 업로드 시점 설정이 아니라 해당 시도의 WAKE_REQUESTED도 WAKE를 기록한다.

## 관찰
| 한국 시각 | 로그 |
|---|---|
| 09:02:52.321 | Wi-Fi 연결 감지, 인터넷 검증 중 |
| 09:02:52.722 | Wi-Fi ALLOWED |
| 09:02:52.775 | 자동 입실 시작: 연결 허용 53ms 후 |
| 09:02:53.458 | 깨우기 화면 보임, keyguard_locked=true/device_locked=false |
| 09:02:53.476 | WAKE 정책이 USER_AUTH_REQUIRED로 종료 |
| 09:04:44.861 | 시스템 USER_PRESENT 수신 |
| 09:04:44.881 | 자동 처리 재개: 20ms 후 |
| 09:04:47.678 | 입실 버튼 클릭 계획 |
| 09:04:48.238 | AFTER_APP_CLICK, 사이트 입실 시각 09:04 확인 |

연결 허용부터 기록 확인까지 115.516초 중 핵심은 약 112초의 잠금화면 대기다. Wi-Fi 검증은 약 0.4초였으며 GPS 대기/60초 폴링이 이번 첫 시도를 늦춘 증거는 없다. 기기 인증은 유효했으나 화면 깨우기 정책이 keyguard 표시와 인증 필요를 같은 것으로 처리했다. 서버 차단이 아니라 로컬 정책 문제다.

## 수정
- 일반 예약 WAKE도 화면이 켜지고 device_locked=false이면 requestDismissKeyguard로 표시 중인 잠금화면 닫기를 요청한다.
- 화면이 꺼져 있으면 깨우기 완료를 기다리고, device_locked=true이면 인증 필요로 유지한다.
- Android의 실제 잠금 해제 성공 후 Wi-Fi/시간대/실제 잠금 상태를 다시 검사해 즉시 출결로 넘긴다.
- 기존 400ms tick을 기다리지 않고 성공 콜백에서 재검사를 예약한다.
- 진단 로그에 scheduled_lock_mode와 AUTHENTICATED_KEYGUARD_DISMISS를 추가했다.
- 예약 시간/휴강일/등록 Wi-Fi/완료 여부/서버 접근 제한과 중복 클릭 보류는 유지한다.
- 제작자 진단 WAKE 시험의 순수 화면 깨우기 의미는 유지한다.
- 기존 미커밋 설정 UI 수정은 보존한다.

공식 근거: https://developer.android.com/reference/android/app/KeyguardManager#requestDismissKeyguard(android.app.Activity,%20android.app.KeyguardManager.KeyguardDismissCallback)
인증이 필요하지 않거나 신뢰 상태인 경우 시스템이 사용자 동작 없이 닫을 수 있다. API 호출은 실제 PIN 잠금을 우회하지 않는다.

## 별도 관찰
입실 완료 후 LOCKDOWN_ITEM_NOT_FOUND 및 SCREEN_OFF_UNCONFIRMED도 기록됐다. 입실 이전 지연의 원인은 아니며 이번 수정에서 해결됐다고 주장하지 않는다.
실제 Galaxy Android 17의 도착 재시험은 필요하다. 서버에 시험 출석을 전송하지 않는다.

## 검증 결과
- 단위 테스트 231개 통과, 실패/오류 0. 로그의 WAKE + keyguard=true + device_locked=false 회귀 및 실제 인증 필요 상태 차단을 포함.
- testDebugUnitTest, lintDebug, assembleDebug, assembleDebugAndroidTest 성공. 최종 설명 문구 변경 후 testDebugUnitTest/lintDebug/assembleDebug 재실행 성공.
- Lint: 오류 0, 경고 19.
- v0.9.22(versionCode 37), 제작자용 로그 기능 포함. 기존 0.9.21과 서명 인증서 SHA-256 일치.
- 실기기/에뮬레이터 실행 시험은 이번 작업에서 미실행. 단위 테스트와 APK 빌드를 실기기 성공으로 간주하지 않음.
- APK SHA-256: 
CE4B79D18D44B293EC3DCFE3522B8EFB1C9296A33B445A97DFE0DDF07882593D
- 이번 작업에서 Git 커밋/푸시 미실행.
