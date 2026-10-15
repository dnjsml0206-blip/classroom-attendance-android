package net.tacademy.attendance;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

/** Illustrations of the Samsung settings path; grants always remain user-controlled. */
final class AccessibilitySetupHelp {
    private static final int INK=0xFF183631, TEAL=0xFF007E7A;
    static void show(Activity a){
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(a,20),dp(a,8),dp(a,20),dp(a,16));
        body.addView(text(a,"갤럭시 화면 기준 · 기종에 따라 메뉴 이름이 다를 수 있어요.",13,false));
        step(a,body,"1  접근성 → 설치된 앱","‘접근성 바로가기’ 바로 아래에 있어요.",new String[]{"접근성 바로가기","설치된 앱  ›"},1);
        step(a,body,"2  출석 메이트 선택","설치된 앱 목록에서 아래 항목을 눌러요.",new String[]{"출석 메이트 · Chrome 출석  ›"},0);
        step(a,body,"3  맨 위 스위치 켜기","권한 안내를 읽고 허용하면 ‘사용 중’으로 바뀌어요.",new String[]{"사용 중                         ● 켜짐","출석 메이트 · Chrome 출석 바로가기"},0);
        body.addView(text(a,"아래 ‘바로가기’ 스위치는 켜지 않아도 돼요. 앱으로 돌아오면 Chrome 접근성의 ‘완료’를 확인하세요.",14,false));
        ScrollView scroll=new ScrollView(a);scroll.addView(body);
        new AlertDialog.Builder(a).setTitle("Chrome 접근성 설정하는 법").setView(scroll)
            .setPositiveButton("접근성 설정 열기",(d,w)->PhoneSettings.open(a,PhoneSettings.Target.ACCESSIBILITY))
            .setNegativeButton("닫기",null).show();
    }
    static void step(Activity a,LinearLayout body,String title,String hint,String[] lines,int selected){
        TextView heading=text(a,title,16,true);heading.setPadding(0,dp(a,18),0,dp(a,4));body.addView(heading);body.addView(text(a,hint,13,false));
        LinearLayout picture=new LinearLayout(a);picture.setOrientation(LinearLayout.VERTICAL);picture.setPadding(dp(a,12),dp(a,12),dp(a,12),dp(a,12));picture.setBackground(bg(a,0xFFF1F3F5,0));
        LinearLayout.LayoutParams box=new LinearLayout.LayoutParams(-1,-2);box.setMargins(0,dp(a,8),0,dp(a,12));body.addView(picture,box);
        for(int i=0;i<lines.length;i++){
            TextView row=text(a,lines[i],14,i==selected);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinHeight(dp(a,48));row.setPadding(dp(a,10),dp(a,10),dp(a,10),dp(a,10));
            row.setBackground(bg(a,i==selected?0xFFE2F3EF:0xFFFFFFFF,i==selected?TEAL:0));if(i==selected)row.setTextColor(TEAL);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(a,3),0,dp(a,3));picture.addView(row,lp);
        }
        picture.setContentDescription(title+" 안내 그림");
    }
    private static TextView text(Activity a,String value,int size,boolean bold){TextView v=new TextView(a);v.setText(value);v.setTextSize(size);v.setTextColor(INK);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private static GradientDrawable bg(Activity a,int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,12));if(stroke!=0)d.setStroke(dp(a,2),stroke);return d;}
    private static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
}
