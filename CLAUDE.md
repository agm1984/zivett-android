# ZiVETT Android

Kotlin + Jetpack Compose (Material 3) app, feature-parity twin of
`../zivett-ios`. Talks to the Laravel backend in `../ratedpro-web` with
Sanctum bearer tokens — contract in `../ratedpro-web/docs/mobile-api.md`.
Human onboarding lives in `README.md`; this file is the condensed
conventions. The iOS → Android punch list is `PARITY.md`.

## Build & test

```sh
./gradlew :app:assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest      # JUnit 4 on the JVM, no device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Toolchain: AGP 9.4 with its built-in Kotlin 2.2 compiler, Compose BOM
2026.02, minSdk 26, target/compileSdk 37, Java 17. Libraries must be
compiled with Kotlin ≤ 2.3 (that is why Coil is pinned to 3.3.0 in
`gradle/libs.versions.toml`; 3.6.x needs Kotlin 2.4).

Debug builds hit `http://10.0.2.2:8090` (the Sail container through the
emulator's host alias). Release builds hit `https://zivett.com`. Both
are the `API_BASE_URL` BuildConfig field in `app/build.gradle.kts` →
`app/AppConfig.kt`. Debug builds also get a Server menu on Welcome
(`AppConfig.debugBackends`): local, local-via-LAN (`lan.host` in
`local.properties`), production. `AppEvents.backendChanged` rebuilds
`AppEnvironment`.

## Push, links, payments

- **Push**: FCM. `core/push/PushRegistration.kt` (token →
  `POST /api/push/tokens`, `platform=android`, released before logout),
  `ZivettMessagingService` (data payload → notification with
  `route_name`/`route_id` extras), `PushRouting` (tap → job by CODE via
  `JobRefEndpoints`). The google-services plugin is applied only when
  `app/google-services.json` exists (gitignored), so builds never depend
  on it. Backend: `FCM_PROJECT_ID` + `FCM_SERVICE_ACCOUNT` env.
- **Links**: `zivett://` scheme plus App Links for `zivett.com/r/*` and
  `/invitations/*` (manifest, `autoVerify`). The backend serves
  `/.well-known/assetlinks.json` when `ANDROID_APP_FINGERPRINTS` is set.
- **Stripe**: `core/payments/StripeBridge.kt` is the ONE file that
  imports the Stripe SDK. Keyed at runtime from the billing payload's
  `publishable_key`. PaymentSheet in setup mode for card capture;
  `Stripe.handleNextActionForPayment` for a 409 `payment_action_required`
  + `client_secret` on pay/close, after which the webhook settles
  server-side and the models poll briefly. Return URL
  `zivett://stripe-redirect`. `StripeHost` is mounted in `MainActivity`.
  Test cards: 4242 4242 4242 4242, 4000 0027 6000 3184 (3DS).

## Layout

```
app/src/main/java/com/zivett/app/
  MainActivity.kt, ZivettApplication.kt
  app/        AppConfig, AppEnvironment (composition root + AppEvents), Routes, RootScreen
  core/
    network/  ApiClient + HttpApiClient (OkHttp), ApiRequest, ApiError, JsonCoding, PreviewApiClient
    storage/  TokenStore (AndroidKeyStore AES-GCM / in-memory)
    auth/     AuthSession (state machine), Access (authorization rules), payloads
    models/   @Serializable mirrors of the Laravel resources + *Endpoints objects + *Area bundles
    realtime/ PusherProtocol + RealtimeClient (OkHttp WebSocket, lifecycle naps)
    push/ payments/ media/
  design/     Colors/Typography/Theme tokens + Z* components, Maps (osmdroid), QRCode
  features/   one package per screen family; each screen = composable + model class
app/src/test/java/com/zivett/app/   JUnit 4; Fixtures + PreviewApiClient stand in for the network
```

## Conventions

- Every screen's logic lives in a plain model class holding
  `mutableStateOf` fields that takes `ApiClient`/`AuthSession` as
  parameters → unit-testable with `PreviewApiClient`. Composables are
  thin. Models are created with `remember { }` in the screen and run
  work in `rememberCoroutineScope()` / `LaunchedEffect`.
- Server data is held as `Loadable<T>`; refresh with `state.reloaded {}`
  so a failed refresh keeps the last good value.
- Endpoints are factories on an `*Endpoints` object next to their
  models; never build paths inline in composables.
- Authorization is decided once in `Access.destination(user)` /
  `user.can(...)` — the server still enforces everything; this is for
  routing and hiding controls, and it is fully unit-tested.
- Design tokens come from the web's `@theme` (`resources/css/app.css`);
  add new colors to `design/Colors.kt` with a dark variant, never inline
  hex. Material 3 components, not iOS look-alikes: NavigationBar tabs,
  ModalBottomSheet, ExposedDropdownMenu, Snackbar pill toasts.
- JSON: `JsonCoding.json` (snake_case, ignore unknown, absent → null),
  `JsonCoding.explicitNulls` for bodies that must clear a field,
  `JsonCoding.plain` for the booking-draft snapshot (web's exact key
  spelling). `Instant` fields need
  `@file:UseSerializers(LaravelInstantSerializer::class)`. PHP writes an
  empty associative array as `[]`: rate tables use `PhpRatesSerializer`,
  the draft payload has its own tolerant decoder.
- Realtime consumers poll only when `RealtimeClient.connected` is false.
- Keep the API contract platform-neutral; `Access`, `CustomerHomeLogic`,
  `BookingWizardEngine`, and the endpoint tables are 1:1 ports of the
  iOS files and their tests should stay in lockstep.
- Debug-only host rewrite: `LocalHostRewrite` in `ZivettApplication.kt`
  maps the backend's `localhost` image URLs to the selected backend
  host. Do not extend it to release builds.

## Gotchas hit while building

- Sealed interface members named `value` collide with data-class
  properties of the same name; `Loadable.Loaded` stores `loaded`,
  `AuthState.SignedIn` stores `account`.
- A data-class property named `field` needs `this.field` inside the
  class (the property-setter keyword shadows it).
- OkHttp response bodies must be read inside the enqueue callback;
  reading after resuming onto Main throws `NetworkOnMainThreadException`.
- osmdroid's `MapView` in `AndroidView` draws past its bounds; wrap it in
  a clipping `FrameLayout` and add `Modifier.clipToBounds()` (see
  `design/Maps.kt`).
- `Stripe.handleNextActionForPayment` needs a `ComponentActivity`, not a
  plain `Activity`.
