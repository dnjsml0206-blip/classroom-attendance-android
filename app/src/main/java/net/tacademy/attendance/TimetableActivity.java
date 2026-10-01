package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Offline, date-first view of the supplied curriculum. This screen never submits attendance. */
public final class TimetableActivity extends Activity {
    private Prefs prefs;private HomeTheme theme;private LocalDate selected;private YearMonth month;
    private LinearLayout content;private ScrollView scroll;private boolean outline;private AlertDialog sourcePdf;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=()->render(false);
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener changes=(p,k)->{if(k==null||Set.of("days","excluded","checkout").contains(k)){handler.removeCallbacks(refresh);handler.post(refresh);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=new Prefs(this);theme=HomeTheme.at(ZonedDateTime.now(Policy.ZONE),prefs.end(),prefs.checkout());
        try{selected=LocalDate.parse(state!=null?state.getString("selected"):getIntent().getStringExtra("initial_date"));}catch(RuntimeException e){selected=LocalDate.now(Policy.ZONE);}
        selected=CourseTimetable.clamp(selected);month=YearMonth.from(selected);outline=state!=null&&state.getBoolean("outline");
        LinearLayout root=column();root.setBackgroundColor(theme.background);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);}else v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),dp(6),dp(12),dp(6));
        bar.addView(button("‹","시간표 닫기",this::finish),new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=text("수업 시간표",22,theme.ink,true);title.setTag("timetable-title");bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button today=button("오늘","오늘 수업으로 이동",()->{selected=CourseTimetable.clamp(LocalDate.now(Policy.ZONE));month=YearMonth.from(selected);outline=false;render(true);});today.setTag("timetable-today");bar.addView(today,new LinearLayout.LayoutParams(dp(58),dp(48)));root.addView(bar);
        scroll=new ScrollView(this);scroll.setFillViewport(true);content=column();content.setPadding(dp(16),dp(8),dp(16),dp(24));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        if(Build.VERSION.SDK_INT>=30&&getWindow().getInsetsController()!=null)getWindow().getInsetsController().setSystemBarsAppearance(theme==HomeTheme.NIGHT?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        render(true);
    }
    @Override protected void onResume(){super.onResume();prefs.raw.registerOnSharedPreferenceChangeListener(changes);render(false);}
    @Override protected void onPause(){prefs.raw.unregisterOnSharedPreferenceChangeListener(changes);handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(sourcePdf!=null)sourcePdf.dismiss();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("selected",selected.toString());state.putBoolean("outline",outline);super.onSaveInstanceState(state);}
    private void render(boolean top){
        int position=top?0:scroll.getScrollY();content.removeAllViews();
        TextView course=text("AI캠퍼스 · AI 데이터 과학자 1기",13,theme.muted,false);content.addView(course);
        TextView period=text("2026.08.31 — 2027.02.26   ·   120일 / 940시간",12,theme.muted,false);period.setTag("timetable-period");content.addView(period);gap(content,12);
        LinearLayout tabs=new LinearLayout(this);String[] labels={"월간 시간표","전체 과정"};
        for(int i=0;i<2;i++){final boolean target=i==1;Button b=button(labels[i],labels[i],()->{outline=target;render(true);});boolean active=outline==target;b.setSelected(active);b.setBackground(shape(active?theme.button:theme.surface,14,active?theme.accent:theme.border));b.setTextColor(active?theme.ink:theme.muted);b.setTag(target?"timetable-outline":"timetable-monthly");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);if(i==1)lp.leftMargin=dp(8);tabs.addView(b,lp);}content.addView(tabs);gap(content,14);
        if(outline)renderOutline();else{renderSelected();renderCalendar();}
        Button source=button("원본 PDF · 안내  ↗","원본 과정명세서와 시간표 안내",this::sourceInfo);source.setTag("timetable-source");content.addView(source,new LinearLayout.LayoutParams(-1,dp(48)));
        scroll.post(()->scroll.scrollTo(0,position));
    }
    private boolean userOff(LocalDate day){return prefs.userExcluded().contains(day.toString())||!prefs.days().contains(day.getDayOfWeek().getValue());}
    private String offReason(LocalDate day){
        if(!CourseCalendar.dayOff(day).isEmpty())return CourseCalendar.dayOff(day);
        if(prefs.userExcluded().contains(day.toString()))return "추가로 등록한 휴강일";
        if(day.getDayOfWeek().getValue()>5)return "주말 · 수업 없음";
        if(!prefs.days().contains(day.getDayOfWeek().getValue()))return "설정상 수업 요일이 아니에요";
        return "예정된 수업이 없어요";
    }
    private void renderSelected(){
        CourseTimetable.Lesson lesson=CourseTimetable.on(selected);boolean active=lesson!=null&&!userOff(selected);
        LinearLayout card=card();card.setTag("timetable-selected");
        String date=selected.format(DateTimeFormatter.ofPattern("M월 d일 EEEE",Locale.KOREAN));if(selected.equals(LocalDate.now(Policy.ZONE)))date+=" · 오늘";
        TextView day=text(date,13,theme.muted,true);day.setTag("timetable-selected-date");card.addView(day);
        TextView name=text(active?lesson.title():offReason(selected),24,active?color(lesson.category()):theme.ink,true);name.setTag("timetable-subject");card.addView(name);
        TextView hours=text(active?lesson.category().label+"   ·   "+lesson.hours()+"시간":"쉬어가는 날",13,theme.muted,false);hours.setTag("timetable-hours");card.addView(hours);
        if(lesson!=null&&!active)card.addView(text("원본 시간표: "+lesson.title()+" · "+lesson.hours()+"시간",12,theme.muted,false));
        if(active){
            TextView attendance=text(CourseSchedule.shortened(selected)?"단축수업 · "+CourseSchedule.classEnd(selected)+" 종료\n자동 퇴실 "+Policy.checkoutStartTime(selected,prefs.checkout())+"부터":"자동 퇴실 "+Policy.checkoutStartTime(selected,prefs.checkout())+"부터 · 기본 설정",13,theme.ink,true);
            attendance.setTag("timetable-checkout");card.addView(attendance);
            if(!CourseSchedule.shortened(selected))card.addView(text("시작·종료 시각은 원본에 기재되어 있지 않아요.",11,theme.muted,false));
        }
        var next=CourseTimetable.DAYS.tailMap(selected,false).values().stream().filter(d->!userOff(d.date())).findFirst();
        if(!active&&next.isPresent()){var n=next.get();Button b=button("다음 수업  "+n.date().format(DateTimeFormatter.ofPattern("M/d"))+" · "+n.shortTitle()+"  ›","다음 수업으로 이동",()->select(n.date()));card.addView(b,new LinearLayout.LayoutParams(-1,dp(48)));}
    }
    private void renderCalendar(){
        LinearLayout box=card();box.setPadding(dp(8),dp(10),dp(8),dp(12));LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);
        Button prev=button("‹","이전 달",()->move(-1));prev.setTag("timetable-prev");prev.setEnabled(month.isAfter(YearMonth.from(CourseCalendar.START)));nav.addView(prev,new LinearLayout.LayoutParams(dp(44),dp(48)));
        TextView title=text(month.format(DateTimeFormatter.ofPattern("yyyy년 M월")),20,theme.ink,true);title.setTag("timetable-month");title.setGravity(Gravity.CENTER);nav.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button next=button("›","다음 달",()->move(1));next.setTag("timetable-next");next.setEnabled(month.isBefore(YearMonth.from(CourseCalendar.END)));nav.addView(next,new LinearLayout.LayoutParams(dp(44),dp(48)));box.addView(nav);
        var lessons=CourseTimetable.month(month);TextView summary=text("원본 기준 "+lessons.size()+"일 · "+lessons.stream().mapToInt(CourseTimetable.Lesson::hours).sum()+"시간",12,theme.muted,false);summary.setGravity(Gravity.CENTER);summary.setTag("timetable-month-summary");box.addView(summary);gap(box,8);
        LinearLayout names=new LinearLayout(this);for(String n:new String[]{"일","월","화","수","목","금","토"}){TextView t=text(n,11,theme.muted,true);t.setGravity(Gravity.CENTER);names.addView(t,new LinearLayout.LayoutParams(0,dp(28),1));}box.addView(names);
        var cells=MonthAttendance.cells(month);
        for(int start=0;start<cells.size();start+=7){LinearLayout week=new LinearLayout(this);
            for(int j=start;j<start+7;j++){LocalDate d=cells.get(j);View cell;
                if(d==null)cell=new View(this);else{
                    var l=CourseTimetable.on(d);boolean inside=!d.isBefore(CourseCalendar.START)&&!d.isAfter(CourseCalendar.END),active=l!=null&&!userOff(d),chosen=d.equals(selected),today=d.equals(LocalDate.now(Policy.ZONE));
                    LinearLayout tile=column();tile.setGravity(Gravity.CENTER);tile.setPadding(dp(1),dp(3),dp(1),dp(3));tile.setTag("timetable-day:"+d);
                    tile.setBackground(shape(chosen?theme.soft:theme.surface,12,chosen?theme.accent:today?theme.border:theme.surface));
                    TextView num=text(String.valueOf(d.getDayOfMonth()),16,inside?theme.ink:theme.muted,chosen||today);num.setGravity(Gravity.CENTER);tile.addView(num);
                    String label=!inside?"":active?l.shortTitle():!CourseCalendar.dayOff(d).isEmpty()||prefs.userExcluded().contains(d.toString())?"휴강":d.getDayOfWeek().getValue()>5?"—":"비수업";
                    TextView subject=text(label,9,active?color(l.category()):theme.muted,false);subject.setMaxLines(1);subject.setEllipsize(TextUtils.TruncateAt.END);subject.setGravity(Gravity.CENTER);tile.addView(subject,new LinearLayout.LayoutParams(-1,-2));
                    if(active&&CourseSchedule.shortened(d)){TextView shortDay=text(CourseSchedule.classEnd(d)+" 종료",8,theme.accent,true);shortDay.setGravity(Gravity.CENTER);tile.addView(shortDay);}
                    tile.setAlpha(inside?1:.35f);tile.setSelected(chosen);tile.setEnabled(inside);tile.setContentDescription(d+" "+(active?l.title()+" "+l.hours()+"시간":offReason(d))+(chosen?" 선택됨":""));
                    if(inside){tile.setOnClickListener(v->select(d));tile.setFocusable(true);}cell=tile;
                }
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(80),1);lp.setMargins(dp(1),dp(2),dp(1),dp(2));week.addView(cell,lp);
            }box.addView(week);
        }
        LinearLayout legend=new LinearLayout(this);legend.setGravity(Gravity.CENTER);for(var category:CourseTimetable.Category.values()){TextView label=text("● "+category.label,10,color(category),false);label.setPadding(dp(6),dp(8),dp(6),0);legend.addView(label);}box.addView(legend);
        TextView note=text("날짜를 누르면 위에 수업이 표시돼요. 추가 휴강·수업 요일 설정도 반영합니다.",11,theme.muted,false);box.addView(note);
    }
    private void renderOutline(){
        content.addView(text("과목별 흐름을 한눈에",20,theme.ink,true));content.addView(text("과목을 누르면 해당 수업 날짜로 이동해요.",12,theme.muted,false));gap(content,12);
        int index=0;for(var module:CourseTimetable.modules()){
            LinearLayout row=card();row.setTag("timetable-module:"+module.title());row.setFocusable(true);row.setClickable(true);
            row.addView(text(String.format(Locale.KOREAN,"%02d   %s",++index,module.category().label),11,color(module.category()),true));row.addView(text(module.title(),19,theme.ink,true));
            String a=module.first().format(DateTimeFormatter.ofPattern("M.d")),b=module.last().format(DateTimeFormatter.ofPattern("M.d"));
            row.addView(text(a+(a.equals(b)?"":" – "+b)+"   ·   "+module.lessons().size()+"일 / "+module.hours()+"시간",12,theme.muted,false));
            row.setContentDescription(module.title()+" "+module.lessons().size()+"일 "+module.hours()+"시간. 날짜 보기");
            row.setOnClickListener(v->{LocalDate today=LocalDate.now(Policy.ZONE);LocalDate target=module.lessons().stream().map(CourseTimetable.Lesson::date).filter(d->!d.isBefore(today)).findFirst().orElse(module.first());outline=false;select(target);});
        }
        content.addView(text("기간 표시는 첫 수업부터 마지막 수업까지이며, 주말·휴강일과 중간의 다른 과목은 시수에서 제외합니다.",11,theme.muted,false));gap(content,10);
    }
    private void select(LocalDate day){selected=CourseTimetable.clamp(day);month=YearMonth.from(selected);render(true);}
    private void move(int delta){YearMonth next=month.plusMonths(delta);if(next.isBefore(YearMonth.from(CourseCalendar.START))||next.isAfter(YearMonth.from(CourseCalendar.END)))return;select(next.atDay(Math.min(selected.getDayOfMonth(),next.lengthOfMonth())));}
    private void sourceInfo(){new AlertDialog.Builder(this).setTitle("과정명세서 기준 시간표")
        .setMessage("AI캠퍼스 AI 데이터 과학자 1기\n2026.08.31–2027.02.26 · 120일 · 940시간\n\n공휴일과 원본의 9/23·2/5 휴강을 제외했습니다. 추가로 등록한 휴강일에는 원래 과목도 함께 표시합니다. 월별 시수는 해당 날짜의 실제 월 기준입니다.\n\n원본에는 교시별 시작·종료 시각이 없습니다. 단축수업은 요청하신 전체 기간 기준으로 6시간 수업 16시 종료·15:50:02 퇴실, 7시간 수업 17시 종료·16:50:02 퇴실을 적용합니다. 수업 변경은 원본 시간표에 자동 반영되지 않습니다.")
        .setNegativeButton("닫기",null).setPositiveButton("원본 PDF 보기",(d,w)->showPdf()).show();}
    private void showPdf(){
        // Render the bundled one-page original only when requested; there is no network access.
        LinearLayout body=column();ProgressBar loading=new ProgressBar(this);body.addView(loading);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("원본 과정명세서").setView(body).setPositiveButton("닫기",null).create();
        sourcePdf=dialog;final Bitmap[] bitmap={null};dialog.setOnDismissListener(d->{body.removeAllViews();if(bitmap[0]!=null){bitmap[0].recycle();bitmap[0]=null;}});dialog.show();
        new Thread(()->{Bitmap rendered=null;try{
            File file=new File(getCacheDir(),"course-specification.pdf");try(InputStream in=getAssets().open("course-specification.pdf");OutputStream out=new FileOutputStream(file)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd);PdfRenderer.Page page=renderer.openPage(0)){
                rendered=Bitmap.createBitmap(page.getWidth()*3,page.getHeight()*3,Bitmap.Config.ARGB_8888);rendered.eraseColor(Color.WHITE);page.render(rendered,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            }
            Bitmap ready=rendered;runOnUiThread(()->{if(isFinishing()||isDestroyed()||!dialog.isShowing()){ready.recycle();return;}bitmap[0]=ready;body.removeAllViews();body.addView(text("두 손가락으로 확대 · 끌어서 이동",12,0xFF666666,false));body.addView(new PdfImage(this,ready),new LinearLayout.LayoutParams(-1,Math.min(dp(520),getResources().getDisplayMetrics().heightPixels*2/3)));});
        }catch(Exception e){if(rendered!=null)rendered.recycle();runOnUiThread(()->{if(dialog.isShowing()){body.removeAllViews();body.addView(text("원본을 열지 못했어요. 창을 닫고 다시 시도해 주세요.",14,theme.muted,false));}});}},"timetable-pdf").start();
    }
    private static final class PdfImage extends View {
        private final Bitmap bitmap;private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);private float scale,minScale,x,y,lastX,lastY;private final ScaleGestureDetector pinch;private final GestureDetector taps;
        PdfImage(Context c,Bitmap b){super(c);bitmap=b;setBackgroundColor(Color.WHITE);setContentDescription("원본 시간표. 두 손가락으로 확대하고 드래그로 이동");
            pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){zoom(d.getScaleFactor(),d.getFocusX(),d.getFocusY());return true;}});
            taps=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(android.view.MotionEvent e){return true;}@Override public boolean onDoubleTap(android.view.MotionEvent e){zoom(scale>minScale*1.5f?minScale/scale:3,e.getX(),e.getY());return true;}});
        }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){minScale=Math.min((float)w/bitmap.getWidth(),(float)h/bitmap.getHeight());scale=minScale;bound();}
        private void zoom(float factor,float cx,float cy){float next=Math.max(minScale,Math.min(minScale*8,scale*factor));x=cx-(cx-x)*next/scale;y=cy-(cy-y)*next/scale;scale=next;bound();invalidate();}
        private void bound(){float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;x=w<=getWidth()?(getWidth()-w)/2:Math.max(getWidth()-w,Math.min(0,x));y=h<=getHeight()?(getHeight()-h)/2:Math.max(getHeight()-h,Math.min(0,y));}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(bitmap.isRecycled())return;c.save();c.translate(x,y);c.scale(scale,scale);c.drawBitmap(bitmap,0,0,paint);c.restore();}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){pinch.onTouchEvent(e);taps.onTouchEvent(e);if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();}else if(e.getActionMasked()==android.view.MotionEvent.ACTION_MOVE){if(!pinch.isInProgress()&&e.getPointerCount()==1){x+=e.getX()-lastX;y+=e.getY()-lastY;bound();invalidate();}lastX=e.getX();lastY=e.getY();}else if(e.getActionMasked()==android.view.MotionEvent.ACTION_UP)performClick();return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
    private int color(CourseTimetable.Category c){boolean night=theme==HomeTheme.NIGHT;return switch(c){case STUDY->night?0xFFA7CFF5:0xFF315F7C;case PROJECT->night?0xFF98D7BD:0xFF276B54;case SPECIAL->night?0xFFF2C78C:0xFF99602E;};}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){LinearLayout l=column();l.setPadding(dp(16),dp(12),dp(16),dp(12));l.setBackground(shape(theme.surface,20,theme.border));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);content.addView(l,lp);return l;}
    private TextView text(String value,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);t.setPadding(0,dp(3),0,dp(3));t.setLineSpacing(dp(2),1);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private Button button(String value,String description,Runnable action){Button b=new Button(this);b.setText(value);b.setTextSize(13);b.setAllCaps(false);b.setContentDescription(description);b.setTextColor(theme.accent);b.setMinWidth(0);b.setMinimumWidth(0);b.setStateListAnimator(null);b.setPadding(dp(6),dp(4),dp(6),dp(4));b.setBackground(shape(theme.soft,12,theme.soft));b.setOnClickListener(v->action.run());return b;}
    private GradientDrawable shape(int color,int radius,int stroke){GradientDrawable s=new GradientDrawable();s.setColor(color);s.setCornerRadius(dp(radius));s.setStroke(dp(1),stroke);return s;}
    private void gap(LinearLayout p,int n){View space=new View(this);p.addView(space,new LinearLayout.LayoutParams(1,dp(n)));}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
