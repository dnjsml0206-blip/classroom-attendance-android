package net.tacademy.attendance;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Shared visual presentation only: it never launches Chrome or changes attendance. */
final class ProcessingPage extends ScrollView {
    private final TextView message;
    ProcessingPage(Context c,Policy.Phase phase,String title,String hint,String action,Runnable pressed){
        super(c);setFillViewport(true);setClipToPadding(false);setTag("processing-page");
        HomeTheme theme=phase==Policy.Phase.CHECKOUT?HomeTheme.NIGHT:HomeTheme.WARM;
        setBackgroundColor(theme.background);setClickable(true);
        if(c instanceof android.app.Activity activity)post(()->{
            Window window=activity.getWindow();
            window.setStatusBarColor(theme.background);window.setNavigationBarColor(theme.background);
            if(android.os.Build.VERSION.SDK_INT>=30){WindowInsetsController bars=window.getInsetsController();if(bars!=null){int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;bars.setSystemBarsAppearance(theme==HomeTheme.NIGHT?0:mask,mask);}}
            else window.getDecorView().setSystemUiVisibility(theme==HomeTheme.NIGHT?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        });
        LinearLayout outer=new LinearLayout(c);outer.setOrientation(LinearLayout.VERTICAL);outer.setGravity(Gravity.CENTER);
        outer.setPadding(dp(28),dp(40),dp(28),dp(40));addView(outer,new LayoutParams(-1,-2));
        LinearLayout body=new LinearLayout(c);body.setOrientation(LinearLayout.VERTICAL);body.setGravity(Gravity.CENTER);
        outer.addView(body,new LinearLayout.LayoutParams(-1,-2));
        TextView brand=label("ATTENDANCE MATE",11,theme.accent,true);brand.setLetterSpacing(.18f);body.addView(brand);space(body,28);
        FrameLayout picture=new FrameLayout(c);picture.setBackground(shape(theme.soft,28));picture.setClipToOutline(true);
        ImageView artwork=new ImageView(c);artwork.setImageResource(phase==Policy.Phase.CHECKOUT?R.drawable.hero_night:R.drawable.hero_warm_stand_on);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        picture.addView(artwork,new FrameLayout.LayoutParams(-1,-1));body.addView(picture,new LinearLayout.LayoutParams(-1,dp(190)));space(body,24);
        TextView greeting=label(phase==Policy.Phase.CHECKOUT?"오늘도 배움으로 채운 하루":"새로운 배움을 시작하는 아침",13,theme.accent,true);body.addView(greeting);space(body,12);
        TextView heading=label(title,27,theme.ink,true);heading.setTag("processing-title");body.addView(heading);space(body,16);
        LinearLayout progressRow=new LinearLayout(c);progressRow.setGravity(Gravity.CENTER);progressRow.setPadding(dp(16),dp(10),dp(16),dp(10));progressRow.setBackground(shape(theme.soft,22));
        ProgressBar progress=new ProgressBar(c);progress.setIndeterminateTintList(ColorStateList.valueOf(theme.accent));progress.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        progressRow.addView(progress,new LinearLayout.LayoutParams(dp(18),dp(18)));
        TextView state=label(title.equals("출결 준비 중")?"화면과 잠금 상태를 확인하고 있어요":phase==Policy.Phase.CHECKOUT?"퇴실 완료 시각을 확인하고 있어요":"입실 완료 시각을 확인하고 있어요",12,theme.ink,false);
        LinearLayout.LayoutParams stateParams=new LinearLayout.LayoutParams(-2,-2);stateParams.leftMargin=dp(8);progressRow.addView(state,stateParams);body.addView(progressRow);space(body,18);
        message=label(hint,14,theme.muted,false);message.setTag("processing-message");message.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(message);space(body,26);
        Button button=new Button(c);button.setText(action);button.setTextSize(14);button.setAllCaps(false);button.setTextColor(theme.ink);button.setBackground(shape(theme.surface,16));
        button.setMinHeight(dp(48));button.setPadding(dp(24),dp(10),dp(24),dp(10));button.setStateListAnimator(null);button.setTag("processing-action");
        body.addView(button,new LinearLayout.LayoutParams(-2,-2));button.setOnClickListener(v->pressed.run());
    }
    void setMessage(String value){message.setText(value);}
    private TextView label(String value,int size,int color,boolean bold){TextView t=new TextView(getContext());t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);t.setLineSpacing(dp(3),1);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private void space(LinearLayout parent,int size){parent.addView(new View(getContext()),new LinearLayout.LayoutParams(1,dp(size)));}
    private GradientDrawable shape(int color,int radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
