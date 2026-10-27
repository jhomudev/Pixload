package com.pixload.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Pixiv login over a WebView; the cookies it stores are the session PixivClient reuses. */
class LoginActivity : Activity() {
    private val checks: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var webView: WebView
    private var checking = false
    private var finishing = false

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    @Suppress("DEPRECATION") // Bar colors are kept exactly as the Java activity set them.
    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Ui.INPUT
        window.navigationBarColor = Ui.BACK
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setBackgroundColor(Ui.BACK)
        val hint = Ui.text(this, getString(R.string.login_hint), 14, Ui.WHITE, false)
        hint.setPadding(Ui.dp(this, 20f), Ui.dp(this, 16f), Ui.dp(this, 20f), Ui.dp(this, 12f))
        layout.addView(hint)
        val back = Ui.button(this, getString(R.string.login_back), R.drawable.tabler_arrow_left, false,
            Runnable { returnToApp(false) })
        layout.addView(back)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val uri = Uri.parse(url)
                val host = uri.host
                if ("www.pixiv.net".equals(host, ignoreCase = true) || "pixiv.net".equals(host, ignoreCase = true)) {
                    checkSession()
                }
            }
        }
        layout.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        webView.loadUrl("https://www.pixiv.net/login.php")
    }

    private fun checkSession() {
        if (checking || finishing) return
        checking = true
        CookieManager.getInstance().flush()
        checks.execute {
            val loggedIn = PixivClient.isLoggedIn()
            runOnUiThread {
                checking = false
                if (loggedIn && !finishing) returnToApp(true)
            }
        }
    }

    private fun returnToApp(loggedIn: Boolean) {
        if (finishing) return
        finishing = true
        CookieManager.getInstance().flush()
        setResult(if (loggedIn) RESULT_OK else RESULT_CANCELED, Intent())
        finish()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION") // Hardware back still walks the WebView history before closing.
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else returnToApp(false)
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        checks.shutdownNow()
        webView.destroy()
        super.onDestroy()
    }
}
