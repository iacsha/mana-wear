# Mana — mana-wear Android apps

Two apps and a shared module:

| Module | What it is |
|--------|------------|
| `:wear` | The watch app. Shows five-hour and weekly usage, reset countdowns, and the age of the reading. |
| `:phone` | The phone relay. Holds the collector URL and token, fetches over the tailnet, and answers the watch over the Wear Data Layer. |
| `:shared` | Payload model, HTTP fetching, the relay envelope, and display formatting. |

The watch never sees the token. It sends an empty request on `/mana/usage`, the phone
fetches the collector and answers with the payload and a status, and the phone also
keeps the last good payload in a DataItem so the watch has something to show on a cold
start.

## Build

You need JDK 17 or newer and an Android SDK with platform 37.2. Gradle comes from the
wrapper.

```sh
echo "sdk.dir=$HOME/android-sdk" > local.properties
./gradlew testDebugUnitTest assembleRelease
```

The APKs are `wear/build/outputs/apk/release/wear-release.apk` and
`phone/build/outputs/apk/release/phone-release.apk`.

**Build both APKs on the same machine.** They share the applicationId
`io.github.manawear`, and the Data Layer only connects them if they are also signed with
the same key. Check with `apksigner verify --print-certs` on each.

## Install

1. Phone: `adb install -r phone-release.apk`. The phone needs Tailscale (or any route to
   the collector) and a paired watch.
2. Watch: turn on ADB debugging and Wireless debugging, pair, then
   `adb install -r wear-release.apk`.
3. Pair the phone with the collector (below).
4. Open Mana on the watch.

## Pairing

On the machine running the collector, run `mana-collector pair`. It prints a QR code that
holds the collector URL and token. Open Mana on the phone, tap **Scan pairing code**, and
point the camera at it. The phone fills in both fields, saves them, and runs the same test
as **Save and test**. The scanner is Google's, running in Play services, so Mana asks for
no camera permission.

The code is a `mana://pair?v=1&url=...&token=...` link, and opening that link on the phone
works too. A link never saves on its own: Mana shows the collector's host and waits for a
tap on **Pair**, because any app or web page can open a `mana://` link.

To enter things by hand instead, type the collector URL (for example
`https://my-pc.tailnet.ts.net/v1/usage`) and the token, then tap **Save and test**.

## Plain http

Plain `http://` is allowed only to addresses where the token cannot cross the internet in
the clear: `10/8`, `172.16/12`, `192.168/16`, Tailscale's `100.64/10`, loopback,
`localhost`, `*.local`, and IPv6 `::1` and `fd00::/8` (which holds Tailscale's IPv6
range). Anything else needs `https://`; `tailscale serve` gives the collector an https
address on your tailnet. Hostnames are not resolved, so `http://my-pc.tailnet.ts.net` is
refused even though it points at a tailnet address.

The rule is `cleartextAllowed()` in `:shared` (`UrlPolicy.kt`). `HttpUsageSource` checks
it before connecting, on the phone and on the watch's direct mode, and does not follow
redirects. Both apps' `network_security_config.xml` still permit cleartext, because that
file cannot express address ranges.

## Direct HTTP instead of the relay

For a watch that can reach the collector itself, set the URL on the watch. That skips the
phone. The same plain-http rule applies:

```sh
adb shell am start -n io.github.manawear/io.github.manawear.watch.MainActivity \
  --es url https://COLLECTOR_HOST/v1/usage --es token TOKEN
```

If you change the URL without also sending a token, the app forgets the stored token.
That stops another app on the watch from sending the token to its own server.

## What the watch shows

| Shown | Meaning |
|-------|---------|
| `5-hour 7%` and the ring | Five-hour window usage |
| `resets 2h 31m` | Time until the five-hour window resets |
| `week 43% · 1d 12h` | Weekly usage and its reset |
| `5s ago` | Age of the reading, by the watch's clock |
| `stale` | The collector flagged the reading, or it is over 15 minutes old |
| `unreachable` | The last fetch failed. The figures are the last good reading. |
| `token rejected` | The collector returned 401 or 403 |
| `phone not connected` | No phone is connected to the watch |
| `install Mana on phone` | A phone is connected but did not answer as Mana |
| `Not set up` | The phone app has no collector URL yet |
| `--` | The collector does not know this window |

The watch fetches when it opens and then once a minute while the screen is on.
