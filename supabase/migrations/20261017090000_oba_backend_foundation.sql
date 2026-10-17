-- OBA backend foundation.
-- Keep family records, reminders, documents, and household data on Android only.
-- Supabase Auth owns credentials; this schema stores entitlement metadata and public market quotes.
create extension if not exists pgcrypto;

create table if not exists public.oba_user_entitlements (
  user_id uuid primary key references auth.users(id) on delete cascade,
  product_id text not null,
  source text not null check (source in ('bazaar', 'other')),
  status text not null default 'pending' check (status in ('pending', 'active', 'expired', 'revoked')),
  started_at timestamptz,
  expires_at timestamptz,
  verified_at timestamptz,
  provider_order_id text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.oba_market_quotes (
  symbol text primary key,
  display_name text not null,
  price_toman numeric(20, 2),
  change_percent numeric(10, 4),
  source text not null,
  observed_at timestamptz,
  fetched_at timestamptz not null default now(),
  is_stale boolean not null default true,
  check (price_toman is null or price_toman >= 0)
);

alter table public.oba_user_entitlements enable row level security;
alter table public.oba_market_quotes enable row level security;

-- Users can read only their own subscription status; clients cannot write entitlements.
create policy "users can read own entitlement"
on public.oba_user_entitlements for select to authenticated
using (user_id = (select auth.uid()));

-- Market quotes are public read-only snapshots. Only a trusted backend job may update them.
create policy "market quotes readable"
on public.oba_market_quotes for select to anon, authenticated
using (true);

revoke insert, update, delete on public.oba_user_entitlements from anon, authenticated;
revoke insert, update, delete on public.oba_market_quotes from anon, authenticated;
grant select on public.oba_user_entitlements to authenticated;
grant select on public.oba_market_quotes to anon, authenticated;
