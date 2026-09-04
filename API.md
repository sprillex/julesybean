# Julesybean API & Interface Specifications

This document defines the interface contracts, deep linking schemes, intent handling specifications, WebView injection bridges, and local data schemas for the **Julesybean** Android application (`com.julesybean.app`).

---

## Overview

* **Base Web URL**: `https://jules.google.com`
* **Supported Protocols / Schemes**: `https://`, `http://`, `julesybean://`
* **Internal Target Domain Scope**: `jules.google.com` and all subdomains (`*.jules.google.com`)
* **Application ID**: `com.julesybean.app`
* **Target Android Version**: SDK 34 (Android 14) / Min SDK 24 (Android 7.0)

Julesybean operates as a native Android container wrapping `https://jules.google.com`. Interfaces in this repository consist of Android Intent filters, scheme rewriting contracts, WebView client/chrome hooks, DOM injection bridges, and local key-value storage contracts.

---

## Authentication & Security

### Web Session Authentication
* **Scheme**: Standard web cookie / token-based session managed within the WebView container by `https://jules.google.com`.
* **Cookie Persistence**: Managed by `android.webkit.WebView` with DOM storage enabled (`domStorageEnabled = true`).

### Security Boundaries & Open Redirect Prevention
* **Host Filtering**: All incoming Intent URIs and WebView navigation requests are strictly validated against `jules.google.com` and `*.jules.google.com`.
* **Scheme Normalization**: Insecure (`http://`) and custom (`julesybean://`) URIs are rewritten to `https://` natively before loading in the WebView.
* **External Link Isolation**: Any external domain navigation attempt is intercepted and passed to the Android OS (`Intent.ACTION_VIEW`) to open in an external browser.
* **FileProvider Permissions**: Camera file capture uses temporary read/write URI grants (`Intent.FLAG_GRANT_READ_URI_PERMISSION`, `Intent.FLAG_GRANT_WRITE_URI_PERMISSION`) via `androidx.core.content.FileProvider`.

---

## Standard Envelopes & Error Formats

### Intent Payload Contract
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

### File Chooser Callback Contract (`ValueCallback<Array<Uri>>`)
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

## Interfaces & Endpoints by Resource

### 1. Deep Link Intent Handler

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

#### Request Data Schema
```json
{
  "type": "object",
  "properties": {
    "scheme": { "type": "string", "enum": ["http", "https", "julesybean"] },
    "host": { "type": "string", "pattern": "^([a-zA-Z0-9-]+\\.)*jules\\.google\\.com$" },
    "path": { "type": "string", "default": "/" },
    "query": { "type": "string", "optional": true }
  },
  "required": ["scheme", "host"]
}
```

#### Example Usage
```bash
adb shell am start -a android.intent.action.VIEW -d "julesybean://jules.google.com/chat/abc" com.julesybean.app
```

---

### 2. Persistent URL State Interface (`JulesybeanPrefs`)

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

### 3. WebView Navigation & Interception Interface (`WebViewClient`)

Guards internal web navigation and routes external links out of the application.

* **Handler Method**: `WebViewClient.shouldOverrideUrlLoading`

#### Interception Logic Matrix

| Target Host | Is Internal Scope? | Action Taken |
| :--- | :--- | :--- |
| `jules.google.com` | Yes | Allow WebView to load URL (`return false`) |
| `sub.jules.google.com` | Yes | Allow WebView to load URL (`return false`) |
| `google.com` | No | Launch `Intent.ACTION_VIEW` system browser (`return true`) |
| `example.com` | No | Launch `Intent.ACTION_VIEW` system browser (`return true`) |

---

### 4. File Chooser & Camera Bridge Interface (`WebChromeClient`)

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

### 5. DOM & Styling Injection Interfaces

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

### 6. Gesture & Back Key Event Handlers

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

## Pagination & Querying

When deep links contain path segments or query parameters (e.g., search queries, prompt parameters, or session routing tokens), they are preserved during URI normalization and passed intact to `https://jules.google.com`.

### Example Query Parameter Pass-Through
* **Incoming Deep Link**: `julesybean://jules.google.com/chat?q=android+webview&mode=compact`
* **Normalized Web Request**: `https://jules.google.com/chat?q=android+webview&mode=compact`
