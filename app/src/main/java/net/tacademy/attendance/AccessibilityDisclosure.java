package net.tacademy.attendance;
import android.app.*;
/** Explicit, separate accessibility disclosure before opening Android's grant screen. */
final class AccessibilityDisclosure {
 static void show(Activity a,Runnable proceed){
  new AlertDialog.Builder(a).setTitle("Chrome 접근성 사용 동의")
   .setMessage("출석 메이트는 AccessibilityService API로 Chrome 출결 화면의 주소·텍스트·버튼을 읽습니다. 이름·과정명이 화면에 포함될 수 있으며, 출결 날짜와 입실·퇴실 시각을 읽어 휴대폰에 기록합니다.\n\n자동 처리를 선택하면 지정된 시간·Wi-Fi 조건 또는 직접 누른 실행 버튼에 따라 입실·퇴실·확인 버튼을 대신 누릅니다. 자동출석을 꺼도 접근성이 켜져 있으면 화면에 표시된 출결 기록을 읽어 저장합니다.\n\n사용자가 요청한 설정 이동과 출결 후 잠금에는 Android 설정·잠금 화면의 버튼 정보와 터치·길게 누르기·화면 잠금 동작을 사용합니다. PIN·비밀번호·쿠키를 추출하지 않습니다.\n\n일반 배포판은 읽은 화면 내용과 출결 기록을 개발자 서버로 전송하거나 광고에 사용하지 않습니다. 동의하지 않아도 Chrome에서 직접 출결할 수 있습니다. Android 접근성 설정에서 언제든 끌 수 있습니다.")
   .setPositiveButton("동의하고 설정 열기",(d,w)->{new Prefs(a).raw.edit().putInt("accessibility_disclosure_version",1).apply();proceed.run();})
   .setNegativeButton("동의하지 않음",null).show();
 }
}
