package net.tacademy.attendance;

import android.app.*;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public final class AuthActivity extends Activity {
    private WebView web;
    @android.annotation.SuppressLint("SetJavaScriptEnabled") // The observed attendance page requires JS. No native JS bridge; HTTPS origin is restricted.
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setBackgroundColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        layout.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(bars.left + 12, bars.top, bars.right + 12, bars.bottom);
            } else v.setPadding(12, insets.getSystemWindowInsetTop(), 12, insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView info = new TextView(this); info.setText("출석 사이트 인증\n처음에는 발급받은 인증 링크를 입력하세요. 인증 후 ‘인증 저장’을 누르세요.");
        info.setTextSize(16); info.setPadding(16,20,16,12); info.setTextColor(0xFF183631); layout.addView(info);
        EditText address = new EditText(this); address.setSingleLine(true); address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setHint("https://skplanet.atosoft.net/… 인증 링크"); layout.addView(address);
        LinearLayout actions = new LinearLayout(this);
        Button back = new Button(this); back.setText("돌아가기"); back.setOnClickListener(v -> finish());
        Button load = new Button(this); load.setText("링크 열기"); load.setOnClickListener(v -> {
            String url = address.getText().toString().trim();
            if (!SiteProtocol.allowedNavigation(url)) { address.setError("skplanet.atosoft.net의 HTTPS 인증 링크를 입력해 주세요"); return; }
            web.loadUrl(url);
        });
        Button save = new Button(this); save.setText("인증 저장"); save.setOnClickListener(v -> saveAuth());
        actions.addView(back,new LinearLayout.LayoutParams(0,-2,1)); actions.addView(load,new LinearLayout.LayoutParams(0,-2,1)); actions.addView(save,new LinearLayout.LayoutParams(0,-2,1));
        layout.addView(actions);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!SiteProtocol.allowedNavigation(request.getUrl().toString())) {
                    Toast.makeText(AuthActivity.this, "출석 사이트의 HTTPS 페이지에서 인증해 주세요", Toast.LENGTH_LONG).show(); return true;
                }
                return false;
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel(); Toast.makeText(AuthActivity.this, "사이트의 보안 연결을 확인할 수 없습니다", Toast.LENGTH_LONG).show();
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        layout.addView(web,new LinearLayout.LayoutParams(-1,0,1)); setContentView(layout);
        web.loadUrl(SiteProtocol.PAGE);
    }
    private void saveAuth() {
        try {
            if (!SiteProtocol.allowedNavigation(web.getUrl())) throw new IllegalArgumentException();
            String cookie = CookieManager.getInstance().getCookie(SiteProtocol.PAGE);
            new AuthVault(this).save(cookie);
            CookieManager.getInstance().flush();
            new Prefs(this).log("앱 내 사이트 인증 저장");
            Toast.makeText(this, "인증 정보를 이 기기에 저장했습니다. 조회 모드로 먼저 확인해 주세요.", Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("아직 인증되지 않았습니다")
                .setMessage("Chrome에서 쓰던 인증은 이 앱과 별개입니다. 교육기관에서 받은 본인 인증 링크를 위 입력란에서 열어 인증한 뒤 다시 저장해 주세요.")
                .setPositiveButton("확인", null).show();
        }
    }
    @Override protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }
}
