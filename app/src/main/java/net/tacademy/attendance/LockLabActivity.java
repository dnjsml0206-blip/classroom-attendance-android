package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;

/** Temporary, user-operated lab. Never silently enables trust agents or changes screen security. */
public final class LockLabActivity extends Activity {
    private static final int INK=0xFF183631, MUTED=0xFF687D77, ACCENT=0xFF007C78;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private TextView current, progress, log, description;
    private Spinner methods, delay;
    private Switch actual;
    private final Runnable refresh=new Runnable(){@Override public void run(){update();handler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xFFF4F7F6);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(22),dp(22),dp(22),dp(30));scroll.addView(content);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFFF4F7F6);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);}
            else v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;
        });setContentView(root);
        content.addView(text("임시 메뉴 · 기기에서 직접 확인",12,ACCENT,true));space(content,8);
        content.addView(text("잠금 상태 테스트",28,INK,true));space(content,8);
        content.addView(text("방법 선택 → 예약 → 화면 끄기 → 결과 확인\n한 번만 실행하며 최대 2분 안에 확인합니다.",14,MUTED,false));space(content,18);

        LinearLayout select=card("1. 테스트 방법");
        methods=new Spinner(this);String[] labels=new String[LockTestPolicy.Mode.values().length];
        for(int i=0;i<labels.length;i++)labels[i]=LockTestPolicy.Mode.values()[i].label;
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels);methods.setAdapter(adapter);select.addView(methods,new LinearLayout.LayoutParams(-1,dp(52)));
        description=text("",14,MUTED,false);space(select,8);select.addView(description);
        methods.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){@Override public void onNothingSelected(AdapterView<?> p){} @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id){describe();}});

        LinearLayout run=card("2. 실행");
        delay=new Spinner(this);delay.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"15초 후 실행","30초 후 실행","60초 후 실행"}));run.addView(delay,new LinearLayout.LayoutParams(-1,dp(48)));
        actual=new Switch(this);actual.setText("잠금 해제 후 실제 출결도 시도");actual.setTextColor(INK);actual.setTextSize(15);actual.setMinHeight(dp(52));run.addView(actual);
        run.addView(text("기본은 조회 테스트입니다. 테스트가 진행되는 동안 예약 자동 클릭을 잠시 보류합니다. 스위치를 켜면 기존 예약·시간·Wi-Fi·Chrome 자동 클릭 조건을 그대로 적용합니다.",12,MUTED,false));
        add(run,"예약하고 화면 끄기",true,()->startTrial(new int[]{15,30,60}[delay.getSelectedItemPosition()]));
        add(run,"지금 테스트",false,()->startTrial(0));add(run,"예약·테스트 취소",false,()->{LockLab.cancel(this);update();});
        progress=text("",13,ACCENT,true);space(run,12);run.addView(progress);

        LinearLayout phone=card("3. 휴대폰 상태와 신뢰 설정");phone.addView(text(LockLab.device(),14,INK,true));current=text("",13,MUTED,false);space(phone,8);phone.addView(current);
        phone.addView(text("Extend Unlock은 예전 Smart Lock입니다. 휴대폰에서 신뢰하는 기기·장소·신체 활동 감지를 직접 설정한 뒤 각각 시험하세요. 앱에서는 활성화된 신뢰 방식을 판별할 수 없습니다.",13,MUTED,false));
        add(phone,"보안 · Extend Unlock 설정",false,()->{open(new Intent(Settings.ACTION_SECURITY_SETTINGS));Toast.makeText(this,"보안 또는 잠금화면 메뉴에서 Extend Unlock / Smart Lock을 선택하세요",Toast.LENGTH_LONG).show();});
        add(phone,"신뢰 방식별 준비 방법",false,this::trustHelp);
        add(phone,"Chrome 접근성 설정",false,()->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        add(phone,"알람 및 리마인더 설정",false,()->open(Build.VERSION.SDK_INT>=31?new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())):new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        add(phone,"테스트 알림 설정",false,()->open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())));
        add(phone,"서버 인증 · API 준비 상태",false,()->new AlertDialog.Builder(this).setTitle("서버 직접 처리 · 미연동")
            .setMessage("Chrome 인증은 앱의 서버 인증으로 공유되지 않습니다. 현재 잠금 유지 테스트는 등록 Wi-Fi에서 HTTPS 연결 응답만 확인합니다.\n\n앱 전용 인증 방법과 출결 API가 확보되어야 잠금 상태의 실제 출결 처리를 연결할 수 있습니다. 연결 응답이 와도 인증·출결 성공으로 표시하지 않습니다.")
            .setPositiveButton("확인",null).show());

        LinearLayout results=card("4. 테스트 기록");log=text("",12,INK,false);log.setTextIsSelectable(true);results.addView(log);
        add(results,"성공 · 부분 성공 · 실패 기록",true,this::verdict);
        add(results,"결과 전체 복사",false,()->{getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("출석 메이트 잠금 테스트",LockLab.allReports(this)));Toast.makeText(this,"이전 결과를 포함해 복사했습니다",Toast.LENGTH_SHORT).show();});
        add(results,"이전 테스트 함께 보기",false,()->{TextView reports=text(LockLab.allReports(this),13,INK,false);reports.setTextIsSelectable(true);reports.setPadding(dp(20),dp(12),dp(20),dp(12));ScrollView all=new ScrollView(this);all.addView(reports);new AlertDialog.Builder(this).setTitle("최근 테스트 · 최대 11회").setView(all).setPositiveButton("닫기",null).show();});
        add(content,"설정으로 돌아가기",false,this::finish);describe();update();
    }
    private LockTestPolicy.Mode selected(){return LockTestPolicy.Mode.values()[methods.getSelectedItemPosition()];}
    private void describe(){
        if(description==null)return;
        String message=switch(selected()){
            case WAKE -> "화면을 깨운 뒤 잠금이 이미 해제되어 있으면 Chrome을 엽니다. 잠금 화면이 남으면 결과만 남깁니다.";
            case EXTEND -> "Extend Unlock이 활성화된 상태에서 시스템에 잠금 해제를 요청합니다. 새 인증이 필요하면 자동 진행을 멈춥니다.";
            case DEVICE -> "Extend Unlock에 등록한 Bluetooth 기기에 연결하고 시험하세요. 시스템의 신뢰 상태를 이용해 잠금 해제를 요청합니다.";
            case PLACE -> "Extend Unlock에 등록한 신뢰 장소에서 시험하세요. 장소 범위는 앱의 강의실 반경 30m와 별개입니다.";
            case BODY -> "Extend Unlock의 신체 활동 감지를 켜고 휴대 중인 상태에서 시험하세요. 책상 위에 놓았을 때도 비교해 보세요.";
            case AUTH -> "잠겨 있으면 Android의 인증 화면을 요청합니다. 휴대폰이 제공하는 PIN·패턴·지문 등 인증 후 Chrome으로 이어집니다. 이미 해제된 상태에서는 새 인증을 요구하지 않습니다.";
            case NETWORK -> "화면을 깨우거나 Chrome을 열지 않고 등록 Wi-Fi에서 HTTPS 연결만 진단합니다. 앱 전용 서버 인증/API는 아직 연결되지 않아 실제 출결은 처리하지 않습니다.";
        };
        description.setText(message);
        if(actual!=null){boolean available=selected()!=LockTestPolicy.Mode.NETWORK;actual.setEnabled(available);if(!available)actual.setChecked(false);}
    }
    private void startTrial(int seconds){
        try{
            String token=LockLab.prepare(this,selected(),actual.isChecked(),seconds);
            if(seconds==0){LockLab.fire(this,token,"앱에서 즉시 실행");if(selected()==LockTestPolicy.Mode.NETWORK)new Thread(()->LockTestReceiver.diagnose(getApplicationContext(),token),"lock-network-diagnostic").start();}
            else Toast.makeText(this,seconds+"초 안에 전원 버튼으로 화면을 꺼 주세요",Toast.LENGTH_LONG).show();
            update();
        }catch(IllegalStateException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void trustHelp(){new AlertDialog.Builder(this).setTitle("Extend Unlock · 신뢰 방식")
        .setMessage("먼저 PIN 등으로 한 번 잠금을 해제한 후 시험하세요.\n\n• 신뢰 기기: 등록한 Bluetooth 기기 연결\n• 신뢰 장소: 휴대폰에 등록한 장소에 머무르기\n• 신체 활동 감지: 휴대 중과 책상 위 상태 비교\n\n지원 항목과 설정 경로는 기기마다 다릅니다. 신뢰 유지 시간은 제한되어 있으며 잠금 모드·재부팅·재인증 요구 뒤에는 다시 인증이 필요할 수 있습니다.\n\n앱은 보안 설정을 자동으로 켜거나 변경하지 않습니다.")
        .setPositiveButton("확인",null).setNeutralButton("Google 안내",(d,w)->open(new Intent(Intent.ACTION_VIEW,Uri.parse("https://support.google.com/android/answer/9075927?hl=ko")))).show();}
    private void verdict(){
        if(LockLab.id(this).isEmpty()){Toast.makeText(this,"먼저 테스트를 실행해 주세요",Toast.LENGTH_SHORT).show();return;}
        String token=LockLab.id(this);LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(22),dp(8),dp(22),dp(8));
        Spinner result=new Spinner(this);String[] values={"성공","부분 성공","실패","미확인"};result.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));
        String previous=LockLab.prefs(this).getString("verdict","미확인");for(int i=0;i<values.length;i++)if(values[i].equals(previous))result.setSelection(i);form.addView(result);
        EditText note=new EditText(this);note.setHint("예: 화면은 켜졌지만 PIN 입력이 필요했음");note.setText(LockLab.prefs(this).getString("note",""));note.setMinLines(2);note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});form.addView(note);
        new AlertDialog.Builder(this).setTitle("직접 확인한 결과").setView(form).setNegativeButton("취소",null).setPositiveButton("저장",(d,w)->{
            if(!token.equals(LockLab.id(this))){Toast.makeText(this,"새 테스트가 시작되어 저장하지 않았습니다",Toast.LENGTH_SHORT).show();return;}
            LockLab.verdict(this,values[result.getSelectedItemPosition()],note.getText().toString());update();
        }).show();
    }
    private void update(){if(progress==null)return;LockLab.expire(this);
        current.setText(getString(R.string.lock_lab_state,LockLab.snapshot(this),ChromeAttendanceService.available()?"연결됨":"연결 필요",AlarmScheduler.allowed(this)?"허용":"미허용"));
        long seconds=Math.max(0,(LockLab.prefs(this).getLong("due",0)-System.currentTimeMillis()+999)/1000);
        String stage=LockLab.prefs(this).getString("stage","아직 실행하지 않았습니다.");
        progress.setText(LockLab.live(this,LockLab.id(this))&&seconds>0?getString(R.string.lock_lab_remaining,stage,seconds):stage);
        String report=LockLab.report(this);if(!log.getText().toString().equals(report))log.setText(report);
    }
    private void open(Intent intent){try{startActivity(intent);}catch(RuntimeException e){Toast.makeText(this,"휴대폰 설정에서 해당 메뉴를 직접 열어 주세요",Toast.LENGTH_LONG).show();}}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(4),1);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private void space(LinearLayout p,int n){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private LinearLayout card(String title){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(18),dp(18),dp(18));GradientDrawable bg=new GradientDrawable();bg.setColor(0xFFFFFFFF);bg.setCornerRadius(dp(20));c.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);content.addView(c,lp);c.addView(text(title,14,ACCENT,true));space(c,10);return c;}
    private void add(LinearLayout p,String label,boolean primary,Runnable action){space(p,10);Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?0xFFFFFFFF:ACCENT);b.setStateListAnimator(null);b.setMinHeight(dp(48));GradientDrawable bg=new GradientDrawable();bg.setColor(primary?ACCENT:0xFFE5F2EF);bg.setCornerRadius(dp(12));b.setBackground(bg);b.setPadding(dp(10),dp(8),dp(10),dp(8));b.setOnClickListener(v->action.run());p.addView(b,new LinearLayout.LayoutParams(-1,-2));}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}