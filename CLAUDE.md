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
- **Links**: `zivett://` scheme plus App Links for `zivett.com/r/*`,
  `/invitations/*` and `/app/stripe-return` (manifest, `autoVerify`).
  The last is the way back from Stripe's hosted Connect onboarding: the
  onboarding-link request sends `return_to: "app"`, the server's return
  page opens the app (App Link, or its `zivett://stripe-return` button),
  `MainActivity` sets `AppEvents.stripeReturned`, the approved-company
  shell lands on the dashboard, and the dashboard consumes the flag and
  re-checks payout status. The backend serves
  `/.well-known/assetlinks.json` when `ANDROID_APP_FINGERPRINTS` is set.
- **Stripe**: `core/payments/StripeBridge.kt` is the ONE file that
  imports the Stripe SDK. Keyed at runtime from the billing payload's
  `publishable_key`. PaymentSheet in setup mode for card capture;
  `PaymentLauncher` (Activity Result based — no `onActivityResult`, no
  app return URL) for a 409 `payment_action_required` on pay/close. The
  server charges OFF-session, so that PaymentIntent is in
  `requires_payment_method`: `StripeBridge.confirmPayment` CONFIRMS it
  again with the 409's `payment_method_id` (next-action only when an
  older server omits the id), then the webhook settles server-side and
  the models poll briefly. The result is a `ChallengeOutcome`
  (`core/payments/PaymentChallenge.kt`, SDK-free; models take a
  `PaymentChallenger` so tests can drive it): canceled and a bank
  refusal mean nothing was charged, `Unknown` (the result never reached
  us) must NEVER be worded that way — poll first. `StripeHost` is
  mounted in `MainActivity`.
  Test cards: 4242 4242 4242 4242, 4000 0027 6000 3184 (3DS),
  4000 0000 0000 0341 (saves fine, DECLINES at charge — the one to test
  the card-change flow with).
- **Plans (Google Play Billing)**: `core/store/StoreBridge.kt` is the ONE
  file that imports the Play Billing Library (`PlayStore`, `StoreHost`
  mounted beside `StripeHost` for the activity + delivery); everything
  else speaks `StoreTypes.kt`. The app never decides a purchase is
  valid: it posts the purchase TOKEN to
  `POST /api/{company|business}/subscription/google-play` and the
  server asks Google. Acknowledge only AFTER the server took it
  (`StoreDelivery`) — Google refunds unacknowledged purchases after 3
  days, which is the right outcome if we never granted the plan.
  Prices are always Play's formatted strings. Never offer a purchase
  while another biller owns the plan (`PlanShelf.mode`: App Store or a
  paid web term → read-only), and never mention or link web pricing.
  Contract: `docs/mobile-api.md` "Android: Google Play Billing"; Play
  Console checklist: README "Google Play Billing".
- **Every pay surface shows the card and lets it be changed**
  (`core/payments/PaymentCard.kt` — `PaymentCardModel` +
  `PaymentCardSection`, which go through `StripeBridge`, never the SDK):
  pay invoice, Pay & close, the home invoice hero. A declined charge is a
  422 `{ message, code: "payment_declined" }` with NO `errors` key
  (`ApiError.paymentDeclinedMessage`) — keep the surface open, show the
  bank's message in the section, and let them swap cards; never
  toast-and-dismiss. `POST /api/billing/card` also re-pins the new card
  on the booker's live holds server-side. The Pay gate is
  `PaymentCardModel.blocksPay` on all three — only a LOADED "Stripe, no
  card" context disables Pay; loading or a failed fetch (which shows a
  Retry) never does, because the server is the real gate. There is no
  The card itself always renders through `SavedCardLine` (brand ••••
  last4, "exp MM/YY", a danger EXPIRED badge + a promoted "Use a
  different card"); `saved_card.exp_month/exp_year` are nullable, and
  the server's `saved_card.expired` wins over the local month maths
  when present.
- **Reading the card never mints a SetupIntent.** Display / `canCharge`
  / the 3DS publishable key all go through `ApiClient.billingCard()` →
  `GET /api/billing/card` (no Stripe call; falls back to the POST only
  when an older server 404/405s). `POST /api/billing/setup-intent`
  (`CustomerEndpoints.cardSetupIntent()`) is called ONLY when a card
  form is about to open — `PaymentCardModel.changeCard` and the accept
  sheet's `collectCard()` — one fresh intent per attempt.

- **Money calls are never abandoned.** `JobDetailModel.close` /
  `acceptQuote` / `cancel` and `PayInvoiceModel.pay` run
  `withContext(NonCancellable)` (their callers' scopes belong to sheets),
  the sheets pin themselves with `ZSheet(dismissable = !busy)`, and every
  `catch (Exception)` on a money path rethrows `CancellationException`
  first — a cancelled call says nothing about what the server did.

## Devices & orientation (2026-10-24)

Phones stay portrait; large screens (600dp+ on the short side — tablets,
unfolded foldables) turn freely: `MainActivity.orientationFor`, applied in
onCreate (a fold recreates the activity) and onConfigurationChanged — no
manifest lock (Android 16+ ignores one on large screens for API 36+
anyway). Every screen sits in the readable column — `ZScreen`, or
`ZReadableWidth` for screens that don't (welcome panels, booking wizard):
560dp on a phone, 720dp on a large screen (`readableMaxWidth()`); a
`widthIn` after `fillMaxSize` is IGNORED, which left every screen edge to
edge on a tablet until this date. `ZScreen` also carries `imePadding()`
so the page shrinks above the keyboard (in tablet landscape it covers the
Log in button), and `ZTextField(onImeAction = …)` wires the keyboard's
✓/Next key (login: Next → password via a FocusRequester — the default
focus order went to "Forgot?" — and ✓ submits). Secure fields have the
show/hide eye.

**Screen tour:** `scripts/screen-tour.py <out-dir> [portrait|landscape]`
drives the first `adb` device — signs in as the seeded dev accounts
(fresh `pm clear` per role, keyboard Next/✓ like a person) and
screenshots every tab, menu row and first list item, tapping nothing
that changes data. A large-screen AVD is `Large_Screen_API_35`
(2208×1840, 420dpi).

## Calendar & dispatch (2026-10-25)

Contract: `docs/mobile-api.md` "Calendar & dispatch". The company
Calendar (`features/company/CompanyCalendar.kt`) is a Month | Week toggle
over ONE payload (`CompanyCalendar` in `core/models/CalendarModels.kt`,
`?month=` / `?week=`); all derivation is pure in `CalendarLogic` (flags,
load-vs-cap labels, the board rows, window states, openings, map legs,
settings bodies) and tested in `CalendarDispatchTest`. Month cells show
the lead's face + window + code, warning-tinted for `needs_crew` /
`needs_start` / `overdue`, dashed ghosts for `moves` and `tentative`, and
"N open" for `openings`; admins get a tech filter. Week = the dispatch
board: a real grid at ≥600dp width, a per-day list grouped by tech on a
phone. Tapping a day opens `CalendarDaySheet` (window states + blocks
with an inline confirm, the osmdroid `DayRouteMap` — numbered pins from
the company `base`, straight-line legs — jobs with Start job (the job
page's `CrewPickerSheet` via `CompanyJobDetailModel`) and Move, pending
moves, tentative quotes, openings whose Quote opens the feed's
`QuoteComposerSheet`, and time off). Settings (admins): working days go
through `updateWorkingDays` (explicit null = every day — `DetailsBody`'s
coder would drop it), weekly blocks + cap through
`PATCH /api/company/availability`. Moving a visit is `MoveVisit.kt`
(`MoveVisitModel` + sheet; `VisitTimeCard` on the company job page);
the booker answers on the job page's `RescheduleCard`
(`JobDetailModel.decideReschedule`, via `JobArea.accept/declineReschedule`;
409 `schedule_conflict` shows the server's sentence and reloads). PHP's
`[]` for empty objects: `load` uses `PhpRatesSerializer`, `openings`
its own `OpeningsSerializer` (drops undecodable rows).

## My quotes (2026-10-25)

Contract: `docs/mobile-api.md` "My quotes". `CompanyQuotesScreen`
(`features/company/CompanyQuotesJobs.kt`) is a summary line ("N out · $X
in play · N accepted in the last 30 days") over **Waiting on a decision**
(rich cards: booker photos, trade + code, price + "You keep ≈", "Can
arrive …", the release state + "Sent N days ago"; Revise is the one
button and shows only while `job.quotes_released` is false — the PATCH
422s after; Withdraw sits in the More menu with its confirm) and **Past
quotes** (Accepted / Declined / Job cancelled — never Won / Not chosen,
Damon's review #12; decided date, price; All · Accepted · Declined + a
title/code search; only Accepted rows open `CompanyJobRoute`).
All derivation is pure in `CompanyQuotesLogic` (`CompanyQuotesLogicTest`).
`decided_at`, `job.status` (a string) and `job.quotes_released` decode
with defaults, so an older server reads as "not released, undated".

## Review pass (2026-10-29)

Reviews only on closed jobs (`JobStatus.reviewable`; a review never
closes or charges). Nav badges: `features/shared/NavBadges.kt`
(`NavBadgesModel` per shell via `RoleShell(navBadges =)` →
`LocalNavBadges`; `ViewingNavBadge(key)` marks a "since you looked" badge
seen while its screen shows). Notification taps resolve through
`PushTarget` (`PushRouting.target` / `AppNotification.target`) — job
routes open the job, `company.opportunities|quotes|payouts` open
`openCompanyTarget`; add a case there for any new non-job route. Details
in PARITY.md "Review pass port".

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
- `rememberPaymentLauncher` needs the publishable key at composition
  time, but the key only arrives with a billing payload — so
  `StripeBridge.publishableKey` is Compose state and `confirmPayment`
  waits (briefly) for `StripeHost` to build the launcher.
