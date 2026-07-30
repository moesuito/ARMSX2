# ARMSX2 
PlayStation 2 Emulator for Android based on the work of [PCSX2](https://github.com/PCSX2/pcsx2)

## App version

The Android app version is configured in `app/build.gradle.kts` under
`android.defaultConfig`:

```kotlin
versionCode = providers.gradleProperty("armsx2.versionCode").orNull?.toInt() ?: 1088
versionName = providers.gradleProperty("armsx2.versionName").orNull ?: "2.5.8"
```

Change `versionName` to the user-facing release version and increment the integer
`versionCode` for every published APK or AAB. Release scripts can still override
these defaults with `-Parmsx2.versionName=...` and `-Parmsx2.versionCode=...`.

## Android APK builds

Release/test APKs should be built with the universal page-size builder:

```bash
./tools/build-universal-page-apk.sh ~/Downloads/ARMSX2-Refresh-UniversalPage.apk
```

The script compiles both 4K and 16K ARM64 emucore variants, packages them into
one APK, signs it, and verifies 16K zip alignment. This keeps one distributable
APK working correctly on both older 4K-page devices and newer 16K-page devices.

## Dedicated HDMI output

`Graphics > Display & Resolution > Dedicated HDMI Output (Beta)` sends the
game surface to an Android presentation display while leaving a companion
screen and the pause-menu button on the phone. It is an app-wide preference.

The output starts only while a game is running or paused and the app is in the
foreground. Disconnecting the display, disabling the option, or backgrounding
the app hands the existing renderer back to the phone. Reconnecting hands it
back to the external display; no second renderer or emulation loop is created.
The external surface uses the display's physical mode by default and still
honours the existing output-resolution override and hardware scaler.

Samsung DeX and Android desktop mode take precedence. If either is active, the
saved option remains enabled but ARMSX2 does not create a `Presentation`; the
app renders normally in its desktop window and can use its existing fullscreen
controls. Devices which do not expose a presentation display keep Android's
normal display behaviour.

Manual device checks:

1. In normal mirroring mode, start a game and enable the option: the monitor
   shows only game video and the phone shows the companion screen.
2. Disconnect and reconnect HDMI during gameplay and while paused: rendering
   returns to the phone, then moves back to the monitor without restarting.
3. Start DeX before launching ARMSX2, then activate DeX while playing: the
   dedicated output is ignored/released and the app remains in the DeX UI.
4. Background and foreground ARMSX2 with HDMI connected: the presentation is
   dismissed in the background and restored on return.

The local policy check can be run later with:

```bash
./gradlew :app:testDebugUnitTest --tests \
  com.armsx2.runtime.DedicatedExternalDisplayTest
```
