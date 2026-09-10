# v0.3.0 검증 기록

2026-09-10 · JDK 21 · Android SDK 36 · Gradle 9.3.1 · Android 15 / API 35 에뮬레이터

## 동작

오늘 화면은 따뜻한 일러스트와 밝은 배경으로 시작합니다. 퇴실 시각 1시간 전부터 자정까지 야간 일러스트와 남색 대시보드를 표시합니다. 기본 퇴실 17:50이면 전환 시각은 16:50입니다. 입실 종료와 겹치면 입실 종료를 우선합니다. 한국 시간 기준이며 휴대폰 시간대와 무관합니다.

MainActivity의 기존 화면 갱신 주기(2초)에서 테마·날짜·설정 변경을 반영합니다. 앱 복귀 때도 갱신하며 스크롤 위치를 보존합니다. 테마 전환용 서비스나 알람은 추가하지 않았습니다. 완료 출결과 달력 데이터는 기존 확인 기록을 사용합니다.

일러스트는 APK 안에 포함되어 화면 표시를 위해 인터넷 다운로드가 필요하지 않습니다. 생성 도구와 정확한 프롬프트는 [theme-assets.json](theme-assets.json)에 기록했습니다. 글자와 버튼은 Android 네이티브 화면이며 이미지에 포함된 가짜 UI가 아닙니다.

## 자동 검증 결과

- 단위 테스트 30개 통과: 기존 23개 + 테마 시간 경계 7개
- 린트 오류 0개, 경고 1개: 새 Gradle 버전 안내
- 앱 APK 및 Android 테스트 APK 빌드 성공
- v0.2.1 위에 v0.3.0 업데이트 설치 성공
- Android 화면 테스트 10개 통과
- 기존 Android 예약·완료 알림 테스트 12개 통과

테마 단위 테스트는 입실/낮 유지, 16:50 경계, 자정 복귀, 퇴실 시각 변경, 한국 시간 변환, 입실 시간 중복, 자정 직후 퇴실 설정을 확인합니다.

화면 테스트는 다음을 확인합니다.

1. 아침 테마에서 확인된 입실 시각을 유지하고 퇴실 기록을 임의로 만들지 않음
2. 16:49:59까지 밝은 테마 유지
3. 화면을 열어 둔 상태에서 16:50에 야간 테마로 변경하고 기록·로그 유지
4. 야간 화면에서 출결 달력 열기
5. 퇴실 시각 변경을 현재 화면에 반영
6. 변경한 퇴실 시각의 1시간 전 경계에서 야간 전환
7. 자정에 밝은 테마와 새 날짜의 기록·로그로 변경
8. 백그라운드에서 돌아왔을 때 테마 갱신
9. 설정 탭의 밝은 색상과 상태 표시줄 아이콘 복구
10. 설정·연결을 거쳐 오늘 탭으로 돌아오면 야간 색상 복구

에뮬레이터에서 화면용 Clock만 고정했고 OS 시계나 출석 예약 시계는 변경하지 않았습니다. 테스트용 예시 출결 기록·로그를 사용했으며 출석 예약과 자동 클릭을 끄고 불일치 SSID를 지정했습니다. 테스트가 종료되면 원래 설정·기록을 복원합니다. 기존 예약·알림 테스트도 실제 출석 요청이나 Chrome 클릭을 하지 않습니다.

```shell
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e suite themes net.tacademy.attendance.test/net.tacademy.attendance.DeviceChecks
adb shell am instrument -w net.tacademy.attendance.test/net.tacademy.attendance.DeviceChecks
```

## 실제 앱 캡처

예시 기록은 테스트에서만 사용하며 사용자 설치 APK에는 들어가지 않습니다. 상태 표시줄은 에뮬레이터의 실제 시각이고 앱의 테마 판단 시각은 테스트용 시각입니다.

- [따뜻한 오늘 화면](screenshots/today-warm.png)
- [야간 오늘 화면](screenshots/today-night.png)
- [야간 화면의 달력](screenshots/calendar-night.png)
- [설정](screenshots/settings.png)
- [연결](screenshots/connection.png)

## 배포 파일

- classroom-attendance-0.3.0-debug.apk: 4,384,079 bytes
- versionName 0.3.0 / versionCode 5
- SHA-256: b31176dc55e51fe86ddba262627f9c9ee3e9cfe3a30507139fcc1cebcf94bba3
- 서명 인증서 SHA-256: 9f2973a9e4c72cdbc3304b649a62f48775ecd7b6b5542e9667e6d7711ffefc2a
- v0.2.1과 동일한 인증서로 서명 검증 성공

APK와 SHA256SUMS-v0.3.0.txt를 GitHub 테스트 릴리스에 첨부합니다.

## 실기기 확인 범위

실제 수강생의 인증된 Chrome·강의실 Wi-Fi 출결 및 갤럭시별 인텔리전트 Wi-Fi 메뉴 이동은 실기기 확인이 필요합니다. 기존 출석 조건을 따르며 화면 잠금 해제가 필요합니다. 완료 알림은 항상 표시하되 Android의 앱/채널 알림 허용이 필요합니다.