# Play Store listing copy

Wording is taken from the marketing site and the iOS listing so all three
surfaces agree. Character limits are Play's; counts below are current.

## App name (30 max)

```
ZiVETT: Trusted Pros
```
20 characters. Keep the colon form — "ZiVETT — Trusted Pros" uses an em
dash that Play's search index treats inconsistently.

## Short description (80 max)

```
Book verified local Pros, follow the job in real time, and pay securely.
```
72 characters.

## Full description (4000 max)

```
ZiVETT connects homeowners and property managers with local trade Pros who
have been verified against the ZiVETT 5-Point Trust Standard: identity,
credentials, quality of work, reliability, and customer approval. Every
review in the app comes from a job actually completed through ZiVETT.

FOR HOMEOWNERS
Pick a trade. Compare trusted Pros. Book with confidence.
• Describe the work in a few guided steps and send it to Pros who serve
  your area
• Compare quotes side by side, with each Pro's trust profile, trade
  credentials, and reviews from completed ZiVETT jobs
• Watch your Pro's live location on the map when they are en route
• Message your Pro in the app, with photos, from first quote to final
  invoice
• Pay by card in the app. Card details are handled by Stripe and never
  touch ZiVETT's servers
• Keep every job, quote, invoice, and receipt in one history

FOR PROS
Get discovered. Win more jobs. Get paid securely.
• See opportunities near you and quote the ones you want
• Build a trust passport — verified identity, licences, insurance, and
  your rating from completed jobs
• Run the day from your calendar: accept, schedule, mark en route, and
  close jobs from the phone
• Share your live location with the customer while you drive, so nobody
  is waiting by the window
• Invoice on completion and get paid out to your bank through Stripe
• No commission until you are paid

FOR PROPERTY MANAGERS
• Keep your properties, units, and contacts in one place
• Raise requests against a specific property and route them to trusted
  Pros
• Add your team, control who can approve spend, and keep the paper trail

BUILT ON TRUST
The 5-Point Trust Standard is the reason ZiVETT exists. Pros are checked
for who they say they are, the credentials their trade requires, the
quality of their work, whether they turn up when they said they would,
and what customers said afterwards. Ratings come only from jobs booked
and completed in the app.

WHAT YOU NEED
A free ZiVETT account. ZiVETT is available in Canada. Payments are
processed by Stripe. Location is used only while a Pro is en route to a
job, and only when that Pro turns it on. Notifications keep you posted on
quotes, messages, arrivals, and invoices.

Questions or account deletion: https://zivett.com/support
Privacy policy: https://zivett.com/privacy
```
About 2,300 characters — room to grow if you want more detail later.

**Never mention memberships, plans, tiers, or a plan fee anywhere in the
listing.** The app sells nothing and shows no plan prices (Play's payments
policy treats a Pro/Premium plan as a digital service — it's store billing
or nothing, and the listing must not point at the website to buy one
either). The "plan fee" bullet was removed 2026-09-15 for exactly this.

## Store settings

| Field | Value |
|---|---|
| App or game | App |
| Category | Business (alternative: House & Home — Business matches the Pro/property-manager side better) |
| Tags | Home services, Local services, Business management |
| Contact email | the address you want public — `support@zivett.com`, not a personal inbox |
| Contact website | `https://zivett.com` |
| Privacy policy | `https://zivett.com/privacy` |
| Countries | Canada only (same call as iOS) |
| Ads | No ads |
| In-app purchases | No — Play Billing is not used and the app sells nothing. The only in-app card entry pays a tradesperson for physical work done at the customer's home (Stripe is permitted for that). Pro/business memberships are purchased and managed on the ZiVETT website only; the app just reflects the account's current membership (read-only "Membership" row) |
| Content rating | Complete the questionnaire; expect Everyone / PEGI 3. Answer "yes" to user-to-user communication (messaging) and to sharing location |
| Target audience | 18+ |

## App access (reviewer credentials)

The app is login-only, so Play needs credentials or review is rejected.
Under **App content → App access**, add "All functionality requires
login" with these production accounts (seeded by `AppReviewDemoSeeder`,
same set Apple got — see the `app-store-review-status` note):

| Email | Role it demonstrates |
|---|---|
| `review-customer@zivett.com` | homeowner: booking, quotes, messaging, pay sheet |
| `review-company@zivett.com` | Pro: opportunities, calendar, en-route, invoicing |
| `review-business@zivett.com` | property manager: properties, team, requests |

Password: the value of `APP_REVIEW_DEMO_PASSWORD` on production. Put it in
the App access notes, plus these lines (the same text goes in App Store
Connect's App Review Information for iOS):

> Payments run in Stripe test mode; use card 4242 4242 4242 4242 with any
> future expiry and any CVC.
>
> The app contains no in-app purchases and sells no digital content or
> subscriptions. The only card entry in the app is on the BOOKER accounts
> (customer, business) and pays a tradesperson for physical work performed
> at the home or property, after the job is done. The Pro (company) account
> never enters a card — payouts run through Stripe Connect. Pro and
> property-manager memberships are purchased and managed on the ZiVETT
> website only; the app does not sell, price, or link to them — the
> "Membership" screen (company: More tab; business: Account tab) is a
> read-only reflection of the account.
