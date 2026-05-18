# 🚀 ParaWeather

ParaWeather is a modern, paragliding-centric Android safety assistant application built with **Jetpack Compose**, **Kotlin Coroutines**, **Room/Preferences DataStore**, **Retrofit**, and **Hilt**. 

Designed specifically for free-flight pilots (paragliders, hang-gliders, speed-wings), ParaWeather analyzes advanced aviation meteorology and local slope geometry to help you answer the ultimate question: *Is it safe and soarable to fly right now?*

---

## ✨ Key Features

### 1. 💨 Advanced Flyability Analysis Engine
Calculates safety ratings (**Excellent, Good, Marginal, Unflyable**) in real-time by analyzing:
* **Average Wind Speeds**: Enforces minimum soaring wind speeds and maximum penetration thresholds.
* **Gust Spread Turbulence**: Flags high-risk wind shear or wing collapse hazards if gusts exceed average wind by >10 km/h.
* **Precipitation**: Warns against parachutal stalls triggered by wet wing fabrics.
* **Atmospheric Instability**: Analyzes **CAPE (Convective Available Potential Energy)** and **Lifted Index (LI)** to warn of thunderstorm, overdevelopment, or cloud-suck hazards.
* **Takeoff Headwind Alignment**: Dynamically calculates wind alignment offset angles against local launch headings to warn of crosswinds or dangerous tailwinds.

### 2. ☀️ Predictive Thermal Soaring Estimator
Estimates dynamic thermal lift potential (**None, Low, Medium, High**) by correlating solar heating with convective ceilings:
* **Shortwave Radiation (W/m²)**: Direct measure of solar power engine heating.
* **Boundary Layer Height (meters)**: Convective mixing depth representing maximum accessible flight altitude.

### 3. 🗺️ Interactive Topographical Site Map
* Powered by **Osmdroid (OpenStreetMap)**—runs natively with **zero Google Maps API keys or usage fees**.
* Supports **Topographical Terrain switching (OpenTopoMap)** showing exact slope contours—vital for ridge soaring.
* **Dynamic Viewport Search (Overpass API)**: Dragging or panning the map automatically queries the OpenStreetMap database for registered paragliding, hang-gliding, and free-flying launches visible on your screen.
* **Local In-Memory Cache**: Automatically caches a 50km buffer around your viewport, giving you 0ms scroll latency and preventing server spamming.
* **Custom Pins**: Long-press any mountain slope to drop a pin, custom-name it, and save it instantly.

### 4. ⏰ Background Thermal Alerts & Notifications
* Uses a hybrid **AlarmManager + WorkManager** loop to scan weather for checked alerts sites twice daily at exactly **6:00 AM** and **6:00 PM** local time.
* Survives phone reboots automatically (`RECEIVE_BOOT_COMPLETED`) to preserve alarm schedules without battery drain.
* Fires local high-importance heads-up notifications with sounds when exceptional thermal soaring windows are forecasted.

---

## 🛠️ Tech Stack & Architecture
* **UI**: 100% Jetpack Compose with Material 3 design and Custom Circular Wind Compass Canvas components.
* **DI**: Hilt (Dependency Injection).
* **Local Cache**: Jetpack Preferences DataStore with Moshi serialization.
* **Networking**: Retrofit 2 + OkHttp 4 client injecting custom Legitimate User-Agent headers to bypass Overpass ModSecurity blocks.
* **Diagnostics**: Multi-threaded WorkManager coroutine workers with static Hilt EntryPoint accessors.

---

## 🏗️ Getting Started & Local Builds

### Prerequisites
* Android Studio (Ladybug or newer).
* Android SDK 35 (Compiled) / Min SDK 26.
* Java Toolchain **JDK 21**.

### Fixing macOS Gradle `jlink` Blocks
If you are compiling on macOS and experience a build crash relating to Gradle JDK image transformations (`jlink does not exist`), add the following toolchain properties to your local `gradle.properties` to lock the build to Android Studio's pre-approved embedded JBR:

```properties
org.gradle.java.home=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home
org.gradle.java.installations.paths=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home
org.gradle.java.installations.auto-download=false
org.gradle.java.installations.auto-detect=false
```

### Build Commands
Compile and assemble debug APK over terminal:
```bash
./gradlew assembleDebug
```

---

## 🧪 Testing Guidelines

### 1. Automated Unit Tests
The project houses a comprehensive JUnit unit test suite covering prefect conditions, extreme gusts, convective thunderstorms, tailwinds, and all four levels of thermal solar scoring:
```bash
./gradlew testDebugUnitTest
```

### 2. Manual Background Alerts Scan Trigger (ADB)
You can manually force-trigger a background thermal scan and notification scan at any time over USB using this targeted intent broadcast:
```bash
adb shell am broadcast -a com.gzavagli.paraweather.TRIGGER_ALERT_SCAN -p com.gzavagli.paraweather
```
*(Check your terminal logcat outputs using `adb logcat | grep ParaWeatherAlerts` to see real-time scan diagnostics).*
