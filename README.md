# julesybean

Julesybean is an Android application acting as a highly optimized WebView wrapper for `https://jules.google.com`. It provides a seamless, mobile-native experience by injecting custom styling and behavior.

## Features

*   **Deep Link Handling:** The app natively registers as the handler for internal URLs. It intercepts links to `jules.google.com` (and its subdomains) using `http://`, `https://`, and the custom `julesybean://` scheme. Any non-secure (`http` or `julesybean`) links are automatically upgraded to `https` before loading to ensure security.
*   **Persistent State:** The app automatically saves the last visited internal URL when closed or paused, and restores it upon reopening so you never lose your place. Opening a deep link overrides this saved state.
*   **Mobile-Friendly UI Injection:** Custom CSS and JavaScript are injected into the WebView to force a responsive layout. It adjusts font sizes for readability, hides intrusive panels (like code panels), and forces the chat container to take up the full width of the screen.
*   **Native Dark Mode Support:** The app detects the device's system-wide dark mode setting and injects scripts to automatically synchronize the website's theme (`data-theme="dark"` or `"light"`) with your native preferences.
*   **Gesture Navigation:** A custom gesture detector is implemented to allow users to swipe right anywhere on the screen to open the main navigation menu.
*   **Custom Back Button Handling:** Tapping the back button once intelligently scrolls to the bottom of the active chat window. Double-tapping the back button within two seconds exits the app.
*   **Media and File Uploads:** Full support for file choosers and camera intents. Users can upload existing files from their device storage or take a photo directly within the app, which securely passes the URI back to the web application via `FileProvider`.

## Definitions
* **Internal URL**: Any URL within the `jules.google.com` subdomain.

## Future Features
* **Save Chat Text**: In a future update, the app will remember any unsent text the user typed into the chat when the app is closed, and restore it upon reopening.
