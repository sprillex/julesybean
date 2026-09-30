package com.julesybean.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.GestureDetector
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebNotification
import org.mozilla.geckoview.WebNotificationDelegate
import org.mozilla.geckoview.WebPushDelegate
import org.mozilla.geckoview.WebPushSubscription
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var geckoView: GeckoView
    private lateinit var geckoSession: GeckoSession
    private lateinit var sharedPreferences: SharedPreferences
    private val PREFS_NAME = "JulesybeanPrefs"
    private val KEY_LAST_URL = "last_url"
    private var lastValidInternalUrl: String? = null
    private lateinit var gestureDetector: GestureDetector
    private lateinit var backPressedCallback: OnBackPressedCallback

    // For file uploads
    private var pendingFilePrompt: GeckoSession.PromptDelegate.FilePrompt? = null
    private var promptResultCallback: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = null
    private var currentPhotoPath: String? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val prompt = pendingFilePrompt
        val callback = promptResultCallback

        if (prompt != null && callback != null) {
            var chosenUri: Uri? = null
            if (result.resultCode == RESULT_OK) {
                val dataString = result.data?.dataString
                if (dataString != null) {
                    chosenUri = Uri.parse(dataString)
                } else if (currentPhotoPath != null) {
                    chosenUri = Uri.parse("file:" + currentPhotoPath)
                }
            } else {
                // User cancelled, cleanup empty file if created
                if (currentPhotoPath != null) {
                    val file = File(currentPhotoPath!!)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            }

            if (chosenUri != null) {
                callback.complete(prompt.confirm(this, chosenUri))
            } else {
                callback.complete(prompt.dismiss())
            }
        }

        pendingFilePrompt = null
        promptResultCallback = null
        currentPhotoPath = null
    }

    // --- Configuration Selectors ---
    private val CSS_SELECTOR_CHAT_CONTAINER = "main"
    private val CSS_SELECTOR_CODE_PANEL = "aside, .code-panel, [role='complementary']"
    private val CSS_SELECTOR_NAV_MENU = "button[aria-label='Main menu'], button[aria-label='Menu']"
    private val CSS_SELECTOR_CHAT_SCROLL = "main, .chat-container"

    companion object {
        private var runtime: GeckoRuntime? = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        geckoView = findViewById(R.id.geckoView)

        if (runtime == null) {
            val settings = GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(true)
                .build()
            runtime = GeckoRuntime.create(this, settings)
        }

        // Enable Web Push Controller & Notification Delegate
        runtime?.webPushController?.delegate = object : WebPushDelegate {
            override fun onSubscribe(scope: String, appServerKey: ByteArray?): GeckoResult<WebPushSubscription> {
                return GeckoResult.fromValue(null)
            }

            override fun onGetSubscription(scope: String): GeckoResult<WebPushSubscription> {
                return GeckoResult.fromValue(null)
            }

            override fun onUnsubscribe(scope: String): GeckoResult<Void> {
                return GeckoResult.fromValue(null)
            }
        }

        runtime?.webNotificationDelegate = object : WebNotificationDelegate {
            override fun onShowNotification(notification: WebNotification) {
                notification.click()
            }

            override fun onCloseNotification(notification: WebNotification) {}
        }

        geckoSession = GeckoSession()
        setupDelegates()
        geckoSession.open(runtime!!)
        geckoView.setSession(geckoSession)

        setupSwipeGesture()
        setupBackButtonHandling()

        if (!handleIntent(intent)) {
            val lastUrl = sharedPreferences.getString(KEY_LAST_URL, "https://jules.google.com")
            lastValidInternalUrl = lastUrl
            geckoSession.loadUri(lastUrl ?: "https://jules.google.com")
        }
    }

    private fun setupDelegates() {
        // Navigation Delegate
        geckoSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val uri = Uri.parse(request.uri)
                val host = uri.host

                if (host != null && host != "jules.google.com" && !host.endsWith(".jules.google.com")) {
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(intent)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                if (host == "jules.google.com" || host?.endsWith(".jules.google.com") == true) {
                    updateLastValidInternalUrl(request.uri)
                }

                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onLocationChange(session: GeckoSession, url: String?, permissions: List<GeckoSession.PermissionDelegate.ContentPermission>) {
                if (url != null) {
                    val uri = Uri.parse(url)
                    val host = uri.host
                    if (host == "jules.google.com" || host?.endsWith(".jules.google.com") == true) {
                        updateLastValidInternalUrl(url)
                    }
                }
            }
        }

        // Progress Delegate
        geckoSession.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (success) {
                    injectMobileFriendlyScript()
                    injectDarkModeScript()
                    backPressedCallback.isEnabled = true
                }
            }
        }

        // Permission Delegate: Grant desktop notifications for *.jules.google.com
        geckoSession.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission
            ): GeckoResult<Int>? {
                if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION) {
                    val uri = Uri.parse(perm.uri)
                    val host = uri.host
                    if (host == "jules.google.com" || host?.endsWith(".jules.google.com") == true) {
                        return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                    }
                }
                return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT)
            }
        }

        // Prompt Delegate: File chooser / Camera capture
        geckoSession.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onFilePrompt(
                session: GeckoSession,
                prompt: GeckoSession.PromptDelegate.FilePrompt
            ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                pendingFilePrompt = prompt
                promptResultCallback = result

                var takePictureIntent: Intent? = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (takePictureIntent?.resolveActivity(packageManager) != null) {
                    var photoFile: File? = null
                    try {
                        photoFile = createImageFile()
                    } catch (ex: IOException) {
                        // Error creating file
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
                contentSelectionIntent.type = "*/*"

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
                return result
            }
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
            } else if (data.scheme == "julesybean") {
                urlToLoad = urlToLoad.replaceFirst("julesybean://", "https://")
            }
            updateLastValidInternalUrl(urlToLoad)
            geckoSession.loadUri(urlToLoad)
            return true
        }
        return false
    }

    private fun updateLastValidInternalUrl(url: String) {
        lastValidInternalUrl = url
        saveCurrentInternalUrl()
    }

    override fun onPause() {
        super.onPause()
        saveCurrentInternalUrl()
    }

    override fun onStop() {
        super.onStop()
        saveCurrentInternalUrl()
    }

    private fun saveCurrentInternalUrl() {
        val urlToSave = lastValidInternalUrl
        if (urlToSave != null) {
            sharedPreferences.edit().putString(KEY_LAST_URL, urlToSave).apply()
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
        var backPressTime: Long = 0
        backPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (backPressTime + 2000 > System.currentTimeMillis()) {
                    this.isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    this.isEnabled = true
                } else {
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
        geckoSession.loadUri(js)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeGesture() {
        val swipeListener = object : SwipeGestureListener(this) {
            override fun onSwipeRight() {
                openNavigationMenu()
            }
        }
        gestureDetector = GestureDetector(this, swipeListener)

        geckoView.setOnTouchListener { _, event ->
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
        geckoSession.loadUri(js)
    }

    private fun isDarkModeEnabled(): Boolean {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun injectDarkModeScript() {
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
        geckoSession.loadUri(js)
    }

    private fun injectMobileFriendlyScript() {
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
        geckoSession.loadUri(js)
    }
}
