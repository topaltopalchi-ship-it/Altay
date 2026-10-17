-- Restrict privileged OBA functions to the roles that actually need them.
revoke all on function public.oba_consume_feature_use(text) from public, anon;
grant execute on function public.oba_consume_feature_use(text) to authenticated;

-- This function is only intended to run as the auth.users trigger, never via PostgREST RPC.
revoke all on function public.oba_handle_new_auth_user() from public, anon, authenticated;
