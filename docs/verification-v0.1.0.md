# v0.1.0 검증 기록

## 자동 검사

- 날짜: 2026-09-10
- Android Gradle Plugin: 9.1.1
- Gradle Wrapper: 9.3.1
- JDK: 21
- compileSdk / targetSdk: 36 / 36
- minSdk: 26 (Android 8.0)
- 실행: `gradlew.bat assembleDebug testDebugUnitTest lintDebug --no-daemon`
- JVM 테스트: 12개, 실패 0개
- Android Lint: 오류 0개, 경고 1개 (고정된 Gradle 버전보다 최신 버전 존재)
- APK: 테스트용 debug 서명

## 설치 및 화면

- Android 15 / API 35, Pixel 6 에뮬레이터에 APK 설치 및 앱 시작 성공
- 기본 TACADEMY_5G 표시 확인
- UI로 CODEX_TEST_WIFI 추가, 저장, 앱 재실행 후 유지 및 삭제 확인
- 테스트 좌표와 반경을 UI에서 등록하고 저장 확인
- 감시 시작 버튼으로 위치 포그라운드 서비스 시작 확인
- 정확한 알람 권한을 허용한 상태에서 퇴실 예약 저장 확인
- 가상 GPS 도착 후 지정 Wi-Fi 미연결 알림 표시 확인 (위치 갱신 간격에 따른 지연 관찰)
- AndroidRuntime 크래시 로그 없음
- 테스트용 좌표와 기기 데이터는 APK 또는 저장소에 포함하지 않음

설정 화면 테스트는 실제 Android UI로 수행했다. 에뮬레이터 키보드 전환 중 발생한 테스트 입력 타이밍 문제는 화면 상태를 확인한 뒤 다시 입력해 검증했다.
위치 갱신에서 최소 이동 거리 조건을 제거해 정지 상태에서도 새 위치를 받을 수 있도록 보완했다.

## 실제 서비스 검증 범위

공개 페이지의 요청 경로와 JavaScript 분기는 읽기 전용으로 확인했다.
본인의 인증 계정이 연결되어 있지 않아 실제 입실·퇴실 변경은 수행하지 않았다.
실제 강의실 Wi-Fi, GPS 정확도, OEM 절전 정책과 인증 링크의 동작은 휴대폰에서 추가 확인해야 한다.
