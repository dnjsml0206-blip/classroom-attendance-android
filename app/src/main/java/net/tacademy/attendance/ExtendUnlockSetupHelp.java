package net.tacademy.attendance;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.*;

/** Samsung menu illustrations based on the supplied device screens. */
final class ExtendUnlockSetupHelp {
    static void addSteps(Activity a,LinearLayout body){
        AccessibilitySetupHelp.step(a,body,"1  설정 → 잠금화면 및 AOD","설정 첫 화면이 열리면 아래로 내려서 찾아요.",new String[]{"설정","잠금화면 및 AOD  ›"},1);
        AccessibilitySetupHelp.step(a,body,"2  잠금 해제 유지 선택","PIN·패턴·비밀번호를 요청하면 직접 입력해요.",new String[]{"화면 잠금 및 생체 인식","잠금 해제 유지  ›","보안 잠금 상세설정"},1);
        AccessibilitySetupHelp.step(a,body,"3  신뢰할 수 있는 장소 선택","강의실 위치를 추가하고 지도에서 위치를 확인해 저장하세요.",new String[]{"신체 활동 감지","신뢰할 수 있는 장소  ›","신뢰할 수 있는 기기"},1);
        TextView note=new TextView(a);note.setText("설정 후 앱으로 돌아와 ‘신뢰 설정을 직접 확인했어요’를 눌러 주세요. 앱은 신뢰 장소 등록 여부를 직접 확인할 수 없어요.\n\n앱의 강의실 위치·Wi-Fi 등록과는 별도 설정이에요. 기종에 따라 ‘Extend Unlock’ 또는 ‘Smart Lock’으로 표시될 수 있어요.");note.setTextSize(14);note.setTextColor(0xFF536963);body.addView(note);
    }
    static void show(Activity a){
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(20*a.getResources().getDisplayMetrics().density);body.setPadding(pad,pad/2,pad,pad);
        addSteps(a,body);ScrollView scroll=new ScrollView(a);scroll.addView(body);
        new AlertDialog.Builder(a).setTitle("잠금 해제 유지 설정하는 법").setView(scroll)
            .setPositiveButton("휴대폰 설정 열기",(d,w)->PhoneSettings.open(a,PhoneSettings.Target.EXTEND)).setNegativeButton("닫기",null).show();
    }
}
