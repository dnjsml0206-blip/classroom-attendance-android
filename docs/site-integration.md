# Chrome 출석 연동

출결 화면: https://skplanet.atosoft.net/Worknet/Present/Present.asp
인증 시작: https://skplanet.atosoft.net/Worknet/Present/PAuthWay.asp

사용자가 제공한 HTTP 인증 주소는 HTTPS에서도 응답함을 읽기 전용으로 확인했다. 사용자는 앱 내부에서 새로 인증할 때 훈련생 인증 오류를 보고했으며, v0.2.0은 기존 휴대폰 Chrome의 인증을 사용한다.

앱은 쿠키 추출 또는 WebView 세션 공유를 시도하지 않는다. Chrome을 열고 접근성 노드에서 다음 공개 페이지 구조를 사용한다.

- 주소 표시줄: 지정 호스트와 /Worknet/Present/Present.asp 경로
- 출결일: YYYY-MM-DD
- 입실 버튼: 입실
- 퇴실 버튼: 퇴실
- 완료 표시: 입실시간 : HH:mm[:ss], 퇴실시간 : HH:mm[:ss]
- 퇴실 확인: 정말로 퇴실 하시겠습니까?

공개 JavaScript에서 버튼 클릭 후 화면을 새로고침하고 기록 시각을 표시하는 구조를 확인했다. 앱은 Chrome UI를 통해서만 출석하며 직접 출석 변경 엔드포인트를 호출하지 않는다.

실제 인증된 Chrome과 강의실 Wi-Fi에서 버튼 접근성 및 기록 인식 확인은 추가로 필요하다. 페이지 구조·주소 표시 또는 언어가 달라져 확인할 수 없으면 자동 클릭을 보류하고 사용자 확인을 요청한다.
