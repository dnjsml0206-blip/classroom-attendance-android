# v0.2.1 검증 기록

검증일: 2026-09-10. JDK 21, Android SDK 36, Gradle 9.3.1, Android 15 / API 35 에뮬레이터에서 확인했습니다.

## 변경 사항

Chrome에서 완료 기록을 확인하면 입실·퇴실 알림을 항상 게시합니다. 날짜와 입실/퇴실 단계를 각각 구분해 기존 알림을 덮어쓰지 않으며, 알림을 눌러 앱을 열거나 감시가 종료되어도 자동 삭제하지 않습니다. 사용자는 직접 지울 수 있습니다.

기존 결과 알림 OFF 설정을 완료 알림 경로에서 제거했습니다. 설정에서는 항상 표시 안내, 휴대폰의 알림 허용 상태와 알림 설정 버튼을 보여 줍니다.

## 자동 검증

- `testDebugUnitTest`: 23개 통과, 실패·오류 0개
- `lintDebug`: 오류 0개, 경고 1개 (새 Gradle 버전 안내)
- `assembleDebug`, `assembleDebugAndroidTest`: 성공
- v0.2.0 위에 v0.2.1 업데이트 설치: 성공
- `DeviceChecks`: 12개 통과

기존 예약 수명 검증 6개와 다음 알림 검증 6개를 실행했습니다.

1. 이전 결과 알림 OFF 설정에서도 완료한 입실 알림과 시각을 표시합니다.
2. 입실·퇴실 완료 알림이 동시에 남습니다.
3. 감시 종료 후에도 완료 알림이 남습니다.
4. 알림의 진입 동작으로 앱을 열어도 입실·퇴실 알림이 유지되며 자동 삭제 옵션이 꺼져 있습니다.
5. 다른 날짜의 완료 알림이 이전 날짜의 알림을 덮어쓰지 않습니다.
6. 완료되지 않은 입실·퇴실 기록으로는 완료 알림을 만들지 않습니다.

기기 테스트는 에뮬레이터에서만 실행됩니다. 자동 클릭을 끄고 일치하지 않는 SSID 및 가상 기록으로 검사했으며, 실제 출석 요청이나 Chrome 클릭은 수행하지 않았습니다. 테스트 종료 후 원래 설정을 복원하고 테스트용 완료 알림을 정리한 것을 확인했습니다.

```shell
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w net.tacademy.attendance.test/net.tacademy.attendance.DeviceChecks
```

## 화면 확인

- 설정의 결과 알림 ON/OFF 스위치가 항상 표시 안내로 바뀌었습니다.
- 휴대폰 알림 설정 버튼으로 해당 앱의 알림 설정이 열립니다.
- OS에서 앱 알림을 끄면 차단 안내를 표시합니다. 권한 변경으로 Android가 앱을 다시 시작한 경우에도 설정 탭에서 확인했습니다.
- OS에서 알림을 다시 허용하면 허용 상태로 갱신됩니다. 테스트 기기는 알림 허용 상태로 복구했습니다.
- [알림 설정 화면](screenshots/notifications.png)을 캡처했습니다.

## 동작 범위

화면이 켜져 있고 잠금이 풀린 상태에서는 기존 예약 조건에 따라 자동으로 Chrome 출석을 처리하도록 구현되어 있습니다. 수업일·시간대, 등록 Wi-Fi, Chrome 인증·접근성·자동 클릭·출석 예약과 필요한 권한을 충족해야 합니다. 실제 인증된 수강생 Chrome과 강의실 Wi-Fi에서의 출석은 여전히 실기기 확인이 필요합니다.

휴대폰에서 앱 알림이나 ‘입실·퇴실 결과’ 채널을 차단하면 앱이 강제로 알림을 표시할 수 없습니다. 사용자의 직접 삭제, OS의 알림 정리나 재부팅까지 차단하는 영구 고정 알림을 구현한 것은 아닙니다.

## 배포 파일

- `classroom-attendance-0.2.1-debug.apk`: 977,621 bytes
- versionName `0.2.1`, versionCode `4`
- SHA-256: `12348624d82becd9988d363e8ac0feabf0b65e772012acb7ebb7f8ff9ca23de3`
- 서명 인증서 SHA-256: `9f2973a9e4c72cdbc3304b649a62f48775ecd7b6b5542e9667e6d7711ffefc2a`
- 서명 검증 성공, v0.2.0과 동일한 인증서

APK와 체크섬 파일은 v0.2.1 GitHub 테스트 릴리스에 첨부합니다.
