# OBA Android backend setup

OBA remains an Android-only application. This folder prepares a backend contract; it does not create a web version.

## What is included

- `app/src/main/java/ir/khone/yar/BackendConfig.java`: reads the public Supabase URL and publishable key from Android `BuildConfig`.
- `app/src/main/java/ir/khone/yar/MarketQuotesClient.java`: read-only REST client for market quote snapshots, with timeouts and safe user-facing errors.
- `supabase/migrations/20261017090000_oba_backend_foundation.sql`: starting schema for household membership, subscription entitlements, and market quote snapshots. RLS is enabled on all exposed tables.
- Android CI reads optional repository variables during APK builds. Empty values are allowed, so builds do not fail before Supabase is connected.

## Configure GitHub Actions

In GitHub repository settings, add these **Actions variables** (not secrets; they are public client configuration):

- `OBA_SUPABASE_URL`: project URL, such as `https://YOUR_PROJECT_REF.supabase.co`
- `OBA_SUPABASE_PUBLISHABLE_KEY`: a Supabase publishable key beginning with `sb_publishable_`

Do not add a Supabase secret/service-role key to GitHub variables used by the APK. Do not embed third-party market-provider credentials in the Android app.

## Before using a real Supabase project

1. Link the intended Supabase account/project and confirm its project ID.
2. Review and apply the SQL migration to that project, then inspect RLS/advisors.
3. Add a trusted Edge Function to verify Bazaar purchase receipts with the provider's official server-side verification mechanism. The client must never be allowed to mark its own entitlement as active.
4. Configure market-data provider credentials as Edge Function secrets, fetch quotes server-side, and update `oba_market_quotes`. Do not scrape from the Android app or invent fallback prices.
5. Test anonymous reads, signed-in reads, and denied client writes against the actual project.
6. Add authentication/session handling before enabling household syncing or entitlement reads.

## Security model

- Publishable key in an APK is public by design; RLS is the security boundary.
- No password is stored by OBA tables. Supabase Auth should manage credentials and password hashes.
- The entitlement table has no client write policies. Purchase verification is a backend-only operation.
- Market quote rows are read-only to app clients. A trusted backend job must update them and set `is_stale` accurately.
- Household member writes are not enabled directly from the client; a validated server-side invite/membership flow is required.

## Current limitation

This is a repository scaffold only. It is not yet connected to a live Supabase project, the purchase verification function is not deployed, and market quotes are not live until a data provider and server-side updater are configured.
