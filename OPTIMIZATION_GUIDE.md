# Gander Optimization & Best Practices Guide

This guide documents the core optimizations, memory management guidelines, security guarantees, and cross-platform testing best practices implemented in Gander.

---

## 1. ⚡ Memory & Bitmap Lifecycle Management

### Problem
Generating image or video thumbnails (`Thumbs.kt`) requires downsampling, rotation, or scaling. Helper functions like `Bitmap.createBitmap` (for EXIF rotation) or `Bitmap.createScaledBitmap` (for video frame scaling) allocate new `Bitmap` objects. Leaving the original intermediate Bitmaps in memory until Garbage Collection (GC) can cause memory spikes and Out-Of-Memory (OOM) errors on low-spec devices.

### Best Practice & Implementation
Always explicitly recycle intermediate `Bitmap` instances as soon as a transformed instance is created:

```kotlin
// Example: EXIF Image Rotation in Thumbs.kt
val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
if (rotated !== bmp) {
    bmp.recycle() // Explicitly free native memory of intermediate unrotated bitmap
}
return rotated

// Example: Video Frame Downscaling in Thumbs.kt
val scaled = Bitmap.createScaledBitmap(
    frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true
)
if (scaled !== frame) {
    frame.recycle() // Explicitly free unscaled video frame
}
return scaled
```

---

## 2. 💻 Cross-Platform Host Unit Testing (Windows / macOS / Linux)

### Problem
Android Studio unit tests (`./gradlew testDebugUnitTest` using Robolectric/JUnit) run directly on the developer's host machine JVM. On Windows workstations:
1. `File.separatorChar` is `\` instead of `/`.
2. Windows drive letters (`C:\`) appear in file paths.
3. Git checkouts on Windows convert LF (`\n`) to CRLF (`\r\n`).

These differences cause local JVM unit tests to fail on Windows machines even when the Android runtime (which is Linux-based) works perfectly.

### Best Practice & Implementation
1. **Path Normalization in SAF Utilities (`StorageUris.kt`)**:
   Always normalize path separators and strip drive letter prefixes when resolving Document IDs:
   ```kotlin
   internal fun folderDocId(path: String, primaryRoot: String): String? {
       val normPath = path.replace('\\', '/').substringAfter(':')
       val normRoot = primaryRoot.replace('\\', '/').substringAfter(':')
       return when {
           normPath.startsWith(normRoot) ->
               "primary:" + normPath.removePrefix(normRoot).trimStart('/')
           // ...
       }
   }
   ```

2. **Line-Ending Normalization in Test Fixtures (`ZipReaderTest.kt`)**:
   Normalize CRLF (`\r\n`) to LF (`\n`) before comparing text bytes in unit tests:
   ```kotlin
   private fun assertTextBytesEqual(actual: ByteArray, expected: ByteArray) {
       val actualText = actual.toString(Charsets.UTF_8).replace("\r\n", "\n")
       val expectedText = expected.toString(Charsets.UTF_8).replace("\r\n", "\n")
       assertThat(actualText).isEqualTo(expectedText)
   }
   ```

---

## 3. 📄 System File Associations & Intent Filtering

### Problem
Third-party file managers (e.g., Samsung My Files, Google Files) and email clients send non-standard or legacy MIME types for Microsoft PowerPoint files (`.ppt`/`.pptx`), such as `application/vnd.ms-powerpoint` or `application/x-mspowerpoint`. If these MIME types are missing from `AndroidManifest.xml` or `FileKind.kt`, tapping a PowerPoint file in a file manager fails to offer Gander as an opening choice.

### Best Practice & Implementation
1. **Manifest Registration (`AndroidManifest.xml`)**:
   Include all known legacy and registered MIME aliases under both `VIEW` and `SEND` intent filters:
   ```xml
   <data android:mimeType="application/vnd.ms-powerpoint" />
   <data android:mimeType="application/mspowerpoint" />
   <data android:mimeType="application/ms-powerpoint" />
   <data android:mimeType="application/x-mspowerpoint" />
   <data android:mimeType="application/powerpoint" />
   ```
2. **Include `<category android:name="android.intent.category.BROWSABLE" />`**:
   Allows system pickers and web links to offer Gander for document viewing.
3. **Synchronize `FileKind.kt`**:
   Ensure `slideMimes` set in `FileKind.kt` matches the Manifest and is verified by `FormatRegistryTest.kt`.

---

## 4. 🛡️ Security & Privacy Guarantees

### Zero-Permission Model
- **No Network Access**: Transitive library permissions (such as Media3's `ACCESS_NETWORK_STATE`) are explicitly stripped in `AndroidManifest.xml`:
  ```xml
  <uses-permission
      android:name="android.permission.ACCESS_NETWORK_STATE"
      tools:node="remove" />
  ```
- **Build Invariant Enforcement**: The `checkPermissions` Gradle task fails the build if any unauthorized permission is merged into the final APK.

### Intent Redirection Protection
- `ViewerActivity.mayOpenFromOutside(uri)` validates incoming `content://` URIs and rejects any URI matching Gander's own package authority when launched from external applications, preventing Intent Redirection attacks against internal providers.

### WebView Metrics Opt-Out
- WebView diagnostic uploads are disabled via manifest meta-data:
  ```xml
  <meta-data android:name="android.webkit.WebView.MetricsOptOut" android:value="true" />
  <meta-data android:name="android.webkit.WebView.EnableSafeBrowsing" android:value="false" />
  ```

---

## 5. 📦 Automated Build & Release Signing Fallback

### Release Signing Fallback (`app/build.gradle.kts`)
Configure the `release` build type to automatically fall back to the `debug` signing key when `keystore/gander.jks` is absent. This ensures contributors can immediately build installable release APKs without needing secret credentials:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
    }
}
```

### Portable One-Click Packaging Script (`Build_APKs.bat`)
Avoid hardcoding user-specific paths (e.g., `C:\Users\Administrator\...`). Use dynamic environment variables (`%USERPROFILE%`, `%LOCALAPPDATA%`, `%ProgramFiles%`) and script-relative directory navigation (`cd /d "%~dp0"`):

```cmd
@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"
set "ANDROID_PREFS_ROOT="

if not defined ANDROID_USER_HOME (
    set "ANDROID_USER_HOME=%USERPROFILE%\.android"
)

if not defined ANDROID_HOME (
    if exist "%LOCALAPPDATA%\Android\Sdk" (
        set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
        set "ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk"
    )
)

call gradlew.bat assembleDebug assembleRelease
pause
```

---

## 6. 🧹 Code Hygiene & Deprecation Best Practices

1. **Kotlin 1.9+ Enum Entries**: Prefer `Enum.entries` over `Enum.values()` to avoid allocating array copies on every call:
   ```kotlin
   val orders = Settings.SortOrder.entries
   ```
2. **Media3 Compatibility Suppressions**: Suppress Java deprecation warnings on required Media3 APIs (`PlayerNotificationManager.Builder`, `setOnFullScreenModeChangedListener`) using `@Suppress("DEPRECATION")`.
3. **Range Validation**: Use idiomatic range checks for numeric bounds (`if (start !in 0..end) return null`).
