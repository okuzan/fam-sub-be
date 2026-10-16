# Repository agent guidance

This is the Spring Boot backend of **FamSub**, a shared-subscription cost
splitter for a private friend group. It is one of several repositories that
make up the product; none of them is nested inside the others and there is no
umbrella repository, so read this file before assuming the backend is the whole
system.

## Workspace layout

All sibling repositories live under `/media/ok/Space/Code/mine`:

- `../fam-sub-fe` — the **React 19 + Vite + TypeScript admin/subscriber web
  client**, and the only consumer of this API. Every endpoint added, renamed, or
  removed here needs a matching change in `src/config/api.ts` and the relevant
  component and `src/types/*.ts` file there. Response DTO field names are the
  contract: they are consumed untransformed by the client. Coordinate contract
  changes as two commits, one per repository.
- `../fam-sub-cli` — the **original Java/Maven CSV prototype** (`fam.sub.*`)
  that this service replaced. It still holds the historical seed data under
  `src/main/resources/data/*.csv` and the original `pinned.md` template. Treat
  it as read-only history; do not add features there.
- `../../almonium/almonium-infra` — owns **deployment, DNS, TLS, and secrets**
  for this service. Read its `AGENTS.md` before touching anything about how
  FamSub is deployed or configured in production.
- `../../almonium/almonium-be`, `almonium-fe`, `almonium-mobile` — a separate
  product that shares infrastructure with FamSub (see below). FamSub does not
  depend on Almonium code, and Almonium must not depend on FamSub.

## What is shared with Almonium, and what is not

FamSub is a side project hosted on the Almonium platform's infrastructure. The
following are genuinely shared and must not be duplicated or diverged:

- the Oracle Cloud ARM host, its Traefik reverse proxy, and the
  `almonium.com` domain (`famsub.almonium.com`, `api.famsub.almonium.com`);
- the ZeptoMail account and the `noreply@almonium.com` sender identity;
- the Google OAuth client (`28756406725-…apps.googleusercontent.com`), whose
  redirect URIs must list this API's `/v1/login/oauth2/code/google` callbacks;
- the `almonium-infra` Ansible repository and its vault workflow.

Everything else is separate: FamSub has its own Postgres database and user
(`fam_sub` / `fam_sub_user`), its own containers, its own vault file
(`ansible/vars/apps/fam_sub/`), and its own GitHub Actions. Do not read or write
Almonium tables, and do not put FamSub secrets in Almonium vault files.

## Deployment

Pushing to `main` runs `.github/workflows/build-and-deploy.yaml`: Gradle
`bootJar -x test`, a `linux/arm64` image pushed to
`ghcr.io/okuzan/fam-sub-be:<sha>`, then a checkout of `almonium-infra` and
`ansible/playbook-deploy-fam-sub-be.yaml`. The playbook does a blue/green slot
switch (health ports 8881/8882, slot recorded in `.next_color_prod_fam_sub` on
the host) and only supports `prod`. A `workflow_dispatch` with `image_tag`
redeploys an existing image without rebuilding — that is the rollback path.

Deployment configuration is not in this repository. Container environment,
ports, hostnames, and the frontend's nginx SPA config
(`nginx-configs/spa.conf`) live in `almonium-infra`. A change to a Spring
property that must be settable per environment needs a coordinated commit
there; never invent an infra-only variable that this application does not read.

## Architecture

Package-by-feature under `com.almonium.famsubbe`. The money model is a chain,
and each link is append-only in spirit:

`SubscriptionService` (a service and its price) → `Membership` (who is on it,
`[startMonth, endMonth]`, half-open at the end when `endMonth` is null) →
`Charge` (one real bill for one service in one month, unique per
service+month) → `CostCalculationService` splits each charge evenly across
that month's active members into `LedgerEntry` rows (unique per
charge+subscriber, grouped by a `CostCalculationBatch`) → `InvoiceService`
groups uninvoiced ledger entries per subscriber into an `Invoice`
(DRAFT → SENT → PAID, or VOID).

Around that chain: `InvoiceDeliveryService` sends every invoice email, after
the invoice has committed and in its own short transaction per invoice, and
only then moves it to SENT. `SubscriberStandingService` is the one place that
computes what a subscriber owes (unpaid invoices minus balance), for the admin
view, the cabinet, and every email. `SituationEmailService` sends the
on-demand and Saturday "where you stand" emails. `BillingTime` owns the
Europe/Kyiv zone: use `BillingTime.currentMonth()`, never `YearMonth.now()`.

Incoming money is read from the bank, not entered by hand: `BankSyncService`
polls the Monobank personal statement (`app.bank.mono`, token in
`MONO_API_TOKEN`) every five minutes in prod. Polling rather than Mono's
webhook is deliberate, because the webhook cannot send `X-Requested-With`.
Each credit from another person becomes a `BankTransaction`, unique per bank
operation. `BankPaymentService` applies it on its own when its purpose carries
a subscriber's `PaymentCode` (`FamSub FS-XXXXXX`). Failing that, it matches by
who sent it: first the sender name holding exactly one subscriber's `name` and
`surname` (any order and case; no surname, no match), then a remembered sender
(`BankSender`) as the fallback for people paying under someone else's name.
Either sender match applies only when the amount is within what the
subscriber owes plus `auto-apply-tolerance`. Anything else waits on the admin bank payments
page. Every email links to the public pay page `/pay/<Subscriber.payToken>`
(`/public/pay/{token}` here). The page prefills that purpose through an NBU QR
code (`NbuQrCode`), which needs the recipient's РНОКПП in
`PAYMENT_RECIPIENT_TAX_ID`. Payments go only to the Mono account in
`app.payment`, because that is the only account that is read. Applying goes through `InvoiceService.applyIncomingPayment`: credit the
balance, then pay invoices from it.

Two write paths bypass the ledger: manual/duplicated invoices
(`InvoiceOrigin.MANUAL`) and `Subscriber.balance`, a prepaid credit that
`payFromBalance` draws down. The amount drawn is recorded in
`Invoice.balancePaidAmount`, and moving that invoice out of PAID refunds it. Undo is handled by `RecoveryService`, which
deletes a whole cost run or invoice run and stamps the batch/run as undone;
that is why `CostCalculationBatch` and `InvoiceGenerationRun` exist.

Conventions worth preserving:

- JPA entities are mutable classes with nullable `var`s and no-arg
  construction; callers use `requireNotNull(...)` at the mapping boundary.
  Do not convert them to data classes.
- Request/response DTOs are `data class`es in `<Feature>Dto.kt` or a
  dedicated file; mapping extensions are private in the service that owns them.
- Admin endpoints live under `/admin/**` (`hasRole(ADMIN)`), the self-service
  cabinet under `/subscriber/**` (`hasRole(SUBSCRIBER)`), and anything
  unauthenticated under `/public/**` or `/auth/**`. Authorization is enforced
  by URL pattern in `SecurityConfig`, not by annotations — a new top-level path
  segment is unprotected until it is added there.
- **Every admin mutation writes an audit record** via
  `AdminAuditLogService.log(...)` from the controller, with the acting account
  resolved by `AuthenticationUtil.resolveAccountId`. Follow this for any new
  mutating admin endpoint.
- Money is `BigDecimal` with scale 2 and the currency is hardcoded UAH (`₴`).
  Even splits go through `CostCalculationService.splitEvenly`, which works in
  integer cents and distributes the remainder to the first participants, so
  the parts always sum to the total. Do not reintroduce floating point or
  naive division.
- `YearMonth` is persisted as `VARCHAR(7)` via
  `converter/YearMonthConverter.kt`.

## Authentication

Google OIDC only, with a server-side session cookie (`JSESSIONID`) and CORS
credentials allowed for `app.web-domain`. CSRF protection is
`RequestedWithHeaderFilter`, not Spring's token: every non-GET request must
carry `X-Requested-With`, and the client sends it through `apiFetch`. Never
exempt an endpoint from it. A release that needs both sides deploys the
frontend first. `AccountOidcUserService` refuses to
create an account unless the email already matches a `Subscriber` row or a
pending `AdminInvite` — signup is closed by design. Admin rights are granted
only by accepting an emailed invite token (SHA-256 hashed at rest, 7-day
expiry). Password accounts do not exist: `Account.password` holds a random UUID
for Google-created accounts and nothing authenticates against it.

## Building and testing

`./gradlew build`. Note that `FamSubBeApplicationTests` is a `@SpringBootTest`
that needs a live Postgres reachable via `.env`, so a local build requires the
dev database to be up. To run that test without the dev
database, point it at a throwaway container, e.g. `docker run --rm -p
127.0.0.1:55432:5432 -e POSTGRES_USER=t -e POSTGRES_PASSWORD=t -e
POSTGRES_DB=fam_sub postgres:16` with `DB_HOST=127.0.0.1 DB_PORT=55432
DB_USERNAME=t DB_PASSWORD=t`. Environment variables override `.env`, and Flyway
builds the schema from V0. CI deliberately runs `bootJar -x test` and therefore
never executes tests — if you add a test, it only ever runs locally, so prefer
plain JUnit + Mockito unit tests (as in `invoice/`, `accounting/`, `finance/`)
over Spring-context tests.

Configuration comes from `.env` (gitignored, never commit it) imported through
`spring.config.import`. `application-dev.yaml` sets `app.email.dry-run: true`,
which makes `DefaultInvoiceEmailService` write the rendered HTML to
`temp/rendered_email.html` instead of calling ZeptoMail. Keep it that way for
local work; a dry-run send still returns `true`, and callers deliberately skip
status transitions when `dry-run` is on.

## Known sharp edges

Do not "fix" these silently in an unrelated change; they are load-bearing or
deliberate, or they need their own commit and a decision from the owner.

- `ddl-auto` is `validate`: migrations are the only thing that may change the
  schema, and the application refuses to start if the entities disagree with it.
  `V0__Baseline_schema.sql` captures the tables Hibernate had created implicitly
  before migrations existed. It is guarded so it is a no-op on databases that
  already ran `V1`–`V10`, and it applies out of order there
  (`spring.flyway.out-of-order: true`). Once every environment has V0 recorded,
  that flag can go.
- `Subscriber.balance` is mutated by whole-object `PUT /admin/subscribers/{id}`
  and there is no balance transaction table; the only history is
  `admin_action.metadata_json`. Treat a balance edit as lossy.
- `Subscriber.billingExempt` covers family and dependants: they take part in
  cost splits, so everyone else's share stays correct, but their invoices are
  created already PAID and their balance is never touched. Enabling it also
  settles every invoice they currently owe. It is a waiver, not a payment
  method.
- An invoice can reach VOID only through `voidInvoice`, including when the
  status endpoint is asked for VOID, and it can never leave VOID. Voiding a
  ledger invoice releases its ledger entries back to the uninvoiced
  pool so the cost can be regenerated. That is the only way to redo part of an
  invoice run after it has gone out — `RecoveryService.undoInvoiceRun` requires
  every invoice in the run to still be DRAFT.

## Finishing work

After completing a requested implementation and its verification, stage only the
files that belong to that task and create a focused commit. Split unrelated
concerns into separate commits rather than one sweep; where a single file
genuinely carries two concerns, say so in the message instead of pretending the
commit is narrower than it is. Do not include unrelated working-tree changes,
and do not amend an existing commit unless asked. Report the commit hashes in
the handoff.

Never end a task with the work sitting uncommitted. The only exceptions are when
the user explicitly asks for that, or when the work is blocked or waiting on a
material decision from them — and in those cases say plainly that the changes
are uncommitted and why.

Committing is not deploying. This repository deploys on push to `main`, so
pushing is a separate, explicit step the user asks for.
