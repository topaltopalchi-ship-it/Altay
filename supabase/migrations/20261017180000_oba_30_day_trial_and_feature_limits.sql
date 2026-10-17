-- OBA per-feature free-use limits: 10 uses per feature, no time-based trial.
-- Recreate the RPC with the same three-column contract as the initial migration.
drop function if exists public.oba_consume_feature_use(text);

create function public.oba_consume_feature_use(p_feature_key text)
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
