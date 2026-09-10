# v0.4.1 검증 기록

2026-09-11 · Android 15/API 35 에뮬레이터 · JDK 21 · SDK 36

## 변경 사항

잠금 상태 테스트에서 방식을 선택하면 준비·실행·성공 확인과 방식별 참고 사항을 표시합니다. ‘처음 한 번 확인할 설정’에 공통 권한 준비, 조회 모드, 알림을 누르지 않고 기다리기, 결과 기록 방법을 추가했습니다.

## 확인 결과

- 단위 테스트 38개 통과
- 기존 잠금 메뉴 Android 검사 12개 통과
- lint: 오류 0개, 기존 Gradle 버전 경고 1개
- APK와 테스트 APK 빌드 성공
- v0.4.0이 설치된 에뮬레이터에서 업데이트 설치 성공
- 실제 앱에서 7가지 방법을 차례로 선택해 준비·실행·성공 확인 문구가 각각 바뀌는 것을 확인
- 공통 도움말 열기와 안내 표시 확인
- 실제 출결 스위치 기본 OFF 및 서버 진단에서 비활성 표시 확인

화면 안내 변경에 대해 기존 검사를 실행하고 실제 UI를 확인했습니다. 이번 버전에서는 실제 Extend Unlock 신뢰 상태나 강의실 출결을 시험하지 않았습니다. 제조사별 신뢰 동작과 실제 출결 여부는 휴대폰에서 확인해야 합니다.

## 실제 APK 화면

- [화면 깨우기](screenshots/lock-guide-wake.png)
- [Extend Unlock](screenshots/lock-guide-extend.png)
- [Bluetooth 기기](screenshots/lock-guide-device.png)
- [신뢰 장소](screenshots/lock-guide-place.png)
- [신체 활동](screenshots/lock-guide-body.png)
- [시스템 인증](screenshots/lock-guide-auth.png)
- [서버 연결 진단](screenshots/lock-guide-network.png)
- [처음 한 번 확인할 설정](screenshots/lock-guide-first-help.png)

## 설치 파일

- classroom-attendance-0.4.1-debug.apk: 4,356,101 bytes
- versionName 0.4.1 / versionCode 7
- SHA-256: 30765c0d909b745eeceee5afdb68ad2b65730b72242ad777825888abe5ba28fe
- 서명 인증서 SHA-256: 9f2973a9e4c72cdbc3304b649a62f48775ecd7b6b5542e9667e6d7711ffefc2a
- v0.4.0과 동일한 서명. APK와 체크섬을 테스트 릴리스에 첨부
