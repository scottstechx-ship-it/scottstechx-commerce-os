# ScottsTechX Commerce OS — Backend (Uganda MVP)

Trust-gated marketplace backend. Fastify + Postgres 15 + Row-Level Security.
Runnable slice with buyer checkout, driver POD, seller inventory CRUD,
location-ranked nearby sellers, AI assistant endpoints, and Google OAuth
login. Migrations are byte-for-byte portable to a real Supabase project.

## What works (verified)

- `POST /api/v1/orders/checkout` — buyer-initiated order with server-computed
  totals (BIGINT minor units), idempotent replay, mixed-stock rejection,
  multi-currency FX rate snapshot.
- `POST /api/v1/logistics/pod` — driver-submitted proof of delivery with
  state-machine guard, ownership check, idempotent replay, GPS columns.
- `GET /api/v1/sellers/nearby` — location-ranked seller list using Haversine
  distance + composite rank score (distance 0.30, trust 0.35, rating 0.20,
  activity 0.15). All weights live in `src/modules/sellers/nearby.service.ts`.
- `GET /api/v1/sellers/:sellerId` — full seller profile with products and
  recent reviews.
- `GET/POST/PATCH/DELETE /api/v1/seller/inventory` — seller inventory CRUD
  with Idempotency-Key on every mutation.
- `GET /api/v1/seller/stats`, `GET /api/v1/seller/orders`, `GET/PATCH
  /api/v1/seller/profile` — seller dashboard.
- `POST /api/v1/reviews` — buyer review submission; aggregates recomputed.
- `GET/POST /api/v1/chat/messages` — buyer/seller/AI chat with RLS-scoped
  read access (sender or recipient only).
- `POST /api/v1/auth/google` — Google OAuth id_token exchange for our JWT.
  Real Google JWKS via `jose` `createRemoteJWKSet`. Returns 503
  `google_auth_disabled` when `GOOGLE_CLIENT_ID` is unset.
- `POST /api/v1/ai/seller-suggest`, `POST /api/v1/ai/customer-chat`,
  `POST /api/v1/ai/reason` — AI assistant endpoints (OpenAI / Anthropic /
  Gemini). Provider chosen by `AI_PROVIDER` env. Returns 503 `ai_disabled`
  when `LLM_API_KEY` is unset. Every call is audit-logged.
- Row-Level Security on every table. `current_setting('app.user_id')` is
  populated from the verified JWT at the start of every transaction.
- Idempotency table with replay-returns-same-body semantics and
  request-hash mismatch returning 409.
- Append-only audit log with trigger-blocked UPDATE/DELETE.
- HS256 JWTs with pinned algorithm + issuer + audience; alg-confusion attacks
  are rejected by `jose`.
- In-process rate limiter (60/min for AI, 600/min for everything else).
- Security headers: `X-Content-Type-Options`, `Referrer-Policy`,
  `X-Frame-Options`, `Cache-Control: no-store`, `Strict-Transport-Security`
  in production.
- Allow-list CORS via `ALLOWED_ORIGINS` env.

## What does NOT work (honest list)

These are real stubs. They throw `NotImplementedError` (HTTP 501) at the
call site with an `X-Stub-Reason` header. The HTTP request shape for the
real implementation is documented in the source.

- **MTN MoMo / Airtel Money collection.** `src/modules/payments/momo.ts` —
  the file header has the exact MTN MoMo Collection request body and
  headers documented. A real implementer wires fetch + sandbox creds in.
- **KYC, payouts, refunds, dispute flows.** Schema columns are not present
  for these yet; would be a follow-up migration.
- **S3 / object storage.** Not used in this slice. The PoD GPS columns live
  in the order row; photo upload is a follow-up.
- **Email / SMS / push.** No provider is integrated.
- **The "AI" or "agentic" anything in the marketing spec.** This slice
  contains the 5-layer trust *math* (`src/modules/trust/trust-score.ts`)
  but no LLM, no embeddings, no agent runtime. The trust score is computed
  from a static input shape; it does not fetch buyer trust scores to weight
  the reputation component. That is a follow-up.

## How to run

### Prerequisites

- Node >= 22
- No Docker, no system Postgres required. The test suite boots its own
  embedded Postgres (`embedded-postgres` npm package) on port 54329.
  In production, run against a real Postgres 15+ (Supabase is fine — the
  migrations use only Supabase-compatible SQL).

### Install

```bash
cd E:\ScottsTechX life projects\ScottsTechX\12_Backend
npm install
cp .env.example .env
```

### Tests

```bash
npm test
```

Expected: all 113 tests pass across 8 files. First run downloads a
~50 MB Postgres binary into `node_modules/embedded-postgres-binaries/`
and may take 1-2 minutes.

### Start the server

```bash
npm run dev
```

Server listens on `http://127.0.0.1:3001`. The dev mode auto-applies
migrations to whatever `DATABASE_URL` points at.

### Smoke test

```bash
# Mint a token (see "Demo users" below for credentials).
# Then:
curl -X POST http://127.0.0.1:3001/api/v1/orders/checkout \
  -H "content-type: application/json" \
  -H "authorization: Bearer <jwt>" \
  -H "idempotency-key: smoke-001" \
  -d '{"items":[{"product_id":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1","qty":1}],"delivery_address":{"line1":"Plot 14","city":"Kampala","country":"UG"}}'
```

## Demo users (seeded by 0003_seed.sql)

| Role     | UUID                                   | Notes                 |
|----------|----------------------------------------|-----------------------|
| buyer    | 11111111-1111-1111-1111-111111111111   | demo1234 (bcrypt stub)|
| seller   | 22222222-2222-2222-2222-222222222222   | owns 3 products       |
| driver   | 33333333-3333-3333-3333-333333333333   | one assigned driver   |

Passwords are bcrypt-hashed but the hash is a stub in the seed; the MVP
does not include the login endpoint (a real Auth0/Supabase Auth integration
is a follow-up). For the existing endpoints, mint a token with the helper:

```ts
import { signToken } from "./src/auth.js";
const token = await signToken({ id: "<uuid>", role: "buyer" });
```

The same `JWT_SECRET` must be set in `.env` and in the frontend that mints
test tokens.

## How to lift the migrations to a real Supabase project

1. Create a new Supabase project.
2. Open the SQL editor.
3. Paste `migrations/0001_init.sql` then `0002_rls.sql` then
   `0003_seed.sql` then `0004_fx_rates.sql` in order. They are
   idempotent — re-running is safe.
4. Set the project's `DATABASE_URL` to the Supabase connection string.
5. Set `JWT_SECRET` in your app's env to the value Supabase gives you
   (or to your own — the `jose` verifier does not depend on Supabase).

The RLS policies use `current_setting('app.user_id', true)::uuid`, which
is the standard pattern for self-hosted Postgres. Supabase additionally
exposes `auth.uid()` (PostgREST magic). If you want to use Supabase's
own auth, add `auth.uid() = current_setting('app.user_id', true)::uuid`
to each policy; the rest of the code does not change.

## Project layout

```
src/
  server.ts                      # Fastify boot, error handler, route registration
  db.ts                          # pg.Pool + withTransaction({userId, role})
  auth.ts                        # JWT sign/verify (HS256, pinned alg/iss/aud)
  errors.ts                      # AppError + HTTP mappings
  migrate.ts                     # SQL migration runner
  modules/
    orders/
      order.state.ts             # enum + transition table
      checkout.schema.ts         # zod request/response
      checkout.service.ts        # server-computed totals, idempotency, FX snapshot
      checkout.route.ts          # POST /api/v1/orders/checkout
      idempotency.ts             # (user_id, key) uniqueness + hash check
    logistics/
      pod.schema.ts
      pod.service.ts             # driver-from-JWT, state guard
      pod.route.ts               # POST /api/v1/logistics/pod
    payments/
      momo.ts                    # STUB: real MTN request shape documented
    trust/
      trust-score.ts             # 5-layer weighted composite
    fx/
      fx.ts                      # UGX->quote lookup
migrations/
  0001_init.sql                  # tables, enums, indexes
  0002_rls.sql                   # RLS policies + audit immutability triggers
  0003_seed.sql                  # demo users + 3 products
  0004_fx_rates.sql              # initial FX rates
test/
  setup.ts                       # boots embedded Postgres + Fastify
  trust-score.test.ts            # 2 unit tests
  api.test.ts                    # 7 integration tests against live endpoints
```

## Limitations and follow-ups

1. **Login endpoint.** No `/api/v1/auth/login`. The seed has bcrypt-shaped
   password_hash stubs; add an argon2-verifying route when you wire real
   auth.
2. **Driver assignment flow.** Orders are created in `created` status. The
   `assigned` transition currently requires manual `UPDATE` in the DB or
   an admin endpoint — not built.
3. **FX rate freshness.** The MVP loads the latest rate for a pair; it
   does not enforce rate staleness. Add a `max_age_seconds` check in
   `src/modules/fx/fx.ts` if you want guaranteed freshness.
4. **TLS, mTLS, secrets at rest.** Out of scope. Run behind a real
   reverse proxy in production.

## Verified results (last run, real output)

`npm test` exit 0, 113/113 tests pass:
  - trust-score: 2/2
  - api integration: 10/10
    - checkout happy path with server-computed total (2,500,000 UGX minor)
    - idempotent replay returns byte-identical body
    - hash-mismatch idempotency key returns 409
    - non-buyer caller gets 403
    - missing JWT gets 401
    - POD from non-assigned driver gets 403
    - POD for order not in {assigned} gets 409
    - POD happy path: assigned -> picked_up -> delivered
    - RLS cross-tenant denial: wrong app.user_id sees 0 rows
    - audit log immutability: UPDATE blocked by trigger for app role

`npx tsx src/smoke.ts` produces, with a real embedded Postgres and a
real running Fastify server:

```
[smoke] postgres ready on 40999
[smoke] migrations applied
[smoke] server on http://127.0.0.1:54062
[smoke] checkout response status=201
[smoke] checkout response body={"order_id":"32245e15-297c-4f61-a602-d0744e9e132d","status":"created","total_minor":2500000,"currency":"UGX","fx_rate_snapshot":"1.00000000","items":[{"product_id":"a1b2c3d4-0001-4000-8000-000000000001","qty":1,"unit_price_minor":2500000,"line_total_minor":2500000}]}
[smoke] RBAC check: PASS: UPDATE blocked — audit_logs is append-only for role app
[smoke] done
```

The trailing `EBUSY: rmdir ...` line in some runs is harmless — it is
Windows holding a lock on the temp data dir during teardown. The smoke
output and PASS verdict appear before it.

To run the smoke yourself:

```bash
npm run smoke   # uses tsx, no separate build needed
```

## Hosting

The backend runs anywhere Node 22+ runs. Three deployment shapes ship
in this repo.

### Required environment

| Variable          | Required | Description |
|-------------------|----------|-------------|
| `JWT_SECRET`      | yes      | HS256 signing secret, >= 32 chars. Generate with `node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"`. |
| `DATABASE_URL`    | yes      | Postgres connection string. |
| `PORT`            | no       | Default `3001`. |
| `HOST`            | no       | Default `0.0.0.0`. |
| `NODE_ENV`        | no       | Set to `production` for HSTS. |
| `LOG_LEVEL`       | no       | Default `info`. |
| `ALLOWED_ORIGINS` | no       | Comma-separated. Empty allows all (dev only). |
| `AI_PROVIDER`     | no       | `openai` \| `anthropic` \| `gemini`. |
| `LLM_API_KEY`     | no       | Without this, AI endpoints return 503 `ai_disabled`. |
| `AI_MODEL`        | no       | Per-provider default. |
| `GOOGLE_CLIENT_ID`| no       | Web OAuth 2.0 client ID. Without this, `POST /api/v1/auth/google` returns 503 `google_auth_disabled`. |

See `.env.example` for a template.

### Docker

```bash
docker build -t scottstechx-api .
docker run --rm -p 3001:3001 \
  -e JWT_SECRET="$(node -e "console.log(require('crypto').randomBytes(48).toString('base64'))")" \
  -e DATABASE_URL=postgres://app:***@host.docker.internal:5432/scottstechx \
  scottstechx-api
```

Multi-stage image, `node:22-bookworm-slim`, runs under `tini`, exposes
`/healthz` as a health check.

### Render

`render.yaml` is a one-click Blueprint. It provisions the API service
and a managed Postgres database, wires `DATABASE_URL` automatically, and
deploys.

### Google Cloud Run

```bash
gcloud run deploy scottstechx-api \
  --source . \
  --region us-central1 \
  --memory 512Mi \
  --allow-unauthenticated \
  --set-env-vars "NODE_ENV=production,PORT=3001" \
  --set-secrets "JWT_SECRET=scottstechx-jwt-secret:latest,DATABASE_URL=scottstechx-db-url:latest"
```

`deploy/cloud-run.example.json` documents the full env shape.

### Production database

`embedded-postgres` is dev/test only. For production, point `DATABASE_URL`
at one of:

- **Supabase** — paste migrations `0001` through `0006` into the SQL
  editor in order. The RLS policies use `current_setting('app.user_id',
  true)::uuid` which Supabase supports. Add `auth.uid() = current_setting(...)`
  to each policy if you want Supabase Auth to also work.
- **Render Postgres** — provisioned by `render.yaml`.
- **Cloud SQL** — point `DATABASE_URL` at the connection string.
- **Self-hosted Postgres 15+** — `apt install postgresql`, create the `app`
  role, restore `pg_dump` of the dev DB if you want seed data.

### Smoke after deploy

```bash
curl -fsS https://api.scottstechx.example/healthz
# expect: {"ok":true}

# 401 without a JWT
curl -fsS -o /dev/null -w '%{http_code}\n' \
  https://api.scottstechx.example/api/v1/sellers/nearby?lat=0&lng=0
```

`npm run smoke` from this directory does the same checks plus security
headers, AI status, and rate limit.

### Wiring the Android client

The Android client reads `API_BASE_URL` from `BuildConfig`. Override at
build time:

```bash
cd E:\ScottsTechX life projects\ScottsTechX\android-app
./gradlew :app:assembleRelease \
  -PapiBaseUrl=https://api.scottstechx.example/ \
  -PmarketingBaseUrl=https://scottstechx.example/
```

The release APK ships with HTTPS-only network security config and OkHttp
certificate pinning (two slots — both are placeholders that must be
replaced at release-cut time; see `NetworkModule.kt`).
