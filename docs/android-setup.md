# Android 설정

## Chrome

평소 인증된 Google Chrome 일반 탭을 사용한다. 앱의 연결 탭에서 출결 페이지를 열어 인증 상태를 먼저 확인한다. Chrome 접근성을 켜야 자동 클릭을 사용할 수 있다. 화면이 잠겨 있으면 출석 알림을 눌러 잠금을 해제한다.

## 예약 권한

정확한 위치·알림, 위치 항상 허용, 정확한 알람 권한을 허용한다. Android 10에서는 항상 허용 권한 요청을 별도로 진행하고, 이후 버전에서는 앱 정보의 위치 권한에서 설정한다.

[Android 백그라운드 서비스 제한](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
[Android 접근성 서비스](https://developer.android.com/guide/topics/ui/accessibility/service)
[Android 화면 시작 제한](https://developer.android.com/guide/components/activities/secure-bal)

## Wi-Fi와 30m 반경

휴대폰에도 TACADEMY_5G를 저장하고 자동 연결을 켠다. 앱에 추가하는 SSID 목록은 출석 허용 목록이다. Wi-Fi 비밀번호를 저장하거나 꺼진 Wi-Fi를 임의로 켜지 않는다.

강의실 기본 반경은 30m이고 설정에서 바꿀 수 있다. 위치 오차가 반경보다 크면 도착으로 판단하지 않는다. 도착 알림은 입실 감시 시간에만 동작한다.

## 인텔리전트 Wi-Fi

설정의 인텔리전트 Wi-Fi 버튼을 사용한다. 기기가 제공하는 직접 진입 화면을 우선 사용하며, 접근성이 켜진 갤럭시에서는 Wi-Fi의 더보기 메뉴를 찾아 이동한다. 지원되지 않으면 Wi-Fi → ⋮ → 인텔리전트 Wi-Fi 경로를 안내한다. 설정의 Wi-Fi 옵션 자체를 자동으로 바꾸지는 않는다.

[삼성 Wi-Fi 설정 안내](https://www.samsung.com/us/support/answer/ANS10002546/)

## 시간과 휴강일

입실 시작·종료, 퇴실 시각은 시간 선택기로 변경한다. 퇴실 재시도 시간은 1–60분이며 자정을 넘기지 않도록 설정한다. 휴강일은 달력에서 날짜를 추가하고 저장한다.

예약이 켜져 있어도 시간 밖에서는 감시를 종료한다. 재부팅 후 예약은 복구하며, 앱을 강제 중지한 경우 다시 앱을 연다.

## 완료 알림

입실·퇴실 완료가 확인되면 날짜와 시각을 알림창에 각각 표시한다. 이전 버전에서 결과 알림을 껐어도 표시한다. 앱을 열거나 감시가 끝나도 알림을 자동 삭제하지 않으며 직접 지울 수 있다.

설정 → 알림 → 휴대폰 알림 설정에서 앱 알림과 ‘입실·퇴실 결과’ 채널을 허용해야 한다. Android가 알림을 차단한 경우 앱에서 이를 강제로 허용하지 않는다.

[Android 알림 생성과 자동 삭제](https://developer.android.com/develop/ui/compose/notifications/create-notification)
[Android 알림 권한](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
