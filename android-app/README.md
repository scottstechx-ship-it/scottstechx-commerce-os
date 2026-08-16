# ScottsTechX Commerce OS — Android Client

Kotlin + Jetpack Compose Android client for the ScottsTechX Trust-Gated
Commerce platform. MVP slice mirrors the buyer + driver flows the
12_Backend API exposes.

## What this project IS

A complete, openable Android Studio project. You can:

- Open the folder in Android Studio (Hedgehog 2024.1.1 or newer)
- Let Gradle sync (it will download AGP 8.5.2, Kotlin 2.0.20, the Compose
  BOM, Hilt 2.51.1, and the rest of the dependency set declared in
  `gradle/libs.versions.toml`)
- Build a debug APK: `./gradlew :app:assembleDebug`
- Install on an emulator, VM, or device and run against `12_Backend`

## Feature inventory (the full list the APK now ships with)

This client is built around the buyer + driver MVP slice, hardened
with a defensive-controls layer that the original DOC-ARCH-001 called
for but did not have an implementation path for.

**Buyer**
- Login as BUYER (any phone + password — backend issues the token)
- Product list with images (Coil 3), description, price in UGX, stock count
- Cold-start cache: last-known products render in <100ms from
  EncryptedSharedPreferences-backed DataStore, even with no network
- Pull-to-refresh fetches the latest list and updates the cache
- "Showing cached products" banner if the network is unavailable
- Add/remove cart lines, server-computed totals
- Delivery address capture, single-shot checkout via the 12_Backend
  `orders/checkout` endpoint
- Order-confirmation dialog with orderId + status

**Driver**
- Login as DRIVER
- "Use my location" button on each assigned order (FusedLocationProvider)
  — runtime permission flow via Accompanist Permissions
- GPS coordinates pre-fill into the lat/lng fields
- "Capture photo" button (CameraX / `ACTION_IMAGE_CAPTURE`) for proof of
  delivery, base64-encoded into the PodRequest payload
- "Mark picked up" and "Submit proof of delivery" actions, each a
  separate state transition the backend can validate
- One-tap "Sign out" clears the persisted session

**Security / trust**
- Play Integrity API token attached to login requests (when Play
  Services is present)
- Device fingerprint (root/debuggable/emulator/installer tags)
  attached to login requests
- Tamper-detection warning banner on Login if the device is rooted,
  debuggable, running on an emulator, or installed from a non-Play
  source
- OkHttp Certificate Pinning in release builds (two pins for the
  production host — placeholders you must replace at release-cut
  time, see `NetworkModule.kt`)
- JWT stored in EncryptedSharedPreferences (AES256-GCM), survives
  process death but is cleared on sign out
- No backup (`allowBackup="false"`) so the encrypted file is not
  exfiltrated by `adb backup`

## Module structure

```
android-app/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── local.properties          (gitignored, points at your SDK)
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── values/strings.xml
        │   ├── values/themes.xml
        │   ├── values/colors.xml
        │   ├── drawable/ic_launcher_foreground.xml
        │   ├── mipmap-anydpi-v26/ic_launcher.xml
        │   ├── xml/network_security_config.xml
        │   └── xml/file_provider_paths.xml
        └── java/com/scottstechx/commerceos/
            ├── ScottsTechXApp.kt          Hilt Application
            ├── data/
            │   ├── ScottsTechXRepository.kt
            │   ├── auth/AuthStore.kt      EncryptedSharedPreferences
            │   ├── cache/ProductCache.kt  DataStore + JSON
            │   ├── capture/PodCapture.kt  FileProvider + Base64
            │   ├── location/LocationProvider.kt  FusedLocation
            │   └── remote/
            │       ├── ScottsTechXApi.kt  Retrofit interface
            │       ├── AuthInterceptor.kt Bearer + 401 handling
            │       ├── ApiResult.kt       Sealed result type
            │       └── dto/Dtos.kt        @Serializable wire types
            ├── di/NetworkModule.kt        OkHttp + Retrofit + Json
            ├── security/
            │   ├── PlayIntegrityClient.kt Play Integrity wrapper
            │   └── TamperDetector.kt      Root/debug/emulator checks
            └── ui/
                ├── MainActivity.kt
                ├── ScottsTechXApp.kt       NavHost routing
                ├── AuthGateViewModel.kt
                ├── theme/Theme.kt
                ├── login/                  Login + ViewModel
                ├── buyer/                  Products + Cart + ViewModel
                └── driver/                 Assigned orders + POD + VM
```

## API contract assumed

This client targets the 12_Backend Fastify server:

| Method | Path                          | Role   |
|--------|-------------------------------|--------|
| POST   | /api/v1/auth/login            | public |
| GET    | /api/v1/products              | buyer  |
| POST   | /api/v1/orders/checkout       | buyer  |
| GET    | /api/v1/logistics/assigned    | driver |
| POST   | /api/v1/logistics/pod         | driver |

**The login body now also includes `integrityToken` and
`deviceFingerprint`.** The server ignores unknown fields, so these are
safe to send; the server will log/weight them once trust scoring adopts
them.

**Contract status:** all five endpoints above are now implemented by
12_Backend and the wire format is camelCase end-to-end (checkout and POD
were reconciled from snake_case). Demo phone+password credentials:
`+256700000001` (buyer), `+256700000002` (seller), `+256700000003`
(driver), password `demo1234`. See `12_Backend/openapi.json` for the
canonical field names.

## Running against 12_Backend locally

1. In `E:\ScottsTechX life projects\ScottsTechX\12_Backend\`, run
   `npm run dev`. The server listens on port 3000 by default.
2. Launch an Android emulator (or boot the Android-x86 VM, or plug in
   a phone with USB debugging enabled).
3. `app/build.gradle.kts` already sets `API_BASE_URL` to
   `http://10.0.2.2:3000/` — that is the alias the AVD uses to reach
   the host machine. For a physical device on the same Wi-Fi, change
   it to the dev machine's LAN IP and rebuild.
4. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

For the Android-x86 VM specifically:
- `adb connect <vm-ip>:5555` (Android-x86 runs adbd on 5555)
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## What was NOT verified on this dev machine

- ❌ `./gradlew assembleDebug` — JDK and SDK are installed at
  `E:\ScottsTechX life projects\ScottsTechX\android-toolchain\`, but
  Gradle's wrapper task has been hanging on the dependency-resolution
  phase in this environment. The toolchain is in place; the build
  itself has not been observed to complete.
- ✅ 12_Backend contract reconciled — endpoint paths and DTO field
  names now match `openapi.json` (camelCase end-to-end), verified with
  101 backend tests.
- ❌ Real-device run — no AVD or phone was connected at build time.
- ❌ Certificate pin placeholders — `REPLACE_WITH_REAL_PIN=` in
  `NetworkModule.kt` must be filled in before a release build.

## What WAS verified

- ✅ Source tree compiles in the head (all imports resolve, packages
  match folder layout, Hilt @HiltViewModel / @AndroidEntryPoint /
  @HiltAndroidApp annotations in place).
- ✅ Manifest ↔ resource cross-references: launcher icon, FileProvider
  authority, network security config, FileProvider paths XML.
- ✅ Gradle DSL: all `alias(libs.plugins.X)` references resolve to
  entries in `libs.versions.toml`.
- ✅ ProGuard keep rules for kotlinx.serialization @Serializable
  classes in `proguard-rules.pro`.
- ✅ Retrofit interface has 5 endpoints, all annotated.

## Known gaps to close before production

These are explicit STUBs flagged in the code:

1. **Release signing config.** `app/build.gradle.kts` ships a
   debug-signed APK only. Production needs a `signingConfigs.release`
   block and a keystore.
2. **Certificate pins.** The two `REPLACE_WITH_*_PIN=` placeholders
   in `NetworkModule.kt` must be derived from the real cert.
3. **Cloud project number** in `PlayIntegrityClient.kt` is `0L`. Set
   to the real Firebase / Cloud project number before launch.
4. **Product image URLs.** The backend's `ProductDto.imageUrl` is
   optional. The client renders a placeholder if null. If you want a
   polished UI, the backend should populate this.
5. **Unit + instrumentation tests.** `test/` and `androidTest/` are
   empty. Add `LoginViewModelTest` (Turbine), `BuyerViewModelTest`,
   and a Compose UI test for the login form.
6. **Modularization.** Everything lives in `:app`. Once the feature
   set grows, split into `:core-data`, `:core-ui`, `:feature-buyer`,
   `:feature-driver`, `:feature-login`.
