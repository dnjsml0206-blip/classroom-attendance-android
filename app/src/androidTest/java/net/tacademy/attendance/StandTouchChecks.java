package net.tacademy.attendance;
import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import java.util.*;
/** Real attached-view touch dispatch, with attendance disabled and no attendance callback. */
final class StandTouchChecks {
    private final Instrumentation test;private int checks;private Activity activity;private Context context;private Map<String,?> saved;
    StandTouchChecks(Instrumentation test){this.test=test;}
    void run(){Bundle result=new Bundle();try{
        if(!Build.HARDWARE.equals("ranchu")&&!Build.HARDWARE.equals("goldfish"))throw new AssertionError("Emulator only");
        context=test.getTargetContext();Prefs prefs=new Prefs(context);saved=new HashMap<>(prefs.raw.getAll());
        main(()->{prefs.raw.edit().putBoolean("enabled",false).putBoolean("auto",false).commit();AlarmScheduler.cancel(context);context.stopService(new Intent(context,MonitorService.class));});
        activity=test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        for(HomeTheme theme:HomeTheme.values())for(int widthDp:new int[]{280,366,412}){
            float density=context.getResources().getDisplayMetrics().density;int width=Math.round(widthDp*density),height=Math.round(144*density);
            int[] clicked={0};StandSceneView[] scene={null};
            main(()->{
                scene[0]=new StandSceneView(activity,theme,()->{clicked[0]++;scene[0].setLit(!scene[0].isLit());});scene[0].setLit(false);
                FrameLayout stage=new FrameLayout(activity);stage.addView(scene[0],new FrameLayout.LayoutParams(width,height));activity.setContentView(stage);
            });
            View target=scene[0].findViewWithTag("reservation-stand");Rect before=new Rect();target.getHitRect(before);
            check(before.width()>=47*density&&before.height()>=47*density&&before.left>=0&&before.right<=width&&before.top>=0&&before.bottom<=height,"Visible stand target at "+theme+" / "+widthDp);
            main(()->tap(scene[0],width*.25f,height*.5f));check(clicked[0]==0&&!scene[0].isLit(),"Background does not toggle at "+theme+" / "+widthDp);
            int off=scene[0].imageResource();main(()->tap(scene[0],before.exactCenterX(),before.exactCenterY()));
            check(clicked[0]==1&&scene[0].isLit()&&scene[0].imageResource()!=off,"Stand touch changes light at "+theme+" / "+widthDp);
            Rect after=new Rect();target.getHitRect(after);check(before.equals(after),"Light swap preserves target geometry at "+theme+" / "+widthDp);
            main(()->tap(scene[0],after.exactCenterX(),after.exactCenterY()));check(clicked[0]==2&&!scene[0].isLit(),"Second stand touch turns OFF at "+theme+" / "+widthDp);
        }
        restore();result.putString("stream","PASS: "+checks+" stand touch and lighting checks. No attendance work.\n");test.finish(Activity.RESULT_OK,result);
    }catch(Throwable e){try{restore();}catch(Throwable ignored){}result.putString("stream","FAIL: "+e+"\n");test.finish(Activity.RESULT_CANCELED,result);}}
    private void main(Runnable run){Throwable[] error={null};test.runOnMainSync(()->{try{run.run();}catch(Throwable e){error[0]=e;}});test.waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private void tap(View v,float x,float y){long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_UP,x,y,0);try{v.dispatchTouchEvent(down);v.dispatchTouchEvent(up);}finally{down.recycle();up.recycle();}}
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;Bundle progress=new Bundle();progress.putString("stream","PASS: "+label+"\n");test.sendStatus(0,progress);}
    @SuppressWarnings("unchecked")private void restore(){if(context==null||saved==null)return;main(()->{if(activity!=null)activity.finish();var edit=new Prefs(context).raw.edit().clear();for(var item:saved.entrySet()){Object value=item.getValue();String key=item.getKey();if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof Set)edit.putStringSet(key,new HashSet<>((Set<String>)value));}edit.commit();AlarmScheduler.arm(context,false);});}
}
