package com.julesybean.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.content.Context
import android.content.SharedPreferences

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "JulesybeanPrefs"
    private val KEY_LAST_URL = "last_url"
    private var lastValidInternalUrl: String? = null
    private lateinit var gestureDetector: GestureDetector
    private lateinit var backPressedCallback: OnBackPressedCallback

    // For file uploads
    private var uploadMessage: ValueCallback<Array<Uri>>? = null
    private var currentPhotoPath: String? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (uploadMessage == null) return@registerForActivityResult

        var results: Array<Uri>? = null
        if (result.resultCode == RESULT_OK) {
            val dataString = result.data?.dataString
            if (dataString != null) {
                results = arrayOf(Uri.parse(dataString))
            } else if (currentPhotoPath != null) {
                results = arrayOf(Uri.parse("file:" + currentPhotoPath))
            }
        } else {
            // User cancelled, cleanup the empty file we created for the camera
            if (currentPhotoPath != null) {
                val file = File(currentPhotoPath!!)
                if (file.exists()) {
                    file.delete()
                }
            }
        }

        uploadMessage?.onReceiveValue(results)
        uploadMessage = null
        currentPhotoPath = null
    }

    // --- Configuration Selectors (Easily tweakable) ---
    private val CSS_SELECTOR_CHAT_CONTAINER = "main"
    private val CSS_SELECTOR_CODE_PANEL = "aside, .code-panel, [role='complementary']"
    private val CSS_SELECTOR_NAV_MENU = "button[aria-label='Main menu'], button[aria-label='Menu']"
    private val CSS_SELECTOR_CHAT_SCROLL = "main, .chat-container"
    // ----------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        webView = findViewById(R.id.webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (url != null) {
                    val uri = Uri.parse(url)
                    val host = uri.host
                    if (host == "jules.google.com" || host?.endsWith(".jules.google.com") == true) {
                        lastValidInternalUrl = url
                    }
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                injectMobileFriendlyScript(view)
                injectDarkModeScript(view)
                // Enable back button overriding once the page loads and we're inside the SPA
                backPressedCallback.isEnabled = true
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url
                val host = uri?.host
                if (host != null && host != "jules.google.com" && !host.endsWith(".jules.google.com")) {
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(intent)
                    return true
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (uploadMessage != null) {
                    uploadMessage?.onReceiveValue(null)
                    uploadMessage = null
                }
                uploadMessage = filePathCallback

                var takePictureIntent: Intent? = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (takePictureIntent?.resolveActivity(packageManager) != null) {
                    var photoFile: File? = null
                    try {
                        photoFile = createImageFile()
                    } catch (ex: IOException) {
                        // Error occurred while creating the File
                    }
                    if (photoFile != null) {
                        val photoURI = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${packageName}.fileprovider",
                            photoFile
                        )
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    } else {
                        takePictureIntent = null
                    }
                }

                val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
                contentSelectionIntent.type = "*/*" // Allow all file types

                val intentArray: Array<Intent> = if (takePictureIntent != null) {
                    arrayOf(takePictureIntent)
                } else {
                    emptyArray()
                }

                val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "Choose an action")
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)

                fileChooserLauncher.launch(chooserIntent)
                return true
            }
        }

        setupSwipeGesture()
        setupBackButtonHandling()

        if (!handleIntent(intent)) {
            val lastUrl = sharedPreferences.getString(KEY_LAST_URL, "https://jules.google.com")
            lastValidInternalUrl = lastUrl
            webView.loadUrl(lastUrl ?: "https://jules.google.com")
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?): Boolean {
        val data: Uri? = intent?.data
        val host = data?.host
        if (data != null && (host == "jules.google.com" || host?.endsWith(".jules.google.com") == true)) {
            var urlToLoad = data.toString()
            if (data.scheme == "http") {
                urlToLoad = urlToLoad.replaceFirst("http://", "https://")
            }
            lastValidInternalUrl = urlToLoad
            webView.loadUrl(urlToLoad)
            return true
        }
        return false
    }

    override fun onPause() {
        super.onPause()
        saveCurrentInternalUrl()
    }

    private fun saveCurrentInternalUrl() {
        if (lastValidInternalUrl != null) {
            sharedPreferences.edit().putString(KEY_LAST_URL, lastValidInternalUrl).apply()
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun setupBackButtonHandling() {
        // We only enable this once the page loads. And if we want the user to exit the app
        // we should let them. Since the prompt says "One tap of the back button should go to the bottom of the chat window",
        // we'll implement it as scrolling to bottom, and then we will disable the interceptor for a short time
        // to let the user double-tap to exit.

        var backPressTime: Long = 0
        backPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (backPressTime + 2000 > System.currentTimeMillis()) {
                    // Double tap within 2 seconds: let system handle it (exit app)
                    this.isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    // Re-enable in case they don't exit fully
                    this.isEnabled = true
                } else {
                    // First tap: scroll to bottom
                    scrollToBottomOfChat()
                }
                backPressTime = System.currentTimeMillis()
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    private fun scrollToBottomOfChat() {
        val js = """
            javascript:(function() {
                var scrollContainers = document.querySelectorAll('$CSS_SELECTOR_CHAT_SCROLL');
                if (scrollContainers.length > 0) {
                    for(var i=0; i<scrollContainers.length; i++) {
                        scrollContainers[i].scrollTop = scrollContainers[i].scrollHeight;
                    }
                } else {
                    window.scrollTo(0, document.body.scrollHeight);
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeGesture() {
        val swipeListener = object : SwipeGestureListener(this) {
            override fun onSwipeRight() {
                openNavigationMenu()
            }
        }
        gestureDetector = GestureDetector(this, swipeListener)

        webView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun openNavigationMenu() {
        val js = """
            javascript:(function() {
                var menuBtn = document.querySelector("$CSS_SELECTOR_NAV_MENU");
                if (menuBtn) {
                    menuBtn.click();
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun isDarkModeEnabled(): Boolean {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun injectDarkModeScript(view: WebView) {
        val isDark = isDarkModeEnabled()
        val js = """
            javascript:(function() {
                if ($isDark) {
                    document.documentElement.classList.add('dark');
                    document.documentElement.setAttribute('data-theme', 'dark');
                } else {
                    document.documentElement.classList.remove('dark');
                    document.documentElement.setAttribute('data-theme', 'light');
                }
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    private fun injectMobileFriendlyScript(view: WebView) {
        // SPA elements might load dynamically. So we use CSS injection instead of
        // JS style.display which would fail if the element doesn't exist yet.
        val js = """
            javascript:(function() {
                var style = document.createElement('style');
                style.innerHTML = `
                    /* Force readable font size */
                    * {
                        font-size: 16px !important;
                    }
                    /* Ensure no horizontal scrolling */
                    body, html {
                        overflow-x: hidden;
                        width: 100vw;
                        max-width: 100%;
                    }
                    /* Hide code panel */
                    $CSS_SELECTOR_CODE_PANEL {
                        display: none !important;
                    }
                    /* Make chat full width */
                    $CSS_SELECTOR_CHAT_CONTAINER {
                        width: 100% !important;
                        max-width: 100% !important;
                        margin: 0 !important;
                        padding: 10px !important;
                    }
                `;
                document.head.appendChild(style);
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }
}
