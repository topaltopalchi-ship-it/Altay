-- Fix the quota boundary: the 11th and later attempts must remain denied.
-- Retain a capped sentinel count of 11 so usage rows cannot grow without bound.
create or replace function public.oba_consume_feature_use(p_feature_key text)
returns table (allowed boolean, used_count integer, remaining integer)
language plpgsql security definer set search_path = ''
as $$
declare current_user_id uuid := auth.uid(); new_count integer;
begin
  if current_user_id is null then raise exception 'authentication required' using errcode = '28000'; end if;
  if p_feature_key is null or p_feature_key not in (
    'home_expense', 'vehicle_expense', 'shop_expense', 'school_expense',
    'health_expense', 'other_expense', 'shop_income',
    'budget', 'inventory', 'summary', 'health', 'vehicle', 'reminder',
    'income', 'calc', 'shopping', 'task', 'expense'
  ) then
    raise exception 'invalid feature key' using errcode = '22023';
  end if;
  insert into public.oba_feature_usage as usage (user_id, feature_key, used_count, updated_at)
  values (current_user_id, p_feature_key, 1, now())
  on conflict (user_id, feature_key) do update
    set used_count = case when usage.used_count < 11 then usage.used_count + 1 else 11 end,
        updated_at = now()
  returning usage.used_count into new_count;
  return query select (new_count <= 10), new_count, greatest(0, 10 - new_count);
end;
$$;
revoke all on function public.oba_consume_feature_use(text) from public, anon;
grant execute on function public.oba_consume_feature_use(text) to authenticated;
