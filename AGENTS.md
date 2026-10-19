# AGENTS.md

Instructions for coding agents that work on XPplay.

## What this repository is

XPplay is a CarPlay receiver for Android head units, built for the **XPeng G3 (D20)**:
Android 9 / API 28, Snapdragon 820 class, 3.72 GB RAM, fixed **portrait** 1080×1920 centre screen,
no root. It is not an Apple-certified product.

Lineage: `xcertplay` (protocol library, GPL-3.0) is embedded by `DiPlay` (GPL-3.0, UI adapted from
DiAuto, AGPL-3.0), which this repository forks. Read `README.md` for the feature list and limits.

| Module | Holds | Notes |
| --- | --- | --- |
| `:shared` | the protocol stack: AirPlay, iAP2, MFi, media, network, transport, orchestration | upstream `xcertplay` code plus this fork's patches |
| `:common` | the app shell and **every** screen, including Settings | DiPlay-derived UI, XPplay customisations |
| `:mobile` | the packaging shell: manifest, `res/values`, R8 keep rules | **no Kotlin sources**; applicationId, version, signing and R8 live here |

Application id: `com.xpplay.app` (debug builds get `.debug`). Non-goals: CarPlay Ultra, instrument
cluster / HUD projection, Android Auto, driving video.

## Checks

Run these before you report a change as done:

```sh
export JAVA_HOME=~/java/current   # Corretto 25
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest \
          :shared:lintDebug :common:lintDebug :mobile:lintRelease
```

`DIPLAY_AUTH_ASSETS_DIR` is only needed for assembling an APK, not for tests or lint.

Rules learned the hard way:

1. **Never run two Gradle builds at once.** Parallel JVMs starve Robolectric's sandbox and produce
   false failures (historically `CarHotspotSwitchTest`). Verify with one serial full run.
2. `:common` unit tests set `forkEvery = 20` and `maxHeapSize = 2g`. One large Robolectric JVM
   deadlocks its instrumentation thread after roughly 20 instrumented classes; do not raise
   `forkEvery` without re-measuring the whole suite.
3. Baseline: `shared` 1094 tests, `common` 1219 tests, all green; three-module lint has 0 errors.
   Several hundred warnings are expected — do not "fix" them as part of an unrelated change.
4. The `--offline` flag works because the dependency cache is warm; use it when the network is slow.

## Build a release

```sh
DIPLAY_AUTH_ASSETS_DIR=$HOME/keys/xpplay-runtime-assets \
ANDROID_KEYSTORE_PATH=$HOME/keys/xpplay.jks \
ANDROID_KEYSTORE_PASSWORD=<see ~/keys notes> \
ANDROID_KEY_ALIAS=xpplay \
ANDROID_KEY_PASSWORD=<see ~/keys notes> \
./gradlew :mobile:assembleRelease
```

- The MFi identity (`offline-mfi/identity.pk8` + `certificate.p7b`) is a **required** APK asset:
  without it the head unit cannot authenticate CarPlay at all. `mobile/build.gradle.kts`
  (`rejectBundledCredentials`) pins both SHA-256 values, so a replaced or corrupted identity fails
  the build. Do not weaken that pinning.
- Change the pin only when the operator deliberately provisions a new identity.
- Never commit credential containers (`.pk8`, `.p7b`, `.jks`, `.pem`, `.key`, `.p12`, `.pfx`,
  `.keystore`, `.apk`, `.aab`) or private-key blocks. `scripts/check_public_tree.py` is the guard.
- Keep R8 conservative: minify + shrink resources, `optimization { enable = false }`. The APK is
  ~5 MB; a larger build costs install and dexopt time on this head unit.

### Versioning

`XPplay-V<major>.<minor>.<fix>` with `versionCode = major*10000 + minor*100 + fix`
(V1.4.4 = 10404); `version.json` in the repository root is what the in-app updater reads.

A release also needs, in this order:

1. `RELEASE_NOTES_v<version>.md` and a matching CHANGELOG entry;
2. the built APK and its R8 `mapping.txt` archived outside the repository;
3. **`version.json` updated** to point at the new tag and assets;
4. a tag, a GitHub release, and **`SHA256SUMS.txt` uploaded with the APK** —
   `UpdateCatalog.parse` returns null without it and the in-app update check silently does nothing.

## Settings

`SettingsCategory`, `SettingsSection` and `SettingsInformationArchitecture` are defined at the top of
`common/src/main/java/com/shilapi/xcertplay/DiPlayActivity.kt`. The Settings screen is grouped by
driver goal, not by implementation.

Categories today: `OVERVIEW`, `CONNECTION`, `DISPLAY`, `AUDIO`, `NAVIGATION`, `VEHICLE`, `LANGUAGE`,
`DIAGNOSTICS`, `ADVANCED`, `ABOUT`. Sections today: `CARPLAY_CONTROLS`, `WHEEL_KEYS`,
`CONNECTION_SETUP`, `AUTOMATIC_CONNECTION`, `USB_CONNECTION`, `PERMISSIONS_AND_HELP`,
`DISPLAY_AND_PERFORMANCE`, `EXPERIMENTAL_DISPLAY`, `AUDIO_ROUTING`, `LOCATION`, `CAR_BUTTON`,
`NAVIGATION_WHEEL`, `DIAGNOSTICS`, `LANGUAGE`.

### Choose the category

Ask which goal the driver has when they look for the setting, then use the first row that fits.

| Category | Put a setting here when it controls… | Examples |
| --- | --- | --- |
| Connection | how the iPhone connects and how XPplay starts | connection setup, connect on open, start with the car, USB permissions, car-hotspot automation, iPhone choice |
| Display | how CarPlay looks on the head-unit screen | day/night mode, picture, size, dock, system bars |
| Audio | what the driver hears | media / navigation / call streams, audio quality limits, music buffer |
| Navigation | location and guidance | location reporting to the iPhone |
| Vehicle | how CarPlay fits this car and its driver | wheel keys, car button, driving side, gestures that conflict with the head unit |
| Language | the app language | language picker |
| Diagnostics | troubleshooting evidence | diagnostic reports |
| Advanced | experimental or risky behaviour | side panel, split screen, HEVC, audio focus, buffered music, video output |
| Overview | nothing new | see "Overview" below |

A control goes to Advanced when it is experimental, depends on specific firmware, or is an opt-in
that can break sound, video or the connection on some head units. A control that fixes a common
problem and is safe at its default stays in its category.

Mark an experimental control or card with the title suffix ` (experimental)`. Each locale MUST use its
existing form: `(تجريبي)`, `(experimental)`, `(экспериментально)`, `(експериментально)`, `（实验性）`,
`（實驗性）`.

### Add the code

1. To extend an existing card, add the control inside the `filteredSection(...)` block of that card
   in the correct category.
2. For a new card, add a `SettingsSection` entry and map it to exactly one category in
   `SettingsInformationArchitecture.sectionsByCategory`. Layout policy tests fail when a section has
   no category or more than one (`SettingsLayoutPolicyTest.everySettingsSectionHasExactlyOneDestination`).
3. Build the card with `filteredSection(content, SettingsSection.X, …)` inside `allSettingsSections()`.
   A plain `section(...)` call there MUST NOT be used: it renders on every category page.
4. A setting MUST NOT appear in two categories. Overview quick settings are the only duplicates and
   MUST use the same persistence as the full control. Overview SHOULD NOT have more than four quick
   settings. A header shortcut MAY duplicate a Display setting when it uses the same persistence.
5. If the change applies at the next connection, the description MUST say so and the save callback
   MUST call `markReconnectNeeded()`. That shows the "Reconnect now" bar and never drops a running
   session without consent. Reconnect at once (`reconnectIfRunning()`) only after a dialog button
   that says "Apply and reconnect", or when the description says the change reconnects CarPlay.
6. Add each new string to `common/src/main/res/values/` **and to all seven locales**:
   `values-ar`, `values-es`, `values-ja`, `values-ru`, `values-uk`, `values-zh-rCN`, `values-zh-rTW`.
   New Settings copy SHOULD use the `settings_` prefix. `SettingsTranslationsTest` fails when a
   `settings_*` string is missing in a locale or when placeholders differ; `JapaneseTranslationsTest`
   and `TraditionalChineseTranslationsTest` require **every** English key in those two locales.
7. A control that opens a choice uses `button()` with the text `"Title · Value"`. That form renders as
   a setting row with the value and a chevron, and Search indexes the title.
8. Search indexing: card titles and `toggle(...)` labels are indexed automatically through the
   `searchIndexSink`. A raw `Button`/row added outside those helpers MUST add its title itself
   (`searchIndexSink?.add(title)`) while indexing, like the car-hotspot control does.
9. When you move a control between categories, update the layout-policy expectations in
   `AdaptiveSettingsUiTest` / `SettingsLayoutPolicyTest`.

### Overview

Overview holds connection status, links to the categories, quick settings, About and Language.
Do not add a new setting to Overview: add it to its category, then promote it to quick settings only
if drivers change it often. Every category except Overview MUST keep its link on Overview
(`AdaptiveSettingsUiTest.overviewLinksToEveryOtherCategory`).

## Settings layout

Spacing, size and shape in the Settings screen MUST come from one place.

1. A gap between blocks MUST use the named constant `SETTINGS_BLOCK_GAP_DP`, not a literal dp value
   (`SettingsLayoutSourceRulesTest.blockGapsUseTheNamedConstant`).
2. A size MUST NOT be a pixel constant (`*_PX`). Use dp and clamp to the available window width.
   `SettingsLayoutSourceRulesTest.sizesAreNotPixelConstants` enforces this.
3. Controls in one row MUST have the same height; corner radius follows the control height. A new
   button MUST NOT set its own radius.
4. A layout change MUST keep the compact/full and large-font cases green in `AdaptiveSettingsUiTest`,
   and MUST add or update the screenshot cases under `common/src/test/java/.../*ScreenshotsTest.kt`
   when the change is visual. Add an Arabic (right-to-left) case when the change touches row
   alignment.

## Fork-specific invariants

These are deliberate XPplay decisions; a change must not silently undo them.

- **Portrait only.** The head unit has no landscape mode: activities stay `portrait` and CarPlay
  negotiates a single portrait canvas. Do not reintroduce rotation/square-canvas negotiation.
- **No BYD branding or BYD-only modules.** The app name, accessory name, hotspot name and bundled
  graphics are XPplay's own. BYD heads-up-display, cluster, I2C/CH341, remote-MFi and Android Auto
  code was removed on purpose; do not "restore" it from upstream.
- **MFi identity stays bundled**, see "Build a release".
- **Wireless path is "existing Wi-Fi / same LAN" first.** Wi-Fi Direct and the car hotspot are
  fallbacks.
- **Update mirrors are ordered by measurement** in `update/UpdateMirrors.kt`. Re-measure before
  reordering, and delete a prefix that keeps timing out instead of leaving it in first place.

## Working agreements

- Chinese is the working language for reports to the project owner; code, comments and commit
  messages stay English.
- Commit messages: `V<version>: <what changed and why>` for releases, `fix:`/`feat:`/`test:`/`docs:`
  otherwise. Keep the reason, not just the diff.
- Do not rewrite localisation files with regex bulk edits. Roll back, delete key by key, and verify
  with a real XML parse.
- When syncing from upstream, remember the layering: `xcertplay` ⊂ `DiPlay` ⊂ `XPplay`. Apply each
  upstream change onto our files; never replace whole files.
- This project's releases are published to `github.com/joeyzhang99/XPplay`. Release assets and the
  `version.json` update are part of the change, not an afterthought.
