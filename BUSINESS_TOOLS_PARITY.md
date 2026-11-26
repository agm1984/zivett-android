# Business tools + November parity: the overnight build (started 2026-11-25)

The user asked (2026-11-25, going to bed): bring both apps to parity with the
web for Pro companies, business users and homeowners, including Business
tools. No questions possible: take the most obvious answer to every small
issue and write it down under "Decisions" below.

This file is the TRACKER. An identical copy lives in
`zivett-android/BUSINESS_TOOLS_PARITY.md`; each app ticks its own column.
Branch in both repos: `business-tools-parity` (off main). Commit atomically,
never merge. Web source of truth: `../ratedpro-web` (UAT branch state),
plan doc `docs/plans/business-tools.md`, nav `resources/js/lib/nav.js`,
pages `resources/js/pages/company/business/*.vue`, stores
`resources/js/stores/companyPro*.js` / `companyCustomers.js` /
`companyBusinessTools.js` / `companyTimesheets.js` / `companyPayroll.js`,
routes `routes/api.php` (`/api/company/business/*`, all bearer-ready).

Status: [ ] todo · [~] in progress · [x] done · [-] deliberately not ported

## Menu decision (both apps)

The web shows ONE side at a time ("Marketplace" | "Your business",
`lib/companySpace.js`). The apps mirror it: a two-way switch at the top of
More swaps the whole tab bar, remembered on the device.
- Marketplace tabs (unchanged): Dashboard (or My day for field techs) ·
  Opportunities · Quotes · Jobs · More.
- Business tabs: Overview · Customers · Jobs (own) · Invoices · More.
  More (business side) lists: Requests, Quotes (own), My day, My hours,
  Calendar, Expenses, Time on jobs / Payroll, Reports, Campaigns (org
  admins), Team (org admins), Business tools settings, Business profile,
  Plan, account rows.
- Tools-only company (not submitted, `setup_completed_at` set, tools on):
  business tabs only, plus "Find work on ZiVETT" in More opening the
  marketplace setup.
- Submitted-not-approved + tools: Application · Overview · Customers ·
  Jobs · More (tools rows in More).
- Sections follow `permissions` (`business_tools`, `payroll` flag);
  a member without `business_tools` gets no business side.

## Slices

| # | Slice | iOS | Android |
|---|---|---|---|
| S0 | Shell: space switch, tools-only + submitted lifecycles, permissions gating, Business tools switch card on the Plan screen (`GET/PATCH /api/company/business-tools`) | [ ] | [x] |
| S1 | Overview (`GET /business`) + Requests (list, added, dismiss, request-form link card) | [ ] | [x] |
| S2 | Customers: list/search, detail (addresses, history, campaigns card), add/edit, archive/delete, marketing consent, ZiVETT-customer block + exception request. Import/export web-only | [ ] | [x] |
| S3 | Own quotes: list, detail, editor (line items + presets), send, answer, share link, duplicate, delete, PDF (share sheet), email | [ ] | [x] |
| S4 | Own jobs: list, editor (incl. repeating series), detail for admins (status, crew, record: notes/photos/time/costs/changes, money card, series stop/crew). Extend existing OwnJobView | [ ] | [x] |
| S5 | Own invoices: list, editor, detail (issue, void, record/edit/delete payment, receipt email, PDF, email), Bill visits | [ ] | [x] |
| S6 | Books: Expenses (receipt photo), Time on jobs, Payroll (when on: period sheet, approve/reopen, shift edits), Reports | [ ] | [x] |
| S7 | Campaigns (org admins): list, editor, audience filters + count, preview, test, send, schedule/unschedule, results | [ ] | [x] |
| S8 | Settings: business tools settings (invoicing/numbering, payment instructions, email branding, reminders), line presets | [ ] | [x] |
| G1 | Booker job page: `free_cancel_until` line ("Free to cancel until …") | [ ] | [ ] |
| G2 | Emergency fee itemized in accept/coupon preview; pro quote screen says it's added on top; business setup says "verified pros" | [ ] | [ ] |
| G3 | Team: resend invitation, send password reset email, remove from team; crew picker shows inactive leavers ("Tap to take them off") | [ ] | [ ] |
| G4 | Business: Messages inbox (`/business/conversations`); request from a property card skips the property step | [ ] | [ ] |
| G5 | Company org live channel (`private-organization.{id}`, `organization.changed`) refetches own-work screens | [ ] | [ ] |
| G6 | Plan cards: `registration_fee_waived` + `_label` ("waived for a limited time"), Founding Pro badge | [ ] | [ ] |
| G7 | Company Jobs tab badge (`jobs` nav badge) + "New message" tags (`unread_job_ids`) | [ ] | [ ] |
| G8 | Homeowner marketing opt-in: unticked box at signup + booking Review, "Offers and news" switch in notification settings | [ ] | [ ] |
| G9 | Restricted members: hide tabs/rows whose section `permissions` lacks (CLAUDE.md: "the apps don't hide tabs yet") | [ ] | [ ] |

Each agent VERIFIES a G item first (it may already be done) and marks it.

## Decisions (append as you go)

- 2026-11-25: imports and CSV/accountant exports stay web-only (files on a
  phone are awkward); PDFs open in the share sheet.

- 2026-11-25 (Android): switching sides rebuilds the tab bar and lands on
  its first tab (Overview / Dashboard), the way the web's switch goes home.
- 2026-11-25 (Android): limit and reminder upgrade prompts name the plan
  ("The Pro plan holds more customers") and open the Plan screen; the app
  never shows the web's price (store prices differ, no web purchase talk).
- 2026-11-25 (Android): Requests is a row in More on the business side
  (the bar holds Overview · Customers · Jobs · Invoices · More).
- 2026-11-25 (Android): the Overview's figures, not a header menu, open
  the filtered lists; New quote / job / invoice / Add customer are buttons
  under the title.

- 2026-11-25 (Android): "Print" on quotes/invoices is the PDF in the share
  sheet (it lists printers); no separate print action.

- 2026-11-25 (Android): an own job opened from My day, the calendar or a
  push shows the office's full page to anyone holding `business_tools`,
  and the crew's route sheet to everyone else (the web's two pages).

- 2026-11-25 (Android): Payroll's pay settings (presets, week start, pay
  rates) stay on the web, like exports; the app shows the sheet and its
  approve / reopen / shift edits, and says "Set rates on the web" when a
  person with hours has no rate.
- 2026-11-25 (Android): Reports' monthly chart is one navy bar per month
  (the web's one-hue rule), invoiced only.

- 2026-11-25 (Android): the campaign preview renders the server's HTML in a
  WebView with JavaScript off.

- 2026-11-25 (Android): payment instructions are edited as free text
  (`payment_instructions`, which the server accepts from older clients);
  the web's structured payment-method fields stay on the web. Saving from
  the app clears the stored methods, as the server does for free text.
- 2026-11-25 (Android): reminders stay on the Overview (as on the web);
  Settings points there.

## Server asks

(none yet)

## Log (append: date, slice, app, commit range, what's untested)

- 2026-11-25 S0-S2 Android cc54675..3158598: shell + space switch + Plan
  card, Overview + Requests, Customers (list, detail, form, addresses,
  consent, exception request). Unit-tested decoding and rules; nothing run
  on a device yet. The Overview's New quote / job / invoice buttons and
  the customer page's quote / job / invoice links land with S3-S5.
- 2026-11-25 S3 Android 7d28cbb..d79b665: own quotes list, detail (status
  actions, email sheet, approval link, deposit, duplicate, PDF share, delete
  draft) and editor (customer/address, lines + presets, terms, taxes). Not
  run on a device.
- 2026-11-25 S4 Android: own jobs tab, office job page (status, crew
  picker, changes, notes, photos, time, costs, money, series stop / crew
  to all visits, invoices) and editor (incl. repeating). Not run on a device;
  photo upload untested against a real server.
- 2026-11-25 S5 Android: own invoices tab, editor (incl. from quote / job
  seeding a server draft, like the web), detail (issue, email, payments with
  receipts, change / remove with reason, void + corrected copy, PDF, delete
  draft) and Bill completed visits. Not run on a device.
- 2026-11-25 S6 Android: Expenses (receipt photo upload), Time on jobs,
  Payroll (approve/reopen, add/edit/delete shift), Reports. Not run on a
  device; receipt multipart upload untested against a real server.
- 2026-11-25 S7 Android: campaigns list + editor (templates, audience
  filters with live count, preview, test, send / schedule / unschedule,
  results, delete draft). Not run on a device.
- 2026-11-25 S8 Android: settings (payment text, numbering, GST, email
  colour, presets list/delete). Not run on a device.
