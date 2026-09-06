# iOS → Android parity punch list (2026-09-06 port)

Status legend: [x] done · [~] partial · [!] needs a manual step

The whole iOS app (108 Swift files) was read and ported screen by screen.
Everything the iOS app does, this app does, with Material 3 equivalents
where iOS used a native control. Verified on an emulator against the
local Sail backend as customer, company, new company (through email
verification), pending company, business, and new business.

## Foundation
- [x] Networking: `ApiRequest`/`ApiClient`/`HttpApiClient` with the same error mapping (401/403/404/409/422/429, `Conflict` carries the body for 3DS), `PreviewApiClient` for tests
- [x] Token storage: AndroidKeyStore AES-GCM key wrapping the token in private prefs (the iOS Keychain equivalent)
- [x] `AuthSession` state machine (restore / login / register / accept invitation / logout / delete account / refresh) and `Access` rules, tests mirrored from `AccessTests`
- [x] Models: 1:1 mirrors of every Laravel resource, `LaravelInstantSerializer`, route-param int|string, tolerant booking-draft payload, tolerant PHP rate tables
- [x] Realtime: Pusher-protocol client over OkHttp WebSocket with lifecycle naps; bell/thread/tracking poll only while disconnected
- [x] Design system: tokens ported from `Colors.swift`/`Typography.swift` (light + dark), the full Z* component set, brand mark and wordmark, QR renderer
- [x] Navigation: typed `@Serializable` routes, one `RoleShell` per role with a Material `NavigationBar`, shared booker destinations, push/deep-link handoff via `AppEvents`

## Auth
- [x] Welcome (charcoal hero, debug Server menu), Login, Signup (referral + fee banners), Forgot password, Verify email (cooldown, "I verified on the web" recheck), Accept invitation (member photo required for company invites)
- [x] Blocked-account screens (suspended / deactivated / missing organization)

## Customer
- [x] Home heroes (en route with live map, in-progress, quotes with ZIVETT RECOMMENDS, pending, invoice due with inline pay, first-run, empty) + StatusChecklist + secondary strip + Messages badge
- [x] Jobs (Active / History), job detail with all sheets (accept quote, cancel, report, warranty claim, review, pay & close with tip/3DS/card capture, company reviews)
- [x] Messages list + conversation (realtime with polling fallback)
- [x] Notifications inbox + bell badge
- [x] Account: profile (phone OTP, photo for company members), notification prefs, saved addresses (pin-adjust map), referral (QR + share + banked credits), warranties (active/past), invoices + detail + pay + PDF share
- [x] Booking wizard: `BookingWizardEngine` port with tests, server drafts (debounced autosave, resume/start-over rules, photos uploaded on pick), ResumeBookingBanner on the same screens as iOS, success screen
- [x] Terms / Privacy links + delete-account sheet on every Account screen

## Company
- [x] Dashboard (stat tiles → reviews sheet, setup checklist, timeline, referral card with QR), Opportunities + quote composer (payouts pre-flight, leads banner), Quotes, Active/Completed jobs
- [x] Job detail: status advance, assign/reassign, change orders, report, withdraw, breadcrumb tracking map, live-location sharer while en route (LocationManager, foreground only, same as iOS)
- [x] Calendar month grid with chips, blocked days, tap-through
- [x] Passport: credential uploads (document picker + photo picker), viewer (PdfRenderer / image, "Open with…"), details editor, field guide, business-identity row
- [x] Setup wizard + credential guide, submit blocked until complete, pending state
- [x] Subscription (billing card panel, charges history, renewal-aware labels, change confirm/undo), Payouts, Invoices + detail, More, Team, Account

## Business
- [x] Setup gate for org admins (profile → properties → team → plan), skip-for-now dismiss
- [x] Overview, Requests, Properties (+ form sheet, request-job shortcut), Book (shared wizard with property/unit step), Account (profile, business profile form, team + edit member, subscription, notifications, invoices, warranties)

## Push, links, payments
- [x] FCM registration + messaging service + tap routing to the job over any tab; POST_NOTIFICATIONS prompt on shell appear
- [x] Backend: `App\Support\Push` gained an `fcm` driver (HTTP v1, service-account JWT, dead-token pruning) and the `/.well-known/assetlinks.json` route; tests added
- [x] `zivett://` scheme + App Links intent filters; referral and invitation links routed
- [x] StripeBridge (PaymentSheet setup mode + `handleNextActionForPayment`), StripeHost in MainActivity, `zivett://stripe-redirect` return

## Needs a manual step
- [!] **Firebase**: create the Firebase project, register `com.zivett.app`, put `google-services.json` in `app/` (gitignored). Until then push registration is silent by design.
- [!] **Backend push env**: `FCM_PROJECT_ID` and `FCM_SERVICE_ACCOUNT` (JSON or `file://storage/app/private/fcm-service-account.json`) in the local `.env` and in Forge. Without them Android tokens log instead of send.
- [!] **App Links**: `ANDROID_APP_FINGERPRINTS` on the backend (debug keystore SHA-256 locally; Play App Signing certificate in production), then confirm `https://zivett.com/.well-known/assetlinks.json` and re-verify with `adb shell pm verify-app-links --re-verify com.zivett.app`.
- [!] **Device-only verification**: push delivery, live location while en route, camera capture, and the Stripe 3DS challenge were only exercised as far as the emulator allows. Run each once on a physical phone.
- [!] **Release signing / Play listing**: no upload keystore, versionCode scheme, or Play console entry yet. R8 is off for release until keep rules are checked.
- [!] **Git**: this repo was built from an empty Android Studio template and has no git history yet. `git init` and a first commit are up to you.

## Known divergences (intentional)
- Material 3 controls instead of iOS look-alikes (NavigationBar tabs, ModalBottomSheet, ExposedDropdownMenu, Snackbar pill toasts, system pickers). Screen content and copy match iOS.
- Maps are osmdroid on OSM tiles (no API key), matching the web's Leaflet tiles rather than Apple Maps.
- The debug-only `LocalHostRewrite` for server image URLs has no iOS counterpart because the simulator shares the Mac's `localhost`.

## Fixed on iOS while porting
- `PHPRates` property wrapper (2026-09-06): a brand-new company's `hourly_rates` / `category_rates` arrive as `[]` (PHP's empty associative array) and a rate can be `null`; plain `[String: Int]` refused both, which broke the company setup screen for `company-new@ratedpro.ca`. Same fix as Android's `PhpRatesSerializer`, with a decoding test.
