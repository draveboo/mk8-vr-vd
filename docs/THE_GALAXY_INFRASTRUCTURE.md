# The Galaxy infrastructure and pricing model

Public product name: **The Galaxy**  
Internal product acronym: **GEM — Galaxy Experience Machine**

## Current commercial model

- GEM Basic: $4/month launch price
- GEM Creator: $9/month target standard price
- AI usage: BYOK; the customer pays their own AI provider
- Billing: Square subscriptions and Square webhooks
- Hosting: Vercel
- Database: Neon Postgres

The browser performs WebXR rendering and 2D interaction. Server-side work is limited to authentication, entitlement checks, Square webhooks, saved user data, and protected publishing actions.

## Unit economics

For a $4 online card payment, use this planning estimate:

```text
gross revenue                 $4.00
Square estimate (2.9% + $0.30) -$0.42
net before infrastructure     $3.58
```

Vercel Pro is currently budgeted at $20/month. Break-even subscribers are approximately:

| Monthly fixed cost | $4 subscribers | $9 subscribers |
| ---: | ---: | ---: |
| $20 | 6 | 3 |
| $50 | 14 | 6 |
| $100 | 28 | 12 |

Formula:

```text
net_per_subscriber = price - (price * 0.029) - 0.30
break_even = ceil(monthly_fixed_cost / net_per_subscriber)
```

These are planning estimates, not a tax, accounting, or payment-contract guarantee. Recheck Square's actual rate and Vercel/Neon usage monthly.

## Traffic and compute assumptions

WebXR frames are rendered on the customer's device and do not consume server CPU. The main scaling risks are dynamic API requests, database connections, saved-world traffic, and abusive requests.

Protect the system with:

1. CDN-cache static assets and public documents.
2. Keep AI calls BYOK and server routes bounded by authentication and rate limits.
3. Verify Square webhooks and make webhook handling idempotent.
4. Use pooled Neon `DATABASE_URL` for application traffic.
5. Never expose database, Square, or provider secrets to browser code.
6. Set Vercel spend alerts and a hard budget pause threshold.

## Dashboards

- **Vercel:** Usage, spend management, runtime logs, function duration, invocations, transfer, and firewall events.
- **Neon:** compute usage, storage, active connections, database health, and query activity.
- **Square:** subscription status, successful payments, failed payments, refunds, disputes, and webhook delivery.

## Sovereign User Vault & Cryptographic Key Schema

To preserve user data sovereignty while linking email, cryptographic bearer key, Square payment receipts, and the 8-parameter Soul Matrix calibration, Neon Postgres runs the `sovereign_vault` schema:

```sql
CREATE TABLE IF NOT EXISTS sovereign_vault (
  id SERIAL PRIMARY KEY,
  email VARCHAR(255) UNIQUE NOT NULL,
  key_hash VARCHAR(64) NOT NULL,
  payment_id VARCHAR(128),
  matrix_state JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sovereign_vault_email ON sovereign_vault(email);
CREATE INDEX IF NOT EXISTS idx_sovereign_vault_key_hash ON sovereign_vault(key_hash);
```

### Integration Flow:
1. **User Enters Email & Pays via Square**: Client calls `/api/vault` with `email`, `paymentId`, and current calibrated `matrixState`.
2. **Key Generation & Storage**: Server hashes a 32-byte sovereign cryptographic bearer key with SHA-256 (`key_hash`). The plaintext key is returned to the user's browser/local vault and can be emailed to them as their immutable backup token.
3. **Instant Auto-Insert Launch**: On returning or opening a new headset session, the client queries `GET /api/vault?email=...&key=...`. If verified, their 3D construct calibration (`creativity`, `balance`, `harmony`, `energy`, `joy`, `customColor`, `customSymbol`) is injected directly into WebXR local memory with zero manual configuration.

## Safe load-testing plan

Load-test a preview deployment or a dedicated staging project first. Do not stress production Square checkout, webhook endpoints, or provider APIs.

Test these routes separately:

- static homepage and WebXR assets
- authenticated entitlement check
- saved-world read/write route
- Square webhook with a signed test fixture

Record p50/p95/p99 latency, error rate, function duration, database connections, Neon compute, and Vercel spend. Start with 10 requests/second, then 25 and 50 only after the previous level is healthy.

Suggested release gates:

- p95 entitlement latency below 500 ms
- error rate below 1%
- no database connection exhaustion
- no repeated webhook side effects
- no secrets in logs or responses
- no unexpected Vercel or Neon spend increase

## Intrinsic release value calculation

Use the beta infrastructure itself as a product-operating proof point. The goal is to demonstrate that The Galaxy can release improvements quickly, cheaply, and repeatably while preserving the data stream.

Track each release with:

```text
release_value_score = delivered_capability / (engineering_hours + monthly_cost + operational_steps)
```

For the first beta release, record:

| Measure | Target |
| --- | ---: |
| Time from branch to beta URL | <= 3 hours |
| Incremental monthly infrastructure cost | <= $10 |
| Manual deployment steps | <= 5 |
| Rollback time | <= 10 minutes |
| Data loss during rollback | 0 records |
| Production impact | 0 planned downtime |

The release qualifies as a high-value facilitation improvement when it meets all targets and provides a repeatable path for the next feature. This measures execution value and operational leverage; it is not a formal financial valuation.

For data-stream continuity, keep user data in Neon, keep release code in Git tags, and never use a rollback to delete or rewrite production records. Roll back application code while preserving database migrations and audit records.

