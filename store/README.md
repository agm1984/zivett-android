# Play Store submission kit

Generated 2026-09-09. The ordered checklist lives in `PARITY.md` under
"Play Console checklist"; this folder holds the material each step needs.

| File | Use |
|---|---|
| `LISTING.md` | App name, short/full description, category, and the reviewer credentials for App access |
| `DATA-SAFETY.md` | Every Data safety form answer, derived from the source |
| `SCREENSHOTS.md` | Shot list plus the emulator/adb commands to capture them |
| `play-icon-512.png` | 512×512 app icon. Full-bleed twin of the launcher icon — Play applies its own corner mask |
| `feature-graphic-1024x500.jpg` | 1024×500 feature graphic, no alpha channel |
| `play-icon.svg`, `feature-graphic.svg` | Sources. Re-render with `qlmanage -t -s 1024 -o . <file>.svg` (see below) |

Both PNG/JPEG assets were rendered from the SVGs with macOS Quick Look,
which pads a thumbnail to a square — `feature-graphic.svg` is therefore
authored on a 1024×1024 canvas with the artwork in the centre 500 rows, and
cropped back with `sips -c 500 1024`. Keep that arrangement if you edit it.

## App Links needs TWO fingerprints, not one

`ANDROID_APP_FINGERPRINTS` on production currently holds a single cert:

```
4C:CF:56:B1:18:AA:C1:3E:45:9C:FE:93:97:06:2F:76:92:5A:69:3F:6B:E4:32:9D:5C:B6:88:16:F9:1E:1C:C3
```

That is the debug cert. Two more matter, because App Links verify against
whichever key actually signed the installed app:

1. **The upload key** — signs anything you sideload from
   `assembleRelease`. Add this now or `zivett.com/r/*` links will silently
   fall back to the browser during the physical-device pass, and you will
   spend an hour thinking you broke deep linking:

   ```
   FE:AC:60:3D:F7:EE:E4:EE:78:BD:F6:A8:51:2E:39:AE:58:B1:A4:42:02:FC:6D:AA:C3:2E:65:A4:F7:94:59:54
   ```

2. **Play's app-signing key** — signs what real users install, because
   Play re-signs the AAB. This is the one that matters in production.
   Copied 2026-09-11 from the App signing page (URL
   `…/app/<id>/keymanagement`; the sidebar entry keeps moving):

   ```
   42:AF:41:02:03:A9:44:C1:17:23:10:F8:02:93:E9:C9:95:02:D3:E7:2A:87:15:87:B6:39:8A:D4:9A:A6:4A:AE
   ```

Set all three, comma-separated, then `php artisan config:cache` and
re-verify on device:

```sh
adb shell pm verify-app-links --re-verify com.zivett.app
adb shell pm get-app-links com.zivett.app     # expect "verified"
```

## Artifacts

- Upload artifact: `app/build/outputs/bundle/release/app-release.aab`
  (`./gradlew :app:bundleRelease`). Rebuilding just bumps the
  day-count `versionCode` (see README "Version code"); a second upload
  the same day needs `-PbuildNumber=1`.
- Device-test artifact: `app/build/outputs/apk/release/app-release.apk`
  (`./gradlew :app:assembleRelease`) — signed with the upload key and
  sideloadable. An AAB cannot be installed directly.
- The AAB embeds the R8 mapping under `BUNDLE-METADATA`, so Play retraces
  crash stacks with no separate `mapping.txt` upload.
