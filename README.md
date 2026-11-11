# Julesybean Operator & Developer Manual

> Mobile-optimized native Android client and WebView runtime wrapper for Google Jules (`https://jules.google.com`), featuring native deep linking, prompt library management, responsive CSS/DOM injection, dynamic dark mode synchronization, and gesture-driven workflows.

---

## 1. Project Overview

**Julesybean** (`com.julesybean.app`) is an Android application designed to deliver a first-class mobile experience for Jules (`https://jules.google.com`). Standard mobile browsers often struggle with complex web application layouts, failing to maximize conversational real estate, lacking native touch ergonomics, and requiring manual context switching. Julesybean solves this by wrapping the web application in a security-hardened `android.webkit.WebView` container augmented with native platform primitives.

### Primary Architecture & Key Capabilities
* **Hardened WebView Container**: Isolates web browsing sessions, strictly constraining navigations to `jules.google.com` and its subdomains (`*.jules.google.com`) while allowing essential authentication flows (`accounts.google.com`). Non-internal links are safely dispatched to the Android system browser (`Intent.ACTION_VIEW`).
* **Deep Link Normalization & Routing**: Intercepts `http://`, `https://`, `jules://`, and `julesybean://` protocols, automatically sanitizing and rewriting them to secure `https://` endpoints before loading.
* **Dynamic DOM & CSS Injection**: Injects CSS styles directly into the DOM tree upon page load to eliminate bulky desktop sidebars (code panels, complementary elements), enforce readable base typography (16px), prevent horizontal viewport clipping, and expand the chat canvas to full viewport width.
* **System Dark Mode Synchronization**: Observes native Android night mode configuration (`UI_MODE_NIGHT_MASK`) and coordinates the DOM's theme classes (`dark` class and `data-theme="dark"` / `data-theme="light"`).
* **Native Camera & File Upload Integration**: Bridges web `<input type="file">` elements to native system file pickers and the Android `MediaStore.ACTION_IMAGE_CAPTURE` camera intent through an isolated `FileProvider`.
* **Prompt Manager Subsystem**: A dedicated native activity (`PromptManagerActivity`) powered by an offline SQLite database (`prompt_manager.db`) enabling rapid creation, editing, tagging, full-text searching, sorting, clipboard copying, and `.txt` import/export of prompt templates.
* **Ergonomic Native Controls**: Provides swipe-right gestures for opening the navigation drawer, single-tap back button scrolling to the bottom of the active chat thread, and double-tap back button app exit.

---

## 2. Prerequisites

### Hardware Requirements
* **Workstation (Build Host)**:
  * Minimum 4 physical CPU cores (x86_64 or ARM64 / Apple Silicon).
  * Minimum 8 GB RAM (16 GB recommended for running Android Virtual Devices / emulators alongside the Gradle daemon).
  * Minimum 10 GB free disk space for Android SDK, build tools, Gradle caches, and system images.
* **Target Android Device / Emulator**:
  * Architecture: `arm64-v8a`, `armeabi-v7a`, `x86_64`, or `x86`.
  * Minimum Android Version: Android 7.0 Nougat (API Level 24).
  * Target Android Version: Android 14 UpsideDownCake (API Level 34).

### Supported Host Operating Systems
* **Linux**:
  * Ubuntu 20.04 LTS / 22.04 LTS / 24.04 LTS
  * Linux Mint 20.x / 21.x
  * Debian 11 (Bullseye) / 12 (Bookworm) / DietPi (Debian-based)
  * Fedora 38+ / Arch Linux
* **macOS**: macOS 12 (Monterey) or newer (Apple Silicon M1/M2/M3 or Intel).
* **Windows**: Windows 10 / 11 with WSL2 (Ubuntu) or native PowerShell with Android Studio CLI tools.

### Runtime Dependencies & SDK Components
* **Java Development Kit (JDK)**: OpenJDK 17 LTS (recommended) or OpenJDK 11.
* **Android SDK Command-Line Tools**: Latest version (`cmdline-tools;latest`).
* **Android SDK Platforms**: `platforms;android-34`.
* **Android SDK Build-Tools**: `build-tools;33.0.1` and `build-tools;34.0.0`.
* **Android Platform Tools**: `platform-tools` (providing `adb`).
* **Android Permissions Declared**:
  * `android.permission.INTERNET`: Required to communicate with `https://jules.google.com`.
  * Note: Camera and external storage permissions are not required at runtime because camera capture and document selection rely exclusively on scoped Storage Access Framework intents (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`) and `androidx.core.content.FileProvider`.

---

## 3. Installation & Setup

Execute the following steps on your workstation or build server. All commands use non-interactive flags (`-y`, `--yes`) suitable for headless CI/CD and terminal execution.

### Step 1: Install System Packages & JDK 17 (Debian / Ubuntu / Linux Mint)
```bash
sudo apt-get update -y && sudo apt-get install -y --no-install-recommends \
    git \
    curl \
    unzip \
    zip \
    openjdk-17-jdk-headless
```

Verify Java installation:
```bash
java -version
```
Expected output:
```text
openjdk version "17.0.x" ...
OpenJDK Runtime Environment ...
```

### Step 2: Install Android SDK Command-Line Tools (Headless / Standalone)
If Android Studio or the Android SDK is not already installed:
```bash
export ANDROID_HOME="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd "$ANDROID_HOME/cmdline-tools"

# Download official Google Android Command-Line Tools for Linux
curl -fsSL -o commandlinetools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
unzip -q commandlinetools.zip -d latest_extracted
mv latest_extracted/cmdline-tools latest
rm -rf latest_extracted commandlinetools.zip

# Persist environment variables
cat << 'EOF' >> ~/.bashrc
export ANDROID_HOME="$HOME/android-sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
EOF
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# Accept SDK licenses and install required packages non-interactively
yes | sdkmanager --licenses
sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;33.0.1"
```

### Step 3: Clone Repository
```bash
git clone https://github.com/sprillex/julesybean.git
cd julesybean
```

### Step 4: Verify Gradle Wrapper & Build Environment
```bash
./gradlew --version
```

Verify project builds cleanly:
```bash
./gradlew check
```

---

## 4. Configuration

The application is configured through Gradle build scripts, Android Manifest definitions, SharedPreferences keys, SQLite schemas, and DOM selectors.

### 4.1 Environment Variables
* `JAVA_HOME`: Absolute path to the JDK 17 installation (e.g., `/usr/lib/jvm/java-17-openjdk-amd64`).
* `ANDROID_HOME` or `ANDROID_SDK_ROOT`: Path to the local Android SDK root directory (e.g., `/opt/android-sdk` or `$HOME/android-sdk`).

### 4.2 Configuration Files & Properties

#### `gradle.properties`
Controls memory limits and optimizations for the Gradle JVM daemon:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=false
```

#### Maven Repositories (`settings.gradle.kts`)
Dependency resolution includes Google, Maven Central, Mozilla (GeckoView), and the Google Cloud Storage mirror:
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://maven.mozilla.org/maven2/") }
        maven { url = java.net.URI("https://maven-central.storage-download.googleapis.com/maven2/") }
    }
}
```

#### Shared Preferences (`JulesybeanPrefs`)
* **Storage Location**: `/data/data/com.julesybean.app/shared_prefs/JulesybeanPrefs.xml`
* **Key `last_url`** (String): Stores the last visited URL on `jules.google.com` or subdomains.
* **Default Value**: `https://jules.google.com`

#### SQLite Database Configuration
* **Database Name**: `prompt_manager.db`
* **Storage Location**: `/data/data/com.julesybean.app/databases/prompt_manager.db`
* **Table**: `saved_prompts`
* **Schema**:
  * `id`: `INTEGER PRIMARY KEY AUTOINCREMENT`
  * `title`: `TEXT NOT NULL`
  * `content`: `TEXT NOT NULL`
  * `created_at`: `INTEGER NOT NULL`
  * `updated_at`: `INTEGER NOT NULL`
  * `last_used_at`: `INTEGER` (Nullable)
  * `sort_order`: `INTEGER DEFAULT 0`

#### FileProvider Storage Mapping (`app/src/main/res/xml/file_paths.xml`)
Maps the app's external files directory for temporary camera capture:
```xml
<paths>
    <external-files-path name="my_images" path="Pictures/" />
</paths>
```

#### Web DOM Selectors (`MainActivity.kt`)
Selectors used by the injection engine to adapt `https://jules.google.com`:
```kotlin
private val CSS_SELECTOR_CHAT_CONTAINER = "main"
private val CSS_SELECTOR_CODE_PANEL = "aside, .code-panel, [role='complementary']"
private val CSS_SELECTOR_NAV_MENU = "button[aria-label='Main menu'], button[aria-label='Menu']"
private val CSS_SELECTOR_CHAT_SCROLL = "main, .chat-container"
```

---

## 5. Usage Guide

### 5.1 Build Commands (Gradle)

#### Compile & Run All Unit Tests
```bash
./gradlew test
```
*Sample Output:*
```text
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest
> Task :app:compileReleaseUnitTestKotlin
> Task :app:testReleaseUnitTest
> Task :app:test

BUILD SUCCESSFUL in 12s
43 actionable tasks: 43 executed
```

#### Run Android Lint Analysis
```bash
./gradlew lint
```

#### Run Full Project Verification (`check`)
```bash
./gradlew check
```

#### Clean Build Tree
```bash
./gradlew clean
```

#### Build Debug APK
```bash
./gradlew assembleDebug
```
The compiled APK is emitted to:
`app/build/outputs/apk/debug/app-debug.apk`

#### Build Release APK
```bash
./gradlew assembleRelease
```
The release APK is emitted to:
`app/build/outputs/apk/release/app-release-unsigned.apk`

---

### 5.2 Device Deployment & Execution

#### Check Connected Devices / Emulators
```bash
adb devices -l
```

#### Install Debug APK via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
*Or via Gradle directly:*
```bash
./gradlew installDebug
```

#### Launch Application Main Activity
```bash
adb shell am start -n com.julesybean.app/.MainActivity
```
*Sample Output:*
```text
Starting: Intent { cmp=com.julesybean.app/.MainActivity }
```

#### Launch Prompt Manager Activity Directly
```bash
adb shell am start -n com.julesybean.app/.PromptManagerActivity
```

---

### 5.3 Deep Link Testing via ADB

Verify scheme rewrites and URI handling using the Android Activity Manager CLI:

#### Test Custom Scheme `jules://`
```bash
adb shell am start -a android.intent.action.VIEW \
    -d "jules://jules.google.com/chat/session-1234" com.julesybean.app
```

#### Test Custom Scheme `julesybean://`
```bash
adb shell am start -a android.intent.action.VIEW \
    -d "julesybean://jules.google.com/chat/session-5678" com.julesybean.app
```

#### Test Standard HTTP Upgrade Scheme
```bash
adb shell am start -a android.intent.action.VIEW \
    -d "http://jules.google.com/chat/session-9999" com.julesybean.app
```

#### Monitor Runtime Logs with Logcat
Filter logcat to monitor WebView client loading, intent handoffs, and activity lifecycle events:
```bash
adb logcat -v time -s "MainActivity" "PromptManagerActivity" "Chromium" "WebView"
```

---

## 6. Architecture & Subsystems

```text
+-----------------------------------------------------------------------------------+
|                                  Android OS                                       |
+-----------------------------------------------------------------------------------+
       |                                       |                            |
  [Deep Link Intents]                    [User Touch]               [Storage Access]
       |                                       |                            |
       v                                       v                            v
+-----------------------+           +-----------------------+   +-------------------+
|      MainActivity     |           | SwipeGestureListener  |   | FileProvider / SAF|
|  - UrlUtils Validation| <=======> |  - Right Swipe Drawer |   | - Camera Captures |
|  - WebView Container  |           |  - Back Button Scroll |   | - File Uploads    |
+-----------------------+           +-----------------------+   +-------------------+
       |                                       |
       | Web Page Navigation                   | Opens Manager
       v                                       v
+-----------------------------------+   +-------------------------------------------+
|      android.webkit.WebView       |   |           PromptManagerActivity           |
|  +-----------------------------+  |   |  - RecyclerView List & Filtering          |
|  | Injected CSS (Mobile Layout)|  |   |  - Speed Dial FAB (Create / Import)       |
|  | Injected JS (Dark Mode Sync)|  |   |  - BottomSheet Actions (Edit/Share/Export)|
|  +-----------------------------+  |   |  - SQLite OpenHelper (prompt_manager.db)  |
+-----------------------------------+   +-------------------------------------------+
       |                                       |
       v                                       v
[SharedPreferences: last_url]           [SQLite: saved_prompts]
```

### 6.1 Subsystems Breakdown

1. **Navigation & Security Subsystem (`UrlUtils`, `MainActivity`)**:
   - Evaluates incoming Intent URIs and WebView navigation requests against strict host whitelists.
   - Internal hosts (`jules.google.com`, `*.jules.google.com`) are retained within the WebView.
   - Google authentication domains (`accounts.google.com`, `myaccount.google.com`) are allowed in WebView to preserve session state.
   - External domains are handed over to the Android OS browser using `Intent.ACTION_VIEW`.
   - Normalizes non-HTTPS schemes (`jules://`, `julesybean://`, `http://`) to secure `https://`.

2. **DOM Injection & Styling Engine (`MainActivity`)**:
   - Executes inside `WebViewClient.onPageFinished`.
   - Injects a responsive `<style>` block into `document.head` to hide code sidebars, enforce full-width chat containers, and set base typography.
   - Detects system UI night mode via `resources.configuration.uiMode` and manipulates document attributes (`documentElement.setAttribute('data-theme', ...)`).

3. **Touch & Gesture Engine (`SwipeGestureListener`, `MainActivity`)**:
   - Uses `android.view.GestureDetector` to intercept touch events on the WebView surface.
   - Translates horizontal right swipes (delta X > 100px, velocity > 100px/s) into JavaScript clicks targeting the web drawer menu button (`button[aria-label='Main menu']`).
   - Implements back-button interception: single tap executes JavaScript scrolling to the bottom of the chat container; double tap within 2000ms dismisses the activity.

4. **Prompt Manager & Persistence Engine (`PromptManagerActivity`, `PromptDatabaseHelper`, `PromptAdapter`)**:
   - Local relational store (`saved_prompts` in SQLite) providing zero-latency search, sorting (Alphabetical, Recently Used, Date Created), and CRUD operations.
   - Supports importing one or multiple `.txt` files through the Android Storage Access Framework (`ActivityResultContracts.OpenMultipleDocuments`).
   - Supports exporting individual prompts to `.txt` files with sanitized filenames (`ActivityResultContracts.StartActivityForResult` with `ACTION_CREATE_DOCUMENT`).
   - Single tap on any prompt item copies prompt content directly to the system clipboard, triggers light haptic feedback, and updates the `last_used_at` timestamp.

5. **Threading & Concurrency Model**:
   - **Main UI Thread**: Coordinates activity lifecycles, RecyclerView view bindings, WebView rendering, dialog presentation, and animations.
   - **Chromium IO & Render Threads**: Handled asynchronously by the Android WebView runtime.
   - **Storage Operations**: Small preference updates (`SharedPreferences.apply()`) run asynchronously off the main thread; SQLite operations run against optimized indexed local storage; SAF file stream transfers occur during user file operations.

---

## 7. Troubleshooting

| Symptom / Error | Root Cause | Actionable Recovery Steps |
| :--- | :--- | :--- |
| `SDK location not found. Define location with an ANDROID_SDK_ROOT environment variable...` | Gradle cannot locate the Android SDK directory. | Set `export ANDROID_HOME=/path/to/android-sdk` in your shell, or create a `local.properties` file in the project root with `sdk.dir=/path/to/android-sdk`. |
| `Unsupported class file major version 65` or Java version mismatch | Building with an incompatible JDK version (e.g., JDK 21+ with older Gradle or JDK 8). | Install JDK 17: `sudo apt-get install -y openjdk-17-jdk-headless` and set `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`. |
| HTTP 429 Too Many Requests during dependency download | Cloudflare or upstream rate limiting on Maven Central. | The repository includes a Google Storage Maven Mirror in `settings.gradle.kts`. Run `./gradlew build --refresh-dependencies`. |
| Deep link opens system browser instead of Julesybean | Intent filter missing in manifest or domain mismatch. | Verify host matches `jules.google.com` or `*.jules.google.com`. Ensure you test using package target: `adb shell am start -a android.intent.action.VIEW -d "<url>" com.julesybean.app`. |
| External URLs fail to load in WebView | Expected security behavior. External URLs are intentionally redirected to the system browser. | Verify if the domain is intended to be internal. If it is an internal subdomain of Jules, verify `UrlUtils.isInternalHost` logic. |
| Injected dark mode does not match system theme | System configuration changed while app was in background. | `MainActivity` queries system mode on `onPageFinished`. Reload or reopen the page to trigger theme synchronization. |
| `FileProvider: Failed to find configured root that contains ...` | Camera image output path does not match `file_paths.xml`. | Ensure image capture uses `getExternalFilesDir(Environment.DIRECTORY_PICTURES)` matching `<external-files-path name="my_images" path="Pictures/" />`. |
| Prompt export fails with `IOException` | Destination file picker cancelled or permission denied by user. | Check Toast output. Ensure storage destination selected in document creator has write permissions. |

---

## 8. Detailed API Reference

For full technical specifications on URL rewriting algorithms, intent contract payloads, JSON error envelopes, DOM injection contracts, and SQLite database schemas, refer to the dedicated **[API.md](./API.md)** document.
