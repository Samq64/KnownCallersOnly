# Known Callers Only

A tiny Android app that only lets calls from the contacts list ring through. By default everyone
else is silently blocked unless they call again within 15 minutes. Voicemail is unaffected.
Blocked calls are still recorded in the call log, but some phone apps don't show them.

## How it works

The app registers as Android's call screening app (`CallScreeningService`), which other apps
can use from Android 10 onwards. For each incoming call it looks the number up in the device's
contacts (including work-profile contacts where allowed) and blocks it if there's no match. A
toggle in the app, or its Quick Settings tile, pauses blocking. Only one call screening app can
be active at a time, so this app replaces any other (e.g. Truecaller).

## Options

Both are on by default.

### Decline instantly

Blocked callers go straight to voicemail, or hear a busy tone or "unavailable" message, depending
on the carrier. When off, they hear ringing until the call times out.

### Allow repeat callers

If the same number calls again within 15 minutes, the second call rings. The app remembers blocked
numbers until the next incoming call after their 15 minutes are up, and forgets them straight away
when this option or blocking is turned off. Hidden numbers never count as repeats.

## Limitations

- An outgoing call does not unblock that number.
- Only regular phone calls are blocked. Calls through apps like WhatsApp or Signal are unaffected.
- Until the phone is unlocked for the first time after a reboot, calls are not blocked.
- Although Android logs blocked calls, not all phone apps display them.

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
