# MUX Center

HUD style Notification Center, Control Center and status bar for Android. Package: `com.mux.center`

## How it works
- An **Accessibility Service** draws overlay windows above everything: the HUD status bar (with the swipe gesture zone) and the full screen panels.
- A **Notification Listener** reads notifications and media sessions for the Notification Center and the media card.
- **Modify system settings** lets the brightness slider and auto-rotate tile work. **Do Not Disturb access** is optional.

Android does not let normal apps switch Wi-Fi, Bluetooth, mobile data, airplane mode or hotspot, so those tiles open the system panel.

## Setup on the phone
1. Install the APK and open MUX Center.
2. Android 13+: App info -> three dots -> **Allow restricted settings**.
3. Follow the 4 steps on the SETUP tab (Accessibility, Notification access, Modify settings, optional extras).
4. Xiaomi / MIUI / HyperOS: turn on Autostart and set Battery saver to "No restrictions", otherwise the system kills the service.

## Build
Push to GitHub -> Actions -> "Build APK" -> download artifact `MUXCenter-debug-apk`.

## Releasing an update
1. Change `appVersionName` in `app/build.gradle.kts` (and `version` in `metadata.json`).
2. Push, wait for the build, download the APK.
3. GitHub -> Releases -> new release, tag `v1.0.1` (same number), attach the APK, publish.

The app checks `releases/latest` of `mahfuz0712/MUXCenter` on launch and from SYS -> CHECK FOR UPDATES.

Author: Mohammad Mahfuz Rahman, mahfuzrahman0712@gmail.com, https://github.com/mahfuz0712
