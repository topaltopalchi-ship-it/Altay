-- OBA backend foundation: households, entitlements, and market quote cache.
-- Apply only after linking the intended Supabase project and reviewing the migration.
create extension if not exists pgcrypto;

create table if not exists public.oba_households (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(trim(name)) between 1 and 100),
  created_by uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

create table if not exists public.oba_household_members (
  household_id uuid not null references public.oba_households(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  member_role text not null default 'member' check (member_role in ('owner', 'admin', 'member', 'viewer')),
  joined_at timestamptz not null default now(),
  primary key (household_id, user_id)
);

create table if not exists public.oba_user_entitlements (
  user_id uuid primary key references auth.users(id) on delete cascade,
  product_id text not null,
  source text not null check (source in ('bazaar', 'other')),
  status text not null default 'pending' check (status in ('pending', 'active', 'expired', 'revoked')),
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

alter table public.oba_households enable row level security;
alter table public.oba_household_members enable row level security;
alter table public.oba_user_entitlements enable row level security;
alter table public.oba_market_quotes enable row level security;

-- Members can see households they belong to. Membership writes should be
-- mediated by a trusted server function, not arbitrary client inserts.
create policy "household members can read their households"
on public.oba_households for select to authenticated
using (
  exists (
    select 1 from public.oba_household_members m
    where m.household_id = id and m.user_id = (select auth.uid())
  )
  or created_by = (select auth.uid())
);

create policy "members can read household membership"
on public.oba_household_members for select to authenticated
using (
  user_id = (select auth.uid())
  or exists (
    select 1 from public.oba_household_members own
    where own.household_id = household_id and own.user_id = (select auth.uid())
  )
);

-- No client-side INSERT/UPDATE/DELETE policies on entitlements.
-- Only a trusted server-side purchase verifier should write entitlement status.
create policy "users can read own entitlement"
on public.oba_user_entitlements for select to authenticated
using (user_id = (select auth.uid()));

-- Market quotes are public read-only data; only trusted backend jobs update them.
create policy "market quotes readable"
on public.oba_market_quotes for select to anon, authenticated
using (true);

revoke insert, update, delete on public.oba_households from anon, authenticated;
revoke insert, update, delete on public.oba_household_members from anon, authenticated;
revoke insert, update, delete on public.oba_user_entitlements from anon, authenticated;
revoke insert, update, delete on public.oba_market_quotes from anon, authenticated;

grant select on public.oba_households to authenticated;
grant select on public.oba_household_members to authenticated;
grant select on public.oba_user_entitlements to authenticated;
grant select on public.oba_market_quotes to anon, authenticated;
