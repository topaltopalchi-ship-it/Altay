-- OBA keeps backups exclusively on the user's device.
-- Remove the table that was previously used to store cloud backup payloads.
drop table if exists public.oba_user_data;
