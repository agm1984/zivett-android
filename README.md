# ZiVETT Android

The Android app for ZiVETT, feature-for-feature with the iOS app in
`../zivett-ios` but built the Android way: Kotlin, Jetpack Compose,
Material 3. Both apps talk to the same Laravel backend in
`../ratedpro-web` over the contract in `../ratedpro-web/docs/mobile-api.md`.

If you are new to the codebase, read this file top to bottom once. The
condensed conventions live in `CLAUDE.md`; the iOS → Android punch list
is `PARITY.md`.

## First-day setup

You need Android Studio (Narwhal or newer, which bundles a JDK 17+) and
the backend running. The project targets SDK 37 with a floor of Android 8
(API 26).

### 1. Start the backend

```sh
cd ../ratedpro-web
./vendor/bin/sail up -d
./vendor/bin/sail artisan migrate:fresh --seed   # first time only
```

That gives you the API on `http://localhost:8090`, Reverb websockets on
`8091`, and Mailpit (every email the app triggers) on
`http://localhost:8026`. The seeder creates accounts you will use all
day; every password is `password`:

| Email | What it is |
|---|---|
| `customer@ratedpro.ca` | homeowner with live jobs, quotes, and an invoice due |
| `company@ratedpro.ca` | approved pro (Ravensworth Plumbing) with opportunities and active jobs |
| `company-member@ratedpro.ca` | a tech on that company |
| `company-new@ratedpro.ca` | unverified email → setup wizard, nothing filled in |
| `company-pending@ratedpro.ca` | application submitted, awaiting approval |
| `business@ratedpro.ca` | property manager with properties and requests |
| `business-new@ratedpro.ca` | business mid-setup (lands on the Plan step) |

The two `-new` and `-pending` accounts start with an unverified email, so
the app shows the "Check your email" screen first. Grab the code from
Mailpit.

### 2. Open the project

Open the `zivett-android` folder in Android Studio and let Gradle sync.
Nothing else to configure: the SDK path is picked up from
`local.properties`, which Studio writes for you.

### 3. Run it

Pick any emulator (a Pixel image on API 34+ is what the screenshots in
this repo were taken on) and press Run. A Debug build points at
`http://10.0.2.2:8090`, which is how the emulator reaches the Mac's
`localhost`. Log in as `customer@ratedpro.ca`.

From the command line:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. Run the tests

```sh
./gradlew :app:testDebugUnitTest
```

Plain JUnit 4 on the JVM, no device needed. They cover the pieces that
must match iOS byte for byte: `Access` (who lands where), the booking
wizard engine, customer-home ranking, job presentation, JSON coding
quirks, and the API client's error mapping (`PreviewApiClient` stands in
for the network).

## Switching backends

A Debug build has a **Server** menu on the Welcome screen with three
targets. Release builds always hit production and ignore this.

| Entry | URL | Use it when |
|---|---|---|
| Local (Sail) | `http://10.0.2.2:8090` | emulator, the default |
| Local (Sail via LAN) | `http://<your-mac>.local:8090` | a physical phone on the same Wi-Fi |
| Production | `https://zivett.com` | checking a bug against real data |

The LAN hostname comes from `lan.host` in `local.properties`
(gitignored), defaulting to `Adams-MacBook-Pro.local`. Set yours with
`scutil --get LocalHostName` plus `.local`:

```
lan.host=your-mac.local
```

The base URL itself is the `API_BASE_URL` BuildConfig field, per build
type, in `app/build.gradle.kts`, read by `app/AppConfig.kt`.

One local-only quirk: the Sail backend stamps absolute
`http://localhost:8090/storage/...` URLs on photos and avatars, and
"localhost" on a device is the device. Debug builds rewrite that host to
whatever the Server menu picked (`LocalHostRewrite` in
`ZivettApplication.kt`) so images load. Release never sees localhost.

## Running on a real phone

Needed for push notifications, App Links, the camera, and live location.

1. Enable USB debugging, plug the phone in, pick it as the run target.
2. In the app's Server menu choose **Local (Sail via LAN)**.

Realtime needs one tweak on the backend: `.env` has
`REVERB_HOST=localhost`, which the phone can't reach, so the live bell
falls back to polling. Blank the value (`REVERB_HOST=`) so the app
connects to whatever host served the API, and keep
`sail artisan reverb:start` running.

Cleartext HTTP to the two local hosts is allowed by
`res/xml/network_security_config.xml`; everything else must be HTTPS.

## Push notifications

The app side is done; the Firebase project is the manual step.

- **App**: `core/push/PushRegistration.kt` asks for the notification
  permission when a signed-in shell appears, syncs the FCM token to
  `POST /api/push/tokens` with `platform=android`, and releases it before
  logout. `ZivettMessagingService` turns a data message into a
  notification; tapping it hands `route_name`/`route_id` to
  `MainActivity`, and `PushRouting` opens that job over any tab.
- **Firebase**: create (or get added to) the Firebase project, register
  the Android app with package `com.zivett.app`, and drop the downloaded
  `google-services.json` into `app/`. The file is gitignored. Without it
  the app builds and runs normally and push registration simply stays
  quiet, so nobody is blocked by its absence.
- **Backend**: `App\Support\Push` picks FCM for Android tokens when
  `FCM_PROJECT_ID` is set, and needs a service-account JSON in
  `FCM_SERVICE_ACCOUNT` (either the JSON itself or `file://` a path,
  locally under `storage/app/private/`). Without the project id, Android
  tokens fall through to the `log` driver. Both settings must also land
  in the Forge env for production. iOS is unaffected: APNs settings stay
  as they are.

To exercise tap routing without Firebase, fire the same intent the
notification would:

```sh
adb shell am start -n com.zivett.app/.MainActivity \
  -e route_name customer.jobs.show -e route_id Z-9Q8AAC
```

Use a job code the signed-in account can actually see.

## Stripe

`core/payments/StripeBridge.kt` is the only file that touches the Stripe
SDK. The publishable key arrives at runtime in the billing payload, so
there is nothing to configure in the app. Two jobs: PaymentSheet in setup
mode confirms the SetupIntent when a card is saved, and
`Stripe.handleNextActionForPayment` runs 3DS when pay/close comes back
409 with a `client_secret`. Test cards:

| Card | Behaviour |
|---|---|
| 4242 4242 4242 4242 | succeeds |
| 4000 0027 6000 3184 | forces the 3DS challenge |

3DS returns through `zivett://stripe-redirect`; the scheme is registered
on `MainActivity`. Connect onboarding for pros is a hosted web link.

## Deep links

- `zivett://r/CODE` and `https://zivett.com/r/CODE` open signup with a
  referral applied. `https://zivett.com/invitations/TOKEN` opens the
  invitation acceptance screen.
- The `https` links are App Links (`android:autoVerify`), which only
  verify once `https://zivett.com/.well-known/assetlinks.json` lists the
  signing certificate. The backend serves that file when
  `ANDROID_APP_FINGERPRINTS` is set (comma-separated SHA-256
  fingerprints; the Play App Signing one for production, your debug
  keystore's for a dev build). Get the debug one with:

  ```sh
  keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep SHA256
  ```

- Locally, the custom scheme covers testing:

  ```sh
  adb shell am start -a android.intent.action.VIEW -d "zivett://r/SOMECODE"
  ```

## How the code is organised

```
app/src/main/java/com/zivett/app/
  MainActivity.kt        single activity; intents (push, links) → AppEvents
  ZivettApplication.kt   Coil image loader, osmdroid config, push/referral hooks
  app/                   AppConfig, AppEnvironment (composition root), Routes, RootScreen
  core/
    network/             ApiClient + HttpApiClient (OkHttp), ApiRequest, ApiError, JsonCoding
    storage/             TokenStore (AndroidKeyStore-wrapped / in-memory)
    auth/                AuthSession (state machine), Access (authorization rules), payloads
    models/              @Serializable mirrors of the Laravel resources + *Endpoints objects
    realtime/            Pusher-protocol client over OkHttp WebSocket
    push/                FCM registration, messaging service, tap routing
    payments/            StripeBridge
    media/               photo import (EXIF-aware downscale to JPEG)
  design/                ZColors/ZType tokens, ZivettTheme, Z* components, maps, QR
  features/              one package per screen family; each screen = composable + model
app/src/test/            JUnit 4; PreviewApiClient stands in for the network
```

### The patterns to copy

- **A screen is a composable plus a model.** The model is a plain class
  holding `mutableStateOf` fields, taking `ApiClient` and `AuthSession`
  as constructor parameters, so a unit test can drive it with
  `PreviewApiClient`. The composable stays thin. Look at
  `features/customer/jobs/JobDetailModel.kt` next to `JobDetailScreen.kt`.
- **Endpoints are factories on an `*Endpoints` object** next to their
  models (`CustomerEndpoints.job(id)`), never paths built inline in a
  screen.
- **`Loadable<T>`** (Loading / Loaded / Failed) plus `reloaded {}` is how
  every model holds server data. A refresh that fails keeps the last
  good value.
- **Authorization** is decided once in `Access.destination(user)` and
  `user.can(capability)`. The server still enforces everything; this is
  for routing and hiding controls, and it is fully unit-tested against
  the same cases as iOS.
- **Design tokens** come from `design/Colors.kt` and `design/Typography.kt`,
  ported from the web's `@theme`. New colors go there with a dark
  variant, never inline hex.
- **JSON**: `JsonCoding.json` is snake_case on the wire, ignores unknown
  keys, treats absent as null. Laravel's timestamps decode through
  `LaravelInstantSerializer`; PHP's habit of writing an empty associative
  array as `[]` is absorbed by `PhpRatesSerializer` (rate tables) and the
  booking-draft decoder.

### Android-specific choices

Where iOS uses a native control, this app uses the Material 3
equivalent rather than imitating iOS: a bottom `NavigationBar` for the
tabs, `ModalBottomSheet` for the action sheets, `ExposedDropdownMenu`
for pickers, a Snackbar-style pill for toasts, `PullToRefreshBox` for
refresh, the system photo picker and document picker for uploads, and
`PdfDocument` + a `FileProvider` share intent for invoice PDFs. Maps are
osmdroid on OpenStreetMap tiles, the same tiles the web's Leaflet maps
use, so there is no Google Maps key to manage.

## Shipping

Not set up yet. Before the first Play upload: pick a `versionCode`
scheme, create the upload keystore (or opt into Play App Signing), add
its SHA-256 fingerprint to `ANDROID_APP_FINGERPRINTS` on the backend, and
turn `optimization.enable` back on for release in `app/build.gradle.kts`
once the R8 keep rules for kotlinx.serialization and Stripe have been
checked.

## Where else to look

- `../ratedpro-web/docs/mobile-api.md`: the API contract both apps follow.
- `../zivett-ios/README.md`: the same walkthrough for the iOS app.
- `PARITY.md`: what was ported, what needs a manual step.
