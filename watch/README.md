# Mana — mana-wear watch app

The Wear OS app. It shows the five-hour and weekly plan usage that the collector
serves, with reset countdowns and the age of the reading.

This is the Phase 3 MVP. It fetches straight from the collector over HTTP. The phone
relay (Phase 5) and the tile and complication (Phase 4) come later.

## Build

You need JDK 17 or newer and an Android SDK with platform 37.2. Gradle comes from the
wrapper.

```sh
echo "sdk.dir=$HOME/android-sdk" > local.properties
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

The release APK is at `app/build/outputs/apk/release/app-release.apk`.

## Install and configure

Turn on ADB debugging on the watch (Settings, Developer options), connect over Wi-Fi,
and then run:

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n io.github.manawear.watch/.MainActivity \
  --es url http://COLLECTOR_HOST:7339/v1/usage \
  --es token "$(cat ~/.config/claude-clip/token)"
```

The watch must be able to reach the collector. Plain HTTP is allowed because the
collector normally sits on a tailnet, where WireGuard already encrypts the hop.

If you change the URL without also sending a token, the app forgets the stored token.
That stops another app on the watch from sending the token to its own server.

## What the screen means

| Shown | Meaning |
|-------|---------|
| `5-hour 7%` and the ring | Five-hour window usage |
| `resets 2h 31m` | Time until the five-hour window resets |
| `week 43% · 1d 12h` | Weekly usage and its reset |
| `5s ago` | Age of the reading, by the watch's clock |
| `stale` | The collector flagged the reading, or it is over 15 minutes old |
| `unreachable` | The last fetch failed. The figures are the last good reading. |
| `token rejected` | The collector returned 401 or 403 |
| `--` | The collector does not know this window |

The app fetches when it opens and then once a minute while the screen is on.
