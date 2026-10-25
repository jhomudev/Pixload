package com.pixload.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends Activity {
    private final ExecutorService checks = Executors.newSingleThreadExecutor();
    private WebView webView;
    private TextView hint;
    private boolean checking;
    private boolean finishing;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.wrap(base));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.INPUT);
        getWindow().setNavigationBarColor(Ui.BACK);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Ui.BACK);
        hint = Ui.text(this, getString(R.string.login_hint), 14, Ui.WHITE, false);
        hint.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 12));
        layout.addView(hint);
        TextView back = Ui.button(this, getString(R.string.login_back), R.drawable.tabler_arrow_left, false, () -> returnToApp(false));
        layout.addView(back);
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                if ("www.pixiv.net".equalsIgnoreCase(host) || "pixiv.net".equalsIgnoreCase(host)) {
                    checkSession();
                }
            }
        });
        layout.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        webView.loadUrl("https://www.pixiv.net/login.php");
    }

    private void checkSession() {
        if (checking || finishing) return;
        checking = true;
        CookieManager.getInstance().flush();
        checks.execute(() -> {
            boolean loggedIn = PixivClient.isLoggedIn();
            runOnUiThread(() -> {
                checking = false;
                if (loggedIn && !finishing) returnToApp(true);
            });
        });
    }

    private void returnToApp(boolean loggedIn) {
        if (finishing) return;
        finishing = true;
        CookieManager.getInstance().flush();
        setResult(loggedIn ? RESULT_OK : RESULT_CANCELED, new Intent());
        finish();
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else returnToApp(false);
    }

    @Override protected void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override protected void onDestroy() {
        checks.shutdownNow();
        webView.destroy();
        super.onDestroy();
    }
}
