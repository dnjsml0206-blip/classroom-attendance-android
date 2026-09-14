package net.tacademy.attendance;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;

/** User previews a bounded report and explicitly uploads it with their own GitHub permission. */
public final class LogUploadActivity extends Activity {
    private static final int INK=0xFF183631,MUTED=0xFF687D77,ACCENT=0xFF007C78;
    private LinearLayout content;private TextView status,connection,summary;private Button upload,connect,disconnect,reload;
    private String payload="";private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){update();handler.postDelayed(this,700);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(!CreatorTools.enabled()){finish();return;}if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xFFF4F7F6);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(22),dp(22),dp(22),dp(30));scroll.addView(content);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFFF4F7F6);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(b.left,b.top,b.right,b.bottom);}else v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});setContentView(root);
        content.addView(text("제작자 진단 기록",12,ACCENT,true));space(content,8);content.addView(text("GitHub에 로그 올리기",26,INK,true));space(content,8);
        content.addView(text("기록을 올린 뒤 Codex 대화에 ‘로그 분석해줘’라고 알려 주세요. 방식별 결과와 멈춘 단계를 확인할 수 있습니다.",14,MUTED,false));space(content,18);
        LinearLayout report=card("1. 올릴 기록 확인");summary=text("",13,INK,false);report.addView(summary);
        report.addView(text("마지막 성공 업로드 이후 새로 생기거나 수정된 로그만 올립니다. 잠금 테스트 최근 최대 11회와 최근 7일 출결 동작 최대 200건 중 미업로드 기록이 대상입니다. 기기 정보, 시험 시각, 앱 기록, 직접 남긴 판정과 메모가 포함됩니다. 위치 좌표와 Chrome 쿠키는 수집하지 않습니다. 메모에 개인 정보가 없는지 확인하세요.",12,MUTED,false));
        add(report,"로그 내용 미리보기",false,()->showText("업로드할 JSON 로그",payload.isEmpty()?"새로 올릴 로그가 없습니다.":payload));
        reload=add(report,"최신 기록 불러오기",false,this::load);
        LinearLayout github=card("2. GitHub 연결 · 처음 한 번");github.addView(text(GitHubLogClient.REPO+"\n비공개 저장소 · 제작자 진단 로그",12,MUTED,false));connection=text("",14,INK,true);space(github,8);github.addView(connection);
        connect=add(github,"GitHub 업로드 연결 설정",false,this::credentials);
        disconnect=add(github,"연결 해제",false,()->{try{new GitHubTokenStore(this).clear();toast("휴대폰에 저장된 연결 정보를 삭제했습니다. GitHub 토큰 자체는 GitHub 설정에서 폐기할 수 있습니다.");}catch(Exception e){toast("연결 정보 삭제에 실패했습니다. 다시 시도해 주세요.");}update();});
        LinearLayout send=card("3. 전송");upload=add(send,"로그 올리기",true,()->{if(!new GitHubTokenStore(this).configured()){credentials();return;}if(LockLab.live(this,LockLab.id(this))){toast("진행 중인 테스트가 끝난 뒤 올려 주세요.");return;}LogUploadStore.start(this);update();});
        status=text("",13,ACCENT,true);space(send,12);send.addView(status);
        add(send,"마지막 업로드 파일 열기",false,()->{String url=lastUrl();if(url.isEmpty())toast("아직 완료된 업로드가 없습니다.");else open(url);});
        add(send,"업로드 링크 복사",false,()->{String url=lastUrl();if(url.isEmpty()){toast("아직 완료된 업로드가 없습니다.");return;}getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("잠금 테스트 로그",url));toast("링크를 복사했습니다. Codex 대화에 붙여 넣어 주세요.");});
        add(content,"설정으로 돌아가기",false,this::finish);load();
    }
    private void load(){
        try{
            payload=LogUploadStore.prepare(this);
            summary.setText(getString(R.string.log_upload_summary,LogUploadStore.periodLabel(this),payload.isEmpty()?"새로 올릴 로그가 없습니다.":LockLogExport.summary(new JSONObject(payload).getJSONArray("records"))));
        }catch(Exception e){payload="";summary.setText(e instanceof IllegalStateException?e.getMessage():"로그를 준비하지 못했습니다. 잠시 후 최신 기록을 다시 불러와 주세요.");}
        update();
    }
    private String lastUrl(){String value=LogUploadStore.prefs(this).getString("last_url","");return value.startsWith("https://github.com/"+GitHubLogClient.REPO+"/blob/"+GitHubLogClient.BRANCH+"/logs/lock-tests/")?value:"";}
    private void update(){if(status==null)return;boolean busy=LogUploadStore.busy();
        if(!busy&&!payload.isEmpty()&&!payload.equals(LogUploadStore.prefs(this).getString("payload",""))){load();return;}
        boolean configured=new GitHubTokenStore(this).configured();connection.setText(configured?"연결 정보 저장됨 · 전송할 때 권한 확인":"최초 연결이 필요합니다");
        upload.setEnabled(!busy&&!payload.isEmpty());upload.setAlpha(upload.isEnabled()?1f:.45f);connect.setEnabled(!busy);disconnect.setEnabled(!busy&&configured);reload.setEnabled(!busy);upload.setText(payload.isEmpty()?"새로 올릴 로그 없음":"로그 올리기");
        String value=LogUploadStore.prefs(this).getString("status","기록을 준비한 뒤 로그 올리기를 누르세요.");
        status.setText(!busy&&value.equals("GitHub에 전송 중…")?"이전 전송 결과가 미확인입니다. 다시 누르면 같은 파일부터 확인합니다.":value);
    }
    private void credentials(){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(22),dp(8),dp(22),dp(8));
        form.addView(text("1. GitHub에서 로그인 후 토큰을 만드세요.\n2. Repository access → Only select repositories에서 classroom-attendance-android만 선택하세요.\n3. Contents 권한을 Read and write로 확인하고 Generate token을 누르세요.\n4. 발급된 토큰을 아래에 붙여 넣고 저장하세요.\n\n토큰은 휴대폰에 암호화해 보관합니다. 이 대화나 로그에 붙여 넣지 마세요.",13,INK,false));
        EditText token=new EditText(this);token.setHint("github_pat_…");token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);token.setSingleLine(true);token.setSaveEnabled(false);token.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);form.addView(token);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("GitHub 업로드 연결").setView(scroll).setNegativeButton("취소",null)
            .setNeutralButton("토큰 발급 페이지",null).setPositiveButton("연결 저장",null).create();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.setOnDismissListener(d->token.setText(""));dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->open("https://github.com/settings/personal-access-tokens/new?name=AttendanceMate-Logs&target_name=dnjsml0206-blip&expires_in=30&contents=write"));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{new GitHubTokenStore(this).save(token.getText().toString().trim());token.setText("");dialog.dismiss();toast("연결 정보를 저장했습니다. 로그 올리기를 누르면 전송합니다.");update();}catch(IllegalArgumentException e){token.setError(e.getMessage());}catch(Exception e){toast("연결 정보를 저장하지 못했습니다. 다시 시도해 주세요.");}});
    }
    private void showText(String title,String value){TextView text=text(value,12,INK,false);text.setTextIsSelectable(true);text.setPadding(dp(20),dp(12),dp(20),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(text);new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("닫기",null).show();}
    private void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(RuntimeException e){toast("링크를 열 수 없습니다. Chrome을 확인해 주세요.");}}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(4),1);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private void space(LinearLayout p,int n){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private LinearLayout card(String title){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(18),dp(18),dp(18));GradientDrawable bg=new GradientDrawable();bg.setColor(0xFFFFFFFF);bg.setCornerRadius(dp(20));c.setBackground(bg);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);content.addView(c,lp);c.addView(text(title,14,ACCENT,true));space(c,10);return c;}
    private Button add(LinearLayout p,String label,boolean primary,Runnable action){space(p,10);Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?0xFFFFFFFF:ACCENT);b.setStateListAnimator(null);b.setMinHeight(dp(48));GradientDrawable bg=new GradientDrawable();bg.setColor(primary?ACCENT:0xFFE5F2EF);bg.setCornerRadius(dp(12));b.setBackground(bg);b.setPadding(dp(10),dp(8),dp(10),dp(8));b.setOnClickListener(v->action.run());p.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
