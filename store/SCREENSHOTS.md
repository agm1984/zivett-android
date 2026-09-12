# Screenshot capture plan

**Done 2026-09-11**: ten shots in `~/Desktop/ZiVETT Play Store
Screenshots/Phone (1080x1920)/`, numbered `01-live-tracking` …
`10-welcome` to mirror the App Store set. Captured on the debug build
against local Sail (`customer@ratedpro.ca`, `company@ratedpro.ca`) for
01–08, and on the release build for 09–10 (the debug build prints a
`Server:` footer on Welcome). Notes for a re-shoot:

- The stock AVD is 1080×2400; `adb shell wm size 1080x1920` gives a
  native 9:16 without a new AVD, and `wm size reset` puts it back.
- The seeded en-route trail ages: shift `job_location_points.created_at`
  forward (tinker one-liner) or the map badge reads "147h ago".
- The thread view has no bottom nav, so tab-bar taps land in the
  message box; leave with the back arrow first.

Play needs **2–8 phone screenshots**, PNG or JPEG, each side 320–3840 px.
Shoot at **1080×1920** (a clean 9:16) so nothing has to be cropped later.
Tablet screenshots are optional; the app has no tablet-specific layout, so
skip them and leave the app phone-only in the Console.

## Which build

Use the **release** build against production, logged in as the review
accounts. It doubles as the physical-device pass on the checklist, and the
screenshots then show real production data and correct URLs.

```sh
cd /Users/adammackintosh/dev/zivett-android
./gradlew :app:assembleRelease        # signed APK, sideloadable
adb install -r app/build/outputs/apk/release/app-release.apk
```

Log in as `review-customer@zivett.com` for shots 1–5 and
`review-company@zivett.com` for 6–8.

## Emulator sized for the store

The stock Pixel AVDs are 1080×2400, which is not 9:16. Create an AVD on the
**Nexus 5** hardware profile (1080×1920, 420 dpi) — exactly the ratio Play
wants, no post-processing. Confirm before you shoot:

```sh
adb shell wm size      # expect 1080x1920
```

## Clean status bar (worth the 30 seconds)

Demo mode freezes a tidy clock and a full battery, so the shots look
composed rather than captured:

```sh
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile hide
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
```

Afterwards:

```sh
adb shell am broadcast -a com.android.systemui.demo -e command exit
```

## Capture

One command per screen, run with the screen already on the right state:

```sh
mkdir -p store/screenshots
adb exec-out screencap -p > store/screenshots/01-book.png
```

Verify every file before uploading:

```sh
for f in store/screenshots/*.png; do
  printf '%s  ' "$f"; sips -g pixelWidth -g pixelHeight "$f" | tr '\n' ' '; echo
done
```

## Shot list, in upload order

Play shows the first two almost everywhere, so the trust story leads.

| # | Screen | Why it earns the slot | How to get there |
|---|---|---|---|
| 1 | Booking — trade picker / first wizard step | "Pick a trade. Compare trusted Pros." is the whole pitch | Customer → Book |
| 2 | Quote comparison with Pro trust profile | Shows the 5-Point Trust Standard doing work | Customer → a job with quotes in |
| 3 | Live map, Pro en route | The signature feature, and visually unlike any competitor | Customer → active job whose Pro is En Route |
| 4 | Message thread with a photo | Proves in-app comms and UGC handling (which Apple asked about too) | Customer → Messages |
| 5 | Invoice + pay sheet | Card payment, Stripe-backed | Customer → Invoices → Pay |
| 6 | Pro opportunities list | "Get discovered. Win more jobs." | Company → Opportunities |
| 7 | Pro trust passport | Credentials and rating, the Pro-side trust artefact | Company → Passport |
| 8 | Pro money / payouts | "Get paid securely." | Company → Money |

Upload the raw captures. Device frames and marketing overlays are allowed
but not required, and plain screenshots can never be read as misleading.

## Pre-launch report

After the first internal-testing upload, read **Release → Pre-launch
report**. Play runs the APK on real devices and will flag crashes, ANRs,
and accessibility issues (contrast, touch-target size) that the emulator
never surfaced. Fix anything in the crash tab before promoting.
