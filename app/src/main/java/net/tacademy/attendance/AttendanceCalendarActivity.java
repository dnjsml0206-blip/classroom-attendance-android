package net.tacademy.attendance;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Observed attendance and editable annotations for past dates, with an inline detail panel. */
public final class AttendanceCalendarActivity extends Activity {
    private Prefs prefs;
    private HomeTheme theme;
    private YearMonth month;
    private LocalDate selected;
    private LinearLayout content;
    private ScrollView scroll;
    private AlertDialog manualDialog;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=this::render;
    private final SharedPreferences.OnSharedPreferenceChangeListener changes=(p,key)->{
        if(key==null||key.startsWith("record:")||key.startsWith("manual_attendance:")||Set.of("record_day","in","out","days","excluded").contains(key)){
            handler.removeCallbacks(refresh);handler.postDelayed(refresh,100);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);prefs=new Prefs(this);
        LocalDate today=LocalDate.now(Policy.ZONE);
        try { selected=LocalDate.parse(state!=null?state.getString("selected"):getIntent().getStringExtra("initial_date")); }
        catch(RuntimeException e){selected=today;}
        if(selected.getYear()<1||selected.getYear()>9999)selected=today;
        month=YearMonth.from(selected);
        try { theme=HomeTheme.valueOf(getIntent().getStringExtra("theme")); }
        catch(RuntimeException e){theme=HomeTheme.at(ZonedDateTime.now(Policy.ZONE),prefs.end(),prefs.checkout());}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(theme.background);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;
        });
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(8),dp(6),dp(12),dp(6));
        toolbar.addView(button("‹","출결 달력 닫기",this::finish),new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=text("월간 출결",22,theme.ink,true);toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(button("이번 달","이번 달과 오늘 선택",()->{selected=LocalDate.now(Policy.ZONE);month=YearMonth.from(selected);render();}),new LinearLayout.LayoutParams(dp(78),dp(48)));
        root.addView(toolbar);
        scroll=new ScrollView(this);scroll.setFillViewport(true);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(8),dp(16),dp(20));
        scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        if(Build.VERSION.SDK_INT>=30&&getWindow().getInsetsController()!=null)getWindow().getInsetsController().setSystemBarsAppearance(
            theme==HomeTheme.NIGHT?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        render();
    }
    @Override protected void onResume(){super.onResume();prefs.raw.registerOnSharedPreferenceChangeListener(changes);render();}
    @Override protected void onPause(){prefs.raw.unregisterOnSharedPreferenceChangeListener(changes);handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(manualDialog!=null)manualDialog.dismiss();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("selected",selected.toString());super.onSaveInstanceState(state);}
    private void moveMonth(int delta){month=month.plusMonths(delta);selected=month.atDay(Math.min(selected.getDayOfMonth(),month.lengthOfMonth()));render();}
    private void render(){
        if(content==null)return;
        int position=scroll.getScrollY();content.removeAllViews();
        LocalDate today=LocalDate.now(Policy.ZONE);Set<Integer> weekdays=prefs.days();Set<String> holidays=prefs.excluded();
        LinearLayout calendar=card();calendar.setPadding(dp(8),dp(12),dp(8),dp(12));
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        Button previous=button("‹","이전 달",()->moveMonth(-1));previous.setTag("calendar-prev");previous.setEnabled(month.getYear()>1||month.getMonthValue()>1);
        heading.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView label=text(month.format(DateTimeFormatter.ofPattern("yyyy년 M월",Locale.KOREAN)),21,theme.ink,true);label.setTag("calendar-month");label.setGravity(Gravity.CENTER);
        heading.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        Button next=button("›","다음 달",()->moveMonth(1));next.setTag("calendar-next");next.setEnabled(month.getYear()<9999||month.getMonthValue()<12);
        heading.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));calendar.addView(heading);
        int complete=0,early=0,absent=0,entered=0,unknown=0;
        List<LocalDate> dates=MonthAttendance.cells(month);
        for(LocalDate date:dates)if(date!=null){switch(MonthAttendance.state(date,today,prefs.history(date),prefs.manualAttendance(date),weekdays,holidays)){
            case COMPLETE, ATTENDED -> complete++;
            case EARLY_LEAVE -> early++;
            case ABSENT -> absent++;
            case ENTERED -> entered++;
            case UNKNOWN -> unknown++;
            default -> {}
        }}
        TextView summary=text(getString(R.string.calendar_summary,complete,early,absent,entered,unknown),12,theme.muted,false);summary.setTag("calendar-summary");summary.setGravity(Gravity.CENTER);calendar.addView(summary);
        if(dates.stream().filter(Objects::nonNull).anyMatch(date->date.isBefore(today)&&prefs.manualAttendance(date)!=null)){
            TextView legend=text("* 직접 입력한 출결",11,theme.muted,false);legend.setGravity(Gravity.CENTER);calendar.addView(legend);
        }
        gap(calendar,12);
        LinearLayout names=new LinearLayout(this);String[] dayNames={"일","월","화","수","목","금","토"};
        for(String name:dayNames){TextView day=text(name,12,theme.muted,true);day.setGravity(Gravity.CENTER);names.addView(day,new LinearLayout.LayoutParams(0,dp(30),1));}calendar.addView(names);
        for(int start=0;start<dates.size();start+=7){
            LinearLayout week=new LinearLayout(this);
            for(int index=start;index<start+7;index++){
                LocalDate date=dates.get(index);View cell;
                if(date==null)cell=new View(this);
                else {
                    Policy.Record record=prefs.history(date);MonthAttendance.State status=MonthAttendance.state(date,today,record,prefs.manualAttendance(date),weekdays,holidays);
                    LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(1),dp(5),dp(1),dp(5));
                    boolean chosen=date.equals(selected),isToday=date.equals(today);int color=color(status);
                    tile.setBackground(shape(chosen?theme.soft:theme.surface,chosen?theme.accent:isToday?theme.border:theme.surface,chosen||isToday?2:0,10));
                    TextView number=text(String.valueOf(date.getDayOfMonth()),16,chosen?theme.accent:theme.ink,chosen||isToday);number.setGravity(Gravity.CENTER);tile.addView(number);
                    TextView state=text(status.label+(date.isBefore(today)&&prefs.manualAttendance(date)!=null?"*":""),11,color,false);state.setGravity(Gravity.CENTER);state.setSingleLine(true);tile.addView(state);
                    tile.setTag("calendar-day:"+date);tile.setFocusable(true);tile.setClickable(true);tile.setContentDescription(date+" "+status.label+(date.isBefore(today)&&prefs.manualAttendance(date)!=null?" 직접 입력":"")+(isToday?" 오늘":"")+(chosen?" 선택됨":""));
                    tile.setOnClickListener(v->{selected=date;render();});cell=tile;
                }
                cell.setMinimumHeight(dp(62));
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.setMargins(dp(1),dp(2),dp(1),dp(2));week.addView(cell,params);
            }
            calendar.addView(week);
        }
        gap(content,14);
        Policy.Record record=prefs.history(selected);
        MonthAttendance.ManualStatus manual=selected.isBefore(today)?prefs.manualAttendance(selected):null;
        MonthAttendance.State state=MonthAttendance.state(selected,today,record,manual,weekdays,holidays);
        LinearLayout detail=card();detail.setTag("calendar-detail");
        TextView date=text(selected.format(DateTimeFormatter.ofPattern("M월 d일 EEEE",Locale.KOREAN)),18,theme.ink,true);date.setTag("calendar-selected-date");detail.addView(date);
        TextView stateLabel=text(state==MonthAttendance.State.ENTERED?"입실 확인 · 퇴실 미확인":state.label,13,color(state),true);stateLabel.setTag("calendar-selected-status");detail.addView(stateLabel);gap(detail,12);
        LinearLayout times=new LinearLayout(this);
        times.addView(time("입실",record==null?"":record.in(),"calendar-in"),new LinearLayout.LayoutParams(0,-2,1));
        times.addView(time("퇴실",record==null?"":record.out(),"calendar-out"),new LinearLayout.LayoutParams(0,-2,1));detail.addView(times);
        if(manual!=null){
            TextView source=text("직접 입력 · 앱에 저장한 출결 상태",12,theme.accent,true);source.setTag("calendar-source");detail.addView(source);
            detail.addView(text("입실·퇴실 시간은 사이트에서 확인한 기록입니다.",12,theme.muted,false));
        }
        if(state==MonthAttendance.State.UNKNOWN)detail.addView(text("앱에서 확인된 입실·퇴실 기록이 없습니다.",12,theme.muted,false));
        else if(state==MonthAttendance.State.HOLIDAY)detail.addView(text("등록된 휴강일입니다.",12,theme.muted,false));
        else if(state==MonthAttendance.State.NO_CLASS)detail.addView(text("현재 설정한 수업 요일에 포함되지 않습니다.",12,theme.muted,false));
        else if(state==MonthAttendance.State.PLANNED)detail.addView(text("현재 설정에 따른 수업 예정일입니다.",12,theme.muted,false));
        if(selected.isBefore(today)){
            gap(detail,12);LocalDate editDate=selected;
            Button edit=button(manual==null?"출결 직접 기록":"직접 입력 수정","선택한 날짜 출석·조퇴·결석 기록",()->editAttendance(editDate));
            edit.setTag("calendar-edit");detail.addView(edit,new LinearLayout.LayoutParams(-1,dp(48)));
        } else detail.addView(text("직접 입력은 지난 날짜에 할 수 있습니다.",12,theme.muted,false));
        gap(content,12);content.addView(text("지난 날짜를 눌러 앱 사용 전 출결도 직접 기록하세요. 직접 입력은 앱에만 저장되며 사이트 출결은 변경하지 않습니다. 미확인은 결석을 뜻하지 않습니다.",12,theme.muted,false));
        gap(content,10);content.addView(button("Chrome 출결 화면 열기","Chrome 출결 화면 열기",()->ChromeAttendanceService.openChrome(this,SiteProtocol.PAGE)),new LinearLayout.LayoutParams(-1,dp(48)));
        scroll.post(()->scroll.scrollTo(0,position));
    }
    private void editAttendance(LocalDate date){
        if(!date.isBefore(LocalDate.now(Policy.ZONE))){render();return;}
        MonthAttendance.ManualStatus existing=prefs.manualAttendance(date);
        MonthAttendance.ManualStatus[] choice={existing};
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(24),dp(8),dp(24),dp(8));
        TextView note=new TextView(this);note.setText("앱에만 저장되며 사이트 출결은 변경하지 않습니다.");note.setTextSize(13);form.addView(note);
        RadioGroup options=new RadioGroup(this);options.setOrientation(RadioGroup.VERTICAL);form.addView(options);
        for(MonthAttendance.ManualStatus status:MonthAttendance.ManualStatus.values()){
            RadioButton option=new RadioButton(this);option.setId(View.generateViewId());option.setText(status.state.label);option.setTag("manual-status:"+status.name());option.setMinHeight(dp(48));options.addView(option);
            if(status==existing)option.setChecked(true);
            option.setOnCheckedChangeListener((b,checked)->{if(checked){choice[0]=status;if(manualDialog!=null)manualDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);}});
        }
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 출결",Locale.KOREAN)))
            .setView(form).setNegativeButton("취소",null).setPositiveButton("저장",null);
        if(existing!=null)builder.setNeutralButton("직접 입력 삭제",null);
        manualDialog=builder.create();
        manualDialog.setOnShowListener(d->{
            manualDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(choice[0]!=null);
            manualDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(choice[0]!=null)saveManual(date,choice[0]);});
            if(existing!=null)manualDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->saveManual(date,null));
        });
        manualDialog.show();
    }
    private void saveManual(LocalDate date,MonthAttendance.ManualStatus status){
        try {
            if(!prefs.saveManualAttendance(date,status)){Toast.makeText(this,"저장하지 못했습니다. 다시 시도해 주세요.",Toast.LENGTH_LONG).show();return;}
            if(manualDialog!=null)manualDialog.dismiss();manualDialog=null;render();
            Toast.makeText(this,status==null?"직접 입력을 삭제했습니다":"출결을 저장했습니다",Toast.LENGTH_SHORT).show();
        }catch(IllegalArgumentException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private LinearLayout time(String label,String value,String tag){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.addView(text(label,12,theme.muted,false));TextView time=text(value.isEmpty()?"—":value,23,theme.ink,true);time.setFontFeatureSettings("tnum");time.setTag(tag);box.addView(time);return box;}
    private int color(MonthAttendance.State state){boolean night=theme==HomeTheme.NIGHT;return switch(state){
        case COMPLETE, ATTENDED -> night?0xFF8CDCC3:0xFF196A51;
        case ABSENT -> night?0xFFFFB4AB:0xFFAD3434;
        case EARLY_LEAVE, ENTERED -> night?0xFFF4CA91:0xFF885118;
        case HOLIDAY -> night?0xFFD0B7F3:0xFF73508F;
        case PLANNED -> night?0xFFA7CFF5:0xFF386785;
        default -> theme.muted;
    };}
    private LinearLayout card(){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(shape(theme.surface,theme.border,1,20));content.addView(card,new LinearLayout.LayoutParams(-1,-2));return card;}
    private Button button(String title,String description,Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(theme.accent);b.setStateListAnimator(null);b.setPadding(dp(6),dp(4),dp(6),dp(4));b.setMinWidth(0);b.setMinHeight(0);b.setBackground(shape(theme.soft,theme.soft,0,12));b.setContentDescription(description);b.setOnClickListener(v->action.run());return b;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(3),0,dp(3));if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private GradientDrawable shape(int fill,int border,int width,int radius){GradientDrawable s=new GradientDrawable();s.setColor(fill);s.setCornerRadius(dp(radius));if(width>0)s.setStroke(dp(width),border);return s;}
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private void gap(LinearLayout parent,int size){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
}
