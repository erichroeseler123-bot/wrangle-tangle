# WrangleTangle Text Android App

Standalone Android project for the `WrangleTangle Text` SMS utility.

## Debug Build

1. Install Java 17.
2. Install Android SDK command-line tools and required packages.
3. Create `local.properties` with:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

4. Run:

```bash
./gradlew assembleDebug
```

The debug APK is produced at `build/outputs/apk/debug/app-debug.apk`.

## Release Build

Release signing material stays out of Git. The keystore should live outside this repo, and signing values should come from either environment variables or an ignored `signing.properties` file.

Supported signing keys:

```properties
WT_RELEASE_STORE_FILE=/absolute/path/to/release-keystore.jks
WT_RELEASE_STORE_PASSWORD=...
WT_RELEASE_KEY_ALIAS=wrangletangle-release
WT_RELEASE_KEY_PASSWORD=...
```

Example local `signing.properties`:

```properties
WT_RELEASE_STORE_FILE=/home/your-user/.android/wt-text/release-keystore.jks
WT_RELEASE_STORE_PASSWORD=replace-me
WT_RELEASE_KEY_ALIAS=wrangletangle-release
WT_RELEASE_KEY_PASSWORD=replace-me
```

Build the signed release APK:

```bash
./gradlew assembleRelease
```

The build copies the signed release APK to `dist/wt-text.apk`.

## Version Control Exclusions

These are intentionally excluded from Git:

- `local.properties`
- `signing.properties`
- keystores and other signing materials
- `.gradle/`, `.kotlin/`, `build/`, `dist/`
- heap dumps such as `*.hprof`

## Notes

- Namespace: `com.wrangletangle.text`
- UI: Jetpack Compose, dark mode, teal accent
- SMS sending uses individual `SmsManager.sendTextMessage()` calls, never a group thread
- Contact selection includes an `Add another` fallback for pickers that ignore `EXTRA_ALLOW_MULTIPLE`
