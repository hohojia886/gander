# Gander Optimization & Architecture Guide

This guide documents the engineering principles, APK size reduction techniques, execution performance optimizations, security recoveries, and build automation practices implemented in Gander.

---

## 1. 📉 APK Size Optimization (4.96 MB ➔ 4.50 MB, −453 KB / −9.1% Reduction)

### A. Resource Locale Filtering (`resources.arsc` Stripping)
- **Problem**: Dependent libraries (`appcompat`, `material`, `media3`) bundle string resources for 80+ locales into `resources.arsc`. Because `resources.arsc` must remain uncompressed in APKs targeting Android 11+ (`targetSdk >= 30`), these unused localizations add over 600 KB of uncompressible bloat.
- **Optimization**: Filter resource localizations to English in `app/build.gradle.kts`:
  ```kotlin
  androidResources {
      localeFilters += listOf("en")
  }
  ```
- **Impact**: Saved **~500 KB** directly from `resources.arsc`.

### B. Metadata & Kotlin Resource Exclusion
- **Optimization**: Exclude unnecessary Kotlin runtime metadata, debug probes, and version files in `app/build.gradle.kts`:
  ```kotlin
  packaging {
      resources.excludes += listOf(
          "kotlin/**",
          "META-INF/*.version",
          "META-INF/**/*.kotlin_module",
          "DebugProbesKt.bin",
          "kotlin-tooling-metadata.json"
      )
  }
  dependenciesInfo {
      includeInApk = false
      includeInBundle = false
  }
  ```
- **Impact**: Saved **~15–20 KB**.

### C. Media3 Selective Renderers (DEX Trimming)
- **Problem**: Calling default `ExoPlayer.Builder(context)` pulls in `DefaultRenderersFactory` and `DefaultMediaSourceFactory`, retaining DASH, HLS, RTSP, CameraMotion, and AdInsertion classes that R8 cannot safely prune.
- **Optimization**: Explicitly instantiate only the MediaCodec video/audio renderers and progressive media source required for local media files in `ViewerActivity.kt`:
  ```kotlin
  val renderersFactory = RenderersFactory { handler, videoListener, audioListener, textListener, _ ->
      arrayOf(
          MediaCodecVideoRenderer(this, MediaCodecSelector.DEFAULT, 5000L, handler, videoListener, 50),
          MediaCodecAudioRenderer(this, MediaCodecSelector.DEFAULT, handler, audioListener),
          TextRenderer(textListener, handler.looper)
      )
  }
  val mediaSourceFactory = ProgressiveMediaSource.Factory(
      DefaultDataSource.Factory(this),
      DefaultExtractorsFactory()
  )
  val exo = ExoPlayer.Builder(this, renderersFactory, mediaSourceFactory)
      .setAudioAttributes(AudioAttributes.DEFAULT, true)
      .setHandleAudioBecomingNoisy(true)
      .build()
  ```
- **Impact**: Reduced DEX code footprint by trimming hundreds of unused Media3 classes.

### D. R8 Strict Full Mode & Compiler Assertion Flags
- **Optimization**: Enable R8 strict full mode in `gradle.properties`:
  ```properties
  android.r8.strictFullModeForKeepRules=true
  ```
  And strip redundant Kotlin runtime assertions in `app/build.gradle.kts`:
  ```kotlin
  kotlinOptions {
      jvmTarget = "17"
      freeCompilerArgs += listOf(
          "-Xno-param-assertions",
          "-Xno-call-assertions",
          "-Xno-receiver-assertions"
      )
  }
  ```

---

## 2. ⚡ Execution & Rendering Performance

### A. Asynchronous JS Parsing in Viewer HTML (`defer` Attribute)
- **Problem**: Viewer HTML files (`docx.html`, `pptx.html`, `xlsx.html`) loaded heavy JavaScript libraries synchronously inside `<head>` or before `<body>`. This blocked HTML body parsing and caused WebViews to show a blank screen while parsing multi-megabyte JavaScript libraries.
- **Optimization**: Add the `defer` attribute to `<script>` tags in `docx.html`, `pptx.html`, and `xlsx.html`:
  ```html
  <script defer src="lib/jszip3.min.js"></script>
  <script defer src="lib/docx-preview.min.js"></script>
  ```
- **Impact**: Enables `<body>` and the `#vw-status` ("Rendering document...") status card to render immediately without blocking on script downloads.

### B. RecyclerView ViewHolder Caching (`Rows.kt`)
- **Problem**: Calling `findViewById` repeatedly inside `onBindViewHolder` scans the view hierarchy every time an item is bound during scrolling, creating UI frame drops (scrolling jank).
- **Optimization**: Implement dedicated `ViewHolder` classes that cache view references in `onCreateViewHolder`:
  ```kotlin
  internal class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
      val badge: TextView = view.findViewById(R.id.badge)
      val thumb: ImageView = view.findViewById(R.id.thumb)
      val title: TextView = view.findViewById(R.id.title)
      val subtitle: TextView = view.findViewById(R.id.subtitle)
  }

  override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
      when (val row = rows[position]) {
          is Row.Item -> {
              val itemHolder = holder as ItemViewHolder
              itemHolder.title.text = row.title
              itemHolder.subtitle.text = row.subtitle
              // Directly update cached Views
          }
      }
  }
  ```

### C. Thumbnail Disk Cache & Memory Optimization (`Thumbs.kt`)
- **Disk Format**: Changed disk cache encoding from PNG to JPEG 85 (`Bitmap.CompressFormat.JPEG, 85`). JPEG encodes 5–10x faster and reduces thumbnail cache disk file size by 3–5x (~10 KB vs ~50 KB per thumbnail).
- **Byte-Bounded LruCache**: Sized memory `LruCache` by actual allocated bytes rather than fixed item count:
  ```kotlin
  private val mem = object : LruCache<String, Bitmap>(8 * 1024) { // 8 MB limit
      override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
  }
  ```

---

## 3. 💾 Memory & Bitmap Lifecycle Management

### Bitmap Recycling Rule
Generating image/video thumbnails (`Thumbs.kt`) requires EXIF rotation or downscaling via `Bitmap.createBitmap` or `Bitmap.createScaledBitmap`. Always explicitly invoke `.recycle()` on intermediate Bitmaps when a new transformed instance is created:

```kotlin
// Image EXIF Rotation
val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
if (rotated !== bmp) {
    bmp.recycle() // Explicitly free native memory of intermediate unrotated bitmap
}
return rotated

// Video Frame Downscaling
val scaled = Bitmap.createScaledBitmap(
    frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true
)
if (scaled !== frame) {
    frame.recycle() // Explicitly free unscaled video frame
}
return scaled
```

---

## 4. 🛡️ Security Architecture & Attack Surface Recovery

1. **Category Minimization**: Excluded `<category android:name="android.intent.category.BROWSABLE" />` from `AndroidManifest.xml` to prevent web page `intent://` links from launching `ViewerActivity` with arbitrary third-party URIs.
2. **Unsupported Format Routing**: Removed legacy `.ppt` OLE/CFB MIME types from `slideMimes` in `FileKind.kt` and `AndroidManifest.xml`. Legacy `.ppt` files route cleanly to `UNSUPPORTED` cards rather than crashing inside `pptx.js`.
3. **Intent Redirection Guard**: `ViewerActivity.mayOpenFromOutside(uri)` validates incoming URIs and rejects any URI matching Gander's own package authority when launched from external applications.
4. **Strict Content Security Policy**: `ViewerPolicy.CSP` enforces strict CSP headers (`script-src 'self' 'wasm-unsafe-eval'`, `frame-src 'none'`, `connect-src 'self'`).
5. **Zero Network Permissions**: `ACCESS_NETWORK_STATE` is stripped via `tools:node="remove"` in `AndroidManifest.xml`, and the `checkPermissions` Gradle task fails the build if any network permission is merged.

---

## 5. 💻 Host Workstation Test Compatibility (Windows / macOS / Linux)

### Problem
Android Studio unit tests (`./gradlew testDebugUnitTest` via Robolectric/JUnit) run on the developer's host workstation JVM. On Windows:
- `File.separatorChar` is `\` instead of `/`.
- Windows drive letters (`C:\`) appear in path strings.
- Git checkouts convert LF (`\n`) to CRLF (`\r\n`).

### Best Practice & Implementation
1. **POSIX Path Preservation in Production (`StorageUris.kt`)**:
   Keep `folderDocId()` strictly POSIX for Android runtime:
   ```kotlin
   internal fun folderDocId(path: String, primaryRoot: String): String? = when {
       path.startsWith(primaryRoot) ->
           "primary:" + path.removePrefix(primaryRoot).trimStart('/')
       path.startsWith("/storage/") -> {
           val rest = path.removePrefix("/storage/")
           rest.substringBefore('/') + ":" + rest.substringAfter('/', "")
       }
       else -> null
   }
   ```
2. **Test Fixture Line-Ending Normalization (`ZipReaderTest.kt`)**:
   Normalize CRLF (`\r\n`) to LF (`\n`) before comparing text bytes in unit tests:
   ```kotlin
   private fun assertTextBytesEqual(actual: ByteArray, expected: ByteArray) {
       val actualText = actual.toString(Charsets.UTF_8).replace("\r\n", "\n")
       val expectedText = expected.toString(Charsets.UTF_8).replace("\r\n", "\n")
       assertThat(actualText).isEqualTo(expectedText)
   }
   ```

---

## 6. 📦 Automated Building & Packaging

### Release Signing Fallback (`app/build.gradle.kts`)
```kotlin
signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
```
Automatically falls back to `signingConfigs.debug` when `keystore/gander.jks` is missing, producing installable release APKs out of the box.

### One-Click Packaging Script (`Build_APKs.bat`)
Uses dynamic environment variable detection (`%USERPROFILE%`, `%LOCALAPPDATA%`, `%ProgramFiles%`) and relative path navigation (`cd /d "%~dp0"`), enabling one-click APK generation across any Windows developer machine.
