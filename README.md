# Mana

Your Claude Code plan usage on a Wear OS watch: the five-hour and weekly windows, how long
until each resets, and how old the reading is.

Mana has three parts:

| Part | Where it runs | What it does |
|------|---------------|--------------|
| `mana-collector` | The computer where you use Claude Code | Reads usage from Claude Code's status line and serves it on your network |
| Mana phone app | Your Android phone | Fetches from the collector and relays to the watch. Holds the token. |
| Mana watch app and watch face | Your Wear OS watch | Shows the figures in the app, a tile, complications and the Mana face |

The watch never talks to the collector and never sees the token. It asks the phone, and
the phone answers over the Wear Data Layer.

## Where the numbers come from

Claude Code passes a `rate_limits` object to your `statusLine` command on every render
when you are signed in with a claude.ai Pro or Max account. `mana-collector init` wraps
your existing status line with a small tap that saves those figures and then runs your
original command, so your status line looks the same as before.

This has three consequences:

- **Usage from claude.ai web, desktop or mobile shows up only after the next Claude Code
  status line render.** The tap sees what Claude Code sees, when Claude Code draws.
- **A session signed in with an API key carries no `rate_limits`,** so the tap records
  nothing. Mana is for subscription plans.
- **When an account hits its limit,** Claude Code stops getting successful responses and
  the status line carries no `rate_limits`. The watch keeps showing the last reading and
  its age.

Mana never reads your Claude credentials and never calls an Anthropic API.

## Install

### 1. The collector

You need [Bun](https://bun.sh) 1.1 or newer on the machine that runs Claude Code.

```sh
bun add -g github:iacsha/mana-wear
mana-collector init
```

`init` does four things:

1. Wraps the `statusLine` in `~/.claude/settings.json` with the tap, keeping your command.
2. Creates a random bearer token in Mana's config directory.
3. Installs a background service: a systemd user unit on Linux, a launchd agent on macOS,
   or a logon task on Windows.
4. Prints a pairing QR code.

It binds to your Tailscale address when Tailscale is running. Without Tailscale, pass
`--bind` with this machine's LAN address:

```sh
mana-collector init --bind 192.168.1.20
```

To undo everything, run `mana-collector init --undo`. It restores your original
`statusLine` exactly, and refuses if you have changed the status line since `init`.

### 2. The phone and watch

Download `mana-phone-<version>.apk`, `mana-watch-<version>.apk` and, if you want the
face, `mana-watchface-<version>.apk` from the [latest release](https://github.com/iacsha/mana-wear/releases).

- Phone: `adb install -r mana-phone-<version>.apk`, or open the APK on the phone.
- Watch: turn on ADB debugging and Wireless debugging in the watch's Developer options,
  pair with `adb pair`, then `adb install -r mana-watch-<version>.apk` (and the face APK).

The phone and watch apps must come from the same release. They are signed with the same
key, and the Data Layer connects them only when the keys match.

## Pairing

Open Mana on the phone and tap **Scan pairing code**. Point it at the QR code `init`
printed, or run `mana-collector pair` to show it again. The phone saves the collector URL
and token and runs a test fetch. The scanner runs in Google Play services, so Mana needs
no camera permission.

You can also type the URL and token by hand and tap **Save and test**.

### http or https

The phone allows plain `http://` only to private addresses: `10/8`, `172.16/12`,
`192.168/16`, Tailscale's `100.64/10`, loopback, `*.local` and `fd00::/8`. For anything
else, use https. The easy route is `tailscale serve`:

```sh
tailscale serve --bg 7339
```

Then pair with `https://<machine>.<tailnet>.ts.net/v1/usage`.

## What the watch shows

| Shown | Meaning |
|-------|---------|
| `5-hour 7%` and the ring | Five-hour window usage |
| `resets 2h 31m` | Time until the five-hour window resets |
| `week 43% · 1d 12h` | Weekly usage and its reset |
| `5s ago` | Age of the reading |
| `stale` | The reading is over 15 minutes old, or the collector flagged it |
| `unreachable` | The last fetch failed. The figures are the last good reading. |
| `token rejected` | The collector returned 401 or 403. Pair again. |
| `phone not connected` | The watch has no connection to the phone |
| `install Mana on phone` | The phone is connected but has no Mana app |
| `Not set up` | The phone has no collector URL yet |

On the Mana face, the ring turns amber at 80% used and red at 95%.

## Troubleshooting

**The watch says `stale` and the age keeps growing.** No Claude Code status line has
rendered since that reading. Open or use a Claude Code session and the next render updates
it. Check that the tap is wired up: `statusLine.command` in `~/.claude/settings.json`
should run `mana.ts` with the `tap` argument.

**The phone's test says unreachable.** From the phone, open the collector URL's
`/healthz` in a browser; it should say `ok`. If not, check that the collector service is
running and that the phone can reach that address (Tailscale running on both, or the same
LAN).

**`token rejected`.** The token changed, usually from a second `init`. Run
`mana-collector pair` and scan again.

**The watch says `install Mana on phone` with the app installed.** The two APKs are from
different builds and are signed with different keys. Install both from the same release.

**Figures differ a little from `/usage`.** The tap records what the last status line
render carried. `/usage` asks the server at that moment, so it can be a minute or two
ahead.

## Building from source

- Collector: `bun install && bun test`.
- Android apps: see [android/README.md](android/README.md).

## License

Apache-2.0. See [LICENSE](LICENSE).

Mana is an independent project and is not affiliated with or endorsed by Anthropic.
"Claude" and "Claude Code" are trademarks of Anthropic.
