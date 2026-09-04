# julesybean

Julesybean is an Android application that serves as a mobile-optimized WebView wrapper for `https://jules.google.com`. It delivers a native Android experience by injecting responsive CSS styling, synchronizing device dark mode, intercepting system deep links, and providing native file upload and gesture navigation.

## Features

* **Deep Link Handling & Security**: Intercepts `http://`, `https://`, and `julesybean://` links for `jules.google.com` (and its subdomains) and securely rewrites non-HTTPS schemes to `https://`.
* **Persistent Session & URL Navigation**: Saves the last visited internal URL in `SharedPreferences` upon app pause or closure and restores it on relaunch.
* **Mobile Layout Injection**: Dynamically injects CSS into the loaded web application to hide bulky desktop sidebars (such as code panels), scale base text for readability, and maximize chat interface width.
* **Native Dark Mode Synchronization**: Automatically detects the system's night mode setting and injects themes (`data-theme="dark"` or `"light"`) to keep the web view in sync with device appearance.
* **Gesture Navigation**: Allows users to swipe right across the web view to trigger the main web navigation menu.
* **Smart Back Button Handling**: Single-tapping the back button scrolls to the bottom of the active chat window, while double-tapping within two seconds exits the app.
* **Native File & Camera Uploads**: Intercepts web file inputs using `WebChromeClient` and integrates Android `FileProvider` with image capture intents to support direct camera photos and local file uploads.

## Tech Stack & Architecture

* **Language**: Kotlin 1.9.0
* **Platform**: Android SDK 34 (Target: Android 14 / Min SDK: Android 7.0 - API 24)
* **Build System**: Gradle 8.3 with Kotlin DSL (`build.gradle.kts`)
* **Core Libraries**:
  * `androidx.core:core-ktx`: Kotlin extensions for Android framework APIs
  * `androidx.appcompat:appcompat`: Compatibility support for modern Android UI components
  * `com.google.android.material:material`: Material Design UI components
  * `androidx.constraintlayout:constraintlayout`: ConstraintLayout for responsive layout definitions
* **Storage Layer**: Android `SharedPreferences` (`JulesybeanPrefs`) for state persistence
* **IPC / Data Sharing**: `FileProvider` for secure temporary file sharing with external camera apps

## Repository Layout

```text
julesybean/
├── app/
│   ├── build.gradle.kts              # Application module build configuration and dependencies
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml   # App manifest, intent filters, permissions, provider setup
│           ├── java/com/julesybean/app/
│           │   ├── MainActivity.kt           # Main activity, WebView setup, deep links, injections
│           │   └── SwipeGestureListener.kt   # Gesture detector for right-swipe menu navigation
│           └── res/
│               ├── layout/activity_main.xml  # Layout containing full-screen WebView
│               ├── values/                  # Strings, colors, and theme definitions
│               ├── values-night/            # Night mode theme overrides
│               └── xml/file_paths.xml        # FileProvider storage paths mapping
├── build.gradle.kts                  # Top-level build script and plugin declarations
├── gradle.properties                 # Gradle JVM and build environment properties
├── settings.gradle.kts               # Project settings and module declarations
├── gradlew / gradlew.bat             # Gradle wrapper executables
├── README.md                         # Project overview and instructions
└── API.md                            # Interface specifications and deep link contracts
```

## Prerequisites & Setup

### Requirements
* **Java Development Kit (JDK)**: JDK 17 (recommended) or JDK 11
* **Android SDK**: Android SDK Platform 34 and Build-Tools 33.0.1+
* **Gradle**: Gradle 8.3 (provided via the included Gradle Wrapper)

### Step-by-Step Setup

1. **Clone the Repository**:
   ```bash
   git clone <repository-url>
   cd julesybean
   ```

2. **Verify Java & Android SDK Setup**:
   Ensure `JAVA_HOME` points to a valid JDK installation and `ANDROID_HOME` points to your Android SDK directory.

3. **Build the Project**:
   ```bash
   ./gradlew build
   ```

## Configuration

* **Manifest Intent Filters**: Configured in `app/src/main/AndroidManifest.xml` to intercept schemes (`http`, `https`, `julesybean`) for hosts matching `jules.google.com` and `*.jules.google.com`.
* **Shared Preferences**: Uses `JulesybeanPrefs` with the `last_url` key to store the most recent valid internal URL.
* **DOM Selectors Configuration**: `MainActivity.kt` contains easily tweakable CSS selectors for injected web behaviors:
  * `CSS_SELECTOR_CHAT_CONTAINER`: `"main"`
  * `CSS_SELECTOR_CODE_PANEL`: `"aside, .code-panel, [role='complementary']"`
  * `CSS_SELECTOR_NAV_MENU`: `"button[aria-label='Main menu'], button[aria-label='Menu']"`
  * `CSS_SELECTOR_CHAT_SCROLL`: `"main, .chat-container"`
* **FileProvider Paths**: Configured in `app/src/main/res/xml/file_paths.xml` mapping `Pictures/` to authority `${applicationId}.fileprovider`.

## Running the Application

### Assemble Debug APK
To assemble the debug APK without installing:
```bash
./gradlew assembleDebug
```
The output APK will be placed at `app/build/outputs/apk/debug/app-debug.apk`.

### Install Debug Build on Connected Device / Emulator
Ensure an Android emulator is running or a physical device is connected via ADB (`adb devices`):
```bash
./gradlew installDebug
```

### Build Release APK
To compile an optimized release APK:
```bash
./gradlew assembleRelease
```

### Testing Deep Links via ADB
Once installed on a device or emulator, test deep link handling with ADB:
```bash
adb shell am start -a android.intent.action.VIEW -d "julesybean://jules.google.com/chat/test" com.julesybean.app
```

## Testing

Run unit tests, linting, and build verification suites with the following commands:

* **Run Unit Tests**:
  ```bash
  ./gradlew test
  ```

* **Run Android Lint Analysis**:
  ```bash
  ./gradlew lint
  ```

* **Run All Project Verification Checks**:
  ```bash
  ./gradlew check
  ```

* **Complete Clean Build and Test Run**:
  ```bash
  ./gradlew clean build
  ```

## API Reference

The Julesybean application exposes native deep linking schemes, custom Intent routes, SharedPreferences data contracts, WebView Client/Chrome hooks, and JavaScript DOM injection interfaces.

For full specifications, parameter schemas, JSON data envelopes, security rules, and code examples, see the dedicated interface documentation in **[API.md](./API.md)**.
