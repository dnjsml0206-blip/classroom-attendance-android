# 출결 사이트 로컬 재현

2026-09-16에 공개 Present.asp를 한 번 읽어 폼, input 버튼, inline onclick 함수와 비동기 호출 순서를 복제했다. 원본 응답 SHA-256은 03232a3b1dbd854d21dedb37c70475aebf1ea83041cbf3d7db3a5d2f57a07264이다.

원본의 외부 라이브러리·스타일·로고는 제거하고 최소 $.get 어댑터, 근사 CSS와 시험 배지를 넣었다. 이름·과정 코드·기록은 모두 더미 값이다. 실제 화면의 픽셀 일치나 서버 내부 인증 구현의 복제는 아니다. 버튼 종류(input type=button), value, onclick, 동적 생성, 확인창, location.reload 흐름은 공개 소스의 형태를 유지한다.

scripts/attendance_replica.mjs는 Android Chrome의 CDP Fetch 가로채기를 사용한다. 모든 새 page target은 일시 정지 상태로 연결한다. Android의 시작 대기를 풀면서 요청 가로채기를 설정한다. 시험 Chrome의 host-resolver-rules로 외부 DNS 조회도 차단하므로 연결 초기 단계에서 실제 서버로 접속할 수 없다. 요청을 실제 네트워크로 계속 보내는 명령은 구현하지 않았다. 모든 페이지/조회/출석 경로를 로컬 응답으로 끝내고, 알 수 없는 URL도 로컬 404를 반환한다. 앱은 기존 주소 검증 및 실제 AccessibilityService 코드 그대로 실행한다. 로컬 인증된 제어 연결을 먼저 검사하므로 가로채기가 없으면 검사 시작 전 실패한다.

준비: 전용 API 35 에뮬레이터, Google Chrome, 검사 APK. 에뮬레이터 Chrome에 remote debugging을 켜고 adb forward tcp:9222 localabstract:chrome_devtools_remote 및 adb reverse tcp:9333 tcp:9333을 연결한다. 저장소 루트에서 node scripts/attendance_replica.mjs를 실행한다. build/site-replica-2026-09-16/run-id.txt 값이 검사 실행 인수 replica다.

검사: am instrument -w -r -e suite replica -e case entry -e replica RUN_ID net.tacademy.attendance.test/net.tacademy.attendance.DeviceChecks

case: entry, checkout, slow-page, slow-result, focus, focus-expire, no-result, auth, wrong-date, server-error, no-course. 수정 전 focus는 -e baseline true를 추가해 클릭 전 영구 보류를 재현한다. 요청·화면·앱 이벤트는 build/site-replica-2026-09-16 및 에뮬레이터 앱의 external files/replica에 남는다.

실제 휴대폰에서는 실행하지 않는다. 검사 코드는 androidTest에만 포함된다. 사용자 Chrome 쿠키·인증 정보는 읽거나 복사하지 않는다.

2026-09-16 퇴실 확인창 보완: `checkout-wifi`는 퇴실 응답 0과 사이트 원본의 Wi-Fi 안내 alert, `checkout-unknown-alert`는 분류 불가 안내, `checkout-no-result`는 응답 후 퇴실 시각 미저장, `checkout-reappeared`는 동일 확인창이 즉시 다시 표시되는 조건이다. 마지막 두 안내 변형은 원본 페이지에 없는 검증용 주입이다. CDP는 확인창을 대신 누르지 않으며 앱 접근성의 클릭으로 닫혔는지를 `javascriptDialogClosed` 이벤트와 로컬 제출 횟수로 대조한다.

2026-09-30 출결 관리 검사: `checkout-early`는 예정 시간 전 명시적으로 승인한 조퇴와 실제 Chrome 확인창을 검사한다. `checkout-read`는 이미 수동으로 조퇴한 사이트 시각을 조회하며, `checkout-read-entry`는 입실만 있는 페이지를 조회한다. 조회 검사는 입실·퇴실 제출 및 버튼/확인 클릭이 모두 0건인지 대조한다.
