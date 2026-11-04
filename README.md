# Known Callers Only

A tiny Android app that only lets calls from your contacts list ring through. Anyone not in your
contacts, including hidden numbers, is silently declined. Voicemail and call logs are unaffected.

- Under 100 KB with no third-party libraries
- No internet permission: contacts are only checked on the device
- Android 10+ (API 29)

## How it works

The app registers as Android's call screening app (`CallScreeningService`). For each incoming call
it looks the number up in the device's contacts, including work-profile contacts where allowed, and
declines it if there's no match. A toggle in the app pauses blocking without undoing the setup. Only
one call screening app can be active at a time, so this app replaces any other (e.g. Truecaller).

## Limitations

- An outgoing call does not unblock that number.
- Only regular phone calls are screened. Calls through apps like WhatsApp or Signal are unaffected.
- Until the phone is unlocked for the first time after a reboot, calls are not screened.

## Building

```sh
./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease  # app/build/outputs/apk/release/app-release-unsigned.apk
```

Requires JDK 17+ and the Android SDK (set `sdk.dir` in `local.properties`, or open the project in
Android Studio). The release APK is unsigned, so sign it before installing.

## License

[MIT](LICENSE). The status icons are from
[Material Icons](https://github.com/google/material-design-icons) by Google, under the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
