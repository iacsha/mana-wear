# Wear OS APK distribution without Google Play (researched 2026-09-29)

Legend: [P] = first-party Google/Android source, [S] = secondary, UNKNOWN = not found.

## 13. Play developer account requirements (2026)

Answer: One-time US$25 fee. Personal accounts may be asked for a government ID and a credit card in the
same legal name, plus device verification via the Play Console mobile app. D-U-N-S applies to
Organization accounts only. Personal accounts created after 2023-11-13 must run a closed test with
at least 12 testers opted in continuously for 14 days before production access.

Evidence:
- [P] https://support.google.com/googleplay/android-developer/answer/6112435
  "There is a US$25 one-time registration fee" / "you may be asked for a valid government ID and a credit
  card, both under your legal name." / "Prepaid cards are not accepted."
  "developers with new personal accounts will be required to verify ... access to an Android device using
  the Play Console mobile app"
- [P] https://support.google.com/googleplay/android-developer/answer/10788890
  "D-U-N-S number, if registering as an organization". Organization is mandatory for financial, health,
  VPN, government apps.
- [P] https://support.google.com/googleplay/android-developer/answer/14151465
  "must run a closed test for their app with a minimum of 12 testers who have been opted in continuously
  for at least 14 days." Applies to "personal developer accounts created after November 13, 2023."
  The 12 is still the current figure on that page; no other number is given.
- UNKNOWN: whether the 12/14 rule is waived for any app category. Not stated on the pages fetched.
- Not relevant to us unless we choose Play: fee and ID are the cost of that route.

## 14. Android developer verification

Answer (short): Applies to apps installed on certified Android devices. Enforcement begins 2026-09-30
(tomorrow) only in Brazil, Indonesia, Singapore, Thailand, and only for participating stores. Global in
2027. adb installs are exempt. Wear OS is never named in the program pages; the FAQ says enforcement
only applies to "mobile and tablet form factors" in the selected regions, so a watch APK is not
enforced now. 2027 scope for watches: UNKNOWN.

Timeline [P] https://developer.android.com/developer-verification
- "August 2026 - Developer APIs, limited distribution accounts, and power user advanced flow launch."
- "September 30, 2026 - Regional deadline in Brazil, Indonesia, Singapore, and Thailand for participating app stores."
- "2027 and beyond - Global rollout for all certified Android devices."
- Devices: "certified devices running Android 7+."
- Wear OS: appears only in site navigation, not in the verification content.

Form factors [P] https://developer.android.com/developer-verification/guides/faq
- "If a developer distributes apps on Google Play, their apps across all form factors must be registered.
  For distribution outside of Google Play, it is recommended to register all apps across form factors to
  future-proof your apps availability, though enforcement will only apply to mobile and tablet form
  factors in the selected regions."
- Sideload outside Play, current status: "If you distribute your app through other stores, or if users
  sideload your app directly, these new verification requirements won't apply to your app yet... we still
  recommend that you plan to complete your verification before the global rollout begins in 2027."

adb (same FAQ) [P]
- "As a developer, you are free to install apps without verification with ADB."
- "No, there are no changes to how ADB works... The waiting period does not apply to ADB installs."
- "Unregistered apps can only be installed or updated when the advanced flow is enabled or by using ADB"
- Package installer path: unverified apps are blocked on in-scope devices unless the user has done the
  advanced flow: developer mode, "Confirm you aren't being coached", restart and reauth, "a one-time,
  one-day wait", then biometric/PIN; enable "for 7 days or indefinitely"; then tap "Install Anyway".
- Whether the advanced flow exists on Wear OS: UNKNOWN (docs describe phones).

Free tier [P] FAQ + https://android-developers.googleblog.com/2026/03/android-developer-verification.html
- "we offer a limited distribution account (launching in August). This lets you share apps with up to 20
  specific devices for testing and personal use at no cost and without ID verification."
- Blog: "share apps with a small group (up to 20 devices)... without needing to provide a
  government-issued ID or pay a registration fee." Can migrate to a full account, not back.
- Full Distribution account: "The $25 fee ... similar to Play's $25 registration fee." Organizations need a
  D-U-N-S ("can take up to 28 days" to obtain). Verification is identity + package-name + signing-key
  registration: "If you lose your signing key you won't be able to register your packages."
- [S] Wikipedia "Keep Android Open" summarizes requirements as legal name, address, government photo ID,
  US$25, signing-key evidence, declaration of app identifiers. Consistent with [P] above.

What a GitHub Releases dev must do
- Now (Wear OS, outside the four regions): nothing required. Enforcement is not in force for watches.
- Before 2027 global rollout, to be safe: register the package name + signing key in the Android
  Developer Console, either a Full account ($25 + ID) or a Limited Distribution account (free, no ID, but
  capped at 20 devices, so unsuitable for public release). Keep the signing key safe; use one stable key.
- Otherwise users must use adb or the advanced flow. The docs do not address open source or GitHub by name.
- UNKNOWN: the FAQ line "Developers can continue to distribute apps through ... direct website downloads"
  is stated, but no page says whether a GitHub-hosted APK needs anything beyond registration.

## 15. F-Droid and Wear OS

Answer: F-Droid has no Wear OS category, filter, or watch client. No Wear OS-only app on F-Droid was
confirmed. F-Droid has no documented acceptance or rejection policy for watch apps; the wearable
metadata proposal has no visible resolution.

Evidence [S, F-Droid community; no first-party policy page found]
- https://gitlab.com/fdroid/fdroiddata/-/issues/1941 "Proposal: add support for Wear OS": proposes a
  metadata field with values yes/notification/no. Page shows no status or maintainer reply. Outcome UNKNOWN.
- https://forum.f-droid.org/t/wearos-version-of-f-droid/31323 (Apr 2025): a dev asks for a WearOS F-Droid
  client ("I have developed 3 FOSS apps for WearOS that I want to publish"); maintainer Licaon_Kter notes
  "the Google wear libs are proprietary". No decision. Installing onto the watch "might require an ADB solution."
- Practical risk: Wear OS apps built on Google's proprietary Wear/Play Services libraries can conflict with
  F-Droid's inclusion rules on non-free dependencies; I did not verify against the inclusion policy. UNKNOWN.
- Phone-companion apps that carry a watch module do exist on F-Droid (e.g. Tasks.org, per
  https://github.com/tasks/tasks/issues/3099 [S]), but the watch part then comes from Play, not F-Droid.
- Alternative FOSS channel: IzzyOnDroid or a self-hosted F-Droid repo (not researched; UNKNOWN for Wear OS).

F-Droid position on Google developer verification
- [P] f-droid.org banner (seen on https://f-droid.org/en/news/): "F-Droid is under threat. Google is
  changing the way you install apps on your device. We need your help." linking https://keepandroidopen.org/
- The Feb 2026 open letter URL I guessed 404'd. Claims that F-Droid called it "existential, not cosmetic"
  and that F-Droid signs builds with its own key (conflicting with key registration) come from [S]
  search summaries (pinggy.io blog, Wikipedia "Keep Android Open"); I could not confirm them first-party.
- F-Droid 2.0 shipped 2026-09-24 ([P] https://f-droid.org/en/2026/09/24/f-droid-2.0-a-new-chapter-for-android-freedom.html);
  the post itself never mentions verification or signing keys.

## Sideload paths to a Pixel Watch (brief)

1. adb over Wi-Fi [P] https://developer.android.com/training/wearables/get-started/debug-wifi
   Watch: Settings > Developer options > enable ADB debugging and Wireless debugging > Pair new device.
   PC: `adb pair ip:pairing-port` (enter code), then `adb connect ip:connection-port`, then
   `adb install app.apk`. "You need adb version 30.0.0 or higher"; same Wi-Fi, beware AP isolation
   (hotspot workaround). Connection port differs from pairing port; reconnect after Wi-Fi changes.
2. Bluetooth debugging is gone: "Debugging over Bluetooth is no longer supported as of Wear OS 3."
   [P] https://developer.android.com/training/wearables/get-started/debugging
3. USB: only on watches with a data-capable cradle; Pixel Watch generally lacks it (not verified here).
4. Phone-side "Wear Installer"-style apps: not verified against primary sources. UNKNOWN whether any
   current one works on Wear OS 4/5+ and Pixel Watch; treat as third-party, install-at-own-risk.
5. adb is explicitly exempt from developer verification, so path 1 is safe through 2027 per the FAQ.
