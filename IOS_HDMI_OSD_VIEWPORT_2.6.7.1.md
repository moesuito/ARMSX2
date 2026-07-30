# iOS HDMI OSD Viewport and Scaling — 2.6.7.1 Hotfix

## Scope

This document covers the follow-up hotfix built on the approved iOS Dedicated
HDMI Output 2.6.7 implementation.

- Repository: `moesuito/ARMSX2`
- Base commit: `adad0e580a023340612726ccb5181621e2e02c2d`
- Development branch: `fix/ios-hdmi-osd-viewport-2.6.7.1`
- Public release version: `2.6.7.1`
- Pull request: intentionally not opened until physical-device validation

The original HDMI architecture, companion screen, pause flow, localization,
virtual-controller suppression, and normal non-HDMI behavior are unchanged.

## User-facing changes

### Viewport OSD item

The OSD settings now include **Show Viewport**. It is persisted as:

```ini
[EmuCore/GS]
OsdShowViewport = false
```

When enabled, the performance overlay draws a separate line:

```text
Viewport: 1920x1080-60Hz
```

The line uses `GSDevice::GetWindowInfo()`:

- `surface_width`
- `surface_height`
- `surface_refresh_rate`

On dedicated iOS output these values are populated from the active external
render view, `UIScreen.currentMode.size`, and
`UIScreen.maximumFramesPerSecond`. Fractional refresh values are retained to
two decimal places; values within 0.05 Hz of an integer are displayed without
decimals.

This is deliberately separate from **Show Resolution**, which continues to
report the emulated game's internal GS resolution and video/interlace mode.

The new option participates in:

- core configuration defaults and INI serialization;
- OSD enabled-state detection;
- live `EmuConfig` to `GSConfig` synchronization;
- paused/cached OSD drawing;
- the iOS Off, Simple, Detail, Full, and Custom preset flow;
- custom-preset snapshot/restore;
- generic FullscreenUI OSD settings; and
- all nine non-English interface dictionaries currently shipped by iOS.

It is disabled by default. The iOS Detail and Full presets enable it; Simple
does not.

## Resolution-aware OSD scale

Previously, the external Metal drawable could correctly become 3840x2160 while
the OSD scale remained derived from UIKit's logical content scale. This made
the overlay too small at 4K and left it vulnerable to stale geometry during a
mode transition.

Dedicated output now separates:

- the drawable scale used to create the full-resolution `CAMetalLayer`; and
- the visual OSD scale stored in `WindowInfo.surface_scale`.

`GSCalculateExternalDisplayOSDScale()` uses the shorter output edge so landscape
and portrait dimensions produce the same result.

| Short edge | Scale |
|---:|---:|
| 720 px | 0.6667 |
| 1080 px | 1.0 |
| 1440 px | 1.5 |
| 2160 px | 2.0 |

Values between anchors use linear interpolation. Values below 720p follow the
1080p ratio with the existing ImGui minimum of 0.5. Values above 2160p continue
proportionally, never dropping below the 4K anchor.

The phone renderer retains its original native UIKit scale. The new mapping is
applied only when the active render view is the dedicated external view.

## 4K clipping fix

Two safeguards cover the reported off-screen/cropped OSD at 4K:

1. `GSDeviceMTL::RenderImGui()` derives its framebuffer bounds and scissor
   clipping from the Metal texture attached to the active present pass rather
   than assuming cached window dimensions.
2. `CalculatePerformanceOverlayTextPosition()` clamps ordinary rows between
   the valid left and right bounds of the current ImGui viewport. A transient
   safe-area or mode-size mismatch can no longer place a normal-width,
   right-aligned row beyond the drawable.

The game viewport/aspect-fit path was already correct at both 1080p and 4K and
was not changed.

## Version metadata

Apple bundle marketing versions use three numeric components, while this
project identifies the hotfix as 2.6.7.1. The package therefore uses:

| Key | Value |
|---|---|
| `CFBundleShortVersionString` | `2.6.7` |
| `CFBundleVersion` | `2671` |
| `ARMSX2ReleaseVersion` | `2.6.7.1` |

The app's version display, launch diagnostics, and copied troubleshooting
information use the public hotfix version.

## Code areas

| Area | Main files |
|---|---|
| Core option/default/serialization | `pcsx2/Config.h`, `pcsx2/Pcsx2Config.cpp` |
| Viewport line and bounds | `pcsx2/ImGui/ImGuiOverlays.cpp` |
| Generic OSD settings | `pcsx2/ImGui/FullscreenUI_Settings.cpp` |
| External scale helper | `pcsx2/GS/GS.h`, `pcsx2/GS/GS.cpp` |
| Metal framebuffer clipping | `pcsx2/GS/Renderers/Metal/GSDeviceMTL.mm` |
| iOS render-window metadata | `platforms/ios/app/src/main/cpp/IOS/HostImpls.mm` |
| iOS resize/preset bridge | `platforms/ios/app/src/main/cpp/ios_main.mm`, `ARMSX2Bridge.mm` |
| iOS settings UI/state | `SettingsStore.swift`, `OverlaySettingsView.swift` |
| iOS localization | `AppLanguage+MainTranslations.swift` |
| Version packaging | iOS `CMakeLists.txt`, `Info.plist.in`, `HelpView.swift` |
| Regression anchors | `tests/ctest/core/gs/external_display_aspect_tests.cpp` |

## Automated and simulator validation

Completed:

- CMake regenerated the iOS Simulator Xcode project.
- Debug arm64 simulator build succeeded with Xcode 26.5 SDK.
- The app bundle passed Xcode's shallow bundle validation.
- Generated metadata was inspected as `2.6.7`, build `2671`, public release
  `2.6.7.1`.
- The app installed and launched successfully on the booted iPhone 17 Pro
  simulator.
- Source regression coverage asserts 720p, 1080p, 1440p, 4K, and
  orientation-independent scale anchors.

Per project policy, no game boot or simulated external-display test is required:
the iOS Simulator does not accurately reproduce this HDMI path.

## Pending physical-device checklist

Before opening a pull request for this branch, validate on the iPhone 17 Pro
Max with JIT:

- [ ] `Show Viewport` disabled produces no viewport line.
- [ ] `Show Viewport` enabled persists after app restart.
- [ ] 1080p reports `1920x1080-60Hz` or the actual adapter-reported rate.
- [ ] 1440p reports `2560x1440-...Hz` and uses the 150% scale.
- [ ] 4K reports `3840x2160-...Hz` and uses the 200% scale.
- [ ] 720p uses the reduced scale.
- [ ] The entire OSD remains inside every edge at 4K.
- [ ] Top-left and top-right OSD positions remain correct.
- [ ] Internal Resolution and Viewport can be enabled independently.
- [ ] The game viewport remains correctly fitted at 1080p and 4K.
- [ ] With Dedicated HDMI Output disabled, phone rendering and OSD behavior
  remain unchanged.
- [ ] HDMI hot-plug, companion screen, pause menu, and virtual-controller
  suppression retain their approved 2.6.7 behavior.

No pull request should be opened until this checklist is approved.
