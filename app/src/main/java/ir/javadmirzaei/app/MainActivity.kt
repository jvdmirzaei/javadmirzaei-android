package ir.javadmirzaei.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class MainActivity : Activity() {

    companion object {
        // ===== تنظیمات قابل تغییر =====
        private const val HOME_URL = "https://javadmirzaei.ir/secretpdore/"
        private const val ALLOWED_HOST = "javadmirzaei.ir"
        private const val USER_AGENT_TOKEN = "JMApp/1.1"
        private const val PROTECT_FROM_CAPTURE = true   // قفل اسکرین‌شات و ضبط صفحه
        private const val ALLOW_DOWNLOADS = false       // true = دانلود فایل‌ها (مثل جزوه‌ها) مجاز باشد
        // ==============================

        private val NAVY = Color.parseColor("#1E3A5F")
        private val NAVY_DARK = Color.parseColor("#162C48")
        private val YELLOW = Color.parseColor("#F4B400")
        private val SKY = Color.parseColor("#EAF4FF")

        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

        // اسکریپت محافظت: جلوگیری از انتخاب و کپی متن و کشیدن تصاویر
        private const val PROTECT_SCRIPT = """
(function () {
  if (window.__jmProtect) return;
  window.__jmProtect = true;
  var css = 'html,body,*{-webkit-user-select:none!important;user-select:none!important;-webkit-touch-callout:none!important}' +
    'input,textarea,[contenteditable="true"]{-webkit-user-select:text!important;user-select:text!important}' +
    'img,video,a{-webkit-user-drag:none!important}';
  function add() {
    var p = document.head || document.documentElement;
    if (!p || document.getElementById('__jm_css')) return;
    var s = document.createElement('style');
    s.id = '__jm_css';
    s.textContent = css;
    p.appendChild(s);
  }
  function observe() {
    if (document.documentElement) {
      new MutationObserver(add).observe(document.documentElement, { childList: true });
    }
  }
  add();
  observe();
  document.addEventListener('DOMContentLoaded', add);
  function editable(t) {
    return t && (t.isContentEditable || ['INPUT', 'TEXTAREA'].indexOf(t.tagName) >= 0);
  }
  function block(e) { if (!editable(e.target)) e.preventDefault(); }
  document.addEventListener('contextmenu', function (e) { e.preventDefault(); }, true);
  document.addEventListener('copy', block, true);
  document.addEventListener('cut', block, true);
  document.addEventListener('dragstart', function (e) { e.preventDefault(); }, true);
})();
"""

        // صفحه خطای فارسی (وقتی اینترنت قطع است؛ آدرس سایت نمایش داده نمی‌شود)
        private const val ERROR_HTML = """
<!DOCTYPE html>
<html lang="fa" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
html,body{height:100%;margin:0}
body{display:flex;align-items:center;justify-content:center;background:#EAF4FF;font-family:sans-serif;color:#2B2B2B}
.card{background:#fff;border-radius:20px;padding:36px 28px;margin:20px;max-width:420px;text-align:center;box-shadow:0 12px 40px rgba(30,58,95,.15);border-top:6px solid #F4B400}
.ic{width:68px;height:68px;border-radius:50%;background:#1E3A5F;color:#F4B400;font-size:36px;font-weight:bold;line-height:68px;margin:0 auto 16px}
h1{color:#1E3A5F;font-size:20px;margin:0 0 10px}
p{line-height:2;margin:0 0 24px;color:#555;font-size:15px}
a.btn{display:inline-block;background:#2A9D8F;color:#fff;border-radius:12px;padding:12px 34px;font-size:16px;text-decoration:none}
a.btn:active{background:#F4B400;color:#1E3A5F}
</style></head>
<body><div class="card">
<div class="ic">!</div>
<h1>اتصال به اینترنت برقرار نیست</h1>
<p>لطفاً اتصال اینترنت خود را بررسی کنید و دوباره تلاش کنید.</p>
<a class="btn" href="jmretry://go">تلاش دوباره</a>
</div></body></html>
"""
    }

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var lastUrl: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (PROTECT_FROM_CAPTURE) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        root = FrameLayout(this).apply { setBackgroundColor(SKY) }

        webView = WebView(this)
        root.addView(webView, FrameLayout.LayoutParams(MATCH, MATCH))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(YELLOW)
            progressBackgroundTintList = ColorStateList.valueOf(NAVY_DARK)
            visibility = View.GONE
        }
        root.addView(progress, FrameLayout.LayoutParams(MATCH, dp(3)))

        setContentView(root)

        setupWebView()

        val restored = savedInstanceState?.let { webView.restoreState(it) }
        if (restored == null) webView.loadUrl(HOME_URL)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        WebView.setWebContentsDebuggingEnabled(false)

        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.javaScriptCanOpenWindowsAutomatically = false
        s.setGeolocationEnabled(false)
        s.userAgentString = s.userAgentString + " " + USER_AGENT_TOKEN

        webView.setBackgroundColor(SKY)
        webView.isLongClickable = false
        webView.isHapticFeedbackEnabled = false
        webView.setOnLongClickListener { true }

        // ذخیره لاگین کاربر
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, PROTECT_SCRIPT, setOf("*"))
        }

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "jmretry") {
                    view.loadUrl(lastUrl ?: HOME_URL)
                    return true
                }
                if (!request.isForMainFrame) return false // ویدیوهای داخل صفحه (iframe) آزاد باشند

                if (isOwnHost(uri)) {
                    if (uri.scheme == "https") return false
                    if (uri.scheme == "http") {
                        view.loadUrl(uri.buildUpon().scheme("https").build().toString())
                        return true
                    }
                }
                // سایت‌های دیگر (مثل درگاه پرداخت) در مرورگر گوشی باز شوند
                openExternal(uri)
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url != null && url.startsWith("https://") && isOwnHost(Uri.parse(url))) {
                    lastUrl = url
                }
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.visibility = View.GONE
                view.evaluateJavascript(PROTECT_SCRIPT, null)
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    view.loadDataWithBaseURL(null, ERROR_HTML, "text/html", "UTF-8", null)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            // تمام‌صفحه شدن ویدیو
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                webView.visibility = View.GONE
                root.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
                enterImmersive()
            }

            override fun onHideCustomView() {
                exitCustomView()
            }

            // دوربین، میکروفون و موقعیت مکانی همیشه رد شوند
            override fun onPermissionRequest(request: PermissionRequest) {
                request.deny()
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, false, false)
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (!ALLOW_DOWNLOADS) {
                toast("دانلود فایل داخل این برنامه امکان‌پذیر نیست. برای دانلود از وب‌سایت در مرورگر استفاده کنید.")
            } else {
                startDownload(url, userAgent, contentDisposition, mimeType)
            }
        }
    }

    private fun startDownload(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        try {
            val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val req = DownloadManager.Request(Uri.parse(url))
            if (mimeType != null) req.setMimeType(mimeType)
            req.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
            req.addRequestHeader("User-Agent", userAgent)
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            toast("دانلود شروع شد")
        } catch (e: Exception) {
            toast("دانلود انجام نشد")
        }
    }

    private fun isOwnHost(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return host.equals(ALLOWED_HOST, ignoreCase = true) ||
            host.equals("www.$ALLOWED_HOST", ignoreCase = true)
    }

    private fun openExternal(uri: Uri) {
        val scheme = uri.scheme ?: return
        if (scheme !in listOf("http", "https", "tel", "mailto", "sms")) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            // برنامه‌ای برای باز کردن این لینک نیست
        }
    }

    @Suppress("DEPRECATION")
    private fun enterImmersive() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    @Suppress("DEPRECATION")
    private fun exitImmersive() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun exitCustomView() {
        val view = customView ?: return
        root.removeView(view)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        webView.visibility = View.VISIBLE
        exitImmersive()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            customView != null -> exitCustomView()
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        root.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }
}
