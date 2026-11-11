# Julesybean API & Interface Specifications

This document defines the interface contracts, deep linking schemes, intent handling specifications, WebView injection bridges, local storage contracts, and SQLite entity models for the **Julesybean** Android application (`com.julesybean.app`).

---

## 1. Overview & Protocol Endpoints

* **Base Web URL**: `https://jules.google.com`
* **Supported Protocols / Schemes**: `https://`, `http://`, `julesybean://`, `jules://`
* **Internal Target Domain Scope**: `jules.google.com` and all subdomains (`*.jules.google.com`)
* **Authentication Domain Scope**: `accounts.google.com`, `myaccount.google.com`
* **Application ID**: `com.julesybean.app`
* **Target Android Version**: SDK 34 (Android 14) / Min SDK 24 (Android 7.0)

Julesybean operates as a native Android container wrapping `https://jules.google.com`. Interfaces in this repository consist of Android Intent filters, scheme rewriting contracts, WebView client/chrome hooks, DOM injection bridges, local key-value storage contracts, and an offline SQLite persistence engine.

---

## 2. Authentication & Security Boundaries

### 2.1 Web Session Authentication
* **Session Model**: Cookie / token-based session managed within the WebView container directly by Google identity endpoints and `https://jules.google.com`.
* **Cookie Persistence**: Managed by `android.webkit.CookieManager` and `android.webkit.WebView` with DOM storage enabled (`domStorageEnabled = true`).

### 2.2 Security Boundaries & Open Redirect Prevention
* **Host Filtering**: All incoming Intent URIs and WebView navigation requests are strictly validated against `jules.google.com` and `*.jules.google.com` via `UrlUtils.isInternalHost()`.
* **Scheme Normalization**: Insecure (`http://`) and custom (`julesybean://`, `jules://`) URIs are rewritten to `https://` natively before loading in the WebView.
* **External Link Isolation**: Any external domain navigation attempt (outside `jules.google.com`, `*.jules.google.com`, and `accounts.google.com`) is intercepted by `WebViewClient.shouldOverrideUrlLoading` and delegated to the Android OS browser (`Intent.ACTION_VIEW`).
* **FileProvider Permissions**: Camera file capture uses temporary read/write URI grants (`Intent.FLAG_GRANT_READ_URI_PERMISSION`, `Intent.FLAG_GRANT_WRITE_URI_PERMISSION`) via `androidx.core.content.FileProvider`.

---

## 3. Standard Envelopes & Contract Formats

### 3.1 Deep Link Intent Payload Contract
Incoming deep links deliver URIs via `android.content.Intent` data.

```json
{
  "action": "android.intent.action.VIEW",
  "categories": [
    "android.intent.category.DEFAULT",
    "android.intent.category.BROWSABLE"
  ],
  "data": "julesybean://jules.google.com/chat/12345",
  "normalized_url": "https://jules.google.com/chat/12345"
}
```

### 3.2 File Chooser Callback Contract (`ValueCallback<Array<Uri>>`)
The file chooser and camera capture bridge returns an array of Android `Uri` objects or `null` upon cancellation.

#### Success Response (File / Photo Selected)
```json
{
  "status": 200,
  "result_code": "RESULT_OK",
  "data": [
    "content://com.julesybean.app.fileprovider/my_images/JPEG_20231025_120000_123456789.jpg"
  ]
}
```

#### Error / Cancellation Response
```json
{
  "status": 400,
  "result_code": "RESULT_CANCELED",
  "data": null,
  "action_taken": "Cleaned up temporary unwritten image file if created"
}
```

#### External Domain Handoff Envelope
```json
{
  "status": 302,
  "action": "android.intent.action.VIEW",
  "target_host": "external-domain.com",
  "handled_internally": false
}
```

---

## 4. Interfaces & Endpoints by Resource

### 4.1 Deep Link Intent Handler

Intercepts system-wide deep links matching internal domains and routes them into the active `MainActivity` WebView instance.

* **Action**: `android.intent.action.VIEW`
* **Categories**: `android.intent.category.DEFAULT`, `android.intent.category.BROWSABLE`
* **Exported**: `true`

#### Endpoint / Intent Routes

| Scheme | Host Pattern | Path Pattern | Rewrite Action |
| :--- | :--- | :--- | :--- |
| `https://` | `jules.google.com`, `*.jules.google.com` | `/*` | Load directly in WebView |
| `http://` | `jules.google.com`, `*.jules.google.com` | `/*` | Upgrade `http://` to `https://` |
| `julesybean://` | `jules.google.com`, `*.jules.google.com` | `/*` | Replace `julesybean://` with `https://` |
| `jules://` | `jules.google.com`, `*.jules.google.com` | `/*` | Replace `jules://` with `https://` |

#### Request Data Schema
```json
{
  "type": "object",
  "properties": {
    "scheme": { "type": "string", "enum": ["http", "https", "julesybean", "jules"] },
    "host": { "type": "string", "pattern": "^([a-zA-Z0-9-]+\\.)*jules\\.google\\.com$" },
    "path": { "type": "string", "default": "/" },
    "query": { "type": "string", "optional": true }
  },
  "required": ["scheme", "host"]
}
```

#### CLI Test Invocation
```bash
adb shell am start -a android.intent.action.VIEW -d "julesybean://jules.google.com/chat/abc" com.julesybean.app
```

---

### 4.2 Persistent URL State Interface (`JulesybeanPrefs`)

Persists and restores the last visited valid internal URL across application restarts.

* **Storage Engine**: Android `SharedPreferences`
* **Preference File Name**: `JulesybeanPrefs`
* **Key**: `last_url`
* **Default Value**: `https://jules.google.com`

#### State Schema
```json
{
  "preference_file": "JulesybeanPrefs",
  "keys": {
    "last_url": {
      "type": "string",
      "format": "uri",
      "example": "https://jules.google.com/chat/67890",
      "description": "Last visited valid internal domain URL"
    }
  }
}
```

#### Lifecycle Hooks
* `doUpdateVisitedHistory`: Updates in-memory `lastValidInternalUrl` if the visited host is internal.
* `onPause`: Flushes `lastValidInternalUrl` to `JulesybeanPrefs` under `last_url`.
* `onCreate`: Restores `last_url` on startup unless overridden by a deep link Intent.

---

### 4.3 WebView Navigation & Interception Interface (`WebViewClient`)

Guards internal web navigation and routes external links out of the application.

* **Handler Method**: `WebViewClient.shouldOverrideUrlLoading`

#### Interception Logic Matrix

| Target Host | Is Allowed Scope? | Action Taken |
| :--- | :--- | :--- |
| `jules.google.com` | Yes (Internal) | Allow WebView to load URL (`return false`) |
| `sub.jules.google.com` | Yes (Internal) | Allow WebView to load URL (`return false`) |
| `accounts.google.com` | Yes (Auth) | Allow WebView to load URL (`return false`) |
| `myaccount.google.com`| Yes (Auth) | Allow WebView to load URL (`return false`) |
| `google.com` | No | Launch `Intent.ACTION_VIEW` system browser (`return true`) |
| `github.com` | No | Launch `Intent.ACTION_VIEW` system browser (`return true`) |
| `example.com` | No | Launch `Intent.ACTION_VIEW` system browser (`return true`) |

---

### 4.4 File Chooser & Camera Bridge Interface (`WebChromeClient`)

Bridges web `<input type="file">` controls to native Android photo capture and file picking intents.

* **Handler Method**: `WebChromeClient.onShowFileChooser`
* **FileProvider Authority**: `com.julesybean.app.fileprovider`
* **Storage Path**: `Pictures/` (mapped via `res/xml/file_paths.xml`)

#### File Provider Path Schema (`res/xml/file_paths.xml`)
```xml
<paths>
    <external-files-path name="my_images" path="Pictures/" />
</paths>
```

#### Request / Intent Parameters
```json
{
  "chooser_title": "Choose an action",
  "content_type": "*/*",
  "image_capture_intent": {
    "action": "android.media.action.IMAGE_CAPTURE",
    "output_extra": "content://com.julesybean.app.fileprovider/my_images/JPEG_<timestamp>_<random>.jpg"
  }
}
```

---

### 4.5 DOM & Styling Injection Interfaces

Injected into the WebView page during `onPageFinished` to optimize web UI responsiveness and synchronize system dark mode.

#### CSS / DOM Selectors
* `CSS_SELECTOR_CHAT_CONTAINER`: `"main"`
* `CSS_SELECTOR_CODE_PANEL`: `"aside, .code-panel, [role='complementary']"`
* `CSS_SELECTOR_NAV_MENU`: `"button[aria-label='Main menu'], button[aria-label='Menu']"`
* `CSS_SELECTOR_CHAT_SCROLL`: `"main, .chat-container"`

#### DOM Injection Endpoints

##### A. Mobile Layout Script (`injectMobileFriendlyScript`)
* **Trigger**: `WebViewClient.onPageFinished`
* **Action**: Injects a `<style>` block into `document.head`.
* **Injected Styling Specifications**:
  * Enforces base font size: `16px !important`
  * Body/HTML overflow protection: `overflow-x: hidden; width: 100vw; max-width: 100%;`
  * Code panel visibility: `display: none !important;` on `$CSS_SELECTOR_CODE_PANEL`
  * Chat container width: `width: 100% !important; max-width: 100% !important; margin: 0 !important; padding: 10px !important;` on `$CSS_SELECTOR_CHAT_CONTAINER`

##### B. Dark Mode Synchronization (`injectDarkModeScript`)
* **Trigger**: `WebViewClient.onPageFinished`
* **Detection**: Evaluates system `Configuration.UI_MODE_NIGHT_MASK`.
* **Script Action**:
  * Dark mode active: Adds `dark` class to `document.documentElement` and sets `data-theme="dark"`.
  * Light mode active: Removes `dark` class from `document.documentElement` and sets `data-theme="light"`.

##### C. Scroll to Chat Bottom (`scrollToBottomOfChat`)
* **Trigger**: Single tap on Android back button (intercepted by `OnBackPressedCallback`).
* **JavaScript Action**:
  ```javascript
  (function() {
      var scrollContainers = document.querySelectorAll('main, .chat-container');
      if (scrollContainers.length > 0) {
          for(var i=0; i<scrollContainers.length; i++) {
              scrollContainers[i].scrollTop = scrollContainers[i].scrollHeight;
          }
      } else {
          window.scrollTo(0, document.body.scrollHeight);
      }
  })();
  ```

##### D. Navigation Menu Toggle (`openNavigationMenu`)
* **Trigger**: Swipe Right touch gesture (`SwipeGestureListener`).
* **JavaScript Action**:
  ```javascript
  (function() {
      var menuBtn = document.querySelector("button[aria-label='Main menu'], button[aria-label='Menu']");
      if (menuBtn) {
          menuBtn.click();
      }
  })();
  ```

---

### 4.6 Gesture & Back Key Event Handlers

#### Gesture Navigation (`SwipeGestureListener`)
* **Event**: Touch listener attached to `WebView`.
* **Thresholds**:
  * Minimum distance (`SWIPE_THRESHOLD`): `100 px`
  * Minimum velocity (`SWIPE_VELOCITY_THRESHOLD`): `100 px/s`
* **Action**: Right swipe calls `openNavigationMenu()`.

#### Back Button Double-Tap Interceptor (`OnBackPressedCallback`)
* **Timeout Window**: `2000 ms` (2 seconds)
* **First Tap**: Scrolls active chat container to bottom via `scrollToBottomOfChat()`.
* **Second Tap (within 2 seconds)**: Disables callback temporarily and passes event back to system to exit application.

---

### 4.7 Prompt Manager Database & IPC Interfaces

The Prompt Manager subsystem (`PromptManagerActivity`, `PromptDatabaseHelper`, `PromptUtils`) exposes a structured local SQLite contract and system share/file export interfaces.

#### A. SQLite Entity Contract (`saved_prompts`)

* **Database File**: `prompt_manager.db` (Version 1)
* **Table Name**: `saved_prompts`

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `INTEGER` | `PRIMARY KEY AUTOINCREMENT` | Unique identifier for prompt record. |
| `title` | `TEXT` | `NOT NULL` | Display title of the prompt template. |
| `content` | `TEXT` | `NOT NULL` | Full prompt content string. |
| `created_at` | `INTEGER` | `NOT NULL` | Timestamp (epoch ms) of creation. |
| `updated_at` | `INTEGER` | `NOT NULL` | Timestamp (epoch ms) of last edit. |
| `last_used_at` | `INTEGER` | `NULLABLE` | Timestamp (epoch ms) of last copy/use. |
| `sort_order` | `INTEGER` | `DEFAULT 0` | Optional manual display ordering index. |

#### B. Prompt Sort Enums (`PromptSortType`)
* `ALPHABETICAL`: Orders by `title COLLATE NOCASE ASC`.
* `RECENTLY_USED`: Orders by `COALESCE(last_used_at, updated_at) DESC`.
* `DATE_CREATED`: Orders by `created_at DESC`.

#### C. System Clipboard Copy Contract
* **MIME Type**: `text/plain`
* **Clip Label**: Prompt `title`
* **Clip Payload**: Prompt `content`
* **Side Effects**: Haptic feedback (`HapticFeedbackConstants.KEYBOARD_TAP`), Toast notification, updates `last_used_at` in SQLite.

#### D. System Share Sheet Contract
* **Action**: `android.intent.action.SEND`
* **Type**: `text/plain`
* **Extras**:
  * `Intent.EXTRA_SUBJECT`: Prompt `title`
  * `Intent.EXTRA_TEXT`: Prompt `content`

#### E. Document Export Contract
* **Action**: `android.intent.action.CREATE_DOCUMENT`
* **Categories**: `android.intent.category.OPENABLE`
* **Type**: `text/plain`
* **Suggested Title**: `PromptUtils.sanitizeFilename(title) + ".txt"`
* **Output Payload**: Full UTF-8 encoded string of `content`.

#### F. Document Import Contract
* **Action**: `ActivityResultContracts.OpenMultipleDocuments`
* **MIME Types**: `["text/plain", "*/*"]`
* **Mapping**:
  * `title`: Derived from document display name (removing `.txt` extension) or `Untitled Prompt - YYYY-MM-DD`.
  * `content`: UTF-8 string read from stream.

---

## 5. Pagination & Querying

When deep links contain path segments or query parameters (e.g., search queries, prompt parameters, or session routing tokens), they are preserved during URI normalization and passed intact to `https://jules.google.com`.

### Example Query Parameter Pass-Through
* **Incoming Deep Link**: `julesybean://jules.google.com/chat?q=android+webview&mode=compact`
* **Normalized Web Request**: `https://jules.google.com/chat?q=android+webview&mode=compact`
