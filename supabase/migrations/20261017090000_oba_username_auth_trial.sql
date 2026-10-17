-- OBA username accounts and one-time 30-day trial entitlement.
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

-- Do not allow clients to insert/update profiles or entitlement rows directly.
-- The trigger below is the only automatic trial grant path.
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

  insert into public.oba_user_entitlements
    (user_id, product_id, source, status, started_at, expires_at, verified_at)
  values
    (new.id, 'trial_30d', 'other', 'active', now(), now() + interval '30 days', now())
  on conflict (user_id) do nothing;

  return new;
end;
$$;

drop trigger if exists oba_on_auth_user_created on auth.users;
create trigger oba_on_auth_user_created
after insert on auth.users
for each row execute procedure public.oba_handle_new_auth_user();
