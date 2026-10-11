# Custom Changelog for Gander

All custom modifications, enhancements, bug fixes, performance optimizations, and build improvements in this repository are documented below.

This codebase is synced with the upstream repository [hohojia886/gander](https://github.com/hohojia886/gander) up to commit [`ebf8d609a130b9a7bddffcc22b5da57c9b3415cd`](https://github.com/hohojia886/gander/commit/ebf8d609a130b9a7bddffcc22b5da57c9b3415cd) (v2.4 Release).

---

## v2.4.1 - Custom Enhancements & Maintenance Release

### 📄 File Associations & Format Support
- **PowerPoint (`.ppt`) Support**: Added full Microsoft PowerPoint MIME type definitions (`application/vnd.ms-powerpoint`, `application/mspowerpoint`, `application/ms-powerpoint`, `application/x-mspowerpoint`, `application/powerpoint`) to `AndroidManifest.xml` and `FileKind.kt`.
- **System Intent Filter Category**: Added `<category android:name="android.intent.category.BROWSABLE" />` to `AndroidManifest.xml` intent filters for broader compatibility with third-party file managers and system pickers.
- **Format Verification**: Updated `FormatRegistryTest.kt` with PowerPoint MIME routing tests.

### 📱 User Experience & System Settings
- **System Default App Settings Entry**: Added an "Open by default settings" (`open_default_settings`) row in the "About Gander" dialog (`dialog_about.xml`).
- **System Settings Launching**: Implemented `openDefaultAppSettings()` in `MainActivity.kt` to directly launch Android System Default App Settings (`ACTION_APP_OPEN_BY_DEFAULT_SETTINGS` / `ACTION_APPLICATION_DETAILS_SETTINGS`), allowing users to easily set Gander as their default document viewer.

### ⚡ Performance & Memory Optimization
- **Bitmap Recycling**: Added explicit `.recycle()` calls on intermediate Bitmaps in `Thumbs.kt` (`imageThumb` and `videoThumb`) during image rotation and video frame scaling, reducing GC overhead and preventing Out-Of-Memory (OOM) errors during thumbnail generation.

### 💻 Host Workstation Test Compatibility
- **Windows Path Normalization**: Fixed `StorageUris.kt` path normalization in `folderDocId()` to support Windows drive letter prefixes (`C:\`) and backslashes (`\`) during local host unit testing.
- **Line Ending Normalization**: Implemented `assertTextBytesEqual()` in `ZipReaderTest.kt` to normalize CRLF (`\r\n`) to LF (`\n`) for seamless cross-platform test assertions on Windows workstations.

### 🧹 Code Hygiene & Modernization
- **Kotlin 1.9+ Enum Entries**: Replaced `Settings.SortOrder.values()` with `Settings.SortOrder.entries` in `MainActivity.kt`.
- **Media3 Deprecation Cleanup**: Added `@Suppress("DEPRECATION")` annotations for `PlayerNotificationManager.Builder` in `LockScreenControls.kt` and `setOnFullScreenModeChangedListener` in `VideoChrome.kt`.
- **UI Setter Warning Suppression**: Added `@Suppress("UsePropertyAccessSyntax")` on `toolbar.setNavigationIconTint()` in `NightChrome.kt` and `@SuppressLint("NotifyDataSetChanged")` on `RowAdapter.submit()` in `Rows.kt`.
- **Range & Parameter Cleanup**: Simplified Range validation in `DocRange.kt` (`start !in 0..end`) and removed the unused `ext` parameter from `Thumbs.kt`.

### 🧪 Unit Testing Expansion
- **Zip Encryption Test Suite**: Created `ZipEncryptionTest.kt` to verify password character set conversions across multi-locale environments (`passwordBytes()`).
- **Test Suite Health**: Expanded the unit test suite to **509 passing tests** with 0 failures and 0 Android Lint errors.

### 📦 Build Automation & Packaging
- **Custom Output APK Naming**: Configured Gradle in `app/build.gradle.kts` to output APKs named `Gander-v{versionName}-{variant}.apk` (e.g., `Gander-v2.3-debug.apk` and `Gander-v2.3-release.apk`).
- **Release Signing Fallback**: Configured release builds to automatically fall back to the `debug` signing key when a private release keystore is absent, producing installable release APKs out of the box.
- **Cross-Platform Packaging Script**: Created `Build_APKs.bat` in the root directory with dynamic environment variable detection (`JAVA_HOME`, `ANDROID_HOME`, `ANDROID_USER_HOME`) for one-click APK generation on Windows developer workstations.
