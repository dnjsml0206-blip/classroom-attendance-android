# v0.2.0 검증 기록

검증일: 2026-09-10. Windows, JDK 21, Android SDK 36, Gradle 9.3.1에서 빌드했습니다.

## 빌드와 정적 검사

- `testDebugUnitTest`: 23개 성공, 실패·오류 0개
- `lintDebug`: 오류 0개, 경고 1개. 고정한 Gradle보다 새 버전이 있다는 안내입니다.
- `assembleDebug`, `assembleDebugAndroidTest`: 성공
- APK 패키지: `net.tacademy.attendance`, versionName `0.2.0`, versionCode `3`
- APK 서명 검증 성공. v0.1.1과 동일한 인증서이므로 업데이트 설치가 가능합니다.

| 단위 테스트 | 수 | 확인 내용 |
| --- | ---: | --- |
| ScheduleTest | 10 | 입실·퇴실 시간 경계, 완료 후 종료, 다음 예약, 휴강일·요일, 설정 시각, 한국 시간, 30m 위치 오차 |
| ChromePageTest | 6 | 지정 출석 주소, 출결일·시간 판독, 잘못된 값 거부, 퇴실 확인 문구, 자체 주소 HTTPS 변환 |
| PolicyTest | 4 | SSID 판별, 기록 형식 |
| SiteProtocolTest | 3 | 공개 페이지의 프로토콜 판별 규칙 |

## Android 실행 검증

Android 15 / API 35 Pixel 6 에뮬레이터에서 `DeviceChecks` 계측 테스트 6개가 통과했습니다.

1. 앱 화면이 뒤로 간 상태에서 정확한 알람이 입실 감시 서비스를 시작합니다.
2. 입실 기록 확인 후 감시 서비스를 종료하고 예약 활성화는 유지합니다.
3. 다음 퇴실 예약이 남습니다.
4. 지정 Wi-Fi에 연결되지 않아 퇴실할 수 없으면 Wi-Fi 연결 요청 알림을 표시합니다.
5. 퇴실 기록 확인 후 감시 서비스와 연결 요청 알림을 종료합니다.
6. 휴강일에는 당일 감시를 건너뜁니다.

테스트는 자동 클릭을 끄고 일치하지 않는 테스트용 SSID와 가상 출결 기록을 사용했습니다. 실제 출석 요청이나 Chrome 출석 버튼 클릭은 수행하지 않았습니다. 테스트 종료 후 기존 설정을 복원하는 것도 확인했습니다. 에뮬레이터에서만 실행하도록 제한했습니다.

```shell
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w net.tacademy.attendance.test/net.tacademy.attendance.DeviceChecks
```

기기 테스트의 전제: 앱의 정확한 위치·백그라운드 위치·알림 권한과 정확한 알람 허용. 현재 시각을 기준으로 알람을 생성하므로 한국 시간 01:00–23:00에 실행합니다. 계측 코드는 테스트 APK에만 포함됩니다.

## 화면과 설정 확인

- 오늘 화면에 출결 현황·오늘 로그가 표시되고 CONNECTION·AUTOMATION 카드는 제거됐습니다.
- 출결 달력에서 날짜를 고르면 해당 날짜의 앱 확인 기록을 표시합니다. 기록이 없으면 미확인으로 안내합니다.
- 휴강일을 달력에서 추가·저장하고 다시 삭제·저장해 실제 설정 반영을 확인했습니다.
- 시간 선택기로 퇴실을 18:05로 변경해 저장되는 것을 확인한 뒤 기본 17:50으로 복구했습니다.
- 오늘·설정 화면의 최종 스크린샷을 저장하고 확인했습니다.
- Chrome에서 HTTPS 출석 주소를 열어 URL과 페이지의 접근성 노출을 확인했습니다. 인증된 학생 세션을 사용하지 않았습니다.

## 실기기에서 남은 확인

- 수강생의 기존 인증된 Chrome에서 실제 입실·퇴실 버튼과 확인 대화상자, 완료 기록 판독
- 실제 강의실 Wi-Fi 및 실내 30m 위치 조건
- 갤럭시 One UI 버전별 인텔리전트 Wi-Fi 직접 진입·접근성 메뉴 이동
- 화면 잠금 해제 후 알림으로 재진입, 제조사 절전 조건에서 예약 시각 실행

잠금 화면을 자동으로 해제하지 않습니다. 달력에는 앱이 직접 확인한 기록만 저장되며, 설치 이전의 전체 출결은 Chrome에서 확인합니다.

## 배포 파일

- `classroom-attendance-0.2.0-debug.apk` (976,075 bytes)
- SHA-256: `6a13fcb0554972b61f80c0ee77c3ab823f35a2ee2aa3b80cbd71735daaeb9eca`
- 서명 인증서 SHA-256: `9f2973a9e4c72cdbc3304b649a62f48775ecd7b6b5542e9667e6d7711ffefc2a`

배포 APK와 체크섬 파일은 GitHub v0.2.0 릴리스에 첨부합니다. debug 서명의 테스트 버전입니다.
