# v0.4.0 검증 기록

2026-09-10 · Android 15/API 35 에뮬레이터 · JDK 21 · SDK 36 · Gradle 9.3.1

## 변경 사항

설정 맨 위에 일회성 잠금 상태 테스트 메뉴를 추가했습니다. 7가지 방식을 선택하고 15·30·60초 예약 또는 즉시 실행할 수 있습니다. 자동/알림 수동 진입, 잠금 상태와 Chrome 주소 표시를 구분해 기록하며 사용자 확인 결과·메모와 이전 10개 기록을 복사할 수 있습니다.

테스트 상태는 출석 설정과 별도 저장합니다. 기존 완료 알림 ID와 다른 테스트 알림을 사용합니다. 실제 출결 시도는 기본 OFF이며, ON이어도 정상 인증 뒤 기존 출결 조건을 충족한 경우에만 연결합니다. 화면 시작은 일반 Activity/접근성 서비스 문맥으로 시도하며 전체 화면 알림 권한이나 잠금 해제 우회 권한은 추가하지 않았습니다.

서버 직접 인증/API는 미연동입니다. 네트워크 진단은 등록 Wi-Fi에서 인증 시작 페이지에 GET을 한 번 보내고 HTTPS 응답 코드만 확인하며, Chrome 쿠키나 응답 본문을 저장하지 않습니다. 미등록 Wi-Fi에서는 요청을 보류합니다.

## 자동 검증

- 단위 테스트 38개 통과: 기존 30개와 잠금 테스트 정책 8개
- 새 Android 임시 메뉴 검증 12개 통과
- 기존 Android 예약·완료 알림 검증 12개 통과
- 린트 오류 0개 / 기존 Gradle 버전 안내 경고 1개
- 앱 APK·테스트 APK 빌드와 이전 앱 위 업데이트 설치 성공

정책 테스트: 화면 깨우기만으로 잠금 해제하지 않음, 꺼진 화면에서 Chrome 보류, 신뢰 방식 4종의 인증 필요 상태 보류, 신뢰 상태의 시스템 해제 요청, 시스템 인증 요청, 이미 해제된 상태, 잠금을 유지하는 네트워크 경로, 취소·이전 실행·만료 토큰 차단.

Android 테스트: 메뉴 진입, 조회 시험 동안 출결 클릭 보류, 취소/보류 해제, 취소된 알람 무시, 실제 출결 ON의 카운트다운도 보류, 네트워크 방식 실제 출결 비활성화, 이전 콜백 무시, Wi-Fi 조건 위반 시 네트워크 요청 보류, 사용자 결과/메모 보존, 만료 종료, 재부팅/업데이트 후 재실행 차단, 원래 출결 설정 보존.

```shell
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e suite locks net.tacademy.attendance.test/net.tacademy.attendance.DeviceChecks
adb shell am instrument -w net.tacademy.attendance.test/net.tacademy.attendance.DeviceChecks
```

자동 검사에는 불일치 SSID를 사용했고 실제 출결 변경이나 서버 진단 요청은 수행하지 않았습니다. 검사 후 원래 설정과 테스트 알림을 복원/정리했습니다.

## 화면을 실제로 끈 상태의 에뮬레이터 실험

1. **보안 잠금이 없고 화면만 꺼진 상태**: 예약에서 자동 진입 → 화면 켜짐 → Chrome 출결 주소 표시 확인. 이 결과로 실제 Extend Unlock의 활성화를 증명하지는 않습니다.
2. **새 PIN 인증이 필요한 상태에서 Extend Unlock 선택**: 자동으로 테스트 화면에 진입했지만 인증 필요를 감지하여 Chrome 실행을 보류했습니다.
3. **시스템 인증 선택**: Android System UI의 실제 PIN 입력 화면이 표시됐고 정상 인증 완료 콜백 후 Chrome 출결 주소 표시를 확인했습니다.

이 실험은 임시 에뮬레이터 PIN을 사용했으며 종료 후 PIN을 제거하고 원래 잠금 방식·접근성 설정을 복원했습니다. 인증된 실제 수강생 계정을 사용하지 않았고 입실·퇴실 버튼을 클릭하지 않았습니다. 화면 확인은 설치한 앱의 기본 조회 모드로 수행했습니다.

실제 Extend Unlock의 Bluetooth·장소·신체 활동 감지, 휴대폰에서 제공하는 지문·얼굴 인증, 제조사별 절전/백그라운드 제한, 강의실의 인증된 Chrome 출결 및 HTTPS 응답은 사용자 휴대폰에서 추가 확인해야 합니다.

## 화면

- [설정의 임시 메뉴](screenshots/settings-lab.png)
- [테스트 메뉴](screenshots/lock-lab.png)
- [7가지 테스트 방법](screenshots/lock-methods.png)
- [신뢰 설정과 결과 기록](screenshots/lock-trust-settings.png)

## 배포 파일

- classroom-attendance-0.4.0-debug.apk: 4,423,909 bytes
- versionName 0.4.0 / versionCode 6
- SHA-256: 153247f5707fc6989e8f3b6a6c706c252f7422fc545c757f585fd78d8ef8b8ce
- 서명 인증서 SHA-256: 9f2973a9e4c72cdbc3304b649a62f48775ecd7b6b5542e9667e6d7711ffefc2a
- 이전 v0.3.0과 같은 서명으로 검증 성공

APK와 SHA256SUMS-v0.4.0.txt를 비공개 GitHub 테스트 릴리스에 첨부합니다.

[사용법·공식 Android 근거](lock-screen-lab.md)