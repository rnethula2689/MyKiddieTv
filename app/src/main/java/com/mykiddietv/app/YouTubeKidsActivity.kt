package com.mykiddietv.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * YouTube Kids inside an in-app WebView, wrapped in the kid guardrails (immersive,
 * screen-lock, Back-stays-in-app). Keeps the kid inside MyKiddieTv so screen-pinning
 * and the rest of the guardrails stay intact.
 *
 * First run needs a grown-up to sign in with a Google account and pick the child's
 * content level on youtubekids.com — the WebView remembers it via cookies afterward.
 */
class YouTubeKidsActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var fullscreenContainer: FrameLayout
    private var screenLock: ScreenLock? = null

    // HTML5 fullscreen video state
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_youtube_kids)
        web = findViewById(R.id.web)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            // Desktop-Chrome UA: youtubekids.com mobile-web only pushes the native app +
            // Family Link setup, while the desktop site has the actual "Who's watching -> watch" flow.
            userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
        WebView.setWebContentsDebuggingEnabled(true)

        // Cookies (incl. third-party for Google sign-in) so the parent's setup persists.
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {   // keep all navigation inside the app
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                android.util.Log.i("YTKids", "start: $url")
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                android.util.Log.i("YTKids", "finish: $url")
            }
            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                android.util.Log.e("YTKids", "error ${request?.url}: ${error?.errorCode} ${error?.description}")
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) { callback.onCustomViewHidden(); return }
                customView = view
                fullscreenContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                fullscreenContainer.visibility = View.VISIBLE
                web.visibility = View.GONE
                customCallback = callback
                KidGuard.immersive(this@YouTubeKidsActivity)
            }

            override fun onHideCustomView() {
                customView?.let { fullscreenContainer.removeView(it) }
                fullscreenContainer.visibility = View.GONE
                web.visibility = View.VISIBLE
                customView = null
                customCallback?.onCustomViewHidden()
                customCallback = null
                KidGuard.immersive(this@YouTubeKidsActivity)
            }
        }

        if (savedInstanceState == null) web.loadUrl("https://www.youtubekids.com/")

        KidGuard.immersive(this)
        screenLock = ScreenLock(this)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        KidGuard.immersive(this)
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (screenLock?.locked == true) return true
        return super.dispatchKeyEvent(event)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (screenLock?.locked == true) return
        if (customView != null) { web.webChromeClient?.onHideCustomView(); return }
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
