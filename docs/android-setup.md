# Android 설정과 제약

아래 내용은 2026-09-10에 확인한 공식 문서와 현재 설계를 기준으로 한다.
실제 설정 이름과 지원 범위는 휴대폰 모델, Android 및 One UI 버전에 따라 확인한다.

## Wi-Fi 켜기와 자동 연결

Android 10 이상을 대상으로 하는 일반 앱은 Wi-Fi를 직접 켜거나 끌 수 없다.

기본 설정은 사용자가 강의실 네트워크를 저장하고 자동 연결을 켜는 방식이다.
갤럭시에서 지원된다면 모드 및 루틴에 강의실 주변 도착 → Wi-Fi 켜기를 등록할 수 있다.
이것은 연결 성공을 보장하지 않으므로 앱은 이후 지정 네트워크 연결을 확인해야 한다.

앱에서 네트워크를 추가해야 한다면 사용자 승인 기반 저장 네트워크 추가 또는 네트워크 제안 API를 검토한다.
네트워크 제안은 OS가 선택하는 후보이며 즉시 강제 연결 기능으로 설명하지 않는다.
인터넷용 출석에 기기 간 연결용 Wi-Fi 요청 API를 무조건 적용하지 않는다.

- [Android Wi-Fi 제어 제한](https://developer.android.com/about/versions/10/privacy/changes#enable-disable-wifi)
- [Wi-Fi 인프라 API](https://developer.android.com/develop/connectivity/wifi/wifi-infrastructure)
- [네트워크 제안 API](https://developer.android.com/develop/connectivity/wifi/wifi-suggest)
- [삼성 위치 기반 Wi-Fi 루틴 예시](https://insights.samsung.com/2019/04/02/10-tips-to-get-the-most-out-of-your-new-galaxy-s10/)

## 위치

- 정확한 위치 및 백그라운드 위치 권한이 필요한 기능을 구분한다.
- 최초 위치 설정은 지도 선택 또는 현재 위치 등록 방식으로 구현할 예정이다.
- 실내 위치 오차와 지오펜스 이벤트 지연을 처리한다.
- 위치 확인 불가는 외부에 있음과 다르게 표시한다.
- 필요 없는 이동 경로를 지속 수집하지 않는다.

[Android 지오펜스 문서](https://developer.android.com/develop/sensors-and-location/location/geofencing)

## Wi-Fi 식별

OS 버전과 사용하는 API에 따라 위치 또는 주변 Wi-Fi 기기 권한이 필요할 수 있다.
SSID가 숨겨지거나 확인 불가로 반환되는 경우 권한 오류를 안내하고 임의로 허용 네트워크로 취급하지 않는다.
필요한 권한은 구현 API를 선정한 뒤 최소 범위로 결정한다.

## 알림과 백그라운드 실행

- 알림 권한을 허용해야 미연결 상태와 처리 결과를 알림바에서 볼 수 있다.
- 지속 감시가 필요할 때에는 사용자에게 보이는 상태와 OS 실행 제한을 함께 고려한다.
- 제조사 배터리 제한과 앱 자동 종료 설정을 실제 기기에서 확인한다.
- 알림 권한 또는 감시 권한이 없는 상태를 앱 화면에서 보여 준다.

[포그라운드 서비스 문서](https://developer.android.com/develop/background-work/services/fgs)

## 17:50 예약

정확한 알람 접근 권한을 확인한 뒤 수업일마다 단발 예약을 생성하는 방향으로 설계한다.
권한 거부·철회 시 예약이 정상인 것처럼 표시하지 않는다.
재부팅 및 시각 변경 후 재예약하고, 과거 예약은 허용된 지연 시간 범위에서만 복구한다.

알람 실행과 서버 출석 기록 완료는 별개다. 전원 종료, 강제 중지, 통신 장애에서는 완료를 보장할 수 없다.

[Android 알람 문서](https://developer.android.com/develop/background-work/services/alarms)

## 로그인

WebView는 Chrome과 상태를 자동 공유하지 않는다.
Chrome 기반 Custom Tabs는 브라우저 로그인 상태를 공유할 수 있지만, 그것만으로 앱의 임의 페이지 조작이나 백그라운드 출석 요청이 가능해지는 것은 아니다.
실제 앱 로그인과 출석 처리 방식은 사이트 확인 후 결정한다.

- [Chrome Custom Tabs와 WebView 차이](https://developer.chrome.com/docs/android/custom-tabs)
- [Android WebView CookieManager](https://developer.android.com/reference/android/webkit/CookieManager)
