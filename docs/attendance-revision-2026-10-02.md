# v0.9.14 · 오늘 화면, 출결률, 누적 휴가

## 오늘 화면

- Chrome 처리 안내 문장 삭제.
- 출결 기록 확인은 입실 완료 등 현재 상태 옆으로 이동.
- 달력에서 출결 조회는 오늘 출결 제목 옆의 연한 버튼으로 이동.

## 계산

현재 출결률 분모는 한국 시각 기준 종료된 수업일이다. 오늘은 정상 수업 18:00, 6시간 수업 16:00, 7시간 수업 17:00에 포함한다. 퇴실 실행 여유 시각과 구분한다. 사용자 요청에 따라 기록 없는 지난 수업은 출석으로 계산하고 시각은 생성하지 않는다. 실제 지각 입실이 확인된 부분 기록은 퇴실 시각이 없어도 지각을 보존한다.

지각·조퇴 합산을 3으로 나눈 몫을 결석으로 환산한다. 현재율은 `(종료 수업일 - 결석 - 환산 결석) / 종료 수업일`. 전체 허용 결석은 `총수업일 / 5`의 정수 몫이다. 추가 결석과 지각·조퇴 횟수는 각각의 최대치이며 합해서 사용할 수 없다. 남은 수업일 수를 상한으로 두고 이미 80% 기준을 넘으면 0이다. 미완료 오늘은 현재율에 아직 포함하지 않는다.

## 달력·휴가

지각/조퇴는 독립 토글이다. 기존 LATE_EARLY 저장 기록은 두 토글로 표시하여 보존한다. 둘 다 끄면 출석 보정으로 저장해 원래 시각의 자동 분류가 다시 켜지지 않게 한다. 출석/결석/휴가는 지각/조퇴와 배타적이다.

휴가는 월 1일 누적되며 출석 인정이다. 기본 지급 기준은 수업월 시작일(8/31, 9/30, 10/31…)이며 출결 관리에서 한 달 수료 후 또는 매달 1일로 바꿀 수 있다. 수업 기간 밖과 비수업일에는 새 휴가를 등록하지 않는다. 미래 휴가 예약이 가능하며 해당 날짜까지의 누적 지급과 이후 예약도 검증한다. 부여/사용/예약/잔여를 표시하고 취소하면 반환한다. 미사용분은 과정 기간 안에서 누적한다.

휴가일은 출결 계산의 수업일에는 포함하지만 자동 출결·알림 예약에서 제외한다. 오늘 휴가 취소 시 현재 시간대의 감시를 재검토한다. 휴가 등록은 기관 신청이 아닌 앱 기록이다. 기존 입·퇴실 시각은 보존한다.

## 잠금과 설치 문의

v0.9.14 검토 당시 완료 후 잠금은 AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN이다. 이후 사용자가 PIN 1회 해제를 수락해 v0.9.15에서 전원 메뉴 잠금 모드를 추가했다. 자세한 동작은 [잠금 모드 문서](lockdown-after-attendance-2026-10-02.md)를 참고한다. 화면 꺼짐과 isKeyguardLocked / isDeviceLocked / isDeviceSecure 상태를 별도 기록한다. Extend Unlock 자체를 해제하는 API는 일반 앱에 제공되지 않는다. DevicePolicyManager.lockNow는 다음 해제에 PIN/패턴/비밀번호를 요구하므로 지문만으로 해제하려는 요청에 맞지 않는다. 단순 화면 꺼짐을 신뢰 해지로 단정하지 않는다.

- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#GLOBAL_ACTION_LOCK_SCREEN
- https://developer.android.com/reference/android/app/admin/DevicePolicyManager#lockNow()
- https://developer.android.com/work/versions/android-10#device-admin-deprecation

설치 경고는 Android/Play Protect/Samsung Auto Blocker의 영역이다. 경고를 숨기거나 보안 기능을 끄지 않는다. 향후 Google Play 내부 테스트 배포를 검토할 수 있으나 현재 debug 인증서에서 변경하려면 기록 백업과 업데이트 호환성 설계가 먼저다. 이번 APK 서명은 유지한다.

- https://support.google.com/googleplay/answer/2812853?hl=ko
- https://www.samsung.com/uk/support/mobile-devices/protect-your-galaxy-device-with-the-new-auto-blocker-feature/
- https://support.google.com/googleplay/android-developer/answer/9845334?hl=en
- https://developer.android.com/studio/publish/app-signing

## 검증

단위 테스트 164개, 최종 에뮬레이터 새 기능 20개·달력 24개·출결 관리 13개(합계 57개) 통과. 최초 전체 빌드 lint 오류 0/경고 10. 최종 UI 수정 후 APK 재빌드 및 화면 검사 완료. 실제 출결 서버 요청 없이 검증했다. 첫 캡처를 가린 에뮬레이터 System UI 응답 지연 창은 닫고 최종 캡처를 다시 확인했다.

v0.9.14 / versionCode 29. 기존 업데이트 인증서 일치. APK SHA-256: `35898e4db9ebe7d397527607b5f3a0350e6105567b03a174d344cd8106387983`.
