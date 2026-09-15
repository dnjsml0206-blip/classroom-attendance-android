package net.tacademy.attendance;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** A decorative status lamp. Its only input is the saved reservation switch, never the monitor. */
@android.annotation.SuppressLint("ViewConstructor") // Constructed with the current Today theme, never inflated from XML.
final class ReservationLampView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final HomeTheme theme;
    private boolean lit;
    private final Shader halo, beam;
    private final Shader[] bases=new Shader[2], shades=new Shader[2];
    ReservationLampView(Context context,HomeTheme theme) {
        super(context);this.theme=theme;
        boolean night=theme==HomeTheme.NIGHT;
        halo=new RadialGradient(53,46,45,new int[]{night?0x55FFD585:0x33E9AD53,0x00FFD585},null,Shader.TileMode.CLAMP);
        beam=new LinearGradient(52,43,52,83,night?0x70FFD993:0x65F6CA7C,0x00FFD993,Shader.TileMode.CLAMP);
        for(int i=0;i<2;i++){
            boolean on=i==1;
            bases[i]=new LinearGradient(0,76,0,85,night?(on?0xFFD8BA85:0xFF879AAE):(on?0xFFCAA06C:0xFFB8A99A),night?(on?0xFF927855:0xFF536B81):(on?0xFF9A6F47:0xFF958578),Shader.TileMode.CLAMP);
            shades[i]=new LinearGradient(31,20,77,42,night?(on?0xFFFFDEA0:0xFF9AAAAF):(on?0xFFFCE0AC:0xFFD7C9B9),night?(on?0xFFCA9450:0xFF5A7188):(on?0xFFD8A163:0xFFA99480),Shader.TileMode.CLAMP);
        }
        // The adjacent text announces the state; the illustration must not duplicate it or act as a switch.
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setContentDescription("출석 예약 전등 꺼짐");
    }
    void setLit(boolean value) {
        if(lit==value)return;
        lit=value;setContentDescription(value?"출석 예약 전등 켜짐":"출석 예약 전등 꺼짐");invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale=Math.min(getWidth()/100f,getHeight()/90f);
        canvas.save();canvas.translate((getWidth()-100*scale)/2,(getHeight()-90*scale)/2);canvas.scale(scale,scale);
        boolean night=theme==HomeTheme.NIGHT;
        if(lit) {
            shader(halo);
            canvas.drawCircle(53,46,45,paint);paint.setShader(null);
            path.reset();path.moveTo(36,42);path.lineTo(68,42);path.lineTo(87,80);path.quadTo(52,87,17,80);path.close();
            shader(beam);
            canvas.drawPath(path,paint);paint.setShader(null);
        }
        color(night?0x28101928:0x18734D32);canvas.drawOval(22,81,82,88,paint);
        // Stem and oval foot keep the soft, rounded furniture shape of the hero illustrations.
        paint.setStrokeWidth(5);paint.setStrokeCap(Paint.Cap.ROUND);color(night?(lit?0xFFCCAA77:0xFF718498):(lit?0xFFA57442:0xFFA99B8F));
        canvas.drawLine(53,43,53,78,paint);
        shader(bases[lit?1:0]);
        canvas.drawOval(29,76,77,85,paint);paint.setShader(null);
        color(night?(lit?0xFFF3D5A1:0xFF98A8B8):(lit?0xFFE9C289:0xFFC2B4A6));canvas.drawOval(36,77,70,80,paint);
        // A linen shade with a softly curved edge, warm amber when lit and muted when off.
        path.reset();path.moveTo(41,15);path.quadTo(53,11,65,15);path.lineTo(76,42);path.quadTo(53,50,30,42);path.close();
        shader(shades[lit?1:0]);
        canvas.drawPath(path,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);color(night?(lit?0xFFDDBA7A:0xFF8395A7):(lit?0xFFC59A64:0xFFB49C83));canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
        color(lit?(night?0xFFFFEDBE:0xFFFFEDC3):(night?0xFF495E72:0xFF9D8976));canvas.drawOval(30,39,76,47,paint);
        if(lit){color(0xFFFFF5D9);canvas.drawOval(42,41,64,46,paint);}
        color(lit?0x66FFF5DA:0x22FFFFFF);canvas.drawRoundRect(42,19,45,34,1.5f,1.5f,paint);
        canvas.restore();
    }
    private void shader(Shader value){paint.setColor(Color.WHITE);paint.setShader(value);}
    private void color(int value){paint.setColor(value);}
}
