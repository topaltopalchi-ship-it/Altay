-- OBA username accounts and per-feature free-use limits (10 uses per feature).
create table if not exists public.oba_user_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique,
  created_at timestamptz not null default now(),
  constraint oba_user_profiles_username_format
    check (username ~ '^[a-z0-9_]{3,24}$')
);

alter table public.oba_user_profiles enable row level security;

drop policy if exists "users can read own OBA profile" on public.oba_user_profiles;
create policy "users can read own OBA profile"
  on public.oba_user_profiles for select
  to authenticated
  using (user_id = (select auth.uid()));

-- Keep usage counters server-controlled; clients can only read their own counters.
create table if not exists public.oba_feature_usage (
  user_id uuid not null references auth.users(id) on delete cascade,
  feature_key text not null check (feature_key ~ '^[a-z0-9_]{1,64}$'),
  used_count integer not null default 0 check (used_count >= 0),
  updated_at timestamptz not null default now(),
  primary key (user_id, feature_key)
);

alter table public.oba_feature_usage enable row level security;
drop policy if exists "users can read own OBA feature usage" on public.oba_feature_usage;
create policy "users can read own OBA feature usage"
  on public.oba_feature_usage for select
  to authenticated
  using (user_id = (select auth.uid()));

-- Atomically consumes one free use. The 11th and later attempts are denied.
-- Call this before running each feature; never trust a counter stored only on the phone.
create or replace function public.oba_consume_feature_use(p_feature_key text)
returns table (allowed boolean, used_count integer, remaining integer)
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := auth.uid();
  new_count integer;
begin
  if current_user_id is null then
    raise exception 'authentication required' using errcode = '28000';
  end if;
  if p_feature_key is null or p_feature_key !~ '^[a-z0-9_]{1,64}$' then
    raise exception 'invalid feature key' using errcode = '22023';
  end if;

  insert into public.oba_feature_usage as usage (user_id, feature_key, used_count, updated_at)
  values (current_user_id, p_feature_key, 1, now())
  on conflict (user_id, feature_key) do update
    set used_count = case
      when usage.used_count < 10 then usage.used_count + 1
      else usage.used_count
    end,
    updated_at = now()
  returning usage.used_count into new_count;

  return query select (new_count <= 10), new_count, greatest(0, 10 - new_count);
end;
$$;

revoke all on function public.oba_consume_feature_use(text) from public;
grant execute on function public.oba_consume_feature_use(text) to authenticated;

create or replace function public.oba_handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  requested_username text;
begin
  requested_username := lower(trim(coalesce(new.raw_user_meta_data ->> 'username', '')));
  if requested_username !~ '^[a-z0-9_]{3,24}$' then
    requested_username := 'user_' || replace(left(new.id::text, 8), '-', '');
  end if;

  insert into public.oba_user_profiles (user_id, username)
  values (new.id, requested_username)
  on conflict (user_id) do nothing;

  -- No time-based trial or trial entitlement is granted.
  return new;
end;
$$;

drop trigger if exists oba_on_auth_user_created on auth.users;
create trigger oba_on_auth_user_created
after insert on auth.users
for each row execute procedure public.oba_handle_new_auth_user();
