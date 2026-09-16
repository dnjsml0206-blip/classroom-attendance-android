# 2026-09-16 아침 자동 입실 원인 분석

조사 대상은 09:08:12 KST에 내보내고 09:08:27에 업로드한 v0.9.1 로그다. 사용자 설명은 자동 입실 실패 후 직접 로그인·입실했다는 것이다. 해당 수동 작업 시각과 재인증 여부는 현재 이벤트에 기록되지 않아 구분해서 해석한다. 실제 출석 서버를 조회하거나 출결을 제출하지 않았다.

[고정된 원본 커밋의 로그](https://github.com/dnjsml0206-blip/classroom-attendance-android/blob/80348ee010262ad734f558bd9eb49a585d6106c0/logs/lock-tests/2026-09-16/3ac2ded3-0612-47cb-af50-42413cba7620.json)

## 결론

로그에 입실 클릭 직전(CLICK_PLANNED), 클릭 반환(BUTTON_REQUEST/BUTTON_FAILED), 서버 시각 관찰(RECORD_CONFIRMED)이 모두 0건이다. 완료된 일반 자동 시도는 Chrome 주소 확인 후 화면 판독 단계에서 멈췄다. 오늘 앱이 입실을 눌렀으나 서버가 저장하지 못했다는 근거는 없다. 두 번째 앱 내 직접 실행은 내보내기 시점에 아직 35초 대기 시간이 끝나지 않아 이후 결과를 이 파일로 판단할 수 없다.

## 시간표 (KST)

| 시각 | 관찰 | 해석 |
| --- | --- | --- |
| 08:30:00.024 | ALARM_FIRED, 기록 지연 23ms | 시작 알람 정상 수신 |
| 08:30:00.057–.071 | MONITOR_STARTED, NO_WIFI_TRANSPORT | 감시는 시작했으나 Wi-Fi 조건 대기 |
| 09:02:22.713 | WAKE_REQUESTED / TRUST | 설정한 신뢰 잠금 방식으로 화면 진입 시도 |
| 09:02:23.591–.594 | WAKE_VISIBLE, USER_AUTH_REQUIRED | 화면 진입 후 기기가 인증 필요 상태여서 종료. 시스템 잠금 해제 요청·성공 기록은 없음 |
| 09:04:16.574 | CHROME_OPEN_DISPATCHED / AUTOMATIC | 잠금 조건 통과 후 Chrome 실행 전달 |
| 09:04:17.949 | PAGE_SETTLING | 지정 출결 주소를 읽고 초기 화면 안정화 대기에 진입 |
| 09:04:19.366 | CHROME_NOT_VISIBLE | 선택한 접근성 화면 루트가 없거나 Chrome 패키지가 아니었음 |
| 09:04:51.584 | TIMEOUT_CHROME_NOT_VISIBLE | 시작 후 약 35초에 화면 판독 실패로 종료 |
| 09:04:55.667 이후 | AUTOMATIC:PAUSED | 자동 재접속 보류 |
| 09:06:45.826 | WAKE_BLOCKED / ACCESS_PAUSED | 같은 보류로 잠금 진입도 제한 |
| 09:07:57.198–.221 | MANUAL_REQUEST / ONE_SHOT, Chrome 실행 | 앱 내 직접 입실 실행 요청. 웹사이트에서 사람이 누른 시각을 뜻하지 않음 |
| 09:07:58.307–09:07:59.516 | PAGE_SETTLING → ADDRESS_NOT_VISIBLE → CHROME_NOT_VISIBLE | 직접 실행도 주소·화면 판독 단계에서 막힌 흔적 |
| 09:08:12.736 | 내보내기 | 직접 실행 시작 후 약 15.5초. 이후 종료·완료 결과는 미포함 |

CHROME_STAGE는 시도마다 같은 사유를 한 번만 기록하므로 각 단계가 계속 유지된 정확한 시간은 알 수 없다. 마지막 자동 종료 코드는 마지막으로 관찰한 단계가 CHROME_NOT_VISIBLE였다는 뜻이다.

## 코드로 확인한 원인과 한계

1. **09:02의 신뢰 잠금 처리:** ScheduledUnlockActivity는 TRUST 모드에서 Android가 기기 인증 필요로 보고하면 USER_AUTH_REQUIRED로 끝낸다. Extend Unlock 설정을 선택했다는 사실은 그 순간 기기가 신뢰 상태였다는 증거가 아니다. 신뢰가 왜 풀렸는지는 이 로그에 없다. 현재 깨우기 시도는 날짜·입실/퇴실별 한 번으로 제한한다.
2. **09:04의 클릭 전 중단:** ChromeAttendanceService.focusedRoot()는 포커스를 가진 창의 루트를 먼저 하나 반환한다. inspect()는 그 루트가 null이거나 패키지가 com.android.chrome이 아니면 CHROME_NOT_VISIBLE로 대기한다. 눈에 Chrome이 보이지 않았다는 단정이나 접근성 권한이 꺼졌다는 뜻은 아니다. 실제 화면 전환, 시스템 창의 포커스, 접근성 루트 반환 실패 등을 현재 로그로 구분할 수 없다. 현재 앱은 창 종류·선택한 패키지 분류·루트 유무를 기록하지 않는다.
3. **회복이 안 된 이유:** fail()은 클릭 전 실패도 TrafficGuard.pause()로 연결한다. 따라서 이번에는 실제 클릭을 전혀 요청하지 않은 화면 판독 실패가 이후 자동 시도까지 보류시켰다. 보류는 날짜·ENTRY 단계 기준이다. 단순한 페이지 새로고침 간격 부족이나 자동 접속 3회 소진이 아니다.
4. **최근 UI 업데이트 적용 여부:** 미배포 스탠드·달력 변경 전후 ChromeAttendanceService, ChromePage, ScheduledUnlock의 실행 로직은 v0.9.1과 같다. 그 UI 업데이트만 설치해도 이번 문제가 해결된다고 볼 수 없다.

[Android의 현재 활성 창 설명](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#getRootInActiveWindow())에 따르면 활성 창은 사용자가 터치하거나 입력 포커스를 가진 창이다. 이를 토대로 화면 포커스 영향을 의심할 수 있지만, 오늘 특정 창이 원인이었다는 증거는 없다.

## 입실 버튼 처리 방식

- 조건 확인 후 ACTION_VIEW Intent를 com.android.chrome으로 보내 기존 Chrome 세션에서 Present.asp를 연다. 별도 앱 WebView로 로그인하거나 네이티브 HTTP 출석 요청을 보내지 않는다.
- 접근성으로 현재 Chrome의 화면 요소와 문구를 수집한다. 지정 주소, 오늘 출결일, 입실·퇴실 시각을 확인한다. 초기 주소 관찰 후 최소 1.8초를 기다린다.
- 이미 오늘 입실 시간이 보이면 다시 클릭하지 않고 사이트에서 관찰한 시간으로 완료를 기록한다.
- 화면에 보이고 활성화된 요소 중 텍스트 또는 설명이 정확히 '입실'인 요소를 찾는다. 그 요소 또는 상위 두 단계에서 클릭 가능한 대상을 선택한다. 고정 좌표나 화면 이미지 인식으로 누르지 않는다.
- 클릭 전 날짜·단계·시각을 저장하고 CLICK_PLANNED를 남긴 뒤 performAction(AccessibilityNodeInfo.ACTION_CLICK)을 한 번 호출한다. Android 반환값을 BUTTON_REQUEST 또는 BUTTON_FAILED로 기록한다.
- 클릭 동작 반환만으로 출석 성공을 판정하지 않는다. 화면에 '입실시간 : HH:mm'이 표시되는 것을 다시 읽어야 완료와 알림을 기록한다. 클릭 뒤 결과 대기는 최대 35초로 새로 시작한다. 퇴실은 별도로 확인 대화상자의 확인 버튼도 한 번 누른다.
- Chrome 화면 확인은 약 700ms 간격의 로컬 접근성 조회이며 매번 페이지를 새로 여는 요청이 아니다.

[클릭 구현](../app/src/main/java/net/tacademy/attendance/ChromeAttendanceService.java) · [Android performAction 설명](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#performAction(int))

## 다른 가능성의 구분

- 앱 실행 버전은 이전 v0.6.0이 아닌 v0.9.1이다. 이번 일반 자동 시도는 조회 전용 잠금 테스트가 아니다.
- 업로드 시점 접근성 설정·연결, 알람, 위치 권한, 등록 Wi-Fi, 예약 및 자동 클릭은 ON이다. 이 스냅샷을 모든 과거 시점의 상태로 간주하지 않는다.
- 이전 클릭 표식은 9월 15일 CHECKOUT이다. 오늘 9월 16일 ENTRY와 키가 다르며, 오늘 중단 원인은 PENDING_CLICK이 아니다.
- 배터리 최적화 제외가 false이지만 이번에는 알람·감시·Chrome 실행이 진행됐으므로 이를 직접 원인으로 확정할 근거가 없다.
- 알려진 인증 필요/서버 오류 화면에 해당하는 CHROME_AUTH_REQUIRED, SITE_ERROR_SCREEN은 기록되지 않았다. 판독이 중단된 만큼 로그인·서버 상태가 정상이었다고 보장할 수도 없다.
- 사용자가 직접 처리한 사이트 입실은 앱이 읽지 못해 내보내기 시점에도 entry_confirmed_today=false다. 이는 실제 서버에 입실이 없다는 의미가 아니다.
- Chrome 실행 전달은 자동 1회와 앱 내 직접 실행 1회다. 실제 HTTP 요청량은 이 로그에서 측정하지 않는다.

## 권장 보완 (이번 조사에서는 미구현)

1. 화면 루트 없음, 다른 앱 포커스, 시스템 창, Chrome 주소 미노출을 개인정보 없는 고정 분류로 진단한다. 앱 화면의 입실 진행 상태도 준비·클릭·시간 확인으로 분리한다.
2. Chrome이 실제로 조작 가능한 앞쪽 화면인지 판별하는 창 선택 로직을 보강한다. 주소가 사라졌다고 다른 페이지를 출결 화면으로 인정하거나 가려진 버튼을 누르지 않는다.
3. 클릭 전 화면 중단과 클릭 후 결과 미확인을 분리한다. 전자는 사용자가 Chrome으로 돌아오면 기존 시간대·횟수·접속 제한 안에서 재개하고, 후자는 기존 중복 클릭 방지를 유지한다. 무제한 재접속은 추가하지 않는다.
4. 신뢰가 회복된 경우 깨우기 기회를 어떻게 제한적으로 재허용할지 검토한다. PIN·지문이 필요한 보안 잠금을 우회하는 기능은 아니다.

원본을 기존 분석기에 넣고 이벤트 수·클릭 0건·실행 2건·버전·중단 코드를 대조했다. 결과는 build/entry-audit-2026-09-16에 보관했다. 앱 코드와 APK는 이번 조사에서 변경하지 않았으며 Git 푸시하지 않았다.

후속 작업: 사용자의 재현·보완 요청에 따라 같은 날 로컬 페이지 재현 시험과 복구 보완을 완료했다. 위 분석의 조사 시점 상태와 구분한다. [시험 및 보완 결과](verification-replica-2026-09-16.md)
