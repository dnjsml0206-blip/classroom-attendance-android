package net.tacademy.attendance;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

/** The artwork is decorative; only its pictured desk stand is an interactive switch. */
final class StandSceneView extends FrameLayout {
    private final HomeTheme theme;
    private final ImageView artwork;
    private final View stand;
    private Boolean lit;
    private int imageResource;
    StandSceneView(Context context,HomeTheme theme,Runnable toggle){
        super(context);this.theme=theme;setTag("reservation-artwork");
        artwork=new ImageView(context);artwork.setScaleType(ImageView.ScaleType.MATRIX);
        artwork.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(artwork,new LayoutParams(-1,-1));
        stand=new View(context);stand.setTag("reservation-stand");stand.setFocusable(true);stand.setOnClickListener(v->toggle.run());
        stand.setAccessibilityDelegate(new AccessibilityDelegate(){
            @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Switch.class.getName());info.setCheckable(true);info.setChecked(Boolean.TRUE.equals(lit));}
        });
        addView(stand,new LayoutParams(1,1));
    }
    void setLit(boolean enabled){
        if(lit!=null&&lit==enabled)return;lit=enabled;
        imageResource=theme==HomeTheme.NIGHT?(enabled?R.drawable.hero_night:R.drawable.hero_night_stand_off):(enabled?R.drawable.hero_warm_stand_on:R.drawable.hero_warm_stand_off);
        artwork.setImageResource(imageResource);stand.setSelected(enabled);
        stand.setContentDescription(enabled?"출석 예약 스탠드 켜짐 · 눌러 끄기":"출석 예약 스탠드 꺼짐 · 눌러 켜기");
        requestLayout();
    }
    boolean isLit(){return Boolean.TRUE.equals(lit);}
    int imageResource(){return imageResource;}
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        super.onLayout(changed,left,top,right,bottom);Drawable asset=artwork.getDrawable();if(asset==null)return;
        int width=getWidth(),height=getHeight();float sourceWidth=asset.getIntrinsicWidth(),sourceHeight=asset.getIntrinsicHeight();
        float scale=Math.max(width/sourceWidth,height/sourceHeight),dx=(width-sourceWidth*scale)/2,dy=(height-sourceHeight*scale)*(theme==HomeTheme.WARM?.74f:.5f);
        RectF bounds=theme==HomeTheme.NIGHT?new RectF(.72f,.275f,.875f,.615f):new RectF(.797f,.33f,.966f,.795f);
        float margin=6*getResources().getDisplayMetrics().density;
        float minimumY=margin-bounds.top*sourceHeight*scale,maximumY=height-margin-bounds.bottom*sourceHeight*scale;
        if(minimumY<=maximumY)dy=Math.max(minimumY,Math.min(maximumY,dy));
        dy=Math.max(height-sourceHeight*scale,Math.min(0,dy));
        Matrix matrix=new Matrix();matrix.setScale(scale,scale);matrix.postTranslate(dx,dy);artwork.setImageMatrix(matrix);
        // Source-space stand silhouettes, transformed by the exact same crop as the artwork.
        bounds.set(bounds.left*sourceWidth,bounds.top*sourceHeight,bounds.right*sourceWidth,bounds.bottom*sourceHeight);matrix.mapRect(bounds);
        float minimum=48*getResources().getDisplayMetrics().density;
        bounds.inset(-Math.max(0,(minimum-bounds.width())/2),-Math.max(0,(minimum-bounds.height())/2));
        stand.layout(Math.max(0,(int)bounds.left),Math.max(0,(int)bounds.top),Math.min(width,(int)Math.ceil(bounds.right)),Math.min(height,(int)Math.ceil(bounds.bottom)));
    }
}
