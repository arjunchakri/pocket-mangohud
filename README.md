# Pocket HUD

A lightweight Android floating HUD for battery percentage and estimated remaining gaming time. It does not bundle an emulator, Linux runtime, game files, or DroidDeck.

## MVP
- Floating, draggable battery/playtime panel.
- Battery percentage from Android BatteryManager.
- Runtime estimate from charge counter/current when available.
- Fallback estimate from observed battery-level decline during the current session.
- No root required; uses Android's draw-over-other-apps permission.

## Build
Open in Android Studio with JDK 17 and Android SDK 35, sync Gradle, then run the app on a device. GitHub Actions builds a debug APK on each push.

## Accuracy
Android devices do not expose battery current and charge-counter readings consistently. If unavailable, Pocket HUD estimates drain from observed battery percentage changes; this fallback needs time and a measurable battery-level drop. Estimates vary with game load, brightness, network activity, and temperature.

Package: com.arjunchakri.pockethud. Minimum Android: 8.0 (API 26).
