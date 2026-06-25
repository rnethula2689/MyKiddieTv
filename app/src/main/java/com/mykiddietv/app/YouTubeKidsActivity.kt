package com.mykiddietv.app

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * YouTube Kids inside an in-app WebView. Uses a DESKTOP user-agent because the mobile
 * youtubekids.com only shows a marketing/app-push gateway, while the desktop site serves
 * the real "Who's watching -> watch" experience. Locked to landscape so the desktop layout
 * fits (it overflows/clips in portrait), and immersive so the Android bars stay hidden.
 *
 * YouTube Kids has its own on-screen lock, so we don't add ours. Instead we add an always-
 * visible "Exit" button (and Back) that returns to the kid home — otherwise YouTube's
 * internal navigation is a dead end with the system Back hidden.
 *
 * First run needs a grown-up to sign in with a Google account and pick the child's content
 * level on youtubekids.com — the WebView remembers it via cookies afterward.
 */
class YouTubeKidsActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var fullscreenContainer: FrameLayout

    // HTML5 fullscreen video state
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_youtube_kids)
        web = findViewById(R.id.web)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
        findViewById<View>(R.id.exitBtn).setOnClickListener { finish() }

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

        // Cookies (incl. third-party for Google sign-in) so the parent's setup persists.
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                android.util.Log.i("YTKids", "start: $url")
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                android.util.Log.i("YTKids", "finish: $url")
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

        WebView.setWebContentsDebuggingEnabled(true)
        if (savedInstanceState == null) web.loadUrl("https://www.youtubekids.com/")

        KidGuard.immersive(this)
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

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Exit fullscreen video first; otherwise always return to the kid home.
        if (customView != null) { web.webChromeClient?.onHideCustomView(); return }
        finish()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
