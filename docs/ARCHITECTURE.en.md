[English](ARCHITECTURE.en.md) | [简体中文](ARCHITECTURE.md)

# Android Credential Switcher Architecture

This document explains the core architecture of Android Credential Switcher, its three-key `Settings.Secure` model, the Android 15–17 read-restriction mirror algorithm, and HyperOS 4 selector behavior findings.

## 1. Core Architecture & Runtime Model

This utility targets <b>Android 14–17</b> (API 34–37, compiled cleanly down to `minSdkVersion 29`), specifically solving the missing "Additional services" Passkey toggles on <b>Xiaomi HyperOS 3/4</b>, <b>ColorOS</b>, and <b>OriginOS</b>. It is written strictly in standard Java with pure Android Framework APIs—zero third-party libraries, zero Gradle dependencies, and zero `INTERNET` permission.

### 1.1 Modular Project Structure

```
android-credential-switcher/
├── .github/
│   └── workflows/
│       └── build.yml                  # GitHub Actions zero-Gradle CI workflow
├── assets/
│   ├── logo.svg                       # Brand vector icon
│   └── preview-zh.png                 # Sanitized real-device screenshot
├── docs/
│   ├── ARCHITECTURE.en.md             # English architecture documentation
│   └── ARCHITECTURE.md                # Chinese architecture documentation
├── res/
│   ├── drawable/                      # Vector layers and legacy icon fallback
│   ├── drawable-anydpi-v26/           # Android 8+ adaptive icon definition
│   ├── values/                        # Default English app name
│   └── values-zh/                     # Chinese localized app name
├── src/io/github/toruolenyeva/credswitcher/
│   ├── CredentialStore.java           # Service discovery, Settings.Secure I/O, mirror state, cleanup
│   ├── MainActivity.java              # Single-screen controller and collapsible FAQ card
│   ├── Strings.java                   # Runtime EN/ZH dictionary with system locale auto-detection
│   └── UiKit.java                     # Day/Night palette, ripples, and safe system bar tinting
├── AndroidManifest.xml                # Minimal permissions and package intent queries
├── build.ps1                          # Windows offline CLI build script (auto SDK/JDK discovery)
├── build.sh                           # macOS / Linux offline CLI build script
├── CHANGELOG.md                       # Version changelog
├── LICENSE                            # MIT License
├── README.en.md                       # English README
└── README.md                          # Chinese README
```

```
+-------------------------------------------------------------------+
|                  Android / HyperOS Framework Layer                |
|  +------------------------+         +--------------------------+  |
|  |   PackageManager       |         |   Settings.Secure        |  |
|  |   (Intent Discovery)   |         |   (3-Key Persistence)    |  |
|  +-----------+------------+         +------------+-------------+  |
+--------------|-----------------------------------|----------------+
               | queryIntentServices               | WRITE_SECURE_SETTINGS
               v                                   v
+-------------------------------------------------------------------+
|            Android Credential Switcher (Zero-Network Process)     |
|  +-------------------------------------------------------------+  |
|  | CredentialStore.java : Atomic Write + Mirror + Ghost Clean  |  |
|  +-------------------------------------------------------------+  |
|  | Strings.java / UiKit.java / MainActivity.java : Adaptive UI |  |
|  +-------------------------------------------------------------+  |
+----------------------------------+--------------------------------+
                                   |
                                   v
                SharedPreferences ("cred_switcher_state")
```

## 2. `Settings.Secure` Three-Key Model & Mirror Algorithm

Android Credential Manager (Android 14+) and the Autofill Framework coordinate active password and passkey providers through three keys in `Settings.Secure`:

| Setting Key | Permission / Read Gate | Value Format | Responsibility & Linkage |
| :--- | :--- | :--- | :--- |
| `credential_service_primary` | Write via `WRITE_SECURE_SETTINGS`; Android 15+ read gated by system signature | Single `ComponentName` string or `null` | Sets the preferred Passkey provider; automatically included in `credential_service` and linked to matching `autofill_service` |
| `credential_service` | Same as above (protected by `LIST_ENABLED_CREDENTIAL_PROVIDERS`) | Colon-delimited `ComponentName` list | Controls "Additional services" toggles; allows multiple managers to participate concurrently |
| `autofill_service` | Remains directly readable via `Settings.Secure.getString` | Single `ComponentName` string or `null` | Controls traditional password autofill service; independently switchable or disableable |

### 2.1 Compatibility Strategy for Android 15–17 Read Restrictions

On Android 15, 16, and 17, calling `Settings.Secure.getString()` for `credential_service` and `credential_service_primary` from non-privileged apps silently returns `null`, whereas `Settings.Secure.putString()` with `WRITE_SECURE_SETTINGS` continues to work with full write authority. `CredentialStore.loadSnapshot()` resolves state in three steps:

1. <b>Live System Read First</b>: Whenever the ROM permits direct reading (non-null value), parse and validate live components from `Settings.Secure`.
2. <b>Initialized Mirror Fallback</b>: If system reads return `null` and `SharedPreferences` marks `initialized=true`, load the last mirrored `mirrored_primary` and `mirrored_creds`.
3. <b>First-Launch Autofill Bootstrap</b>: Before the user makes their first write, infer the active primary `CredentialProviderService` from the readable `autofill_service` package so initial UI reflects the real device setup.

### 2.2 Broken Pointer Cleanup

If an app is uninstalled or changes its service class name while still referenced in `Settings.Secure`, credential lookups can stall or fail silently. `detectGhostPointers()` probes every token with `PackageManager.getServiceInfo(cn, 0)` and surfaces a one-tap cleanup card whenever invalid entries exist.

## 3. HyperOS 4 Selector Analysis & Reset Defense

### 3.1 Why Locked Vaults Fall Back to Google on HyperOS 4

Inspection of HyperOS 4's built-in `MiuiCredentialManager` (customized with Compose and `miuix.appcompat.app.AlertDialog`) explains the observed fallback behavior:

- Stock AOSP Jetpack credential selector displays `AuthenticationEntryInfo` cards ("Tap to unlock vault") when an enabled provider requires authentication;
- `MiuiCredentialManager`'s `GetMiCredentialScreen` handles populated `PRIMARY_SELECTION` results, while routing locked-only or empty-credential states straight to `onIllegalUiState()`, terminating with `cancelCode = 3`;
- Browsers (Edge / Chrome) treat this cancellation as `TYPE_NO_CREDENTIAL` and fall back to Google Password Manager;
- Multi-provider choice lists only appear when unlocked providers return <b>2 or more</b> total matching entries for the site; a single entry shows a direct confirmation dialog.

### 3.2 Recovery from OEM Security Resets

Some OEM security daemons overwrite autofill and credential defaults during OTA updates or scheduled sweeps. Rather than keeping a battery-draining background service alive, this app stores confirmed selections locally and exposes a one-tap "Re-Apply Config to System" action to restore all three keys immediately without needing ADB again.

## 4. Zero-Gradle Build Pipeline & Security Contracts

### 4.1 Offline CLI Build Pipeline

`build.ps1` and `build.sh` orchestrate standard Android SDK tools directly without Gradle or remote Maven repositories:

1. `aapt2 compile`: Compiles localized `res/` strings and adaptive icon vectors;
2. `aapt2 link`: Generates `R.java` and links the base APK with uncompressed `resources.arsc`;
3. `javac --release 11`: Compiles `src/` and generated `R.java` into Java 11 bytecode;
4. `d8 --min-api 29`: Desugars and packages class files into a single `classes.dex`;
5. `zip` + `zipalign -p -f 4`: Inserts `classes.dex` into the APK and aligns entries on 4-byte boundaries;
6. `apksigner sign` + `verify`: Signs the APK with the local ignored keystore and verifies V2/V3 certificates.

### 4.2 Permission Boundary & Storage Schema

- <b>Least Privilege</b>: Requests only `android.permission.WRITE_SECURE_SETTINGS` (granted once via ADB, automatically revoked on uninstall) and `android.permission.QUERY_ALL_PACKAGES` (paired with `<queries>` for provider enumeration), with zero network, storage, or account permissions.
- <b>`SharedPreferences` Keys (`cred_switcher_state`)</b>: Stores only `initialized` (boolean), `mirrored_primary` (String), `mirrored_creds` (String), and `ui_lang` (`"auto"` / `"zh"` / `"en"`), with `android:allowBackup="false"` enforced in `AndroidManifest.xml`.
