# fam-sub-be

Backend for **FamSub** — a shared-subscription cost splitter for a private
friend group. It answers one question accurately: *given a set of shared
subscriptions, who actually owes what?*

Netflix, Spotify and friends get billed to one person's card. FamSub records
each real bill, splits it across whoever was on that plan that month, rolls the
splits up into invoices, emails them, and tracks payment — with a full audit
trail and an undo for every bulk operation.

Live at [famsub.almonium.com](https://famsub.almonium.com) (API:
`api.famsub.almonium.com/v1`). Sign-in is Google-only and closed: an email must
already be registered as a subscriber or hold a pending admin invite.

## Repositories

| Repo | What it is |
| --- | --- |
| `fam-sub-be` (this one) | Kotlin / Spring Boot API and Postgres schema |
| [`fam-sub-fe`](https://github.com/okuzan/fam-sub-fe) | React + Vite admin console and subscriber cabinet |
| [`fam-sub`](https://github.com/okuzan/fam-sub) | the original Java CSV prototype (`fam-sub-cli`), kept for its historical data |
| `almonium-infra` (private) | Ansible deployment, Traefik, TLS, secrets |

## The model

```
SubscriptionService   Netflix, 260₴/mo
        │
        ├── Membership     Anna,  2024-03 → (open)
        ├── Membership     Bohdan, 2024-03 → 2025-01
        │
Charge                one real bill: Netflix, 2025-06, 268.40₴
        │
        │  CostCalculationService splits it evenly across that
        │  month's active members, in integer cents, remainder
        │  to the first participants — the parts always sum
        ▼
LedgerEntry × N       Anna 89.47₴, Bohdan 89.47₴, Denys 89.46₴
        │
        │  InvoiceService groups every uninvoiced entry per
        │  subscriber over a month range
        ▼
Invoice               Anna, 2025-04 → 2025-06, 412.90₴
                      DRAFT → SENT → PAID   (or VOID)
```

Two things sit outside that chain. **Manual invoices** cover one-off charges
that never came from a subscription. **Subscriber balance** is prepaid credit:
top it up, then settle invoices against it with *pay from balance*.

Every bulk run — cost calculation and invoice generation — is recorded as a
batch (`CostCalculationBatch`, `InvoiceGenerationRun`) and can be previewed and
undone from the Recovery endpoints. Every admin mutation writes an
`admin_action` row.

## Stack

Kotlin 2.3 · Spring Boot 4.1 (snapshot) · Java 21 · Gradle · PostgreSQL ·
Flyway · Spring Security with Google OIDC · Thymeleaf for emails · ZeptoMail ·
iText for invoice PDFs.

## Running locally

You need JDK 21 and a Postgres database.

1. Create `.env` in the repository root (it is gitignored):

   ```properties
   DB_HOST=127.0.0.1
   DB_PORT=5432
   DB_NAME=fam_sub
   DB_USERNAME=fam_sub_user
   DB_PASSWORD=…

   LOCAL_PORT=8888
   CONTEXT_PATH=/v1
   SPRING_PROFILE=dev

   ZEPTO_API_KEY=…
   GOOGLE_CLIENT_ID=…
   GOOGLE_CLIENT_SECRET=…
   ```

2. `./gradlew bootRun`

The API is then on `http://localhost:8888/v1`. Run `fam-sub-fe` with `npm run
dev` on port 9000 to get the UI; its `.env.development` already points there.

The `dev` profile sets `app.email.dry-run: true`, so no mail is sent — rendered
emails are written to `temp/rendered_email.html` instead. Add your callback URL
`http://localhost:8888/v1/login/oauth2/code/google` to the Google OAuth client
before signing in locally.

`./gradlew build` runs the tests, but `FamSubBeApplicationTests` starts a full
Spring context and needs the database up.

## API surface

Everything under `/admin/**` requires the `ADMIN` role, `/subscriber/**`
requires `SUBSCRIBER`, and authorization is enforced by URL pattern in
`SecurityConfig`.

| Area | Path |
| --- | --- |
| Services, subscribers, memberships, charges | `/admin/subscription-services`, `/admin/subscribers`, `/admin/memberships`, `/admin/charges` |
| Cost calculation and ledger | `/admin/cost-calculations`, `/admin/ledger` |
| Invoices (generate, email, void, PDF, pay) | `/admin/invoices` |
| Finance summary, audit log, undo | `/admin/finance/summary`, `/admin/actions`, `/admin/recovery` |
| Accounts and admin invites | `/admin/accounts`, `/admin/invites`, `/admin-invites/accept` |
| Subscriber self-service | `/subscriber/cabinet`, `/subscriber/invoices` |
| Session | `/auth/google`, `/auth/logout`, `/public/auth/me` |

A weekly job (Saturdays 10:00 Europe/Kyiv) emails every subscriber with an
outstanding balance a summary of what they owe.

## Deployment

Push to `main` → GitHub Actions builds an `arm64` image to
`ghcr.io/okuzan/fam-sub-be` → the `almonium-infra` Ansible playbook does a
blue/green slot switch on the shared Almonium host behind Traefik.

To roll back, re-run the workflow with `workflow_dispatch` and pass the
`image_tag` of a known-good build; the build job is skipped and only the deploy
runs.

FamSub shares the host, domain, Google OAuth client, and ZeptoMail sender with
[Almonium](https://almonium.com), but has its own database, containers, and
secrets. See `AGENTS.md` for the boundary.
