-- OBA 30-day trial, followed by 10 free uses per feature.
-- The trial starts when the account profile is created.
drop function if exists public.oba_consume_feature_use(text);

create function public.oba_consume_feature_use(p_feature_key text)
returns table (
  allowed boolean,
  used_count integer,
  remaining integer,
  trial_active boolean,
  trial_ends_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  current_user_id uuid := auth.uid();
  account_created_at timestamptz;
  current_count integer := 0;
  new_count integer;
begin
  if current_user_id is null then
    raise exception 'authentication required' using errcode = '28000';
  end if;
  if p_feature_key is null or p_feature_key !~ '^[a-z0-9_]{1,64}$' then
    raise exception 'invalid feature key' using errcode = '22023';
  end if;

  select coalesce(p.created_at, u.created_at)
    into account_created_at
    from auth.users u
    left join public.oba_user_profiles p on p.user_id = u.id
    where u.id = current_user_id;

  if account_created_at is null then
    raise exception 'account not found' using errcode = '28000';
  end if;

  select coalesce(usage.used_count, 0)
    into current_count
    from public.oba_feature_usage usage
    where usage.user_id = current_user_id
      and usage.feature_key = p_feature_key;

  if now() < account_created_at + interval '30 days' then
    return query select true, current_count, 10, true, account_created_at + interval '30 days';
    return;
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

  return query select (new_count <= 10), new_count, greatest(0, 10 - new_count), false, account_created_at + interval '30 days';
end;
$$;

revoke all on function public.oba_consume_feature_use(text) from public;
grant execute on function public.oba_consume_feature_use(text) to authenticated;
