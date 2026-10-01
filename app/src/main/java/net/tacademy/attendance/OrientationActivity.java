package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.ZonedDateTime;
import java.util.concurrent.*;

/** Bundled course document. Rendering stays offline and independent of attendance state. */
public final class OrientationActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean disposed;
    private volatile int generation;
    private File dataFile;
    private PdfRenderer renderer; // Worker-thread owned, including close.
    private SharedPreferences bookmarks;
    private HomeTheme theme;
    private PageView pageView;
    private TextView status;
    private Button previous,next,pageNumber;
    private int requested,pageCount;
    private AlertDialog jumpDialog;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Prefs prefs=new Prefs(this);
        theme=HomeTheme.at(ZonedDateTime.now(Policy.ZONE),prefs.end(),prefs.checkout());
        bookmarks=getSharedPreferences("course_documents",MODE_PRIVATE);
        requested=state!=null?state.getInt("page",0):bookmarks.getInt("orientation_page",0);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(theme.background);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);}
            else v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;
        });
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(button("‹","자료 닫기",this::finish),new LinearLayout.LayoutParams(dp(48),dp(52)));
        TextView title=text("오리엔테이션",21,theme.ink);title.setTypeface(null,Typeface.BOLD);title.setTag("orientation-title");bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button cover=button("처음","첫 페이지로 이동",()->showPage(0));cover.setTag("orientation-cover");bar.addView(cover,new LinearLayout.LayoutParams(dp(64),dp(48)));root.addView(bar);
        TextView subtitle=text("SK플래닛 · AI 데이터 과학자 1기",12,theme.muted);subtitle.setPadding(dp(20),0,dp(20),dp(8));root.addView(subtitle);
        pageView=new PageView(this);pageView.setTag("orientation-page");pageView.setBackgroundColor(theme.soft);root.addView(pageView,new LinearLayout.LayoutParams(-1,0,1));
        status=text("자료를 여는 중…",12,theme.muted);status.setGravity(Gravity.CENTER);status.setPadding(dp(12),dp(8),dp(12),0);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);status.setTag("orientation-status");root.addView(status);
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(12),dp(6),dp(12),dp(4));nav.setGravity(Gravity.CENTER_VERTICAL);
        previous=button("‹ 이전","이전 페이지",()->showPage(requested-1));previous.setTag("orientation-prev");nav.addView(previous,new LinearLayout.LayoutParams(0,dp(48),1));
        pageNumber=button("— / —","페이지 번호로 이동",this::jump);pageNumber.setTag("orientation-number");nav.addView(pageNumber,new LinearLayout.LayoutParams(0,dp(48),1));
        next=button("다음 ›","다음 페이지",()->showPage(requested+1));next.setTag("orientation-next");nav.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(nav);
        TextView hint=text("두 손가락으로 확대 · 두 번 눌러 확대/축소",11,theme.muted);hint.setGravity(Gravity.CENTER);hint.setPadding(dp(8),0,dp(8),dp(12));root.addView(hint);
        setContentView(root);updateNavigation();
        if(Build.VERSION.SDK_INT>=30&&getWindow().getInsetsController()!=null)getWindow().getInsetsController().setSystemBarsAppearance(theme==HomeTheme.NIGHT?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        worker.execute(()->{
            ParcelFileDescriptor descriptor=null;
            try{
                // Each activity owns its file so rotation cannot race with an older renderer.
                dataFile=File.createTempFile("orientation-",".pdf",getCacheDir());
                try(InputStream in=getAssets().open("orientation.pdf");OutputStream out=new FileOutputStream(dataFile)){byte[] buffer=new byte[32768];int read;while((read=in.read(buffer))!=-1)out.write(buffer,0,read);}
                descriptor=ParcelFileDescriptor.open(dataFile,ParcelFileDescriptor.MODE_READ_ONLY);
                renderer=new PdfRenderer(descriptor);descriptor=null;
                int total=renderer.getPageCount();
                runOnUiThread(()->{if(disposed)return;pageCount=total;showPage(Math.max(0,Math.min(requested,total-1)));});
            }catch(Exception e){if(descriptor!=null)try{descriptor.close();}catch(IOException ignored){}runOnUiThread(()->{if(!disposed)status.setText("자료를 열지 못했어요. 뒤로 갔다가 다시 열어 주세요.");});}
        });
    }
    private void updateNavigation(){previous.setEnabled(pageCount>0&&requested>0);next.setEnabled(pageCount>0&&requested<pageCount-1);pageNumber.setEnabled(pageCount>0);pageNumber.setText(pageCount==0?"— / —":(requested+1)+" / "+pageCount);}
    private void showPage(int index){
        if(disposed||pageCount==0)return;
        requested=Math.max(0,Math.min(index,pageCount-1));int target=requested,ticket=++generation;
        updateNavigation();status.setText((target+1)+"쪽을 여는 중…");
        // Never label the previous page as the new one while rendering.
        pageView.replace(null);pageView.setContentDescription("자료를 불러오는 중");
        worker.execute(()->{
            if(disposed||ticket!=generation)return;
            Bitmap bitmap=null;
            try(PdfRenderer.Page page=renderer.openPage(target)){
                float factor=Math.min(2.5f,2560f/Math.max(page.getWidth(),page.getHeight()));
                bitmap=Bitmap.createBitmap(Math.max(1,Math.round(page.getWidth()*factor)),Math.max(1,Math.round(page.getHeight()*factor)),Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                Bitmap rendered=bitmap;bitmap=null;
                runOnUiThread(()->{
                    if(disposed||ticket!=generation){rendered.recycle();return;}
                    pageView.replace(rendered);pageView.setContentDescription("오리엔테이션 "+(target+1)+"쪽. 두 손가락으로 확대할 수 있어요.");
                    status.setText("오프라인 자료 · 마지막 읽은 페이지 자동 저장");
                    bookmarks.edit().putInt("orientation_page",target).apply();
                });
            }catch(Exception|OutOfMemoryError e){if(bitmap!=null)bitmap.recycle();runOnUiThread(()->{if(!disposed&&ticket==generation)status.setText("이 페이지를 열지 못했어요. 페이지 번호를 눌러 다시 열어 주세요.");});}
        });
    }
    private void jump(){
        if(pageCount==0)return;
        EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setSingleLine(true);input.setText(String.valueOf(requested+1));input.selectAll();input.setTag("orientation-jump-input");input.setContentDescription("이동할 페이지 번호");
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(24),dp(8),dp(24),0);box.addView(input,new LinearLayout.LayoutParams(-1,-2));
        jumpDialog=new AlertDialog.Builder(this).setTitle("페이지 이동 · 1–"+pageCount+"쪽").setView(box).setNegativeButton("취소",null).setPositiveButton("이동",null).create();
        jumpDialog.setOnShowListener(d->jumpDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{int page;try{page=Integer.parseInt(input.getText().toString());}catch(NumberFormatException e){page=0;}if(page<1||page>pageCount){input.setError("1–"+pageCount+" 사이로 입력해 주세요");return;}showPage(page-1);jumpDialog.dismiss();}));jumpDialog.show();
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putInt("page",requested);super.onSaveInstanceState(state);}
    @Override protected void onDestroy(){
        disposed=true;generation++;if(jumpDialog!=null)jumpDialog.dismiss();if(pageView!=null)pageView.replace(null);
        worker.execute(()->{if(renderer!=null){renderer.close();renderer=null;}if(dataFile!=null)dataFile.delete();});worker.shutdown();super.onDestroy();
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button button(String label,String description,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(theme.ink);b.setContentDescription(description);GradientDrawable bg=new GradientDrawable();bg.setColor(theme.surface);bg.setCornerRadius(dp(14));b.setBackground(bg);b.setOnClickListener(v->action.run());return b;}

    /** One bounded bitmap at a time; all bitmap swaps and drawing run on the UI thread. */
    static final class PageView extends View {
        private Bitmap bitmap;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private float scale,fit,x,y,lastX,lastY;private boolean pinching;
        private final ScaleGestureDetector zoom;private final GestureDetector taps;
        PageView(Context c){super(c);setClickable(true);zoom=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){scaleAt(scale*detector.getScaleFactor(),detector.getFocusX(),detector.getFocusY());return true;}
        });taps=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){return true;}@Override public boolean onDoubleTap(MotionEvent e){scaleAt(scale>fit*1.2f?fit:fit*3,e.getX(),e.getY());return true;}});}
        void replace(Bitmap next){Bitmap old=bitmap;bitmap=next;reset();invalidate();if(old!=null&&old!=next)old.recycle();}
        private void reset(){if(bitmap==null||getWidth()==0||getHeight()==0)return;fit=Math.min((float)getWidth()/bitmap.getWidth(),(float)getHeight()/bitmap.getHeight());scale=fit;x=(getWidth()-bitmap.getWidth()*scale)/2;y=(getHeight()-bitmap.getHeight()*scale)/2;}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){reset();}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(bitmap==null)return;canvas.save();canvas.translate(x,y);canvas.scale(scale,scale);canvas.drawBitmap(bitmap,0,0,paint);canvas.restore();}
        private void scaleAt(float next,float focusX,float focusY){if(bitmap==null||scale==0)return;next=Math.max(fit,Math.min(fit*8,next));float ratio=next/scale;x=focusX-(focusX-x)*ratio;y=focusY-(focusY-y)*ratio;scale=next;bound();invalidate();}
        private void bound(){if(bitmap==null)return;float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;x=w<=getWidth()?(getWidth()-w)/2:Math.max(getWidth()-w,Math.min(0,x));y=h<=getHeight()?(getHeight()-h)/2:Math.max(getHeight()-h,Math.min(0,y));}
        @Override public boolean onTouchEvent(MotionEvent e){
            zoom.onTouchEvent(e);taps.onTouchEvent(e);
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();pinching=false;}
            if(e.getPointerCount()>1)pinching=true;
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&e.getPointerCount()==1&&!zoom.isInProgress()&&!pinching){x+=e.getX()-lastX;y+=e.getY()-lastY;bound();invalidate();}
            lastX=e.getX();lastY=e.getY();if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
