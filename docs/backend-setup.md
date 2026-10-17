# OBA Android backend setup

OBA is an Android-only app. Family records, reminders, documents, expenses, home inventory, and all other personal household data remain on the device. No web version is planned.

## Live Supabase project

- Project name: `OBA`
- Project ref: `sxyaujvliummbvzznejq`
- Region: `eu-central-1` (Frankfurt)
- Plan selected: Free ($0/month quoted at creation; usage limits apply)
- Project status: active and healthy

## Backend scope

- Supabase Auth is intended to manage sign-up, sign-in, password recovery, and password hashes. OBA must never store raw passwords in its own tables.
- `oba_user_entitlements` stores only subscription metadata needed to check access: user ID, product/source, verification status, and start/expiry timestamps. Client writes are denied by RLS; a trusted purchase-verification function must activate subscriptions.
- `oba_market_quotes` stores public market quote snapshots only. It contains no family-specific data. It is read-only for Android clients; a trusted server-side updater and a licensed/reliable provider still need to be configured.
- No household/member tables are used on Supabase. Family records stay in Android local storage.

## Android configuration

`BackendConfig.java` reads these public client settings at build time:

- `OBA_SUPABASE_URL`: `https://sxyaujvliummbvzznejq.supabase.co`
- `OBA_SUPABASE_PUBLISHABLE_KEY`: a Supabase publishable key beginning with `sb_publishable_`

They can be supplied as GitHub Actions repository variables or environment variables during a local build. The publishable key is public by design and must be paired with correct RLS policies. Never put a Supabase secret/service-role key or third-party market-provider credentials into the APK.

## Schema and security

- Migration: `supabase/migrations/20261017090000_oba_backend_foundation.sql`
- The migration has been applied to the live project.
- RLS is enabled on both public tables.
- Authenticated users can read only their own entitlement row.
- Anonymous/authenticated clients can read market quotes, but cannot insert/update/delete quote rows.
- Clients cannot write entitlement rows. Subscription activation must be performed by trusted server-side purchase verification.

## Not yet implemented or activated

1. Android sign-up/sign-in/password-recovery screens and session storage.
2. Verified subscription purchase flow for a three-month Bazaar product, including server-side receipt validation and renewal/expiry handling.
3. A trusted server-side market data updater and an approved provider for gold, USD, silver, and coin prices.
4. GitHub Actions public configuration variables, if they are not already set.
5. End-to-end tests on a physical Android device.

The current free-quota prompt for the eleventh record is a local UI guard. It is not tamper-resistant until account and entitlement checks are implemented; clearing app data or reinstalling may reset local-only quota state. This limitation must not be presented as server-enforced protection.
