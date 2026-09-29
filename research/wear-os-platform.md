# Wear OS platform research (claude-clip)

Researched 2026-09-29. Tags: [1P] first-party Google/Android source, [1P-src] AndroidX source or Google Maven, [2P] secondary or search-summary (not verified on a first-party page), UNKNOWN = not found.

## 6. Wear OS on Pixel Watch 5 / 4 / 3

**Answer.** Pixel Watch 5 exists (announced Aug 12 2026, on sale Aug 20 2026) and its Google Store FAQ refers to Wear OS 7.0. Wear OS 7 (Android 17, API 37) is the current platform. Google says it is rolling out to "eligible Pixel Watch devices" from Jun 18 2026. The exact per-model version for Pixel Watch 4 and 3 is UNKNOWN from first-party pages. Community threads suggest the Pixel Watch 4 rollout was uneven [2P].

Evidence:
- https://store.google.com/product/pixel_watch_5?hl=en-US [1P]: "Yes. With Wear OS 7.0, easily transfer your Pixel Watch 5 data when upgrading your phone." This is an indirect version statement in an FAQ. No spec-sheet line was parsed.
- https://blog.google/products-and-platforms/platforms/wear-os/google-io-2026-wear-os/ [1P]: "Wear OS 7 is rolling out to eligible Pixel Watch devices today..." The page does not list the models.
- https://blog.google/products-and-platforms/devices/pixel/pixel-watch-5/ [1P]: the launch post exists. The Pixel Watch 5 dates and prices came from search snippets of blog.google pages [2P], not from a fetched page.
- https://android-developers.googleblog.com/2026/05/whats-new-wear-os-7.html [1P]: "Wear OS 7 Canary Emulator, based on Android 17 that's arriving later this year." The stable release followed in June.
- Pixel Watch 4 / 3 exact version: UNKNOWN. Support-forum threads (support.google.com/googlepixelwatch), e.g. "Pixel Watch 4 still hasn't received Wear OS 7 update", are only seen via search titles [2P].
- The Pixel Watch 5 SoC (Snapdragon W5 Gen 2) and its 2026-08-20 release date came from GSMArena via search [2P]. Not needed for the app.

## 7. minSdk, version-to-API mapping, Play requirements

**Answer.** Recommended `minSdk = 30` (Wear OS 3), `compileSdk`/`targetSdk` = 35 at minimum for Play, 36 or 37 preferred. The minSdk value is my judgment, not a Google statement. Rationale: Wear OS 3 watches (Galaxy Watch 4 era, older TicWatch) are API 30, and everything newer is above it. Raising to 33 (Wear OS 4) would drop watches that are still supported. Whether every AndroidX Wear library used here supports minSdk 30 is UNKNOWN (not checked). Horologist's README lists Min SDK (Wear) 26, which shows the ecosystem floor is at or below 30 [1P].

Mapping:

| Wear OS | Android | API | Source |
|---|---|---|---|
| 3 | 11 | 30 | not verified on a fetched first-party page [2P/memory] |
| 4 | 13 | 33 | not verified [2P/memory] |
| 5 | 14 | 34 | not verified [2P/memory] |
| 6 | 16 | 36 | verified below |
| 7 | 17 | 37 | verified below |

Note that 5.1 (Android 15, API 35) also exists per memory [2P].

- https://developer.android.com/training/wearables/versions/6/setup [1P]: "Wear OS 6 (API level 36), which is based on Android 16"
- https://developer.android.com/training/wearables/versions/7/changes [1P]: "Wear OS 7 is based on Android 17 (API level 37)."
- https://developer.android.com/training/wearables/versions/7/setup [1P]: "targeting Wear OS 7 (API level 37), which is based on Android 17, or higher." It also says: "If you publish your Wear OS app to Google Play, you must target a sufficiently recent version of the platform."
- Play policy, https://support.google.com/googleplay/android-developer/answer/11926878 [1P, Google Play Help]:
  - "When you publish a new Wear app, you must target Android 15 (API level 35) or higher."
  - The API 35 requirement takes effect Aug 31 2026 for new apps and updates. It was API 34 from Aug 31 2025.
  - Existing apps below API 34 lose availability to newer OS devices. An extension is possible until Nov 1 2026.
- Device coverage by brand (Samsung, TicWatch, OnePlus): UNKNOWN from first-party pages. Per-model Wear OS versions were not verified.

## 8. Tile refresh floor

**Answer.** There is no hard numeric floor. The documented guidance is: do not refresh more often than once a minute, and the system may throttle faster requests. The interval is inexact. A value of 0 disables auto-refresh, so you must call `requestUpdate` yourself. For a 5-minute-ish poll, set freshness to 5 to 15 minutes, or push updates from WorkManager.

Evidence:
- AndroidX `TileBuilders.java` (raw.githubusercontent.com/androidx/androidx/androidx-main/wear/tiles/tiles/src/main/java/androidx/wear/tiles/TileBuilders.java) [1P-src]: "This mechanism should not be used to update your tile more frequently than once a minute, and the system may throttle your updates if you request updates faster than this interval. This interval is also inexact; the system will generally update your tile if it is on-screen, or about to be on-screen, although this is not guaranteed due to system-level optimizations."
- Same file: "A value of 0 here signifies that auto-refreshes should not be used".
- https://developer.android.com/training/wearables/tiles/update [1P]: "When you set a freshness interval, the system calls onTileRequest() shortly after the interval finishes. If you don't set a freshness interval, the system doesn't call onTileRequest()."
- Same page: "If you're doing more intensive background work repeatedly, such as polling for weather data, use WorkManager, and push updates to your tile."
- Same page shows `TileService.getUpdater(context).requestUpdate(MyTileService::class.java)`.
- Wear OS 7 direction: the Android Developers blog says Wear Widgets are the next step after Tiles. "we will continue to support our Protolayout and Tiles libraries for some time" (https://android-developers.googleblog.com/2026/05/whats-new-wear-os-7.html) [1P].

## 9. Complication update floor and push updates

**Answer.** `UPDATE_PERIOD_SECONDS` must be 0 or at least 300 (5 minutes); the system enforces 300. Use 0 plus push updates via `ComplicationDataSourceUpdateRequester.requestUpdate()`/`requestUpdateAll()`. The docs ask for no more than one push per 5 minutes on average. Requests are also less frequent in ambient or off-wrist states. There is no separate hard-coded rate-limit number in the API. Only the caution text exists.

Evidence:
- https://developer.android.com/training/wearables/complications/exposing-data [1P]: "If you don't set UPDATE_PERIOD_SECONDS to 0, you must use a value of at least 300 (5 minutes), which is the minimum update period that the system enforces, to preserve device battery life. In addition, keep in mind that update requests come less often when the device is in ambient mode or isn't being worn."
- Same page: "To preserve device battery life, don't call requestUpdate() from your instance of ComplicationDataSourceUpdateRequester more often than every 5 minutes on average."
- Same page: "you can use an instance of ComplicationDataSourceUpdateRequester to initiate updates dynamically."
- `ComplicationDataSourceService.kt` [1P-src]: "Note that update requests are not guaranteed to be sent with this frequency."
- Same file: `IMMEDIATE_UPDATE_PERIOD_MILLISECONDS` allows 1 Hz updates, but only with the privileged permission `USE_IMMEDIATE_COMPLICATION_UPDATE`. That is not available to third-party apps. Do not plan on it.
- Same file: `onComplicationRequest` must answer within about 20 seconds or the system unbinds.
- Use `TimeDifferenceComplicationText` for the countdown so no update is needed each second or minute. This is from the same docs page (search-summary, not re-read in full) [2P].

## 10. Networking without a paired phone; Tailscale

**Answer.**
- Yes, a Wear OS app can make direct HTTP(S) calls over Wi-Fi or LTE with no phone.
- With a Bluetooth link to a phone, watch traffic is generally proxied through the phone.
- Whether a proxied watch can reach a Tailscale (100.x) or LAN IP via the phone: UNKNOWN. No first-party doc addresses it.
- Cleartext HTTP is blocked by default for apps targeting API 28+. A LAN or Tailscale JSON endpoint over plain HTTP needs a network security config allowing that domain or IP, or use HTTPS.
- There is no official Tailscale client for Wear OS. Sideloading the Android app is reported as unreliable [2P].
- New in Wear OS 7 / Android 17: apps targeting API 37 must declare the `ACCESS_LOCAL_NETWORK` runtime permission to reach LAN devices.

Evidence:
- https://developer.android.com/training/wearables/data/network-access [1P]:
  - "With Wear OS by Google, a watch can communicate with a network directly, without access to an Android or iOS phone."
  - "When a watch has a Bluetooth connection to a phone, the watch's network traffic is generally proxied through the phone."
  - "When a phone is unavailable, Wi-Fi and cellular networks are used, depending on the watch hardware."
  - Background requests may want an explicit Wi-Fi network via `requestNetwork` + `bindProcessToNetwork`, since the default network favors battery: "the active network might not have enough bandwidth..."
  - The page has no mention of cleartext or network security config.
- https://developer.android.com/privacy-and-security/security-config [1P]: "Starting with Android 9 (API level 28), cleartext support is disabled by default. Applications that require cleartext traffic can opt in to cleartext traffic."
- https://developer.android.com/training/wearables/versions/7/changes [1P]: "Apps targeting Android 17 must declare the ACCESS_LOCAL_NETWORK runtime permission to interact with devices on a local area network (LAN)." Whether this applies to a Tailscale address (not an RFC1918 LAN address) is UNKNOWN.
- https://tailscale.com/docs/install/android [not in requested source list]: "The Tailscale client works with Android 8.0 or later on devices such as phones, tablets, and Android TV." No Wear OS mention.
- https://github.com/tailscale/tailscale/issues/3972 "FR: Wear OS support" is open (search snippet) [2P]. Issue #12177 reports the APK fails to launch on Galaxy Watch 6 Classic [2P].
- Design consequence: for a tailnet-only endpoint, the watch on LTE or Wi-Fi alone cannot reach it. The realistic options are (a) a phone relay app, (b) expose the endpoint via a reachable HTTPS URL such as Tailscale Funnel or a reverse proxy with auth, or (c) accept "phone connected only". Options need an on-device test, which was not done.

## 11. Current stable AndroidX versions (as of 2026-09-29)

| Artifact | Stable | Date | Notes |
|---|---|---|---|
| androidx.wear.compose:compose-material3 | 1.7.0 | 2026-09-23 | Google Maven `<release>1.7.0</release>`, `lastUpdated 20260923` |
| androidx.wear.compose:compose-foundation / navigation | 1.7.0 | 2026-09-23 | release-notes page |
| androidx.wear.tiles:tiles | 1.6.2 | 2026-07-29 | no RC/beta/alpha |
| androidx.wear.protolayout:protolayout (+ -material, -expression) | 1.4.2 | 2026-07-29 | no pre-release |
| androidx.wear.watchface:watchface-complications-data-source (+ -ktx) | 1.3.0 | 2026-02-25 | no pre-release |

Evidence:
- https://developer.android.com/jetpack/androidx/releases/wear-compose [1P]: "Stable Release: 1.7.0", "September 23, 2026 `androidx.wear.compose:compose-*:1.7.0` is released."
- https://dl.google.com/dl/android/maven2/androidx/wear/compose/compose-material3/maven-metadata.xml [1P-src]: `<release>1.7.0</release>`.
- Caution: the dedicated page https://developer.android.com/jetpack/androidx/releases/wear-compose-m3 still shows "1.5.0 / August 27, 2025" (stale). Trust Maven and the wear-compose page.
- https://developer.android.com/jetpack/androidx/releases/wear-tiles [1P]: "Stable Release 1.6.2 ... July 29, 2026".
- https://developer.android.com/jetpack/androidx/releases/wear-protolayout [1P]: "Stable Release 1.4.2 ... July 29, 2026".
- https://developer.android.com/jetpack/androidx/releases/wear-watchface [1P]: "watchface-*:1.3.0 is released" (Feb 25 2026). Maven `<release>1.3.0</release>` confirms.
- Compose libs: 1.7.0 highlights include `TransformingLazyColumn` `PinnableContainer` support and new one-handed gesture APIs [1P]. `compose-material` (M2) is superseded by `compose-material3`.

## 12. Horologist status in 2026

**Answer.** Active, not archived, and nothing has been folded into AndroidX as a whole. Recommendation: avoid it for this app. Prefer plain `androidx.wear.compose` (material3) + tiles + complications. Horologist's current releases are alpha-tagged (0.8.x), and its remaining value is media and data-layer helpers, which this app does not need. Optionally use its `composables` module for a picker, but nothing here requires it.

Evidence:
- GitHub API for google/horologist [1P]: `"archived": false`, `pushed_at 2026-09-29`.
- Latest release tags: v0.8.4-alpha (2026-08-10), v0.8.3-alpha (2025-10-29), v0.8.2-alpha (2025-08-27).
- README (raw.githubusercontent.com/google/horologist/main/README.md) [1P]:
  - Maintained lines are 0.7.x (release-0.7.x, "Wear Compose 1.5.x ... generally latest stable Androidx") and 0.8.x (main, "Wear Compose 1.6.x, Compose 1.9.x and generally latest relevant alphas of Androidx"). Both are Min SDK 26.
  - "the main branch will actively update to incorporate new API guidance, removing or changing APIs."
  - The Media UI and audio UI modules are labeled "Legacy Material 2".
  - No README text says the repo is deprecated or moved to AndroidX (my reading of the README; not searched beyond it).
- Horologist targets Wear Compose 1.6.x, while stable is 1.7.0 [1P]. Expect lag behind current AndroidX.

## Watch Face Format note (complications only)

- https://developer.android.com/jetpack/androidx/releases/wear-watchface [1P]: the legacy `watchface`, `watchface-client` and `watchface-complications-rendering` APIs are "deprecated in favor of the Wear Watchface Format ... The complication APIs are not deprecated and will remain." So the data-source side (`ComplicationDataSourceService`) stays valid.
- WFF 5 arrived with Wear OS 7 (https://android-developers.googleblog.com/2026/05/whats-new-wear-os-7.html) [1P]. It adds hierarchical User Styles that "can now enable or disable complication slots", plus blend modes on `ComplicationSlot`. Data sources need no change.
- Whether Play requires WFF for all new watch faces: UNKNOWN (not verified). It does not affect a complication data source app.

## Method caveats
- Pages were fetched with curl and text-stripped. Where a WebFetch summary disagreed with the raw page, the raw page was used.
- The Wear OS 3/4/5 API mapping, Pixel Watch 4/3 versions and OEM device coverage remain unverified. Verify before publishing.
