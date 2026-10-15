package net.tacademy.attendance;

import android.app.*;
import android.content.Intent;
import android.net.Uri;

/** Shown before location permission prompts; cancelling never opens system settings. */
final class LocationDisclosure {
    static final String PRIVACY="https://dnjsml0206-blip.github.io/classroom-attendance-android/privacy/";
    static final String MESSAGE="출석 메이트는 앱을 닫거나 사용하지 않는 동안에도 예약된 출결 시간에 위치 정보와 Wi-Fi 연결 정보를 이용합니다.\n\n등록한 강의실 주변의 Wi-Fi 미연결 알림, 퇴실 미확인 상태의 강의실 이탈 알림, 출결용 Wi-Fi 확인에 사용합니다. 출결 완료 또는 확인 시간 종료 후에는 감시를 중단합니다.\n\n강의실 좌표와 설정은 휴대폰에 저장하며, 위치 좌표를 개발자 서버로 전송하거나 광고에 사용하지 않습니다. 권한을 허용하지 않아도 Chrome에서 직접 출결할 수 있습니다.";
    static void show(Activity activity,Runnable proceed){
        new AlertDialog.Builder(activity).setTitle("백그라운드 위치 사용 안내").setMessage(MESSAGE)
            .setPositiveButton("계속",(d,w)->proceed.run()).setNegativeButton("나중에",null)
            .setNeutralButton("개인정보 처리방침",(d,w)->activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(PRIVACY))))
            .show();
    }
}
