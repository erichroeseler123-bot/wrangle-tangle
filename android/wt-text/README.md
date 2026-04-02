# WrangleTangle Text Android App

Standalone Android project for the `WrangleTangle Text` SMS utility.

## Build

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

The build copies the debug APK to `dist/wt-text.apk`.

## Notes

- Namespace: `com.wrangletangle.text`
- UI: Jetpack Compose, dark mode, teal accent
- SMS sending uses individual `SmsManager.sendTextMessage()` calls, never a group thread
- Contact selection includes an `Add another` fallback for pickers that ignore `EXTRA_ALLOW_MULTIPLE`
