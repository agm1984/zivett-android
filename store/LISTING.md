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
• A plan fee plus a commission on paid jobs. No commission until you are
  paid

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
About 2,350 characters — room to grow if you want more detail later.

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
| In-app purchases | No — Play billing is not used. Card payments are for physical trade services performed off-app, which is why Stripe is permitted here rather than Google Play Billing |
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
the App access notes, plus one line: "Payments run in Stripe test mode;
use card 4242 4242 4242 4242 with any future expiry and any CVC."
