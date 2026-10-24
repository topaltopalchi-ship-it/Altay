-- OBA user-owned cloud data.
-- Applied to the OBA Supabase project as migration 20261024194656_oba_user_cloud_data.
-- This repository copy keeps the live schema change version-controlled.

create table if not exists public.oba_user_data (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  data_type text not null check (data_type in (
    'expense','income','purchase','inventory','budget',
    'household_task','shopping_list','reminder','vehicle_service','home_service'
  )),
  record_id text not null,
  payload jsonb not null default '{}'::jsonb check (jsonb_typeof(payload) = 'object'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, data_type, record_id)
);

alter table public.oba_user_data enable row level security;

drop policy if exists "Users can read own OBA data" on public.oba_user_data;
create policy "Users can read own OBA data"
  on public.oba_user_data for select to authenticated
  using (auth.uid() = user_id);

drop policy if exists "Users can insert own OBA data" on public.oba_user_data;
create policy "Users can insert own OBA data"
  on public.oba_user_data for insert to authenticated
  with check (auth.uid() = user_id);

drop policy if exists "Users can update own OBA data" on public.oba_user_data;
create policy "Users can update own OBA data"
  on public.oba_user_data for update to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists "Users can delete own OBA data" on public.oba_user_data;
create policy "Users can delete own OBA data"
  on public.oba_user_data for delete to authenticated
  using (auth.uid() = user_id);

revoke all on table public.oba_user_data from anon;
grant select, insert, update, delete on table public.oba_user_data to authenticated;
