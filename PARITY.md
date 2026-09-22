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
- [x] **Change the card on every pay surface (2026-09-19, mirrors iOS).** A saved card that DECLINED was a dead end: "use a different card" had nowhere to go — the pay screens only offered a card when NONE was on file, and Pay & close dismissed itself whatever happened. Shared `core/payments/PaymentCard.kt` (`PaymentCardModel` + `PaymentCardSection`: card on file + "Use a different card" / "Add a payment card" → PaymentSheet on a FRESH SetupIntent → `POST /api/billing/card`, which now also re-pins the card on live holds) on all three: `PayInvoiceScreen`, `PayAndCloseSheet`, the home invoice hero (the single-use `BillingCardPanel` is gone). A decline is 422 `{message, code: "payment_declined"}` — `ValidationErrors` keeps `code` (`ApiError.paymentDeclinedMessage`); `JobDetailModel.close` returns a `CloseOutcome` so the sheet stays open on `Declined` with the bank's message.
- [x] **Accept-quote failures show in the accept sheet (2026-09-20).** They went to the job page's toast — under the modal sheet, gone in 4s — so no-card / card-couldn't-be-saved 422s, the "already assigned" 409, throttling and offline all looked like a dead Confirm button. `JobDetailModel.acceptError` renders beside Confirm (and in the schedule-conflict picker), with a "Use a different card" reset after a refused card. A failed billing fetch is now a failed state with Retry, not a made-up simulated context ("Test payments (simulated)" + an enabled Confirm on a real Stripe backend).
- [x] **A charge in flight can't be walked away from (2026-09-20).** Swiping/scrim/back on Pay & close (or back on the pay screen) cancelled the sheet's coroutine scope → the HTTP call → and `mutate` caught the CancellationException as a failure: "Could not close this job" about a card that may have been charged. `ZSheet(dismissable = !busy)` pins the accept, pay and cancel sheets; the pay screen swallows back while paying; `close` / `acceptQuote` / `cancel` / `PayInvoiceModel.pay` run `NonCancellable` so the answer always lands on the model; CancellationException is always rethrown; `close` and `acceptQuote` have their own re-entrancy guards.
- [x] **Unknown pay outcomes look before they speak (2026-09-20).** A timeout, a 5xx, an unreadable 2xx or the server's 503 `payment_provider_error` (`ApiError.unconfirmedPaymentMessage` — the server's message wins) reloads the job/invoice first: settled = success; otherwise the pay sheet/screen stays up with "couldn't confirm… safe to try again" (the server is idempotent) — never "nothing was charged". A non-payment 409 on close keeps the server's message. Paying from the invoice screen ends on a "Payment received" confirmation instead of silently popping back.
- [x] **No SetupIntent per screen load (2026-09-20).** `POST /api/billing/setup-intent` doubled as the saved-card read, so the home invoice hero, the pay screen, Pay & close and the accept sheet each minted a Stripe SetupIntent just to SHOW a card. Reads now use `GET /api/billing/card` (`ApiClient.billingCard()`, 404/405 → old POST for an older server); the POST runs only at the tap that opens a card form, fresh per attempt. `saved_card.expired` from the server is preferred over the local computation. iOS needs the same split.
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
- [x] **No membership surface at all — removed 2026-09-21.** The read-only `MembershipScreen` (2026-09-15) got the iOS build rejected AGAIN, under 3.1.3(b): an app may only show a plan bought elsewhere if it also sells it through In-App Purchase. So the apps no longer read, show, or mention the org's plan: no Membership row/screen/route, no PREMIUM tag on the business account header, `SubscriptionResponse` + both `subscription()` endpoints + `Organization.planBadge` deleted, the leads banner says "your leads" not "your plan's leads"; still no company-setup Plan row, no wizard plan step, no org billing endpoints (booker card endpoints stay — physical work). **Plan chips (booker-facing PRO/ELITE on other companies' quotes) were pulled too, same day, pre-emptively** — a label on a seller isn't the viewer's purchase (eBay/Etsy seller badges pass), but the chip text IS the product name on zivett.com/pros and a twice-burned reviewer may connect them. `CompanySummary.plan` still decodes; the render sites are commented `Plan chip pulled` (`JobPieces` company row, `JobDetailScreen`'s Your-pro card, and the `proLine` tier word in `CustomerHomeLogic` + `JobPresentation`, which now always reads "ZiVETT-verified"). The feed's PRIORITY badge stays: it names the ordering, not the Premium plan behind it. To stretch the boundary later, restore those sites once the app has an approval on record. Memberships live on the web account; the approval email says so. Don't reintroduce any of it without Play Billing/StoreKit. Payouts, Invoices + detail, More, Team, Account

## Business
- [x] Setup gate for org admins (profile → properties → team — the web's plan step is deliberately absent, store policy), skip-for-now dismiss
- [x] Overview, Requests, Properties (+ form sheet, request-job shortcut), Book (shared wizard with property/unit step), Account (profile, business profile form, team + edit member, notifications, invoices, warranties)

## Push, links, payments
- [x] FCM registration + messaging service + tap routing to the job over any tab; POST_NOTIFICATIONS prompt on shell appear
- [x] Backend: `App\Support\Push` gained an `fcm` driver (HTTP v1, service-account JWT, dead-token pruning) and the `/.well-known/assetlinks.json` route; tests added
- [x] `zivett://` scheme + App Links intent filters; referral and invitation links routed
- [x] **Connect onboarding returns to the app (2026-09-20).** The onboarding link is requested with `return_to: "app"`; `zivett://stripe-return` and the verified `https://zivett.com/app/stripe-return` open the company dashboard and trigger the payout-status refresh (cold start included — the old resume-only re-poll lived in screen state). `openUrl` no longer crashes when the device has no browser.
- [x] StripeBridge (PaymentSheet setup mode + `PaymentLauncher`), StripeHost in MainActivity
- [x] **Pay-time 3-D Secure recovery (2026-09-20).** The server charges off-session, so the 409's PaymentIntent is `requires_payment_method` and `handleNextActionForPayment` failed on it — every 3DS card was a dead end. Now re-confirmed on-session with the 409's `payment_method_id` through the Activity Result–based `PaymentLauncher` (MainActivity's deprecated `onActivityResult` route and the unused `zivett://stripe-redirect` return URL are gone). Outcomes are told apart: canceled / refused by the bank (their message, beside the card row) / unknown (poll, and never claim "nothing was charged"). iOS needs the same fix.

## Needs a manual step
- [x] **Firebase**: project `zivett-android`, app `com.zivett.app`, `google-services.json` in `app/` (gitignored) — done 2026-09-06; the emulator registers a token and receives pushes.
- [x] **Backend push env**: `FCM_PROJECT_ID` + `FCM_SERVICE_ACCOUNT` set locally (key at `storage/app/private/fcm-service-account.json`, gitignored) and verified end to end 2026-09-06, tap routing included. The same two values are set in Forge for production (confirmed 2026-09-06).
- [~] **App Links**: `ANDROID_APP_FINGERPRINTS` is set on production with the debug keystore's SHA-256 (2026-09-06) and `https://zivett.com/.well-known/assetlinks.json` serves it; `https://zivett.com/r/*` opens the app on the emulator. Still to do: append the Play App Signing certificate fingerprint once the Play listing exists (comma-separated, then `config:cache`).
- [!] **Device-only verification**: live location while en route, camera capture, and the Stripe 3DS challenge were only exercised as far as the emulator allows. Run each once on a physical phone. (Push delivery + tap routing are verified on the emulator, warm and cold.)
- [~] **Release signing**: done 2026-09-06 — upload keystore at `~/.android/zivett-upload.jks` (wired through `local.properties`, env-var fallback for CI), day-count `versionCode` (Play rejected yyyyMMdd on 2026-09-11), R8 on for release with line-number keep rules, backup rules made explicit (nothing leaves the device). The shrunk build was walked as customer (home, jobs, messages, account, invoices, pay sheet) and company-new (setup, subscription, PaymentSheet launch) against local Sail, and a production login round-trip. `bundleRelease` produces a signed AAB. Still to do: the Play Console entry itself (listing, data safety, content rating, internal-testing upload), then the App Links fingerprint above.
- [x] **Git**: initialised 2026-09-06, remote `github.com/agm1984/zivett-android`.

## Play Console checklist (2026-09-08)

Code is done: `bundleRelease` produces a signed AAB (upload key in
`~/.android/zivett-upload.jks`) with the R8 mapping embedded under
`BUNDLE-METADATA`, so Play retraces crashes without a separate upload.
What remains, in order. The listing copy, Data safety answers,
screenshot plan, and the icon/feature-graphic assets each step needs are
in `store/` (start at `store/README.md`).

1. [ ] **Developer account** registered and verified. A new *personal*
   account must run a closed test with at least 12 testers opted in for
   14 continuous days before it can publish to production; organization
   accounts are exempt. Start this first.
2. [x] **Create the app** (`com.zivett.app`) and upload the AAB to
   internal testing — done 2026-09-11, versionCode 2530 published to
   the internal track. Play App Signing enrolled on that upload.
3. [x] **App Links fingerprints** — done 2026-09-11: production's
   `assetlinks.json` now lists the debug, upload, and Play app-signing
   certs (all three written out in `store/README.md`). Verify on a
   device with `adb shell pm verify-app-links --re-verify com.zivett.app`.
4. [~] **Store listing**: copy in `store/LISTING.md`; icon and feature
   graphic in `store/`; ten 1080×1920 phone screenshots captured
   2026-09-11 in `~/Desktop/ZiVETT Play Store Screenshots/` (same
   numbering and seed data as the iOS set). Left: paste into the
   Console.
5. [ ] **App content**: Data safety (name, email, phone, addresses,
   photos, foreground location, push token, payments via Stripe,
   encrypted in transit, deletion documented at
   `https://zivett.com/support`), content rating, target audience 18+,
   no ads, App access credentials = the `review-*@zivett.com` demo
   accounts seeded by `AppReviewDemoSeeder`.
6. [ ] **Physical-phone pass** on the internal-testing build: live
   location while en route, camera capture, Stripe 3DS challenge. Read
   the pre-launch report.
7. [~] **Closed test** (personal account → 12 testers opted in for 14
   days before production access). Submitted 2026-09-11: Alpha track,
   release 2530, Canada only, testers = Google Group
   `zivett-android-beta@googlegroups.com` (anyone can join, so the
   website can link to it). Opt-in URL
   `https://play.google.com/apps/testing/com.zivett.app`. Waiting on
   Google's first review; then recruit testers, then apply for
   production access.

## Known divergences (intentional)
- Material 3 controls instead of iOS look-alikes (NavigationBar tabs, ModalBottomSheet, ExposedDropdownMenu, Snackbar pill toasts, system pickers). Screen content and copy match iOS.
- Maps are osmdroid on OSM tiles (no API key), matching the web's Leaflet tiles rather than Apple Maps.
- The debug-only `LocalHostRewrite` for server image URLs has no iOS counterpart because the simulator shares the Mac's `localhost`.

## Fixed on iOS while porting
- `PHPRates` property wrapper (2026-09-06): a brand-new company's `hourly_rates` / `category_rates` arrive as `[]` (PHP's empty associative array) and a rate can be `null`; plain `[String: Int]` refused both, which broke the company setup screen for `company-new@ratedpro.ca`. Same fix as Android's `PhpRatesSerializer`, with a decoding test.
