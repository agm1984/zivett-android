# Data safety form — answers derived from the code

Every row below was checked against the source, not assumed. The app ships
only two third-party SDKs that touch data: `firebase-messaging` (push) and
`stripe-android` (card capture). There is **no** Firebase Analytics, no
Crashlytics, and no ad SDK, so the whole "Analytics" and "Advertising"
purpose set is answered No.

## Section 1 — Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** — release `API_BASE_URL` is `https://zivett.com`; the only cleartext base URL is the debug build, which is never shipped |
| Do you provide a way for users to request that their data be deleted? | **Yes** — URL `https://zivett.com/support`. Also tick "Users can request that some or all of their data be deleted" and note the in-app path (Account → delete account, in `features/customer/account/AccountScreens.kt`) |

Play's definitions used below: **Collected** = leaves the device.
**Shared** = transferred to a third party. Transfers to a service provider
acting on your behalf (Stripe, FCM) and transfers to another user caused by
a user action (a message you send, a location you choose to broadcast) are
**not** "sharing" under Play's definition — so every row is Collected, and
nothing is marked Shared.

## Section 2 — Data types

| Category → Type | Collected | Required/Optional | Purpose | Where in the app |
|---|---|---|---|---|
| Personal info → **Name** | Yes | Required | App functionality, Account management | Signup, profile |
| Personal info → **Email address** | Yes | Required | App functionality, Account management | Signup, login, verification |
| Personal info → **Phone number** | Yes | Required | App functionality | Signup (`AuthPayloads.kt`), Pro/customer contact |
| Personal info → **Address** | Yes | Required | App functionality | Job service addresses, business properties |
| Personal info → **User IDs** | Yes | Required | App functionality, Account management | Sanctum-authenticated account id |
| Financial info → **Payment info** | Yes | Optional | App functionality | Stripe PaymentSheet in `core/payments/StripeBridge.kt`. Card data goes device → Stripe directly; ZiVETT never stores or sees a card number. Mark it collected because it leaves the device |
| Financial info → **Purchase history** | Yes | Required | App functionality | Jobs, invoices, receipts held server-side |
| Location → **Approximate location** | Yes | Optional | App functionality | Coarse permission is declared alongside fine |
| Location → **Precise location** | Yes | Optional | App functionality | Pro-only, foreground-only, and only after the Pro sets a job **En Route** (`CompanyJobDetail.kt`). Stops on dispose. **No background location permission is declared**, so Play's background-location declaration form does not apply |
| Photos and videos → **Photos** | Yes | Optional | App functionality | Job photos and attachments via the system picker (`core/media/PhotoImport.kt`). No `CAMERA` permission — capture goes through the system app |
| Messages → **Other in-app messages** | Yes | Optional | App functionality | Customer ↔ Pro threads |
| Device or other IDs → **Device or other IDs** | Yes | Optional | App functionality | FCM registration token, `POST /api/push/tokens`, released on logout (`core/push/PushRegistration.kt`) |

Answer **No** to everything else — in particular: no health/fitness, no
contacts, no calendar, no SMS/call log, no installed apps, no web browsing
history, no audio, no files/docs, no sensitive categories (race, religion,
political, sexual orientation), and no "App activity → App interactions"
because nothing analytics-shaped is recorded.

## Section 3 — Per-type follow-ups

For each type Play asks whether it is collected, shared, processed
ephemerally, and required. Use: **Collected = yes, Shared = no,
Processed ephemerally = no** (server-persisted), and Required/Optional per
the table. The only ephemeral answer that could be argued is precise
location — it is persisted to the job record so the customer can see the
trail, so answer "no".

## Cross-checks before you submit

- The privacy policy at `https://zivett.com/privacy` must actually name
  each collected type above, especially **live location shared with the
  customer** and **photos**. Play compares the form against the policy and
  a mismatch is a rejection.
- Data safety answers must match the iOS App Privacy answers. If they
  diverge, fix whichever is wrong rather than shipping both.
